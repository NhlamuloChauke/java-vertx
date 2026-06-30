package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.AirlineHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class AirlineRouter {

    public static Router create(Vertx vertx, AirlineHandler airlineHandler) {
        Router router = Router.router(vertx);

        // GET /airlines -> Retrieves all registered airlines
        router.get("/").handler(airlineHandler::getAllAirlines);

        // POST /airlines -> Registers a new airline
        router.post("/").handler(airlineHandler::createAirline);

        return router;
    }
}
