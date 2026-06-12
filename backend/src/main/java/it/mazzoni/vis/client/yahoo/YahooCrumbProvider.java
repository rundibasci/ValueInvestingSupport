package it.mazzoni.vis.client.yahoo;

import it.mazzoni.vis.exception.MarketDataUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class YahooCrumbProvider {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String FC_URL     = "https://fc.yahoo.com";
    private static final String CRUMB_URL  = "https://query1.finance.yahoo.com/v1/test/getcrumb";

    private final WebClient client;
    private volatile CrumbSession session;

    public YahooCrumbProvider() {
        this.client = WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .build();
    }

    public synchronized CrumbSession acquireSession() {
        if (session == null) {
            session = fetchNewSession();
        }
        return session;
    }

    public synchronized void invalidate() {
        session = null;
    }

    private CrumbSession fetchNewSession() {
        String cookieHeader = client.get()
                .uri(FC_URL)
                .exchangeToMono(resp ->
                        resp.releaseBody().thenReturn(
                                resp.headers().header("Set-Cookie").stream()
                                        .filter(h -> h.startsWith("A=") || h.startsWith("A3="))
                                        .map(h -> h.split(";")[0])
                                        .findFirst()
                                        .orElse("")))
                .block(Duration.ofSeconds(10));

        String crumb = client.get()
                .uri(CRUMB_URL)
                .header("Cookie", cookieHeader)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));

        if (crumb == null || crumb.isBlank()) {
            throw new MarketDataUnavailableException(
                    "Failed to acquire Yahoo Finance session crumb", null);
        }
        return new CrumbSession(cookieHeader, crumb);
    }

    public record CrumbSession(String cookie, String crumb) {}
}
