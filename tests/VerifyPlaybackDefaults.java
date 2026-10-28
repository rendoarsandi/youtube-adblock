import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;

/** Exercises actual APK playback defaults under ART with isolated in-memory preferences.
 * Protects fresh-install activation, explicit user disablement, and read-only default loading.
 * Does not launch the app, read app/account data, or make network requests.
 * Compile-only Android API stubs must be excluded from the checker DEX.
 */
public final class VerifyPlaybackDefaults {
    private static final String KEY = "morphe_spoof_video_streams";
    private static final String SHARED_SETTINGS = "app.morphe.extension.shared.settings.SharedYouTubeSettings";
    private static final String SHARED_SPOOF = "app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch";

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class PreferenceStore implements InvocationHandler {
        final Map<String, Object> values = new HashMap<>();
        final List<String> writtenKeys = new ArrayList<>();
        final SharedPreferences preferences;

        PreferenceStore(boolean explicitlyDisabled) throws Exception {
            if (explicitlyDisabled) values.put(KEY, Boolean.FALSE);
            preferences = (SharedPreferences) Proxy.newProxyInstance(
                VerifyPlaybackDefaults.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, this);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
            String name = method.getName();
            if (name.equals("toString")) return "IsolatedPlaybackPreferences";
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == arguments[0];
            if (name.equals("getAll")) return new HashMap<>(values);
            if (name.equals("contains")) return values.containsKey((String) arguments[0]);
            if (name.startsWith("get") && arguments != null && arguments.length == 2) {
                String key = (String) arguments[0];
                return values.containsKey(key) ? values.get(key) : arguments[1];
            }
            if (name.equals("edit")) {
                Class<?> editorType = Class.forName("android.content.SharedPreferences$Editor");
                return Proxy.newProxyInstance(VerifyPlaybackDefaults.class.getClassLoader(),
                    new Class<?>[]{editorType}, new Editor(this));
            }
            if (name.equals("registerOnSharedPreferenceChangeListener")
                    || name.equals("unregisterOnSharedPreferenceChangeListener")) return null;
            throw new UnsupportedOperationException("Unexpected preference operation: " + name);
        }
    }

    private static final class Editor implements InvocationHandler {
        private final PreferenceStore store;
        private final Map<String, Object> pending = new HashMap<>();
        private final Set<String> removed = new HashSet<>();
        private boolean clear;

        Editor(PreferenceStore store) { this.store = store; }

        @Override public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if (name.equals("toString")) return "IsolatedPlaybackPreferenceEditor";
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == arguments[0];
            if (name.startsWith("put")) {
                String key = (String) arguments[0];
                pending.put(key, arguments[1]);
                removed.remove(key);
                return proxy;
            }
            if (name.equals("remove")) {
                String key = (String) arguments[0];
                removed.add(key);
                pending.remove(key);
                return proxy;
            }
            if (name.equals("clear")) { clear = true; return proxy; }
            if (name.equals("commit") || name.equals("apply")) {
                if (clear) {
                    store.writtenKeys.addAll(store.values.keySet());
                    store.values.clear();
                }
                for (String key : removed) { store.writtenKeys.add(key); store.values.remove(key); }
                for (Map.Entry<String, Object> entry : pending.entrySet()) {
                    store.writtenKeys.add(entry.getKey());
                    if (entry.getValue() == null) store.values.remove(entry.getKey());
                    else store.values.put(entry.getKey(), entry.getValue());
                }
                return name.equals("commit") ? Boolean.TRUE : null;
            }
            throw new UnsupportedOperationException("Unexpected preference editor operation: " + name);
        }
    }

    private static final class MemoryContext extends ContextWrapper {
        private final PreferenceStore store;
        private final File privateRoot;

        MemoryContext(PreferenceStore store, File privateRoot) {
            super(null);
            this.store = store;
            this.privateRoot = privateRoot;
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return store.preferences;
        }
        @Override public String getPackageName() { return "app.revanced.android.youtube"; }
        @Override public Context getApplicationContext() { return this; }
        @Override public Resources getResources() { return Resources.getSystem(); }
        @Override public File getFilesDir() { return privateRoot; }
        @Override public File getCacheDir() { return privateRoot; }
    }

    private static void verify(String apk, File work, boolean explicitlyDisabled) throws Exception {
        File privateRoot = new File(work, explicitlyDisabled ? "explicit-false" : "fresh");
        check(privateRoot.mkdirs() || privateRoot.isDirectory(), "Could not prepare isolated preference context");
        PreferenceStore store = new PreferenceStore(explicitlyDisabled);
        Constructor<?> constructor = Class.forName("dalvik.system.DexClassLoader")
            .getConstructor(String.class, String.class, String.class, ClassLoader.class);
        ClassLoader loader = (ClassLoader) constructor.newInstance(apk, privateRoot.getAbsolutePath(), null,
            VerifyPlaybackDefaults.class.getClassLoader());
        // A startup report can run before extension context initialization.
        // It must leave actual playback settings able to initialize afterwards.
        try {
            Class<?> reportType = Class.forName("app.local.youtube.adblock.PlaybackDiagnostics", true, loader);
            Method earlyReport = reportType.getDeclaredMethod("report"); earlyReport.setAccessible(true);
            earlyReport.invoke(null);
        } catch (ClassNotFoundException olderBuildHasNoReport) {}
        Class<?> utils = Class.forName("app.morphe.extension.shared.utils.Utils", true, loader);
        Field context = utils.getDeclaredField("context");
        context.setAccessible(true);
        context.set(null, new MemoryContext(store, privateRoot));

        Class<?> settings = Class.forName(SHARED_SETTINGS, true, loader);
        Object setting = settings.getField("SPOOF_VIDEO_STREAMS").get(null);
        Object defaultValue = setting.getClass().getField("defaultValue").get(setting);
        Object actualValue = setting.getClass().getMethod("get").invoke(setting);
        check(KEY.equals(setting.getClass().getField("key").get(setting)), "Playback preference key changed");
        check(Boolean.TRUE.equals(defaultValue), "Fresh playback spoofing default is disabled");
        check(Boolean.valueOf(!explicitlyDisabled).equals(actualValue),
            explicitlyDisabled ? "Explicit playback disablement was overwritten" : "Fresh playback spoofing is not enabled");
        Class<?> spoof = Class.forName(SHARED_SPOOF, true, loader);
        check(Boolean.valueOf(!explicitlyDisabled).equals(spoof.getMethod("isSpoofingEnabled").invoke(null)),
            "Effective playback hook flag does not respect the loaded preference");
        check(!store.writtenKeys.contains(KEY), "Loading playback defaults wrote its preference key");
        if (explicitlyDisabled) {
            check(Boolean.FALSE.equals(store.values.get(KEY)), "Explicit false preference was removed or changed");
            System.out.println("PASS: actual APK preserves explicit playback disablement without rewriting the preference");
        } else {
            check(!store.values.containsKey(KEY), "Loading fresh playback defaults persisted a value");
            System.out.println("PASS: actual APK enables playback spoofing by default without persisting the absent preference");
        }
    }

    public static void main(String[] arguments) {
        try {
            check(arguments.length == 2, "Usage: READONLY_APK PRIVATE_WORK_DIR");
            verify(arguments[0], new File(arguments[1]), false);
            verify(arguments[0], new File(arguments[1]), true);
            System.out.println("PASS: playback-default contracts complete; live playback was not tested");
        } catch (Throwable failure) {
            System.out.println("FAIL: " + failure);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
