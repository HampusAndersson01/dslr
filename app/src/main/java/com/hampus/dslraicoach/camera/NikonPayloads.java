package com.hampus.dslraicoach.camera;

import com.remoteyourcam.usb.ptp.PtpConstants;

import java.util.Set;

public final class NikonPayloads {
    private NikonPayloads() {}

    public static int findJpegStart(byte[] data, int start, int length) {
        int end = Math.min(data.length - 1, start + length - 1);
        for (int i = Math.max(0, start); i < end; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD8) {
                return i;
            }
        }
        return -1;
    }

    public static boolean shouldAutoRetrieveObject(int objectFormat) {
        int normalized = objectFormat & 0xFFFF;
        return normalized == PtpConstants.ObjectFormat.EXIF_JPEG
                || normalized == PtpConstants.ObjectFormat.JFIF
                || normalized == PtpConstants.ObjectFormat.UnknownImageObject
                || normalized == PtpConstants.ObjectFormat.UnknownNonImageObject;
    }

    public static boolean isObjectAddedEvent(int eventCode) {
        int normalized = eventCode & 0xFFFF;
        return normalized == PtpConstants.Event.ObjectAdded
                || normalized == PtpConstants.Event.NikonObjectAddedInSdram;
    }

    public static boolean isCaptureCompleteEvent(int eventCode) {
        int normalized = eventCode & 0xFFFF;
        return normalized == PtpConstants.Event.CaptureComplete
                || normalized == PtpConstants.Event.NikonCaptureCompleteRecInSdram;
    }

    public static boolean isUsableOpenSessionResponse(int responseCode) {
        return responseCode == PtpConstants.Response.Ok
                || responseCode == PtpConstants.Response.SessionAlreadyOpen;
    }

    public static int preferredNikonCaptureOperation(Set<Integer> operations) {
        if (operations.contains(PtpConstants.Operation.NikonInitiateCaptureRecInMedia)) {
            return PtpConstants.Operation.NikonInitiateCaptureRecInMedia;
        }
        if (operations.contains(PtpConstants.Operation.NikonInitiateCaptureRecInSdram)) {
            return PtpConstants.Operation.NikonInitiateCaptureRecInSdram;
        }
        return PtpConstants.Operation.InitiateCapture;
    }

    public static boolean isLiveViewUnavailableResponse(int responseCode) {
        return responseCode == PtpConstants.Response.NotLiveView;
    }

    public static boolean canContinueAfterNikonControlModeResponse(int responseCode) {
        return responseCode == PtpConstants.Response.Ok
                || responseCode == PtpConstants.Response.ChangeCameraModeFailed;
    }

    public static boolean canContinueAfterNikonRecordingMediaResponse(int responseCode) {
        return responseCode == PtpConstants.Response.Ok
                || responseCode == PtpConstants.Response.DeviceBusy;
    }
}
