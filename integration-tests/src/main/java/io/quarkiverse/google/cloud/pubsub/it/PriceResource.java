package io.quarkiverse.google.cloud.pubsub.it;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import io.smallrye.reactive.messaging.annotations.Broadcast;

@ApplicationScoped
public class PriceResource {

    private static final double CONVERSION_RATE = 0.88;

    @Incoming("prices")
    @Outgoing("processed-prices")
    @Broadcast
    public double process(int priceInUsd) {
        return priceInUsd * CONVERSION_RATE;
    }
}
