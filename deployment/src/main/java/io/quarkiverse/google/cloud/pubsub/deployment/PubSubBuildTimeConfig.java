package io.quarkiverse.google.cloud.pubsub.deployment;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "google.cloud.pubsub", phase = ConfigPhase.BUILD_TIME)
public class PubSubBuildTimeConfig {

    /**
     * Configuration for DevServices. DevServices allows Quarkus to automatically start PubSub in dev and test mode.
     */
    @ConfigItem
    public PubSubDevServiceConfig devservice;
}
