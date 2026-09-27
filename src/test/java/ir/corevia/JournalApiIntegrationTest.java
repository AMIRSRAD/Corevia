package ir.corevia;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import ir.corevia.accounts.Account;
import ir.corevia.accounts.AccountService;
import ir.corevia.accounts.CreateAccountCommand;
import ir.corevia.accounts.NormalBalance;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JournalApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AccountService accounts;

    @Test
    void postingEndpointReturnsCreatedJournalAndRejectsKeyReuse() throws Exception {
        Account debit = accounts.create(new CreateAccountCommand(
                "API-D-" + UUID.randomUUID(), "API debit", "IRR", NormalBalance.DEBIT));
        Account credit = accounts.create(new CreateAccountCommand(
                "API-C-" + UUID.randomUUID(), "API credit", "IRR", NormalBalance.CREDIT));
        String body = """
                {
                  "businessDate": "2026-07-12",
                  "valueDate": "2026-07-12",
                  "narrative": "API posting",
                  "entries": [
                    {"accountId": "%s", "side": "DEBIT", "amount": "2500", "currency": "IRR", "description": "debit"},
                    {"accountId": "%s", "side": "CREDIT", "amount": "2500", "currency": "IRR", "description": "credit"}
                  ]
                }
                """.formatted(debit.id(), credit.id());

        HttpResponse<String> created = post("api-posting-key", body);
        HttpResponse<String> replayed = post("api-posting-key", body);
        HttpResponse<String> conflict = post("api-posting-key", body.replace("API posting", "Changed"));

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("location")).isPresent();
        assertThat(created.body()).contains("\"amount\":\"2500\"").contains("\"currency\":\"IRR\"");
        assertThat(replayed.statusCode()).isEqualTo(201);
        assertThat(replayed.body()).isEqualTo(created.body());
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("Idempotency key");
    }

    private HttpResponse<String> post(String key, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/journals"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .header("X-Actor-ID", "api-tester")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
