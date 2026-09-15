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
        targetIndex = random.nextInt(LABELS.length);
    }

    void advance() {
        if (!isActive()) return;
        // Select one of the other eight cells so two adjacent trials never look unchanged.
        targetIndex = (targetIndex + 1 + random.nextInt(LABELS.length - 1)) % LABELS.length;
        trialId++;
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
