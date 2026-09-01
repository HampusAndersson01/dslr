package com.hampus.dslraicoach.camera;

import com.remoteyourcam.usb.ptp.model.ObjectInfo;

public final class LatestImageSelector {
    private LatestImageSelector() {}

    public static Selection selectLatest(int[] handles) {
        if (handles == null || handles.length == 0) {
            return new Selection(0);
        }
        int latest = handles[0];
        for (int handle : handles) {
            if (Integer.compareUnsigned(handle, latest) > 0) {
                latest = handle;
            }
        }
        return new Selection(latest);
    }

    public static Selection selectLatest(Candidate... candidates) {
        if (candidates == null || candidates.length == 0) {
            return new Selection(0);
        }
        Candidate latest = null;
        for (Candidate candidate : candidates) {
            if (candidate == null || candidate.handle == 0) {
                continue;
            }
            if (latest == null || compare(candidate, latest) > 0) {
                latest = candidate;
            }
        }
        return latest == null ? new Selection(0) : latest.toSelection();
    }

    private static int compare(Candidate left, Candidate right) {
        int date = Long.compare(left.dateKey(), right.dateKey());
        if (date != 0) {
            return date;
        }
        int filename = Integer.compare(left.filenameNumber(), right.filenameNumber());
        if (filename != 0) {
            return filename;
        }
        int sequence = Integer.compare(left.sequenceNumber(), right.sequenceNumber());
        if (sequence != 0) {
            return sequence;
        }
        return Integer.compareUnsigned(left.handle, right.handle);
    }

    public static final class Candidate {
        public final int handle;
        private final ObjectInfo info;

        public Candidate(int handle, ObjectInfo info) {
            this.handle = handle;
            this.info = info;
        }

        private long dateKey() {
            long capture = ptpDateKey(info == null ? null : info.captureDate);
            if (capture != 0) {
                return capture;
            }
            return ptpDateKey(info == null ? null : info.modificationDate);
        }

        private int filenameNumber() {
            if (info == null || info.filename == null) {
                return 0;
            }
            int value = 0;
            boolean foundDigit = false;
            for (int i = 0; i < info.filename.length(); i++) {
                char c = info.filename.charAt(i);
                if (c >= '0' && c <= '9') {
                    foundDigit = true;
                    value = value * 10 + (c - '0');
                }
            }
            return foundDigit ? value : 0;
        }

        private int sequenceNumber() {
            return info == null ? 0 : info.sequenceNumber;
        }

        private Selection toSelection() {
            return new Selection(
                    handle,
                    info == null ? null : info.filename,
                    info == null ? null : info.captureDate,
                    info == null ? null : info.modificationDate);
        }

        private static long ptpDateKey(String value) {
            if (value == null || value.isEmpty()) {
                return 0;
            }
            long key = 0;
            int digits = 0;
            for (int i = 0; i < value.length() && digits < 14; i++) {
                char c = value.charAt(i);
                if (c >= '0' && c <= '9') {
                    key = key * 10 + (c - '0');
                    digits++;
                }
            }
            return digits >= 8 ? key : 0;
        }
    }

    public static final class Selection {
        public final int handle;
        public final String filename;
        public final String captureDate;
        public final String modificationDate;

        private Selection(int handle) {
            this(handle, null, null, null);
        }

        private Selection(int handle, String filename, String captureDate, String modificationDate) {
            this.handle = handle;
            this.filename = filename;
            this.captureDate = captureDate;
            this.modificationDate = modificationDate;
        }

        public boolean hasHandle() {
            return handle != 0;
        }
    }
}
