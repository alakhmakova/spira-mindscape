package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "resource")
@Getter
@Setter
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(regexp = "note|link|file|email|vacancy")
    @Column(nullable = false, length = 20)
    private String type;

    @Size(max = 200)
    @Column(length = 200)
    private String title;

    @Size(max = 50000)
    @Column(columnDefinition = "TEXT")
    private String body;

    @Size(max = 1000)
    @Column(length = 1000)
    private String url;

    @Column(length = 100)
    private String mime;

    // Base64 data URL for file/image resources. Size is enforced in
    // ResourceService (5 MB on the decoded bytes) — the authoritative, byte-based
    // check. No @Size here: a char-count bound can't express a byte limit and the
    // column is TEXT (unbounded in Postgres).
    @Column(name = "data_url", columnDefinition = "TEXT")
    private String dataUrl;

    @Size(max = 200)
    @Column(length = 200)
    private String name;

    @Size(max = 200)
    @Column(length = 200)
    private String role;

    @Size(max = 200)
    @Column(length = 200)
    private String email;

    @Size(max = 50)
    @Column(length = 50)
    private String phone;

    // The vacancy map's document, as JSON — see VacancyMapPatch for its shape and why it is
    // written one field at a time. Like data_url above, it is loaded ON DEMAND only and is never
    // selected by the list projections (see ResourceView), so a goals fetch does not carry every
    // map. Size is enforced in ResourceService; no @Size here, for the same reason as data_url.
    @Column(name = "map_data", columnDefinition = "TEXT")
    private String mapData;

    /** Drive file id of the Google Doc created from this note (null until exported). */
    @Column(name = "drive_file_id", columnDefinition = "TEXT")
    private String driveFileId;

    /** Shareable link of the linked Google Doc (shown to the user / opened in a tab). */
    @Column(name = "drive_web_view_link", columnDefinition = "TEXT")
    private String driveWebViewLink;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        normalizeType();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {
        normalizeType();
        this.updatedAt = Instant.now();
    }

    private void normalizeType() {
        if (type != null) {
            type = type.toLowerCase(Locale.ROOT);
        }
    }
}
