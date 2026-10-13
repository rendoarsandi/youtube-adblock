import java.lang.reflect.Constructor;

/** ART regression boundary: initialize the two shipped feed endpoint classes.
 * No app launch, request construction, account access or networking.
 */
public final class VerifyFeedClasses {
    public static void main(String[] args) {
        try {
            Class<?> loaderType = Class.forName("dalvik.system.DexClassLoader");
            Constructor<?> constructor = loaderType.getConstructor(String.class, String.class, String.class, ClassLoader.class);
            ClassLoader loader = (ClassLoader)constructor.newInstance(args[0], args[1], null, VerifyFeedClasses.class.getClassLoader());
            int failures = 0;
            for (String name : new String[]{"alap", "alic"}) {
                try {
                    Class.forName(name, true, loader);
                    System.out.println("PASS: ART verified and initialized " + name);
                } catch (Throwable failure) {
                    failures++;
                    System.out.println("FAIL: " + name + " " + failure.getClass().getName() + ": " + failure.getMessage());
                }
            }
            if (args.length > 2 && args[2].equals("--display-filter")) {
                try { VerifyDisplayFilter.run(loader); }
                catch (Throwable failure) {
                    failures++;
                    System.out.println("FAIL: display filter " + failure.getClass().getName() + ": " + failure.getMessage());
                }
            }
            if (failures != 0) System.exit(1);
        } catch (Throwable failure) {
            System.out.println("Probe failed: " + failure.getClass().getName() + ": " + failure.getMessage());
            System.exit(2);
        }
    }
}
