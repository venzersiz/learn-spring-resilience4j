package learn.resilience4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.retry.Retry.Metrics;
import io.vavr.collection.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircuitBreakWithRetryTest extends AbstractCircuitBreakerTest {

    @Test
    @DisplayName("최소 3번 요청, 실패율 50% 이상, 재시도 2로 설정")
    void t1() {
        Stream.range(0, 2).forEach(index -> callFailure(SERVER_C));
        // Loop 2 x Retry 2 = Call 4

        // Loop 1
        // - Retry 1: Recorded an exception as failure
        // - Retry 2: Recorded an exception as failure

        // Loop 2
        // - Retry 1: Recorded an exception as failure
        // Exceeded failure rate threshold. Current failure rate: 100.0
        // Changed state from CLOSED to OPEN
        // - Retry 2: Recorded a call which was not permitted
        // OPEN and does not permit further calls

        Metrics metrics = getRetryMetrics(SERVER_C);
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);

        callFailure(SERVER_C);
        // Retry 1: Recorded a call which was not permitted
        // OPEN and does not permit further calls
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(2);
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);
    }
}
