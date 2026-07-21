package com.sunasterisk.socialanalytics.messaging;

/** Tập trung quản lý tên các JMS queue — tránh hardcode string rải rác trong code. */
public final class JmsQueues {

    public static final String IMPORT_COMPLETED = "IMPORT_COMPLETED";

    private JmsQueues() {}
}
