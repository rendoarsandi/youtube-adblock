package app.morphe.extension.youtube.sponsorblock.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.local.youtube.adblock.SponsorBlockOverlayHelper;
import app.morphe.extension.youtube.settings.Settings;
import app.morphe.extension.youtube.sponsorblock.SegmentPlaybackController;
import app.morphe.extension.youtube.sponsorblock.objects.SponsorSegment;

/** Programmatic replacement for Morphe's SkipSponsorButton without external XML layout dependencies.
 * Preserves the exact public class hierarchy and method signatures expected by SponsorBlockViewController.
 */
public class SkipSponsorButton extends FrameLayout {
    private SponsorSegment segment;
    private final LinearLayout skipSponsorBtnContainer;
    private final TextView skipSponsorTextView;
    private final GradientDrawable pillBackground;
    private final float density;

    public SkipSponsorButton(Context context) {
        this(context, null);
    }

    public SkipSponsorButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SkipSponsorButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SkipSponsorButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.density = context.getResources().getDisplayMetrics().density;

        setMinimumHeight(dp(40));
        setClickable(true);
        setFocusable(true);
        setElevation(dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(0xE60F0F0F);
        bg.setStroke(Math.max(1, dp(1)), 0x80FFFFFF);
        bg.setCornerRadius(dp(20));
        this.pillBackground = bg;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setMinimumHeight(dp(40));
        container.setPadding(dp(16), dp(8), dp(16), dp(8));
        container.setBackground(bg);
        container.setClickable(false);
        container.setFocusable(false);

        TextView label = new TextView(context);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        label.setTypeface(null, 1); // Typeface.BOLD

        container.addView(label, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        addView(container, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        this.skipSponsorBtnContainer = container;
        this.skipSponsorTextView = label;

        updateLayout();

        setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setVisibility(View.GONE);
                SponsorSegment current = segment;
                if (current != null) {
                    SegmentPlaybackController.onSkipSegmentClicked(current);
                }
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * density);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            SponsorBlockOverlayHelper.onButtonShown(this);
            requestLayout();
            invalidate();
        }
    }

    @Override
    public final void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    public void updateLayout() {
        boolean square = Boolean.TRUE.equals(Settings.SB_SQUARE_LAYOUT.get());
        if (square) {
            pillBackground.setCornerRadius(dp(4));
            setPadding(0, 0, 0, 0);
        } else {
            pillBackground.setCornerRadius(dp(20));
            setPadding(dp(12), 0, dp(12), 0);
        }
    }

    public void updateSkipButtonText(SponsorSegment sponsorSegment) {
        this.segment = sponsorSegment;
        if (sponsorSegment == null) return;
        String text = sponsorSegment.getSkipButtonText();
        if (text == null || text.isEmpty() || text.startsWith("revanced_sb_")) {
            text = "Skip";
        }
        String display = text + " \u00BB";
        CharSequence existing = skipSponsorTextView.getText();
        if (existing != null && display.equals(existing.toString())) {
            return;
        }
        skipSponsorTextView.setText(display);
    }
}
