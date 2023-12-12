package learn.resilience4j.controller;

import learn.resilience4j.service.ServerAService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Circuit Breaker 테스트용
@RestController
@RequestMapping("/serverA")
@RequiredArgsConstructor
public class ServerAController {

    private final ServerAService service;

    @GetMapping("/success")
    public String success() {
        return service.success();
    }

    @GetMapping("/failure")
    public String failure() {
        return service.failure();
    }

    @GetMapping("/failureWithFallback")
    public String failureWithFallback() {
        return service.failureWithFallback();
    }
}
