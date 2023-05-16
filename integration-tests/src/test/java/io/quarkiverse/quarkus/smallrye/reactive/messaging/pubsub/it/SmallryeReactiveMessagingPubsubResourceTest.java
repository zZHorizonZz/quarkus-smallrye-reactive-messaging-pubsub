package io.quarkiverse.quarkus.smallrye.reactive.messaging.pubsub.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SmallryeReactiveMessagingPubsubResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/smallrye-reactive-messaging-pubsub")
                .then()
                .statusCode(200)
                .body(is("Hello smallrye-reactive-messaging-pubsub"));
    }
}
