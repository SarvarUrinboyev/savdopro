package uz.barakat.mobile.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.mobile.config.JwtService;
import uz.barakat.mobile.domain.Cart;
import uz.barakat.mobile.domain.Customer;
import uz.barakat.mobile.domain.OtpCode;
import uz.barakat.mobile.dto.AuthDtos.AuthResponse;
import uz.barakat.mobile.dto.AuthDtos.CustomerResponse;
import uz.barakat.mobile.dto.AuthDtos.RequestOtpResponse;
import uz.barakat.mobile.dto.AuthDtos.UpdateProfileRequest;
import uz.barakat.mobile.repository.CartRepository;
import uz.barakat.mobile.repository.CustomerRepository;
import uz.barakat.mobile.repository.OtpCodeRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_ATTEMPTS = 5;

    private final CustomerRepository customers;
    private final OtpCodeRepository otpCodes;
    private final CartRepository carts;
    private final JwtService jwtService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.dev-mode}") private boolean devMode;
    @Value("${app.otp.ttl-seconds}") private int ttlSeconds;
    @Value("${app.otp.length}") private int codeLength;

    public AuthService(CustomerRepository customers, OtpCodeRepository otpCodes,
                       CartRepository carts, JwtService jwtService) {
        this.customers = customers;
        this.otpCodes = otpCodes;
        this.carts = carts;
        this.jwtService = jwtService;
    }

    @Transactional
    public RequestOtpResponse requestOtp(String rawPhone) {
        String phone = normalize(rawPhone);
        String code = generateCode();

        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setCode(code);
        otp.setExpiresAt(Instant.now().plus(ttlSeconds, ChronoUnit.SECONDS));
        otpCodes.save(otp);

        if (devMode) {
            log.info("[OTP][DEV] {} uchun kod: {}", phone, code);
        } else {
            // TODO: real SMS provayder (Eskiz / Play Mobile) integratsiyasi
            log.info("[OTP] SMS yuborildi: {}", phone);
        }
        return new RequestOtpResponse(phone, ttlSeconds, devMode ? code : null);
    }

    @Transactional
    public AuthResponse verifyOtp(String rawPhone, String code) {
        String phone = normalize(rawPhone);
        OtpCode otp = otpCodes.findTopByPhoneAndUsedFalseOrderByIdDesc(phone)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Kod topilmadi, qaytadan so'rang"));

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "Urinishlar tugadi, yangi kod so'rang");
        }
        if (otp.isExpired()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Kod muddati tugagan");
        }
        if (!otp.getCode().equals(code)) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw new ResponseStatusException(UNAUTHORIZED, "Kod noto'g'ri");
        }

        otp.setUsed(true);

        Customer customer = customers.findByPhone(phone).orElseGet(() -> {
            Customer c = new Customer();
            c.setPhone(phone);
            Customer saved = customers.save(c);
            Cart cart = new Cart();
            cart.setCustomer(saved);
            carts.save(cart);
            return saved;
        });

        String token = jwtService.generate(customer.getId(), customer.getPhone());
        return new AuthResponse(token, toResponse(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse me(Long customerId) {
        return toResponse(getCustomer(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, UpdateProfileRequest req) {
        Customer c = getCustomer(customerId);
        if (req.name() != null) c.setName(req.name());
        if (req.email() != null) c.setEmail(req.email());
        return toResponse(customers.save(c));
    }

    private Customer getCustomer(Long id) {
        return customers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mijoz topilmadi"));
    }

    private CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(c.getId(), c.getPhone(), c.getName(), c.getEmail());
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        int n = random.nextInt(bound);
        return String.format("%0" + codeLength + "d", n);
    }

    /** Telefon raqamini standart "+998..." ko'rinishiga keltiradi. */
    private String normalize(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 9) digits = "998" + digits;          // 901234567 → 998901234567
        if (digits.startsWith("998") && digits.length() == 12) return "+" + digits;
        return "+" + digits;
    }
}
