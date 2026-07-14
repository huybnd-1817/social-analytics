package com.sunasterisk.socialanalytics.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một field của row-model là cột Excel (D6-06).
 * ReflectionRowWriter scan các field mang annotation này để dựng header + cell
 * động — field không có annotation sẽ KHÔNG được xuất.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

    /** Tên cột hiển thị trên header; để trống → dùng tên field. */
    String headerName() default "";

    /** Thứ tự cột (tăng dần). Trùng giá trị → giữ thứ tự khai báo field (sort ổn định). */
    int order() default Integer.MAX_VALUE;

    /**
     * Pattern ngày/giờ (java.time.format.DateTimeFormatter) cho field kiểu temporal
     * (Instant...). Format theo UTC. Để trống → giá trị ghi bằng toString().
     */
    String format() default "";
}
