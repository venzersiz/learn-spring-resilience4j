package learn.resilience4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

abstract class AbstractCircuitBreakerTest extends AbstractIntegrationTest {

    protected void assertState(String circuitBreakerName, CircuitBreaker.State state) {

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName);

        assertThat(circuitBreaker.getState()).isEqualTo(state);
    }
}
