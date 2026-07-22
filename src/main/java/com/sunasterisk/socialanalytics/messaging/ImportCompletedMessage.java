package com.sunasterisk.socialanalytics.messaging;

/** Payload JMS được gửi vào queue IMPORT_COMPLETED sau khi import Excel thành công. */
public record ImportCompletedMessage(Long batchId, int recordCount) {}
