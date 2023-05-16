package io.quarkiverse.quarkus.smallrye.reactive.messaging.pubsub;

import jakarta.inject.Singleton;

import io.quarkus.arc.Unremovable;

@Singleton
@Unremovable
public class PubSubConfigHolder {

    private PubSubConfiguration config;

    public PubSubConfiguration getConfig() {
        return config;
    }

    public PubSubConfigHolder setConfig(PubSubConfiguration config) {
        this.config = config;
        return this;
    }
}
