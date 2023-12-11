package learn.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public abstract class AbstractCircuitBreakerTest {

    private RegistryEventConsumer<CircuitBreaker> circuitBreakerRegistryEventConsumer = new RegistryEventConsumer<>() {

        @Override
        public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
            entryAddedEvent.getAddedEntry().getEventPublisher().onEvent(event -> log.info("{}", event));
        }

        @Override
        public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemoveEvent) {
        }

        @Override
        public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
        }
    };

    private RegistryEventConsumer<Retry> retryRegistryEventConsumer = new RegistryEventConsumer<>() {


        @Override
        public void onEntryAddedEvent(EntryAddedEvent<Retry> entryAddedEvent) {
            entryAddedEvent.getAddedEntry().getEventPublisher().onEvent(event -> log.info("{}", event));
        }

        @Override
        public void onEntryRemovedEvent(EntryRemovedEvent<Retry> entryRemoveEvent) {

        }

        @Override
        public void onEntryReplacedEvent(EntryReplacedEvent<Retry> entryReplacedEvent) {

        }
    };

    protected void assertHealthStatus(CircuitBreaker circuitBreaker, CircuitBreaker.State state) {
        assertThat(circuitBreaker.getState()).isEqualTo(state);
    }
}
