package io.github.tgwsproxy.python114;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private TextView status;
    private EditText secret, port, pool, domains, dc2, dc4;
    private CheckBox cf, h2;
    private Button start, stop;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean resumed;

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

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
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4);
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(body);
        setContentView(scroll);

        TextView heading = label("TG WS Proxy 1.11.4", 23);
        heading.setTextColor(Color.rgb(20, 50, 90));
        body.addView(heading);
        body.addView(label("Оригинальное ядро Flowseal (Python 1.11.4)\n" +
                "Локальный адрес: 127.0.0.1; не нужен VPN или root.", 14));
        status = label("", 16);
        status.setTextColor(Color.rgb(0, 105, 90));
        body.addView(status);

        body.addView(label("Секрет MTProto (32 hex)", 14));
        secret = field(body, Settings.secret(this), false);
        Button regen = button(body, "Новый случайный секрет", () -> secret.setText(Settings.generateSecret()));
        regen.setTextSize(13);

        body.addView(label("Локальный порт", 14));
        port = field(body, Integer.toString(Settings.port(this)), true);
        body.addView(label("Прямые WebSocket-соединения на DC (0 = выключить)", 14));
        pool = field(body, Integer.toString(Settings.pool(this)), true);

        cf = new CheckBox(this);
        cf.setText("Резервный маршрут через Cloudflare");
        cf.setChecked(Settings.cf(this));
        body.addView(cf);
        h2 = new CheckBox(this);
        h2.setText("HTTP/2 для медиа через Cloudflare (как на ПК)");
        h2.setChecked(Settings.h2(this));
        body.addView(h2);

        body.addView(label("Cloudflare-домены (необязательно; через запятую)", 14));
        domains = field(body, Settings.domains(this), false);
        body.addView(label("IP перенаправления для DC2", 14));
        dc2 = field(body, Settings.dc2(this), false);
        body.addView(label("IP перенаправления для DC4", 14));
        dc4 = field(body, Settings.dc4(this), false);

        start = button(body, "Сохранить и запустить", this::startProxy);
        stop = button(body, "Остановить", () -> {
            Intent intent = new Intent(this, ProxyService.class).setAction(ProxyService.STOP);
            startService(intent);
            status.setText("Состояние: " + Settings.STOPPING);
        });
        button(body, "Открыть прокси в Telegram", () -> {
            String link = Settings.link(this);
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); }
            catch (Exception e) {
                copyLink(link);
                Toast.makeText(this, "Нет обработчика tg://. Ссылка скопирована.", Toast.LENGTH_LONG).show();
            }
        });
        button(body, "Копировать ссылку прокси", () -> copyLink(Settings.link(this)));
        body.addView(label("Важно: сначала запустите прокси. Для работы в мобильной сети " +
                "включите Cloudflare. При смене Wi-Fi/мобильной сети попробуйте перезапуск. " +
                "Если система останавливает службу, отключите для приложения оптимизацию батареи.", 13));
        refresh();
    }

    private TextView label(String value, int sp) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }
    private EditText field(LinearLayout parent, String value, boolean numeric) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        if (numeric) edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(value);
        edit.setTextSize(16);
        parent.addView(edit, new LinearLayout.LayoutParams(-1, dp(48)));
        return edit;
    }
    private Button button(LinearLayout parent, String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(51));
        lp.topMargin = dp(8);
        parent.addView(b, lp);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void copyLink(String value) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Telegram MTProto proxy", value));
        Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
    }
    private void startProxy() {
        try {
            String key = secret.getText().toString().trim();
            if (!Pattern.matches("(?i)[a-f0-9]{32}", key))
                throw new IllegalArgumentException("Секрет должен состоять из 32 шестнадцатеричных символов");
            int pn = Integer.parseInt(port.getText().toString().trim());
            int n = Integer.parseInt(pool.getText().toString().trim());
            if (pn < 1024 || pn > 65535) throw new IllegalArgumentException("Порт: 1024–65535");
            if (n < 0 || n > 32) throw new IllegalArgumentException("Пул: 0–32");
            String a = dc2.getText().toString().trim();
            String b = dc4.getText().toString().trim();
            if (!ipv4(a) || !ipv4(b)) throw new IllegalArgumentException("Проверьте IPv4-адреса DC2 и DC4");
            if (Settings.RUNNING.equals(Settings.status(this)) ||
                    Settings.STARTING.equals(Settings.status(this)) ||
                    Settings.STOPPING.equals(Settings.status(this)))
                throw new IllegalArgumentException("Сначала остановите запущенный прокси");
            SharedPreferences.Editor editor = Settings.of(this).edit();
            editor.putString("secret", key.toLowerCase())
                    .putInt("port", pn).putInt("pool", n)
                    .putBoolean("cf", cf.isChecked()).putBoolean("h2", h2.isChecked())
                    .putString("domains", domains.getText().toString().trim())
                    .putString("dc2", a).putString("dc4", b);
            if (!editor.commit()) throw new IllegalStateException("Не удалось записать настройки");
            Settings.status(this, Settings.STARTING);
            Intent intent = new Intent(this, ProxyService.class).setAction(ProxyService.START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            refresh();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private boolean ipv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String p : parts) {
            try {
                if (p.isEmpty() || p.length() > 3 || Integer.parseInt(p) > 255 || Integer.parseInt(p) < 0)
                    return false;
            } catch (NumberFormatException e) { return false; }
        }
        return true;
    }
    private void refresh() {
        String s = Settings.status(this);
        status.setText("Состояние: " + s);
        boolean active = Settings.RUNNING.equals(s) || Settings.STARTING.equals(s) ||
                Settings.STOPPING.equals(s);
        start.setEnabled(!active);
        stop.setEnabled(active && !Settings.STOPPING.equals(s));
    }
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (resumed) { refresh(); handler.postDelayed(this, 1000); }
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
