package com.android.photo.camera.capture;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Utility class for device-specific optimizations and battery settings
 */
public class DeviceUtils {
    private static final String TAG = "DeviceUtils";

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
     * Different manufacturers have custom battery optimization systems that need to be handled
     */
    public static void applyManufacturerSpecificOptimizations(Activity activity) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        try {
            if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                applyXiaomiOptimizations(activity);
            } else if (manufacturer.contains("oppo")) {
                applyOppoOptimizations(activity);
            } else if (manufacturer.contains("vivo")) {
                applyVivoOptimizations(activity);
            } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                applyHuaweiOptimizations(activity);
            } else if (manufacturer.contains("samsung")) {
                applySamsungOptimizations(activity);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying manufacturer optimizations: " + e.getMessage());
        }
    }

    private static void applyXiaomiOptimizations(Activity activity) {
        try {
            // For MIUI - Xiaomi
            Intent intent = new Intent();
            intent.setClassName("com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity");
            intent.putExtra("package_name", activity.getPackageName());
            intent.putExtra("package_label", activity.getString(R.string.app_name));

            // Check if this activity exists
            if (activityExists(activity, intent)) {
                activity.startActivity(intent);
            } else {
                // Try alternative Xiaomi battery settings
                Intent altIntent = new Intent();
                altIntent.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));

                if (activityExists(activity, altIntent)) {
                    activity.startActivity(altIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Xiaomi optimizations", e);
        }
    }

    private static void applyOppoOptimizations(Activity activity) {
        try {
            // For ColorOS (OPPO, Realme)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"));

            if (activityExists(activity, intent)) {
                activity.startActivity(intent);
            } else {
                Intent altIntent = new Intent();
                altIntent.setComponent(new ComponentName("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"));

                if (activityExists(activity, altIntent)) {
                    activity.startActivity(altIntent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying OPPO optimizations", e);
        }
    }

    private static void applyVivoOptimizations(Activity activity) {
        try {
            // For FuntouchOS (Vivo)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));

            if (activityExists(activity, intent)) {
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Vivo optimizations", e);
        }
    }

    private static void applyHuaweiOptimizations(Activity activity) {
        try {
            // For EMUI (Huawei, Honor)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity"));

            if (activityExists(activity, intent)) {
                activity.startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error applying Huawei optimizations", e);
        }
    }

    private static void applySamsungOptimizations(Activity activity) {
        try {
            // For OneUI (Samsung)
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"));

            if (activityExists(activity, intent)) {
                activity.startActivity(intent);
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
        // This is a simplified approach that tries common methods
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

    /**
     * Disable power saving modes for the app if possible
     */
    public static void disablePowerSaving(Context context) {
        // This varies greatly by manufacturer
        // Here we attempt for some common manufacturers like Samsung
        try {
            if (Build.MANUFACTURER.toLowerCase().contains("samsung")) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"));
                if (activityExists(context, intent)) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disabling power saving", e);
        }
    }
}