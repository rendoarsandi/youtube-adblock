package app.local.youtube.adblock;

/** Narrow subset of upstream ReVanced AdsFilter patterns (GPL-3.0).
 * No broad image/button/layout matching and no protobuf/URL logging.
 */
public final class DisplayAdPolicy {
    private static final String[] PROTECTED_PATHS = {
        "home_video_with_context", "related_video_with_context",
        "comment_thread", "|comment.", "library_recent_shelf"
    };
    private static final String[] AD_PATHS = {
        "_ad_with", "ads_video_with_context", "watch_metadata_app_promo"
    };

    public static boolean shouldHide(String identifier, StringBuilder path) {
        // Unknown context stays visible. Exceptions apply to identifier matches too.
        if (path == null) return false;
        for (String exception : PROTECTED_PATHS) if (path.indexOf(exception) >= 0) return false;
        if (identifier != null && identifier.contains("carousel_ad")) return true;
        for (String ad : AD_PATHS) if (path.indexOf(ad) >= 0) return true;
        return false;
    }
}
