package com.remoteyourcam.usb;

public final class AppConfig {
    private AppConfig() {}
    public static final boolean LOG = true;
    public static final boolean USE_ACRA = false;
    public static final boolean LOG_PACKETS = false;
    public static final int EVENTCHECK_PERIOD = 500;
    public static final int USB_TRANSFER_TIMEOUT = 30000;
}
