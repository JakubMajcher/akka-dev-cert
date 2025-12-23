package io.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * The flight conditions agent is responsible for making a determination about the flight
 * conditions for a given day and time. You will need to clearly define the success criteria
 * for the report and instruct the agent (in the system prompt) about the schema of
 * the results it must return (the ConditionsReport).
 *
 * Also be sure to provide clear instructions on how and when tools should be invoked
 * in order to generate results.
 *
 * Flight conditions criteria don't need to be exhaustive, but you should supply the
 * criteria so that an agent does not need to make an external HTTP call to query
 * the condition limits.
 */

@Component(id = "flight-conditions-agent")
public class FlightConditionsAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(FlightConditionsAgent.class);

    public record ConditionsReport(String timeSlotId, Boolean meetsRequirements) {
    }

    private static final String SYSTEM_MESSAGE = """
            You are a flight safety officer.
            To evaluate a timeslot, you MUST follow these steps:
            1. CALL the 'getWeatherForecast' tool using the provided timeSlotId.
            2. Examine the weather description returned by the tool.
            3. If the description contains 'thunderstorms' or 'high winds', you MUST set meetsRequirements to false.
            4. Otherwise, set meetsRequirements to true.
            5. Return the result strictly as a JSON object matching the ConditionsReport schema.
            DO NOT guess the weather. Always use the tool.
            """.stripIndent();

    public Effect<ConditionsReport> query(String timeSlotId) {
        logger.info("Agent received query for slot: {}", timeSlotId);
        return effects().systemMessage(SYSTEM_MESSAGE)
                .userMessage("Evaluate conditions for timeslot: " + timeSlotId)
                .responseAs(ConditionsReport.class)
                .thenReply();
    }

    /*
     * You can choose to hard code the weather conditions for specific days or you
     * can actually
     * communicate with an external weather API. You should be able to get both
     * suitable weather
     * conditions and poor weather conditions from this tool function for testing.
     */
    @FunctionTool(description = "Queries the weather conditions as they are forecasted based on the time slot ID")
    public String getWeatherForecast(String timeSlotId) {
        logger.info("Agent is calling tool getWeatherForecast for slot: {}", timeSlotId);
        // 13th is unlucky
        if (timeSlotId.contains("-13-")) {
            return "Thunderstorms and high winds expected.";
        }
        return "Clear skies, light breeze.";
    }
}
