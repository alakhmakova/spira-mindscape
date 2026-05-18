package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTimestampTest {

    @Test
    void goalHooksSetTimestamps() throws InterruptedException {
        Goal goal = new Goal();
        goal.onCreate();
        assertThat(goal.getCreatedAt()).isNotNull();
        assertThat(goal.getUpdatedAt()).isEqualTo(goal.getCreatedAt());

        Thread.sleep(1);
        goal.onUpdate();
        assertThat(goal.getUpdatedAt()).isAfter(goal.getCreatedAt());
    }

    @Test
    void optionHooksSetTimestamps() throws InterruptedException {
        Option option = new Option();
        option.onCreate();
        assertThat(option.getCreatedAt()).isNotNull();
        assertThat(option.getUpdatedAt()).isEqualTo(option.getCreatedAt());

        Thread.sleep(1);
        option.onUpdate();
        assertThat(option.getUpdatedAt()).isAfter(option.getCreatedAt());
    }

    @Test
    void realityItemHooksSetTimestamps() throws InterruptedException {
        RealityItem item = new RealityItem();
        item.onCreate();
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isEqualTo(item.getCreatedAt());

        Thread.sleep(1);
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }

    @Test
    void targetHooksSetTimestamps() throws InterruptedException {
        Target target = new Target();
        target.onCreate();
        assertThat(target.getCreatedAt()).isNotNull();
        assertThat(target.getUpdatedAt()).isEqualTo(target.getCreatedAt());

        Thread.sleep(1);
        target.onUpdate();
        assertThat(target.getUpdatedAt()).isAfter(target.getCreatedAt());
    }

    @Test
    void resourceHooksSetTimestamps() throws InterruptedException {
        Resource resource = new Resource();
        resource.onCreate();
        assertThat(resource.getCreatedAt()).isNotNull();
        assertThat(resource.getUpdatedAt()).isEqualTo(resource.getCreatedAt());

        Thread.sleep(1);
        resource.onUpdate();
        assertThat(resource.getUpdatedAt()).isAfter(resource.getCreatedAt());
    }

    @Test
    void checklistItemHooksSetTimestamps() throws InterruptedException {
        ChecklistItem item = new ChecklistItem();
        item.onCreate();
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isEqualTo(item.getCreatedAt());

        Thread.sleep(10);
        item.onUpdate();
        assertThat(item.getUpdatedAt()).isAfterOrEqualTo(item.getCreatedAt());
        assertThat(item.getUpdatedAt()).isAfter(item.getCreatedAt());
    }
}
