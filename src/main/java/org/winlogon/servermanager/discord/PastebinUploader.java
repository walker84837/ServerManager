package org.winlogon.servermanager.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import org.winlogon.servermanager.config.PasteUploadConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PastebinUploader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PasteUploadConfig config;
    private final Path pastesDir;
    private final Logger logger;
    private final HttpClient httpClient;
    private final Scope jqScope;
    private final JsonQuery jqQuery;
    @Getter
    private final boolean uploadEnabled;

    public PastebinUploader(PasteUploadConfig config, Path dataFolder, Logger logger) {
        this.config = config;
        this.pastesDir = dataFolder.resolve("pastes");
        this.logger = logger;
        this.httpClient = HttpClient.newHttpClient();

        var hasSelector = config.selector != null && !config.selector.isBlank();
        if (hasSelector) {
            try {
                var scope = Scope.newEmptyScope();
                BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, scope);
                this.jqScope = scope;
                this.jqQuery = JsonQuery.compile(config.selector, Versions.JQ_1_6);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid jq selector: " + config.selector, e);
            }
        } else {
            this.jqScope = null;
            this.jqQuery = null;
        }

        this.uploadEnabled = config.url != null && !config.url.isBlank();
    }

    public CompletableFuture<Optional<String>> upload(String content) {
        if (!uploadEnabled) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        HttpRequest.BodyPublisher bodyPublisher;
        String contentType = null;

        if ("multipart".equalsIgnoreCase(config.format)) {
            // Multipart/form-data requests need a unique boundary string. This boundary marks where one part
            // ends and the next begins.
            var boundary = "----" + UUID.randomUUID();

            // --- Build a multipart request body manually ---

            var body = "--" + boundary + "\r\n" // a boundary line
                    // headers describing the uploaded "part"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"output.txt\"\r\n"
                    + "Content-Type: text/plain; charset=utf-8\r\n"
                    // a blank line
                    + "\r\n"
                    // the actual content
                    + content + "\r\n"
                    // a closing boundary
                    + "--" + boundary + "--\r\n";

            // Send the multipart body as a UTF-8 string
            bodyPublisher = HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);

            // Tell the server this is multipart/form-data, and include the boundary
            // so the HTTP server can parse the parts correctly
            contentType = "multipart/form-data; boundary=" + boundary;
        } else {
            // For non-multipart requests, use the normal body format
            bodyPublisher = buildBodyPublisher(content);
        }

        var requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(config.url))
            .method(config.method.toUpperCase(), bodyPublisher);

        if (contentType != null) {
            requestBuilder.header("Content-Type", contentType);
        } else if (!config.headers.containsKey("Content-Type")) {
            switch (config.format.toLowerCase()) {
                case "json" -> requestBuilder.header("Content-Type", "application/json");
                default -> requestBuilder.header("Content-Type", "text/plain; charset=utf-8");
            }
        }

        for (var entry : config.headers.entrySet()) {
            // Skip Content-Type for multipart - already set with correct boundary
            if (contentType != null && entry.getKey().equalsIgnoreCase("Content-Type")) {
                continue;
            }
            requestBuilder.header(entry.getKey(), entry.getValue());
        }

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                var code = response.statusCode();
                var body = response.body();
                if (code / 100 != 2) {
                    logger.warning("Paste service returned " + code + ": " + body);
                    return Optional.<String>empty();
                }
                return Optional.ofNullable(extractUrl(body));
            })
            .exceptionally(ex -> {
                logger.log(Level.WARNING, "Failed to upload to paste service", ex);
                return Optional.empty();
            });
    }

    public Path saveToFile(String content) throws IOException {
        Files.createDirectories(pastesDir);
        var timestamp = System.currentTimeMillis();
        var random = ThreadLocalRandom.current().nextInt(100000, 999999);
        var path = pastesDir.resolve(timestamp + "-" + random + ".txt");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    /**
     * Builds an HTTP request body publisher for the current output format.
     * <p>
     * For JSON output, the content is JSON-escaped before being inserted into the request body.
     * <p>
     * For all other formats, the content is treated as plain text and null characters are stripped out before use.
     * <p>
     * If {@code config.body} is not empty, it is used as a template and
     * {@code {content}} is replaced with the processed content. Otherwise, the
     * processed content is sent directly as the request body.
     *
     * @param content the raw content to include in the request body
     * @return a {@link HttpRequest.BodyPublisher} containing the formatted body
     */
    private HttpRequest.BodyPublisher buildBodyPublisher(String content) {
        var sanitized = stripNulls(content);

        return switch (config.format.toLowerCase()) {
            case "json" -> {
                var escaped = escapeJson(content);
                var body = config.body.isEmpty() ? escaped : config.body.replace("{content}", escaped);
                yield HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            }
            default -> { // text
                var body = config.body.isEmpty() ? sanitized : config.body.replace("{content}", sanitized);
                yield HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
            }
        };
    }

    private String extractUrl(String responseBody) {
        if (jqQuery == null || responseBody == null || responseBody.isBlank()) {
            return responseBody != null ? responseBody.strip() : null;
        }

        try {
            var root = MAPPER.readTree(responseBody);
            var results = new ArrayList<JsonNode>();
            jqQuery.apply(jqScope, root, results::add);

            if (results.isEmpty()) return null;

            var extracted = results.getFirst().asText();
            if (extracted == null || extracted.isBlank()) return null;

            if (config.urlTemplate == null || config.urlTemplate.isBlank() || config.urlTemplate.equals("{result}")) {
                return extracted;
            }
            return config.urlTemplate.replace("{result}", extracted);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse paste service response: " + responseBody, e);
            return null;
        }
    }

    static String escapeJson(String text) {
        var sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    // Format non-printable characters as "\\uXXXX"
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String stripNulls(String text) {
        return text.indexOf('\0') < 0 ? text : text.replace("\0", "");
    }
}
