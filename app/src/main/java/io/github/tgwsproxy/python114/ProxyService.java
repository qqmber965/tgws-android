package io.github.tgwsproxy.python114;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** Serializes start, stop, and restart on the Android main thread. */
public final class ProxyService extends Service {
    static final String START = "tgwsproxy.START";
    static final String STOP = "tgwsproxy.STOP";
    static final String RESTART = "tgwsproxy.RESTART";
    private static final String CHANNEL = "local_proxy_status";
    private static final int NOTIFICATION_ID = 114;
    private final Handler main = new Handler(Looper.getMainLooper());

    static volatile boolean activeProcess;
    private volatile boolean stopRequested;
    private volatile PyObject runner;
    private boolean restartPending;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;
    private long runGeneration;
    private int latestStartId;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        String action = intent == null ? START : intent.getAction();
        if (STOP.equals(action)) {
            restartPending = false;
            Settings.status(this, Settings.STOPPING);
            Settings.probe(this, "");
            requestStop();
            if (worker == null || !worker.isAlive()) stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (RESTART.equals(action)) {
            if (worker != null && worker.isAlive()) {
                restartPending = true;
                Settings.status(this, Settings.STARTING);
                Settings.probe(this, "Перезапуск прокси…");
                requestStop();
                return START_STICKY;
            }
        }
        if (worker != null && worker.isAlive()) return START_STICKY;
        beginStart();
        return START_STICKY;
    }

    private void beginStart() {
        activeProcess = true;
        restartPending = false;
        stopRequested = false;
        final long generation = ++runGeneration;
        Settings.status(this, Settings.STARTING);
        Settings.probe(this, "Ожидание запуска прокси…");
        try {
            showForegroundNotification();
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGWSDLCProxy:localProxy");
                wakeLock.setReferenceCounted(false);
            }
            if (!wakeLock.isHeld()) wakeLock.acquire();
        } catch (Exception ex) {
            Settings.status(this, "Ошибка foreground-службы: " + ex.getMessage());
            Settings.probe(this, "");
            stopSelfResult(latestStartId);
            return;
        }
        worker = new Thread(() -> runProxy(generation), "Python-MTProto-proxy");
        worker.start();
    }

    private void runProxy(long generation) {
        boolean failure = false;
        try {
            synchronized (ProxyService.class) {
                if (!Python.isStarted()) Python.start(new AndroidPlatform(getApplicationContext()));
            }
            PyObject module = Python.getInstance().getModule("mobile_runner");
            runner = module;
            module.callAttr("reset");
            if (stopRequested) { module.callAttr("stop"); return; }

            Thread monitor = new Thread(() -> {
                for (int i = 0; i < 100 && !stopRequested; i++) {
                    try {
                        if (module.callAttr("is_listening").toBoolean()) {
                            if (!stopRequested && generation == runGeneration) {
                                Settings.status(this, Settings.RUNNING);
                                Settings.probe(this, "Проверка соединения с Telegram…");
                                startTelegramProbe(module, generation);
                            }
                            return;
                        }
                        Thread.sleep(300);
                    } catch (Exception ex) { return; }
                }
            }, "Proxy-listener-monitor");
            monitor.setDaemon(true);
            monitor.start();

            module.callAttr("start",
                    Settings.secret(this), Settings.port(this), Settings.pool(this),
                    Settings.cf(this), Settings.h2(this), Settings.domains(this),
                    Settings.dc2(this), Settings.dc4(this),
                    Settings.workerEnabled(this), Settings.workerDomain(this));
            if (!stopRequested) {
                failure = true;
                Settings.status(this, "Прокси завершился без команды остановки");
            }
        } catch (Exception ex) {
            failure = true;
            Log.e("TGWSDLCProxy", "Python proxy failed", ex);
            if (!stopRequested) {
                String msg = ex.getMessage();
                Settings.status(this, "Ошибка ядра: " +
                        (msg != null ? msg : ex.getClass().getSimpleName()));
            }
        } finally {
            runner = null;
            boolean completedNormally = !failure || stopRequested;
            main.post(() -> {
                // A stale finish callback may not stop a newer run.
                if (generation != runGeneration) return;
                worker = null;
                if (restartPending) {
                    beginStart();
                } else {
                    if (completedNormally) Settings.status(this, Settings.STOPPED);
                    Settings.probe(this, "");
                    stopSelfResult(latestStartId);
                }
            });
        }
    }

    private void startTelegramProbe(PyObject module, long generation) {
        Thread probe = new Thread(() -> {
            String result;
            try {
                boolean dc2 = module.callAttr("probe_telegram", Settings.secret(this),
                        Settings.port(this), 2, 11).toBoolean();
                if (dc2) {
                    result = "Связь с Telegram подтверждена (DC2)";
                } else {
                    boolean dc4 = module.callAttr("probe_telegram", Settings.secret(this),
                            Settings.port(this), 4, 11).toBoolean();
                    result = dc4 ? "Связь с Telegram подтверждена (DC4)" :
                            "Нет ответа от Telegram (DC2/DC4)";
                }
            } catch (Exception ex) {
                Log.w("TGWSDLCProxy", "Telegram connection probe failed", ex);
                result = "Не удалось проверить соединение с Telegram";
            }
            if (!stopRequested && generation == runGeneration &&
                    Settings.RUNNING.equals(Settings.status(this))) {
                Settings.probe(this, result);
            }
        }, "Telegram-connectivity-probe");
        probe.setDaemon(true);
        probe.start();
    }

    private void requestStop() {
        stopRequested = true;
        PyObject module = runner;
        if (module != null) {
            Thread thread = new Thread(() -> {
                try { module.callAttr("stop"); }
                catch (Exception ex) { Log.e("TGWSDLCProxy", "Stop failed", ex); }
            }, "Proxy-stop");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void showForegroundNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Локальный MTProto-прокси",
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(channel);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 1,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent restartIntent = PendingIntent.getService(this, 3,
                new Intent(this, ProxyService.class).setAction(RESTART),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopIntent = PendingIntent.getService(this, 2,
                new Intent(this, ProxyService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("TGWS DLC Proxy")
                .setContentText("Локальный прокси 127.0.0.1 – порт " + Settings.port(this))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Перезапустить", restartIntent).build())
                .addAction(new Notification.Action.Builder(null, "Остановить", stopIntent).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override public void onDestroy() {
        activeProcess = false;
        restartPending = false;
        ++runGeneration;
        requestStop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
