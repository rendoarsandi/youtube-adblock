import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Android ART verification harness for v0.1.10:
 *  - Verifies patched classes in classes2, classes9, classes11 load without VerifyError.
 *  - Verifies LocalDiagnostics and PlaybackDiagnostics are complete no-ops (no threads spawned).
 *  - Verifies SponsorBlock settings defaults, category fetch list, programmatic SkipSponsorButton
 *    construction, and seekbar segment rendering on real Android ART.
 */
public final class VerifySponsorBlockAndNoDiag {
    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError("FAILED: " + msg);
    }

    private static final class FakeTimeBar {
        public final Rect aa;
        FakeTimeBar(int l, int t, int r, int b) { this.aa = new Rect(l, t, r, b); }
    }

    private static final class PrefStore implements InvocationHandler {
        final Map<String, Object> values = new HashMap<>();
        final SharedPreferences prefs;

        PrefStore() {
            this.prefs = (SharedPreferences) Proxy.newProxyInstance(
                VerifySponsorBlockAndNoDiag.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (name.equals("toString")) return "MemoryPrefs";
            if (name.equals("hashCode")) return System.identityHashCode(proxy);
            if (name.equals("equals")) return proxy == args[0];
            if (name.equals("getAll")) return new HashMap<>(values);
            if (name.equals("contains")) return values.containsKey(args[0]);
            if (name.startsWith("get") && args != null && args.length == 2) {
                Object v = values.get(args[0]);
                return v != null ? v : args[1];
            }
            if (name.equals("edit")) {
                Class<?> editorType = Class.forName("android.content.SharedPreferences$Editor");
                return Proxy.newProxyInstance(
                    VerifySponsorBlockAndNoDiag.class.getClassLoader(),
                    new Class<?>[]{editorType},
                    (p, m, a) -> {
                        if (m.getName().equals("commit")) return Boolean.TRUE;
                        if (m.getName().equals("apply")) return null;
                        return p;
                    }
                );
            }
            return null;
        }
    }

    private static Context getSystemBaseContext() {
        try {
            Class<?> looperCls = Class.forName("android.os.Looper");
            if (looperCls.getMethod("myLooper").invoke(null) == null) {
                looperCls.getMethod("prepareMainLooper").invoke(null);
            }
            Class<?> atCls = Class.forName("android.app.ActivityThread");
            Object at = atCls.getMethod("systemMain").invoke(null);
            return (Context) atCls.getMethod("getSystemContext").invoke(at);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class MemoryContext extends ContextWrapper {
        private final PrefStore store;
        private final File root;
        MemoryContext(PrefStore store, File root) {
            super(getSystemBaseContext());
            this.store = store;
            this.root = root;
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return store.prefs; }
        @Override public String getPackageName() { return "app.revanced.android.youtube"; }
        @Override public Context getApplicationContext() { return this; }
        @Override public Resources getResources() { return Resources.getSystem(); }
        @Override public File getFilesDir() { return root; }
        @Override public File getCacheDir() { return root; }
    }

    public static void main(String[] args) {
        try {
            runChecks(args);
        } catch (Throwable t) {
            System.out.println("FAIL: " + t);
            t.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static void runChecks(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: VerifySponsorBlockAndNoDiag <apk> <optDir>");
        }
        File optDir = new File(args[1]);
        optDir.mkdirs();

        Constructor<?> loaderCtor = Class.forName("dalvik.system.DexClassLoader")
            .getConstructor(String.class, String.class, String.class, ClassLoader.class);
        ClassLoader loader = (ClassLoader) loaderCtor.newInstance(
            args[0], args[1], null, VerifySponsorBlockAndNoDiag.class.getClassLoader()
        );

        // 1. Verify patched classes load and verify cleanly on ART
        String[] classes = {
            "com.google.android.apps.youtube.app.common.player.overlay.YouTubePlayerOverlaysLayout",
            "jsi",
            "app.local.youtube.adblock.LocalDiagnostics",
            "app.local.youtube.adblock.PlaybackDiagnostics",
            "app.local.youtube.adblock.SponsorBlockBridge",
            "app.local.youtube.adblock.SponsorBlockOverlayHelper",
            "app.morphe.extension.youtube.sponsorblock.ui.SkipSponsorButton",
            "app.morphe.extension.youtube.sponsorblock.ui.SponsorBlockViewController",
            "app.morphe.extension.youtube.sponsorblock.SegmentPlaybackController",
            "app.morphe.extension.youtube.sponsorblock.SponsorBlockSettings",
            "app.morphe.extension.youtube.sponsorblock.objects.SegmentCategory",
            "app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment",
            "app.morphe.extension.youtube.shared.VideoInformation",
            "app.morphe.extension.youtube.settings.Settings",
        };
        for (String name : classes) {
            Class<?> c = Class.forName(name, false, loader);
            c.getDeclaredMethods();
            c.getDeclaredConstructors();
            System.out.println("VERIFIED_CLASS " + name);
        }

        // 2. Verify LocalDiagnostics & PlaybackDiagnostics are complete no-ops
        Class<?> localDiag = Class.forName("app.local.youtube.adblock.LocalDiagnostics", true, loader);
        Class<?> playbackDiag = Class.forName("app.local.youtube.adblock.PlaybackDiagnostics", true, loader);
        int threadsBefore = Thread.activeCount();
        localDiag.getMethod("initialize", Object.class).invoke(null, new Object());
        localDiag.getMethod("adHidden").invoke(null);
        localDiag.getMethod("shortsEntriesFiltered", int.class).invoke(null, 5);
        localDiag.getMethod("filterFailed").invoke(null);
        playbackDiag.getMethod("install", Object.class).invoke(null, new Object());
        playbackDiag.getMethod("spoofResponse", int.class, Object.class).invoke(null, 200, null);
        playbackDiag.getMethod("captureCrash", Throwable.class).invoke(null, new RuntimeException("test"));
        Method report = playbackDiag.getDeclaredMethod("report");
        report.setAccessible(true);
        String reportText = (String) report.invoke(null);
        check("diagnostics_disabled=true\n".equals(reportText), "Unexpected PlaybackDiagnostics.report(): " + reportText);
        check(Thread.activeCount() == threadsBefore, "Diagnostics spawned unexpected background threads!");
        for (String removed : new String[]{
            "app.local.youtube.adblock.LocalDiagnostics$0",
            "app.local.youtube.adblock.LocalDiagnostics$3",
            "app.local.youtube.adblock.PlaybackDiagnostics$1",
            "app.local.youtube.adblock.PlaybackDiagnostics$3",
            "app.morphe.extension.youtube.sponsorblock.ui.SkipSponsorButton$$ExternalSyntheticLambda0",
        }) {
            boolean found = true;
            try {
                Class.forName(removed, false, loader);
            } catch (ClassNotFoundException expected) {
                found = false;
            }
            check(!found, "Stale inner class still present: " + removed);
        }
        System.out.println("PASS: LocalDiagnostics & PlaybackDiagnostics completely disabled");

        // 3. Inject isolated MemoryContext into Morphe Utils.context and verify SponsorBlock defaults
        MemoryContext memCtx = new MemoryContext(new PrefStore(), optDir);
        Class<?> utils = Class.forName("app.morphe.extension.shared.utils.Utils", true, loader);
        Field ctxField = utils.getDeclaredField("context");
        ctxField.setAccessible(true);
        ctxField.set(null, memCtx);

        Class<?> settings = Class.forName("app.morphe.extension.youtube.settings.Settings", true, loader);
        Object sbEnabled = settings.getField("SB_ENABLED").get(null);
        Object sbSponsor = settings.getField("SB_CATEGORY_SPONSOR").get(null);
        Object sbToast = settings.getField("SB_TOAST_ON_SKIP").get(null);
        Object sbAutoHide = settings.getField("SB_AUTO_HIDE_SKIP_BUTTON").get(null);
        Object sbConnErr = settings.getField("SB_TOAST_ON_CONNECTION_ERROR").get(null);
        check(Boolean.TRUE.equals(sbEnabled.getClass().getMethod("get").invoke(sbEnabled)), "SB_ENABLED should be true");
        check("manual-skip".equals(sbSponsor.getClass().getMethod("get").invoke(sbSponsor)), "SB_CATEGORY_SPONSOR should be manual-skip");
        check(Boolean.TRUE.equals(sbToast.getClass().getMethod("get").invoke(sbToast)), "SB_TOAST_ON_SKIP should be true");
        check(Boolean.FALSE.equals(sbAutoHide.getClass().getMethod("get").invoke(sbAutoHide)), "SB_AUTO_HIDE_SKIP_BUTTON should be false");
        check(Boolean.FALSE.equals(sbConnErr.getClass().getMethod("get").invoke(sbConnErr)), "SB_TOAST_ON_CONNECTION_ERROR should be false");
        System.out.println("PASS: SponsorBlock settings defaults verified (enabled=true, sponsor=manual-skip, toast=true, autoHideSkipBtn=false, connErrToast=false)");

        // 4. Initialize SponsorBlockSettings and verify API fetch categories are populated
        Class<?> sbSettings = Class.forName("app.morphe.extension.youtube.sponsorblock.SponsorBlockSettings", true, loader);
        sbSettings.getMethod("initialize").invoke(null);
        Class<?> segCat = Class.forName("app.morphe.extension.youtube.sponsorblock.objects.SegmentCategory", true, loader);
        String fetchCats = (String) segCat.getField("sponsorBlockAPIFetchCategories").get(null);
        check(fetchCats != null && fetchCats.contains("sponsor") && fetchCats.contains("selfpromo")
            && fetchCats.contains("intro") && fetchCats.contains("outro"),
            "Unexpected sponsorBlockAPIFetchCategories: " + fetchCats);
        System.out.println("PASS: SponsorBlock fetch categories active: " + fetchCats);

        // 5. Verify SkipSponsorButton, SponsorBlockOverlayHelper, SponsorBlockViewController, and PlayerType on ART
        Class<?> skipBtnCls = Class.forName("app.morphe.extension.youtube.sponsorblock.ui.SkipSponsorButton", true, loader);
        check(FrameLayout.class.isAssignableFrom(skipBtnCls), "SkipSponsorButton must extend FrameLayout");
        skipBtnCls.getConstructor(Context.class);
        Class<?> segCls = Class.forName("app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment", true, loader);
        skipBtnCls.getMethod("updateSkipButtonText", segCls);
        skipBtnCls.getMethod("updateLayout");
        skipBtnCls.getMethod("setVisibility", int.class);
        skipBtnCls.getMethod("dispatchDraw", Canvas.class);
        Object sponsorEnum = Enum.valueOf((Class<Enum>) segCat, "SPONSOR");
        Object introEnum = Enum.valueOf((Class<Enum>) segCat, "INTRO");
        Object seg = segCls.getConstructor(segCat, String.class, long.class, long.class, boolean.class)
            .newInstance(sponsorEnum, "uuid-test", 20000L, 50000L, false);
        Object introSeg = segCls.getConstructor(segCat, String.class, long.class, long.class, boolean.class)
            .newInstance(introEnum, "uuid-intro", 1000L, 15000L, false);

        Class<?> overlayHelper = Class.forName("app.local.youtube.adblock.SponsorBlockOverlayHelper", true, loader);
        overlayHelper.getMethod("initialize", ViewGroup.class).invoke(null, (Object) null);
        overlayHelper.getMethod("onOverlaysConfigured", ViewGroup.class, Object.class).invoke(null, null, null);
        overlayHelper.getMethod("onOverlaysMeasure", ViewGroup.class, int.class, int.class).invoke(null, null, 1080, 608);
        overlayHelper.getMethod("onOverlaysLayout", ViewGroup.class, int.class, int.class, int.class, int.class)
            .invoke(null, null, 0, 0, 1080, 608);
        overlayHelper.getMethod("onButtonShown", View.class).invoke(null, (Object) null);

        // Verify qyn.b().C() player rect extraction via SponsorBlockOverlayHelper.extractPlayerRectFromQyn
        Object fakeQyn = new Object() {
            public Object b() {
                return new Object() {
                    public Rect C() {
                        return new Rect(0, 96, 1080, 704);
                    }
                };
            }
        };
        Method extractRect = overlayHelper.getDeclaredMethod("extractPlayerRectFromQyn", Object.class);
        extractRect.setAccessible(true);
        Rect extracted = (Rect) extractRect.invoke(null, fakeQyn);
        check(extracted != null && extracted.left == 0 && extracted.top == 96 && extracted.right == 1080 && extracted.bottom == 704,
            "Expected extractPlayerRectFromQyn to return [0,96,1080,704], got " + extracted);

        // Verify SPONSOR category resolves to MANUAL_SKIP so clickable Skip pill shows on sponsors
        Object sponsorBehaviour = segCat.getField("behaviour").get(sponsorEnum);
        check("MANUAL_SKIP".equals(String.valueOf(sponsorBehaviour)),
            "Expected SPONSOR behaviour=MANUAL_SKIP, got " + sponsorBehaviour);

        Class<?> viewCtrl = Class.forName("app.morphe.extension.youtube.sponsorblock.ui.SponsorBlockViewController", true, loader);
        Class<?> playerTypeCls = Class.forName("app.morphe.extension.youtube.shared.PlayerType", true, loader);
        Field canShowField = viewCtrl.getDeclaredField("canShowViewElements");
        canShowField.setAccessible(true);

        playerTypeCls.getMethod("setFromString", String.class).invoke(null, "WATCH_WHILE_MAXIMIZED");
        check(canShowField.getBoolean(null), "Expected canShowViewElements=true in WATCH_WHILE_MAXIMIZED");
        playerTypeCls.getMethod("setFromString", String.class).invoke(null, "WATCH_WHILE_FULLSCREEN");
        check(canShowField.getBoolean(null), "Expected canShowViewElements=true in WATCH_WHILE_FULLSCREEN");
        playerTypeCls.getMethod("setFromString", String.class).invoke(null, "WATCH_WHILE_MINIMIZED");
        check(!canShowField.getBoolean(null), "Expected canShowViewElements=false in WATCH_WHILE_MINIMIZED");
        playerTypeCls.getMethod("setFromString", String.class).invoke(null, "WATCH_WHILE_MAXIMIZED");

        // Verify showSkipSegmentButton stores active manual-skip segment cleanly
        viewCtrl.getMethod("showSkipSegmentButton", segCls).invoke(null, introSeg);
        Field skipSegField = viewCtrl.getDeclaredField("skipSegment");
        skipSegField.setAccessible(true);
        check(skipSegField.get(null) == introSeg, "Expected skipSegment to be set to introSeg");
        viewCtrl.getMethod("hideSkipSegmentButton").invoke(null);
        check(skipSegField.get(null) == null, "Expected skipSegment cleared after hideSkipSegmentButton");
        System.out.println("PASS: SkipSponsorButton, SponsorBlockOverlayHelper hooks, and PlayerType->canShowViewElements verified on ART");

        // 6. Test seekbar drawing and videoLength==0 guard on real Android Bitmap/Canvas
        Class<?> vidInfo = Class.forName("app.morphe.extension.youtube.shared.VideoInformation", true, loader);
        Class<?> bridge = Class.forName("app.local.youtube.adblock.SponsorBlockBridge", true, loader);
        vidInfo.getMethod("setVideoLength", long.class).invoke(null, 0L);
        bridge.getMethod("onVideoTime", long.class).invoke(null, 5000L);
        vidInfo.getMethod("setVideoLength", long.class).invoke(null, 100000L);
        Class<?> ctrl = Class.forName("app.morphe.extension.youtube.sponsorblock.SegmentPlaybackController", true, loader);
        Field segsField = ctrl.getDeclaredField("segments");
        segsField.setAccessible(true);
        Object segArr = Array.newInstance(segCls, 1);
        Array.set(segArr, 0, seg);
        segsField.set(null, segArr);

        Bitmap bmp = Bitmap.createBitmap(100, 20, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        bridge.getMethod("drawSeekbarSegments", Object.class, Canvas.class)
            .invoke(null, new FakeTimeBar(0, 5, 100, 15), canvas);
        int insidePixel = bmp.getPixel(35, 10);
        int outsideBefore = bmp.getPixel(5, 10);
        int outsideAfter = bmp.getPixel(80, 10);
        check(insidePixel != 0, "Expected colored SponsorBlock segment bar at x=35, got transparent");
        check(outsideBefore == 0 && outsideAfter == 0, "Expected transparent seekbar outside segment range");
        System.out.println("PASS: Seekbar segment bar drawn cleanly (insideColor=0x" + Integer.toHexString(insidePixel) + ")");

        System.out.println("ALL_V0112_ART_CHECKS_PASSED");
    }
}
