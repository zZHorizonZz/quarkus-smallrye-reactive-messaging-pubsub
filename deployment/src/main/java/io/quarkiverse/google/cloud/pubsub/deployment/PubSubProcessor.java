package io.quarkiverse.google.cloud.pubsub.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.smallrye.reactivemessaging.deployment.items.ConnectorManagedChannelBuildItem;

public class PubSubProcessor {

    static final DotName INCOMING = DotName.createSimple(org.eclipse.microprofile.reactive.messaging.Incoming.class.getName());
    static final DotName OUTGOING = DotName.createSimple(org.eclipse.microprofile.reactive.messaging.Outgoing.class.getName());
    static final DotName CHANNEL = DotName.createSimple(org.eclipse.microprofile.reactive.messaging.Channel.class.getName());

    @BuildStep
    public void defaultChannelConfiguration(
            LaunchModeBuildItem launchMode,
            //PubSubConfiguration runtimeConfig,
            CombinedIndexBuildItem combinedIndex,
            List<ConnectorManagedChannelBuildItem> channelsManagedByConnectors,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> defaultConfigProducer,
            BuildProducer<GeneratedClassBuildItem> generatedClass,
            BuildProducer<ReflectiveClassBuildItem> reflection) {

        var annotations = combinedIndex.getIndex().getAnnotations(INCOMING);

        System.out.println("annotations: " + annotations);
    }
}
