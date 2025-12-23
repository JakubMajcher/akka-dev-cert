package io.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import io.example.application.ParticipantSlotEntity.Event.MarkedAvailable;
import io.example.domain.Participant.ParticipantType;


import static io.example.application.ParticipantSlotEntity.Event.Booked;
import static io.example.application.ParticipantSlotEntity.Event.Canceled;
import static io.example.application.ParticipantSlotEntity.Event.UnmarkedAvailable;

@Component(id = "participant-slot")
public class ParticipantSlotEntity
        extends EventSourcedEntity<ParticipantSlotEntity.State, ParticipantSlotEntity.Event> {

    public Effect<Done> unmarkAvailable(Commands.UnmarkAvailable unmark) {
        UnmarkedAvailable event = new UnmarkedAvailable(unmark.slotId(), unmark.participantId(), unmark.participantType());
        Effect.OnSuccessBuilder<State> stateOnSuccessBuilder = effects().persist(event).deleteEntity();
        return stateOnSuccessBuilder.thenReply(__ -> Done.done());
    }

    public Effect<Done> markAvailable(Commands.MarkAvailable mark) {
        return effects()
                .persist(new MarkedAvailable(mark.slotId(), mark.participantId(), mark.participantType()))
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> book(Commands.Book book) {
        return effects()
                .persist(new Booked(book.slotId(), book.participantId(), book.participantType(), book.bookingId()))
                .thenReply(__ -> Done.done());
    }

    public Effect<Done> cancel(Commands.Cancel cancel) {
        return effects()
                .persist(new Canceled(cancel.slotId(), cancel.participantId(), cancel.participantType(), cancel.bookingId()))
                .thenReply(__ -> Done.done());
    }

    record State(
            String slotId, String participantId, ParticipantType participantType, String status) {
    }

    public sealed interface Commands {
        record MarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record UnmarkAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Commands {
        }

        record Book(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }

        record Cancel(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Commands {
        }
    }

    public sealed interface Event {
        @TypeName("marked-available")
        record MarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("unmarked-available")
        record UnmarkedAvailable(String slotId, String participantId, ParticipantType participantType)
                implements Event {
        }

        @TypeName("participant-booked")
        record Booked(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }

        @TypeName("participant-canceled")
        record Canceled(
                String slotId, String participantId, ParticipantType participantType, String bookingId)
                implements Event {
        }
    }

    @Override
    public State applyEvent(Event event) {
        if (event instanceof MarkedAvailable e) {
            return new State(e.slotId(), e.participantId(), e.participantType(), "available");
        } else if (event instanceof UnmarkedAvailable e) {
            // this entity is deleted in command handler, but state must still be non-null during handling
            return new State(e.slotId(), e.participantId(), e.participantType(), "unavailable");
        } else if (event instanceof Booked e) {
            return new State(e.slotId(), e.participantId(), e.participantType(), "booked");
        } else if (event instanceof Canceled e) {
            return new State(e.slotId(), e.participantId(), e.participantType(), "canceled");
        }

        return currentState();
    }
}
