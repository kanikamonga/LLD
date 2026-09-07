BookingService refactor

This module refactors the simple booking example into a Strategy-based design.

Files:
- booking.py: implementation with BookingService, strategies, and demo

How to run:
- python3 src/LLD/booking/booking.py

Design notes:
- Strategy pattern: add new booking types by implementing BookingStrategy and registering
- Errors: domain errors raise BookingError; unknown types raise InvalidBookingTypeError
- Methods return BookingResult instead of printing; callers decide how to present results
- Logging used for diagnostics
