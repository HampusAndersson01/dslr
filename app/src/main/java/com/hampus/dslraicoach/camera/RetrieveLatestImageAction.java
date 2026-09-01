package com.hampus.dslraicoach.camera;

import android.util.Log;

import com.remoteyourcam.usb.ptp.Camera.StorageInfoListener;
import com.remoteyourcam.usb.ptp.PtpAction;
import com.remoteyourcam.usb.ptp.PtpCamera.IO;
import com.remoteyourcam.usb.ptp.PtpConstants.Response;
import com.remoteyourcam.usb.ptp.commands.GetObjectHandlesCommand;
import com.remoteyourcam.usb.ptp.commands.GetObjectInfoCommand;
import com.remoteyourcam.usb.ptp.commands.RetrievePictureAction;
import com.remoteyourcam.usb.ptp.model.ObjectInfo;

import java.util.ArrayList;
import java.util.List;

public class RetrieveLatestImageAction implements PtpAction {
    private static final String TAG = RetrieveLatestImageAction.class.getSimpleName();
    private static final int ALL_STORAGES = 0xFFFFFFFF;
    private static final int ALL_FORMATS = 0;
    private static final int FULL_SIZE_SAMPLE = 1;

    private final DslrNikonCamera camera;

    public RetrieveLatestImageAction(DslrNikonCamera camera) {
        this.camera = camera;
    }

    @Override
    public void exec(IO io) {
        GetObjectHandlesCommand getHandles = new GetObjectHandlesCommand(
                camera,
                new NoOpStorageInfoListener(),
                ALL_STORAGES,
                ALL_FORMATS);
        io.handleCommand(getHandles);
        if (getHandles.getResponseCode() != Response.Ok) {
            return;
        }

        int[] handles = getHandles.getObjectHandles();
        List<LatestImageSelector.Candidate> candidates = new ArrayList<>();
        for (int handle : handles) {
            GetObjectInfoCommand getInfo = new GetObjectInfoCommand(camera, handle);
            io.handleCommand(getInfo);
            ObjectInfo info = getInfo.getObjectInfo();
            if (getInfo.getResponseCode() == Response.Ok
                    && info != null
                    && NikonPayloads.shouldAutoRetrieveObject(info.objectFormat)) {
                candidates.add(new LatestImageSelector.Candidate(handle, info));
            }
        }

        LatestImageSelector.Selection selection = LatestImageSelector.selectLatest(
                candidates.toArray(new LatestImageSelector.Candidate[0]));
        if (!selection.hasHandle()) {
            selection = LatestImageSelector.selectLatest(handles);
        }
        if (selection.hasHandle()) {
            Log.i(TAG, "Selected latest image handle=0x" + Integer.toHexString(selection.handle)
                    + " filename=" + selection.filename
                    + " captureDate=" + selection.captureDate
                    + " modificationDate=" + selection.modificationDate
                    + " scannedCandidates=" + candidates.size()
                    + " handles=" + handles.length);
            new RetrievePictureAction(camera, selection.handle, FULL_SIZE_SAMPLE).exec(io);
        }
    }

    @Override
    public void reset() {
    }

    private static final class NoOpStorageInfoListener implements StorageInfoListener {
        @Override public void onStorageFound(int handle, String label) {}
        @Override public void onAllStoragesFound() {}
        @Override public void onImageHandlesRetrieved(int[] handles) {}
    }
}
