package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.service.ExcelExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller xuất báo cáo dưới dạng file Excel (.xlsx).
 * <p>
 * D2 — chưa có xác thực (auth dành cho D3); endpoint mở theo thiết kế.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Export", description = "Xuất báo cáo Excel")
public class ExcelExportController {

    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final DateTimeFormatter FILENAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ExcelExportService excelExportService;

    /**
     * GET /export-report
     * <p>
     * Trả về file .xlsx chứa toàn bộ post ACTIVE và metric mới nhất của từng post.
     * Tên file có dạng report_yyyyMMddHHmmss.xlsx (BR-003).
     */
    @GetMapping("/export-report")
    @Operation(summary = "Xuất báo cáo post ACTIVE ra file Excel (.xlsx)")
    public void exportReport(HttpServletResponse response) throws IOException {
        // Tạo tên file có timestamp UTC — đáp ứng BR-003
        String filename = "report_" + FILENAME_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC)) + ".xlsx";

        // Build workbook TRƯỚC khi set header — nếu build ném exception,
        // Spring vẫn còn quyền trả về 500 với response sạch
        byte[] workbookBytes = excelExportService.buildExportBytes();

        response.setContentType(CONTENT_TYPE_XLSX);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        response.setContentLength(workbookBytes.length);
        response.getOutputStream().write(workbookBytes);
        response.getOutputStream().flush();
    }
}
