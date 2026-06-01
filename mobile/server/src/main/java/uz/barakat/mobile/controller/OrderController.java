package uz.barakat.mobile.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.barakat.mobile.config.CurrentUser;
import uz.barakat.mobile.dto.OrderDtos.CreateOrderRequest;
import uz.barakat.mobile.dto.OrderDtos.OrderResponse;
import uz.barakat.mobile.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest req) {
        return orderService.create(CurrentUser.id(), req);
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list(CurrentUser.id());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return orderService.get(CurrentUser.id(), id);
    }
}
