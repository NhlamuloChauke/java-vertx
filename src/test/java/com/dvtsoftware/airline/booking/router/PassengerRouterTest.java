package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.PassengerHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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
public class PassengerRouterTest {

    private PassengerHandler mockHandler;
    private WebClient webClient;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        webClient = WebClient.create(vertx);
        mockHandler = Mockito.mock(PassengerHandler.class);

        Router router = PassengerRouter.create(vertx, mockHandler);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(TEST_PORT)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldRouteToCreatePassenger(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(201).end();
            return null;
        }).when(mockHandler).createPassenger(any());

        webClient.post(TEST_PORT, "localhost", "/")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                        });
                        verify(mockHandler).createPassenger(any());
                        testContext.completeNow();
                    });
                }));
    }
}
