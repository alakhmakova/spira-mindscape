package com.spiramindscape.backend.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Device tokens, always accessed scoped to their owning user.
 *
 * <p>{@link #findByToken} is the one lookup by the (globally unique) token — used by the
 * register upsert to move a re-used token to its new owner — and by send-time cleanup to drop
 * a token FCM has reported as gone.
 */
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserId(Long userId);

    Optional<DeviceToken> findByToken(String token);

    /** Owner-scoped delete: a user can only unregister a token that is theirs. */
    long deleteByTokenAndUserId(String token, Long userId);
}
