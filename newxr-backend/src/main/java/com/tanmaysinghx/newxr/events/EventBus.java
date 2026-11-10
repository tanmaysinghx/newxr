package com.tanmaysinghx.newxr.events;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publish a {@link DomainEvent}; delivery happens after the surrounding transaction commits. */
@Component
public class EventBus {

    private final ApplicationEventPublisher publisher;

    public EventBus(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
