import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Actual-APK ART boundary for the narrow component filter.
 * No app launch, account access, player construction or network requests.
 */
public final class VerifyDisplayFilter {
    public static void run(ClassLoader loader) throws Exception {
        Object[][] cases = {
            {"carousel_ad", "watch_metadata|carousel", true},
            {null, "watch_metadata|ads_video_with_context", true},
            {null, "watch_metadata|video_ad_with_button", true},
            {null, "watch_metadata_app_promo", true},
            {null, "watch_metadata|video_with_context", false},
            {null, "full_width_square_image_layout", false},
            {"carousel_ad", "related_video_with_context|ads_video_with_context", false},
            {"carousel_ad", "home_video_with_context|video_ad_with_button", false},
            {"carousel_ad", "comment_thread|video_ad_with_button", false},
            {"carousel_ad", "comments|comment.reply|video_ad_with_button", false},
            {"carousel_ad", "library_recent_shelf|ads_video_with_context", false},
            {"carousel_ad", null, false},
            {null, null, false},
            {null, "", false}
        };
        for (String name : new String[]{"vin", "app.local.youtube.adblock.DisplayAds", "hqn", "hqm", "wpp"}) {
            Class.forName(name, true, loader);
            System.out.println("PASS: ART verified and initialized " + name);
        }

        Class<?> component = Class.forName("hgo", false, loader);
        Class<?> componentContext = Class.forName("hgt", false, loader);
        Class<?> renderContext = Class.forName("wpp", false, loader);
        Method filter = Class.forName("app.local.youtube.adblock.DisplayAds", true, loader)
            .getMethod("filter", component, componentContext, renderContext);
        if (filter.invoke(null, null, null, null) != null) throw new AssertionError("Null component did not fail open");
        System.out.println("PASS: actual component bridge preserves null inputs");

        // A system resources context is sufficient for an unrendered empty
        // component. This bootstraps this probe process, not the YouTube app.
        Class<?> looper = Class.forName("android.os.Looper");
        if (looper.getMethod("myLooper").invoke(null) == null) looper.getMethod("prepareMainLooper").invoke(null);
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Object context = activityThread.getMethod("getSystemContext").invoke(thread);
        if (context == null) throw new IllegalStateException("Probe system context unavailable");
        Object lithoContext = componentContext.getConstructor(Class.forName("android.content.Context"), String.class,
            Class.forName("xnc", false, loader), Class.forName("hlw", false, loader)).newInstance(context, null, null, null);
        Class<?> emptyType = Class.forName("hqn", true, loader);
        Object builder = emptyType.getMethod("aE", componentContext).invoke(null, lithoContext);
        Object original = builder.getClass().getField("a").get(builder);
        if (original == null) throw new IllegalStateException("Probe empty component unavailable");

        Constructor<?> renderConstructor = null;
        for (Constructor<?> candidate : renderContext.getConstructors()) {
            if (candidate.getParameterTypes().length == 40) renderConstructor = candidate;
        }
        if (renderConstructor == null) throw new IllegalStateException("Probe render-context constructor unavailable");
        Class<?>[] types = renderConstructor.getParameterTypes();
        for (Object[] row : cases) {
            Object[] parameters = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == boolean.class) parameters[i] = false;
                else if (types[i] == int.class) parameters[i] = 0;
                else if (types[i] == float.class) parameters[i] = 0f;
            }
            parameters[12] = row[1] == null ? null : new StringBuilder((String)row[1]);
            parameters[14] = row[0];
            Object rendering = renderConstructor.newInstance(parameters);
            Object result = filter.invoke(null, original, lithoContext, rendering);
            boolean replaced = (Boolean)row[2];
            if (replaced ? result == original || !emptyType.isInstance(result) : result != original) {
                throw new AssertionError("Component filter case failed: " + row[0] + " / " + row[1]);
            }
        }
        if (filter.invoke(null, original, null, null) != original) throw new AssertionError("Unknown context did not fail open");
        System.out.println("PASS: actual component filter replaces explicit ads and preserves protected/unknown components (14 cases)");
    }
}
