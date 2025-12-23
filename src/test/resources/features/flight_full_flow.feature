Feature: Flight Training Scheduler Full Flow

  Scenario: Complete booking and cancellation lifecycle
    Given the flight service is running on "http://localhost:9000"

    # 1. Mark availability for all 3 required participants
    When I POST "/flight/availability/{slotId}" with slotId "2026-12-10-10" and participantId "alice" and participantType "student"
    Then the response status should be 200

    When I POST "/flight/availability/{slotId}" with slotId "2026-12-10-10" and participantId "superplane" and participantType "aircraft"
    Then the response status should be 200

    When I POST "/flight/availability/{slotId}" with slotId "2026-12-10-10" and participantId "superteacher" and participantType "instructor"
    Then the response status should be 200

    # 2. Verify slot internal state shows 3 available participants
    When I GET "/flight/availability/{slotId}" with slotId "2026-12-10-10"
    Then the response status should be 200
    And the response body should contain participant "alice"
    And the response body should contain participant "superplane"
    And the response body should contain participant "superteacher"

    # Verify participants are listed as 'available' in their personal slot views
    When I GET "/flight/slots/{participantId}/{status}" with participantId "alice" and status "available"
    Then eventually the response body should contain "alice"

    When I GET "/flight/slots/{participantId}/{status}" with participantId "superplane" and status "available"
    Then eventually the response body should contain "superplane"

    # 3. Create a booking
    When I POST "/flight/bookings/{slotId}" with slotId "2026-12-10-10" and body:
      """
      {
        "bookingId": "booking4",
        "aircraftId": "superplane",
        "instructorId": "superteacher",
        "studentId": "alice"
      }
      """
    Then the response status should be 201

    # 4. Verify Alice is now "booked" in the view (with retry for eventual consistency)
    When I GET "/flight/slots/{participantId}/{status}" with participantId "alice" and status "booked"
    Then eventually the response status should be 200
    And eventually the response body should contain "booking4"

    # 5. Cancel the booking
    When I DELETE "/flight/bookings/{slotId}/{bookingId}" with slotId "2026-12-10-10" and bookingId "booking4"
    Then the response status should be 200

    # 6. Verify slot state is now empty (NO availability, NO bookings)
    When I GET "/flight/availability/{slotId}" with slotId "2026-12-10-10"
    Then the response status should be 200
    And the response body should be empty of bookings and available

    # 7. Verify participants are no longer "booked" in the view
    When I GET "/flight/slots/{participantId}/{status}" with participantId "alice" and status "booked"
    Then eventually the response status should be 200
    And eventually the response body should contain no slots
