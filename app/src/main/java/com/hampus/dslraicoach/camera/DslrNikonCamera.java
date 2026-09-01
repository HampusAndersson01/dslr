package com.hampus.dslraicoach.camera;

import com.remoteyourcam.usb.ptp.Camera.CameraListener;
import com.remoteyourcam.usb.ptp.Camera.WorkerListener;
import com.remoteyourcam.usb.ptp.NikonCamera;
import com.remoteyourcam.usb.ptp.PtpConstants;
import com.remoteyourcam.usb.ptp.PtpUsbConnection;
import com.remoteyourcam.usb.ptp.commands.InitiateCaptureCommand;
import com.remoteyourcam.usb.ptp.commands.SimpleCommand;
import com.remoteyourcam.usb.ptp.commands.nikon.NikonStopLiveViewAction;

import java.util.HashSet;
import java.util.Set;

public class DslrNikonCamera extends NikonCamera {
    private static final int NIKON_CAPTURE_NO_AF = 0xFFFFFFFF;
    private static final int NIKON_CAPTURE_TARGET_SDRAM = 1;
    private final AutoRetrieveState autoRetrieveState = new AutoRetrieveState();

    public DslrNikonCamera(PtpUsbConnection connection, CameraListener listener, WorkerListener workerListener) {
        super(connection, listener, workerListener);
    }

    @Override
    public void capture() {
        autoRetrieveState.onCaptureRequested();
        if (liveViewOpen) {
            queue.add(new NikonStopLiveViewAction(this, false));
        }

        Set<Integer> captureOperations = new HashSet<>();
        addIfSupported(captureOperations, PtpConstants.Operation.NikonInitiateCaptureRecInSdram);
        addIfSupported(captureOperations, PtpConstants.Operation.NikonInitiateCaptureRecInMedia);
        addIfSupported(captureOperations, PtpConstants.Operation.InitiateCapture);

        int operation = NikonPayloads.preferredNikonCaptureOperation(captureOperations);
        if (operation == PtpConstants.Operation.InitiateCapture) {
            queue.add(new InitiateCaptureCommand(this));
        } else if (operation == PtpConstants.Operation.NikonInitiateCaptureRecInMedia) {
            queue.add(new SimpleCommand(this, operation, NIKON_CAPTURE_NO_AF, NIKON_CAPTURE_TARGET_SDRAM));
        } else {
            queue.add(new SimpleCommand(this, operation, NIKON_CAPTURE_NO_AF));
        }
    }

    @Override
    public void onEventObjectAdded(int handle, int format) {
        super.onEventObjectAdded(handle, format);
        if (NikonPayloads.shouldAutoRetrieveObject(format)) {
            autoRetrieveState.onImageObjectRetrieved();
            retrievePicture(handle);
        }
    }

    @Override
    public void onEventCaptureComplete() {
        if (autoRetrieveState.shouldFetchLatestOnCaptureComplete()) {
            retrieveLatestPicture();
        }
    }

    public void retrieveLatestPicture() {
        queue.add(new RetrieveLatestImageAction(this));
    }

    private void addIfSupported(Set<Integer> operations, int operation) {
        if (hasSupportForOperation(operation)) {
            operations.add(operation);
        }
    }
}
