package io.quarkiverse.quarkus.smallrye.reactive.messaging.pubsub;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 * Configuration root for Google Cloud Pub/Sub settings.
 * These settings are processed at build time.
 */
@ConfigRoot(name = "google.cloud.pubsub", phase = ConfigPhase.BUILD_TIME)
public class PubSubConfiguration {

    /**
     * The Project ID for Google Cloud Pub/Sub.
     */
    @ConfigItem(name = "project-id")
    public String projectId;

    /**
     * The path to the Google Cloud credentials file.
     */
    @ConfigItem(name = "credential-path")
    public Optional<String> credentialPath;

    /**
     * Flag to enable or disable mocking of Pub/Sub topics.
     * If set to true, Pub/Sub topics will be mocked.
     */
    @ConfigItem(name = "mock-pubsub-topics")
    public boolean mockPubSubTopics;

    /**
     * Host for the mocked Pub/Sub service.
     * This setting is only used when 'mockPubSubTopics' is set to true.
     */
    @ConfigItem(name = "mock-pubsub-host")
    public Optional<String> host;

    /**
     * Port for the mocked Pub/Sub service.
     * This setting is only used when 'mockPubSubTopics' is set to true.
     */
    @ConfigItem(name = "mock-pubsub-port")
    public Optional<Integer> port;
}
