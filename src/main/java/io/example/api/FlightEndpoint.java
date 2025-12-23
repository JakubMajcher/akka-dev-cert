package io.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Delete;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import akka.javasdk.http.HttpResponses;
import io.example.application.BookingSlotEntity;
import io.example.application.FlightConditionsAgent;
import io.example.application.ParticipantSlotsView;
import io.example.application.ParticipantSlotsView.SlotList;
import io.example.domain.Participant;
import io.example.domain.Participant.ParticipantType;
import io.example.domain.Timeslot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/flight")
public class FlightEndpoint extends AbstractHttpEndpoint {
    private final Logger log = LoggerFactory.getLogger(FlightEndpoint.class);

    private final ComponentClient componentClient;

    public FlightEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    // Creates a new booking. All three identified participants will
    // be considered booked for the given timeslot, if they are all
    // "available" at the time of booking.
    @Post("/bookings/{slotId}")
    public HttpResponse createBooking(String slotId, BookingRequest request) {
        log.info("Creating booking for slot {}: {}", slotId, request);

        if (request == null) throw HttpException.badRequest("request body is required");
        if (request.bookingId() == null || request.bookingId().trim().isEmpty()) throw HttpException.badRequest("bookingId is required");
        if (request.studentId() == null || request.studentId().trim().isEmpty()) throw HttpException.badRequest("studentId is required");
        if (request.aircraftId() == null || request.aircraftId().trim().isEmpty()) throw HttpException.badRequest("aircraftId is required");
        if (request.instructorId() == null || request.instructorId().trim().isEmpty()) throw HttpException.badRequest("instructorId is required");

        log.info("Consulting AI Agent for flight conditions in slot {}", slotId);

        String now = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        if (slotId.compareTo(now) <= 0) {
            throw HttpException.badRequest("Cannot book a slot in the past or present. SlotId must be in the future.");
        }

        FlightConditionsAgent.ConditionsReport report = componentClient
                .forAgent()
                .inSession(slotId)
                .method(FlightConditionsAgent::query)
                .invoke(slotId);

        if (report == null || report.meetsRequirements() == null || !report.meetsRequirements()) {
            log.warn("Booking rejected due to flight conditions in slot {}: {}", slotId, report);
            throw HttpException.badRequest("Flight conditions do not meet requirements for this timeslot.");
        }

        log.info("Flight conditions approved for slot {}. Proceeding with booking.", slotId);
        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::bookSlot)
                .invoke(new BookingSlotEntity.Command.BookReservation(
                        request.studentId(),
                        request.aircraftId(),
                        request.instructorId(),
                        request.bookingId()));

        return HttpResponses.created();
    }

    // Cancels an existing booking. Note that both the slot
    // ID and the booking ID are required.
    @Delete("/bookings/{slotId}/{bookingId}")
    public HttpResponse cancelBooking(String slotId, String bookingId) {
        log.info("Canceling bookingId {} by slotId {}", bookingId, slotId);

        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::cancelBooking)
                .invoke(bookingId);

        return HttpResponses.ok();
    }

    // Retrieves all slots in which a given participant has the supplied status.
    // Used to retrieve bookings and slots in which the participant is available
    @Get("/slots/{participantId}/{status}")
    public SlotList slotsByStatus(String participantId, String status) {
        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();

        log.info("Getting availability for participantId {} by status {}", participantId, normalizedStatus);
        return componentClient
                .forView()
                .method(ParticipantSlotsView::getSlotsByParticipantAndStatus)
                .invoke(new ParticipantSlotsView.ParticipantStatusInput(participantId, normalizedStatus));
    }

    // Returns the internal availability state for a given slot
    @Get("/availability/{slotId}")
    public Timeslot getSlot(String slotId) {
        log.info("Getting availability for slot {}", slotId);
        return componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::getSlot)
                .invoke();
    }

    // Indicates that the supplied participant is available for booking
    // within the indicated time slot
    @Post("/availability/{slotId}")
    public HttpResponse markAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;

        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }

        log.info("Marking timeslot available for entity {}. It's for {} who is {}", slotId, request.participantId(), participantType);
        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::markSlotAvailable)
                .invoke(new BookingSlotEntity.Command.MarkSlotAvailable(
                        new Participant(request.participantId(), participantType)));

        return HttpResponses.ok();
    }

    // Unmarks a slot as available for the given participant.
    @Delete("/availability/{slotId}")
    public HttpResponse unmarkAvailable(String slotId, AvailabilityRequest request) {
        ParticipantType participantType;
        try {
            participantType = ParticipantType.valueOf(request.participantType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad participant type {}", request.participantType());
            throw HttpException.badRequest("invalid participant type");
        }
        componentClient
                .forEventSourcedEntity(slotId)
                .method(BookingSlotEntity::unmarkSlotAvailable)
                .invoke(new BookingSlotEntity.Command.UnmarkSlotAvailable(
                        new Participant(request.participantId(), participantType)));

        return HttpResponses.ok();
    }

    // Public API representation of a booking request
    public record BookingRequest(
            String studentId, String aircraftId, String instructorId, String bookingId) {
    }

    // Public API representation of an availability mark/unmark request
    public record AvailabilityRequest(String participantId, String participantType) {
    }
}
