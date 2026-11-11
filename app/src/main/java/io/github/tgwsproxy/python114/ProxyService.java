package io.github.tgwsproxy.python114;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public final class ProxyService extends Service {
    static final String START = "tgwsproxy.START";
    static final String STOP = "tgwsproxy.STOP";
    private static final String CHANNEL = "local_proxy_status";
    private static final int NOTIFICATION_ID = 114;

    static volatile boolean activeProcess;
    private volatile boolean stopRequested;
    private volatile PyObject runner;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            Settings.status(this, Settings.STOPPING);
            requestStop();
            if (worker == null || !worker.isAlive()) stopSelf();
            return START_NOT_STICKY;
        }
        if (worker != null && worker.isAlive()) return START_STICKY;

        activeProcess = true;
        stopRequested = false;
        Settings.status(this, Settings.STARTING);
        try {
            showForegroundNotification();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TGWSDLCProxy:localProxy");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ex) {
            Settings.status(this, "Ошибка foreground-службы: " + ex.getMessage());
            stopSelf();
            return START_NOT_STICKY;
        }

        worker = new Thread(() -> {
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
                                if (!stopRequested) Settings.status(this, Settings.RUNNING);
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
                android.util.Log.e("TGWSDLCProxy", "Python proxy failed", ex);
                if (!stopRequested) {
                    String message = ex.getMessage();
                    Settings.status(this, "Ошибка ядра: " +
                            (message != null ? message : ex.getClass().getSimpleName()));
                }
            } finally {
                runner = null;
                if (!failure || stopRequested) Settings.status(this, Settings.STOPPED);
                stopSelf();
            }
        }, "Python-MTProto-proxy");
        worker.start();
        return START_STICKY;
    }

    private void requestStop() {
        stopRequested = true;
        PyObject module = runner;
        if (module != null) {
            Thread thread = new Thread(() -> {
                try { module.callAttr("stop"); }
                catch (Exception ex) { android.util.Log.e("TGWSDLCProxy", "Stop failed", ex); }
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
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ProxyService.class).setAction(STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 2, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("TGWS DLC Proxy")
                .setContentText("Локальный прокси 127.0.0.1 – порт " + Settings.port(this))
                .setContentIntent(contentIntent)
                .setOngoing(true)
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
        requestStop();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}
