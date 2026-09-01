package com.remoteyourcam.usb.ptp.commands.nikon;

import android.util.Log;

import com.hampus.dslraicoach.camera.NikonPayloads;
import com.remoteyourcam.usb.AppConfig;
import com.remoteyourcam.usb.ptp.NikonCamera;
import com.remoteyourcam.usb.ptp.PtpCamera.IO;
import com.remoteyourcam.usb.ptp.PtpConstants;
import com.remoteyourcam.usb.ptp.PtpConstants.Event;
import com.remoteyourcam.usb.ptp.PtpConstants.Operation;

import java.nio.ByteBuffer;

public class NikonEventCheckCommand extends NikonCommand {
    private static final String TAG = NikonEventCheckCommand.class.getSimpleName();

    public NikonEventCheckCommand(NikonCamera camera) {
        super(camera);
    }

    @Override
    public void exec(IO io) {
        io.handleCommand(this);
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        encodeCommand(b, Operation.NikonGetEvent);
    }

    @Override
    protected void decodeData(ByteBuffer b, int length) {
        int count = b.getShort() & 0xFFFF;

        while (count > 0) {
            --count;

            int eventCode = b.getShort() & 0xFFFF;
            int eventParam = b.getInt();

            if (AppConfig.LOG) {
                Log.i(TAG, String.format("event %s value %s(%04x)",
                        PtpConstants.eventToString(eventCode),
                        PtpConstants.propertyToString(eventParam),
                        eventParam));
            }

            if (NikonPayloads.isObjectAddedEvent(eventCode)) {
                camera.onEventObjectAdded(eventParam);
            } else if (eventCode == Event.DevicePropChanged) {
                camera.onEventDevicePropChanged(eventParam);
            } else if (NikonPayloads.isCaptureCompleteEvent(eventCode)) {
                camera.onEventCaptureComplete();
            }
        }
    }
}
