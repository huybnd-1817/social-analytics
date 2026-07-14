package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.ExportRowModel;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import com.sunasterisk.socialanalytics.util.ReflectionRowWriter;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Dịch vụ xuất báo cáo Excel.
 * <p>
 * Luồng xử lý:
 * 1. Lấy toàn bộ post ACTIVE (unbounded — quy mô demo chấp nhận được).
 * 2. Với mỗi post: truy vấn metric mới nhất qua findTop1ByPostOrderByCrawledAtDesc
 *    (N+1 được chấp nhận ở D2; composite index post_id + crawled_at DESC giữ mỗi
 *    truy vấn nhanh).
 * 3. Xây ExportRowModel; nếu không có metric → counter = 0, crawledAt null (ô trống).
 * 4. Ghi workbook vào byte array và trả về cho controller stream xuống client.
 *    Header/thứ tự cột/format ngày do @ExcelColumn trên ExportRowModel quyết định (D6-07).
 * <p>
 * Dùng XSSFWorkbook (in-heap) thay vì SXSSFWorkbook vì quy mô demo nhỏ;
 * khi cần export lớn hơn có thể chuyển sang SXSSFWorkbook mà không thay đổi
 * contract của phương thức này.
 * <p>
 * @Transactional(readOnly=true) bao toàn bộ phương thức để tránh
 * LazyInitializationException khi Hibernate load lazy association trong vòng lặp
 * (open-in-view=false trên dự án này).
 */
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final String SHEET_NAME = "Report";

    private final PostRepository postRepository;
    private final SocialMetricRepository socialMetricRepository;

    // Scan @ExcelColumn một lần cho vòng đời singleton — không lặp reflection mỗi request.
    // Không phải final-constructor-arg nên @RequiredArgsConstructor bỏ qua field này.
    private final ReflectionRowWriter<ExportRowModel> writer =
            new ReflectionRowWriter<>(ExportRowModel.class);

    /**
     * Xây dựng workbook Excel chứa toàn bộ post ACTIVE và trả về dưới dạng byte array.
     *
     * @return nội dung file .xlsx dạng byte[]
     * @throws IOException nếu ghi vào ByteArrayOutputStream thất bại (hiếm gặp)
     */
    @Transactional(readOnly = true)
    public byte[] buildExportBytes() throws IOException {
        List<Post> activePosts = postRepository.findByStatus(PostStatus.ACTIVE);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);

            // Hàng 0: tiêu đề — @ExcelColumn trên ExportRowModel xác định tên + thứ tự cột
            writer.writeHeader(sheet, 0);

            // Hàng 1..N: dữ liệu
            int rowIndex = 1;
            for (Post post : activePosts) {
                SocialMetric metric = socialMetricRepository
                        .findTop1ByPostOrderByCrawledAtDesc(post)
                        .orElse(null);

                ExportRowModel row = buildRow(post, metric);
                writer.writeRow(sheet, rowIndex++, row);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // --- helper ---

    /**
     * Chuyển Post + SocialMetric (nullable) thành ExportRowModel phẳng.
     * Instant giữ nguyên — format là việc của ReflectionRowWriter theo @ExcelColumn.
     * Nếu metric == null: counter giữ giá trị 0, crawledAt null (→ ô trống).
     */
    private ExportRowModel buildRow(Post post, SocialMetric metric) {
        return ExportRowModel.builder()
                .platform(post.getPlatform() != null ? post.getPlatform().name() : "")
                .platformPostId(post.getPlatformPostId())
                .title(post.getTitle())
                .postUrl(post.getPostUrl())
                .publishedAt(post.getPublishedAt())
                .status(post.getStatus() != null ? post.getStatus().name() : "")
                // metric fields — 0 / null nếu chưa có metric
                .likesCount(metric != null ? metric.getLikesCount() : 0L)
                .sharesCount(metric != null ? metric.getSharesCount() : 0L)
                .commentsCount(metric != null ? metric.getCommentsCount() : 0L)
                .followersCount(metric != null ? metric.getFollowersCount() : 0L)
                .reach(metric != null ? metric.getReach() : 0L)
                .impressions(metric != null ? metric.getImpressions() : 0L)
                .crawledAt(metric != null ? metric.getCrawledAt() : null)
                .build();
    }
}
