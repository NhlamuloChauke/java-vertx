package com.dvtsoftware.airline.booking.router;


import com.dvtsoftware.airline.booking.handler.BookingHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;

public class BookingRouter {
    public static Router create(Vertx vertx, BookingHandler bookingHandler) {
        Router router = Router.router(vertx);

        // POST /bookings -> Book a ticket
        router.post("/").handler(bookingHandler::createBooking);

        // GET /bookings/{id} -> Retrieve booking details
        router.get("/:id").handler(bookingHandler::getBookingById);

        // DELETE /bookings/{id} -> Cancel a booking
        router.delete("/:id").handler(bookingHandler::cancelBooking);

        return router;
    }
}
