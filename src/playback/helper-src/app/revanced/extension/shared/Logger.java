package app.revanced.extension.shared;
import java.util.function.Supplier;
public final class Logger {
 public static void printDebug(Supplier<String> s) {}
 public static void printException(Supplier<String> s) { android.util.Log.w("YouTubeAdblock", "Playback helper reported an error"); }
 public static void printException(Supplier<String> s, Exception e) { printException(s); }
 public static void printInfo(Supplier<String> s, Exception e) { printException(s); }
}