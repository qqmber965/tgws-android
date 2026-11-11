package io.github.tgwsproxy.python114;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

final class Settings {
    static final String STORE = "tg_ws_proxy_114";
    static final String STATUS = "runtime_status";
    static final String PROBE = "telegram_probe_status";
    static final String STOPPED = "Остановлен";
    static final String STARTING = "Запускается…";
    static final String STOPPING = "Останавливается…";
    static final String RUNNING = "Работает на 127.0.0.1";

    static SharedPreferences of(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    static String secret(Context context) {
        SharedPreferences p = of(context);
        String secret = p.getString("secret", "");
        if (secret.length() != 32) {
            secret = generateSecret();
            p.edit().putString("secret", secret).apply();
        }
        return secret;
    }

    static String generateSecret() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        char[] chars = "0123456789abcdef".toCharArray();
        StringBuilder out = new StringBuilder(32);
        for (byte b : bytes) {
            out.append(chars[(b >> 4) & 0xf]).append(chars[b & 0xf]);
        }
        return out.toString();
    }

    static int port(Context context) { return of(context).getInt("port", 1443); }
    static int pool(Context context) { return of(context).getInt("pool", 4); }
    static boolean cf(Context context) { return of(context).getBoolean("cf", true); }
    static boolean h2(Context context) { return of(context).getBoolean("h2", true); }
    static boolean workerEnabled(Context context) { return of(context).getBoolean("worker_enabled", false); }
    static String workerDomain(Context context) { return of(context).getString("worker_domain", "tgws.dlcpromo.ru"); }
    static String domains(Context context) { return of(context).getString("domains", ""); }
    static String dc2(Context context) { return of(context).getString("dc2", "149.154.167.220"); }
    static String dc4(Context context) { return of(context).getString("dc4", "149.154.167.220"); }
    static String status(Context context) { return of(context).getString(STATUS, STOPPED); }
    static void status(Context context, String value) { of(context).edit().putString(STATUS, value).apply(); }
    static String probe(Context context) { return of(context).getString(PROBE, ""); }
    static void probe(Context context, String value) { of(context).edit().putString(PROBE, value).apply(); }
    static String link(Context context) {
        return "tg://proxy?server=127.0.0.1&port=" + port(context) + "&secret=dd" + secret(context);
    }

    static String httpsLink(Context context) {
        return "https://t.me/proxy?server=127.0.0.1&port=" + port(context) +
                "&secret=dd" + secret(context);
    }

    private Settings() {}
}
