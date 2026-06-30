package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.FlightHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
public class FlightRouter {

    public static Router create(Vertx vertx, FlightHandler flightHandler) {
        Router router = Router.router(vertx);

        // GET /flights/search -> Matches "search" specifically
        router.get("/search").handler(flightHandler::searchFlights);

        // POST /flights -> Matches root of sub-router
        router.post("/").handler(flightHandler::createFlight);

        // GET /flights/:id -> Matches any value after the slash (captured as 'id')
        router.get("/:id").handler(flightHandler::getFlightById);

        return router;
    }
}