package app.local.youtube.adblock;

/** Local diagnostics disabled in v0.1.10.
 * Public static entry points are retained as no-ops for binary compatibility.
 */
public final class LocalDiagnostics {
    public static void adHidden() { }
    public static void shortsEntriesFiltered(int count) { }
    public static void filterFailed() { }
    public static void initialize(Object activity) { }
}
