package app.local.youtube.adblock;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.sponsorblock.ui.SkipSponsorButton;
import app.morphe.extension.youtube.sponsorblock.ui.SponsorBlockViewController;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/** Programmatically initializes SponsorBlock's player overlay (Skip Sponsor / Jump to Highlight pills)
 * inside YouTubePlayerOverlaysLayout without requiring XML layout resources.
 */
public final class SponsorBlockOverlayHelper {
    public static void initialize(ViewGroup overlaysLayout) {
        if (overlaysLayout == null) return;
        try {
            SponsorBlockViewController.hideAll();

            Context context = overlaysLayout.getContext();
            if (context == null) return;
            float density = context.getResources().getDisplayMetrics().density;

            final RelativeLayout overlay = new RelativeLayout(context);
            overlay.setLayoutParams(new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            overlay.setClipChildren(false);
            overlay.setClipToPadding(false);
            overlay.setClickable(false);
            overlay.setFocusable(false);
            overlay.setElevation(1000.0f);
            overlay.setTranslationZ(1000.0f);

            PlayerType currentType = PlayerType.getCurrent();
            boolean isFullscreen = currentType == PlayerType.WATCH_WHILE_FULLSCREEN;
            boolean canShow = isFullscreen || currentType == PlayerType.WATCH_WHILE_MAXIMIZED;
            int bottomMargin = isFullscreen
                ? getStaticInt("ctaBottomMargin", Math.round(72 * density))
                : getStaticInt("defaultBottomMargin", Math.round(56 * density));
            int rightMargin = Math.round(12 * density);

            SkipSponsorButton skipHighlightBtn = createButton(context, bottomMargin, rightMargin);
            SkipSponsorButton skipSponsorBtn = createButton(context, bottomMargin, rightMargin);

            overlay.addView(skipHighlightBtn);
            overlay.addView(skipSponsorBtn);
            overlaysLayout.addView(overlay);

            overlaysLayout.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    if (child != overlay) {
                        overlay.bringToFront();
                    }
                }

                @Override
                public void onChildViewRemoved(View parent, View child) { }
            });

            setStaticField("inlineSponsorOverlayRef", new WeakReference<RelativeLayout>(overlay));
            setStaticField("youtubeOverlaysLayoutRef", new WeakReference<ViewGroup>(overlaysLayout));
            setStaticField("skipHighlightButtonRef", new WeakReference<SkipSponsorButton>(skipHighlightBtn));
            setStaticField("skipSponsorButtonRef", new WeakReference<SkipSponsorButton>(skipSponsorBtn));
            setStaticField("newSegmentLayoutRef", new WeakReference<Object>(null));
            setStaticBoolean("newSegmentLayoutVisible", false);
            setStaticField("skipHighlight", null);
            setStaticField("skipSegment", null);

            setStaticBoolean("canShowViewElements", canShow);
        } catch (Throwable ignored) { }
    }

    private static SkipSponsorButton createButton(Context context, int bottomMargin, int rightMargin) {
        SkipSponsorButton button = new SkipSponsorButton(context);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        lp.bottomMargin = bottomMargin;
        lp.rightMargin = rightMargin;
        button.setLayoutParams(lp);
        button.setVisibility(View.GONE);
        return button;
    }

    private static void setStaticField(String name, Object value) {
        try {
            Field f = SponsorBlockViewController.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Throwable ignored) { }
    }

    private static void setStaticBoolean(String name, boolean value) {
        try {
            Field f = SponsorBlockViewController.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(null, value);
        } catch (Throwable ignored) { }
    }

    private static int getStaticInt(String name, int fallback) {
        try {
            Field f = SponsorBlockViewController.class.getDeclaredField(name);
            f.setAccessible(true);
            int v = f.getInt(null);
            return v > 0 ? v : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
