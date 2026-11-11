package io.github.tgwsproxy.python114;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal home screen: power, apply to Telegram, and a settings entry in the toolbar. */
public final class MainActivity extends Activity {
    private TextView status, connectionStatus;
    private AppUi.TelegramMark mark;
    private Button power, apply;
    private boolean resumed;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int dp(float value) { return AppUi.dp(this, value); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String previous = Settings.status(this);
        if (!ProxyService.activeProcess &&
                (Settings.STARTING.equals(previous) || Settings.RUNNING.equals(previous) ||
                 Settings.STOPPING.equals(previous))) {
            Settings.status(this, Settings.STOPPED);
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4);
        }
        FrameLayout root = AppUi.canvas(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(22));
        scroll.addView(content);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.TOP | Gravity.CENTER_VERTICAL);
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(104)));

        TextView title = AppUi.text(this, "TGWS\nDLC Proxy", 29, AppUi.BLUE, true);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        title.setLetterSpacing(0.025f);
        title.setLineSpacing(-dp(2), 1f);
        title.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView gear = AppUi.text(this, "⚙", 32, AppUi.BLUE, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Открыть настройки");
        gear.setBackground(AppUi.fill(0xE1E9FF, 18, this));
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        gearLp.topMargin = dp(12);
        toolbar.addView(gear, gearLp);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        View before = new View(this);
        content.addView(before, new LinearLayout.LayoutParams(1, 0, 0.85f));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(29), dp(24), dp(28));
        card.setBackground(AppUi.outline(AppUi.CARD, 0xDCE4F6, 30, this));
        card.setElevation(dp(9));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        mark = new AppUi.TelegramMark(this);
        mark.setOnClickListener(v -> toggleProxy());
        card.addView(mark, new LinearLayout.LayoutParams(dp(162), dp(162)));

        status = AppUi.text(this, "Отключено", 20, AppUi.TEXT, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(-1, dp(60));
        stateLp.topMargin = dp(12);
        stateLp.bottomMargin = dp(3);
        card.addView(status, stateLp);

        connectionStatus = AppUi.text(this, "Проверка соединения начнётся при включении", 13,
                AppUi.SECONDARY, false);
        connectionStatus.setGravity(Gravity.CENTER);
        connectionStatus.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams probeLp = new LinearLayout.LayoutParams(-1, -2);
        probeLp.bottomMargin = dp(17);
        card.addView(connectionStatus, probeLp);

        power = AppUi.button(this, "Включить прокси", AppUi.BLUE, 0xFFFFFFFF);
        LinearLayout.LayoutParams powerLp = new LinearLayout.LayoutParams(-1, dp(60));
        card.addView(power, powerLp);
        power.setOnClickListener(v -> toggleProxy());

        apply = AppUi.button(this, "Применить в Telegram", AppUi.SOFT_BLUE, AppUi.TEXT);
        LinearLayout.LayoutParams applyLp = new LinearLayout.LayoutParams(-1, dp(60));
        applyLp.topMargin = dp(12);
        card.addView(apply, applyLp);
        apply.setOnClickListener(v -> openTelegram());

        TextView caption = AppUi.text(this, "Локальный MTProto-прокси", 12, AppUi.SECONDARY, false);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionLp = new LinearLayout.LayoutParams(-1, -2);
        captionLp.topMargin = dp(21);
        card.addView(caption, captionLp);

        View after = new View(this);
        content.addView(after, new LinearLayout.LayoutParams(1, 0, 1.15f));
        setContentView(root);
        refresh();
    }

    private void toggleProxy() {
        String s = Settings.status(this);
        if (Settings.RUNNING.equals(s) || Settings.STARTING.equals(s)) {
            Settings.status(this, Settings.STOPPING);
            startService(new Intent(this, ProxyService.class).setAction(ProxyService.STOP));
        } else if (Settings.STOPPING.equals(s)) {
            return;
        } else {
            Settings.status(this, Settings.STARTING);
            try {
                Intent intent = new Intent(this, ProxyService.class).setAction(ProxyService.START);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
                else startService(intent);
            } catch (Exception ex) {
                Settings.status(this, "Ошибка запуска: " + ex.getMessage());
                Toast.makeText(this, "Не удалось запустить прокси", Toast.LENGTH_LONG).show();
            }
        }
        refresh();
    }

    private void openTelegram() {
        if (!Settings.RUNNING.equals(Settings.status(this))) return;
        // Prefer Telegram's MTProto URI. If it has no handler, provide the HTTPS fallback.
        String tgLink = Settings.link(this);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(tgLink));
            startActivity(intent);
        } catch (Exception ex) {
            String https = Settings.httpsLink(this);
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(https))); }
            catch (Exception second) {
                ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                manager.setPrimaryClip(ClipData.newPlainText("Telegram proxy", https));
                Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refresh() {
        if (status == null) return;
        String s = Settings.status(this);
        boolean running = Settings.RUNNING.equals(s);
        boolean starting = Settings.STARTING.equals(s);
        boolean stopping = Settings.STOPPING.equals(s);
        if (running) status.setText("Прокси работает");
        else if (starting) status.setText("Запуск…");
        else if (stopping) status.setText("Остановка…");
        else if (Settings.STOPPED.equals(s)) status.setText("Отключено");
        else status.setText(s); // Show the error reported by the existing service.
        status.setTextColor(running ? AppUi.BLUE : (starting || stopping ? AppUi.SECONDARY : AppUi.TEXT));
        mark.setActive(running);
        mark.setEnabled(!stopping);
        String probe = Settings.probe(this);
        connectionStatus.setText(running || starting ?
                (probe.isEmpty() ? "Проверка соединения с Telegram…" : probe) :
                "Проверка соединения начнётся при включении");
        connectionStatus.setTextColor(running && probe.startsWith("Связь с Telegram подтверждена")
                ? AppUi.BLUE : AppUi.SECONDARY);
        power.setText(running || starting ? "Выключить прокси" : "Включить прокси");
        power.setEnabled(!stopping);
        apply.setEnabled(running);
        apply.setAlpha(running ? 1f : 0.50f);
    }

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (resumed) {
                refresh();
                handler.postDelayed(this, 1000);
            }
        }
    };
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }
    @Override protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }
}
