package se.lth.math.videoimucapture;

import java.util.Random;

/** Small state holder for the foreground 3x3 touch classification experiment. */
final class GridTrialSequence {
    private static final String[] LABELS = {
            "KEY_1", "KEY_2", "KEY_3",
            "KEY_4", "KEY_5", "KEY_6",
            "KEY_7", "KEY_8", "KEY_9"
    };

    private final Random random;
    private int[] allowedTargetIndices = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private long trialId = -1;
    private int targetIndex = -1;

    GridTrialSequence() {
        this(new Random());
    }

    GridTrialSequence(Random random) {
        this.random = random;
    }

    void start() {
        trialId = 1;
        targetIndex = allowedTargetIndices[random.nextInt(allowedTargetIndices.length)];
    }

    void advance() {
        if (!isActive()) return;
        if (allowedTargetIndices.length == 1) {
            targetIndex = allowedTargetIndices[0];
        } else {
            int currentPosition = indexOfAllowedTarget(targetIndex);
            targetIndex = allowedTargetIndices[(currentPosition + 1
                    + random.nextInt(allowedTargetIndices.length - 1))
                    % allowedTargetIndices.length];
        }
        trialId++;
    }

    void setAllowedTargetIndices(int[] targetIndices) {
        if (targetIndices == null || targetIndices.length == 0) {
            throw new IllegalArgumentException("At least one grid target is required");
        }
        boolean[] seen = new boolean[LABELS.length];
        int[] copy = targetIndices.clone();
        for (int target : copy) {
            if (target < 0 || target >= LABELS.length || seen[target]) {
                throw new IllegalArgumentException("Invalid or duplicate grid target: " + target);
            }
            seen[target] = true;
        }
        allowedTargetIndices = copy;
        if (isActive() && indexOfAllowedTarget(targetIndex) < 0) {
            targetIndex = allowedTargetIndices[random.nextInt(allowedTargetIndices.length)];
        }
    }

    private int indexOfAllowedTarget(int target) {
        for (int i = 0; i < allowedTargetIndices.length; i++) {
            if (allowedTargetIndices[i] == target) return i;
        }
        return -1;
    }

    void stop() {
        trialId = -1;
        targetIndex = -1;
    }

    boolean isActive() {
        return targetIndex >= 0;
    }

    long getTrialId() {
        return trialId;
    }

    int getTargetIndex() {
        return targetIndex;
    }

    String getTargetLabel() {
        return isActive() ? LABELS[targetIndex] : "";
    }
}
