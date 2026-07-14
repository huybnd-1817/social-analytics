package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.ImportBatchResponse;
import com.sunasterisk.socialanalytics.entity.ImportBatch;
import com.sunasterisk.socialanalytics.entity.ImportBatchStatus;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.util.ExcelRowMapper;
import com.sunasterisk.socialanalytics.util.ExcelRowMapper.MappingResult;
import com.sunasterisk.socialanalytics.util.ExcelRowMapper.RowError;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** F002: parse .xlsx → validate toàn bộ dòng (validate-first) → all-or-nothing persist. ImportBatch luôn được lưu. */
@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelImportService.class);

    private final PostRepository postRepository;
    private final ImportBatchService importBatchService;

    /**
     * Điểm vào chính: nhận file multipart, trả ImportBatchResponse.
     * IllegalArgumentException → 400; IllegalStateException (no seed user) → 500.
     * <p>
     * @Transactional đặt ở entry point (proxy call từ controller) — KHÔNG đặt trên
     * persistSuccess() vì self-invocation bỏ qua proxy, annotation sẽ vô hiệu:
     * saveAll() và save(batch) sẽ rơi vào hai transaction rời nhau.
     */
    @Transactional
    public ImportBatchResponse importPosts(MultipartFile file) {
        validateFileGuard(file);                                      // BR-006
        User seedUser = importBatchService.resolveSeedUser();         // BR-005

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = workbook.getSheetAt(0);

            if (sheet.getLastRowNum() <= 0) {
                throw new IllegalArgumentException("File contains no data rows");
            }

            // ALG-001: header index case-insensitive; ném nếu thiếu cột bắt buộc
            Map<String, Integer> headerIndex = ExcelRowMapper.buildHeaderIndex(sheet.getRow(0));

            // Filename có thể null tùy client — fallback để không vi phạm NOT NULL của import_batches.
            // Cắt về 255 ký tự (giữ phần đuôi chứa extension) khớp VARCHAR(255) — tránh 409 khó hiểu.
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "unknown.xlsx";
            if (filename.length() > 255) {
                filename = filename.substring(filename.length() - 255);
            }

            // Persist batch sớm (PROCESSING) — audit record tồn tại kể cả khi validate thất bại
            ImportBatch batch = importBatchService.createProcessingBatch(seedUser, filename);

            MappingResult mapped = ExcelRowMapper.mapRows(sheet, headerIndex, seedUser, batch);
            List<Post> parsedPosts = mapped.posts();
            List<RowError> allErrors = new ArrayList<>(mapped.errors());

            int totalRecords = parsedPosts.size() + mapped.errors().size();
            batch.setTotalRecords(totalRecords);

            validateRows(parsedPosts, allErrors);   // BR-001, BR-002, BR-003

            // BR-004: all-or-nothing
            if (allErrors.isEmpty()) {
                persistSuccess(batch, parsedPosts);
            } else {
                persistFailure(batch, totalRecords, allErrors);
            }

            return ImportBatchResponse.from(batch);

        } catch (IOException e) {
            // Không nối e.getMessage() vào response — tránh lộ chi tiết nội bộ POI ra client
            log.warn("Không đọc được file Excel: {}", e.getMessage());
            throw new IllegalArgumentException("Không đọc được file Excel (file hỏng hoặc sai định dạng)", e);
        }
    }

    /**
     * BR-001/002/003: required fields, enum platform, trùng lặp trong file và DB.
     * Ghi nhận tất cả lỗi; không ném exception.
     */
    private void validateRows(List<Post> posts, List<RowError> allErrors) {
        Set<String> seenInFile = new HashSet<>();
        List<String> facebookIds = new ArrayList<>();
        List<String> twitterIds = new ArrayList<>();

        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            int displayRow = i + 2; // header = row 1, data từ row 2

            // BR-001: required fields
            if (post.getPlatform() == null && post.getPlatformPostId() == null) {
                allErrors.add(new RowError(displayRow,
                        "platform và platform_post_id là bắt buộc"));
                continue;
            }
            if (post.getPlatform() == null) {
                allErrors.add(new RowError(displayRow,
                        "platform là bắt buộc và phải là FACEBOOK hoặc TWITTER (BR-001/BR-002)"));
                continue;
            }
            if (post.getPlatformPostId() == null || post.getPlatformPostId().isBlank()) {
                allErrors.add(new RowError(displayRow, "platform_post_id là bắt buộc (BR-001)"));
                continue;
            }

            // Chặn tràn cột VARCHAR — thành row error (batch FAILED) thay vì DataIntegrityViolation → 409
            if (post.getPlatformPostId().length() > 255) {
                allErrors.add(new RowError(displayRow, "platform_post_id vượt quá 255 ký tự"));
                continue;
            }
            if (post.getTitle() != null && post.getTitle().length() > 500) {
                allErrors.add(new RowError(displayRow, "title vượt quá 500 ký tự"));
                continue;
            }

            // BR-003 in-file
            String fileKey = post.getPlatform().name() + "|" + post.getPlatformPostId();
            if (seenInFile.contains(fileKey)) {
                allErrors.add(new RowError(displayRow,
                        "Trùng lặp trong file: (" + post.getPlatform().name()
                                + ", " + post.getPlatformPostId() + ")"));
                continue;
            }
            seenInFile.add(fileKey);

            if (post.getPlatform() == SocialProvider.FACEBOOK) {
                facebookIds.add(post.getPlatformPostId());
            } else {
                twitterIds.add(post.getPlatformPostId());
            }
        }

        // BR-003 in-DB: một query per platform
        checkDbDuplicates(SocialProvider.FACEBOOK, facebookIds, posts, allErrors);
        checkDbDuplicates(SocialProvider.TWITTER,  twitterIds,  posts, allErrors);
    }

    /** BR-003: phát hiện trùng lặp với DB — đánh dấu lỗi cho từng Post vi phạm. */
    private void checkDbDuplicates(SocialProvider platform, List<String> ids,
                                    List<Post> posts, List<RowError> allErrors) {
        if (ids.isEmpty()) return;
        Set<String> dupKeys = new HashSet<>();
        postRepository.findByStatusAndPlatformAndPlatformPostIdIn(PostStatus.ACTIVE, platform, ids)
                .forEach(p -> dupKeys.add(p.getPlatformPostId()));
        if (dupKeys.isEmpty()) return;
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.getPlatform() == platform && dupKeys.contains(post.getPlatformPostId())) {
                allErrors.add(new RowError(i + 2,
                        "Đã tồn tại post ACTIVE với (" + platform.name()
                                + ", " + post.getPlatformPostId() + ") trong DB (BR-003)"));
            }
        }
    }

    /** BR-004: toàn bộ dòng hợp lệ — lưu tất cả Post + batch DONE (trong transaction của importPosts). */
    private void persistSuccess(ImportBatch batch, List<Post> posts) {
        postRepository.saveAll(posts);
        batch.setStatus(ImportBatchStatus.DONE);
        batch.setSuccessRecords(posts.size());
        batch.setFailedRecords(0);
        batch.setImportedAt(Instant.now());
        importBatchService.save(batch);
        log.info("Import thành công: batchId={}, posts={}", batch.getId(), posts.size());
    }

    /** BR-004: có ít nhất một lỗi — không lưu Post, batch FAILED. */
    private void persistFailure(ImportBatch batch, int totalRecords, List<RowError> errors) {
        batch.setStatus(ImportBatchStatus.FAILED);
        batch.setSuccessRecords(0);
        batch.setFailedRecords(totalRecords);
        importBatchService.save(batch);
        log.warn("Import thất bại: batchId={}, errors={}", batch.getId(), errors.size());
        errors.forEach(e -> log.warn("  Dòng {}: {}", e.rowNumber(), e.message()));
    }

    /**
     * BR-006: từ chối trước khi tạo batch — file rỗng hoặc không phải .xlsx.
     * Kiểm tra cả extension lẫn content-type; chấp nhận khi ít nhất một hợp lệ.
     */
    private void validateFileGuard(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File contains no data rows");
        }

        String filename = file.getOriginalFilename();
        boolean validExtension = filename != null
                && filename.toLowerCase().endsWith(".xlsx");
        boolean validContentType = file.getContentType() != null
                && (file.getContentType().contains("spreadsheetml")
                || file.getContentType().contains("openxmlformats"));

        if (!validExtension && !validContentType) {
            throw new IllegalArgumentException(
                    "Chỉ chấp nhận file .xlsx (Excel 2007+)");
        }
    }
}
