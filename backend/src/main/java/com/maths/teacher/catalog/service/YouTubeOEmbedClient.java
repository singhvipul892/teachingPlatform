package com.maths.teacher.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Looks up a YouTube video's title through the public oEmbed endpoint (no API
 * key). Called from the server because the browser can't: YouTube's oEmbed
 * sends no CORS headers. Best effort — any failure is just "no title".
 */
@Component
public class YouTubeOEmbedClient {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeOEmbedClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper objectMapper;

    public YouTubeOEmbedClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return the title, or null if YouTube didn't give one (private, removed, unreachable). */
    public String fetchTitle(String videoId) {
        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
        URI uri = URI.create("https://www.youtube.com/oembed?format=json&url="
                + URLEncoder.encode(watchUrl, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            String title = objectMapper.readTree(response.body()).path("title").asText(null);
            return title == null || title.isBlank() ? null : title.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.info("YouTube oEmbed lookup failed for {}: {}", videoId, e.toString());
            return null;
        }
    }
}
