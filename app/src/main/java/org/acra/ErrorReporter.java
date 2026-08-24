package org.acra;

/**
 * Compatibility shim for the optional ACRA hooks in the legacy Remote Your Cam
 * PTP engine. DSLR AI Coach does not use ACRA; AppConfig.USE_ACRA is false.
 */
public final class ErrorReporter {
    private static final ErrorReporter INSTANCE = new ErrorReporter();

    private ErrorReporter() {}

    public static ErrorReporter getInstance() {
        return INSTANCE;
    }

    public void putCustomData(String key, String value) {
        // Intentionally no-op. Kept only so the legacy optional reporting hooks compile.
    }
}
