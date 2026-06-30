package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.BookingHandler;
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
public class BookingRouterTest {

    private BookingHandler mockHandler;
    private WebClient webClient;

    @BeforeEach
    void setup(Vertx vertx, VertxTestContext testContext) {
        webClient = WebClient.create(vertx);
        mockHandler = Mockito.mock(BookingHandler.class);

        Router router = BookingRouter.create(vertx, mockHandler);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(TEST_PORT)
                .onComplete(testContext.succeedingThenComplete());
    }

    @Test
    void shouldRouteToCreateBooking(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(201).end();
            return null;
        }).when(mockHandler).createBooking(any());

        webClient.post(TEST_PORT, "localhost", "/")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(201);
                            verify(mockHandler).createBooking(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }

    @Test
    void shouldRouteToCancelBooking(VertxTestContext testContext) {
        doAnswer(invocation -> {
            RoutingContext ctx = invocation.getArgument(0);
            ctx.response().setStatusCode(204).end();
            return null;
        }).when(mockHandler).cancelBooking(any());

        webClient.delete(TEST_PORT, "localhost", "/1")
                .send()
                .onComplete(testContext.succeeding(response -> {
                    testContext.verify(() -> {
                        SoftAssertions.assertSoftly(softly -> {
                            softly.assertThat(response.statusCode()).isEqualTo(204);
                            verify(mockHandler).cancelBooking(any());
                        });
                        testContext.completeNow();
                    });
                }));
    }
}
