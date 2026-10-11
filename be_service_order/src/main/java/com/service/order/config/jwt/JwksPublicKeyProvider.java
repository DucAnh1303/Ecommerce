package com.service.order.config.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Lấy &amp; cache public key của AUTH-SERVICE từ JWKS endpoint.
 * Fetch khi khởi động và khi gặp {@code kid} lạ (có throttle) – mọi request còn lại verify trong RAM.
 */
@Slf4j
@Component
public class JwksPublicKeyProvider {

    private static final long EMPTY_CACHE_RETRY_MS = 5_000;

    private final RestClient restClient;
    private final String jwksUri;
    private final long minRefreshIntervalMs;

    private volatile Map<String, PublicKey> keys = Map.of();
    private long lastRefreshAt = 0;

    public JwksPublicKeyProvider(
            @Value("${jwt.jwks-uri}") String jwksUri,
            @Value("${jwt.jwks-min-refresh-interval:30s}") Duration minRefreshInterval) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.jwksUri = jwksUri;
        this.minRefreshIntervalMs = minRefreshInterval.toMillis();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        refreshIfAllowed();
    }

    /**
     * @return public key theo kid, hoặc {@code null} nếu không tìm thấy kể cả sau khi refresh.
     */
    public PublicKey getKey(String kid) {
        PublicKey cached = keys.get(kid);
        if (cached != null) {
            return cached;
        }
        refreshIfAllowed();
        return keys.get(kid);
    }

    private synchronized void refreshIfAllowed() {
        long now = System.currentTimeMillis();
        long minInterval = keys.isEmpty() ? EMPTY_CACHE_RETRY_MS : minRefreshIntervalMs;
        if (now - lastRefreshAt < minInterval) {
            return; // throttle – tránh bị spam kid giả
        }
        lastRefreshAt = now;
        try {
            JsonNode root = restClient.get().uri(jwksUri).retrieve().body(JsonNode.class);
            this.keys = parseJwks(root);
            log.info("JWKS loaded from {} – kids={}", jwksUri, keys.keySet());
        } catch (Exception e) {
            log.warn("Cannot fetch JWKS from {}: {}", jwksUri, e.getMessage());
        }
    }

    private static Map<String, PublicKey> parseJwks(JsonNode root) {
        Map<String, PublicKey> result = new HashMap<>();
        if (root == null) {
            return Map.of();
        }
        for (JsonNode jwk : root.path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asText()) || !jwk.hasNonNull("kid")) {
                continue;
            }
            try {
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("e").asText()));
                result.put(jwk.get("kid").asText(),
                        KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            } catch (Exception ex) {
                log.warn("Skip invalid JWK kid={}: {}", jwk.path("kid").asText(), ex.getMessage());
            }
        }
        return Map.copyOf(result);
    }
}
