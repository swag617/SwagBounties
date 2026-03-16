package com.swag.swagbounties.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Discord webhook utility that posts a plain-text content message to a
 * webhook URL using only {@link HttpURLConnection} — no extra dependencies.
 *
 * <p>All methods are static and fire-and-forget: checked exceptions are caught
 * internally and logged to stderr. Callers on the main server thread should use
 * {@link #sendAsync(String, String)} so the HTTP I/O happens on a virtual thread.</p>
 */
public final class DiscordWebhook {

    private DiscordWebhook() {
        // utility class — no instances
    }

    /**
     * Sends a Discord webhook message synchronously on the calling thread.
     *
     * <p>This is safe to call from any thread but will block until the HTTP
     * round-trip completes. Prefer {@link #sendAsync} from the main thread.</p>
     *
     * @param webhookUrl the full Discord webhook URL
     * @param content    the message content (will be JSON-escaped)
     */
    public static void send(String webhookUrl, String content) {
        try {
            byte[] payload = buildPayload(content);

            HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "SwagBounties-DiscordWebhook/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                System.err.println("[SwagBounties] Discord webhook returned non-2xx status: " + responseCode);
            }

            conn.disconnect();
        } catch (IOException e) {
            System.err.println("[SwagBounties] Failed to send Discord webhook: " + e.getMessage());
        }
    }

    /**
     * Sends a Discord webhook message asynchronously on a virtual thread (Java 21).
     *
     * <p>This is the preferred method when calling from the main server thread since
     * it offloads the blocking HTTP call without consuming a platform thread.</p>
     *
     * @param webhookUrl the full Discord webhook URL
     * @param content    the message content (will be JSON-escaped)
     */
    public static void sendAsync(String webhookUrl, String content) {
        Thread.ofVirtual().start(() -> send(webhookUrl, content));
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Builds the UTF-8 JSON payload for the given content string, escaping
     * backslashes and double-quotes so the JSON remains valid.
     */
    private static byte[] buildPayload(String content) {
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        String json = "{\"content\": \"" + escaped + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
