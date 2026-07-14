package com.sunasterisk.socialanalytics.util;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test cho ReflectionRowWriter + @ExcelColumn (D6-06/D6-07):
 * header từ headerName, thứ tự theo order, format temporal, bỏ field không annotate.
 */
class ReflectionRowWriterTest {

    /** Model thử nghiệm: order đảo so với thứ tự khai báo, có field không annotate. */
    private static class SampleRow {
        @ExcelColumn(headerName = "count", order = 2)
        private final Long count;

        @ExcelColumn(headerName = "name", order = 1)
        private final String name;

        @ExcelColumn(order = 3, format = "yyyy-MM-dd HH:mm:ss")
        private final Instant createdAt;

        // Không có @ExcelColumn — không được xuất
        private final String internal = "hidden";

        SampleRow(String name, Long count, Instant createdAt) {
            this.name = name;
            this.count = count;
            this.createdAt = createdAt;
        }
    }

    private static class NoColumnRow {
        private final String value = "x";
    }

    /** Hai field trùng order — kỳ vọng giữ thứ tự khai báo (sort ổn định). */
    private static class TiedOrderRow {
        @ExcelColumn(order = 1)
        private final String first = "a";

        @ExcelColumn(order = 1)
        private final String second = "b";
    }

    /** Pattern chứa ký tự reserved — kỳ vọng fail-fast nêu đích danh field. */
    private static class BadPatternRow {
        @ExcelColumn(format = "{bad}")
        private final Instant when = Instant.EPOCH;
    }

    /** format trên field không phải temporal — kỳ vọng fail-fast thay vì toString lặng lẽ. */
    private static class FormatOnStringRow {
        @ExcelColumn(format = "yyyy-MM-dd")
        private final String name = "x";
    }

    @Test
    void writeHeader_annotatedModel_ordersByOrderAndUsesHeaderName() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            new ReflectionRowWriter<>(SampleRow.class).writeHeader(sheet, 0);

            Row header = sheet.getRow(0);
            // order 1=name, 2=count, 3=createdAt (headerName trống → tên field); "internal" bị loại
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("name");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("count");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("createdAt");
            assertThat(header.getCell(3)).isNull();
        }
    }

    @Test
    void writeRow_typedValues_formatsTemporalAndWritesNumeric() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            ReflectionRowWriter<SampleRow> writer = new ReflectionRowWriter<>(SampleRow.class);

            Instant instant = Instant.parse("2026-07-14T03:04:05Z");
            writer.writeRow(sheet, 1, new SampleRow("post-a", 42L, instant));

            Row row = sheet.getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("post-a");
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(42.0);
            // format="yyyy-MM-dd HH:mm:ss" theo UTC
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-07-14 03:04:05");
        }
    }

    @Test
    void writeRow_nullValues_writesTrueBlankCells() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            ReflectionRowWriter<SampleRow> writer = new ReflectionRowWriter<>(SampleRow.class);

            writer.writeRow(sheet, 0, new SampleRow(null, null, null));

            Row row = sheet.getRow(0);
            // BLANK thật sự (không phải STRING rỗng) — đúng ngữ nghĩa khi consumer đọc lại file
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void writeHeader_tiedOrder_keepsDeclarationOrder() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            new ReflectionRowWriter<>(TiedOrderRow.class).writeHeader(sheet, 0);

            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("first");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("second");
        }
    }

    @Test
    void constructor_modelWithoutExcelColumn_throwsIllegalStateException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ReflectionRowWriter<>(NoColumnRow.class));
        assertThat(ex.getMessage()).contains("@ExcelColumn");
    }

    @Test
    void constructor_invalidFormatPattern_throwsWithFieldName() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ReflectionRowWriter<>(BadPatternRow.class));
        assertThat(ex.getMessage()).contains("when").contains("{bad}");
    }

    @Test
    void constructor_formatOnNonTemporalField_throwsWithFieldName() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new ReflectionRowWriter<>(FormatOnStringRow.class));
        assertThat(ex.getMessage()).contains("name").contains("TemporalAccessor");
    }

    @Test
    void writeRow_stringOverExcelCellLimit_truncatedTo32767() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet();
            ReflectionRowWriter<SampleRow> writer = new ReflectionRowWriter<>(SampleRow.class);

            writer.writeRow(sheet, 0, new SampleRow("x".repeat(40_000), 1L, null));

            // Không ném IllegalArgumentException từ POI; giá trị bị cắt về trần 32767
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).hasSize(32_767);
        }
    }
}
