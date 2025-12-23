package io.example.cucumber;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import okhttp3.*;

public class FlightFlowSteps {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
            .build();
    private String baseUrl;
    private Response lastResponse;
    private String lastBody;

    @Given("the flight service is running on {string}")
    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    @When("I POST {string} with slotId {string} and participantId {string} and participantType {string}")
    public void markAvailable(String pathTemplate, String slotId, String pId, String pType) throws IOException {
        String url = baseUrl + pathTemplate.replace("{slotId}", slotId);
        String json = String.format("{\"participantId\": \"%s\", \"participantType\": \"%s\"}", pId, pType);

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .header("Connection", "close")
                .build();
        execute(request);
    }

    @When("I GET {string} with slotId {string}")
    public void getSlot(String pathTemplate, String slotId) throws IOException {
        String url = baseUrl + pathTemplate.replace("{slotId}", slotId);
        execute(new Request.Builder().url(url).get().header("Connection", "close").build());
    }

    @When("I GET {string} with participantId {string}")
    public void getSlotsByParticipant(String pathTemplate, String pId) throws IOException {
        String url = baseUrl + pathTemplate.replace("{participantId}", pId);
        execute(new Request.Builder().url(url).get().header("Connection", "close").build());
    }

    @When("I GET {string} with participantId {string} and status {string}")
    public void getSlotsByStatus(String pathTemplate, String pId, String status) throws IOException {
        String url = baseUrl + pathTemplate.replace("{participantId}", pId).replace("{status}", status);
        execute(new Request.Builder().url(url).get().header("Connection", "close").build());
    }

    @When("I POST {string} with slotId {string} and body:")
    public void createBooking(String pathTemplate, String slotId, String jsonBody) throws IOException {
        String url = baseUrl + pathTemplate.replace("{slotId}", slotId);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON))
                .header("Connection", "close")
                .build();
        execute(request);
    }

    @When("I DELETE {string} with slotId {string} and bookingId {string}")
    public void cancelBooking(String pathTemplate, String slotId, String bId) throws IOException {
        String url = baseUrl + pathTemplate.replace("{slotId}", slotId).replace("{bookingId}", bId);
        execute(new Request.Builder().url(url).delete().header("Connection", "close").build());
    }

    @Then("the response status should be {int}")
    public void assertStatus(int code) {
        assertNotNull(lastResponse);
        assertEquals(code, lastResponse.code());
    }

    @Then("eventually the response status should be {int}")
    public void eventually_the_response_status_should_be(int expectedStatus) throws Exception {
        pollUntil(Duration.ofSeconds(3), Duration.ofMillis(150),
                () -> lastResponse != null && lastResponse.code() == expectedStatus);

        assertNotNull(lastResponse, "No HTTP response captured yet");
        assertEquals(expectedStatus, lastResponse.code(), "Unexpected HTTP status code");
    }

    @Then("eventually the response body should not be empty")
    public void eventually_the_response_body_should_not_be_empty() throws Exception {
        pollUntil(Duration.ofSeconds(3), Duration.ofMillis(150),
                () -> lastBody != null && !lastBody.trim().isEmpty());

        assertNotNull(lastBody, "Response body is null");
        assertFalse(lastBody.trim().isEmpty(), "Response body is empty");
    }

    @Then("the response body should contain participant {string}")
    public void bodyContainsParticipant(String pId) {
        assertTrue(lastBody.contains("\"id\":\"" + pId + "\"") || lastBody.contains("\"id\": \"" + pId + "\""));
    }

    @Then("eventually the response body should contain {string}")
    public void eventuallyBodyContains(String text) {
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    if (lastResponse != null) {
                        execute(lastResponse.request());
                    }
                    // Dodajmy informację o aktualnym body, żeby wiedzieć co się dzieje
                    String currentBody = lastBody == null ? "NULL" : lastBody;
                    assertTrue(currentBody.contains(text),
                            String.format("Expected body to contain '%s' but was: '%s' (Status: %d)",
                                    text, currentBody, lastResponse != null ? lastResponse.code() : -1));
                });
    }

    @Then("eventually the response body should contain no slots")
    public void eventuallyNoSlots() {
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    if (lastResponse != null) {
                        execute(lastResponse.request());
                    }
                    boolean isEmpty = lastBody != null &&
                            (lastBody.contains("\"slots\":[]") || lastBody.contains("\"slots\": []"));
                    assertTrue(isEmpty, "Expected empty slots list but got: " + lastBody);
                });
    }

    @Then("the response body should be empty of bookings and available")
    public void assertEmptyState() {
        assertNotNull(lastBody);
        assertTrue(lastBody.contains("\"bookings\":[]") || lastBody.contains("\"bookings\": []"));
        assertTrue(lastBody.contains("\"available\":[]") || lastBody.contains("\"available\": []"));
    }

    @Then("the response body should contain {string}")
    public void bodyContainsText(String text) {
        assertNotNull(lastBody);
        assertTrue(lastBody.contains(text), "Expected body to contain '" + text + "' but was: " + lastBody);
    }

    private void execute(Request req) throws IOException {
        if (lastResponse != null) lastResponse.close();
        lastResponse = client.newCall(req).execute();
        lastBody = lastResponse.body() != null ? lastResponse.body().string() : "";
    }

    private static void pollUntil(Duration timeout, Duration interval, CheckedBooleanSupplier condition)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        Exception lastError = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                if (condition.getAsBoolean()) return;
            } catch (Exception e) {
                lastError = e;
            }
            Thread.sleep(interval.toMillis());
        }

        if (lastError != null) throw lastError;
    }

    @FunctionalInterface interface CheckedBooleanSupplier { boolean getAsBoolean() throws Exception; }
}
