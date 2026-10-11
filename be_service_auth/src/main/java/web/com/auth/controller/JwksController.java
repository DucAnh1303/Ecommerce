package web.com.auth.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.com.auth.config.jwt.RsaKeyProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Công bố public key (JWKS – RFC 7517) cho Gateway / Order / Product.
 * Các service fetch 1 lần rồi cache, chỉ fetch lại khi gặp {@code kid} lạ (xoay vòng khoá).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/.well-known")
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    @GetMapping("/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePublic())
                .body(Map.of("keys", List.of(rsaKeyProvider.toPublicJwk())));
    }
}
