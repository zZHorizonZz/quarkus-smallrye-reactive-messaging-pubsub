package io.quarkiverse.google.cloud.pubsub.it;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import io.quarkiverse.google.cloud.pubsub.PubSubConnector;

@ApplicationScoped
public class PriceResource {

    @Inject
    @Connector("smallrye-gcp-pubsub")
    PubSubConnector connector;

    @PostConstruct
    public void init() {
        System.out.println("PriceResource init");
    }

    @Incoming("prices")
    public void incoming(String data) {
        System.out.println("Received: " + data);
    }
}
