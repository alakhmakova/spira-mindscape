package com.spiramindscape.backend.push;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner-scoping + upsert for device tokens, against the real (H2) DB.
 *
 * <p>The security boundary this creates: a user can only ever see/remove <em>their own</em>
 * device tokens, and an FCM token that moves between accounts is re-owned, not duplicated.
 */
class DeviceTokenIntegrationTest extends BaseGraphQlIntegrationTest {

    @Autowired
    private PushNotificationService pushService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @AfterEach
    void cleanTokens() {
        // device_token has no JPA relation to app_user (userId is a plain column), so the
        // base class's user cleanup doesn't cascade to it — clear it ourselves.
        deviceTokenRepository.deleteAll();
    }

    @Test
    @DisplayName("register stores a token owned by the given user")
    void registerStoresOwnedToken() {
        pushService.register(testUser.getId(), "tok-A", "android");

        assertThat(deviceTokenRepository.findByUserId(testUser.getId()))
                .extracting(DeviceToken::getToken)
                .containsExactly("tok-A");
    }

    @Test
    @DisplayName("a user cannot unregister another user's token (owner-scoped delete)")
    void cannotUnregisterAnothersToken() {
        pushService.register(testUser.getId(), "tok-A", "android");
        AppUser other = createAdditionalUser("other-sub", "other@example.com");

        // The other user tries to remove testUser's token — it must not be deleted.
        pushService.unregister(other.getId(), "tok-A");

        assertThat(deviceTokenRepository.findByToken("tok-A")).isPresent();
        assertThat(deviceTokenRepository.findByUserId(testUser.getId())).hasSize(1);
    }

    @Test
    @DisplayName("re-registering an existing token moves it to the new owner, no duplicate")
    void reRegisterMovesOwnerWithoutDuplicating() {
        pushService.register(testUser.getId(), "tok-A", "android");
        AppUser other = createAdditionalUser("other-sub", "other@example.com");

        pushService.register(other.getId(), "tok-A", "android");

        assertThat(deviceTokenRepository.findByUserId(testUser.getId())).isEmpty();
        assertThat(deviceTokenRepository.findByUserId(other.getId()))
                .extracting(DeviceToken::getToken)
                .containsExactly("tok-A");
        assertThat(deviceTokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("without FCM credentials, sending is disabled and reaches zero devices")
    void sendingDisabledWithoutCredentials() {
        pushService.register(testUser.getId(), "tok-A", "android");

        assertThat(pushService.isEnabled()).isFalse();
        assertThat(pushService.sendToUser(testUser.getId(), "t", "b")).isZero();
    }
}
