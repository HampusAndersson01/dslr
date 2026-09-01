package com.remoteyourcam.usb.ptp.commands.nikon;

import com.hampus.dslraicoach.camera.NikonPayloads;
import com.remoteyourcam.usb.ptp.NikonCamera;
import com.remoteyourcam.usb.ptp.PtpAction;
import com.remoteyourcam.usb.ptp.PtpCamera.IO;
import com.remoteyourcam.usb.ptp.PtpConstants.Operation;
import com.remoteyourcam.usb.ptp.PtpConstants.Response;
import com.remoteyourcam.usb.ptp.commands.SimpleCommand;

public class NikonStartLiveViewAction implements PtpAction {
    private final NikonCamera camera;

    public NikonStartLiveViewAction(NikonCamera camera) {
        this.camera = camera;
    }

    @Override
    public void exec(IO io) {
        if (camera.hasSupportForOperation(Operation.NikonChangeCameraMode)) {
            SimpleCommand controlMode = new SimpleCommand(camera, Operation.NikonChangeCameraMode, 1);
            io.handleCommand(controlMode);
            if (!NikonPayloads.canContinueAfterNikonControlModeResponse(controlMode.getResponseCode())) {
                camera.onLiveViewStopped();
                return;
            }
        }

        SimpleCommand startLiveView = new SimpleCommand(camera, Operation.NikonStartLiveView);
        io.handleCommand(startLiveView);

        if (startLiveView.getResponseCode() != Response.Ok) {
            camera.onLiveViewStopped();
            return;
        }

        SimpleCommand deviceReady = new SimpleCommand(camera, Operation.NikonDeviceReady);
        for (int i = 0; i < 20; ++i) {
            sleep(300);
            deviceReady.reset();
            io.handleCommand(deviceReady);
            if (deviceReady.getResponseCode() == Response.Ok) {
                camera.onLiveViewStarted();
                camera.getLiveViewPicture(null);
                return;
            }
            if (deviceReady.getResponseCode() != Response.DeviceBusy) {
                camera.onLiveViewStopped();
                return;
            }
        }

        camera.onLiveViewStopped();
    }

    @Override
    public void reset() {
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
