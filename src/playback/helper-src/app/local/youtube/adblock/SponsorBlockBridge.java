package app.local.youtube.adblock;

import android.graphics.Canvas;
import android.graphics.Rect;
import app.morphe.extension.shared.utils.Utils;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.shared.RootView;
import app.morphe.extension.youtube.shared.VideoInformation;
import app.morphe.extension.youtube.sponsorblock.SegmentPlaybackController;
import app.morphe.extension.youtube.sponsorblock.SponsorBlockSettings;
import app.morphe.extension.youtube.sponsorblock.objects.SegmentCategory;
import app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment;
import java.lang.reflect.Field;

/** Bridges YouTube's VideoInformation and InlineTimeBar hooks to Morphe's SponsorBlock controller.
 * All methods fail open so SponsorBlock can never disrupt video playback or rendering.
 */
public final class SponsorBlockBridge {
    private static volatile Field timeBarRectField;
    private static volatile boolean timeBarRectLookupDone;

    public static void initialize() {
        try {
            if (Utils.isCurrentlyOnMainThread()) {
                SegmentPlaybackController.initialize();
            } else {
                Utils.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SegmentPlaybackController.initialize();
                        } catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable ignored) { }
    }

    public static void onVideoId(String videoId) {
        if (videoId == null || videoId.isEmpty()) return;
        try {
            SponsorBlockSettings.initialize();
            SegmentPlaybackController.newVideoStarted(
                VideoInformation.getChannelId(),
                VideoInformation.getChannelName(),
                videoId,
                VideoInformation.getVideoTitle(),
                VideoInformation.getVideoLength(),
                VideoInformation.getLiveStreamState()
            );
        } catch (Throwable ignored) { }
    }

    public static void onVideoInformation(
        String channelId,
        String channelName,
        String videoId,
        String videoTitle,
        long videoLength,
        boolean isLiveStream
    ) {
        if (videoId == null || videoId.isEmpty()) return;
        try {
            SponsorBlockSettings.initialize();
            SegmentPlaybackController.newVideoStarted(
                channelId,
                channelName,
                videoId,
                videoTitle,
                videoLength,
                isLiveStream
            );
        } catch (Throwable ignored) { }
    }

    public static void onVideoTime(final long videoTime) {
        try {
            if (VideoInformation.getVideoLength() <= 0L) return;
            if (Utils.isCurrentlyOnMainThread()) {
                SegmentPlaybackController.setVideoTime(videoTime);
            } else {
                Utils.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (VideoInformation.getVideoLength() <= 0L) return;
                            SegmentPlaybackController.setVideoTime(videoTime);
                        } catch (Throwable ignored) { }
                    }
                });
            }
        } catch (Throwable ignored) { }
    }

    public static void drawSeekbarSegments(Object timeBar, Canvas canvas) {
        if (timeBar == null || canvas == null) return;
        try {
            if (!Boolean.TRUE.equals(Settings.SB_ENABLED.get())) return;
            if (RootView.isShortsActive() || RootView.isAdProgressTextVisible()) return;

            SponsorSegment[] segments = SegmentPlaybackController.getSegments();
            if (segments == null || segments.length == 0) return;

            long length = VideoInformation.getVideoLength();
            if (length <= 0L) return;

            Rect rect = getTimeBarRect(timeBar);
            if (rect == null || rect.right <= rect.left || rect.bottom <= rect.top) return;

            float barLeft = rect.left;
            float barRight = rect.right;
            float barTop = rect.top;
            float barBottom = rect.bottom;
            float scale = (barRight - barLeft) / (float) length;
            float highlightWidth = Math.max(4.0f, barBottom - barTop);

            for (int i = 0; i < segments.length; i++) {
                SponsorSegment segment = segments[i];
                if (segment == null || segment.category == null || segment.category.paint == null) {
                    continue;
                }
                float left = barLeft + (float) segment.start * scale;
                float right;
                if (segment.category == SegmentCategory.HIGHLIGHT) {
                    right = left + highlightWidth;
                } else {
                    right = barLeft + (float) segment.end * scale;
                }
                if (left < barLeft) left = barLeft;
                if (right > barRight) right = barRight;
                if (right > left) {
                    canvas.drawRect(left, barTop, right, barBottom, segment.category.paint);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static Rect getTimeBarRect(Object timeBar) {
        try {
            Field f = timeBarRectField;
            if (!timeBarRectLookupDone) {
                Field resolved = null;
                try {
                    resolved = timeBar.getClass().getDeclaredField("aa");
                    resolved.setAccessible(true);
                } catch (Throwable ignored) { }
                timeBarRectField = resolved;
                timeBarRectLookupDone = true;
                f = resolved;
            }
            if (f == null) return null;
            Object value = f.get(timeBar);
            return value instanceof Rect ? (Rect) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
