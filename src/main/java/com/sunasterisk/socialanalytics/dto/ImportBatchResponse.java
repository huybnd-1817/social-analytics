package com.sunasterisk.socialanalytics.dto;

import com.sunasterisk.socialanalytics.entity.ImportBatch;

/**
 * DTO trả về sau mỗi lần import: tổng số dòng, số thành công, số thất bại và trạng thái batch.
 * Dùng static factory from(entity) theo quy ước chung của project.
 */
public record ImportBatchResponse(
        Long batchId,
        Integer totalRecords,
        Integer successRecords,
        Integer failedRecords,
        String status
) {
    /**
     * Tạo response từ entity ImportBatch sau khi đã cập nhật đầy đủ counts và status.
     */
    public static ImportBatchResponse from(ImportBatch batch) {
        return new ImportBatchResponse(
                batch.getId(),
                batch.getTotalRecords(),
                batch.getSuccessRecords(),
                batch.getFailedRecords(),
                batch.getStatus().name()
        );
    }
}
