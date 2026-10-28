package app.local.youtube.adblock;

import java.io.OutputStream;
import java.lang.reflect.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded device-local outcomes. No identifiers, URL text, headers or bodies
 * enter the report. Exception messages are excluded, including account data. */
public final class PlaybackDiagnostics {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<String>();
    private static final long SINCE = System.currentTimeMillis();
    private static Object context, outputUri;
    private static int responses, failures, writes;
    private static String crash = "unobserved";
    private static ScheduledExecutorService worker;

    private static synchronized void event(String value) {
        if (EVENTS.size() >= 50) EVENTS.removeFirst();
        EVENTS.addLast((System.currentTimeMillis() - SINCE) + "ms " + value);
    }
    private static String client(Object value) {
        return value instanceof Enum ? ((Enum<?>) value).name() : "unknown";
    }
    public static void spoofResponse(int status, Object selectedClient) {
        try { synchronized (PlaybackDiagnostics.class) { responses++; }
            event("player_http=" + status + " client=" + client(selectedClient));
        } catch (Throwable ignored) {}
    }
    public static void mediaResponse(Object info) { response(info, false); }
    public static void apiResponse(Object info) { response(info, true); }
    public static void mediaFailed(Object info, Object error) { failed(info, error, false); }
    public static void apiFailed(Object info, Object error) { failed(info, error, true); }

