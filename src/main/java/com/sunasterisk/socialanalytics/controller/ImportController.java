package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.ImportBatchResponse;
import com.sunasterisk.socialanalytics.service.ExcelImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/import-posts")
@RequiredArgsConstructor
@Tag(name = "Import", description = "Excel bulk import endpoints")
public class ImportController {

    private final ExcelImportService excelImportService;

    // HTTP 200 trong cả hai trường hợp DONE và FAILED (theo spec FR-005):
    // lỗi validate là kết quả nghiệp vụ, không phải lỗi HTTP.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import posts from .xlsx file (all-or-nothing, validate-first)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Import processed; check status field for DONE/FAILED"),
        @ApiResponse(responseCode = "400", description = "Not an Excel file, no data rows found, or exceeds 10 MB limit")
    })
    public ResponseEntity<ImportBatchResponse> importPosts(
            @Parameter(description = "Excel (.xlsx) file containing post rows to import")
            @RequestParam("file") MultipartFile file) {
        ImportBatchResponse response = excelImportService.importPosts(file);
        return ResponseEntity.ok(response);
    }
}
