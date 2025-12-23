Feature: Flight Booking Validation

  Scenario: Reject booking when a participant is missing availability
    Given the flight service is running on "http://localhost:9000"

    # 1. Only mark Student and Aircraft (Instructor is missing!)
    When I POST "/flight/availability/{slotId}" with slotId "2026-12-25-10" and participantId "alice" and participantType "student"
    And I POST "/flight/availability/{slotId}" with slotId "2026-12-25-10" and participantId "superplane" and participantType "aircraft"

    # 2. Try to book - should fail with 400 or 500 (depending on implementation)
    # BookingSlotEntity.bookSlot returns effects().error("slot is not bookable")
    When I POST "/flight/bookings/{slotId}" with slotId "2026-12-25-10" and body:
      """
      {
        "bookingId": "booking-invalid",
        "aircraftId": "superplane",
        "instructorId": "missing-teacher",
        "studentId": "alice"
      }
      """
    Then the response status should be 400

  Scenario: Participant availability remains intact after a rejected booking
    Given the flight service is running on "http://localhost:9000"
    And I POST "/flight/availability/{slotId}" with slotId "2026-12-25-15" and participantId "bob" and participantType "student"

        # Attempt a failing booking (missing others)
    When I POST "/flight/bookings/{slotId}" with slotId "2026-12-25-15" and body:
          """
          {
            "bookingId": "booking-failed",
            "aircraftId": "missing-plane",
            "instructorId": "missing-teacher",
            "studentId": "bob"
          }
          """
    Then the response status should be 400

        # Critical verification of the Participant View
    When I GET "/flight/slots/{participantId}/available" with participantId "bob"
    Then eventually the response body should contain "bob"
    And eventually the response body should contain "available"
    And eventually the response body should contain "2026-12-25-15"
