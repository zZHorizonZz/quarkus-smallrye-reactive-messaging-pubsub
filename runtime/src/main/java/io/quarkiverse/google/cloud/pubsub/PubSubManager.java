package io.quarkiverse.google.cloud.pubsub;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.pubsub.v1.*;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.mutiny.subscription.MultiEmitter;

@ApplicationScoped
public class PubSubManager {

    private static final Logger LOGGER = Logger.getLogger(PubSubManager.class);
    private static final String CLOUD_OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final Map<PubSubConfig, Publisher> publishers = new ConcurrentHashMap<>();
    private final Map<PubSubConfig, TopicAdminClient> topicAdminClients = new ConcurrentHashMap<>();
    private final Map<PubSubConfig, SubscriptionAdminClient> subscriptionAdminClients = new ConcurrentHashMap<>();

    private final List<MultiEmitter<? super Message<?>>> emitters = new CopyOnWriteArrayList<>();
    private final List<ManagedChannel> channels = new CopyOnWriteArrayList<>();

    public Publisher publisher(final PubSubConfig config) {
        return publishers.computeIfAbsent(config, this::buildPublisher);
    }

    public void subscriber(PubSubConfig config, MultiEmitter<? super Message<?>> emitter) {
        final Subscriber subscriber = buildSubscriber(config, new PubSubMessageReceiver(emitter));
        emitter.onTermination(() -> {
            subscriber.stopAsync();
            try {
                subscriber.awaitTerminated(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                // Ignore it.
            }
        });
        subscriber.startAsync();

        emitters.add(emitter);
    }

    public SubscriptionAdminClient subscriptionAdminClient(final PubSubConfig config) {
        return subscriptionAdminClients.computeIfAbsent(config, this::buildSubscriptionAdminClient);
    }

    public TopicAdminClient topicAdminClient(final PubSubConfig config) {
        return topicAdminClients.computeIfAbsent(config, this::buildTopicAdminClient);
    }

    @PreDestroy
    public void destroy() {
        topicAdminClients.values().forEach(PubSubManager::shutdown);
        topicAdminClients.clear();

        subscriptionAdminClients.values().forEach(PubSubManager::shutdown);
        subscriptionAdminClients.clear();

        publishers.values().forEach(publisher -> {
            try {
                publisher.shutdown();
                publisher.awaitTermination(2, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        publishers.clear();

        emitters.forEach(MultiEmitter::complete);
        emitters.clear();

        channels.forEach(channel -> {
            try {
                channel.shutdown();
                channel.awaitTermination(2, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        channels.clear();
    }

    private SubscriptionAdminClient buildSubscriptionAdminClient(final PubSubConfig config) {
        final SubscriptionAdminSettings.Builder subscriptionAdminSettingsBuilder = SubscriptionAdminSettings.newBuilder();

        subscriptionAdminSettingsBuilder.setCredentialsProvider(buildCredentialsProvider(config));
        buildTransportChannelProvider(config).ifPresent(subscriptionAdminSettingsBuilder::setTransportChannelProvider);

        try {
            return SubscriptionAdminClient.create(subscriptionAdminSettingsBuilder.build());
        } catch (final IOException e) {
            LOGGER.error("Unable to build SubscriptionAdminClient", e);
            return null;
        }
    }

    private TopicAdminClient buildTopicAdminClient(final PubSubConfig config) {
        final TopicAdminSettings.Builder topicAdminSettingsBuilder = TopicAdminSettings.newBuilder();

        topicAdminSettingsBuilder.setCredentialsProvider(buildCredentialsProvider(config));
        buildTransportChannelProvider(config).ifPresent(topicAdminSettingsBuilder::setTransportChannelProvider);

        try {
            return TopicAdminClient.create(topicAdminSettingsBuilder.build());
        } catch (final IOException e) {
            LOGGER.error("Unable to build TopicAdminClient", e);
            return null;
        }
    }

    private Publisher buildPublisher(final PubSubConfig config) {
        final ProjectTopicName topicName = ProjectTopicName.of(config.getProjectId(), config.getTopic());

        try {
            final Publisher.Builder publisherBuilder = Publisher.newBuilder(topicName);

            publisherBuilder.setCredentialsProvider(buildCredentialsProvider(config));
            buildTransportChannelProvider(config).ifPresent(publisherBuilder::setChannelProvider);

            return publisherBuilder.build();
        } catch (final IOException e) {
            LOGGER.error("Unable to build Publisher", e);
            return null;
        }
    }

    private Subscriber buildSubscriber(final PubSubConfig config, final PubSubMessageReceiver messageReceiver) {
        final ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(config.getProjectId(),
                config.getSubscription());

        final Subscriber.Builder subscriberBuilder = Subscriber.newBuilder(subscriptionName, messageReceiver);

        subscriberBuilder.setCredentialsProvider(buildCredentialsProvider(config));
        buildTransportChannelProvider(config).ifPresent(subscriberBuilder::setChannelProvider);

        return subscriberBuilder.build();
    }

    private Optional<TransportChannelProvider> buildTransportChannelProvider(final PubSubConfig config) {
        if (config.isMockPubSubTopics()) {
            return Optional.of(FixedTransportChannelProvider.create(GrpcTransportChannel.create(buildChannel(config))));
        }

        return Optional.empty();
    }

    private static CredentialsProvider buildCredentialsProvider(final PubSubConfig config) {
        if (config.isMockPubSubTopics()) {
            LOGGER.info("Mocking of PubSub topics is enabled. No credentials will be used.");
            return NoCredentialsProvider.create();
        }

        Credentials credentials;

        if (config.getCredentialPath() != null) {
            try {
                credentials = ServiceAccountCredentials.fromStream(Files.newInputStream(config.getCredentialPath()));
                LOGGER.info("Credentials path was configured, using credentials from file");
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        } else {
            try {
                credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_OAUTH_SCOPE);
                LOGGER.info("Credentials path was not configured, using default credentials");
                LOGGER.infof("Credentials: %s", credentials.getClass().getName());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        return FixedCredentialsProvider.create(credentials);
    }

    private ManagedChannel buildChannel(final PubSubConfig config) {
        final ManagedChannel channel = ManagedChannelBuilder.forAddress(config.getHost(), config.getPort())
                .usePlaintext()
                .build();
        channels.add(channel);
        return channel;
    }

    private static void shutdown(final BackgroundResource resource) {
        try {
            resource.shutdown();
            resource.awaitTermination(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