    private static String route(Object info) {
        try {
            String value = (String) Class.forName("org.chromium.net.UrlResponseInfo").getMethod("getUrl").invoke(info);
            URI uri = URI.create(value);
            String host = uri.getHost(), path = uri.getPath();
            if (host != null && (host.equals("googlevideo.com") || host.endsWith(".googlevideo.com"))) return "stream";
            if (path != null && (path.endsWith("/player") || path.contains("get_watch") || path.contains("initplayback"))) return "player";
        } catch (Throwable ignored) {}
        return "other";
    }
    private static void response(Object info, boolean filter) {
        try {
            String kind = filter ? route(info) : "media";
            if (kind.equals("other")) return;
            int status = ((Number) Class.forName("org.chromium.net.UrlResponseInfo").getMethod("getHttpStatusCode").invoke(info)).intValue();
            synchronized (PlaybackDiagnostics.class) { responses++; }
            event(kind + "_http=" + status);
        } catch (Throwable ignored) {}
    }
    private static void failed(Object info, Object error, boolean filter) {
        try {
            String kind = filter ? route(info) : "media";
            if (kind.equals("other")) return;
            response(info, filter);
            String codes = "code=unavailable internal=unavailable";
            Class<?> type = Class.forName("org.chromium.net.NetworkException");
            if (type.isInstance(error)) codes = "code=" + type.getMethod("getErrorCode").invoke(error)
                + " internal=" + type.getMethod("getCronetInternalErrorCode").invoke(error);
            synchronized (PlaybackDiagnostics.class) { failures++; }
            event(kind + "_failed " + codes);
        } catch (Throwable ignored) {}
    }
    public static void install(Object activity) {
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            context = Class.forName("android.content.Context").getMethod("getApplicationContext").invoke(activity);
            if (context == null) return;
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread thread, Throwable failure) {
                    try { captureCrash(failure); flush(); } catch (Throwable ignored) {}
                    finally { if (previous != null) previous.uncaughtException(thread, failure); }
                }
            });
            worker = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                public Thread newThread(Runnable task) {
                    Thread thread = new Thread(task, "PlaybackReport"); thread.setDaemon(true); return thread;
                }
            });
            worker.scheduleWithFixedDelay(new Runnable() {
                public void run() { flush(); if (writes >= 125) worker.shutdown(); }
            }, 2, 5, TimeUnit.SECONDS);
            event("report_started");
        } catch (Throwable ignored) {}
    }
    public static void captureCrash(Throwable failure) {
        try {
            StringBuilder text = new StringBuilder();
            for (int cause = 0; failure != null && cause < 5; cause++, failure = failure.getCause()) {
                text.append(failure.getClass().getName()).append('\n');
                if (failure.getClass().getName().equals("android.content.res.Resources$NotFoundException")) {
                    java.util.regex.Matcher ids = java.util.regex.Pattern.compile("0x[0-9a-fA-F]{1,8}").matcher(String.valueOf(failure.getMessage()));
                    for (int count = 0; count < 8 && ids.find(); count++) text.append("  resource_id=").append(ids.group()).append('\n');
                }
                StackTraceElement[] frames = failure.getStackTrace();
                for (int i = 0; i < frames.length && i < 24; i++)
                    text.append("  ").append(frames[i].getClassName()).append('.').append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber()).append('\n');
            }
            synchronized (PlaybackDiagnostics.class) { crash = text.toString(); }
        } catch (Throwable ignored) {}
    }
    private static String flag(String owner, String method) {
        try { return String.valueOf(Class.forName(owner).getMethod(method).invoke(null)); }
        catch (Throwable ignored) { return "unavailable"; }
    }
    private static String mainStack() {
        try {
            Class<?> looper = Class.forName("android.os.Looper");
            Object main = looper.getMethod("getMainLooper").invoke(null);
            Thread thread = (Thread) looper.getMethod("getThread").invoke(main);
            StringBuilder text = new StringBuilder(thread.getState().name());
            StackTraceElement[] frames = thread.getStackTrace();
            for (int i = 0; i < frames.length && i < 16; i++)
                text.append('\n').append("  ").append(frames[i].getClassName()).append('.').append(frames[i].getMethodName());
            return text.toString();
        } catch (Throwable ignored) { return "unavailable"; }
    }
    static String report() {
        String state = "unavailable", preferred = "unavailable";
        boolean extensionContextReady = false;
        try {
            Class<?> utils = Class.forName("app.morphe.extension.shared.utils.Utils");
            Field appContext = utils.getDeclaredField("context"); appContext.setAccessible(true);
            extensionContextReady = appContext.get(null) != null;
        } catch (Throwable ignored) {}
        if (extensionContextReady) try {
            Class<?> type = Class.forName("app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch");
            Field enabled = type.getDeclaredField("SPOOF_VIDEO_STREAMS"); enabled.setAccessible(true);
            state = String.valueOf(enabled.getBoolean(null));
            preferred = client(type.getMethod("getPreferredClient").invoke(null));
        } catch (Throwable ignored) {}
        StringBuilder text = new StringBuilder("build=v0.1.7\nextension_context_ready=" + extensionContextReady + "\nstream_repair_enabled=" + state + "\npreferred_client=" + preferred + "\n");
        text.append("gms_support=").append(flag("app.morphe.extension.shared.patches.PatchStatus", "GmsCoreSupport")).append('\n');
        text.append("last_stream_uses_sabr=").append(extensionContextReady ? flag("app.morphe.extension.shared.spoof.requests.StreamOrDetailsDataRequest", "getLastSpoofedClientUseSABR") : "unavailable").append('\n');
        synchronized (PlaybackDiagnostics.class) {
            text.append("response_observations=").append(responses).append("\nnetwork_failures=").append(failures).append("\ncrash:\n").append(crash).append("\nevents:\n");
            for (String event : EVENTS) text.append(event).append('\n');
        }
        text.append("main_thread:\n").append(mainStack()).append('\n');
        text.append("urls_headers_bodies_identifiers_collected=false\n");
        return text.toString();
    }
    private static void flush() {
        if (context == null || writes++ >= 125) return;
        try {
            byte[] bytes = report().getBytes(StandardCharsets.UTF_8);
            Class<?> ctx = Class.forName("android.content.Context");
            java.io.File files = (java.io.File) ctx.getMethod("getFilesDir").invoke(context);
            Files.write(new java.io.File(files, "youtube-playback-report.txt").toPath(), bytes);
            Object resolver = ctx.getMethod("getContentResolver").invoke(context);
            Class<?> resolverType = Class.forName("android.content.ContentResolver"), uri = Class.forName("android.net.Uri");
            Class<?> valuesType = Class.forName("android.content.ContentValues");
            if (outputUri == null) {
                Object values = valuesType.getConstructor().newInstance();
                Method put = valuesType.getMethod("put", String.class, String.class);
                put.invoke(values, "_display_name", "YouTube-v0.1.7-playback-" + SINCE + ".txt");
                put.invoke(values, "mime_type", "text/plain");
                put.invoke(values, "relative_path", "Download/YouTube-Adblock-Diagnostics/");
                Object collection = Class.forName("android.provider.MediaStore$Downloads").getField("EXTERNAL_CONTENT_URI").get(null);
                outputUri = resolverType.getMethod("insert", uri, valuesType).invoke(resolver, collection, values);
            }
            if (outputUri != null) try (OutputStream stream = (OutputStream) resolverType.getMethod("openOutputStream", uri, String.class).invoke(resolver, outputUri, "wt")) {
                if (stream != null) stream.write(bytes);
            }
        } catch (Throwable ignored) {}
    }
}
