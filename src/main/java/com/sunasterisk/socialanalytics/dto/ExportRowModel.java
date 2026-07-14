package com.sunasterisk.socialanalytics.dto;

import com.sunasterisk.socialanalytics.util.ExcelColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model phẳng dùng cho hàng xuất Excel.
 * Cột được khai báo qua @ExcelColumn (D6-06): headerName + order quyết định
 * header và thứ tự; field temporal mang format — ReflectionRowWriter tự format
 * chuỗi UTC, service không cần format thủ công.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRowModel {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // --- Thông tin bài đăng ---
    @ExcelColumn(headerName = "platform", order = 1)
    private String platform;

    @ExcelColumn(headerName = "platform_post_id", order = 2)
    private String platformPostId;

    @ExcelColumn(headerName = "title", order = 3)
    private String title;

    @ExcelColumn(headerName = "post_url", order = 4)
    private String postUrl;

    @ExcelColumn(headerName = "published_at", order = 5, format = DATE_FORMAT)
    private Instant publishedAt;   // null → ô trống

    @ExcelColumn(headerName = "status", order = 6)
    private String status;

    // --- Chỉ số social (0 nếu chưa có metric) ---
    @ExcelColumn(headerName = "likes_count", order = 7)
    private Long likesCount;

    @ExcelColumn(headerName = "shares_count", order = 8)
    private Long sharesCount;

    @ExcelColumn(headerName = "comments_count", order = 9)
    private Long commentsCount;

    @ExcelColumn(headerName = "followers_count", order = 10)
    private Long followersCount;

    @ExcelColumn(headerName = "reach", order = 11)
    private Long reach;

    @ExcelColumn(headerName = "impressions", order = 12)
    private Long impressions;

    // --- Thời điểm crawl (null nếu chưa có metric → ô trống) ---
    @ExcelColumn(headerName = "crawled_at", order = 13, format = DATE_FORMAT)
    private Instant crawledAt;
}
