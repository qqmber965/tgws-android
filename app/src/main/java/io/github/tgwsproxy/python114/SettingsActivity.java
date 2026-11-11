package io.github.tgwsproxy.python114;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.regex.Pattern;

/** All engine options moved off the home screen. Does not change the Python core. */
public final class SettingsActivity extends Activity {
    private EditText secret, port, pool, domains, workerDomain, dc2, dc4;
    private CheckBox cf, h2, workerEnabled;
    private int dp(float n) { return AppUi.dp(this, n); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = AppUi.canvas(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(32));
        scroll.addView(content);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));
        TextView back = AppUi.text(this, "‹", 40, AppUi.BLUE, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Назад");
        back.setBackground(AppUi.fill(0xE1E9FF, 15, this));
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        back.setOnClickListener(v -> finish());
        TextView title = AppUi.text(this, "Настройки", 25, AppUi.PURPLE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.leftMargin = dp(15);
        toolbar.addView(title, titleLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(21), dp(20), dp(23));
        card.setBackground(AppUi.outline(AppUi.CARD, 0xDCE4F6, 28, this));
        card.setElevation(dp(6));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.topMargin = dp(18);
        content.addView(card, cardLp);

        section(card, "Подключение");
        secret = field(card, "Секрет MTProto (32 символа HEX)", Settings.secret(this), false);
        Button regenerate = action(card, "Создать новый секрет", false);
        regenerate.setOnClickListener(v -> secret.setText(Settings.generateSecret()));
        port = field(card, "Локальный порт", Integer.toString(Settings.port(this)), true);
        pool = field(card, "Пул WebSocket-соединений (0–32)", Integer.toString(Settings.pool(this)), true);

        section(card, "Маршруты");
        cf = check(card, "Резервный маршрут через Cloudflare", Settings.cf(this));
        h2 = check(card, "HTTP/2 для медиа через Cloudflare", Settings.h2(this));
        domains = field(card, "Cloudflare-домены (через запятую)", Settings.domains(this), false);
        section(card, "Собственный Cloudflare Worker");
        workerEnabled = check(card, "Использовать Cloudflare Worker", Settings.workerEnabled(this));
        workerDomain = field(card, "Домен Worker (без https:// и пути)", Settings.workerDomain(this), false);
        TextView workerHelp = AppUi.text(this,
                "Для Yota: включите Worker, укажите tgws.dlcpromo.ru, установите пул 0, " +
                "отключите обычный Cloudflare и HTTP/2. На других сетях можно оставить прежние настройки.",
                13, AppUi.SECONDARY, false);
        workerHelp.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams workerHelpLp = new LinearLayout.LayoutParams(-1, -2);
        workerHelpLp.topMargin = dp(9);
        card.addView(workerHelp, workerHelpLp);
        dc2 = field(card, "IP перенаправления DC2", Settings.dc2(this), false);
        dc4 = field(card, "IP перенаправления DC4", Settings.dc4(this), false);

        Button save = action(card, "Сохранить настройки", true);
        save.setOnClickListener(v -> save());
        Button copy = action(card, "Копировать ссылку Telegram", false);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Telegram MTProto proxy", Settings.httpsLink(this)));
            Toast.makeText(this, "Ссылка скопирована", Toast.LENGTH_SHORT).show();
        });
        TextView help = AppUi.text(this,
                "Для изменения настроек сначала выключите прокси. Они будут использованы при следующем запуске. " +
                "После смены порта или секрета повторно примените прокси в Telegram.",
                13, AppUi.SECONDARY, false);
        help.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2);
        helpLp.topMargin = dp(17);
        card.addView(help, helpLp);
        setContentView(root);
    }

    private void section(LinearLayout parent, String heading) {
        TextView label = AppUi.text(this, heading, 19, AppUi.BLUE, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(9);
        lp.bottomMargin = dp(10);
        parent.addView(label, lp);
    }

    private EditText field(LinearLayout parent, String title, String value, boolean numeric) {
        TextView label = AppUi.text(this, title, 14, AppUi.TEXT, false);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.topMargin = dp(13);
        parent.addView(label, labelLp);
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setTextColor(AppUi.TEXT);
        edit.setSelectAllOnFocus(false);
        edit.setPadding(dp(12), 0, dp(12), 0);
        edit.setBackground(AppUi.outline(0xFFFFFFFF, 0xCFD9EC, 13, this));
        if (numeric) edit.setInputType(InputType.TYPE_CLASS_NUMBER);
        edit.setText(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(7);
        parent.addView(edit, lp);
        return edit;
    }

    private CheckBox check(LinearLayout parent, String title, boolean value) {
        CheckBox cb = new CheckBox(this);
        cb.setText(title);
        cb.setTextSize(15);
        cb.setTextColor(AppUi.TEXT);
        cb.setChecked(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(5);
        parent.addView(cb, lp);
        return cb;
    }

    private Button action(LinearLayout parent, String name, boolean primary) {
        Button b = AppUi.button(this, name, primary ? AppUi.BLUE : 0xFFDCE6FD,
                primary ? 0xFFFFFFFF : AppUi.TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(53));
        lp.topMargin = dp(14);
        parent.addView(b, lp);
        return b;
    }

    private void save() {
        try {
            String state = Settings.status(this);
            if (Settings.RUNNING.equals(state) || Settings.STARTING.equals(state) ||
                    Settings.STOPPING.equals(state)) {
                throw new IllegalArgumentException("Сначала выключите прокси на главном экране");
            }
            String key = secret.getText().toString().trim();
            if (!Pattern.matches("(?i)[a-f0-9]{32}", key))
                throw new IllegalArgumentException("Секрет: ровно 32 шестнадцатеричных символа");
            int pn = Integer.parseInt(port.getText().toString().trim());
            int n = Integer.parseInt(pool.getText().toString().trim());
            if (pn < 1024 || pn > 65535) throw new IllegalArgumentException("Порт: 1024–65535");
            if (n < 0 || n > 32) throw new IllegalArgumentException("Пул: 0–32");
            String a = dc2.getText().toString().trim();
            String b = dc4.getText().toString().trim();
            if (!validIPv4(a) || !validIPv4(b))
                throw new IllegalArgumentException("Проверьте IPv4-адреса DC2 и DC4");
            String workerHost = workerDomain.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (workerEnabled.isChecked() && !validDomain(workerHost))
                throw new IllegalArgumentException("Укажите домен Worker без https:// и пути");
            if (!workerHost.isEmpty() && !validDomain(workerHost))
                throw new IllegalArgumentException("Некорректное доменное имя Worker");
            SharedPreferences.Editor e = Settings.of(this).edit();
            e.putString("secret", key.toLowerCase(java.util.Locale.ROOT));
            e.putInt("port", pn).putInt("pool", n);
            e.putBoolean("cf", cf.isChecked()).putBoolean("h2", h2.isChecked());
            e.putString("domains", domains.getText().toString().trim());
            e.putBoolean("worker_enabled", workerEnabled.isChecked());
            e.putString("worker_domain", workerHost);
            e.putString("dc2", a).putString("dc4", b);
            if (!e.commit()) throw new IllegalStateException("Не удалось записать настройки");
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException ex) {
            Toast.makeText(this, "Порт и пул должны быть целыми числами", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean validDomain(String host) {
        if (host.length() > 253 || host.isEmpty()) return false;
        if (host.contains("..") || host.startsWith(".") || host.endsWith(".")) return false;
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return false;
        for (String label : labels) {
            if (label.length() < 1 || label.length() > 63 ||
                    !Pattern.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", label)) return false;
        }
        return true;
    }

    private static boolean validIPv4(String s) {
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                if (part.isEmpty() || part.length() > 3) return false;
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException ex) { return false; }
        }
        return true;
    }
}
