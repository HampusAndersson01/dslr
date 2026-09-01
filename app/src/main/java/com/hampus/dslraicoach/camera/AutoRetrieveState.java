package com.hampus.dslraicoach.camera;

final class AutoRetrieveState {
    private boolean imageObjectRetrievedSinceCapture;

    void onCaptureRequested() {
        imageObjectRetrievedSinceCapture = false;
    }

    void onImageObjectRetrieved() {
        imageObjectRetrievedSinceCapture = true;
    }

    boolean shouldFetchLatestOnCaptureComplete() {
        if (imageObjectRetrievedSinceCapture) {
            imageObjectRetrievedSinceCapture = false;
            return false;
        }
        return true;
    }
}
