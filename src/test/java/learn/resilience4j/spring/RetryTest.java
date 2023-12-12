package learn.resilience4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.retry.Retry.Metrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetryTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("호출 성공 시 (Re)try는 1회만 수행")
    void t1() {
        Metrics metrics = getRetryMetrics(SERVER_B);
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isZero();

        callSuccess(SERVER_B);
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isOne();
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isZero();
    }

    @Test
    @DisplayName("호출 실패 시 Retry는 3회 수행")
    void t2() {
        Metrics metrics = getRetryMetrics(SERVER_B);

        callFailure(SERVER_B);
        assertThat(metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfSuccessfulCallsWithRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithoutRetryAttempt()).isZero();
        assertThat(metrics.getNumberOfFailedCallsWithRetryAttempt()).isOne();
    }
}
