package se.lth.math.videoimucapture;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

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
}
