package se.lth.math.videoimucapture;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GridTrialSequenceTest {
    @Test public void trialsStartAtOneAndNeverRepeatThePreviousTarget() {
        GridTrialSequence sequence = new GridTrialSequence(new Random(1234));
        sequence.start();

        assertTrue(sequence.isActive());
        assertEquals(1, sequence.getTrialId());
        assertTrue(sequence.getTargetLabel().matches("KEY_[1-9]"));

        for (long trial = 2; trial <= 100; trial++) {
            int previous = sequence.getTargetIndex();
            sequence.advance();
            assertEquals(trial, sequence.getTrialId());
            assertNotEquals(previous, sequence.getTargetIndex());
        }
    }

    @Test public void stopClearsAnnotationState() {
        GridTrialSequence sequence = new GridTrialSequence(new Random(1));
        sequence.start();
        sequence.stop();

        assertFalse(sequence.isActive());
        assertEquals(-1, sequence.getTrialId());
        assertEquals(-1, sequence.getTargetIndex());
        assertEquals("", sequence.getTargetLabel());
    }

    @Test public void customModeOnlyUsesSelectedTargets() {
        GridTrialSequence sequence = new GridTrialSequence(new Random(7));
        sequence.setAllowedTargetIndices(new int[]{0, 3, 8});
        sequence.start();

        for (int i = 0; i < 100; i++) {
            int target = sequence.getTargetIndex();
            assertTrue(target == 0 || target == 3 || target == 8);
            int previous = target;
            sequence.advance();
            assertNotEquals(previous, sequence.getTargetIndex());
        }
    }

    @Test public void singleCustomTargetCanRepeatAcrossTrials() {
        GridTrialSequence sequence = new GridTrialSequence(new Random(9));
        sequence.setAllowedTargetIndices(new int[]{4});
        sequence.start();

        for (long trial = 1; trial <= 10; trial++) {
            assertEquals(trial, sequence.getTrialId());
            assertEquals(4, sequence.getTargetIndex());
            assertEquals("KEY_5", sequence.getTargetLabel());
            sequence.advance();
        }
    }

    @Test public void emptyCustomTargetSelectionIsRejected() {
        GridTrialSequence sequence = new GridTrialSequence(new Random(1));
        try {
            sequence.setAllowedTargetIndices(new int[0]);
            fail("Expected empty target selection to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("At least one"));
        }
    }
}
