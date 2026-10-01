package com.reist.enemyspellcast.ai;

/** Inputs that determine whether a Mob's spell-casting runtime needs refreshing. */
public record InstallFingerprint(
        int traitRank,
        int mobLevel,
        long configGeneration,
        long catalogGeneration
) {
}
