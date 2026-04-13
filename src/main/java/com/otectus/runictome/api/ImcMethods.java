package com.otectus.runictome.api;

/**
 * Stable identifiers used as the `method` field in InterModComms messages
 * sent to Runic Tome. Third-party mods should reference these constants
 * (or hardcode their string values) when calling
 * {@code InterModComms.sendTo("runictome", method, supplier)}.
 */
public final class ImcMethods {

    private ImcMethods() {}

    /** Payload: {@code Supplier<GuideSystemAdapter>} */
    public static final String REGISTER_ADAPTER = "register_adapter";

    /** Payload: {@code Supplier<BookKey>} — convenience for single-book registration */
    public static final String REGISTER_BOOK = "register_book";
}
