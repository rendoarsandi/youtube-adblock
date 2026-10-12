package app.revanced.extension.shared;
import java.util.*; import java.util.concurrent.*;
public final class Utils {
 private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(4);
 public static boolean isEmpty(String s) { return s==null || s.isEmpty(); }
 public static boolean isNotEmpty(String s) { return !isEmpty(s); }
 public static <K,V> Map<K,V> createSizeRestrictedMap(final int limit) {
  return new LinkedHashMap<K,V>(limit,0.75f,false) { protected boolean removeEldestEntry(Map.Entry<K,V> e) { return size()>limit; } };
 }
 public static <T> Future<T> submitOnBackgroundThread(Callable<T> c) { return EXECUTOR.submit(c); }
 public static boolean isCurrentlyOnMainThread() { return android.os.Looper.myLooper()==android.os.Looper.getMainLooper(); }
 public static void showToastShort(String s) { android.util.Log.w("YouTubeAdblock", "Playback compatibility request failed"); }
}