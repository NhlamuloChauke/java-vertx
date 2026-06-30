package com.dvtsoftware.airline.booking;

import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class MainVerticleTest {

    private WebClient webClient;
    private static final int CONFIG_PORT = 8080;

    @BeforeEach
    void deployVerticle(Vertx vertx, VertxTestContext testContext) {
        webClient = WebClient.create(vertx);
        vertx.deployVerticle(new MainVerticle())
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @Test
    void testAirlinesOperational(VertxTestContext testContext) {
        webClient.get(CONFIG_PORT, "localhost", "/airlines")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            softly.assertThat(response.headers().get("content-type")).contains("application/json");
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testFlightsOperational(VertxTestContext testContext) {
        webClient.get(CONFIG_PORT, "localhost", "/flights/search")
                .addQueryParam("from", "JNB")
                .addQueryParam("to", "CPT")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void testBookingsOperational(VertxTestContext testContext) {
        webClient.get(CONFIG_PORT, "localhost", "/bookings/1")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            // 200 if found, 404 if not found (proves handler/service wiring)
                            softly.assertThat(response.statusCode()).isIn(200, 404);
                        });
                        testContext.completeNow();
                    });
                }));
    }


    @Test
    void testPassengerBookingsOperational(VertxTestContext testContext) {
        webClient.get(CONFIG_PORT, "localhost", "/passengers/1/bookings")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                            softly.assertThat(response.bodyAsJsonArray()).isNotNull();
                        });
                        testContext.completeNow();
                    });
                }));
    }


    @Test
    @DisplayName("Verify global 404 handling")
    void testNotFound(VertxTestContext testContext) {
        webClient.get(CONFIG_PORT, "localhost", "/invalid-path")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(404);
                        });
                        testContext.completeNow();
                    });
                }));
    }
}