package learn.resilience4j.service;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;

@Service
@Slf4j
public class ServerBService {

    private static final String SERVER_B = "serverB";

    @Retry(name = SERVER_B)
    public String success() {
        return "Success";
    }

    @Retry(name = SERVER_B)
    public String failure() {
        log.info("서버 장애 발생");
        throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "서버 장애 발생");
    }

    private String fallback(HttpServerErrorException e) {
        return "Recovered from HttpServerErrorException: " + e.getMessage();
    }

    private String fallback(Exception e) {
        return "Recovered: " + e.getMessage();
    }
}
