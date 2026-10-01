package com.reist.enemyspellcast.allegiance;

import java.util.Objects;
import java.util.UUID;

public record FactionView(
        UUID id,
        String team,
        UUID owner,
        boolean hostile,
        UUID currentTarget,
        boolean playerAligned
) {
    public FactionView {
        Objects.requireNonNull(id, "id");
    }
}
