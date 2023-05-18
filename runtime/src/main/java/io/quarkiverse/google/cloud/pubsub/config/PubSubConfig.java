package io.quarkiverse.google.cloud.pubsub.config;

import java.nio.file.Path;
import java.util.Objects;

public class PubSubConfig {
    // always required
    private final String projectId;
    private final String topic;

    private final Path credentialPath;

    private final String subscription;

    private final boolean emulatorEnabled;
    private final String emulatorHost;
    private final Integer emulatorPort;

    public PubSubConfig(final String projectId, final String topic, final Path credentialPath, final boolean emulatorEnabled,
            final String emulatorHost, final Integer emulatorPort) {
        this.projectId = Objects.requireNonNull(projectId);
        this.topic = Objects.requireNonNull(topic);
        this.credentialPath = credentialPath;
        this.subscription = null;
        this.emulatorEnabled = emulatorEnabled;
        this.emulatorHost = emulatorHost;
        this.emulatorPort = emulatorPort;
    }

    public PubSubConfig(final String projectId, final String topic, final Path credentialPath, final String subscription,
            final boolean emulatorEnabled, final String emulatorHost, final Integer emulatorPort) {
        this.projectId = Objects.requireNonNull(projectId);
        this.topic = Objects.requireNonNull(topic);
        this.credentialPath = credentialPath;
        this.subscription = subscription;
        this.emulatorEnabled = emulatorEnabled;
        this.emulatorHost = emulatorHost;
        this.emulatorPort = emulatorPort;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getTopic() {
        return topic;
    }

    public Path getCredentialPath() {
        return credentialPath;
    }

    public String getSubscription() {
        return subscription;
    }

    public boolean isEmulatorEnabled() {
        return emulatorEnabled;
    }

    public String getEmulatorHost() {
        return emulatorHost;
    }

    public Integer getEmulatorPort() {
        return emulatorPort;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PubSubConfig that = (PubSubConfig) o;
        return Objects.equals(projectId, that.projectId) &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(credentialPath, that.credentialPath) &&
                Objects.equals(subscription, that.subscription) &&
                emulatorEnabled == that.emulatorEnabled &&
                Objects.equals(emulatorHost, that.emulatorHost) &&
                Objects.equals(emulatorPort, that.emulatorPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, topic, credentialPath, subscription, emulatorEnabled, emulatorHost, emulatorPort);
    }

    @Override
    public String toString() {
        return "PubSubConfig[" +
                "projectId=" + projectId +
                ", topic=" + topic +
                ", credentialPath=" + credentialPath +
                ", subscription=" + subscription +
                ", useEmulator=" + emulatorEnabled +
                ", emulatorHost=" + emulatorHost +
                ", emulatorPort=" + emulatorPort +
                ']';
    }
}
