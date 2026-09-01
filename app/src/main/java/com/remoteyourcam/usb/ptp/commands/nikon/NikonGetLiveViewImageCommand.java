package com.remoteyourcam.usb.ptp.commands.nikon;

import android.graphics.BitmapFactory;
import android.util.Log;

import com.hampus.dslraicoach.camera.NikonPayloads;
import com.remoteyourcam.usb.AppConfig;
import com.remoteyourcam.usb.ptp.NikonCamera;
import com.remoteyourcam.usb.ptp.PacketUtil;
import com.remoteyourcam.usb.ptp.PtpCamera.IO;
import com.remoteyourcam.usb.ptp.PtpConstants.Operation;
import com.remoteyourcam.usb.ptp.PtpConstants.Response;
import com.remoteyourcam.usb.ptp.model.LiveViewData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NikonGetLiveViewImageCommand extends NikonCommand {
    private static final String TAG = NikonGetLiveViewImageCommand.class.getSimpleName();
    private static final byte[] TEMP_STORAGE = new byte[0x4000];

    private final BitmapFactory.Options options;
    private final LiveViewData data;

    public NikonGetLiveViewImageCommand(NikonCamera camera, LiveViewData data) {
        super(camera);
        this.data = data != null ? data : new LiveViewData();
        options = new BitmapFactory.Options();
        options.inBitmap = this.data.bitmap;
        options.inSampleSize = 1;
        options.inTempStorage = TEMP_STORAGE;
        this.data.bitmap = null;
    }

    @Override
    public void exec(IO io) {
        if (!camera.isLiveViewOpen()) {
            return;
        }
        io.handleCommand(this);
        if (responseCode == Response.DeviceBusy) {
            camera.onDeviceBusy(this, true);
            return;
        }
        if (NikonPayloads.isLiveViewUnavailableResponse(responseCode)) {
            camera.onLiveViewStopped();
            return;
        }
        data.hasHistogram = false;
        camera.onLiveViewReceived(responseCode == Response.Ok && data.bitmap != null ? data : null);
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        encodeCommand(b, Operation.NikonGetLiveViewImage);
    }

    @Override
    protected void decodeData(ByteBuffer b, int length) {
        if (length <= 128) {
            data.bitmap = null;
            return;
        }

        data.hasAfFrame = false;

        int start = b.position();
        readAfFrame(b, start);

        int pictureOffset = NikonPayloads.findJpegStart(b.array(), start, length);
        if (pictureOffset < 0 || pictureOffset >= start + length) {
            data.bitmap = null;
            return;
        }

        try {
            data.bitmap = BitmapFactory.decodeByteArray(b.array(), pictureOffset, start + length - pictureOffset, options);
        } catch (RuntimeException e) {
            Log.e(TAG, "decoding failed " + e);
            if (AppConfig.LOG) {
                PacketUtil.logHexdump(TAG, b.array(), start, Math.min(length, 512));
            }
            data.bitmap = null;
        }
    }

    private void readAfFrame(ByteBuffer b, int start) {
        b.order(ByteOrder.BIG_ENDIAN);
        try {
            data.hasAfFrame = true;

            int jpegImageWidth = b.getShort() & 0xFFFF;
            int jpegImageHeight = b.getShort() & 0xFFFF;
            int wholeWidth = b.getShort() & 0xFFFF;
            int wholeHeight = b.getShort() & 0xFFFF;

            if (wholeWidth <= 0 || wholeHeight <= 0) {
                data.hasAfFrame = false;
                return;
            }

            float multX = jpegImageWidth / (float) wholeWidth;
            float multY = jpegImageHeight / (float) wholeHeight;

            b.position(start + 16);
            data.nikonWholeWidth = wholeWidth;
            data.nikonWholeHeight = wholeHeight;
            data.nikonAfFrameWidth = (int) ((b.getShort() & 0xFFFF) * multX);
            data.nikonAfFrameHeight = (int) ((b.getShort() & 0xFFFF) * multY);
            data.nikonAfFrameCenterX = (int) ((b.getShort() & 0xFFFF) * multX);
            data.nikonAfFrameCenterY = (int) ((b.getShort() & 0xFFFF) * multY);
        } catch (RuntimeException e) {
            data.hasAfFrame = false;
        } finally {
            b.order(ByteOrder.LITTLE_ENDIAN);
            b.position(start);
        }
    }
}
