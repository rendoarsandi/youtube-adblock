package app.local.youtube.adblock;

/** Expanded Litho display-ad and Shorts component policy (GPL-3.0).
 * Combines upstream ReVanced/Morphe AdsFilter and ShortsShelfFilter patterns
 * while preserving normal video, comment, and library contexts.
 */
public final class DisplayAdPolicy {
    private static final String[] PROTECTED_PATHS = {
        "home_video_with_context", "related_video_with_context",
        "comment_thread", "|comment.", "library_recent_shelf"
    };

    private static final String[] SHORTS_IDENTIFIERS = {
        "shorts_shelf", "inline_shorts", "shorts_grid", "shorts_video_cell",
        "shorts_lockup_cell", "shorts_pivot_item", "reel_shelf",
        "reel_multiple_items_shelf", "reel_item", "shorts_chip",
        "shorts_video_with_context"
    };

    private static final String[] AD_IDENTIFIERS = {
        "carousel_ad", "alert_banner_promo.", "product_carousel",
        "shopping_carousel", "statement_banner", "cta_shelf_card",
        "shopping_description_shelf", "product_item", "products_in_video",
        "shopping_overlay"
    };

    private static final String[] AD_PATHS = {
        "_ad_with", "_buttoned_layout", "ads_video_with_context",
        "banner_text_icon", "brand_video_shelf", "brand_video_singleton",
        "carousel_ad", "carousel_footered_layout", "carousel_headered_layout",
        "compact_landscape_image_layout", "composite_concurrent_carousel_layout",
        "full_width_portrait_image_layout", "full_width_square_image_carousel_layout",
        "full_width_square_image_layout", "hero_promo_image",
        "image_button_group_layout", "landscape_image_carousel_layout",
        "landscape_image_wide_button_layout", "legal_disclosure",
        "lumiere_promo_carousel", "primetime_promo", "product_details",
        "shopping_timely_shelf.", "square_image_layout",
        "text_image_button_layout", "text_image_no_button_layout",
        "video_display_button_group_layout",
        "video_display_carousel_button_group_layout",
        "video_display_carousel_buttoned_short_dr_layout",
        "video_display_full_buttoned_short_dr_layout",
        "video_display_full_layout", "watch_metadata_app_promo",
        "paid_content_overlay", "reel_player_disclosure", "shorts_disclosure",
        "products_in_video", "shopping_overlay", "product_item"
    };

    public static boolean shouldHide(String identifier, StringBuilder path) {
        if (identifier != null) {
            for (String s : SHORTS_IDENTIFIERS) {
                if (identifier.contains(s)) return true;
            }
        }
        if (path == null) return false;
        for (String s : SHORTS_IDENTIFIERS) {
            if (path.indexOf(s) >= 0) return true;
        }
        for (String exception : PROTECTED_PATHS) {
            if (path.indexOf(exception) >= 0) return false;
        }
        if (identifier != null) {
            for (String ad : AD_IDENTIFIERS) {
                if (identifier.contains(ad)) return true;
            }
        }
        for (String ad : AD_PATHS) {
            if (path.indexOf(ad) >= 0) return true;
        }
        return false;
    }
}
