package com.spiramindscape.backend.ai.key;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiApiKeyRepository extends JpaRepository<AiApiKey, Long> {

    List<AiApiKey> findByAppUserId(Long appUserId);

    Optional<AiApiKey> findByAppUserIdAndProvider(Long appUserId, String provider);

    void deleteByAppUserIdAndProvider(Long appUserId, String provider);

    boolean existsByAppUserIdAndProvider(Long appUserId, String provider);
}
