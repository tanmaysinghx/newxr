package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByTokenHash(String hash);
    List<UserToken> findByUserAndUsedAtIsNullAndExpiresAtAfter(User user, Instant now);
    List<UserToken> findByUserAndUsedAtIsNull(User user);
}
