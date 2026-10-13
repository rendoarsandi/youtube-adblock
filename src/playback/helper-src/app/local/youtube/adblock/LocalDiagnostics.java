package app.local.youtube.adblock;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded local support report: ad counts, main-looper delay and exit reasons.
 * No account/provider values, headers, URLs, video IDs or network requests.
 * Android reflection keeps compilation stubs out of the delivered APK.
 */
public final class LocalDiagnostics {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicLong HIDDEN = new AtomicLong();
    private static final AtomicLong SHORTS_FILTERED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static final AtomicLong MAX_DELAY = new AtomicLong();
    private static final AtomicBoolean PING_PENDING = new AtomicBoolean();

    public static void adHidden() { HIDDEN.incrementAndGet(); }
    public static void shortsEntriesFiltered(int count) { SHORTS_FILTERED.addAndGet(count); }
    public static void filterFailed() { FAILED.incrementAndGet(); }

    public static void initialize(Object activity) {
        if (!STARTED.compareAndSet(false, true)) return;
        try {
            Class<?> contextType = Class.forName("android.content.Context");
            Object context = contextType.getMethod("getApplicationContext").invoke(activity);
            if (context == null) return;
            ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "YouTubeLocalDiagnostics");
                thread.setDaemon(true);
                return thread;
            });
            final long started = System.currentTimeMillis();
            final String name = "YouTube-v0.1.4-local-" + started + ".txt";
            final Object[] downloadUri = {null};
            final int[] ticks = {0};
            worker.scheduleWithFixedDelay(() -> {
                try {
                    pingMainLooper();
                    String report = "build=v0.1.4\n"
                        + "elapsed_ms=" + (System.currentTimeMillis() - started) + "\n"
                        + "display_ads_replaced=" + HIDDEN.get() + "\n"
                        + "shorts_entries_filtered=" + SHORTS_FILTERED.get() + "\n"
                        + "display_filter_errors=" + FAILED.get() + "\n"
                        + "max_main_looper_delay_ms=" + MAX_DELAY.get() + "\n"
                        + "main_looper_ping_pending=" + PING_PENDING.get() + "\n"
                        + "network_outcomes_collected=false\n"
                        + previousExits(context, contextType);
                    File privateDir = (File) contextType.getMethod("getFilesDir").invoke(context);
                    byte[] bytes = report.getBytes(StandardCharsets.UTF_8);
                    Files.write(new File(privateDir, "youtube-adblock-local.txt").toPath(), bytes);
                    export(context, contextType, name, bytes, downloadUri);
                } catch (Throwable ignored) { /* Diagnostics cannot disrupt app use. */ }
                if (++ticks[0] >= 20) worker.shutdown(); // About five minutes, 20 small writes maximum.
            }, 15, 15, TimeUnit.SECONDS);
        } catch (Throwable ignored) { }
    }

    private static void pingMainLooper() {
        if (!PING_PENDING.compareAndSet(false, true)) return;
        try {
            Class<?> looperType = Class.forName("android.os.Looper");
            Object looper = looperType.getMethod("getMainLooper").invoke(null);
            if (looper == null) { PING_PENDING.set(false); return; }
            Class<?> handlerType = Class.forName("android.os.Handler");
            Object handler = handlerType.getConstructor(looperType).newInstance(looper);
            long sent = System.nanoTime();
            boolean accepted = (Boolean) handlerType.getMethod("post", Runnable.class).invoke(handler, (Runnable) () -> {
                long delay = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sent);
                MAX_DELAY.accumulateAndGet(delay, Math::max);
                PING_PENDING.set(false);
            });
            if (!accepted) PING_PENDING.set(false);
        } catch (Throwable ignored) { PING_PENDING.set(false); }
    }

    private static String previousExits(Object context, Class<?> contextType) {
        try {
            Object manager = contextType.getMethod("getSystemService", String.class).invoke(context, "activity");
            String packageName = (String) contextType.getMethod("getPackageName").invoke(context);
            List<?> exits = (List<?>) Class.forName("android.app.ActivityManager")
                .getMethod("getHistoricalProcessExitReasons", String.class, int.class, int.class)
                .invoke(manager, packageName, 0, 3);
            Class<?> exitType = Class.forName("android.app.ApplicationExitInfo");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < exits.size(); i++) {
                Object exit = exits.get(i);
                result.append("previous_exit_").append(i).append("_reason=")
                    .append(exitType.getMethod("getReason").invoke(exit)).append('\n');
                result.append("previous_exit_").append(i).append("_time=")
                    .append(exitType.getMethod("getTimestamp").invoke(exit)).append('\n');
            }
            return result.toString();
        } catch (Throwable ignored) { return "previous_exits_available=false\n"; }
    }

    private static void export(Object context, Class<?> contextType, String name, byte[] bytes, Object[] uriHolder) throws Exception {
        Object resolver = contextType.getMethod("getContentResolver").invoke(context);
        Class<?> resolverType = Class.forName("android.content.ContentResolver");
        Class<?> uriType = Class.forName("android.net.Uri");
        Class<?> valuesType = Class.forName("android.content.ContentValues");
        Method put = valuesType.getMethod("put", String.class, String.class);
        boolean created = uriHolder[0] == null;
        if (created) {
            Object values = valuesType.getConstructor().newInstance();
            put.invoke(values, "_display_name", name);
            put.invoke(values, "mime_type", "text/plain");
            put.invoke(values, "relative_path", "Download/YouTube-Adblock-Diagnostics/");
            valuesType.getMethod("put", String.class, Integer.class).invoke(values, "is_pending", Integer.valueOf(1));
            Object downloads = Class.forName("android.provider.MediaStore$Downloads").getField("EXTERNAL_CONTENT_URI").get(null);
            uriHolder[0] = resolverType.getMethod("insert", uriType, valuesType).invoke(resolver, downloads, values);
        }
        if (uriHolder[0] == null) return;
        try (OutputStream output = (OutputStream) resolverType.getMethod("openOutputStream", uriType, String.class).invoke(resolver, uriHolder[0], "wt")) {
            if (output == null) return;
            output.write(bytes);
        }
        // Retry publication even if an earlier write/update failed after insert.
        Object values = valuesType.getConstructor().newInstance();
        valuesType.getMethod("put", String.class, Integer.class).invoke(values, "is_pending", Integer.valueOf(0));
        resolverType.getMethod("update", uriType, valuesType, String.class, String[].class)
            .invoke(resolver, uriHolder[0], values, null, null);
    }
}
