package web.com.auth.service;

import lombok.RequiredArgsConstructor;
import web.com.auth.entity.RefreshTokenEntity;
import web.com.auth.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Transactional
    public void save(String token, String username, Date expiry) {
        RefreshTokenEntity rt = new RefreshTokenEntity();
        rt.setToken(token);
        rt.setUsername(username);
        rt.setExpiryDate(expiry);
        rt.setRevoked(false);
        repo.save(rt);
    }

    public boolean isValid(String token) {
        return repo.findById(token)
                .filter(rt -> !rt.isRevoked())
                .filter(rt -> rt.getExpiryDate().after(new Date()))
                .isPresent();
    }

    public void revoke(String token) {
        if (token != null) {
            repo.findById(token).ifPresent(rt -> {
                rt.setRevoked(true);
                repo.save(rt);
            });
        }
    }

    @Transactional
    public void revokeAllByUsername(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        var tokens = repo.findAllByUsernameAndRevokedFalse(username);
        if (!tokens.isEmpty()) {
            tokens.forEach(rt -> rt.setRevoked(true));
            repo.saveAll(tokens);
        }
    }
}
