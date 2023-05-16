package io.quarkiverse.quarkus.smallrye.reactive.messaging.pubsub.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class SmallryeReactiveMessagingPubsubProcessor {

    private static final String FEATURE = "smallrye-reactive-messaging-pubsub";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
