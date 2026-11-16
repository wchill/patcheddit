/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MorpheSettingsStyleTest {
    @Test
    public void mandatoryEdgeToEdgeIgnoresLegacyRequestedBarColors() {
        assertFalse(MorpheSettingsStyle.transparentBars(34, 36, false));
        assertTrue(MorpheSettingsStyle.transparentBars(35, 35, false));
        assertFalse(MorpheSettingsStyle.transparentBars(35, 35, true));
        assertFalse(MorpheSettingsStyle.transparentBars(36, 34, false));
        assertTrue(MorpheSettingsStyle.transparentBars(36, 36, true));
    }

    @Test
    public void transparentSystemBarsFollowTheNativeSurface() {
        assertTrue(MorpheSettingsStyle.lightBar(0x00000000, 0xFFF9F9FF));
        assertFalse(MorpheSettingsStyle.lightBar(0x00000000, 0xFF121212));
    }

    @Test
    public void opaqueBarsKeepTheirOwnContrastInsteadOfFollowingTheContent() {
        assertFalse(MorpheSettingsStyle.lightBar(0xFF3376D8, 0xFFFFFFFF));
        assertTrue(MorpheSettingsStyle.lightBar(0xFFFFFFFF, 0xFF000000));
    }
}
