package app.local.youtube.adblock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Fail-safe helper to hide the Shorts bottom navigation tab in PivotBar.
 * Uses reflection so no Android/YouTube compile stubs are required.
 */
public final class ShortsBlocker {
    private static final byte[] FE_SHORTS_BYTES = "FEshorts".getBytes(StandardCharsets.US_ASCII);

    public static void hideShortsTab(Object pivotItem, Object tabView) {
        if (pivotItem == null || tabView == null) return;
        try {
            if (!isShortsPivotItem(pivotItem)) return;
            Class<?> viewClass = tabView.getClass();
            Method setVisibility = viewClass.getMethod("setVisibility", int.class);
            setVisibility.invoke(tabView, Integer.valueOf(8)); // View.GONE
            try {
                viewClass.getMethod("setEnabled", boolean.class).invoke(tabView, Boolean.FALSE);
                viewClass.getMethod("setClickable", boolean.class).invoke(tabView, Boolean.FALSE);
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) {
            // Fail open: never crash PivotBar creation.
        }
    }

    public static boolean isShortsPivotItem(Object pivotItem) {
        if (pivotItem == null) return false;
        try {
            // 1. Check Laupy;->d (j$.util.Optional<String> pivot identifier)
            Field dField = pivotItem.getClass().getField("d");
            Object optD = dField.get(pivotItem);
            if (optionalEqualsIgnoreCase(optD, "FEshorts")) return true;

            // 2. Check Laupy;->a (MessageLite renderer)
            Field aField = pivotItem.getClass().getField("a");
            Object msg = aField.get(pivotItem);
            if (msg != null) {
                try {
                    Field eField = msg.getClass().getField("e");
                    Object idVal = eField.get(msg);
                    if (idVal instanceof String && "FEshorts".equalsIgnoreCase((String) idVal)) {
                        return true;
                    }
                } catch (Throwable ignored) { }
                try {
                    Method toByteArray = msg.getClass().getMethod("toByteArray");
                    Object raw = toByteArray.invoke(msg);
                    if (raw instanceof byte[] && containsBytes((byte[]) raw, FE_SHORTS_BYTES)) {
                        return true;
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static boolean optionalEqualsIgnoreCase(Object optional, String target) {
        if (optional == null) return false;
        try {
            Method isPresent = optional.getClass().getMethod("isPresent");
            if (!Boolean.TRUE.equals(isPresent.invoke(optional))) return false;
            Object val = optional.getClass().getMethod("get").invoke(optional);
            return val instanceof String && target.equalsIgnoreCase((String) val);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (haystack == null || needle == null || needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        int limit = haystack.length - needle.length;
        outer:
        for (int i = 0; i <= limit; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
