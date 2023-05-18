package io.quarkiverse.google.cloud.pubsub;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import io.quarkiverse.google.cloud.pubsub.config.PubSubConfiguration;
import io.quarkus.arc.DefaultBean;

@Singleton
public class PubSubCredentialsProducer {

    private final static String CLOUD_OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private final static Logger LOGGER = Logger.getLogger(PubSubCredentialsProducer.class);

    @Inject
    PubSubConfiguration config;

    @Produces
    @Singleton
    @DefaultBean
    public CredentialsProvider credentials() {
        if (config.useEmulator) {
            LOGGER.info("Mocking of PubSub topics is enabled. No credentials will be used.");
            return NoCredentialsProvider.create();
        }

        Credentials credentials;
        try {
            if (config.credentialPath.isPresent()) {
                Path credentialPath = new File(config.credentialPath.get()).toPath();
                credentials = ServiceAccountCredentials.fromStream(Files.newInputStream(credentialPath));
                LOGGER.info("Credentials path was configured, using credentials from file");
            } else {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_OAUTH_SCOPE);
                LOGGER.info("Credentials path was not configured, using default credentials");
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return FixedCredentialsProvider.create(credentials);
    }
}
