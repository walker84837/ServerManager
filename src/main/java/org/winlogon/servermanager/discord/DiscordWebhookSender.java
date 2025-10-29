package org.winlogon.servermanager.discord;

import java.net.URI;
import java.util.logging.Logger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordWebhookSender {
    private final String webhookUrl;
    private final Logger logger;
    private final HttpClient httpClient;

    public DiscordWebhookSender(String webhookUrl, Logger logger) {
        this.webhookUrl = webhookUrl;
        this.logger = logger;
        this.httpClient = HttpClient.newHttpClient();
    }

    public void sendMessage(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            logger.warning("Discord webhook URL is not configured.");
            return;
        }

        try {
            String jsonPayload = "{\"content\": \"" + message.replace("\"", "\\\"") + "\"}";

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int responseCode = response.statusCode();
            if (responseCode != 204 && responseCode != 200) {
                logger.severe("Failed to send Discord webhook message. Response Code: " + responseCode + ", Response: " + response.body());
            } else {
                logger.info("Discord webhook message sent successfully.");
            }

        } catch (Exception e) {
            logger.severe("Error sending Discord webhook message: " + e.getMessage());
        }
    }
}
