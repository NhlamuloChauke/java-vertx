package com.dvtsoftware.airline.booking.router;

import com.dvtsoftware.airline.booking.handler.PassengerHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class PassengerRouter {

    public static Router create(Vertx vertx, PassengerHandler passengerHandler) {
        Router router = Router.router(vertx);

        // POST /passengers -> Add a new passenger
        router.post("/").handler(passengerHandler::createPassenger);

        return router;
    }
}
