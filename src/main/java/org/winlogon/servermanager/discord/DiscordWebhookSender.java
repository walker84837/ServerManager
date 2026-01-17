package org.winlogon.servermanager.discord;

import java.net.URI;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordWebhookSender {
    private final String webhookUrl;
    private final Logger logger;
    private final HttpClient httpClient;

    public DiscordWebhookSender(String webhookUrl, Logger logger) {
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendMessage(String message) {
        if (webhookUrl.isBlank()) {
            logger.warning("Discord webhook URL is not configured.");
            return;
        }

        var jsonPayload = """
            {
              "content": "%s"
            }
            """.formatted(escapeJson(message));

        var request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                int code = response.statusCode();

                if (code / 100 != 2) { // Is the code a 2xx status code?
                    // Not a 2xx code, likely failure
                    logger.log(Level.SEVERE, "Failed to send Discord webhook message. Response Code: " + code + ", Response: " + response.body());
                } else {
                    // Success
                    logger.fine("Discord webhook message sent successfully.");
                }
            })
            .exceptionally(ex -> {
                logger.log(Level.SEVERE, "Error sending Discord webhook message", ex);
                return null;
            });
    }

    private static String escapeJson(String text) {
        var sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
