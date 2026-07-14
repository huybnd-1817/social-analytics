package com.sunasterisk.socialanalytics.util;

import com.sunasterisk.socialanalytics.entity.ImportBatch;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ALG-001: ánh xạ từng dòng Excel sang entity Post bằng REFLECTION (D2-07).
 * Tên cột snake_case → tên field camelCase của Post (platform_post_id → platformPostId);
 * Field được cache một lần, giá trị ô convert theo field.getType() rồi Field.set().
 * Header matching: case-insensitive. Enum platform: case-sensitive.
 * published_at: hỗ trợ cả ô kiểu ngày (POI numeric) và chuỗi yyyy-MM-dd HH:mm:ss (UTC).
 */
public class ExcelRowMapper {

    // Formatter cho chuỗi ngày dạng "yyyy-MM-dd HH:mm:ss"
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Tên các cột bắt buộc và tùy chọn (lowercase để so sánh case-insensitive)
    private static final String COL_PLATFORM = "platform";
    private static final String COL_PLATFORM_POST_ID = "platform_post_id";
    private static final String COL_TITLE = "title";
    private static final String COL_CONTENT = "content";
    private static final String COL_POST_URL = "post_url";
    private static final String COL_PUBLISHED_AT = "published_at";

    private static final List<String> DATA_COLUMNS = List.of(
            COL_PLATFORM, COL_PLATFORM_POST_ID, COL_TITLE,
            COL_CONTENT, COL_POST_URL, COL_PUBLISHED_AT);

    // Cache Field của Post theo tên cột — dựng một lần lúc load class, tránh lookup lặp lại
    private static final Map<String, Field> FIELD_BY_COLUMN = buildFieldMap();

    /** Kết quả parse: danh sách Post (chưa validate) và danh sách lỗi từng dòng. */
    public record MappingResult(List<Post> posts, List<RowError> errors) {}

    /** Lỗi tại một dòng cụ thể (1-based, tính cả header). */
    public record RowError(int rowNumber, String message) {}

    /**
     * Xây dựng map: tên cột (lowercase) → chỉ số cột (0-based) từ header row.
     * Ném IllegalArgumentException nếu thiếu cột bắt buộc.
     */
    public static Map<String, Integer> buildHeaderIndex(Row headerRow) {
        if (headerRow == null) {
            throw new IllegalArgumentException("Missing required columns: platform, platform_post_id");
        }

        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            // Chỉ nhận header dạng chuỗi — cell FORMULA/NUMERIC làm getStringCellValue()
            // ném IllegalStateException (→ 500); bỏ qua để rơi vào nhánh
            // "Missing required columns" bên dưới (→ 400, đúng bản chất lỗi input)
            if (cell != null && cell.getCellType() == CellType.STRING) {
                String header = cell.getStringCellValue().trim().toLowerCase();
                index.put(header, i);
            }
        }

