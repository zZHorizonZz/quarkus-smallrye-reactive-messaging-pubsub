package io.quarkiverse.google.cloud.pubsub;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import com.google.api.gax.core.BackgroundResource;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.*;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.google.cloud.pubsub.config.PubSubConfig;
import io.quarkiverse.google.cloud.pubsub.message.PubSubMessageReceiver;
import io.smallrye.mutiny.subscription.MultiEmitter;

/**
 * PubSubManager manages the lifecycle of Google Cloud PubSub resources such as publishers, topic and subscription clients.
 * It ensures the proper initialization and shutdown of these resources.
 */
@ApplicationScoped
public class PubSubManager {
    private static final Logger LOGGER = Logger.getLogger(PubSubManager.class);

    // Maps for storing created publishers, topic and subscription clients.
    private final Map<PubSubConfig, Publisher> publishers = new ConcurrentHashMap<>();
    private final Map<PubSubConfig, TopicAdminClient> topicAdminClients = new ConcurrentHashMap<>();
    private final Map<PubSubConfig, SubscriptionAdminClient> subscriptionAdminClients = new ConcurrentHashMap<>();

    // List for storing emitters and channels.
    private final List<MultiEmitter<? super Message<?>>> emitters = new CopyOnWriteArrayList<>();
    private final List<ManagedChannel> channels = new CopyOnWriteArrayList<>();

    @Inject
    CredentialsProvider credentials;

    /**
     * Creates or returns an existing Publisher associated with the given PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return Publisher associated with the configuration.
     */
    public Publisher publisher(final PubSubConfig config) {
        return publishers.computeIfAbsent(config, this::buildPublisher);
    }

    /**
     * Creates and starts a Subscriber associated with the given PubSubConfig and MultiEmitter.
     *
     * @param config PubSub configuration.
     * @param emitter Emitter associated with the Subscriber.
     */
    public void subscriber(PubSubConfig config, MultiEmitter<? super Message<?>> emitter) {
        final Subscriber subscriber = buildSubscriber(config, new PubSubMessageReceiver(emitter));
        emitter.onTermination(() -> {
            try {
                subscriber.stopAsync().awaitTerminated(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        });
        subscriber.startAsync();
        emitters.add(emitter);
    }

    /**
     * Creates or returns an existing SubscriptionAdminClient associated with the given PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return SubscriptionAdminClient associated with the configuration.
     */
    public SubscriptionAdminClient subscriptionAdminClient(final PubSubConfig config) {
        return subscriptionAdminClients.computeIfAbsent(config, this::buildSubscriptionAdminClient);
    }

    /**
     * Creates or returns an existing TopicAdminClient associated with the given PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return TopicAdminClient associated with the configuration.
     */
    public TopicAdminClient topicAdminClient(final PubSubConfig config) {
        return topicAdminClients.computeIfAbsent(config, this::buildTopicAdminClient);
    }

    /**
     * Performs cleanup when the application is being shut down.
     * It gracefully shuts down all the resources and clears the collections.
     */
    @PreDestroy
    public void destroy() {
        topicAdminClients.values().forEach(this::shutdownResource);
        topicAdminClients.clear();

        subscriptionAdminClients.values().forEach(this::shutdownResource);
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

    /**
     * Shuts down the given BackgroundResource.
     *
     * @param resource The BackgroundResource to be shut down.
     */
    private <T extends BackgroundResource> void shutdownResource(T resource) {
        try {
            resource.shutdown();
            resource.awaitTermination(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds a SubscriptionAdminClient using the provided PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return SubscriptionAdminClient instance.
     */
    private SubscriptionAdminClient buildSubscriptionAdminClient(final PubSubConfig config) {
        final SubscriptionAdminSettings.Builder subscriptionAdminSettingsBuilder = SubscriptionAdminSettings.newBuilder();

        subscriptionAdminSettingsBuilder.setCredentialsProvider(credentials);
        buildTransportChannelProvider(config).ifPresent(subscriptionAdminSettingsBuilder::setTransportChannelProvider);

        try {
            return SubscriptionAdminClient.create(subscriptionAdminSettingsBuilder.build());
        } catch (final IOException e) {
            LOGGER.error("Unable to build SubscriptionAdminClient", e);
            return null;
        }
    }

    /**
     * Builds a TopicAdminClient using the provided PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return TopicAdminClient instance.
     */
    private TopicAdminClient buildTopicAdminClient(final PubSubConfig config) {
        final TopicAdminSettings.Builder topicAdminSettingsBuilder = TopicAdminSettings.newBuilder();

        topicAdminSettingsBuilder.setCredentialsProvider(credentials);
        buildTransportChannelProvider(config).ifPresent(topicAdminSettingsBuilder::setTransportChannelProvider);

        try {
            return TopicAdminClient.create(topicAdminSettingsBuilder.build());
        } catch (final IOException e) {
            LOGGER.error("Unable to build TopicAdminClient", e);
            return null;
        }
    }

    /**
     * Builds a Publisher using the provided PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return Publisher instance.
     */
    private Publisher buildPublisher(final PubSubConfig config) {
        final ProjectTopicName topicName = ProjectTopicName.of(config.getProjectId(), config.getTopic());

        try {
            final Publisher.Builder publisherBuilder = Publisher.newBuilder(topicName);

            publisherBuilder.setCredentialsProvider(credentials);
            buildTransportChannelProvider(config).ifPresent(publisherBuilder::setChannelProvider);

            return publisherBuilder.build();
        } catch (final IOException e) {
            LOGGER.error("Unable to build Publisher", e);
            return null;
        }
    }

    /**
     * Builds a Subscriber using the provided PubSubConfig and PubSubMessageReceiver.
     *
     * @param config PubSub configuration.
     * @param messageReceiver Message receiver for handling received messages.
     * @return Subscriber instance.
     */
    private Subscriber buildSubscriber(final PubSubConfig config, final PubSubMessageReceiver messageReceiver) {
        final ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(config.getProjectId(),
                config.getSubscription());

        final Subscriber.Builder subscriberBuilder = Subscriber.newBuilder(subscriptionName, messageReceiver);

        subscriberBuilder.setCredentialsProvider(credentials);
        buildTransportChannelProvider(config).ifPresent(subscriberBuilder::setChannelProvider);

        return subscriberBuilder.build();
    }

    /**
     * Builds a TransportChannelProvider based on the provided PubSubConfig.
     *
     * @param config PubSub configuration.
     * @return An Optional containing the TransportChannelProvider, or an empty Optional if not applicable.
     */
    private Optional<TransportChannelProvider> buildTransportChannelProvider(final PubSubConfig config) {
        if (config.isMockPubSubTopics()) {
            return Optional.of(FixedTransportChannelProvider.create(GrpcTransportChannel.create(buildChannel(config))));
        }
        return Optional.empty();
    }

    /**
     * Builds a ManagedChannel using the provided PubSubConfig and adds it to the list of channels.
     *
     * @param config PubSub configuration.
     * @return ManagedChannel instance.
     */
    private ManagedChannel buildChannel(final PubSubConfig config) {
        final ManagedChannel channel = ManagedChannelBuilder.forAddress(config.getHost(), config.getPort())
                .usePlaintext()
                .build();
        channels.add(channel);
        return channel;
    }
}
