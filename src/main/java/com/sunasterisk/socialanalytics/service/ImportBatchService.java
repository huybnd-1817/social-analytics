package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.ImportBatch;
import com.sunasterisk.socialanalytics.entity.ImportBatchStatus;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.repository.ImportBatchRepository;
import com.sunasterisk.socialanalytics.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý vòng đời ImportBatch: tạo mới, cập nhật trạng thái, lưu kết quả.
 * Seed-user resolution (BR-005): dùng user có id nhỏ nhất — fallback trước khi OAuth2 lên D3.
 */
@Service
@RequiredArgsConstructor
public class ImportBatchService {

    private final ImportBatchRepository importBatchRepository;
    private final UserRepository userRepository;

    /**
     * Lấy seed user (user đầu tiên theo id tăng dần).
     * Ném IllegalStateException → HTTP 500 nếu DB chưa có user nào (môi trường cấu hình sai).
     */
    public User resolveSeedUser() {
        return userRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "No seed user found — environment misconfigured (no User row in DB)"));
    }

    /**
     * Tạo và lưu batch mới ở trạng thái PENDING, rồi chuyển ngay sang PROCESSING.
     * REQUIRES_NEW: commit độc lập với transaction của importPosts — audit record sống sót
     * kể cả khi transaction ngoài rollback (vd race trùng khóa ở saveAll → 409).
     * Trade-off chấp nhận: trường hợp rollback đó batch kẹt ở PROCESSING (vẫn hơn mất hẳn row).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ImportBatch createProcessingBatch(User user, String fileName) {
        ImportBatch batch = ImportBatch.builder()
                .user(user)
                .fileName(fileName)
                .status(ImportBatchStatus.PENDING)
                .build();
        // Lưu PENDING trước để @CreatedDate điền importedAt
        batch = importBatchRepository.save(batch);

        // Chuyển sang PROCESSING — validation loop sắp bắt đầu (SM-001)
        batch.setStatus(ImportBatchStatus.PROCESSING);
        return importBatchRepository.save(batch);
    }

    /**
     * Lưu batch sau khi đã cập nhật đầy đủ status + counts từ ExcelImportService.
     */
    @Transactional
    public ImportBatch save(ImportBatch batch) {
        return importBatchRepository.save(batch);
    }
}
