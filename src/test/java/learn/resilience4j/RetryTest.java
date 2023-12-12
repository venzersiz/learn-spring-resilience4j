package learn.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.vavr.control.Try;
import java.time.Duration;
import java.util.function.Supplier;
import learn.resilience4j.service.ExternalService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class RetryTest extends AbstractCircuitBreakerTest {

    ExternalService externalService = new ExternalService();

    @Test
    @DisplayName("기본 설정. 원격 서비스 정상")
    void t1() {
        RetryConfig retryConfig = RetryConfig.custom()
                                             .maxAttempts(3) // 첫 번재 시도도 포함. 기본값: 3
                                             .waitDuration(Duration.ofMillis(500)) // 기본값: 500ms
                                             .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig, getRetryRegistryEventConsumer());

        Retry retry = retryRegistry.retry("b");

        assertCallSuccess(retry);
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isOne();
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isZero();
    }

    @Test
    @DisplayName("기본 설정. 원격 서비스 장애")
    void t2() {
        RetryConfig retryConfig = RetryConfig.custom()
                                             .maxAttempts(3)
                                             .waitDuration(Duration.ofMillis(500))
                                             .build();

        RetryRegistry retryRegistry = RetryRegistry.of(retryConfig, getRetryRegistryEventConsumer());

        Retry retry = retryRegistry.retry("b");

        assertCallFailure(retry);
        // Retry 'b', waiting PT0.5S until attempt '1'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // Retry 'b', waiting PT0.5S until attempt '2'. Last attempt failed with exception 'java.lang.RuntimeException: Server fault'.
        // Retry 'b' recorded a failed retry attempt. Number of retry attempts: '3'. Giving up. Last exception was: 'java.lang.RuntimeException: Server fault'.
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isOne(); // Returns the number of failed calls after all retry attempts.
        // 3회 Retry 이후 Circuit Breaker에 1회 실패로 기록해서 1인가?
    }

    private void assertCallSuccess(Retry retry) {

        Supplier<String> decoratedSupplier = Retry.decorateSupplier(retry, externalService::success);

        Try<String> result = Try.ofSupplier(decoratedSupplier)
                                .recover(throwable -> "Recovery");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo("Success");
    }

    private void assertCallFailure(Retry retry) {

        Supplier<String> decoratedSupplier = Retry.decorateSupplier(retry, externalService::failure);

        Try<String> result = Try.ofSupplier(decoratedSupplier)
                                .recover(throwable -> "Recovery");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo("Recovery");
    }
}
