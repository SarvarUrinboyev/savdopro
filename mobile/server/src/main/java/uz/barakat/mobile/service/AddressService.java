package uz.barakat.mobile.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uz.barakat.mobile.domain.Address;
import uz.barakat.mobile.domain.Customer;
import uz.barakat.mobile.dto.AddressDtos.AddressResponse;
import uz.barakat.mobile.dto.AddressDtos.CreateAddressRequest;
import uz.barakat.mobile.repository.AddressRepository;
import uz.barakat.mobile.repository.CustomerRepository;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AddressService {

    private final AddressRepository addresses;
    private final CustomerRepository customers;

    public AddressService(AddressRepository addresses, CustomerRepository customers) {
        this.addresses = addresses;
        this.customers = customers;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long customerId) {
        return addresses.findByCustomerIdOrderByIdDesc(customerId).stream()
                .map(AddressResponse::from).toList();
    }

    @Transactional
    public AddressResponse create(Long customerId, CreateAddressRequest req) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Mijoz topilmadi"));
        Address a = new Address();
        a.setCustomer(customer);
        a.setLabel(req.label());
        a.setAddressLine(req.addressLine());
        a.setLat(req.lat());
        a.setLng(req.lng());
        a.setComment(req.comment());
        return AddressResponse.from(addresses.save(a));
    }

    @Transactional
    public void delete(Long customerId, Long addressId) {
        Address a = addresses.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Manzil topilmadi"));
        addresses.delete(a);
    }
}
