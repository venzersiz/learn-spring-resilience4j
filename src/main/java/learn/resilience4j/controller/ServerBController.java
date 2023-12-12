package learn.resilience4j.controller;

import learn.resilience4j.service.ServerBService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Retry 테스트용
@RestController
@RequestMapping("/serverB")
@RequiredArgsConstructor
public class ServerBController {

    private final ServerBService service;

    @GetMapping("/success")
    public String success() {
        return service.success();
    }

    @GetMapping("/failure")
    public String failure() {
        return service.failure();
    }
}
