package com.hampus.dslraicoach;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hampus.dslraicoach.camera.DslrNikonCamera;
import com.hampus.dslraicoach.camera.NikonPayloads;
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
    private static final int BG = 0xFFF3F0E8;
    private static final int SURFACE = 0xFFFFFCF4;
    private static final int INK = 0xFF1F2933;
    private static final int MUTED = 0xFF65737E;
    private static final int ACCENT = 0xFF25636B;
    private static final int PREVIEW = 0xFF111820;
    private static final int PAGE_CONNECT = 0;
    private static final int PAGE_CAMERA = 1;
    private static final int PAGE_COACH = 2;
    private final Handler main = new Handler(Looper.getMainLooper());
    private PtpService ptp;
    private Camera camera;
    private AiCoach aiCoach;
    private SharedPreferences prefs;
    private TextView cameraStatus, aiStatus, settingStatus, metricStatus, critique, retrievalStatus, liveStatus;
    private ImageView liveView, lastShot;
    private Button liveButton, connectTab, cameraTab, coachTab;
    private FrameLayout pageHost;
    private View connectPage, cameraPage, coachPage;
    private Bitmap lastBitmap;
    private ImageMetrics.Result lastMetrics;
    private String lastCameraData = "Camera metadata unavailable.";
    private boolean requestingLive;
    private boolean liveStartPending;
    private int currentPage = PAGE_CONNECT;

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
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(BG);
        shell.setPadding(dp(16), dp(18), dp(16), 0);

        TextView title = text("DSLR AI Coach", 24, true);
        title.setLetterSpacing(-0.02f);
        shell.addView(title);

        LinearLayout tabs = row();
        connectTab = tabButton("Connect", PAGE_CONNECT);
        cameraTab = tabButton("Camera", PAGE_CAMERA);
        coachTab = tabButton("Coach", PAGE_COACH);
        tabs.addView(connectTab);
        tabs.addView(cameraTab);
        tabs.addView(coachTab);
        shell.addView(tabs);

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        connectPage = buildConnectPage();
        cameraPage = buildCameraPage();
        coachPage = buildCoachPage();
        pageHost.addView(connectPage);
        pageHost.addView(cameraPage);
        pageHost.addView(coachPage);
        showPage(PAGE_CONNECT);
        setContentView(shell);
    }

    private View buildConnectPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        LinearLayout statusCard = card();
        statusCard.addView(sectionTitle("Session"));
        cameraStatus = pill("Camera: looking for Nikon USB...");
        aiStatus = pill("AI: checking Gemini Nano...");
        settingStatus = text("Settings: --", 14, false);
        metricStatus = text("Technical analysis: --", 14, false);
        statusCard.addView(cameraStatus);
        statusCard.addView(aiStatus);
        statusCard.addView(settingStatus);
        statusCard.addView(metricStatus);
        root.addView(statusCard);

        LinearLayout actions = card();
        actions.addView(sectionTitle("Connection actions"));
        LinearLayout row = row();
        row.addView(button("Reconnect", v -> reconnect()));
        row.addView(button("Fetch latest", v -> fetchLatestFromCamera()));
        actions.addView(row);
        TextView help = text("Physical shutter captures auto-fetch while connected. Use Fetch latest if you shot while reconnecting.", 14, false);
        help.setTextColor(MUTED);
        actions.addView(help);
        root.addView(actions);
        return scroll;
    }

    private View buildCameraPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        LinearLayout previewCard = card();
        previewCard.addView(sectionTitle("Live view"));
        liveView = new ImageView(this);
        stylePreview(liveView, PREVIEW);
        liveView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewCard.addView(liveView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(430)));
        liveStatus = text("Live view stopped. Tap Live to start.", 14, false);
        liveStatus.setTextColor(MUTED);
        previewCard.addView(liveStatus);
        liveView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP && camera != null && camera.isLiveViewAfAreaSupported()) {
                camera.setLiveViewAfArea(Math.max(0f, Math.min(1f, event.getX()/Math.max(1f,v.getWidth()))), Math.max(0f, Math.min(1f, event.getY()/Math.max(1f,v.getHeight()))));
                camera.focus(); return true;
            }
            return false;
        });

        LinearLayout controls = row();
        liveButton = button("Live", v -> toggleLive()); controls.addView(liveButton);
        controls.addView(button("AF", v -> { if (camera != null) camera.focus(); }));
        controls.addView(button("Shoot", v -> capture()));
        previewCard.addView(scroller(controls));

        LinearLayout exposure = row();
        exposure.addView(button("ISO", v -> cycle(Camera.Property.IsoSpeed)));
        exposure.addView(button("Aperture", v -> cycle(Camera.Property.ApertureValue)));
        exposure.addView(button("Shutter", v -> cycle(Camera.Property.ShutterSpeed)));
        exposure.addView(button("EV", v -> cycle(Camera.Property.ExposureCompensation)));
        previewCard.addView(scroller(exposure));
        root.addView(previewCard);
        return scroll;
    }

    private View buildCoachPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageRoot(scroll);
        LinearLayout shotCard = card();
        shotCard.addView(sectionTitle("Last shot"));
        lastShot = new ImageView(this);
        stylePreview(lastShot, 0xFFE6E0D2);
        lastShot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        shotCard.addView(lastShot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)));
        LinearLayout importRow = row();
        importRow.addView(button("Import", v -> pickImage()));
        importRow.addView(button("Analyze", v -> analyzeLastShot()));
        importRow.addView(button("Fetch latest", v -> fetchLatestFromCamera()));
        shotCard.addView(scroller(importRow));
        retrievalStatus = text("Waiting for a camera photo or phone import.", 14, false);
        retrievalStatus.setTextColor(MUTED);
        shotCard.addView(retrievalStatus);
        root.addView(shotCard);

        LinearLayout coachCard = card();
        coachCard.addView(sectionTitle("Coach"));
        critique = text("", 16, false);
        critique.setLineSpacing(dp(2), 1.05f);
        critique.setTextIsSelectable(true);
        coachCard.addView(critique);
        setCoachMarkdown("Take a photo. After capture, the app measures the frame and asks Gemini Nano for concrete feedback for your next shot.");
        coachCard.addView(sectionTitle("Teach your coach"));
        LinearLayout ratings = row();
        ratings.addView(button("Best", v -> rate(2)));
        ratings.addView(button("Like", v -> rate(1)));
        ratings.addView(button("Okay", v -> rate(0)));
        ratings.addView(button("Bad", v -> rate(-1)));
        coachCard.addView(scroller(ratings));
        TextView help = text("Ratings stay on the phone and build a lightweight preference profile for future critiques.", 13, false);
        help.setTextColor(MUTED);
        coachCard.addView(help);
        root.addView(coachCard);
        return scroll;
    }

    private void reconnect() {
        cameraStatus.setText("Camera: reconnecting...");
        ptp.shutdown(); ptp.setCameraListener(this); ptp.initialize(this, getIntent());
    }

    private void toggleLive() {
        if (camera == null) { setLiveStatus("Connect the D5200 first."); toast("Connect the D5200 first"); return; }
        if (!camera.isLiveViewSupported()) { setLiveStatus("Live view is not available in this camera session."); toast("Live View is not available in this camera session"); return; }
        boolean opening = !camera.isLiveViewOpen();
        liveStartPending = opening;
        setLiveStatus(opening ? "Starting live view..." : "Stopping live view...");
        camera.setLiveView(opening);
        if (opening) {
            main.postDelayed(() -> {
                if (liveStartPending && camera != null && !camera.isLiveViewOpen()) {
                    liveStartPending = false;
                    liveButton.setText("Live");
                    setLiveStatus("Live view did not start. Check camera battery, mode dial, and USB session, then tap Live again.");
                }
            }, 8000);
        }
    }

    private void capture() {
        if (camera == null) { toast("Connect the D5200 first"); return; }
        setCoachMarkdown("Capturing...");
        setRetrievalStatus("Waiting for camera image event or capture-complete fallback...");
        setLiveStatus("Capturing. Live view may pause briefly.");
        showPage(PAGE_CAMERA);
        camera.capture();
    }

    private void fetchLatestFromCamera() {
        if (camera == null) { toast("Connect the D5200 first"); return; }
        setRetrievalStatus("Scanning camera metadata for newest image...");
        if (camera instanceof DslrNikonCamera) {
            ((DslrNikonCamera) camera).retrieveLatestPicture();
        } else {
            setRetrievalStatus("Latest-photo metadata scan is only available for Nikon DSLR sessions.");
        }
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
        if (bitmap == null) { setCoachMarkdown("Capture completed, but no JPEG preview was returned."); return; }
        lastBitmap = bitmap; lastShot.setImageBitmap(bitmap); lastMetrics = ImageMetrics.analyze(bitmap);
        metricStatus.setText(lastMetrics.shortSummary());
        if (camera != null) refreshSettings(); else lastCameraData = "Image source: " + source + ".";
        setRetrievalStatus("Loaded " + source + " for analysis.");
        showPage(PAGE_COACH);
        saveShot(bitmap, source); analyzeLastShot();
    }

    private void analyzeLastShot() {
        if (lastBitmap == null) { toast("Take or import a photo first"); return; }
        if (lastMetrics == null) lastMetrics = ImageMetrics.analyze(lastBitmap);
        String measured = ImageMetrics.deterministicAdvice(lastMetrics);
        setCoachMarkdown("## Measured checks\n" + measured + "\n\nAI is analyzing the shot...");
        aiCoach.analyze(lastBitmap, lastCameraData, lastMetrics.asPromptText(), preferenceMemory(), result -> {
            setCoachMarkdown(result + "\n\n## Measured checks\n" + measured);
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
            catch (Exception e) { setCoachMarkdown("Could not read image: " + e.getMessage()); }
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
    @Override protected void onStop() { super.onStop(); requestingLive=false; liveStartPending=false; if (ptp != null) ptp.setCameraListener(null); }
    @Override protected void onDestroy() { super.onDestroy(); if (isFinishing() && ptp != null) ptp.shutdown(); if (aiCoach != null) aiCoach.close(); }

    @Override public void onCameraStarted(Camera c) { camera=c; c.setCapturedPictureSampleSize(1); runOnUiThread(() -> { liveStartPending=false; cameraStatus.setText("Camera: "+c.getDeviceName()+" connected"); setLiveStatus("Live view stopped. Tap Live to start."); refreshSettings(); }); }
    @Override public void onCameraStopped(Camera c) { camera=null; runOnUiThread(() -> { requestingLive=false; liveStartPending=false; cameraStatus.setText("Camera: disconnected"); setLiveStatus("Camera disconnected."); setRetrievalStatus("Camera disconnected."); }); }
    @Override public void onNoCameraFound() { runOnUiThread(() -> { cameraStatus.setText("Camera: no Nikon found - connect D5200 by USB OTG, then Reconnect"); setLiveStatus("No camera connected."); }); }
    @Override public void onError(String message) { runOnUiThread(() -> { cameraStatus.setText("Camera error: "+message); setLiveStatus("Camera error: " + message); setRetrievalStatus("Camera error: " + message); }); }
    @Override public void onPropertyChanged(int property,int value) { runOnUiThread(this::refreshSettings); }
    @Override public void onPropertyStateChanged(int property,boolean enabled) {}
    @Override public void onPropertyDescChanged(int property,int[] values) { runOnUiThread(this::refreshSettings); }
    @Override public void onLiveViewStarted() { requestingLive=true; liveStartPending=false; runOnUiThread(() -> { liveButton.setText("Stop"); setLiveStatus("Live view running. Tap preview to focus if supported."); if (camera != null) camera.getLiveViewPicture(null); }); }
    @Override public void onLiveViewData(LiveViewData data) { runOnUiThread(() -> { if (data != null && data.bitmap != null) { liveView.setImageBitmap(data.bitmap); setLiveStatus("Live view running."); } if (requestingLive && camera != null && camera.isLiveViewOpen()) main.postDelayed(() -> { if (requestingLive && camera != null && camera.isLiveViewOpen()) camera.getLiveViewPicture(data); },100); }); }
    @Override public void onLiveViewStopped() { requestingLive=false; liveStartPending=false; runOnUiThread(() -> { liveButton.setText("Live"); setLiveStatus("Live view stopped."); }); }
    @Override public void onCapturedPictureReceived(int objectHandle,String filename,Bitmap thumbnail,Bitmap bitmap) { Bitmap chosen=bitmap!=null?bitmap:thumbnail; runOnUiThread(() -> handleShot(chosen,filename==null?"D5200":filename)); }
    @Override public void onBulbStarted() {}
    @Override public void onBulbExposureTime(int seconds) {}
    @Override public void onBulbStopped() {}
    @Override public void onFocusStarted() { runOnUiThread(() -> cameraStatus.setText("Camera: focusing...")); }
    @Override public void onFocusEnded(boolean focused) { runOnUiThread(() -> cameraStatus.setText("Camera: "+(focused?"focus locked":"focus ended"))); }
    @Override public void onFocusPointsChanged() {}
    @Override public void onObjectAdded(int handle,int format) {
        runOnUiThread(() -> {
            if (NikonPayloads.shouldAutoRetrieveObject(format)) {
                setRetrievalStatus("Camera image event 0x" + Integer.toHexString(handle) + ". Downloading...");
                setCoachMarkdown("Photo captured. Downloading preview from camera...");
            } else {
                setRetrievalStatus("Camera object event 0x" + Integer.toHexString(handle) + " ignored; format 0x" + Integer.toHexString(format & 0xFFFF) + " is not an image.");
            }
        });
    }

    private void setCoachMarkdown(String markdown) { critique.setText(MarkdownText.render(markdown)); }
    private void setRetrievalStatus(String value) { if (retrievalStatus != null) retrievalStatus.setText(value); }
    private void setLiveStatus(String value) { if (liveStatus != null) liveStatus.setText(value); }
    private ScrollView pageScroll() { ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG); return scroll; }
    private LinearLayout pageRoot(ScrollView scroll) { LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(0,0,0,dp(96)); scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)); return root; }
    private void showPage(int page) {
        currentPage = page;
        if (connectPage != null) connectPage.setVisibility(page == PAGE_CONNECT ? View.VISIBLE : View.GONE);
        if (cameraPage != null) cameraPage.setVisibility(page == PAGE_CAMERA ? View.VISIBLE : View.GONE);
        if (coachPage != null) coachPage.setVisibility(page == PAGE_COACH ? View.VISIBLE : View.GONE);
        styleTab(connectTab, page == PAGE_CONNECT);
        styleTab(cameraTab, page == PAGE_CAMERA);
        styleTab(coachTab, page == PAGE_COACH);
    }
    private Button tabButton(String label,int page) { Button b=button(label, v -> showPage(page)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1f); lp.setMargins(0,0,dp(6),dp(8)); b.setLayoutParams(lp); return b; }
    private void styleTab(Button button, boolean selected) { if (button == null) return; button.setTextColor(selected ? SURFACE : INK); button.setBackground(round(selected ? ACCENT : 0xFFE8E1D4, 999, selected ? 0 : 0x1F000000)); }
    private TextView text(String value,int sp,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(INK); t.setPadding(0,dp(6),0,dp(6)); if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private TextView sectionTitle(String value) { TextView t=text(value.toUpperCase(Locale.US),12,true); t.setTextColor(ACCENT); t.setLetterSpacing(0.12f); t.setPadding(0,0,0,dp(8)); return t; }
    private TextView pill(String value) { TextView t=text(value,14,true); t.setTextColor(INK); t.setBackground(round(0xFFE9F2EF, 999, 0)); t.setPadding(dp(12),dp(7),dp(12),dp(7)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(4),0,dp(4)); t.setLayoutParams(lp); return t; }
    private LinearLayout card() { LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setBackground(round(SURFACE, 22, 0x1A000000)); c.setPadding(dp(16),dp(16),dp(16),dp(16)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); lp.setMargins(0,dp(14),0,0); c.setLayoutParams(lp); return c; }
    private void stylePreview(ImageView view,int color) { view.setBackground(round(color, 18, 0x26000000)); view.setPadding(dp(6),dp(6),dp(6),dp(6)); }
    private GradientDrawable round(int color,int radiusDp,int strokeColor) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radiusDp)); if (strokeColor != 0) d.setStroke(dp(1),strokeColor); return d; }
    private Button button(String label,View.OnClickListener listener) { Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(13); b.setTextColor(INK); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(round(0xFFE8E1D4, 999, 0x1F000000)); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(44)); b.setPadding(dp(10),0,dp(10),0); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(44)); lp.setMargins(0,0,dp(5),0); b.setLayoutParams(lp); b.setOnClickListener(listener); return b; }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private HorizontalScrollView scroller(LinearLayout content) { HorizontalScrollView s=new HorizontalScrollView(this); s.setHorizontalScrollBarEnabled(false); s.addView(content); return s; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void toast(String t) { Toast.makeText(this,t,Toast.LENGTH_SHORT).show(); }
}
