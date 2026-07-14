package com.sunasterisk.socialanalytics.util;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

/**
 * Fixture helper: tạo MockMultipartFile .xlsx in-memory dùng Apache POI.
 * Dùng trong các unit test import Excel để tránh phụ thuộc file trên disk.
 */
public final class ExcelFixtureBuilder {

    private ExcelFixtureBuilder() {}

    /**
     * Tạo .xlsx in-memory với một sheet: hàng 0 = headers, hàng tiếp theo = data.
     * Mỗi phần tử {@code rows} là mảng String có độ dài bằng số cột.
     *
     * @param filename tên file gốc (ví dụ "test.xlsx")
     * @param headers  tên các cột
     * @param rows     dòng dữ liệu (tùy chọn — bỏ qua để tạo file header-only)
     */
    public static MockMultipartFile build(String filename, String[] headers, String[]... rows)
            throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Sheet1");

            // Header row
            var headerRow = sheet.createRow(0);
            for (int c = 0; c < headers.length; c++) {
                headerRow.createCell(c).setCellValue(headers[c]);
            }

            // Data rows
            for (int r = 0; r < rows.length; r++) {
                var dataRow = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    dataRow.createCell(c).setCellValue(rows[r][c]);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return new MockMultipartFile(
                    "file",
                    filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}
