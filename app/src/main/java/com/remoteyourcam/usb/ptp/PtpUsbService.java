package com.remoteyourcam.usb.ptp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.hampus.dslraicoach.camera.DslrNikonCamera;
import com.remoteyourcam.usb.ptp.Camera.CameraListener;

import java.util.Map;

public class PtpUsbService implements PtpService {
    private static final String ACTION_USB_PERMISSION = "com.hampus.dslraicoach.USB_PERMISSION";
    private final UsbManager usbManager;
    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PtpCamera camera;
    private CameraListener listener;
    private boolean receiverRegistered;

    private final Camera.WorkerListener workerListener = new Camera.WorkerListener() {
        @Override public void onWorkerStarted() { }
        @Override public void onWorkerEnded() { }
    };

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
            UsbDevice device;
            if (Build.VERSION.SDK_INT >= 33) device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            else device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            unregisterPermissionReceiver();
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) connect(device);
            else if (listener != null) listener.onError("USB permission was not granted");
        }
    };

    public PtpUsbService(Context context) {
        appContext = context.getApplicationContext();
        usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
    }

    @Override public void setCameraListener(CameraListener listener) {
        this.listener = listener;
        if (camera != null) camera.setListener(listener);
    }

    @Override public void initialize(Context context, Intent intent) {
        handler.removeCallbacksAndMessages(null);
        if (camera != null && camera.getState() == PtpCamera.State.Active) {
            if (listener != null) listener.onCameraStarted(camera);
            return;
        }
        UsbDevice device = null;
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= 33) device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice.class);
            else device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
        if (device == null) device = lookupNikonDevice();
        if (device == null) {
            if (listener != null) listener.onNoCameraFound();
            return;
        }
        if (usbManager.hasPermission(device)) connect(device);
        else {
            registerPermissionReceiver();
            Intent permissionIntent = new Intent(ACTION_USB_PERMISSION).setPackage(appContext.getPackageName());
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            usbManager.requestPermission(device, PendingIntent.getBroadcast(appContext, 0, permissionIntent, flags));
        }
    }

    private void registerPermissionReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else appContext.registerReceiver(permissionReceiver, filter);
        receiverRegistered = true;
    }

    private void unregisterPermissionReceiver() {
        if (!receiverRegistered) return;
        try { appContext.unregisterReceiver(permissionReceiver); } catch (IllegalArgumentException ignored) { }
        receiverRegistered = false;
    }

    private UsbDevice lookupNikonDevice() {
        Map<String, UsbDevice> devices = usbManager.getDeviceList();
        for (UsbDevice device : devices.values()) if (device.getVendorId() == PtpConstants.NikonVendorId) return device;
        return null;
    }

    private boolean connect(UsbDevice device) {
        if (camera != null) {
            camera.shutdownHard();
            camera = null;
        }
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            UsbEndpoint bulkIn = null;
            UsbEndpoint bulkOut = null;
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint endpoint = intf.getEndpoint(e);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) bulkIn = endpoint;
                else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) bulkOut = endpoint;
            }
            if (bulkIn == null || bulkOut == null) continue;
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection == null || !connection.claimInterface(intf, true)) {
                if (connection != null) connection.close();
                continue;
            }
            PtpUsbConnection ptpConnection = new PtpUsbConnection(connection, bulkIn, bulkOut, device.getVendorId(), device.getProductId());
            camera = new DslrNikonCamera(ptpConnection, listener, workerListener);
            return true;
        }
        if (listener != null) listener.onError("Nikon found, but no compatible PTP interface could be claimed");
        return false;
    }

    @Override public void shutdown() {
        unregisterPermissionReceiver();
        if (camera != null) {
            camera.shutdown();
            camera = null;
        }
    }

    @Override public void lazyShutdown() { handler.postDelayed(this::shutdown, 4000); }
}
