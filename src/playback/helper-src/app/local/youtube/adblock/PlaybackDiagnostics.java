package app.local.youtube.adblock;

/** Playback diagnostics disabled in v0.1.10.
 * Public static entry points are retained as no-ops for binary compatibility.
 */
public final class PlaybackDiagnostics {
    public static void spoofResponse(int status, Object selectedClient) { }
    public static void mediaResponse(Object info) { }
    public static void apiResponse(Object info) { }
    public static void mediaFailed(Object info, Object error) { }
    public static void apiFailed(Object info, Object error) { }
    public static void install(Object activity) { }
    public static void captureCrash(Throwable failure) { }
    static String report() { return "diagnostics_disabled=true\n"; }
}
