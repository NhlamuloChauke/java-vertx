package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import com.dvtsoftware.airline.booking.handler.BookingHandler;
import com.dvtsoftware.airline.booking.handler.FlightHandler;
import com.dvtsoftware.airline.booking.handler.PassengerHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;

public class WebRouter {

    public static Router create(Vertx vertx,
                                AirlineHandler airlineHandler,
                                FlightHandler flightHandler,
                                PassengerHandler passengerHandler,
                                BookingHandler bookingHandler) {
        Router router = Router.router(vertx);

        // API routes
        router.route("/airlines*").subRouter(AirlineRouter.create(vertx, airlineHandler));
        router.route("/flights*").subRouter(FlightRouter.create(vertx, flightHandler));
        router.route("/bookings*").subRouter(BookingRouter.create(vertx, bookingHandler));

        Router passengerRouter = PassengerRouter.create(vertx, passengerHandler);
        passengerRouter.get("/:id/bookings").handler(bookingHandler::getBookingsByPassenger);
        router.route("/passengers*").subRouter(passengerRouter);

        // Serve openapi.yaml and swagger.html from the classpath webroot/ directory
        router.route("/docs/*").handler(StaticHandler.create("webroot"));

        // Swagger UI assets served from the swagger-ui webjar on the classpath
        router.route("/swagger-ui/*").handler(
                StaticHandler.create("META-INF/resources/webjars/swagger-ui/5.17.14"));

        // /swagger → custom Swagger UI page (swagger.html points url to /docs/openapi.yaml)
        router.get("/swagger").handler(ctx -> ctx.redirect("/docs/swagger.html"));

        return router;
    }
}
