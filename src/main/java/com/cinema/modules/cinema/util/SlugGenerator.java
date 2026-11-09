package com.cinema.modules.cinema.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SlugGenerator {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
    private static final String FALLBACK_SLUG = "cinema";

    private SlugGenerator() {
    }

    public static String toSlug(String name) {
        if (name == null) {
            return FALLBACK_SLUG;
        }
        String withoutDiacritics = DIACRITICS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        String lowerCased = withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        String hyphenated = NON_ALPHANUMERIC.matcher(lowerCased).replaceAll("-");
        String slug = EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
        return slug.isEmpty() ? FALLBACK_SLUG : slug;
    }
}
