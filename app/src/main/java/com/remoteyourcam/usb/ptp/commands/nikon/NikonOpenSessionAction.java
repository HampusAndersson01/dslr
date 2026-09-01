package com.remoteyourcam.usb.ptp.commands.nikon;

import com.hampus.dslraicoach.camera.NikonPayloads;
import com.remoteyourcam.usb.ptp.NikonCamera;
import com.remoteyourcam.usb.ptp.PtpAction;
import com.remoteyourcam.usb.ptp.PtpCamera.IO;
import com.remoteyourcam.usb.ptp.PtpConstants;
import com.remoteyourcam.usb.ptp.PtpConstants.Datatype;
import com.remoteyourcam.usb.ptp.PtpConstants.Operation;
import com.remoteyourcam.usb.ptp.PtpConstants.Property;
import com.remoteyourcam.usb.ptp.commands.OpenSessionCommand;
import com.remoteyourcam.usb.ptp.commands.SetDevicePropValueCommand;

public class NikonOpenSessionAction implements PtpAction {

    private final NikonCamera camera;

    public NikonOpenSessionAction(NikonCamera camera) {
        this.camera = camera;
    }

    @Override
    public void exec(IO io) {
        OpenSessionCommand openSession = new OpenSessionCommand(camera);
        io.handleCommand(openSession);
        if (!NikonPayloads.isUsableOpenSessionResponse(openSession.getResponseCode())) {
            camera.onPtpError(String.format(
                    "Couldn't open session! Open session command failed with error code \"%s\"",
                    PtpConstants.responseToString(openSession.getResponseCode())));
            return;
        }

        if (!camera.hasSupportForOperation(Operation.NikonGetVendorPropCodes)) {
            camera.onSessionOpened();
            return;
        }

        NikonGetVendorPropCodesCommand getPropCodes = new NikonGetVendorPropCodesCommand(camera);
        io.handleCommand(getPropCodes);
        SetDevicePropValueCommand media = new SetDevicePropValueCommand(camera, Property.NikonRecordingMedia, 1,
                Datatype.uint8);
        io.handleCommand(media);
        if (getPropCodes.getResponseCode() == PtpConstants.Response.Ok
                && NikonPayloads.canContinueAfterNikonRecordingMediaResponse(media.getResponseCode())) {
            camera.setVendorPropCodes(getPropCodes.getPropertyCodes());
            camera.onSessionOpened();
        } else {
            camera.onPtpError(String.format(
                    "Couldn't read device property codes! Vendor props response \"%s\", recording media response \"%s\"",
                    PtpConstants.responseToString(getPropCodes.getResponseCode()),
                    PtpConstants.responseToString(media.getResponseCode())));
        }
    }

    @Override
    public void reset() {
    }
}
