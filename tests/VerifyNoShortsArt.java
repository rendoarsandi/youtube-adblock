import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Android ART verification for v0.1.9 Shorts-disable & Litho ad-blocking patches.
 * Verifies ART bytecode/register acceptance on all modified classes and exercises
 * ShortsBlocker on synthetic FEshorts vs Home pivot items without launching YouTube.
 */
public final class VerifyNoShortsArt {
    public static final class FakeOptional {
        private final String value;
        public FakeOptional(String value) { this.value = value; }
        public boolean isPresent() { return value != null; }
        public Object get() { return value; }
    }

    public static final class FakePivotItem {
        public final Object a;
        public final Object d;
        public FakePivotItem(Object a, Object d) { this.a = a; this.d = d; }
    }

    public static final class FakeTabView {
        public int visibility = 0;
        public boolean enabled = true;
        public boolean clickable = true;
        public void setVisibility(int v) { this.visibility = v; }
        public void setEnabled(boolean e) { this.enabled = e; }
        public void setClickable(boolean c) { this.clickable = c; }
    }

    public static void main(String[] args) {
        try {
            Constructor<?> loaderCtor = Class.forName("dalvik.system.DexClassLoader")
                .getConstructor(String.class, String.class, String.class, ClassLoader.class);
            ClassLoader loader = (ClassLoader) loaderCtor.newInstance(
                args[0], args[1], null, VerifyNoShortsArt.class.getClassLoader()
            );

            // 1. Force ART verification of every modified class
            String[] classes = {
                "rrg",
                "kym",
                "app.local.youtube.adblock.ShortsBlocker",
                "app.local.youtube.adblock.DisplayAdPolicy",
                "app.local.youtube.adblock.DisplayAds",
                "app.morphe.extension.youtube.patches.components.AdsFilter",
                "app.morphe.extension.youtube.patches.components.ShortsShelfFilter",
                "app.morphe.extension.shared.patches.components.LithoFilterPatch",
                "app.morphe.extension.youtube.settings.Settings"
            };
            for (String name : classes) {
                Class<?> cls = Class.forName(name, false, loader);
                cls.getDeclaredMethods();
                System.out.println("PASS: ART verified " + name);
            }

            // 2. Exercise ShortsBlocker.hideShortsTab on FEshorts vs Home vs null
            Class<?> blocker = Class.forName("app.local.youtube.adblock.ShortsBlocker", true, loader);
            Method hideTab = blocker.getMethod("hideShortsTab", Object.class, Object.class);
            hideTab.invoke(null, null, null);

            FakeTabView shortsView = new FakeTabView();
            hideTab.invoke(null, new FakePivotItem(null, new FakeOptional("FEshorts")), shortsView);
            if (shortsView.visibility != 8 || shortsView.enabled || shortsView.clickable) {
                throw new AssertionError("FEshorts tab was not hidden: vis=" + shortsView.visibility);
            }

            FakeTabView homeView = new FakeTabView();
            hideTab.invoke(null, new FakePivotItem(null, new FakeOptional("FEwhat_to_watch")), homeView);
            if (homeView.visibility != 0 || !homeView.enabled || !homeView.clickable) {
                throw new AssertionError("Home tab was unexpectedly modified: vis=" + homeView.visibility);
            }
            System.out.println("PASS: ShortsBlocker hides FEshorts tab (GONE) and preserves Home tab");
        } catch (Throwable t) {
            System.out.println("FAIL: " + t.getMessage());
            t.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
