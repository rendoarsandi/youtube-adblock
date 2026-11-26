package app.local.youtube.adblock;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import app.morphe.extension.youtube.shared.PlayerType;
import app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment;
import app.morphe.extension.youtube.sponsorblock.ui.SkipSponsorButton;
import app.morphe.extension.youtube.sponsorblock.ui.SponsorBlockViewController;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/** Programmatically initializes SponsorBlock's player overlay (Skip Sponsor / Jump to Highlight pills)
 * inside YouTubePlayerOverlaysLayout without requiring XML layout resources.
 *
 * Key invariants for YouTube 21.40+:
 * 1. YouTube inflates TWO YouTubePlayerOverlaysLayout instances (Loif.t main watch player with
 *    Optional<Lqyn> present -> G=true, and Loif.d inline feed player with Optional.empty -> G=false).
 *    Only the main watch player may own SponsorBlockViewController's static button refs once present;
 *    an inline player inflating later must never hijack them.
 * 2. In YouTube's modern C && G layout mode, YouTubePlayerOverlaysLayout spans the entire screen
 *    while each overlay child (Lqol) is measured/laid out to the live video player rect qyn.b().C()
 *    offset by (-parent.left, -parent.top) via layout(0, 0, w, h) + translationX/Y. We match Lqol
 *    exactly so the Skip pill sits inside the bottom-right corner of the video player instead of off
 *    the bottom of the screen behind the watch metadata panel.
 */
public final class SponsorBlockOverlayHelper {
    private static final class OverlayHolder {
        final WeakReference<ViewGroup> parentRef;
        final RelativeLayout overlay;
        final SkipSponsorButton skipHighlightBtn;
        final SkipSponsorButton skipSponsorBtn;
        volatile boolean isWatchPlayer;
        volatile WeakReference<Object> qynRef = new WeakReference<Object>(null);

        OverlayHolder(
            ViewGroup parent,
            RelativeLayout overlay,
            SkipSponsorButton skipHighlightBtn,
            SkipSponsorButton skipSponsorBtn
        ) {
            this.parentRef = new WeakReference<ViewGroup>(parent);
            this.overlay = overlay;
            this.skipHighlightBtn = skipHighlightBtn;
            this.skipSponsorBtn = skipSponsorBtn;
        }
    }

    private static final WeakHashMap<ViewGroup, OverlayHolder> holders = new WeakHashMap<ViewGroup, OverlayHolder>();
    private static volatile WeakReference<OverlayHolder> watchHolderRef = new WeakReference<OverlayHolder>(null);
    private static volatile Field overlaysKField;
    private static volatile boolean overlaysKFieldChecked;

    public static void initialize(ViewGroup overlaysLayout) {
        if (overlaysLayout == null) return;
        try {
            OverlayHolder existing = getHolder(overlaysLayout);
            if (existing != null && existing.overlay.getParent() == overlaysLayout) {
                if (existing.isWatchPlayer || getLiveWatchHolder() == null) {
                    bindActiveHolder(overlaysLayout, existing);
                }
                return;
            }

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
            int bottomMargin = isFullscreen
                ? getStaticInt("ctaBottomMargin", Math.round(120 * density))
                : getStaticInt("defaultBottomMargin", Math.round(44 * density));
            int rightMargin = Math.round(12 * density);

            SkipSponsorButton skipHighlightBtn = createButton(context, bottomMargin, rightMargin);
            SkipSponsorButton skipSponsorBtn = createButton(context, bottomMargin, rightMargin);

            overlay.addView(skipHighlightBtn);
            overlay.addView(skipSponsorBtn);
            overlaysLayout.addView(overlay);

            final OverlayHolder holder = new OverlayHolder(overlaysLayout, overlay, skipHighlightBtn, skipSponsorBtn);
            putHolder(overlaysLayout, holder);

            overlaysLayout.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                @Override
                public void onChildViewAdded(View parent, View child) {
                    if (child != overlay) {
                        bringOverlayToFront(parent, overlay);
                    }
                }

                @Override
                public void onChildViewRemoved(View parent, View child) { }
            });

