package com.hampus.dslraicoach.camera;

import com.remoteyourcam.usb.ptp.PtpConstants;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NikonPayloadsTest {

    @Test
    public void findsEmbeddedJpegStartAfterLiveViewHeader() {
        byte[] payload = new byte[512];
        payload[384] = (byte) 0xFF;
        payload[385] = (byte) 0xD8;
        payload[386] = (byte) 0xFF;
        payload[387] = (byte) 0xE0;

        assertEquals(384, NikonPayloads.findJpegStart(payload, 0, payload.length));
    }

    @Test
    public void reportsMissingJpegWhenMarkerAbsent() {
        byte[] payload = new byte[128];

        assertEquals(-1, NikonPayloads.findJpegStart(payload, 0, payload.length));
    }

    @Test
    public void autoRetrievesOnlyJpegObjects() {
        assertTrue(NikonPayloads.shouldAutoRetrieveObject(PtpConstants.ObjectFormat.EXIF_JPEG));
        assertTrue(NikonPayloads.shouldAutoRetrieveObject(PtpConstants.ObjectFormat.JFIF));
        assertTrue(NikonPayloads.shouldAutoRetrieveObject(PtpConstants.ObjectFormat.UnknownImageObject));
        assertTrue(NikonPayloads.shouldAutoRetrieveObject(PtpConstants.ObjectFormat.UnknownNonImageObject));
        assertFalse(NikonPayloads.shouldAutoRetrieveObject(PtpConstants.ObjectFormat.Association));
    }

    @Test
    public void handlesSignedNikonSdramObjectAddedEvent() {
        int signedEventCode = (short) PtpConstants.Event.NikonObjectAddedInSdram;

        assertTrue(NikonPayloads.isObjectAddedEvent(signedEventCode));
    }

    @Test
    public void handlesSignedNikonSdramCaptureCompleteEvent() {
        int signedEventCode = (short) PtpConstants.Event.NikonCaptureCompleteRecInSdram;

        assertTrue(NikonPayloads.isCaptureCompleteEvent(signedEventCode));
    }

    @Test
    public void treatsAlreadyOpenSessionAsUsableForNikonSetup() {
        assertTrue(NikonPayloads.isUsableOpenSessionResponse(PtpConstants.Response.Ok));
        assertTrue(NikonPayloads.isUsableOpenSessionResponse(PtpConstants.Response.SessionAlreadyOpen));
        assertFalse(NikonPayloads.isUsableOpenSessionResponse(PtpConstants.Response.DeviceBusy));
    }

    @Test
    public void prefersNikonMediaCaptureWhenSupported() {
        Set<Integer> operations = new HashSet<>();
        operations.add(PtpConstants.Operation.InitiateCapture);
        operations.add(PtpConstants.Operation.NikonInitiateCaptureRecInMedia);
        operations.add(PtpConstants.Operation.NikonInitiateCaptureRecInSdram);

        assertEquals(PtpConstants.Operation.NikonInitiateCaptureRecInMedia,
                NikonPayloads.preferredNikonCaptureOperation(operations));
    }

    @Test
    public void fallsBackToGenericCaptureWhenNoNikonCaptureIsSupported() {
        Set<Integer> operations = new HashSet<>();
        operations.add(PtpConstants.Operation.InitiateCapture);

        assertEquals(PtpConstants.Operation.InitiateCapture,
                NikonPayloads.preferredNikonCaptureOperation(operations));
    }

    @Test
    public void identifiesNotLiveViewAsUnavailableLiveViewResponse() {
        assertTrue(NikonPayloads.isLiveViewUnavailableResponse(PtpConstants.Response.NotLiveView));
        assertFalse(NikonPayloads.isLiveViewUnavailableResponse(PtpConstants.Response.Ok));
    }

    @Test
    public void acceptsNikonControlModeResponsesThatAllowContinuing() {
        assertTrue(NikonPayloads.canContinueAfterNikonControlModeResponse(PtpConstants.Response.Ok));
        assertTrue(NikonPayloads.canContinueAfterNikonControlModeResponse(PtpConstants.Response.ChangeCameraModeFailed));
        assertFalse(NikonPayloads.canContinueAfterNikonControlModeResponse(PtpConstants.Response.InvalidStatus));
    }

    @Test
    public void acceptsBusyRecordingMediaResponseDuringNikonSessionSetup() {
        assertTrue(NikonPayloads.canContinueAfterNikonRecordingMediaResponse(PtpConstants.Response.Ok));
        assertTrue(NikonPayloads.canContinueAfterNikonRecordingMediaResponse(PtpConstants.Response.DeviceBusy));
        assertFalse(NikonPayloads.canContinueAfterNikonRecordingMediaResponse(PtpConstants.Response.InvalidStatus));
    }
}
