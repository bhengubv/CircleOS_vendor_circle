/*
 * Copyright (C) 2026 CircleOS
 * SPDX-License-Identifier: Apache-2.0
 *
 * Socket file transfer for Quick Share. The Wi-Fi Direct group owner runs the
 * server socket; the client connects to it (retrying briefly until the server
 * is up). The sender writes [name][bytes] and the receiver saves to
 * Download/Circle. Direction (send/receive) comes from the UI, independent of
 * which side became group owner.
 */
package za.co.circleos.quickshare;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

final class FileTransfer extends Thread {

    interface Callback {
        void done(boolean ok, String message);
    }

    private final Context mCtx;
    private final boolean mIsGroupOwner;
    private final String mHost;
    private final boolean mSend;
    private final Uri mUri;
    private final Callback mCb;

    FileTransfer(Context ctx, boolean isGroupOwner, String host, boolean send, Uri uri, Callback cb) {
        mCtx = ctx.getApplicationContext();
        mIsGroupOwner = isGroupOwner;
        mHost = host;
        mSend = send;
        mUri = uri;
        mCb = cb;
    }

    @Override
    public void run() {
        ServerSocket server = null;
        Socket socket = null;
        try {
            if (mIsGroupOwner) {
                server = new ServerSocket(CircleQuickShareActivity.PORT);
                server.setSoTimeout(60000);
                socket = server.accept();
            } else {
                socket = connectWithRetry();
            }
            if (mSend) sendFile(socket);
            else receiveFile(socket);
            done(true, mSend ? "Sent." : "Received — saved to Download/Circle.");
        } catch (Throwable t) {
            done(false, (mSend ? "Send failed: " : "Receive failed: ") + safe(t.getMessage()));
        } finally {
            closeQuietly(socket);
            closeQuietly(server);
        }
    }

    private Socket connectWithRetry() throws Exception {
        if (mHost == null) throw new Exception("no host address");
        long deadline = System.currentTimeMillis() + 20000;
        while (true) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(mHost, CircleQuickShareActivity.PORT), 5000);
                return s;
            } catch (Throwable t) {
                closeQuietly(s);
                if (System.currentTimeMillis() > deadline) throw new Exception("couldn't reach the other phone");
                Thread.sleep(800);
            }
        }
    }

    private void sendFile(Socket socket) throws Exception {
        String name = displayName(mUri);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(name);
        dos.flush();
        try (InputStream in = mCtx.getContentResolver().openInputStream(mUri)) {
            if (in == null) throw new Exception("can't open the chosen file");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) dos.write(buf, 0, n);
            dos.flush();
        }
        socket.shutdownOutput();
    }

    private void receiveFile(Socket socket) throws Exception {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String name = dis.readUTF();
        if (name == null || name.isEmpty()) name = "circle_share";
        Uri out = createDownload(name);
        if (out == null) throw new Exception("can't create the destination file");
        try (OutputStream os = mCtx.getContentResolver().openOutputStream(out)) {
            if (os == null) throw new Exception("can't write the destination file");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = dis.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
        }
    }

    private Uri createDownload(String name) {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
        cv.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Circle");
        return mCtx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
    }

    private String displayName(Uri uri) {
        String name = "circle_share";
        try (Cursor c = mCtx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    String s = c.getString(i);
                    if (s != null && !s.isEmpty()) name = s;
                }
            }
        } catch (Throwable ignored) {
        }
        return name;
    }

    private void done(boolean ok, String msg) {
        if (mCb != null) mCb.done(ok, msg);
    }

    private static String safe(String s) {
        return s == null ? "unknown error" : s;
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try { c.close(); } catch (Throwable ignored) {}
        }
    }
}
