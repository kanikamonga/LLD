"""Booking service refactor using Strategy pattern.

Implements:
- BookingStrategy interface
- HotelBookingStrategy, FlightBookingStrategy
- BookingService with strategy registry
- BookingResult dataclass and custom exceptions

Avoids printing inside methods; returns BookingResult and logs via logging.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Any, Dict, Optional, Protocol

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO)


class BookingError(Exception):
    """Base exception for booking failures."""


class InvalidBookingTypeError(BookingError):
    """Raised when an unknown booking type is requested."""


class BookingStrategy(Protocol):
    def book(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        """Perform booking and return a dict with booking details.

        Raise BookingError (or subclass) on failure.
        """


@dataclass
class BookingResult:
    success: bool
    message: str
    data: Optional[Dict[str, Any]] = None


class HotelBookingStrategy:
    def book(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        # Validate required fields
        hotel_id = payload.get("hotel_id")
        nights = payload.get("nights", 1)
        if not hotel_id:
            raise BookingError("hotel_id is required for hotel bookings")
        if nights <= 0:
            raise BookingError("nights must be positive")
        # Replace with real booking calls (DB/API)
        booking_ref = f"HOTEL-{hotel_id}-{abs(hash((hotel_id, nights))) % 10000}"
        logger.debug("Hotel booked: %s for %d nights", hotel_id, nights)
        return {"booking_ref": booking_ref, "hotel_id": hotel_id, "nights": nights}


class FlightBookingStrategy:
    def book(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        # Validate required fields
        flight_no = payload.get("flight_no")
        seats = payload.get("seats", 1)
        if not flight_no:
            raise BookingError("flight_no is required for flight bookings")
        if seats <= 0:
            raise BookingError("seats must be positive")
        # Replace with real booking calls (DB/API)
        booking_ref = f"FLIGHT-{flight_no}-{abs(hash((flight_no, seats))) % 10000}"
        logger.debug("Flight booked: %s seats=%d", flight_no, seats)
        return {"booking_ref": booking_ref, "flight_no": flight_no, "seats": seats}


class BookingService:
    def __init__(self) -> None:
        self._strategies: Dict[str, BookingStrategy] = {}
        # register defaults
        self.register("hotel", HotelBookingStrategy())
        self.register("flight", FlightBookingStrategy())

    def register(self, booking_type: str, strategy: BookingStrategy) -> None:
        if not booking_type:
            raise ValueError("booking_type must be non-empty")
        self._strategies[booking_type] = strategy
        logger.debug("Registered booking strategy for type: %s", booking_type)

    def book(self, booking_type: str, payload: Optional[Dict[str, Any]] = None) -> BookingResult:
        payload = payload or {}
        strategy = self._strategies.get(booking_type)
        if strategy is None:
            logger.warning("Invalid booking type requested: %s", booking_type)
            raise InvalidBookingTypeError(f"Unknown booking type: {booking_type}")

        try:
            data = strategy.book(payload)
            logger.info("Booking successful: type=%s ref=%s", booking_type, data.get("booking_ref"))
            return BookingResult(success=True, message="Booked", data=data)
        except BookingError as e:
            logger.error("Booking failed for type=%s: %s", booking_type, e)
            return BookingResult(success=False, message=str(e), data=None)
        except Exception as e:  # catch unexpected errors and wrap
            logger.exception("Unexpected error during booking")
            return BookingResult(success=False, message="Internal error", data=None)


# Demo usage
if __name__ == "__main__":
    svc = BookingService()

    # Successful hotel booking
    res1 = svc.book("hotel", {"hotel_id": "H100", "nights": 3})
    print(res1)

    # Invalid booking type -> raises InvalidBookingTypeError
    try:
        svc.book("car", {})
    except InvalidBookingTypeError as e:
        print("Expected error:", e)

    # Flight booking with missing data -> returns BookingResult with success=False
    res2 = svc.book("flight", {"seats": 2})
    print(res2)
