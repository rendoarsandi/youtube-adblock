import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real APK protobuf and seed-constructor regression on ART. No application,
 * account, player or network requests; identifiers are synthetic fixture data.
 */
public final class VerifyShortsFilter {
    static ClassLoader loader;
    static Class<?> type(String name) throws Exception { return Class.forName(name, true, loader); }
    static Object defaultValue(String name) throws Exception { return type(name).getField("a").get(null); }
    static Object builder(String name) throws Exception {
        return type("baxc").getMethod("createBuilder").invoke(defaultValue(name));
    }
    static Object instance(Object builder) throws Exception {
        type("bawu").getMethod("copyOnWrite").invoke(builder);
        return type("bawu").getField("instance").get(builder);
    }
    static Object build(Object builder) throws Exception { return type("bawu").getMethod("build").invoke(builder); }
    static void extension(Object builder, Object extension, Object value) throws Exception {
        type("baww").getMethod("e", type("bawl"), Object.class).invoke(builder, extension, value);
    }
    static Object command(String endpointType, Object endpoint) throws Exception {
        Object command = builder("bdfp");
        extension(command, type(endpointType).getField("b").get(null), endpoint);
        return build(command);
    }
    static Object video(boolean ad, String identifier) throws Exception {
        Object b = builder("bktv"), endpoint = instance(b);
        type("bktv").getField("c").setInt(endpoint, 1);
        type("bktv").getField("k").setInt(endpoint, ad ? 3 : 2);
        type("bktv").getField("o").set(endpoint, identifier);
        type("bktv").getField("p").set(endpoint, "synthetic-video-" + identifier);
        return command("bktv", build(b));
    }
    static Object image(boolean ad) throws Exception {
        Object adContainer = builder("bkrw");
        if (ad) extension(adContainer, type("bkrt").getField("b").get(null), defaultValue("bkrt"));
        Object imageBuilder = builder("bkrx"), image = instance(imageBuilder);
        type("bkrx").getField("e").set(image, build(adContainer));
        type("bkrx").getField("b").setInt(image, 4);
        Object carrier = builder("bkyw");
        extension(carrier, type("bkry").getField("a").get(null), build(imageBuilder));
        Object endpointBuilder = builder("bkrv"), endpoint = instance(endpointBuilder);
        type("bkrv").getField("d").set(endpoint, build(carrier));
        type("bkrv").getField("c").setInt(endpoint, 1);
        return command("bkrv", build(endpointBuilder));
    }
    static Object tracking(String value) throws Exception {
        Object bytes = type("bavx").getMethod("x", String.class).invoke(null, value);
        return type("j$.util.Optional").getMethod("of", Object.class).invoke(null, bytes);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static boolean nativeVideo(Object command) throws Exception {
        return (Boolean)type("aqpp").getMethod("ad", type("bdfp")).invoke(null, command);
    }
    static boolean nativeImage(Object command) throws Exception {
        Object descriptor = type("bkrv").getField("b").get(null);
        Object endpoint = type("bawx").getMethod("b", type("bawl")).invoke(command, descriptor);
        return (Boolean)type("aqpp").getMethod("I", type("bkrv")).invoke(null, endpoint);
    }
    static List<?> field(Object seed, String name) throws Exception {
        return (List<?>)type("asrd").getField(name).get(seed);
    }
    static void unchangedList(List<?> actual, Object... expected) {
        check(actual.size() == expected.length, "Unexpected retained list length");
        for (int i = 0; i < expected.length; i++) check(actual.get(i) == expected[i], "Retained identity/order changed at " + i);
    }
    static void immutable(List<?> list) {
        try { list.add(null); throw new AssertionError("Filtered seed list is mutable"); }
        catch (UnsupportedOperationException expected) { }
    }
    static void run() throws Exception {
        Object first = video(false, "ordinary-A"), ad = video(true, "sponsored"), last = video(false, "ordinary-B");
        Object imageAd = image(true), imageOrdinary = image(false);
        check(!nativeVideo(first) && nativeVideo(ad) && !nativeVideo(last), "Actual native video ad fixture classification failed");
        check(nativeImage(imageAd) && !nativeImage(imageOrdinary) && !nativeVideo(imageAd), "Actual native image ad fixture classification failed");
        Method valid = type("aqpp").getMethod("ah", type("bdfp"));
        for (Object value : new Object[]{first, ad, last, imageAd, imageOrdinary})
            check((Boolean)valid.invoke(null, value), "Fixture is not a native reel command");
        System.out.println("PASS: native APK protobuf classifiers distinguish ordinary and sponsored video/image Shorts");

        Object ta = tracking("tracking-A"), tad = tracking("tracking-ad"), ti = tracking("tracking-image-ad"), tb = tracking("tracking-B");
        List<?> commands = Collections.unmodifiableList(Arrays.asList(first, ad, imageAd, last));
        List<?> tracks = Collections.unmodifiableList(Arrays.asList(ta, tad, ti, tb));
        Constructor<?> pair = type("asrd").getConstructor(List.class, List.class);
        Object seed = pair.newInstance(commands, tracks);
        List<?> retained = field(seed, "a"), retainedTracks = field(seed, "b");
        check(retained.size() == 2, "sponsored Shorts retained in seed (expected 2, actual " + retained.size() + ")");
        unchangedList(retained, first, last);
        unchangedList(retainedTracks, ta, tb);
        unchangedList(commands, first, ad, imageAd, last);
        unchangedList(tracks, ta, tad, ti, tb);
        immutable(retained); immutable(retainedTracks);
        Object allAds = pair.newInstance(Arrays.asList(ad, imageAd), Arrays.asList(tad, ti));
        check(field(allAds, "a").isEmpty() && field(allAds, "b").isEmpty(), "All-ad seed pair not empty");
        immutable(field(allAds, "a")); immutable(field(allAds, "b"));
        List<?> cleanCommands = Collections.unmodifiableList(Arrays.asList(first, imageOrdinary, last));
        List<?> cleanTracks = Collections.unmodifiableList(Arrays.asList(ta, ti, tb));
        Object clean = pair.newInstance(cleanCommands, cleanTracks);
        check(field(clean, "a") == cleanCommands && field(clean, "b") == cleanTracks, "Ordinary seed list identity changed");
        Object empty = pair.newInstance(Collections.emptyList(), Collections.emptyList());
        check(field(empty, "a").isEmpty() && field(empty, "b").isEmpty(), "Empty seed changed");
        Constructor<?> single = type("asrd").getConstructor(type("bdfp"));
        Object singleOrdinary = single.newInstance(first), singleAd = single.newInstance(ad);
        unchangedList(field(singleOrdinary, "a"), first);
        check(field(singleOrdinary, "b").size() == 1, "Singleton ordinary tracking removed");
        check(field(singleAd, "a").isEmpty() && field(singleAd, "b").isEmpty(), "Singleton sponsored seed retained");
        immutable(field(singleAd, "a")); immutable(field(singleAd, "b"));
        System.out.println("PASS: actual seed constructors remove sponsored Shorts and preserve paired ordinary entries");

        Class<?> helper = type("app.local.youtube.adblock.ShortsAds");
        Method filter = helper.getMethod("filter", List.class);
        unchangedList((List<?>)filter.invoke(null, commands), first, last);
        check(filter.invoke(null, cleanCommands) == cleanCommands, "Ordinary dynamic insertion list identity changed");
        check(((List<?>)filter.invoke(null, Arrays.asList(ad, imageAd))).isEmpty(), "All-ad dynamic insertion not empty");
        check(((List<?>)filter.invoke(null, new Object[]{null})).isEmpty(), "Null dynamic insertion not empty");
        // Unknown item shapes fail open without deleting or copying ordinary input.
        List<?> unknown = Collections.unmodifiableList(Arrays.asList(first, "unknown", null));
        check(filter.invoke(null, unknown) == unknown, "Unknown dynamic insertion changed");
        for (String name : new String[]{"asxq", "asyd", "asrd"}) {
            type(name).getDeclaredMethods();
            System.out.println("PASS: ART verified and initialized " + name);
        }
        System.out.println("PASS: actual Shorts insertion filter preserves immutable ordinary and unknown inputs");
    }
    public static void main(String[] args) {
        try {
            Constructor<?> constructor = Class.forName("dalvik.system.DexClassLoader")
                .getConstructor(String.class, String.class, String.class, ClassLoader.class);
            loader = (ClassLoader)constructor.newInstance(args[0], args[1], null, VerifyShortsFilter.class.getClassLoader());
            run();
        } catch (Throwable failure) {
            System.out.println("FAIL: " + failure.getMessage());
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
