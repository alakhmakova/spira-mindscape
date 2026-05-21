package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTimestampTest {

    @Test
    void goalHooksSetTimestamps() throws InterruptedException {
        Goal goal = new Goal();
        goal.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(goal.getCreatedAt(), goal.getUpdatedAt());

        Thread.sleep(1);
        goal.onUpdate();
        assertThat(goal.getUpdatedAt()).isAfter(goal.getCreatedAt());
    }

    @Test
    void optionHooksSetTimestamps() throws InterruptedException {
        Option option = new Option();
        option.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(option.getCreatedAt(), option.getUpdatedAt());

        Thread.sleep(1);
        option.onUpdate();
        assertThat(option.getUpdatedAt()).isAfter(option.getCreatedAt());
    }

    @Test
    void realityItemHooksSetTimestamps() throws InterruptedException {
        RealityItem item = new RealityItem();
        item.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(item.getCreatedAt(), item.getUpdatedAt());

        Thread.sleep(1);
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    @Test
    void targetHooksSetTimestamps() throws InterruptedException {
        Target target = new Target();
        target.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(target.getCreatedAt(), target.getUpdatedAt());

        Thread.sleep(1);
        target.onUpdate();
        assertThat(target.getUpdatedAt()).isAfter(target.getCreatedAt());
    }

    @Test
    void resourceHooksSetTimestamps() throws InterruptedException {
        Resource resource = new Resource();
        resource.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(resource.getCreatedAt(), resource.getUpdatedAt());

        Thread.sleep(1);
        resource.onUpdate();
        assertThat(resource.getUpdatedAt()).isAfter(resource.getCreatedAt());
    }

    @Test
    void checklistItemHooksSetTimestamps() throws InterruptedException {
        ChecklistItem item = new ChecklistItem();
        item.onCreate();
        assertCreatedAndUpdatedAreInitializedTogether(item.getCreatedAt(), item.getUpdatedAt());

        Thread.sleep(10);
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfterOrEqualTo(item.getCreatedAt());
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    private void assertCreatedAndUpdatedAreInitializedTogether(Instant createdAt, Instant updatedAt) {
        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();
        assertThat(updatedAt).isAfterOrEqualTo(createdAt);
        assertThat(Duration.between(createdAt, updatedAt)).isLessThan(Duration.ofMillis(10));
    }
}
