package io.quarkiverse.google.cloud.pubsub.deployment;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class PubSubDevServiceProcessor {

    private static final Logger LOGGER = Logger.getLogger(PubSubDevServiceProcessor.class.getName());

    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile PubSubDevServiceConfig config;

    @BuildStep
    public DevServicesResultBuildItem startPubSub(DockerStatusBuildItem dockerStatusBuildItem,
            PubSubBuildTimeConfig pubSubBuildTimeConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LaunchModeBuildItem launchMode,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig globalDevServicesConfig) {

        if (devService != null) {
            boolean shouldStop = !pubSubBuildTimeConfig.devservice.equals(config);
            if (shouldStop) {
                stopContainer();
            } else {
                return devService.toBuildItem();
            }
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Google Cloud PubSub Dev Services Starting:",
                consoleInstalledBuildItem,
                loggingSetupBuildItem);

        try {
            devService = startContainer(dockerStatusBuildItem, pubSubBuildTimeConfig.devservice,
                    globalDevServicesConfig.timeout);
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        return devService.toBuildItem();
    }

    private DevServicesResultBuildItem.RunningDevService startContainer(DockerStatusBuildItem dockerStatusBuildItem,
            PubSubDevServiceConfig config,
            Optional<Duration> timeout) {

        if (!config.enabled) {
            // explicitly disabled
            LOGGER.debug("Not starting Dev Services for PubSub as it has been disabled in the config");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            LOGGER.warn("Not starting devservice because docker is not available");
            return null;
        }

        PubSubEmulatorContainer pubSubEmulatorContainer = new QuarkusPubSubContainer(
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:380.0.0-emulators")
                        .asCompatibleSubstituteFor("gcr.io/google.com/cloudsdktool/cloud-sdk"),
                8085);

        timeout.ifPresent(pubSubEmulatorContainer::withStartupTimeout);
        pubSubEmulatorContainer.start();

        return new DevServicesResultBuildItem.RunningDevService(PubSubBuildSteps.FEATURE,
                pubSubEmulatorContainer.getContainerId(),
                pubSubEmulatorContainer::close, "pubsub", pubSubEmulatorContainer.getEmulatorEndpoint());
    }

    private void stopContainer() {
        if (devService != null && devService.isOwner()) {
            try {
                devService.close();
            } catch (Throwable e) {
                LOGGER.error("Failed to stop pubsub container", e);
            } finally {
                devService = null;
            }
        }
    }

    private static class QuarkusPubSubContainer extends PubSubEmulatorContainer {

        private final Integer fixedExposedPort;
        private static final int PUBSUB_INTERNAL_PORT = 8085;

        private QuarkusPubSubContainer(DockerImageName dockerImageName, Integer fixedExposedPort) {
            super(dockerImageName);
            this.fixedExposedPort = fixedExposedPort;
        }

        @Override
        public void configure() {
            super.configure();

            if (fixedExposedPort != null) {
                addFixedExposedPort(fixedExposedPort, PUBSUB_INTERNAL_PORT);
            } else {
                addExposedPort(PUBSUB_INTERNAL_PORT);
            }
        }
    }
}
