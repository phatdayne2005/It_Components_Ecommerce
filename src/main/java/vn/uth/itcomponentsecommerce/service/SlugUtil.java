package vn.uth.itcomponentsecommerce.service;

import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtil {

    private SlugUtil() {}

    public static String toSlug(String input) {
        if (input == null) return null;
        String nowhitespace = input.trim().replaceAll("\\s+", "-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String slug = normalized.replaceAll("[^a-zA-Z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .toLowerCase(Locale.ROOT);
        return slug.replaceAll("^-|-$", "");
    }
}
