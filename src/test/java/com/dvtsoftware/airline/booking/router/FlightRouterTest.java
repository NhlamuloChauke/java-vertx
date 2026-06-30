package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.FlightHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import static com.dvtsoftware.airline.booking.utils.Constant.TEST_PORT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(VertxExtension.class)
public class FlightRouterTest {

    private FlightHandler mockHandler;
    private WebClient webClient;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        webClient = WebClient.create(vertx);
        mockHandler = Mockito.mock(FlightHandler.class);

        Router router = FlightRouter.create(vertx, mockHandler);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(TEST_PORT)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldRouteToSearch(VertxTestContext testContext) {
        doAnswer(invocation -> {
            io.vertx.ext.web.RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(200).end();
            return null;
        }).when(mockHandler).searchFlights(any());

        webClient.get(TEST_PORT, "localhost", "/search")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                        });
                        verify(mockHandler).searchFlights(any());
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToCreate(VertxTestContext testContext) {
        doAnswer(invocation -> {
            io.vertx.ext.web.RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(201).end();
            return null;
        }).when(mockHandler).createFlight(any());

        webClient.post(TEST_PORT, "localhost", "/")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                        });
                        verify(mockHandler).createFlight(any());
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToGetById(VertxTestContext testContext) {
        doAnswer(invocation -> {
            io.vertx.ext.web.RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(200).end();
            return null;
        }).when(mockHandler).getFlightById(any());

        webClient.get(TEST_PORT, "localhost", "/123")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(200);
                        });
                        verify(mockHandler).getFlightById(any());
                        testContext.completeNow();
                    });
                }));
    }
}
