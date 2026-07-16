package com.spiramindscape.backend.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Initializes the Firebase Admin SDK for sending FCM push notifications — but only when a
 * service-account credential is configured. Push is an optional capability:
 *
 * <ul>
 *   <li><b>Configured</b> (prod): set {@code app.fcm.credentials-json} (the service-account
 *       JSON, e.g. injected from Secret Manager) or {@code app.fcm.credentials-path} (a file
 *       path). A {@link FirebaseMessaging} bean is created and push works.</li>
 *   <li><b>Not configured</b> (local dev, CI, tests): the bean is {@code null}; the sender
 *       {@link PushNotificationService} degrades to a no-op. Nothing else is affected.</li>
 * </ul>
 *
 * <p>The credential is a secret and must come from the environment / Secret Manager — never
 * committed. Consumers inject this via {@code ObjectProvider<FirebaseMessaging>} so a null
 * (absent) bean is handled gracefully.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /** Inline service-account JSON (e.g. from Secret Manager). Takes precedence over the path. */
    @Value("${app.fcm.credentials-json:}")
    private String credentialsJson;

    /** Path to a service-account JSON file on disk. */
    @Value("${app.fcm.credentials-path:}")
    private String credentialsPath;

    /** Optional explicit project id; usually inferred from the credential. */
    @Value("${app.fcm.project-id:}")
    private String projectId;

    /**
     * Use Application Default Credentials (the service account the process already runs as)
     * instead of an explicit key file. This is the recommended path on Cloud Run: no
     * downloadable service-account key is needed (and orgs often forbid creating one via the
     * {@code iam.disableServiceAccountKeyCreation} policy). The Cloud Run service account just
     * needs permission to send FCM. Off by default so local dev doesn't try to reach ADC.
     */
    @Value("${app.fcm.use-application-default:false}")
    private boolean useApplicationDefault;

    /**
     * @return a ready {@link FirebaseMessaging}, or {@code null} when FCM isn't configured
     *         (injected only via {@code ObjectProvider}, which tolerates the null bean).
     */
    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try {
            GoogleCredentials credentials = loadCredentials();
            if (credentials == null) {
                log.info("FCM disabled: no credentials configured (set app.fcm.credentials-json, "
                        + "app.fcm.credentials-path, or app.fcm.use-application-default=true). "
                        + "Push notifications will be skipped.");
                return null;
            }
            FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
            if (!projectId.isBlank()) {
                options.setProjectId(projectId);
            }
            FirebaseApp app = FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(options.build())
                    : FirebaseApp.getInstance();
            log.info("FCM enabled: Firebase Admin initialized for push notifications.");
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            // Never let a push-config problem break app startup — push is optional.
            log.warn("FCM disabled: failed to initialize Firebase Admin ({}). "
                    + "Push notifications will be skipped.", e.getMessage());
            return null;
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        if (!credentialsJson.isBlank()) {
            try (InputStream in = new ByteArrayInputStream(
                    credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (!credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }
        if (useApplicationDefault) {
            // The service account the process runs as (Cloud Run) — no key file involved.
            return GoogleCredentials.getApplicationDefault();
        }
        return null;
    }
}
