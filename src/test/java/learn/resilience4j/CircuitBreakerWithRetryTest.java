package learn.resilience4j;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.function.Supplier;
import learn.resilience4j.service.ExternalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircuitBreakerWithRetryTest extends AbstractCircuitBreakerTest {

    ExternalService externalService = new ExternalService();

    @Test
    @DisplayName("기본 설정. 원격 서비스 정상")
    void t1() {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                .failureRateThreshold(50)
                                .minimumNumberOfCalls(5)
                                .build();
        // 5번 호출되고 실패율 50% 이상이면 OPEN

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        RetryConfig retryConfig = RetryConfig.custom()
                                             .maxAttempts(3)
                                             .waitDuration(Duration.ofMillis(500))
                                             .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig, getRetryRegistryEventConsumer());

        Retry retry = retryRegistry.retry("b");

        assertCallSuccess(circuitBreaker, retry);
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F);
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isOne();
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isOne();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
    }

    @Test
    @DisplayName("기본 설정. 원격 서비스 장애")
    void t2() {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                .failureRateThreshold(50)
                                .minimumNumberOfCalls(5)
                                .build();

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        RetryConfig retryConfig = RetryConfig.custom()
                                             .maxAttempts(3)
                                             .waitDuration(Duration.ofMillis(500))
                                             .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig, getRetryRegistryEventConsumer());

        Retry retry = retryRegistry.retry("b");

        assertCallFailure(circuitBreaker, retry);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // Retry 'b', waiting PT0.5S until attempt '1'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // Retry 'b', waiting PT0.5S until attempt '2'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // Retry 'b' recorded a failed retry attempt. Number of retry attempts: '3'. Giving up. Last exception was: 'java.lang.RuntimeException: Server fault'.
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F);
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

        assertCallFailure(circuitBreaker, retry);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // Retry 'b', waiting PT0.5S until attempt '1'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // CircuitBreaker 'a' exceeded failure rate threshold. Current failure rate: 100.0
        // CircuitBreaker 'a' changed state from CLOSED to OPEN
        // Retry 'b', waiting PT0.5S until attempt '2'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // CircuitBreaker 'a' recorded a call which was not permitted.
        // Retry 'b' recorded a failed retry attempt. Number of retry attempts: '3'. Giving up. Last exception was:
        //  'io.github.resilience4j.circuitbreaker.CallNotPermittedException: CircuitBreaker 'a' is OPEN and does not permit further calls'.
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0F);
        assertHealthStatus(circuitBreaker, OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(5);
        // -> 호출 중간에 OPEN되면 Retry 횟수가 남아도 재시도

        assertCallFailure(circuitBreaker, retry);
        // CircuitBreaker 'a' recorded a call which was not permitted.
        // Retry 'b', waiting PT0.5S until attempt '1'. Last attempt failed with exception 'io.github.resilience4j.circuitbreaker.CallNotPermittedException:
        //  CircuitBreaker 'a' is OPEN and does not permit further calls'.
        // CircuitBreaker 'a' recorded a call which was not permitted.
        // Retry 'b', waiting PT0.5S until attempt '2'. Last attempt failed with exception 'io.github.resilience4j.circuitbreaker.CallNotPermittedException:
        //  CircuitBreaker 'a' is OPEN and does not permit further calls'.
        // CircuitBreaker 'a' recorded a call which was not permitted.
        // Retry 'b' recorded a failed retry attempt. Number of retry attempts: '3'. Giving up. Last exception was: 'io.github.resilience4j.circuitbreaker.CallNotPermittedException:
        //  CircuitBreaker 'a' is OPEN and does not permit further calls'.
        // -> OPEN된 후에 호출되면 허용하지 않는다
    }

    private void assertCallSuccess(CircuitBreaker circuitBreaker, Retry retry) {

        Supplier<String> decoratedSupplier = Retry.decorateSupplier(retry,
                                                                    CircuitBreaker.decorateSupplier(circuitBreaker, externalService::success));

        Try<String> result = Try.ofSupplier(decoratedSupplier)
                                .recover(throwable -> "Recovery");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo("Success");
    }

    private void assertCallFailure(CircuitBreaker circuitBreaker, Retry retry) {

        Supplier<String> decoratedSupplier = Retry.decorateSupplier(retry,
                                                                    CircuitBreaker.decorateSupplier(circuitBreaker, externalService::failure));

        String result = Try.ofSupplier(decoratedSupplier)
                           .recover(throwable -> "Recovery")
                           .get();
        assertThat(result).isEqualTo("Recovery");
    }
}
