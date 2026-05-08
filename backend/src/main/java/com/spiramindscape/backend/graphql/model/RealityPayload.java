package com.spiramindscape.backend.graphql.model;

import com.spiramindscape.backend.goal.RealityItem;

import java.util.List;

public record RealityPayload(
        Long id,
        List<RealityItem> actions,
        List<RealityItem> obstacles
) {
}
