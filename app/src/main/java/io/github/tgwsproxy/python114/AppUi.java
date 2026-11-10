package io.github.tgwsproxy.python114;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Small native UI toolkit. No WebView, network calls, or external UI dependencies. */
final class AppUi {
    static final int BG = Color.rgb(248, 249, 255);
    static final int CARD = Color.rgb(239, 243, 255);
    static final int TEXT = Color.rgb(47, 69, 107);
    static final int SECONDARY = Color.rgb(105, 122, 152);
    static final int BLUE = Color.rgb(44, 94, 167);
    static final int SOFT_BLUE = Color.rgb(170, 196, 250);
    static final int PURPLE = Color.rgb(87, 52, 200);

    static int dp(Context ctx, float n) {
        return (int) (n * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable fill(int color, float radius, Context ctx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(ctx, radius));
        return d;
    }

    static GradientDrawable outline(int color, int border, float radius, Context ctx) {
        GradientDrawable d = fill(color, radius, ctx);
        d.setStroke(dp(ctx, 1), border);
        return d;
    }

    static TextView text(Context ctx, String value, float size, int color, boolean bold) {
        TextView t = new TextView(ctx);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    static Button button(Context ctx, String value, int bgColor, int textColor) {
        Button b = new Button(ctx);
        b.setText(value);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(textColor);
        b.setBackground(fill(bgColor, 23, ctx));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setElevation(dp(ctx, 1));
        return b;
    }

    static FrameLayout canvas(Activity activity) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(BG);
        root.addView(new Decorations(activity), new FrameLayout.LayoutParams(-1, -1));
        // targetSdk 35 enables edge-to-edge on Android 15. Keep controls out of system bars.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            final int top;
            final int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        activity.getWindow().setStatusBarColor(Color.rgb(224, 232, 252));
        activity.getWindow().setNavigationBarColor(BG);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        return root;
    }

    /** Subtle geometric corners based on the user's visual reference. */
    private static final class Decorations extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        Decorations(Context ctx) { super(ctx); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float s = getResources().getDisplayMetrics().density;
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(285*s, 0);
            path.lineTo(297*s, 32*s);
            path.lineTo(274*s, 60*s);
            path.lineTo(281*s, 110*s);
            path.lineTo(223*s, 123*s);
            path.lineTo(190*s, 170*s);
            path.lineTo(143*s, 157*s);
            path.lineTo(98*s, 187*s);
            path.lineTo(55*s, 166*s);
            path.lineTo(0, 181*s);
            path.close();
            paint.setColor(Color.rgb(222, 230, 252));
            canvas.drawPath(path, paint);

            int w = getWidth(), h = getHeight();
            path.reset();
            path.moveTo(w, h - 245*s);
            path.lineTo(w - 34*s, h - 227*s);
            path.lineTo(w - 24*s, h - 160*s);
            path.lineTo(w - 82*s, h - 138*s);
            path.lineTo(w - 59*s, h - 101*s);
            path.lineTo(w, h - 111*s);
            path.close();
            paint.setColor(Color.rgb(229, 235, 251));
            canvas.drawPath(path, paint);
        }
    }

    /** A clean Telegram-style plane drawn locally, avoiding external image assets. */
    static final class TelegramMark extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        TelegramMark(Context ctx) {
            super(ctx);
            setContentDescription("Telegram");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight());
            float x = (getWidth() - size) / 2;
            float y = (getHeight() - size) / 2;
            int save = canvas.save();
            canvas.translate(x, y);
            canvas.scale(size / 100f, size / 100f);
            paint.setShader(new LinearGradient(20, 0, 80, 100,
                    Color.rgb(49, 190, 246), Color.rgb(1, 124, 195), Shader.TileMode.CLAMP));
            canvas.drawCircle(50, 50, 49, paint);
            paint.setShader(null);
            paint.setColor(Color.WHITE);
            path.reset();
            path.moveTo(17, 47);
            path.lineTo(78, 22);
            path.quadTo(84, 19, 82, 26);
            path.lineTo(73, 80);
            path.quadTo(72, 85, 67, 82);
            path.lineTo(48, 65);
            path.lineTo(40, 73);
            path.quadTo(37, 75, 37, 70);
            path.lineTo(38, 58);
            path.lineTo(19, 52);
            path.quadTo(15, 50, 17, 47);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(Color.rgb(12, 142, 209));
            path.reset();
            path.moveTo(38, 58);
            path.lineTo(68, 35);
            path.lineTo(48, 65);
            path.lineTo(40, 73);
            path.close();
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }
    }

    private AppUi() {}
}
