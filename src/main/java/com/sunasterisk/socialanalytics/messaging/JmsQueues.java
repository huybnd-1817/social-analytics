package com.sunasterisk.socialanalytics.messaging;

/** Tập trung quản lý tên các JMS queue — tránh hardcode string rải rác trong code. */
public final class JmsQueues {

    public static final String IMPORT_COMPLETED     = "IMPORT_COMPLETED";
    /** Dead Letter Queue — nhận message từ IMPORT_COMPLETED sau khi hết số lần retry. */
    public static final String IMPORT_COMPLETED_DLQ = "DLQ." + IMPORT_COMPLETED;

    private JmsQueues() {}
}
