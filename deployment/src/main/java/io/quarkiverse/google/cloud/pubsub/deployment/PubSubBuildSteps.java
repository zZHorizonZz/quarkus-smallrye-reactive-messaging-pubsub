package io.quarkiverse.google.cloud.pubsub.deployment;

import io.quarkiverse.google.cloud.pubsub.PubSubConnector;
import io.quarkiverse.google.cloud.pubsub.PubSubCredentialsProducer;
import io.quarkiverse.google.cloud.pubsub.PubSubManager;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class PubSubBuildSteps {

    private static final String FEATURE = "smallrye-reactive-messaging-pubsub";

    @BuildStep
    public FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void setupPubSub(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder().setUnremovable();

        builder.addBeanClass(PubSubConnector.class);
        builder.addBeanClass(PubSubManager.class);
        builder.addBeanClass(PubSubCredentialsProducer.class);

        additionalBeans.produce(builder.build());
    }
}
