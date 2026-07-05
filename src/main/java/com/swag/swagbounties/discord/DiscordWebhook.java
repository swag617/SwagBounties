package com.swag.swagbounties.discord;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Discord webhook utility that posts rich embeds to a webhook URL using only
 * {@link HttpURLConnection} — no extra dependencies.
 *
 * Callers on the main server thread should use {@link #sendEmbedAsync} so the
 * HTTP I/O happens on a virtual thread.
 */
public final class DiscordWebhook {

    // Embed colours
    public static final int COLOR_SET    = 0xe67e22; // orange  — bounty placed
    public static final int COLOR_CLAIM  = 0xe74c3c; // red     — bounty claimed
    public static final int COLOR_EXPIRE = 0x95a5a6; // grey    — bounty expired

    private DiscordWebhook() {}

    /**
     * Sends a rich embed synchronously. Blocks until the HTTP round-trip completes.
     * Prefer {@link #sendEmbedAsync} from the main thread.
     *
     * @param webhookUrl  full Discord webhook URL
     * @param title       embed title (may include emoji)
     * @param description embed body text (will be JSON-escaped)
     * @param color       sidebar colour as a 24-bit RGB int
     */
    public static void sendEmbed(String webhookUrl, String title, String description, int color) {
        try {
            byte[] payload = buildEmbedPayload(title, description, color);

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
     * Sends a rich embed asynchronously on a virtual thread (Java 21).
     * This is the preferred method when calling from the main server thread.
     */
    public static void sendEmbedAsync(String webhookUrl, String title, String description, int color) {
        Thread.ofVirtual().start(() -> sendEmbed(webhookUrl, title, description, color));
    }

    // -------------------------------------------------------------------------

    private static byte[] buildEmbedPayload(String title, String description, int color) {
        String escapedTitle = escape(title);
        String escapedDesc  = escape(description);
        String timestamp    = Instant.now().toString();

        String json = "{"
                + "\"embeds\":[{"
                + "\"title\":\"" + escapedTitle + "\","
                + "\"description\":\"" + escapedDesc + "\","
                + "\"color\":" + color + ","
                + "\"footer\":{\"text\":\"SwagBounties\"},"
                + "\"timestamp\":\"" + timestamp + "\""
                + "}]}";

        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
