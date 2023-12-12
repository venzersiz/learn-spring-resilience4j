package learn.resilience4j.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry.Metrics;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = RANDOM_PORT) // TestRestTemplate
abstract class AbstractIntegrationTest {

    protected static final String SERVER_A = "serverA";

    protected static final String SERVER_B = "serverB";

    protected static final String SERVER_C = "serverC";

    @Autowired
    protected CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    protected RetryRegistry retryRegistry;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected WebTestClient webClient;

    protected Metrics getRetryMetrics(String circuitBreakerName) {

        return retryRegistry.retry(circuitBreakerName).getMetrics();
    }

    protected void transitionToOpen(String circuitBreakerName) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).transitionToOpenState();
    }

    protected void transitionToHalfOpen(String circuitBreakerName) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).transitionToHalfOpenState();
    }

    protected void transitionToClosed(String circuitBreakerName) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).transitionToClosedState();
    }

    protected void callSuccess(String server) {

        ResponseEntity<String> response = restTemplate.getForEntity("/" + server + "/success", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    protected void callFailure(String server) {

        ResponseEntity<String> response = restTemplate.getForEntity("/" + server + "/failure", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    protected void callFailureWithFallback(String server) {

        ResponseEntity<String> response = restTemplate.getForEntity("/" + server + "/failureWithFallback", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
