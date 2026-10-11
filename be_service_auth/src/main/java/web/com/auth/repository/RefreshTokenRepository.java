package web.com.auth.repository;

import web.com.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
    List<RefreshTokenEntity> findAllByUsernameAndRevokedFalse(String username);
}
