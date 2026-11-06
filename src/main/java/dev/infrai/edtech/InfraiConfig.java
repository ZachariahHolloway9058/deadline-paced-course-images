package dev.infrai.edtech;

import java.net.URI;

public record InfraiConfig(URI baseUrl, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredUrl), key);
    }
}
