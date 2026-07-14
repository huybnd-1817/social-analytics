package com.sunasterisk.socialanalytics.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.lang.reflect.Field;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Ghi hàng tiêu đề và hàng dữ liệu vào sheet Excel bằng reflection (ALG-001, D6-07).
 * <p>
 * Chỉ các field mang {@link ExcelColumn} được xuất: header lấy từ headerName
 * (trống → tên field), thứ tự cột theo order (trùng → giữ thứ tự khai báo),
 * field temporal có format → format chuỗi theo pattern (UTC).
 * <p>
 * Quy tắc ghi cell:
 * <ul>
 *   <li>Giá trị null → ô trống</li>
 *   <li>Temporal + format → chuỗi theo pattern (UTC)</li>
 *   <li>Giá trị {@code Number} → setCellValue(double)</li>
 *   <li>Mọi kiểu còn lại → toString()</li>
 * </ul>
 *
 * @param <T> kiểu row-model — chỉ cần field có @ExcelColumn; không cần implements.
 */
public class ReflectionRowWriter<T> {

    /** Mô tả một cột đã resolve: field + header + formatter (null nếu không có format). */
    private record ColumnSpec(Field field, String header, DateTimeFormatter formatter) {}

    // Trần Excel: 32767 ký tự/cell — vượt trần POI ném IllegalArgumentException giữa chừng
    private static final int EXCEL_CELL_MAX_CHARS = 32767;

    private final List<ColumnSpec> columns;

    /**
     * Khởi tạo writer: scan @ExcelColumn của {@code modelClass} một lần và cache lại.
     * Ném IllegalStateException nếu class không có field nào được đánh dấu —
     * lỗi lập trình, fail sớm thay vì xuất file rỗng.
     *
     * @param modelClass class của row-model (ví dụ ExportRowModel.class)
     */
    public ReflectionRowWriter(Class<T> modelClass) {
        this.columns = Arrays.stream(modelClass.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(ExcelColumn.class))
                // sorted() của Stream ổn định → order trùng nhau giữ thứ tự khai báo
                .sorted(Comparator.comparingInt(f -> f.getAnnotation(ExcelColumn.class).order()))
                .map(ReflectionRowWriter::toColumnSpec)
                .toList();

        if (this.columns.isEmpty()) {
            throw new IllegalStateException(
                    modelClass.getSimpleName() + " không có field nào mang @ExcelColumn");
        }
    }

    /** Resolve annotation của một field thành ColumnSpec (header, formatter). */
    private static ColumnSpec toColumnSpec(Field field) {
        field.setAccessible(true);
        ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
        String header = annotation.headerName().isEmpty() ? field.getName() : annotation.headerName();

        DateTimeFormatter formatter = null;
        if (!annotation.format().isEmpty()) {
            // format chỉ có nghĩa với field temporal — khai sai chỗ thì fail sớm lúc khởi tạo,
            // không để output sai lặng lẽ (rơi xuống toString) ở runtime
            if (!TemporalAccessor.class.isAssignableFrom(field.getType())) {
                throw new IllegalStateException("Field '" + field.getName() + "' mang format nhưng kiểu "
                        + field.getType().getSimpleName() + " không phải TemporalAccessor");
            }
            try {
                formatter = DateTimeFormatter.ofPattern(annotation.format()).withZone(ZoneOffset.UTC);
            } catch (IllegalArgumentException e) {
                // Nêu đích danh field lỗi — không chôn nguyên nhân trong BeanCreationException lồng nhau
                throw new IllegalStateException("Field '" + field.getName()
                        + "' có format pattern không hợp lệ: '" + annotation.format() + "'", e);
            }
        }
        return new ColumnSpec(field, header, formatter);
    }

    /**
     * Ghi hàng tiêu đề vào {@code sheet} tại {@code rowIndex}.
     * Tên cột = headerName của @ExcelColumn (trống → tên field).
     */
    public void writeHeader(Sheet sheet, int rowIndex) {
        Row headerRow = sheet.createRow(rowIndex);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).header());
        }
    }

    /**
     * Ghi một hàng dữ liệu từ {@code model} vào {@code sheet} tại {@code rowIndex}.
     * Ngoại lệ reflection được bọc thành RuntimeException để không rò rỉ checked exception
     * qua lớp service.
     */
    public void writeRow(Sheet sheet, int rowIndex, T model) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < columns.size(); i++) {
            ColumnSpec column = columns.get(i);
            Cell cell = row.createCell(i);
            try {
                Object value = column.field().get(model);
                if (value == null) {
                    cell.setBlank(); // BLANK thật sự — không phải STRING rỗng, đúng ngữ nghĩa khi đọc lại
                } else if (column.formatter() != null && value instanceof TemporalAccessor temporal) {
                    cell.setCellValue(column.formatter().format(temporal));
                } else if (value instanceof Number num) {
                    cell.setCellValue(num.doubleValue());
                } else {
                    cell.setCellValue(truncateToCellLimit(value.toString()));
                }
            } catch (IllegalAccessException e) {
                // Không xảy ra vì đã setAccessible(true) — bọc lại để đảm bảo an toàn
                throw new IllegalStateException(
                        "Không thể truy cập field: " + column.field().getName(), e);
            }
        }
    }

    /** Cắt giá trị vượt trần cell Excel (vd post_url kiểu TEXT không giới hạn) — export không chết vì một giá trị bệnh. */
    private static String truncateToCellLimit(String value) {
        return value.length() <= EXCEL_CELL_MAX_CHARS ? value : value.substring(0, EXCEL_CELL_MAX_CHARS);
    }
}
