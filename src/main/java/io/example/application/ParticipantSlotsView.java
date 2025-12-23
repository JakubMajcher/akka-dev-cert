package io.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "view-participant-slots")
public class ParticipantSlotsView extends View {

    private static Logger logger = LoggerFactory.getLogger(ParticipantSlotsView.class);

    @Consume.FromEventSourcedEntity(ParticipantSlotEntity.class)
    public static class ParticipantSlotsViewUpdater extends TableUpdater<SlotRow> {

        public Effect<SlotRow> onEvent(ParticipantSlotEntity.Event event) {
            if (event instanceof ParticipantSlotEntity.Event.MarkedAvailable e) {
                SlotRow row = new SlotRow(
                        e.slotId(),
                        e.participantId(),
                        e.participantType().name(),
                        "",
                        "available");
                return effects().updateRow(row);
            } else if (event instanceof ParticipantSlotEntity.Event.UnmarkedAvailable e) {
                return effects().deleteRow();
            } else if (event instanceof ParticipantSlotEntity.Event.Booked e) {
                SlotRow row = new SlotRow(
                        e.slotId(),
                        e.participantId(),
                        e.participantType().name(),
                        e.bookingId(),
                        "booked");
                return effects().updateRow(row);
            } else if (event instanceof ParticipantSlotEntity.Event.Canceled e) {
                SlotRow row = new SlotRow(
                        e.slotId(),
                        e.participantId(),
                        e.participantType().name(),
                        e.bookingId(),
                        "canceled");
                return effects().updateRow(row);
            }

            logger.warn("Ignoring unknown event type: {}", event.getClass().getName());
            return effects().ignore();
        }
    }

    public record SlotRow(
            String slotId,
            String participantId,
            String participantType,
            String bookingId,
            String status) {
    }

    public record ParticipantStatusInput(String participantId, String status) {
    }

    public record SlotList(List<SlotRow> slots) {
    }

    @Query("SELECT * as slots FROM participant_slots WHERE participantId = :participantId")
    public QueryEffect<SlotList> getSlotsByParticipant(String participantId) {
        return queryResult();
    }

    @Query("SELECT * as slots FROM participant_slots WHERE participantId = :participantId and status = :status")
    public QueryEffect<SlotList> getSlotsByParticipantAndStatus(ParticipantStatusInput input) {
        return queryResult();
    }
}
