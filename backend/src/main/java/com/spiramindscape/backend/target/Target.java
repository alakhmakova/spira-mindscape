package com.spiramindscape.backend.target;

import com.spiramindscape.backend.goal.Goal;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "target")
@Getter
@Setter
public class Target {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(regexp = "numeric|binary|checklist")
    @Column(nullable = false, length = 20)
    private String type;

    @NotBlank
    @Column(length = 200)
    private String title;

    @Column(name = "start_value")
    private Double start;

    @Column(name = "current_value")
    private Double current;

    @Column(name = "total_value")
    private Double total;

    @Column(length = 50)
    private String unit;

    @Column(nullable = false)
    private Boolean done = false;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "achieved_at")
    private Instant achievedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @OneToMany(mappedBy = "target", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChecklistItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        normalizeType();
        if (done == null) {
            done = false;
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeType();
        if (done == null) {
            done = false;
        }
        this.updatedAt = Instant.now();
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public double getProgress() {
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if ("binary".equals(normalizedType)) {
            return Boolean.TRUE.equals(done) ? 1 : 0;
        }

        if ("numeric".equals(normalizedType)) {
            double currentValue = current == null ? 0 : current;
            double totalValue = total == null ? 0 : total;
            double startValue = start == null
                    ? (currentValue > totalValue ? currentValue : 0)
                    : start;
            double distance = Math.abs(totalValue - startValue);
            if (distance == 0) {
                return currentValue == totalValue ? 1 : 0;
            }
            double completed = totalValue >= startValue
                    ? currentValue - startValue
                    : startValue - currentValue;
            return Math.max(0, Math.min(1, completed / distance));
        }

        if ("checklist".equals(normalizedType)) {
            if (items.isEmpty()) {
                return 0;
            }
            long completed = items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getDone()))
                    .count();
            return (double) completed / items.size();
        }

        return 0;
    }

    private void normalizeType() {
        if (type != null) {
            type = type.toLowerCase(Locale.ROOT);
        }
    }
}
