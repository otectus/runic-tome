package com.otectus.runictome.api;

public final class NameFormat {

    private NameFormat() {}

    /**
     * Convert a registry path like {@code book_of_the_dead} or
     * {@code category/intro} into a human-friendly title-cased string.
     * Used as a fallback when an adapter cannot resolve a localized
     * display name.
     */
    public static String titleCase(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String spaced = raw.replace('_', ' ').replace('/', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean atStart = true;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (Character.isWhitespace(c)) {
                out.append(c);
                atStart = true;
            } else if (atStart) {
                out.append(Character.toUpperCase(c));
                atStart = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
