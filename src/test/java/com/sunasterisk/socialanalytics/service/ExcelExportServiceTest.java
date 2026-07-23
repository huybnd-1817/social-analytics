package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialMetric;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.repository.SocialMetricRepository;
import com.sunasterisk.socialanalytics.util.ReflectionRowWriter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ExcelExportService (D6-08):
 * - Kiểm tra headers khớp với @ExcelColumn trên ExportRowModel
 * - Kiểm tra row dữ liệu được ghi đúng khi có / không có metric
 * - Xác minh @ExcelColumn trên Post và SocialMetric entity hoạt động với ReflectionRowWriter
 */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private SocialMetricRepository socialMetricRepository;

    @InjectMocks
    private ExcelExportService service;

    private static final Instant PUBLISHED = Instant.parse("2026-07-13T08:00:00Z");
    private static final Instant CRAWLED   = Instant.parse("2026-07-13T10:30:00Z");

    private Post buildPost() {
        return Post.builder()
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("fb-001")
                .title("Test Post")
                .postUrl("https://facebook.com/fb-001")
                .publishedAt(PUBLISHED)
                .status(PostStatus.ACTIVE)
                .build();
    }

    private SocialMetric buildMetric(Post post) {
        return SocialMetric.builder()
                .post(post)
                .likesCount(10L)
                .sharesCount(5L)
                .commentsCount(3L)
                .followersCount(100L)
                .reach(200L)
                .impressions(500L)
                .crawledAt(CRAWLED)
                .build();
    }

    // ── Verify full export output ──────────────────────────────────────────────

    @Test
    void buildExportBytes_headers_matchExcelColumnOrder() throws IOException {
        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(List.of());

        byte[] bytes = service.buildExportBytes();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row header = wb.getSheetAt(0).getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("platform");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("platform_post_id");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("title");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("post_url");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("published_at");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("status");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("likes_count");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("shares_count");
            assertThat(header.getCell(8).getStringCellValue()).isEqualTo("comments_count");
            assertThat(header.getCell(9).getStringCellValue()).isEqualTo("followers_count");
            assertThat(header.getCell(10).getStringCellValue()).isEqualTo("reach");
            assertThat(header.getCell(11).getStringCellValue()).isEqualTo("impressions");
            assertThat(header.getCell(12).getStringCellValue()).isEqualTo("crawled_at");
        }
    }

    @Test
    void buildExportBytes_postWithMetric_writesCorrectDataRow() throws IOException {
        Post post = buildPost();
        SocialMetric metric = buildMetric(post);

        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(List.of(post));
        when(socialMetricRepository.findTop1ByPostOrderByCrawledAtDesc(post))
                .thenReturn(Optional.of(metric));

        byte[] bytes = service.buildExportBytes();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = wb.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("FACEBOOK");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("fb-001");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("Test Post");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("https://facebook.com/fb-001");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2026-07-13 08:00:00");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("ACTIVE");
            assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(10.0);
            assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(5.0);
            assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(3.0);
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(100.0);
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(200.0);
            assertThat(row.getCell(11).getNumericCellValue()).isEqualTo(500.0);
            assertThat(row.getCell(12).getStringCellValue()).isEqualTo("2026-07-13 10:30:00");
        }
    }

    @Test
    void buildExportBytes_postWithoutMetric_writesZeroCountersAndBlankDate() throws IOException {
        Post post = buildPost();

        when(postRepository.findByStatus(PostStatus.ACTIVE)).thenReturn(List.of(post));
        when(socialMetricRepository.findTop1ByPostOrderByCrawledAtDesc(post))
                .thenReturn(Optional.empty());

        byte[] bytes = service.buildExportBytes();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = wb.getSheetAt(0).getRow(1);
            // metric counters default to 0
            assertThat(row.getCell(6).getNumericCellValue()).isZero();
            assertThat(row.getCell(7).getNumericCellValue()).isZero();
            assertThat(row.getCell(8).getNumericCellValue()).isZero();
            assertThat(row.getCell(9).getNumericCellValue()).isZero();
            assertThat(row.getCell(10).getNumericCellValue()).isZero();
            assertThat(row.getCell(11).getNumericCellValue()).isZero();
            // crawled_at null → BLANK cell
            assertThat(row.getCell(12).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    // ── Verify @ExcelColumn on entity classes (D6-08) ────────────────────────

    @Test
    void reflectionRowWriter_post_discoversAnnotatedFields() throws IOException {
        ReflectionRowWriter<Post> writer = new ReflectionRowWriter<>(Post.class);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writer.writeHeader(sheet, 0);

            Row header = sheet.getRow(0);
            // @ExcelColumn on Post: order 1..6
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("platform");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("platform_post_id");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("title");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("post_url");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("published_at");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("status");
            assertThat(header.getCell(6)).isNull();
        }
    }

    @Test
    void reflectionRowWriter_post_writesEntityRow() throws IOException {
        Post post = buildPost();
        ReflectionRowWriter<Post> writer = new ReflectionRowWriter<>(Post.class);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writer.writeRow(sheet, 0, post);

            Row row = sheet.getRow(0);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("FACEBOOK");
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("fb-001");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("Test Post");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("https://facebook.com/fb-001");
            assertThat(row.getCell(4).getStringCellValue()).isEqualTo("2026-07-13 08:00:00");
            assertThat(row.getCell(5).getStringCellValue()).isEqualTo("ACTIVE");
        }
    }

    @Test
    void reflectionRowWriter_socialMetric_discoversAnnotatedFields() throws IOException {
        ReflectionRowWriter<SocialMetric> writer = new ReflectionRowWriter<>(SocialMetric.class);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writer.writeHeader(sheet, 0);

            Row header = sheet.getRow(0);
            // @ExcelColumn on SocialMetric: order 1..7
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("likes_count");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("shares_count");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("comments_count");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("followers_count");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("reach");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("impressions");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("crawled_at");
            assertThat(header.getCell(7)).isNull();
        }
    }

    @Test
    void reflectionRowWriter_socialMetric_writesEntityRow() throws IOException {
        Post post = buildPost();
        SocialMetric metric = buildMetric(post);
        ReflectionRowWriter<SocialMetric> writer = new ReflectionRowWriter<>(SocialMetric.class);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            writer.writeRow(sheet, 0, metric);

            Row row = sheet.getRow(0);
            assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(10.0);
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(5.0);
            assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(3.0);
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(100.0);
            assertThat(row.getCell(4).getNumericCellValue()).isEqualTo(200.0);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(500.0);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("2026-07-13 10:30:00");
        }
    }
}
