import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Android resource-parser boundary; no application or account initialization. */
public final class VerifyCompanionLayouts {
    private static final String NS = "http://schemas.android.com/apk/res/android";
    private static final int AD_LAYOUT = 0x7f0e0045;
    private static final int MIXED_LAYOUT = 0x7f0e0953;
    private static final int SHARED_ELEMENT_LAYOUT = 0x7f0e0224;

    private static Object resources(String apk) throws Exception {
        Class<?> assetsType = Class.forName("android.content.res.AssetManager");
        Object assets = assetsType.getConstructor().newInstance();
        int cookie = (Integer)assetsType.getMethod("addAssetPath", String.class).invoke(assets, apk);
        if (cookie == 0) throw new IllegalStateException("Android rejected target resource package");
        Class<?> metricsType = Class.forName("android.util.DisplayMetrics");
        Class<?> configType = Class.forName("android.content.res.Configuration");
        Object metrics = metricsType.getConstructor().newInstance();
        Object config = configType.getConstructor().newInstance();
        metricsType.getMethod("setToDefaults").invoke(metrics);
        configType.getMethod("setToDefaults").invoke(config);
        return Class.forName("android.content.res.Resources")
            .getConstructor(assetsType, metricsType, configType).newInstance(assets, metrics, config);
    }

    private static int integer(Class<?> type, Object parser, String name, int defaultValue) throws Exception {
        return (Integer)type.getMethod("getAttributeIntValue", String.class, String.class, int.class)
            .invoke(parser, NS, name, defaultValue);
    }

    private static int reference(Class<?> type, Object parser, String name) throws Exception {
        return (Integer)type.getMethod("getAttributeResourceValue", String.class, String.class, int.class)
            .invoke(parser, NS, name, 0);
    }

    private static List<String> inspect(Object resources, int layout, boolean assertCollapsed) throws Exception {
        Class<?> parserType = Class.forName("android.content.res.XmlResourceParser");
        Object parser = resources.getClass().getMethod("getLayout", int.class).invoke(resources, layout);
        List<String> snapshot = new ArrayList<>();
        int roots = 0, stubs = 0;
        try {
            Method next = parserType.getMethod("next");
            int event;
            while ((event = (Integer)next.invoke(parser)) != 1) {
                if (event != 2 && event != 3) continue;
                int depth = (Integer)parserType.getMethod("getDepth").invoke(parser);
                String name = (String)parserType.getMethod("getName").invoke(parser);
                if (event == 3) { snapshot.add("end:" + depth + ":" + name); continue; }
                int id = reference(parserType, parser, "id");
                boolean adRoot = layout == AD_LAYOUT && depth == 1;
                boolean targetedStub = layout == MIXED_LAYOUT && (id == 0x7f0b00cf || id == 0x7f0b06fd);
                if (adRoot) {
                    roots++;
                    if (!"LinearLayout".equals(name) || id != 0x7f0b00ce) throw new AssertionError("Unexpected ad-card root");
                    if (assertCollapsed && (integer(parserType, parser, "layout_height", -999) != 0
                        || integer(parserType, parser, "visibility", 0) != 8)) {
                        throw new AssertionError("Dedicated ad-card root is not zero-height and GONE");
                    }
                }
                if (targetedStub) {
                    stubs++;
                    int expectedLayout = id == 0x7f0b00cf ? AD_LAYOUT : SHARED_ELEMENT_LAYOUT;
                    int expectedInflated = id == 0x7f0b00cf ? 0x7f0b00cd : 0x7f0b06fc;
                    if (!"ViewStub".equals(name) || reference(parserType, parser, "layout") != expectedLayout
                        || reference(parserType, parser, "inflatedId") != expectedInflated) {
                        throw new AssertionError("Dedicated companion stub target changed");
                    }
                    if (assertCollapsed && integer(parserType, parser, "layout_height", -999) != 0) {
                        throw new AssertionError("Dedicated companion stub is not zero-height");
                    }
                }
                if (assertCollapsed && depth == 1 && layout != AD_LAYOUT
                    && integer(parserType, parser, "layout_height", -999) != -2) {
                    throw new AssertionError("Mixed/shared companion root must remain wrap-content");
                }
                List<String> attributes = new ArrayList<>();
                int count = (Integer)parserType.getMethod("getAttributeCount").invoke(parser);
                for (int i = 0; i < count; i++) {
                    String namespace = (String)parserType.getMethod("getAttributeNamespace", int.class).invoke(parser, i);
                    String key = (String)parserType.getMethod("getAttributeName", int.class).invoke(parser, i);
                    if (NS.equals(namespace) && ((adRoot && (key.equals("layout_height") || key.equals("visibility")))
                        || (targetedStub && key.equals("layout_height")))) continue;
                    String value = (String)parserType.getMethod("getAttributeValue", int.class).invoke(parser, i);
                    attributes.add(namespace + ":" + key + "=" + value);
                }
                Collections.sort(attributes);
                snapshot.add("start:" + depth + ":" + name + ":" + attributes);
            }
        } finally { parserType.getMethod("close").invoke(parser); }
        if (layout == AD_LAYOUT && roots != 1) throw new AssertionError("Dedicated ad-card root missing");
        if (layout == MIXED_LAYOUT && stubs != 2) throw new AssertionError("Both dedicated companion stubs must exist");
        return snapshot;
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1 || args.length > 2) throw new IllegalArgumentException("Supply APK and optional baseline APK");
            Object current = resources(args[0]);
            Object baseline = args.length == 2 ? resources(args[1]) : null;
            for (int layout : new int[]{AD_LAYOUT, MIXED_LAYOUT, SHARED_ELEMENT_LAYOUT}) {
                List<String> snapshot = inspect(current, layout, true);
                if (baseline != null && !snapshot.equals(inspect(baseline, layout, false))) {
                    throw new AssertionError("Non-target companion XML content changed: " + Integer.toHexString(layout));
                }
            }
            System.out.println("PASS: Android resolved dedicated companion nodes as collapsed; mixed/shared layouts remain intact");
        } catch (AssertionError failure) {
            System.out.println("FAIL: " + failure.getMessage());
            System.exit(1);
        } catch (Throwable failure) {
            failure.printStackTrace(System.out);
            System.exit(2);
        }
    }
}
