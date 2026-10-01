package com.reist.enemyspellcast.loadout;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedPickerTest {
    @Test
    void picksWithoutDuplicatesAndHonorsRequestedCount() {
        var entries = List.of(new WeightedEntry("a", 1), new WeightedEntry("b", 1),
                new WeightedEntry("c", 1));

        var picked = WeightedPicker.pick(entries, 3, WeightedEntry::weight,
                new SequenceRolls(0.0, 0.0, 0.0));

        assertEquals(3, picked.size());
        assertEquals(3, new HashSet<>(picked).size());
    }

    @Test
    void countLargerThanPoolReturnsWholePool() {
        var entries = List.of(new WeightedEntry("a", 1), new WeightedEntry("b", 4));
        assertEquals(2, WeightedPicker.pick(entries, 8, WeightedEntry::weight, () -> 0.99).size());
    }

    private record WeightedEntry(String id, int weight) {
    }

    private static final class SequenceRolls implements RollSource {
        private final double[] values;
        private int index;

        private SequenceRolls(double... values) {
            this.values = values;
        }

        @Override
        public double nextDouble() {
            return values[Math.min(index++, values.length - 1)];
        }
    }
}
