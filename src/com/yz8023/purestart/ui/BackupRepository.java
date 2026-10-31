package com.yz8023.purestart.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.yz8023.purestart.Prefs;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/** Same JSON formats and merge semantics as ExtensionPage; independent of the UI. */
public final class BackupRepository {
    private final Context context;
    public BackupRepository(Context context) { this.context = context.getApplicationContext(); }

    public int exportTo(Uri uri) throws IOException, JSONException {
        JSONObject json = new JSONObject();
        for (Map.Entry<String, ?> entry : Prefs.sp(context).getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof String) {
                json.put(entry.getKey(), value);
            }
        }
        OutputStream opened = context.getContentResolver().openOutputStream(uri);
        if (opened == null) throw new IOException("无法写入");
        try (OutputStream stream = opened) {
            stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
        }
        return json.length();
    }

    public int importFrom(Uri uri) throws IOException, JSONException {
        InputStream opened = context.getContentResolver().openInputStream(uri);
        if (opened == null) throw new IOException("无法读取");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream stream = opened) {
            byte[] bytes = new byte[4096]; int count;
            while ((count = stream.read(bytes)) > 0) buffer.write(bytes, 0, count);
        }
        JSONObject json = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        SharedPreferences.Editor editor = Prefs.sp(context).edit();
        Iterator<String> keys = json.keys(); int count = 0;
        while (keys.hasNext()) {
            String key = keys.next(); Object value = json.get(key);
            if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else continue;
            count++;
        }
        editor.apply();
        return count;
    }
}