            // Never let a secondary inline feed player overwrite an already-live watch player binding.
            if (getLiveWatchHolder() == null) {
                SponsorBlockViewController.hideAll();
                bindActiveHolder(overlaysLayout, holder);
            }
            overlaysLayout.requestLayout();
        } catch (Throwable ignored) { }
    }

    /** Called from YouTubePlayerOverlaysLayout.u(...) with Optional<Lqyn> (p20).
     * Non-empty Optional identifies the main watch player (Loif.t) and supplies Lqyn directly.
     */
    public static void onOverlaysConfigured(ViewGroup overlaysLayout, Object optionalQyn) {
        if (overlaysLayout == null) return;
        try {
            Object qyn = unwrapOptional(optionalQyn);
            if (qyn == null) return;

            OverlayHolder holder = getHolder(overlaysLayout);
            if (holder == null || holder.overlay.getParent() != overlaysLayout) {
                initialize(overlaysLayout);
                holder = getHolder(overlaysLayout);
            }
            if (holder == null) return;

            holder.isWatchPlayer = true;
            holder.qynRef = new WeakReference<Object>(qyn);
            watchHolderRef = new WeakReference<OverlayHolder>(holder);
            bindActiveHolder(overlaysLayout, holder);
            overlaysLayout.requestLayout();
        } catch (Throwable ignored) { }
    }

    /** Called from YouTubePlayerOverlaysLayout.onMeasure when C && G bypasses super.onMeasure. */
    public static void onOverlaysMeasure(ViewGroup overlaysLayout, int width, int height) {
        if (overlaysLayout == null || width <= 0 || height <= 0) return;
        try {
            OverlayHolder holder = getHolder(overlaysLayout);
            if (holder == null) return;
            RelativeLayout overlay = holder.overlay;
            if (overlay == null || overlay.getParent() != overlaysLayout) return;
            if (overlay.getVisibility() == View.GONE) return;

            if (!holder.isWatchPlayer) {
                holder.isWatchPlayer = true;
                watchHolderRef = new WeakReference<OverlayHolder>(holder);
            }
            ensureWatchBound(overlaysLayout, holder);

            Rect bounds = resolvePlayerBounds(
                overlaysLayout,
                holder,
                overlaysLayout.getLeft(),
                overlaysLayout.getTop(),
                width,
                height
            );
            int targetW = bounds.width();
            int targetH = bounds.height();
            if (targetW <= 0 || targetH <= 0) return;

            int wSpec = View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY);
            overlay.measure(wSpec, hSpec);
        } catch (Throwable ignored) { }
    }

    /** Called from YouTubePlayerOverlaysLayout.onLayout when C && G bypasses super.onLayout. */
    public static void onOverlaysLayout(ViewGroup overlaysLayout, int left, int top, int right, int bottom) {
        if (overlaysLayout == null) return;
        try {
            OverlayHolder holder = getHolder(overlaysLayout);
            if (holder == null) return;
            RelativeLayout overlay = holder.overlay;
            if (overlay == null || overlay.getParent() != overlaysLayout) return;
            if (overlay.getVisibility() == View.GONE) return;

            if (!holder.isWatchPlayer) {
                holder.isWatchPlayer = true;
                watchHolderRef = new WeakReference<OverlayHolder>(holder);
            }
            ensureWatchBound(overlaysLayout, holder);

            int width = right - left;
            int height = bottom - top;
            if (width <= 0 || height <= 0) return;

            Rect bounds = resolvePlayerBounds(overlaysLayout, holder, left, top, width, height);
            int targetW = bounds.width();
            int targetH = bounds.height();
            if (targetW <= 0 || targetH <= 0) return;

            // Always re-measure when layout was requested (e.g., SkipSponsorButton changed GONE -> VISIBLE)
            // or when target player dimensions changed.
            if (overlay.isLayoutRequested()
                || overlay.getMeasuredWidth() != targetW
                || overlay.getMeasuredHeight() != targetH) {
                int wSpec = View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY);
                int hSpec = View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY);
                overlay.measure(wSpec, hSpec);
            }
            overlay.layout(0, 0, targetW, targetH);
            overlay.setTranslationX((float) bounds.left);
            overlay.setTranslationY((float) bounds.top);
            bringOverlayToFront(overlaysLayout, overlay);
        } catch (Throwable ignored) { }
    }

    /** Called when SkipSponsorButton becomes VISIBLE so the overlay re-measures and snaps into place immediately. */
    public static void onButtonShown(View button) {
        if (button == null) return;
        try {
            ViewParent parent = button.getParent();
            if (!(parent instanceof RelativeLayout)) return;
            RelativeLayout overlay = (RelativeLayout) parent;
            if (overlay.getVisibility() != View.VISIBLE) {
                overlay.setVisibility(View.VISIBLE);
            }
            ViewParent grandParent = overlay.getParent();
            if (grandParent instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) grandParent;
                bringOverlayToFront(vg, overlay);
                OverlayHolder holder = getHolder(vg);
                if (holder != null && holder.isWatchPlayer) {
                    int w = vg.getMeasuredWidth();
                    int h = vg.getMeasuredHeight();
                    if (w > 0 && h > 0) {
                        Rect bounds = resolvePlayerBounds(vg, holder, vg.getLeft(), vg.getTop(), w, h);
                        int targetW = bounds.width();
                        int targetH = bounds.height();
                        if (targetW > 0 && targetH > 0) {
                            int wSpec = View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY);
                            int hSpec = View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY);
                            overlay.measure(wSpec, hSpec);
                            overlay.layout(0, 0, targetW, targetH);
                            overlay.setTranslationX((float) bounds.left);
                            overlay.setTranslationY((float) bounds.top);
                        }
                    }
                }
            }
            overlay.requestLayout();
            overlay.invalidate();
        } catch (Throwable ignored) { }
    }

    private static Rect resolvePlayerBounds(
        ViewGroup overlaysLayout,
        OverlayHolder holder,
        int parentLeft,
        int parentTop,
        int fallbackWidth,
        int fallbackHeight
    ) {
        Rect raw = extractPlayerRectFromQyn(holder.qynRef.get());
        if (raw == null || raw.isEmpty()) {
            Object discoveredQyn = discoverQynFromOverlaysSet(overlaysLayout);
            if (discoveredQyn != null) {
                holder.qynRef = new WeakReference<Object>(discoveredQyn);
                raw = extractPlayerRectFromQyn(discoveredQyn);
            }
        }
        if (raw != null && !raw.isEmpty()) {
            Rect adjusted = new Rect(raw);
            adjusted.offset(-parentLeft, -parentTop);
            if (adjusted.width() > 0 && adjusted.height() > 0) {
                return adjusted;
            }
        }
        Rect fallback = new Rect();
        fallback.left = 0;
        fallback.top = 0;
        fallback.right = fallbackWidth;
        fallback.bottom = fallbackHeight;
        return fallback;
    }

    private static Rect extractPlayerRectFromQyn(Object qyn) {
        if (qyn == null) return null;
        try {
            Method getLayout = qyn.getClass().getMethod("b");
            getLayout.setAccessible(true);
            Object qvl = getLayout.invoke(qyn);
            if (qvl == null) return null;
            Method getPlayerRect = qvl.getClass().getMethod("C");
            getPlayerRect.setAccessible(true);
            Object rect = getPlayerRect.invoke(qvl);
            return rect instanceof Rect ? (Rect) rect : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object discoverQynFromOverlaysSet(ViewGroup overlaysLayout) {
        try {
            Field kField = overlaysKField;
            if (!overlaysKFieldChecked) {
                Field resolved = null;
                try {
                    resolved = overlaysLayout.getClass().getDeclaredField("k");
                    resolved.setAccessible(true);
                } catch (Throwable ignored) { }
                overlaysKField = resolved;
                overlaysKFieldChecked = true;
                kField = resolved;
            }
            if (kField == null) return null;
            Object kObj = kField.get(overlaysLayout);
            if (!(kObj instanceof Set)) return null;
            Iterator<?> it = ((Set<?>) kObj).iterator();
            while (it.hasNext()) {
                Object elem = it.next();
                if (elem == null) continue;
                try {
                    Field cField = elem.getClass().getDeclaredField("c");
                    cField.setAccessible(true);
                    Object lazy = cField.get(elem);
                    if (lazy == null) continue;
                    Method get = lazy.getClass().getMethod("nH");
                    get.setAccessible(true);
                    Object qyn = get.invoke(lazy);
                    if (qyn != null && extractPlayerRectFromQyn(qyn) != null) {
                        return qyn;
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Object unwrapOptional(Object optionalObj) {
        if (optionalObj == null) return null;
        try {
            Method isPresent = optionalObj.getClass().getMethod("isPresent");
            isPresent.setAccessible(true);
            Object present = isPresent.invoke(optionalObj);
            if (Boolean.TRUE.equals(present)) {
                Method get = optionalObj.getClass().getMethod("get");
                get.setAccessible(true);
                return get.invoke(optionalObj);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static OverlayHolder getHolder(ViewGroup vg) {
        synchronized (holders) {
            return holders.get(vg);
        }
    }

    private static void putHolder(ViewGroup vg, OverlayHolder holder) {
        synchronized (holders) {
            holders.put(vg, holder);
        }
    }

    private static OverlayHolder getLiveWatchHolder() {
        OverlayHolder h = watchHolderRef.get();
        if (h == null) return null;
        ViewGroup parent = h.parentRef.get();
        if (parent == null || h.overlay.getParent() != parent) {
            return null;
        }
        return h;
    }

    private static void ensureWatchBound(ViewGroup overlaysLayout, OverlayHolder holder) {
        try {
            Field f = SponsorBlockViewController.class.getDeclaredField("skipSponsorButtonRef");
            f.setAccessible(true);
            Object ref = f.get(null);
            Object currentBtn = (ref instanceof WeakReference) ? ((WeakReference<?>) ref).get() : null;
            if (currentBtn != holder.skipSponsorBtn) {
                bindActiveHolder(overlaysLayout, holder);
            }
        } catch (Throwable ignored) { }
    }

    private static void bindActiveHolder(ViewGroup overlaysLayout, OverlayHolder holder) {
        PlayerType currentType = PlayerType.getCurrent();
        boolean isFullscreen = currentType == PlayerType.WATCH_WHILE_FULLSCREEN;
        boolean canShow = isFullscreen || currentType == PlayerType.WATCH_WHILE_MAXIMIZED;

        setStaticField("inlineSponsorOverlayRef", new WeakReference<RelativeLayout>(holder.overlay));
        setStaticField("youtubeOverlaysLayoutRef", new WeakReference<ViewGroup>(overlaysLayout));
        setStaticField("skipHighlightButtonRef", new WeakReference<SkipSponsorButton>(holder.skipHighlightBtn));
        setStaticField("skipSponsorButtonRef", new WeakReference<SkipSponsorButton>(holder.skipSponsorBtn));
        setStaticField("newSegmentLayoutRef", new WeakReference<Object>(null));
        setStaticBoolean("newSegmentLayoutVisible", false);
        setStaticBoolean("canShowViewElements", canShow);

        // Re-apply active segments if SponsorBlock already had one pending when binding switched.
        Object activeSeg = getStaticField("skipSegment");
        if (activeSeg instanceof SponsorSegment) {
            holder.skipSponsorBtn.updateSkipButtonText((SponsorSegment) activeSeg);
            holder.skipSponsorBtn.setVisibility(canShow ? View.VISIBLE : View.GONE);
        }
        Object activeHighlight = getStaticField("skipHighlight");
        if (activeHighlight instanceof SponsorSegment) {
            holder.skipHighlightBtn.updateSkipButtonText((SponsorSegment) activeHighlight);
            holder.skipHighlightBtn.setVisibility(canShow ? View.VISIBLE : View.GONE);
        }
    }

    private static void bringOverlayToFront(View parent, RelativeLayout overlay) {
        try {
            if (parent instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) parent;
                int count = vg.getChildCount();
                if (count > 1 && vg.indexOfChild(overlay) != count - 1) {
                    overlay.bringToFront();
                }
            }
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

    private static Object getStaticField(String name) {
        try {
            Field f = SponsorBlockViewController.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
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
