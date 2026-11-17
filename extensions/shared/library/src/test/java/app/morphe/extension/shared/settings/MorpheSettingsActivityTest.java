/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.settings;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class MorpheSettingsActivityTest {
    @Test
    public void ordersASparseClientMenuWithoutAddingMissingCategories() {
        List<String> sections = new ArrayList<>(Arrays.asList(
                "morphe_category_media",
                "morphe_category_login_api",
                "morphe_category_links",
                "morphe_category_diagnostics"
        ));

        sections.sort(Comparator.comparingInt(key ->
                MorpheSettingsActivity.sectionOrder(key, Integer.MAX_VALUE)));

        assertEquals(Arrays.asList(
                "morphe_category_links",
                "morphe_category_media",
                "morphe_category_login_api",
                "morphe_category_diagnostics"
        ), sections);
    }

    @Test
    public void ordersAdditionalCategoriesBeforeUnknownSectionsWithoutReorderingUnknowns() {
        List<String> sections = new ArrayList<>(Arrays.asList(
                "custom_first",
                "morphe_category_other_patches",
                "morphe_category_navigation",
                "morphe_category_interface",
                "custom_second",
                "morphe_category_feed",
                "morphe_category_comments_posting",
                "morphe_category_advanced",
                "morphe_category_ads_tracking",
                "morphe_category_undelete"
        ));

        sections.sort(Comparator.comparingInt(key ->
                MorpheSettingsActivity.sectionOrder(key, Integer.MAX_VALUE)));

        assertEquals(Arrays.asList(
                "morphe_category_interface",
                "morphe_category_feed",
                "morphe_category_navigation",
                "morphe_category_comments_posting",
                "morphe_category_undelete",
                "morphe_category_ads_tracking",
                "morphe_category_other_patches",
                "morphe_category_advanced",
                "custom_first",
                "custom_second"
        ), sections);
    }
}
