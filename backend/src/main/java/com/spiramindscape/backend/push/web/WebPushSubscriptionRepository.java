package com.spiramindscape.backend.push.web;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    List<WebPushSubscription> findByUserIdOrderByLastSeenAtDesc(Long userId);

    long deleteByUserIdAndEndpoint(Long userId, String endpoint);
}
