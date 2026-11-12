package com.loyalty;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end through HTTP: checks routing, JSON shape and error-to-status mapping. */
class HttpApiTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void earnCheckBalanceAndRedeem() throws Exception {
        HttpResponse<String> earn = post("/points/customers/alice/purchases", "{\"purchaseId\":\"order-1\",\"amount\":120.50}");
        assertEquals(201, earn.statusCode());
        assertTrue(earn.body().contains("\"pointsEarned\":120"), earn.body());
        assertTrue(earn.body().contains("\"expiresAt\":\"2027-01-15T10:00:00Z\""), earn.body());

        HttpResponse<String> balance = get("/points/customers/alice/balance");
        assertEquals(200, balance.statusCode());
        assertTrue(balance.body().contains("\"availablePoints\":120"), balance.body());
        assertTrue(balance.body().contains("\"tier\":\"SILVER\""), balance.body());

        HttpResponse<String> redeem = post("/points/customers/alice/redemptions", "{\"rewardId\":\"free-coffee\"}");
        assertEquals(201, redeem.statusCode());
        assertTrue(redeem.body().contains("\"availablePoints\":20"), redeem.body());
    }

    @Test
    void mapsBusinessErrorsToStatusCodes() throws Exception {
        assertEquals(404, get("/points/customers/nobody/balance").statusCode());

        post("/points/customers/alice/purchases", "{\"purchaseId\":\"order-1\",\"amount\":10}");
        assertEquals(409, post("/points/customers/alice/purchases", "{\"purchaseId\":\"order-1\",\"amount\":10}").statusCode());

        HttpResponse<String> insufficient = post("/points/customers/alice/redemptions", "{\"rewardId\":\"free-coffee\"}");
        assertEquals(422, insufficient.statusCode());
        assertTrue(insufficient.body().contains("INSUFFICIENT_POINTS"), insufficient.body());

        assertEquals(400, post("/points/customers/alice/purchases", "not json").statusCode());
        assertEquals(400, post("/points/customers/alice/purchases", "{\"purchaseId\":\"x\",\"amount\":-1}").statusCode());
        assertEquals(400, post("/points/customers/alice/purchases", "{\"purchaseId\":\"x\",\"amount\":1,\"bogus\":1}").statusCode());
    }

    @Test
    void unknownPathsAreNotFoundRatherThanServerErrors() throws Exception {
        assertEquals(404, get("/no-such-thing").statusCode());
        assertEquals(404, get("/rewards").statusCode(), "routes are only served under /points");
        assertEquals(200, get("/points/rewards").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
