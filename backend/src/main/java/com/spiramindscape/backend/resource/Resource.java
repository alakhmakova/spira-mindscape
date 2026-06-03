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

    @Pattern(regexp = "note|link|file|email")
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

    @Column(length = 200)
    private String role;

    @Column(length = 200)
    private String email;

    @Column(length = 50)
    private String phone;

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
