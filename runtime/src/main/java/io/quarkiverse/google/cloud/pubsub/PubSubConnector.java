package io.quarkiverse.google.cloud.pubsub;

import java.io.File;
import java.util.concurrent.*;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Destroyed;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.eclipse.microprofile.reactive.messaging.spi.ConnectorFactory;
import org.jboss.logging.Logger;

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;

import io.quarkiverse.google.cloud.pubsub.config.PubSubConfig;
import io.quarkiverse.google.cloud.pubsub.config.PubSubConfiguration;
import io.quarkiverse.google.cloud.pubsub.message.PubSubMessage;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;
import io.smallrye.reactive.messaging.providers.helpers.MultiUtils;

@ApplicationScoped
@Connector(PubSubConnector.CONNECTOR_NAME)
public class PubSubConnector implements InboundConnector, OutboundConnector {

    static final String CONNECTOR_NAME = "smallrye-gcp-pubsub";
    private static final Logger LOGGER = Logger.getLogger(PubSubConnector.class);

    @Inject
    PubSubConfiguration configuration;

    @Inject
    PubSubManager pubSubManager;

    private ExecutorService executorService;

    @PostConstruct
    public void initialize() {
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) final Object context) {
        try {
            executorService.shutdown();
            executorService.awaitTermination(2, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Flow.Publisher<? extends Message<?>> getPublisher(final Config config) {
        final PubSubConfig pubSubConfig = new PubSubConfig(getProjectId(config), getTopic(config),
                configuration.credentialPath.map(File::new).map(File::toPath).orElse(null),
                getSubscription(config), configuration.mockPubSubTopics, configuration.host,
                configuration.port);

        return Multi.createFrom().uni(Uni.createFrom().completionStage(CompletableFuture.supplyAsync(() -> {
            if (isUseAdminClient(config)) {
                LOGGER.info("Admin client is enabled. The GCP Connector is trying to create topics / subscriptions.");
                createTopic(pubSubConfig);
                createSubscription(pubSubConfig);
            }
            return pubSubConfig;
        }, executorService))).onItem()
                .transformToMultiAndConcatenate(cfg -> Multi.createFrom().emitter(new PubSubSource(cfg, pubSubManager)));
    }

    @Override
    public Flow.Subscriber<? extends Message<?>> getSubscriber(final Config config) {
        final PubSubConfig pubSubConfig = new PubSubConfig(getProjectId(config), getTopic(config),
                configuration.credentialPath.map(File::new).map(File::toPath).orElse(null),
                configuration.mockPubSubTopics, configuration.host, configuration.port);

        return MultiUtils.via(m -> m.onItem()
                .transformToUniAndConcatenate(message -> Uni.createFrom().completionStage(CompletableFuture.supplyAsync(() -> {
                    if (isUseAdminClient(config)) {
                        LOGGER.info("Admin client is enabled. The GCP Connector is trying to create topics / subscriptions.");
                        createTopic(pubSubConfig);
                    }
                    return await(pubSubManager.publisher(pubSubConfig).publish(buildMessage(message)));
                }, executorService))));
    }

    private String getProjectId(Config config) {
        return config.getOptionalValue("project-id", String.class)
                .orElse(configuration.projectId);
    }

    boolean isUseAdminClient(Config config) {
        return config.getOptionalValue("use-admin-client", Boolean.class).orElse(true);
    }

    private void createTopic(final PubSubConfig config) {
        final TopicAdminClient topicAdminClient = pubSubManager.topicAdminClient(config);
        final TopicName topicName = TopicName.of(config.getProjectId(), config.getTopic());

        try {
            topicAdminClient.getTopic(topicName);
        } catch (final NotFoundException nf) {
            try {
                var topic = topicAdminClient.createTopic(topicName);
                LOGGER.infof("Topic %s created", topic.getName());
            } catch (final AlreadyExistsException ae) {
                LOGGER.tracef("Topic %s already exists", topicName);
            }
        }
    }

    private void createSubscription(final PubSubConfig config) {
        try (SubscriptionAdminClient subscriptionAdminClient = pubSubManager.subscriptionAdminClient(config)) {
            final SubscriptionName subscriptionName = SubscriptionName.of(config.getProjectId(), config.getSubscription());

            try {
                subscriptionAdminClient.getSubscription(subscriptionName);
            } catch (final NotFoundException e) {
                final PushConfig pushConfig = PushConfig.newBuilder().build();
                final TopicName topicName = TopicName.of(config.getProjectId(), config.getTopic());

                subscriptionAdminClient.createSubscription(subscriptionName, topicName, pushConfig, 0);
                LOGGER.debugf("Subscription %s created", subscriptionName);
            }
        }
    }

    private static String getTopic(final Config config) {
        final String topic = config.getOptionalValue("topic", String.class)
                .orElse(null);
        if (topic != null) {
            return topic;
        }

        return config.getValue(ConnectorFactory.CHANNEL_NAME_ATTRIBUTE, String.class);
    }

    private static String getSubscription(final Config config) {
        return config.getValue("subscription", String.class);
    }

    private static PubsubMessage buildMessage(final Message<?> message) {
        if (message instanceof PubSubMessage) {
            return ((PubSubMessage) message).getMessage();
        } else if (message.getPayload() instanceof PubSubMessage) {
            return ((PubSubMessage) message.getPayload()).getMessage();
        } else if (message.getPayload() instanceof PubsubMessage) {
            return ((PubsubMessage) message.getPayload());
        } else {
            return PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(message.getPayload().toString()))
                    .build();
        }
    }

    private static <T> T await(final Future<T> future) {
        try {
            return future.get();
        } catch (final ExecutionException e) {
            throw new IllegalStateException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
