package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.example.domain.BookingEvent;
import io.example.domain.Participant;
import io.example.domain.Timeslot;
import java.util.HashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.example.domain.Participant.ParticipantType.AIRCRAFT;
import static io.example.domain.Participant.ParticipantType.INSTRUCTOR;
import static io.example.domain.Participant.ParticipantType.STUDENT;

@Component(id = "booking-slot")
public class BookingSlotEntity extends EventSourcedEntity<Timeslot, BookingEvent> {

    private final String entityId;
    private static final Logger logger = LoggerFactory.getLogger(BookingSlotEntity.class);

    public BookingSlotEntity(EventSourcedEntityContext context) {
        this.entityId = context.entityId();
    }

    public Effect<Done> markSlotAvailable(Command.MarkSlotAvailable cmd) {
        if (cmd == null || cmd.participant() == null) {
            return effects().error("participant is required");
        }
        if (cmd.participant().id() == null || cmd.participant().id().trim().isEmpty()) {
            return effects().error("participantId is required");
        }
        if (cmd.participant().participantType() == null) {
            return effects().error("participantType is required");
        }

        Timeslot state = currentState();

        if (state != null && state.isWaiting(cmd.participant().id(), cmd.participant().participantType())) {
            return effects().reply(Done.done());
        }

        BookingEvent.ParticipantMarkedAvailable event =
                new BookingEvent.ParticipantMarkedAvailable(
                        entityId,
                        cmd.participant().id(),
                        cmd.participant().participantType());

        logger.info(
                "Marking slot {} available for participant {} ({})",
                entityId,
                cmd.participant().id(),
                cmd.participant().participantType());

        return effects().persist(event).thenReply(__ -> Done.done());
    }

    public Effect<Done> unmarkSlotAvailable(Command.UnmarkSlotAvailable cmd) {
        if (cmd == null || cmd.participant() == null) {
            return effects().error("participant is required");
        }
        if (cmd.participant().id() == null || cmd.participant().id().trim().isEmpty()) {
            return effects().error("participantId is required");
        }
        if (cmd.participant().participantType() == null) {
            return effects().error("participantType is required");
        }

        Timeslot state = currentState();

        // Idempotency: if not available, do nothing
        if (state == null || !state.isWaiting(cmd.participant().id(), cmd.participant().participantType())) {
            return effects().reply(Done.done());
        }

        BookingEvent.ParticipantUnmarkedAvailable event =
                new BookingEvent.ParticipantUnmarkedAvailable(
                        entityId,
                        cmd.participant().id(),
                        cmd.participant().participantType());

        logger.info(
                "Unmarking slot {} available for participant {} ({})",
                entityId,
                cmd.participant().id(),
                cmd.participant().participantType());

        return effects().persist(event).thenReply(__ -> Done.done());
    }

    // NOTE: booking a slot should produce 3
    // `ParticipantBooked` events
    public Effect<Done> bookSlot(Command.BookReservation cmd) {
        if (cmd == null) return effects().error("booking request is required");
        if (cmd.bookingId() == null || cmd.bookingId().trim().isEmpty()) return effects().error("bookingId is required");

        Timeslot state = currentState();

        // Idempotency: if booking already exists, OK
        if (state != null && !state.findBooking(cmd.bookingId()).isEmpty()) {
            return effects().reply(Done.done());
        }

        // Must have all 3 participants available
        if (state == null || !state.isBookable(cmd.studentId(), cmd.aircraftId(), cmd.instructorId())) {
            return effects().error("slot is not bookable");
        }

        List<BookingEvent> events = List.of(
                new BookingEvent.ParticipantBooked(entityId, cmd.studentId(), STUDENT, cmd.bookingId()),
                new BookingEvent.ParticipantBooked(entityId, cmd.aircraftId(), AIRCRAFT, cmd.bookingId()),
                new BookingEvent.ParticipantBooked(entityId, cmd.instructorId(), INSTRUCTOR, cmd.bookingId())
        );

        logger.info("Booking slot {} with bookingId {}", entityId, cmd.bookingId());
        return effects().persistAll(events).thenReply(__ -> Done.done());
    }

    // NOTE: canceling a booking should produce 3
    // `ParticipantCanceled` events
    public Effect<Done> cancelBooking(String bookingId) {
        if (bookingId == null || bookingId.trim().isEmpty()) {
            return effects().error("bookingId is required");
        }

        Timeslot state = currentState();
        var bookings = state.findBooking(bookingId);

        // Idempotency: if booking not found, assume it's already canceled
        if (bookings.isEmpty()) {
            logger.warn("Booking {} not found in slot {}, assuming already canceled", bookingId, entityId);
            return effects().reply(Done.done());
        }

        // Map the 3 participants of this booking to 3 ParticipantCanceled events
        List<BookingEvent> events = bookings.stream()
                .map(b -> new BookingEvent.ParticipantCanceled(
                        entityId,
                        b.participant().id(),
                        b.participant().participantType(),
                        bookingId))
                .map(e -> (BookingEvent) e)
                .toList();

        logger.info("Canceling booking {} in slot {}, emitting {} events", bookingId, entityId, events.size());

        return effects().persistAll(events).thenReply(__ -> Done.done());
    }

    public ReadOnlyEffect<Timeslot> getSlot() {
        return effects().reply(currentState());
    }

    @Override
    public Timeslot emptyState() {
        // IMPORTANT: mutable sets (Timeslot mutates them)
        return new Timeslot(new HashSet<>(), new HashSet<>());
    }

    @Override
    public Timeslot applyEvent(BookingEvent event) {
        logger.info("Applying event {} to slot {}", event, entityId);

        Timeslot state = currentState();

        if (event instanceof BookingEvent.ParticipantMarkedAvailable e) {
            return state.reserve(e);
        } else if (event instanceof BookingEvent.ParticipantUnmarkedAvailable e) {
            return state.unreserve(e);
        } else if (event instanceof BookingEvent.ParticipantBooked e) {
            return state.book(e);
        } else if (event instanceof BookingEvent.ParticipantCanceled e) {
            return state.cancelBooking(e.bookingId());
        }

        return state;
    }

    public sealed interface Command {
        record MarkSlotAvailable(Participant participant) implements Command {
        }

        record UnmarkSlotAvailable(Participant participant) implements Command {
        }

        record BookReservation(
                String studentId, String aircraftId, String instructorId, String bookingId)
                implements Command {
        }
    }
}
