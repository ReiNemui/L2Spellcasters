package com.reist.enemyspellcast.ai;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InstallFingerprintTest {
    @Test
    void unchangedFiftyMobStateRefreshesOnlyOncePerMob() {
        Map<Integer, InstallFingerprint> installed = new HashMap<>();
        int refreshes = 0;
        for (int tick = 0; tick < 200; tick++) {
            for (int mob = 0; mob < 50; mob++) {
                InstallFingerprint next = new InstallFingerprint(3, 240, 7, 11);
                if (!next.equals(installed.put(mob, next))) {
                    refreshes++;
                }
            }
        }
        assertEquals(50, refreshes);
    }

    @Test
    void everyFingerprintInputInvalidatesTheFastPath() {
        InstallFingerprint base = new InstallFingerprint(3, 240, 7, 11);
        assertNotEquals(base, new InstallFingerprint(4, 240, 7, 11));
        assertNotEquals(base, new InstallFingerprint(3, 241, 7, 11));
        assertNotEquals(base, new InstallFingerprint(3, 240, 8, 11));
        assertNotEquals(base, new InstallFingerprint(3, 240, 7, 12));
    }
}
