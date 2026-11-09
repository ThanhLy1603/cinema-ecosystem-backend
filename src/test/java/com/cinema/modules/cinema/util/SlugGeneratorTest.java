package com.cinema.modules.cinema.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugGeneratorTest {

    @Test
    void removesVietnameseDiacriticsAndHyphenates() {
        assertEquals("star-cineplex-landmark-81", SlugGenerator.toSlug("Star Cineplex Landmark 81"));
        assertEquals("rap-dong-da-ha-noi", SlugGenerator.toSlug("Rạp Đống Đa - Hà Nội"));
    }

    @Test
    void collapsesSymbolsAndTrimsEdges() {
        assertEquals("cgv-vincom", SlugGenerator.toSlug("  ** CGV  /// Vincom !! "));
    }

    @Test
    void fallsBackWhenNothingUsableRemains() {
        assertEquals("cinema", SlugGenerator.toSlug("***"));
        assertEquals("cinema", SlugGenerator.toSlug(null));
    }
}
