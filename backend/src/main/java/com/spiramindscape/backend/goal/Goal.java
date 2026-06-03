package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.Target;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goal")
@Getter
@Setter
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = GoalService.MAX_GOAL_TITLE_LENGTH)
    private String title;

    @Size(max = GoalService.MAX_GOAL_DESCRIPTION_LENGTH)
    @Column(columnDefinition = "TEXT")
    private String description = "";

    @NotNull
    @Min(1)
    @Max(10)
    @Column(name = "confidence_rating")
    private Integer confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RealityItem> realityItems = new ArrayList<>();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Option> options = new ArrayList<>();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Resource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Target> targets = new ArrayList<>();

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("at DESC")
    private List<ConfidenceHistory> confidenceHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.description == null) {
            this.description = "";
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (this.description == null) {
            this.description = "";
        }
        this.updatedAt = Instant.now();
    }


}
