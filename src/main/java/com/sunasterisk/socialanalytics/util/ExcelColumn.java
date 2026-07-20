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
// Annotation này chỉ được phép đặt trên field (không dùng trên class, method, v.v.)
@Target(ElementType.FIELD)
// Giữ annotation trong bytecode và cho phép đọc bằng Reflection lúc runtime.
// RetentionPolicy có 3 mức:
//   SOURCE  → bị xóa sau khi compile, chỉ tồn tại trong file .java
//   CLASS   → có trong file .class nhưng JVM không nạp vào bộ nhớ khi chạy
//   RUNTIME → JVM nạp vào bộ nhớ, Reflection đọc được lúc runtime
// ReflectionRowWriter gọi field.getAnnotation(ExcelColumn.class) lúc chạy ứng dụng;
// nếu không dùng RUNTIME thì getAnnotation() luôn trả về null
// → không field nào được nhận diện → không xuất được cột nào ra Excel.
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
