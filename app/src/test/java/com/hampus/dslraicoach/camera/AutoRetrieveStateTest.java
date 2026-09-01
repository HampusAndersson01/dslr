package com.hampus.dslraicoach.camera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AutoRetrieveStateTest {
    @Test
    public void captureCompleteRequestsFallbackWhenNoImageObjectArrived() {
        AutoRetrieveState state = new AutoRetrieveState();

        assertTrue(state.shouldFetchLatestOnCaptureComplete());
    }

    @Test
    public void captureCompleteDoesNotDuplicateAfterImageObjectArrived() {
        AutoRetrieveState state = new AutoRetrieveState();
        state.onImageObjectRetrieved();

        assertFalse(state.shouldFetchLatestOnCaptureComplete());
    }

    @Test
    public void newCaptureAllowsFallbackAgain() {
        AutoRetrieveState state = new AutoRetrieveState();
        state.onImageObjectRetrieved();
        state.onCaptureRequested();

        assertTrue(state.shouldFetchLatestOnCaptureComplete());
    }
}
