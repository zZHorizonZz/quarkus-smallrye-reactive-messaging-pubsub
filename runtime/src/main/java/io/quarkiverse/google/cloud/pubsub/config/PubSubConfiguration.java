package io.quarkiverse.google.cloud.pubsub.config;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

/**
 * Configuration root for Google Cloud Pub/Sub settings.
 * These settings are processed at build time.
 */
@ConfigRoot(name = "google.cloud.pubsub", phase = ConfigPhase.RUN_TIME)
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
     * Flag to enable or disable the use of a local Pub/Sub emulator.
     * If set to true, the local Pub/Sub emulator will be used.
     */
    @ConfigItem(name = "use-emulator", defaultValue = "false")
    public boolean useEmulator;

    /**
     * Host for the local Pub/Sub emulator.
     * This setting is only used when 'useEmulator' is set to true.
     */
    @ConfigItem(name = "emulator-host", defaultValue = "localhost")
    public String emulatorHost;

    /**
     * Port for the local Pub/Sub emulator.
     * This setting is only used when 'useEmulator' is set to true.
     */
    @ConfigItem(name = "emulator-port", defaultValue = "8085")
    public Integer emulatorPort;
}
