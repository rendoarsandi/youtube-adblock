import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Exercises the shipped player controller's background eligibility on ART.
 * Uses synthetic/default video metadata; no app launch, account, or network.
 */
public final class VerifyBackgroundPlayback {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        try {
            Constructor<?> constructor = Class.forName("dalvik.system.DexClassLoader")
                .getConstructor(String.class, String.class, String.class, ClassLoader.class);
            ClassLoader loader = (ClassLoader) constructor.newInstance(args[0], args[1], null,
                VerifyBackgroundPlayback.class.getClassLoader());
            Class<?> responseType = Class.forName("bgmn", true, loader);
            final Object response = responseType.getField("a").get(null);
            Class<?> videoType = Class.forName("akut", true, loader);
            Object video = Proxy.newProxyInstance(loader, new Class<?>[]{videoType}, (proxy, method, values) -> {
                if (method.getName().equals("w")) return response;
                throw new UnsupportedOperationException(method.getName());
            });
            Method allowed = Class.forName("tjx", true, loader).getMethod("i", videoType);
            check(!((Boolean) allowed.invoke(null, new Object[]{null})),
                "Missing active video incorrectly became background eligible");
            check(((Boolean) allowed.invoke(null, video)),
                "Active video is still rejected for missing server background-play eligibility");
            System.out.println("PASS: actual player controller permits background play for an active video and preserves the no-video guard");
            for (String name : new String[]{"aqqn", "vaz", "atdr", "rhq"}) {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                System.out.println("PASS: ART verified background owner " + name);
            }
        } catch (Throwable failure) {
            System.out.println("FAIL: " + failure);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
