package learn.resilience4j.spring;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.IllegalStateTransitionException;
import io.vavr.collection.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest extends AbstractCircuitBreakerTest {

    @Test
    @DisplayName("최소 5번 요청, 실패율 50% 이상으로 설정되어 OPEN")
    void t1() {
        assertState(SERVER_A, CLOSED);

        Stream.range(0, 4).forEach(index -> callFailure(SERVER_A));
        assertState(SERVER_A, CLOSED);

        callFailure(SERVER_A);
        assertState(SERVER_A, OPEN);
    }

    @Test
    @DisplayName("CLOSED 상태에서 바로 HALF_OPEN으로 변경 불가")
    void t2() {
        assertThatThrownBy(() -> transitionToHalfOpen(SERVER_A)).isInstanceOf(IllegalStateTransitionException.class);
        // io.github.resilience4j.circuitbreaker.IllegalStateTransitionException:
        //  CircuitBreaker 'serverA' tried an illegal state transition from CLOSED to HALF_OPEN
    }

    @Test
    @DisplayName("OPEN/CLOSE 전환 여부를 판단할 호출 횟수인 3을 만족하여 HALF_OPEN -> OPEN")
    void t3() {
        transitionToOpen(SERVER_A);
        transitionToHalfOpen(SERVER_A);
        assertState(SERVER_A, HALF_OPEN);

        Stream.rangeClosed(1, 2).forEach(count -> callFailure(SERVER_A));
        assertState(SERVER_A, HALF_OPEN);

        callFailure(SERVER_A);
        assertState(SERVER_A, OPEN);
    }

    @Test
    @DisplayName("OPEN/CLOSE 전환 여부를 판단할 호출 횟수인 3을 만족하여 HALF_OPEN -> CLOSED")
    void t4() {
        transitionToOpen(SERVER_A);
        transitionToHalfOpen(SERVER_A);
        assertState(SERVER_A, HALF_OPEN);

        Stream.rangeClosed(1, 2).forEach(count -> callSuccess(SERVER_A));
        assertState(SERVER_A, HALF_OPEN);

        callSuccess(SERVER_A);
        assertState(SERVER_A, CLOSED);
    }

    @Test
    @DisplayName("실패 시 Fallback 로직 적용")
    void t5() {
        callFailureWithFallback(SERVER_A);
    }
}
