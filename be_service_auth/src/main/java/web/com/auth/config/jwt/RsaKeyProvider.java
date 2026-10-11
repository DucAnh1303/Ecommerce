package web.com.auth.config.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quản lý cặp khoá RSA dùng để ký JWT (RS256).
 *
 * <ul>
 *   <li>Private key: chỉ AUTH-SERVICE giữ – dùng để ký token.</li>
 *   <li>Public key : được suy ra từ private key và công bố qua JWKS endpoint
 *       để Gateway / Order / Product tự verify token tại chỗ (không cần gọi mạng).</li>
 *   <li>kid        : RFC 7638 thumbprint của public key → hỗ trợ xoay vòng khoá.</li>
 * </ul>
 *
 * Private key phải ở định dạng PKCS#8 PEM ("-----BEGIN PRIVATE KEY-----").
 * Tạo bằng script: {@code scripts/generate-rsa-keys.sh}.
 */
@Log4j2
@Getter
@Component
public class RsaKeyProvider {

    @Value("${jwt.rsa.private-key-location}")
    private Resource privateKeyResource;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    @PostConstruct
    public void init() throws Exception {
        if (privateKeyResource != null && privateKeyResource.exists()) {
            this.privateKey = loadPrivateKey(privateKeyResource);
            this.publicKey = derivePublicKey(privateKey);
            log.info("Loaded RSA private key from {}", privateKeyResource.getDescription());
        } else {
            // Chỉ dùng cho môi trường dev: token sẽ mất hiệu lực sau mỗi lần restart.
            log.warn("RSA private key NOT FOUND at {} → generating EPHEMERAL key pair (DEV ONLY). "
                    + "Run scripts/generate-rsa-keys.sh to create a persistent key.",
                    privateKeyResource != null ? privateKeyResource.getDescription() : "null");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey = (RSAPublicKey) pair.getPublic();
        }
        this.keyId = computeThumbprint(publicKey);
        log.info("JWT signing key ready – alg=RS256, kid={}", keyId);
    }

    /**
     * Public key dưới dạng JWK (RFC 7517) để trả về trong JWKS.
     */
    public Map<String, Object> toPublicJwk() {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId);
        jwk.put("n", base64Url(publicKey.getModulus()));
        jwk.put("e", base64Url(publicKey.getPublicExponent()));
        return jwk;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static RSAPrivateKey loadPrivateKey(Resource resource) throws Exception {
        String pem;
        try (InputStream in = resource.getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("Private key is PKCS#1. Convert to PKCS#8: "
                    + "openssl pkcs8 -topk8 -nocrypt -in old.pem -out private_key.pem");
        }
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("Cannot derive public key: private key is not an RSA CRT key");
        }
        RSAPublicKeySpec spec = new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent());
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /**
     * RFC 7638 JWK thumbprint: SHA-256 của {"e":..,"kty":"RSA","n":..} (thứ tự key cố định).
     */
    private static String computeThumbprint(RSAPublicKey key) throws Exception {
        String canonical = "{\"e\":\"" + base64Url(key.getPublicExponent())
                + "\",\"kty\":\"RSA\",\"n\":\"" + base64Url(key.getModulus()) + "\"}";
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    /** Base64url (không padding) của số nguyên không dấu, bỏ byte dấu 0x00 ở đầu. */
    private static String base64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
