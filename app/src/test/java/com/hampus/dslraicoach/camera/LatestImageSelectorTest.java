package com.hampus.dslraicoach.camera;

import com.remoteyourcam.usb.ptp.model.ObjectInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LatestImageSelectorTest {
    @Test
    public void selectsHighestObjectHandleAsLatest() {
        LatestImageSelector.Selection selection = LatestImageSelector.selectLatest(new int[] { 12, 44, 19 });

        assertTrue(selection.hasHandle());
        assertEquals(44, selection.handle);
    }

    @Test
    public void reportsNoHandleForEmptyStorage() {
        LatestImageSelector.Selection selection = LatestImageSelector.selectLatest(new int[0]);

        assertFalse(selection.hasHandle());
    }

    @Test
    public void selectsNewestCaptureDateBeforeHighestObjectHandle() {
        ObjectInfo olderHighHandle = image("DSC_2835.JPG", "20260901T151000");
        ObjectInfo newerLowHandle = image("DSC_2836.JPG", "20260901T151500");

        LatestImageSelector.Selection selection = LatestImageSelector.selectLatest(
                new LatestImageSelector.Candidate(44, olderHighHandle),
                new LatestImageSelector.Candidate(12, newerLowHandle));

        assertTrue(selection.hasHandle());
        assertEquals(12, selection.handle);
    }

    private ObjectInfo image(String filename, String captureDate) {
        ObjectInfo info = new ObjectInfo();
        info.filename = filename;
        info.captureDate = captureDate;
        return info;
    }
}
