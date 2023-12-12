package learn.resilience4j.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Service
public class ServerAService {

    private static final String SERVER_A = "serverA";

    @CircuitBreaker(name = SERVER_A)
    public String success() {
        return "Success";
    }

    @CircuitBreaker(name = SERVER_A)
    public String failure() {
        throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 장애 발생");
    }

    @CircuitBreaker(name = SERVER_A, fallbackMethod = "fallback")
    public String failureWithFallback() {
        throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 장애 발생");
    }

    // 대비책이 될 메서드의 메서드 Signature와 같아야 한다
    private String fallback(HttpServerErrorException e) {
        return "Recovered from HttpServerErrorException: " + e.getMessage();
    }

    private String fallback(Exception e) {
        return "Recovered: " + e.getMessage();
    }
}
