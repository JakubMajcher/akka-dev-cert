Feature: Flight Booking Past Date Rejection

  Scenario: Reject booking when slotId is in the past
    Given the flight service is running on "http://localhost:9000"

    # 1. Try to book a slot from the past (2020)
    When I POST "/flight/bookings/{slotId}" with slotId "2020-01-01-10" and body:
      """
      {
        "bookingId": "booking-past",
        "aircraftId": "superplane",
        "instructorId": "superteacher",
        "studentId": "alice"
      }
      """
    # 1. Expect 400 Bad Request
    Then the response status should be 400
    And the response body should contain "SlotId must be in the future"
