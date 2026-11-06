package dev.infrai.edtech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class InfraiClient {
    private final InfraiConfig config;
    private final HttpClient http;
    private final int maxAttempts;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), 4);
    }

    InfraiClient(InfraiConfig config, HttpClient http, int maxAttempts) {
        this.config = config;
        this.http = http;
        this.maxAttempts = maxAttempts;
    }

    public Map<String, Object> publish(String queue, Map<String, Object> payload, String idempotencyKey) {
        return post("/v1/queue/publish", Map.of("queue", queue, "payload", payload), idempotencyKey);
    }

    public List<Map<String, Object>> consume(String queue, int maxMessages, int visibilityTimeout) {
        Map<String, Object> data = post("/v1/queue/consume", Map.of(
                "queue", queue,
                "max_messages", maxMessages,
                "visibility_timeout", visibilityTimeout
        ), idempotency("consume", Integer.toString(maxMessages)));
        Object messages = data.get("items");
        if (!(messages instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(item -> castMap((Map<?, ?>) item)).toList();
    }

    public Map<String, Object> resize(String image, int width, int height, String idempotencyKey) {
        return post("/v1/image/resize", Map.of(
                "image", imageReference(image),
                "width", width,
                "height", height,
                "fit", "contain",
                "enlarge", false,
                "format", "webp",
                "store", true
        ), idempotencyKey);
    }

    static Map<String, String> imageReference(String image) {
        if (image.startsWith("base64:")) return Map.of("base64", image.substring(7));
        if (image.startsWith("image_id:")) return Map.of("image_id", image.substring(9));
        return Map.of("url", image);
    }

    private Map<String, Object> post(String path, Map<String, Object> body, String idempotencyKey) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                    .build();
            HttpResponse<String> response = send(request);
            Map<String, Object> envelope = envelope(response.body());
            if (response.statusCode() == 429 && attempt < maxAttempts) {
                pause(retryMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw businessError(response.statusCode(), envelope.get("error"));
            }
            if (response.statusCode() >= 500) {
                if (attempt < maxAttempts) { pause(250L << (attempt - 1)); continue; }
                throw new IllegalStateException("Infrai transport status " + response.statusCode());
            }
            Object data = envelope.get("data");
            return data instanceof Map<?, ?> map ? castMap(map) : Map.of("value", data);
        }
        throw new IllegalStateException("Request attempts exhausted");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Infrai transport error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Infrai request interrupted", e);
        }
    }

    private static Map<String, Object> envelope(String body) {
        Object decoded = Json.parse(body);
        if (!(decoded instanceof Map<?, ?> map)) throw new IllegalStateException("Expected Infrai envelope");
        return castMap(map);
    }

    private static InfraiException businessError(int status, Object rawError) {
        Map<String, Object> error = rawError instanceof Map<?, ?> map ? castMap(map) : Map.of("message", rawError);
        return new InfraiException(String.valueOf(error.getOrDefault("code", "INFRAI_REJECTED")), status, error);
    }

    private static long retryMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(InfraiClient::secondsToMillis)
                .orElse(250L << (attempt - 1));
    }

    private static long secondsToMillis(String value) {
        try { return Math.max(0L, Long.parseLong(value.trim()) * 1_000L); }
        catch (NumberFormatException ignored) { return 1_000L; }
    }

    private static void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    static String idempotency(String scope, String identity) {
        return scope + "-" + Integer.toUnsignedString(identity.hashCode(), 36);
    }

    private static Map<String, Object> castMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
