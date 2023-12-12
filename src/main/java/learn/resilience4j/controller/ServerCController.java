package learn.resilience4j.controller;

import learn.resilience4j.service.ServerCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Circuit Breaker + Retry 테스트용
@RestController
@RequestMapping("/serverC")
@RequiredArgsConstructor
public class ServerCController {

    private final ServerCService service;

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
