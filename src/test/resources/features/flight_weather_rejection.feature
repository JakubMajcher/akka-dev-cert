Feature: Flight Booking Weather Rejection

  Scenario: Reject booking when AI agent detects thunderstorms
    Given the flight service is running on "http://localhost:9000"

    # Setup availability for a stormy day (13th is stormy in our mock tool)
    When I POST "/flight/availability/{slotId}" with slotId "2026-12-13-10" and participantId "alice" and participantType "student"
    And I POST "/flight/availability/{slotId}" with slotId "2026-12-13-10" and participantId "superplane" and participantType "aircraft"
    And I POST "/flight/availability/{slotId}" with slotId "2026-12-13-10" and participantId "superteacher" and participantType "instructor"

    # This should fail with 400 because Agent AI will see "Thunderstorms"
    When I POST "/flight/bookings/{slotId}" with slotId "2026-12-13-10" and body:
      """
      {
        "bookingId": "booking-storm",
        "aircraftId": "superplane",
        "instructorId": "superteacher",
        "studentId": "alice"
      }
      """
    Then the response status should be 400
    And the response body should contain "Flight conditions do not meet requirements"
