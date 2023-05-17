package io.quarkiverse.google.cloud.pubsub.it;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class PriceResourceTest {

    @Test
    public void testPriceEndpoint() {
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
