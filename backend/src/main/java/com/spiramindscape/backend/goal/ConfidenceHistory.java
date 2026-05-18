package com.spiramindscape.backend.goal;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "confidence_history")
@Getter
@Setter
public class ConfidenceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @NotNull
    @Min(1)
    @Max(10)
    @Column(name = "confidence_rating", nullable = false)
    private Integer confidence;

    @NotNull
    @Column(name = "at", nullable = false)
    private Instant at;

    @PrePersist
    protected void onCreate() {
        if (this.at == null) {
            this.at = Instant.now();
        }
    }
}
