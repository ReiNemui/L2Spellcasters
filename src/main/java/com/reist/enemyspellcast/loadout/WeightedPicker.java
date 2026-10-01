package com.reist.enemyspellcast.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class WeightedPicker {
    private WeightedPicker() {
    }

    public static <T> List<T> pick(List<T> source, int count, ToIntFunction<T> weight,
                                   RollSource rolls) {
        if (count <= 0 || source.isEmpty()) {
            return List.of();
        }
        var remaining = new ArrayList<>(source);
        var result = new ArrayList<T>(Math.min(count, source.size()));
        while (!remaining.isEmpty() && result.size() < count) {
            long total = totalWeight(remaining, weight);
            double roll = rolls.nextDouble();
            if (!Double.isFinite(roll)) {
                roll = 0.0;
            }
            roll = Math.max(0.0, Math.min(Math.nextDown(1.0), roll));
            double cursor = roll * total;
            int selected = remaining.size() - 1;
            for (int index = 0; index < remaining.size(); index++) {
                cursor -= safeWeight(remaining.get(index), weight);
                if (cursor < 0.0) {
                    selected = index;
                    break;
                }
            }
            result.add(remaining.remove(selected));
        }
        return List.copyOf(result);
    }

    private static <T> long totalWeight(List<T> values, ToIntFunction<T> weight) {
        long total = 0L;
        for (T value : values) {
            long next = safeWeight(value, weight);
            total = total > Long.MAX_VALUE - next ? Long.MAX_VALUE : total + next;
        }
        return total;
    }

    private static <T> int safeWeight(T value, ToIntFunction<T> weight) {
        return Math.max(1, weight.applyAsInt(value));
    }
}
