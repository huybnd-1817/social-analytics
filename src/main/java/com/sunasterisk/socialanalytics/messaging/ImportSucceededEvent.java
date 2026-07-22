package com.sunasterisk.socialanalytics.messaging;

/** Spring application event nội bộ, được publish bên trong transaction import. */
public record ImportSucceededEvent(Long batchId, int recordCount) {}
