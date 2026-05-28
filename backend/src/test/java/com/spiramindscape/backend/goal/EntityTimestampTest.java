package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTimestampTest {

    @Test
    @DisplayName("Goal @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void goalHooksSetTimestamps() throws InterruptedException {
        Goal goal = new Goal();
        goal.onCreate();
        assertInitialTimestamps(goal.getCreatedAt(), goal.getUpdatedAt());

        waitForTimestampAdvance();
        goal.onUpdate();
        assertThat(goal.getUpdatedAt()).isAfter(goal.getCreatedAt());
    }

    @Test
    @DisplayName("Option @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void optionHooksSetTimestamps() throws InterruptedException {
        Option option = new Option();
        option.onCreate();
        assertInitialTimestamps(option.getCreatedAt(), option.getUpdatedAt());

        waitForTimestampAdvance();
        option.onUpdate();
        assertThat(option.getUpdatedAt()).isAfter(option.getCreatedAt());
    }

    @Test
    @DisplayName("RealityItem @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void realityItemHooksSetTimestamps() throws InterruptedException {
        RealityItem item = new RealityItem();
        item.onCreate();
        assertInitialTimestamps(item.getCreatedAt(), item.getUpdatedAt());

        waitForTimestampAdvance();
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    @Test
    @DisplayName("Target @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void targetHooksSetTimestamps() throws InterruptedException {
        Target target = new Target();
        target.onCreate();
        assertInitialTimestamps(target.getCreatedAt(), target.getUpdatedAt());

        waitForTimestampAdvance();
        target.onUpdate();
        assertThat(target.getUpdatedAt()).isAfter(target.getCreatedAt());
    }

    @Test
    @DisplayName("Resource @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void resourceHooksSetTimestamps() throws InterruptedException {
        Resource resource = new Resource();
        resource.onCreate();
        assertInitialTimestamps(resource.getCreatedAt(), resource.getUpdatedAt());

        waitForTimestampAdvance();
        resource.onUpdate();
        assertThat(resource.getUpdatedAt()).isAfter(resource.getCreatedAt());
    }

    @Test
    @DisplayName("ChecklistItem @PrePersist sets createdAt and updatedAt; @PreUpdate advances updatedAt")
    void checklistItemHooksSetTimestamps() throws InterruptedException {
        ChecklistItem item = new ChecklistItem();
        item.onCreate();
        assertInitialTimestamps(item.getCreatedAt(), item.getUpdatedAt());

        waitForTimestampAdvance();
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    private static void waitForTimestampAdvance() throws InterruptedException {
        Thread.sleep(50);
    }

    private void assertInitialTimestamps(Instant createdAt, Instant updatedAt) {
        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(updatedAt).isAfterOrEqualTo(createdAt);
        assertThat(Duration.between(createdAt, updatedAt)).isLessThan(Duration.ofMillis(50));
    }
}
