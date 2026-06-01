package uz.barakat.mobile.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.mobile.config.CurrentUser;
import uz.barakat.mobile.dto.AuthDtos.AuthResponse;
import uz.barakat.mobile.dto.AuthDtos.CustomerResponse;
import uz.barakat.mobile.dto.AuthDtos.RequestOtpRequest;
import uz.barakat.mobile.dto.AuthDtos.RequestOtpResponse;
import uz.barakat.mobile.dto.AuthDtos.UpdateProfileRequest;
import uz.barakat.mobile.dto.AuthDtos.VerifyOtpRequest;
import uz.barakat.mobile.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/request-otp")
    public RequestOtpResponse requestOtp(@Valid @RequestBody RequestOtpRequest req) {
        return authService.requestOtp(req.phone());
    }

    @PostMapping("/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return authService.verifyOtp(req.phone(), req.code());
    }

    @GetMapping("/me")
    public CustomerResponse me() {
        return authService.me(CurrentUser.id());
    }

    @PatchMapping("/me")
    public CustomerResponse updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(CurrentUser.id(), req);
    }
}
