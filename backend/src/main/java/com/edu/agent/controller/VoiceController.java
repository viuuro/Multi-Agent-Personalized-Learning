package com.edu.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu.agent.model.User;
import com.edu.agent.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Proxies generated welcome audio so the browser only communicates with Spring Boot. */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);
    private static final int MAX_USERNAME_LENGTH = 80;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_STYLE_LENGTH = 1000;
    private static final int RATE_WINDOW_SECONDS = 60;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final UserRepository userRepository;
    private final ConcurrentHashMap<Long, ArrayDeque<Long>> requestWindows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> inFlightRequests = new ConcurrentHashMap<>();

    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;

    @Value("${voice.rate-limit-per-minute:20}")
    private int rateLimitPerMinute;

    @Value("${voice.max-concurrent-per-user:2}")
    private int maxConcurrentPerUser;

    public VoiceController(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** POST /api/voice/welcome - create one non-cached voice-cloned welcome message. */
    @PostMapping(value = {"/welcome", "/synthesize"}, produces = "audio/wav")
    public ResponseEntity<byte[]> synthesize(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String username = body == null ? "" : body.getOrDefault("username", "");
        String text = body == null ? "" : body.getOrDefault("text", "");
        String style = body == null ? "" : body.getOrDefault("style", "");

        if (username == null) username = "";
        if (text == null) text = "";
        if (style == null) style = "";
        if (username.length() > MAX_USERNAME_LENGTH
                || text.length() > MAX_TEXT_LENGTH
                || style.length() > MAX_STYLE_LENGTH) {
            return ResponseEntity.badRequest().build();
        }

        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.getUsername().equals(username)) {
            return ResponseEntity.status(403).build();
        }
        if (!consumeRateLimit(userId)) {
            return ResponseEntity.status(429)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(RATE_WINDOW_SECONDS))
                    .build();
        }

        AtomicInteger inFlight = inFlightRequests.computeIfAbsent(userId, ignored -> new AtomicInteger());
        if (inFlight.incrementAndGet() > Math.max(1, maxConcurrentPerUser)) {
            inFlight.decrementAndGet();
            return ResponseEntity.status(429)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .build();
        }

        try {
            String requestJson = objectMapper.writeValueAsString(Map.of(
                    "username", username,
                    "text", text,
                    "style", style
            ));
            String normalizedPythonAiUrl = pythonAiUrl.endsWith("/")
                    ? pythonAiUrl.substring(0, pythonAiUrl.length() - 1)
                    : pythonAiUrl;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedPythonAiUrl + "/voice/welcome"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .timeout(Duration.ofSeconds(135))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() != 200 || response.body().length == 0) {
                log.warn("Python voice synthesis request failed with HTTP {}", response.statusCode());
                return ResponseEntity.status(502).build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .cacheControl(CacheControl.noStore())
                    .header("X-Voice-Cache", response.headers()
                            .firstValue("X-Voice-Cache").orElse("UNKNOWN"))
                    .body(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Voice synthesis request interrupted");
            return ResponseEntity.status(503).build();
        } catch (Exception exception) {
            log.warn("Voice synthesis request failed: {}", exception.getMessage());
            return ResponseEntity.status(503).build();
        } finally {
            if (inFlight.decrementAndGet() <= 0) {
                inFlightRequests.remove(userId, inFlight);
            }
        }
    }

    private boolean consumeRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        long cutoff = now - RATE_WINDOW_SECONDS * 1000L;
        ArrayDeque<Long> timestamps = requestWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= Math.max(1, rateLimitPerMinute)) return false;
            timestamps.addLast(now);
            return true;
        }
    }
}
