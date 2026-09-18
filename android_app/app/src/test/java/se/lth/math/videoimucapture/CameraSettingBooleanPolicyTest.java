package se.lth.math.videoimucapture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CameraSettingBooleanPolicyTest {
    @Test public void normalToggleRequiresAdvertisedOffAndOnModes() {
        assertTrue(CameraSettingBoolean.isToggleConfigurable(true, true, true, false));
        assertFalse(CameraSettingBoolean.isToggleConfigurable(false, true, true, false));
        assertFalse(CameraSettingBoolean.isToggleConfigurable(true, false, true, false));
    }

    @Test public void oisToggleCanTryOffWhenVendorAdvertisesOnOnly() {
        assertTrue(CameraSettingBoolean.isToggleConfigurable(false, true, true, true));
        assertFalse(CameraSettingBoolean.isToggleConfigurable(false, false, true, true));
        assertFalse(CameraSettingBoolean.isToggleConfigurable(false, true, false, true));
    }
}
