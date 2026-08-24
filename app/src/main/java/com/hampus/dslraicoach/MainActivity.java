package com.hampus.dslraicoach;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.remoteyourcam.usb.ptp.Camera;
import com.remoteyourcam.usb.ptp.PtpService;
import com.remoteyourcam.usb.ptp.model.LiveViewData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements Camera.CameraListener {
    private static final int PICK_IMAGE = 2001;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PtpService ptp;
    private Camera camera;
    private AiCoach aiCoach;
    private SharedPreferences prefs;
    private TextView cameraStatus, aiStatus, settingStatus, metricStatus, critique;
    private ImageView liveView, lastShot;
    private Button liveButton;
    private Bitmap lastBitmap;
    private ImageMetrics.Result lastMetrics;
    private String lastCameraData = "Camera metadata unavailable.";
    private boolean requestingLive;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("coach_preferences", MODE_PRIVATE);
        buildUi();
        aiCoach = new AiCoach();
        aiCoach.prepare((text, ready) -> aiStatus.setText("AI: " + text));
        ptp = PtpService.Singleton.getInstance(this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(text("DSLR AI Coach", 28, true));
        root.addView(text("Nikon D5200 + private on-device photography coaching", 14, false));
        cameraStatus = text("Camera: looking for Nikon USB…", 16, true);
        aiStatus = text("AI: checking Gemini Nano…", 14, false);
        settingStatus = text("Settings: —", 14, false);
        metricStatus = text("Technical analysis: —", 14, false);
        root.addView(cameraStatus); root.addView(aiStatus); root.addView(settingStatus); root.addView(metricStatus);

        root.addView(text("LIVE VIEW", 13, true));
        liveView = new ImageView(this);
        liveView.setBackgroundColor(Color.rgb(20,20,20));
        liveView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(liveView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        liveView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && camera != null && camera.isLiveViewAfAreaSupported()) {
                camera.setLiveViewAfArea(Math.max(0f, Math.min(1f, event.getX()/Math.max(1f,v.getWidth()))), Math.max(0f, Math.min(1f, event.getY()/Math.max(1f,v.getHeight()))));
                camera.focus(); return true;
            }
            return false;
        });

        LinearLayout controls = row();
        controls.addView(button("Reconnect", v -> reconnect()));
        liveButton = button("Start Live View", v -> toggleLive()); controls.addView(liveButton);
        controls.addView(button("AF", v -> { if (camera != null) camera.focus(); }));
        controls.addView(button("CAPTURE", v -> capture()));
        root.addView(scroller(controls));

        LinearLayout exposure = row();
        exposure.addView(button("ISO next", v -> cycle(Camera.Property.IsoSpeed)));
        exposure.addView(button("Aperture next", v -> cycle(Camera.Property.ApertureValue)));
        exposure.addView(button("Shutter next", v -> cycle(Camera.Property.ShutterSpeed)));
        exposure.addView(button("EV next", v -> cycle(Camera.Property.ExposureCompensation)));
        root.addView(scroller(exposure));

        root.addView(text("LAST SHOT", 13, true));
        lastShot = new ImageView(this);
        lastShot.setBackgroundColor(Color.rgb(235,235,235));
        lastShot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(lastShot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(350)));
        LinearLayout importRow = row();
        importRow.addView(button("Analyze phone photo", v -> pickImage()));
        importRow.addView(button("Analyze again", v -> analyzeLastShot()));
        root.addView(scroller(importRow));

        root.addView(text("COACH", 13, true));
        critique = text("Take a photo. After capture the app measures the frame and asks Gemini Nano for concrete feedback for your next shot.", 16, false);
        critique.setTextIsSelectable(true); root.addView(critique);
        root.addView(text("TEACH YOUR COACH", 13, true));
        LinearLayout ratings = row();
        ratings.addView(button("★ Best", v -> rate(2)));
        ratings.addView(button("♥ Like", v -> rate(1)));
        ratings.addView(button("○ Okay", v -> rate(0)));
        ratings.addView(button("✕ Bad", v -> rate(-1)));
        root.addView(scroller(ratings));
        root.addView(text("Your ratings stay on the phone and build a lightweight preference profile for future critiques.", 13, false));
        setContentView(scroll);
    }

    private void reconnect() {
        cameraStatus.setText("Camera: reconnecting…");
        ptp.shutdown(); ptp.setCameraListener(this); ptp.initialize(this, getIntent());
    }

    private void toggleLive() {
        if (camera == null) { toast("Connect the D5200 first"); return; }
        if (!camera.isLiveViewSupported()) { toast("Live View is not available in this camera session"); return; }
        camera.setLiveView(!camera.isLiveViewOpen());
    }

    private void capture() {
        if (camera == null) { toast("Connect the D5200 first"); return; }
        critique.setText("Capturing…"); camera.capture();
    }

    private void cycle(int property) {
        if (camera == null) return;
        if (!camera.isSettingPropertyPossible(property)) { toast("Setting unavailable in current camera mode"); return; }
        int[] values = camera.getPropertyDesc(property);
        if (values == null || values.length == 0) { toast("Camera did not report options"); return; }
        int current = camera.getProperty(property), index = 0;
        for (int i=0;i<values.length;i++) if (values[i] == current) { index=i; break; }
        camera.setProperty(property, values[(index+1)%values.length]);
        main.postDelayed(this::refreshSettings, 250);
    }

    private void refreshSettings() {
        if (camera == null) return;
        lastCameraData = "Camera: " + camera.getDeviceName() + "; " + setting(Camera.Property.ShutterSpeed,"shutter") + "; " + setting(Camera.Property.ApertureValue,"aperture") + "; " + setting(Camera.Property.IsoSpeed,"ISO") + "; " + setting(Camera.Property.ExposureCompensation,"EV") + ".";
        settingStatus.setText(lastCameraData);
    }

    private String setting(int property, String name) {
        try {
            int value = camera.getProperty(property); String shown = camera.propertyToString(property,value);
            return name + " " + (shown == null ? value : shown);
        } catch (Exception e) { return name + " unavailable"; }
    }

    private void handleShot(Bitmap bitmap, String source) {
        if (bitmap == null) { critique.setText("Capture completed but no JPEG preview was returned."); return; }
        lastBitmap = bitmap; lastShot.setImageBitmap(bitmap); lastMetrics = ImageMetrics.analyze(bitmap);
        metricStatus.setText(lastMetrics.shortSummary());
        if (camera != null) refreshSettings(); else lastCameraData = "Image source: " + source + ".";
        saveShot(bitmap, source); analyzeLastShot();
    }

    private void analyzeLastShot() {
        if (lastBitmap == null) { toast("Take or import a photo first"); return; }
        if (lastMetrics == null) lastMetrics = ImageMetrics.analyze(lastBitmap);
        String measured = ImageMetrics.deterministicAdvice(lastMetrics);
        critique.setText("Measured checks:\n" + measured + "\nAI is analyzing the shot…");
        aiCoach.analyze(lastBitmap, lastCameraData, lastMetrics.asPromptText(), preferenceMemory(), result -> {
            critique.setText(result + "\n\nMEASURED CHECKS\n" + measured);
            writeHistory("analysis", result.replace('\n',' '));
        });
    }

    private String preferenceMemory() {
        int count = prefs.getInt("positive_count",0);
        if (count == 0) return "Personal preference memory: none yet. Do not assume a style.";
        double b = prefs.getFloat("positive_brightness_sum",0f)/count;
        double s = prefs.getFloat("positive_sharpness_sum",0f)/count;
        return String.format(Locale.US,"Personal preference memory from %d positively rated shots: average measured brightness %.1f/100 and sharpness %.1f/100. Treat this as a weak preference signal.",count,b,s);
    }

    private void rate(int rating) {
        if (lastMetrics == null) { toast("No analyzed shot to rate"); return; }
        if (rating > 0) {
            int count = prefs.getInt("positive_count",0)+1;
            prefs.edit().putInt("positive_count",count)
                    .putFloat("positive_brightness_sum",prefs.getFloat("positive_brightness_sum",0f)+(float)lastMetrics.brightness)
                    .putFloat("positive_sharpness_sum",prefs.getFloat("positive_sharpness_sum",0f)+(float)lastMetrics.sharpness).apply();
        }
        writeHistory("rating", Integer.toString(rating)); toast("Rating saved");
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) { handleShot(BitmapFactory.decodeStream(in),"phone-import"); }
            catch (Exception e) { critique.setText("Could not read image: " + e.getMessage()); }
        }
    }

    private void saveShot(Bitmap bitmap, String source) {
        String filename = "DSLR-AI-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date()) + ".jpg";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues values = new ContentValues(); values.put(MediaStore.Images.Media.DISPLAY_NAME,filename); values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg"); values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/DSLR AI Coach");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if (uri != null) try (OutputStream out = getContentResolver().openOutputStream(uri)) { bitmap.compress(Bitmap.CompressFormat.JPEG,96,out); }
            } else {
                File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"DSLR-AI-Coach"); dir.mkdirs();
                try (OutputStream out = new FileOutputStream(new File(dir,filename))) { bitmap.compress(Bitmap.CompressFormat.JPEG,96,out); }
            }
            writeHistory("photo",filename+" source="+source);
        } catch (Exception e) { writeHistory("save_error",e.toString()); }
    }

    private void writeHistory(String type, String value) {
        try {
            File dir = new File(getFilesDir(),"history"); dir.mkdirs(); File file = new File(dir,"coach-history.jsonl");
            String safe = value.replace("\\","\\\\").replace("\"","\\\"");
            String line = "{\"time\":"+System.currentTimeMillis()+",\"type\":\""+type+"\",\"value\":\""+safe+"\"}\n";
            try (FileOutputStream out = new FileOutputStream(file,true)) { out.write(line.getBytes(StandardCharsets.UTF_8)); }
        } catch (Exception ignored) {}
    }

    @Override protected void onStart() { super.onStart(); ptp.setCameraListener(this); ptp.initialize(this,getIntent()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if (ptp != null) ptp.initialize(this,intent); }
    @Override protected void onStop() { super.onStop(); requestingLive=false; if (ptp != null) ptp.setCameraListener(null); }
    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing() && ptp != null) ptp.shutdown(); if (aiCoach != null) aiCoach.close(); }

    @Override public void onCameraStarted(Camera c) { camera=c; c.setCapturedPictureSampleSize(1); runOnUiThread(() -> { cameraStatus.setText("Camera: "+c.getDeviceName()+" connected"); refreshSettings(); }); }
    @Override public void onCameraStopped(Camera c) { camera=null; runOnUiThread(() -> cameraStatus.setText("Camera: disconnected")); }
    @Override public void onNoCameraFound() { runOnUiThread(() -> cameraStatus.setText("Camera: no Nikon found — connect D5200 by USB OTG, then Reconnect")); }
    @Override public void onError(String message) { runOnUiThread(() -> cameraStatus.setText("Camera error: "+message)); }
    @Override public void onPropertyChanged(int property,int value) { runOnUiThread(this::refreshSettings); }
    @Override public void onPropertyStateChanged(int property,boolean enabled) {}
    @Override public void onPropertyDescChanged(int property,int[] values) { runOnUiThread(this::refreshSettings); }
    @Override public void onLiveViewStarted() { requestingLive=true; runOnUiThread(() -> { liveButton.setText("Stop Live View"); if (camera != null) camera.getLiveViewPicture(null); }); }
    @Override public void onLiveViewData(LiveViewData data) { runOnUiThread(() -> { if (data != null && data.bitmap != null) liveView.setImageBitmap(data.bitmap); if (requestingLive && camera != null && camera.isLiveViewOpen()) main.postDelayed(() -> { if (requestingLive && camera != null && camera.isLiveViewOpen()) camera.getLiveViewPicture(data); },100); }); }
    @Override public void onLiveViewStopped() { requestingLive=false; runOnUiThread(() -> liveButton.setText("Start Live View")); }
    @Override public void onCapturedPictureReceived(int objectHandle,String filename,Bitmap thumbnail,Bitmap bitmap) { Bitmap chosen=bitmap!=null?bitmap:thumbnail; runOnUiThread(() -> handleShot(chosen,filename==null?"D5200":filename)); }
    @Override public void onBulbStarted() {}
    @Override public void onBulbExposureTime(int seconds) {}
    @Override public void onBulbStopped() {}
    @Override public void onFocusStarted() { runOnUiThread(() -> cameraStatus.setText("Camera: focusing…")); }
    @Override public void onFocusEnded(boolean focused) { runOnUiThread(() -> cameraStatus.setText("Camera: "+(focused?"focus locked":"focus ended"))); }
    @Override public void onFocusPointsChanged() {}
    @Override public void onObjectAdded(int handle,int format) {}

    private TextView text(String value,int sp,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(Color.rgb(25,25,25)); t.setPadding(0,dp(8),0,dp(8)); if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return t; }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(listener); return b; }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private HorizontalScrollView scroller(LinearLayout content) { HorizontalScrollView s=new HorizontalScrollView(this); s.setHorizontalScrollBarEnabled(false); s.addView(content); return s; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void toast(String t) { Toast.makeText(this,t,Toast.LENGTH_SHORT).show(); }
}
