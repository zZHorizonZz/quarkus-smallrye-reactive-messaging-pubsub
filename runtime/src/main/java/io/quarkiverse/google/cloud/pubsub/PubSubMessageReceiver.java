package io.quarkiverse.google.cloud.pubsub;

import java.util.Objects;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.jboss.logging.Logger;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

import io.smallrye.mutiny.subscription.MultiEmitter;

public class PubSubMessageReceiver implements MessageReceiver {

    private static final Logger LOGGER = Logger.getLogger(PubSubMessageReceiver.class);

    private final MultiEmitter<? super Message<?>> emitter;

    public PubSubMessageReceiver(MultiEmitter<? super Message<?>> emitter) {
        this.emitter = Objects.requireNonNull(emitter);
    }

    @Override
    public void receiveMessage(final PubsubMessage message, final AckReplyConsumer ackReplyConsumer) {
        LOGGER.debugf("Received pub/sub message %s", message);
        emitter.emit(new PubSubMessage(message, ackReplyConsumer));
    }

}
