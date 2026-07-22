package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.util.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    // ?sort=<field không tồn tại> → 400 thay vì 500
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<Map<String, String>> handleInvalidProperty(PropertyReferenceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid sort/filter property: " + ex.getPropertyName()));
    }

    // ?sort=<biểu thức có ký tự lạ> (vd default '["string"]' của Swagger UI) đi qua
    // đường validate khác của Spring Data → InvalidDataAccessApiUsageException chứ không
    // phải PropertyReferenceException. Vẫn là lỗi input của client → 400.
    // Log warn vì exception này cũng có thể do server dùng sai API — cần thấy được trong log.
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<Map<String, String>> handleInvalidApiUsage(InvalidDataAccessApiUsageException ex) {
        log.warn("Invalid data access API usage: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid request parameter (unsupported sort or filter expression)"));
    }

    // Vi phạm ràng buộc DB (unique, FK...) → 409, không lộ chi tiết constraint ra ngoài
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Data conflict: the resource violates a uniqueness or reference constraint"));
    }

    // Lỗi input từ client: giá trị enum không hợp lệ, file import sai định dạng, v.v. → 400
    // Không lộ stack trace; message từ service đã đủ mô tả cho client.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        log.debug("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    // Import: không tìm được seed user — lỗi cấu hình môi trường → 500
    // KHÔNG nối ex.getMessage() vào body — tránh lộ chi tiết nội bộ (POI, reflection, DB state)
    // ra client chưa xác thực; chi tiết chỉ nằm trong log.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleInternalState(IllegalStateException ex) {
        log.error("Internal state error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    // File vượt giới hạn multipart (spring.servlet.multipart.max-file-size) → 400
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload size exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Maximum upload size exceeded (limit: 10MB)"));
    }
}