        // Kiểm tra bắt buộc hai cột quan trọng
        List<String> missing = new ArrayList<>();
        if (!index.containsKey(COL_PLATFORM)) missing.add("platform");
        if (!index.containsKey(COL_PLATFORM_POST_ID)) missing.add("platform_post_id");

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Missing required columns: " + String.join(", ", missing));
        }

        return index;
    }

    /**
     * Ánh xạ tất cả dòng dữ liệu sang Post + thu thập lỗi parse (ALG-001).
     * Field hạ tầng (user, importBatch, status) set tường minh; 6 cột DỮ LIỆU Excel
     * set qua Reflection theo FIELD_BY_COLUMN. Validation thực sự ở ExcelImportService.
     */
    public static MappingResult mapRows(XSSFSheet sheet, Map<String, Integer> headerIndex,
                                        User seedUser, ImportBatch batch) {
        List<Post> posts = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            // Bỏ qua dòng null hoặc dòng trống hoàn toàn
            if (row == null || isRowBlank(row)) continue;

            int displayRow = i + 1; // header = row 1, dữ liệu bắt đầu row 2
            Post post = Post.builder()
                    .user(seedUser)
                    .importBatch(batch)
                    .status(PostStatus.ACTIVE)
                    .build();

            try {
                for (Map.Entry<String, Field> entry : FIELD_BY_COLUMN.entrySet()) {
                    Object value = readCellAs(entry.getValue().getType(), row, headerIndex, entry.getKey());
                    entry.getValue().set(post, value);
                }
            } catch (DateTimeParseException | IllegalStateException e) {
                errors.add(new RowError(displayRow, "published_at không hợp lệ: '"
                        + getCellString(row, headerIndex, COL_PUBLISHED_AT) + "'"));
                continue;
            } catch (IllegalAccessException e) {
                // Không xảy ra vì đã setAccessible(true) — bọc lại để đảm bảo an toàn
                throw new IllegalStateException("Không set được field của Post qua Reflection", e);
            }

            posts.add(post);
        }

        return new MappingResult(posts, errors);
    }

    /** Dựng cache cột → Field của Post; tên field suy từ tên cột (snake_case → camelCase). */
    private static Map<String, Field> buildFieldMap() {
        Map<String, Field> map = new LinkedHashMap<>();
        for (String column : DATA_COLUMNS) {
            try {
                Field field = Post.class.getDeclaredField(toCamelCase(column));
                field.setAccessible(true);
                map.put(column, field);
            } catch (NoSuchFieldException e) {
                // Lệch tên cột ↔ field là lỗi lập trình — fail sớm lúc load class
                throw new IllegalStateException("Post không có field ứng với cột '" + column + "'", e);
            }
        }
        return map;
    }

    /** Chuyển snake_case → camelCase: platform_post_id → platformPostId. */
    private static String toCamelCase(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') { upper = true; continue; }
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return sb.toString();
    }

    /** Convert giá trị ô theo kiểu field đích: String / SocialProvider / Instant. */
    private static Object readCellAs(Class<?> type, Row row,
                                     Map<String, Integer> headerIndex, String colName) {
        if (type == Instant.class) {
            return parsePublishedAt(row, headerIndex);
        }
        String raw = getCellString(row, headerIndex, colName);
        if (type == SocialProvider.class) {
            return parsePlatform(raw); // có thể null nếu không parse được — validate sau
        }
        return raw; // String
    }

    /** Đọc giá trị ô thành String; trả về null nếu không có cột hoặc ô trống. */
    private static String getCellString(Row row, Map<String, Integer> headerIndex, String colName) {
        Integer colIdx = headerIndex.get(colName);
        if (colIdx == null) return null;

        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        // Cell FORMULA: đọc theo kiểu KẾT QUẢ đã cache (không evaluate lại);
        // cell thường: theo kiểu trực tiếp. ERROR/BLANK → null.
        CellType effectiveType = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        return switch (effectiveType) {
            case STRING -> {
                String val = cell.getStringCellValue().trim();
                yield val.isEmpty() ? null : val;
            }
            case NUMERIC -> numericToString(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null; // BLANK, ERROR, _NONE
        };
    }

    /** Convert numeric an toàn: chặn NaN/Infinity (kết quả formula lỗi) → null thay vì "0". */
    private static String numericToString(double value) {
        return Double.isFinite(value) ? String.valueOf((long) value) : null;
    }

    /**
     * Parse published_at: POI numeric date → Instant UTC; chuỗi yyyy-MM-dd HH:mm:ss → Instant UTC.
     * Ném DateTimeParseException nếu chuỗi không parse được.
     */
    private static Instant parsePublishedAt(Row row, Map<String, Integer> headerIndex) {
        Integer colIdx = headerIndex.get(COL_PUBLISHED_AT);
        if (colIdx == null) return null;

        Cell cell = row.getCell(colIdx);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            // Ô ngày POI (numeric) — LocalDateTime không tz → coi là UTC
            return cell.getLocalDateTimeCellValue().toInstant(ZoneOffset.UTC);
        }

        // Ô chuỗi "yyyy-MM-dd HH:mm:ss"
        String raw = cell.getCellType() == CellType.STRING
                ? cell.getStringCellValue().trim()
                : String.valueOf((long) cell.getNumericCellValue());
        if (raw.isEmpty()) return null;

        LocalDateTime ldt = LocalDateTime.parse(raw, DATE_TIME_FORMATTER);
        return ldt.toInstant(ZoneOffset.UTC);
    }

    /** Parse platform string thành enum — trả null nếu không hợp lệ (ExcelImportService kiểm tra sau). */
    private static SocialProvider parsePlatform(String value) {
        if (value == null) return null;
        try {
            return SocialProvider.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Kiểm tra dòng hoàn toàn trống (tất cả ô null hoặc BLANK). */
    private static boolean isRowBlank(Row row) {
        // POI trả -1 khi row được định nghĩa nhưng không chứa cell nào → coi là trống
        if (row.getFirstCellNum() < 0) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
