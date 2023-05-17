package io.quarkiverse.google.cloud.pubsub.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuoteResourceTest {

    @Test
    public void testPriceEndpoint() {
        given()
                .when().post("/quotes/request")
                .then()
                .statusCode(200)
                .body(notNullValue());

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
