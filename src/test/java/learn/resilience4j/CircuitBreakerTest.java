package learn.resilience4j;

import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.HALF_OPEN;
import static io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.vavr.control.Try;
import java.io.IOException;
import java.time.Duration;
import java.util.function.Supplier;
import learn.resilience4j.exception.BusinessException;
import learn.resilience4j.exception.OtherBusinessException;
import learn.resilience4j.service.ExternalService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class CircuitBreakerTest extends AbstractCircuitBreakerTest {

    ExternalService externalService = new ExternalService();

    @Test
    @DisplayName("기본 설정. 원격 서비스 정상")
    void t1() {
        // 기본 설정에 이벤트 핸들러만 등록
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a"); // 서킷 브레이커는 이름을 가진다

        Supplier<String> decoratedSupplier = circuitBreaker.decorateSupplier(externalService::success);

        // vavr 라이브러리의 Try를 사용하면 함수형 인터페이스를 사용해 Try-catch 블락을 대체할 수 있다
        Try<String> result = Try.ofSupplier(decoratedSupplier)
                                .recover(throwable -> "Recovery");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.get()).isEqualTo("Success");
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
        assertHealthStatus(circuitBreaker, CLOSED); // CircuitBreaker.State.CLOSED
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1); // 1개 호출 중
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1); // 성공 1개
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0); // 실패 0개
    }

    @Test
    @DisplayName("기본 설정. 원격 서비스 장애")
    void t2() {
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        Supplier<String> decoratedSupplier = circuitBreaker.decorateSupplier(externalService::failure);

        Try<String> result = Try.ofSupplier(decoratedSupplier)
                                .recover(throwable -> {
                                    log.error("{}", throwable.getMessage());
                                    return "Recovery";
                                });
        assertThat(result.isSuccess()).isTrue();
        // 예외 발생했지만 회복되어 성공

        assertThat(result.get()).isEqualTo("Recovery");
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("설정 > 실패 관련")
    void t3() {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                // ~ Sliding
                                //.slidingWindowType(SlidingWindowType.COUNT_BASED) // CLOSED일 때 호출 결과를 기록할 슬라이딩 윈도우 유형. 기본적으로 최근 slidingWindowSize 개수의 호출이 기록되고 집계된다. 기본값: COUNT_BASED
                                //.slidingWindowSize(100) // 슬라이딩 윈도우의 크기. 기본값: 100

                                // ~ Failure
                                .failureRateThreshold(50) // OPEN될 실패율. 기본값: 50
                                .minimumNumberOfCalls(5) // 에러 또는 느린 호출 비율 계산을 위한 최소 요청수. 미달하면 OPEN되지 않음. 기본값: 100
                                .build();
        // 5번 호출되고 실패율 50% 이상이면 OPEN

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        assertCallSuccess(circuitBreaker);
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms

        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 최소 요청수를 충족하지 않으면 실패율 계산을 하지 않음
        // 실패율: 0%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 실패율: 50%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 실패율: 67%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 실패율: 75%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(4);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(3);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // CircuitBreaker 'a' exceeded failure rate threshold. Current failure rate: 80.0
        // CircuitBreaker 'a' changed state from CLOSED to OPEN
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(80.0F);
        assertHealthStatus(circuitBreaker, OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(4);
    }

    @Test
    @DisplayName("설정 > 느린 호출 관련")
    void t4() {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                // ~ Failure
                                .minimumNumberOfCalls(5)

                                // ~ Slow
                                .slowCallRateThreshold(50) // OPEN될 느린 비율. 기본값 100
                                .slowCallDurationThreshold(Duration.ofMillis(2_900)) // 호출 지속시간이 2.9초를 초과할 때 느리다고 판단. 기본값: 1분
                                .build();
        // 5번 호출되고 느린 비율이 50% 이상이면 OPEN

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        assertCallSuccess(circuitBreaker);
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getSlowCallRate()).isEqualTo(-1.0F); // 느린 비율: 0%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(0);

        assertThat(callSlowly(circuitBreaker)).isEqualTo("Slowness");
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 3000 ms
        assertThat(circuitBreaker.getMetrics().getSlowCallRate()).isEqualTo(-1.0F); // 느린 비율: 50%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(0);

        assertCallSuccess(circuitBreaker);
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getSlowCallRate()).isEqualTo(-1.0F); // 느린 비율: 33%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(0);

        assertThat(callSlowly(circuitBreaker)).isEqualTo("Slowness");
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 3002 ms
        assertThat(circuitBreaker.getMetrics().getSlowCallRate()).isEqualTo(-1.0F); // 느린 비율: 50%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(4);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(0);

        assertThat(callSlowly(circuitBreaker)).isEqualTo("Slowness");
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 3001 ms
        // CircuitBreaker 'a' exceeded slow call rate threshold. Current slow call rate: 60.0
        // CircuitBreaker 'a' changed state from CLOSED to OPEN
        assertThat(circuitBreaker.getMetrics().getSlowCallRate()).isEqualTo(60.0F);
        assertHealthStatus(circuitBreaker, OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(5);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSlowFailedCalls()).isEqualTo(0);
    }

    @Test
    @DisplayName("설정 > HALF_OPEN 관련")
    void t5() throws InterruptedException {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                // ~ Failure
                                .minimumNumberOfCalls(5)

                                // ~ HALF_OPEN
                                .automaticTransitionFromOpenToHalfOpenEnabled(true) // 기본적으로 OPEN -> HALF_OPEN으로 자동 전환되지 않는다. 기본값: false
                                .waitDurationInOpenState(Duration.ofSeconds(1)) // OPEN -> HALF_OPEN 전환 대기 시간. 기본값: 1분
                                .permittedNumberOfCallsInHalfOpenState(2) // HALF_OPEN일 때 OPEN/CLOSE 전환 여부를 판단할 호출 횟수. 기본값: 10
                                .build();
        // OPEN되었다가 HALF_OPEN로 전환되는 타임아웃은 10초이고, HALF_OPEN일 때 OPEN/CLOSE 전환 여부를 호출 2번으로 판단하겠다

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        circuitBreaker.transitionToOpenState();
        // CircuitBreaker 'a' changed state from CLOSED to OPEN
        assertHealthStatus(circuitBreaker, OPEN);

        Thread.sleep(1_000);
        // CircuitBreaker 'a' changed state from OPEN to HALF_OPEN
        assertHealthStatus(circuitBreaker, HALF_OPEN);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 실패율: 0%
        assertHealthStatus(circuitBreaker, HALF_OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded an error: 'java.lang.RuntimeException: Server fault'. Elapsed time: 0 ms
        // CircuitBreaker 'a' changed state from HALF_OPEN to OPEN
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(100.0F);
        assertHealthStatus(circuitBreaker, OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(0);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("설정 > Error 관련")
    void t6() {
        CircuitBreakerConfig circuitBreakerConfig =
            CircuitBreakerConfig.custom()
                                // ~ Failure
                                .failureRateThreshold(25)
                                .minimumNumberOfCalls(2)

                                // ~ Error
                                //.recordException(throwable -> false) // 예외를 실패로 기록할지를 평가하는 Predicate. 기본값: throwable -> true (모든 예외를 실패로 기록)
                                .recordExceptions(IOException.class, BusinessException.class) // 실패로 기록할 예외 목록. 이렇게 명시한 경우 다른 예외는 실패율에 영향을 미치지 않게 된다
                                .ignoreExceptions(OtherBusinessException.class) // 무시할 예외 목록. 성공/실패 계산에 영향을 미치지 않음
                                .build();
        // 최소 2번 호출되고 실패율 25% 이상이고 IOException, BusinessException일 때 실패 기록. OtherBusinessException은 성공/실패 계산에 영향을 미치지 않음

        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.custom()
                                                                              .addRegistryEventConsumer(getCircuitBreakerRegistryEventConsumer())
                                                                              .withCircuitBreakerConfig(circuitBreakerConfig)
                                                                              .build();

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("a");

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
        // -> RuntimeException이 발생하여 실패로 기록되지 않음
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(-1.0F); // 실패율: 0%
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        assertCallFailure(circuitBreaker);
        // CircuitBreaker 'a' recorded a successful call. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(0.0F); // 최소 요청수 충족되어 0으로 제대로 표시됨
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        assertThat(Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker, externalService::otherBusinessExceptionFailure))
                      .recover(throwable -> "Recovery")
                      .get()).isEqualTo("Recovery");
        // CircuitBreaker 'a' recorded an error which has been ignored: 'learn.resilience4j.exception.OtherBusinessException'. Elapsed time: 0 ms
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(0.0F); // OtherBusinessException은 실패율에 영향을 미치지 않음
        assertHealthStatus(circuitBreaker, CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(2); // 무시하는 예외 발생으로 수를 세지 않음
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(0);

        assertThat(Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker, externalService::businessExceptionFailure))
                      .recover(throwable -> "Recovery")
                      .get()).isEqualTo("Recovery");
        // CircuitBreaker 'a' recorded an error: 'learn.resilience4j.exception.BusinessException'. Elapsed time: 0 ms
        // CircuitBreaker 'a' exceeded failure rate threshold. Current failure rate: 33.333332
        // CircuitBreaker 'a' changed state from CLOSED to OPEN
        assertThat(circuitBreaker.getMetrics().getFailureRate()).isEqualTo(33.333332F);
        assertHealthStatus(circuitBreaker, OPEN);
        assertThat(circuitBreaker.getMetrics().getNumberOfBufferedCalls()).isEqualTo(3);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    private void assertCallSuccess(CircuitBreaker circuitBreaker) {

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, externalService::success);

        String result = Try.ofSupplier(decoratedSupplier)
                           .recover(throwable -> "Recovery")
                           .get();
        assertThat(result).isEqualTo("Success");
    }

    private void assertCallFailure(CircuitBreaker circuitBreaker) {

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, externalService::failure);

        String result = Try.ofSupplier(decoratedSupplier)
                           .recover(throwable -> "Recovery")
                           .get();
        assertThat(result).isEqualTo("Recovery");
    }

    private String callSlowly(CircuitBreaker circuitBreaker) {

        Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, externalService::slowness);

        return Try.ofSupplier(decoratedSupplier)
                  .recover(throwable -> "Recovery")
                  .get();
    }
}
