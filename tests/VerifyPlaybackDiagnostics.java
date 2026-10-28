import java.lang.reflect.*;

/** Actual-APK report privacy and native cancelled-callback contracts on ART. */
public final class VerifyPlaybackDiagnostics {
    public static void main(String[] args) {
        try { run(args); } catch (Throwable failure) { failure.printStackTrace(System.out); System.exit(1); }
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void run(String[] args) throws Exception {
        ClassLoader loader = (ClassLoader) Class.forName("dalvik.system.DexClassLoader")
            .getConstructor(String.class, String.class, String.class, ClassLoader.class)
            .newInstance(args[0], args[1], null, VerifyPlaybackDiagnostics.class.getClassLoader());
        Class<?> diagnostic = Class.forName("app.local.youtube.adblock.PlaybackDiagnostics", true, loader);
        String secret = "sensitive-fixture-https://example.invalid/media?token=fixture-account";
        diagnostic.getMethod("captureCrash", Throwable.class).invoke(null, new IllegalStateException(secret));
        diagnostic.getMethod("spoofResponse", int.class, Object.class).invoke(null, 403, null);
        Method report = diagnostic.getDeclaredMethod("report"); report.setAccessible(true);
        String text = (String) report.invoke(null);
        check(text.contains("java.lang.IllegalStateException") && text.contains("player_http=403"), "Report lost actual failure class or HTTP status");
        check(!text.contains(secret) && !text.contains("token=fixture-account"), "Report exposed exception message data");
        check(text.contains("extension_context_ready=false"), "Uninitialized extension context was not detected");
        Class<?> callback = Class.forName("dlt", true, loader);
        Constructor<?> constructor = callback.getDeclaredConstructor(Class.forName("dlu", false, loader)); constructor.setAccessible(true);
        Object instance = constructor.newInstance(new Object[]{null});
        Field cancelled = callback.getDeclaredField("a"); cancelled.setAccessible(true); cancelled.setBoolean(instance, true);
        Class<?> request = Class.forName("org.chromium.net.UrlRequest", false, loader);
        Class<?> info = Class.forName("org.chromium.net.UrlResponseInfo", false, loader);
        Class<?> error = Class.forName("org.chromium.net.CronetException", false, loader);
        Method response = callback.getDeclaredMethod("onResponseStarted", request, info); response.setAccessible(true);
        Method failed = callback.getDeclaredMethod("onFailed", request, info, error); failed.setAccessible(true);
        response.invoke(instance, new Object[]{null, null});
        failed.invoke(instance, new Object[]{null, null, null});
        check(cancelled.getBoolean(instance), "Observation changed the native cancelled-callback state");
        text = (String) report.invoke(null);
        check(text.contains("media_failed code=unavailable internal=unavailable"), "Actual callback did not reach the failure observation");
        for (int i = 0; i < 100; i++) diagnostic.getMethod("spoofResponse", int.class, Object.class).invoke(null, 200, null);
        text = (String) report.invoke(null);
        String events = text.substring(text.indexOf("events:\n") + 8, text.indexOf("main_thread:\n"));
        check(events.split("\n").length <= 50, "Playback report events grew without a bound");
        System.out.println("PASS: actual report captures HTTP/error types, omits sensitive messages, and bounds events");
        System.out.println("PASS: actual native cancelled callbacks preserve behavior with null response/error inputs");
    }
}
