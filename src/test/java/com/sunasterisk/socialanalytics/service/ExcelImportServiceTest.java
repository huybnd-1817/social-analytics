package com.sunasterisk.socialanalytics.service;

import com.sunasterisk.socialanalytics.dto.ImportBatchResponse;
import com.sunasterisk.socialanalytics.entity.ImportBatch;
import com.sunasterisk.socialanalytics.entity.ImportBatchStatus;
import com.sunasterisk.socialanalytics.entity.Post;
import com.sunasterisk.socialanalytics.entity.PostStatus;
import com.sunasterisk.socialanalytics.entity.SocialProvider;
import com.sunasterisk.socialanalytics.entity.User;
import com.sunasterisk.socialanalytics.messaging.ImportSucceededEvent;
import com.sunasterisk.socialanalytics.repository.PostRepository;
import com.sunasterisk.socialanalytics.util.ExcelFixtureBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test cho ExcelImportService — pure Mockito, không Spring context, không H2.
 * Lý do: H2 không hỗ trợ NAMED_ENUM JDBC type của Hibernate 6.
 */
@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ImportBatchService importBatchService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ExcelImportService excelImportService;

    // Seed user + batch dùng chung qua nhiều test
    private User seedUser;
    private ImportBatch processingBatch;

    @BeforeEach
    void setUp() {
        seedUser = User.builder()
                .id(1L)
                .email("seed@example.com")
                .name("Seed User")
                .build();

        processingBatch = ImportBatch.builder()
                .id(10L)
                .user(seedUser)
                .fileName("test.xlsx")
                .status(ImportBatchStatus.PROCESSING)
                .build();
    }

    // -------------------------------------------------------------------------
    // T01: file hợp lệ — batch DONE, toàn bộ post được lưu
    // -------------------------------------------------------------------------

    @Test
    void importPosts_validFile_persistsAllAndReturnsDone() throws Exception {
        // Chuẩn bị: 2 dòng hợp lệ, không trùng lặp DB
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id", "title", "content", "post_url"},
                new String[]{"FACEBOOK", "fb-001", "Title 1", "Content 1", "https://fb.com/1"},
                new String[]{"TWITTER",  "tw-001", "Title 2", "Content 2", "https://tw.com/1"}
        );

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        when(importBatchService.createProcessingBatch(eq(seedUser), anyString()))
                .thenReturn(processingBatch);
        // DB không có bản ghi trùng
        when(postRepository.findByStatusAndPlatformAndPlatformPostIdIn(
                eq(PostStatus.ACTIVE), eq(SocialProvider.FACEBOOK), anyList()))
                .thenReturn(List.of());
        when(postRepository.findByStatusAndPlatformAndPlatformPostIdIn(
                eq(PostStatus.ACTIVE), eq(SocialProvider.TWITTER), anyList()))
                .thenReturn(List.of());
        // saveAll trả về danh sách được lưu
        when(postRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(importBatchService.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportBatchResponse response = excelImportService.importPosts(file);

        // Batch phải DONE với 2 bản ghi thành công
        assertThat(response.status()).isEqualTo("DONE");
        assertThat(response.successRecords()).isEqualTo(2);
        assertThat(response.failedRecords()).isEqualTo(0);
        assertThat(response.totalRecords()).isEqualTo(2);

        // saveAll phải được gọi đúng một lần
        verify(postRepository).saveAll(anyList());
        // event phải được publish để kích hoạt JMS pipeline
        verify(eventPublisher).publishEvent(any(ImportSucceededEvent.class));
    }

    // -------------------------------------------------------------------------
    // T02: thiếu cột bắt buộc — IllegalArgumentException, không lưu gì
    // -------------------------------------------------------------------------

    @Test
    void importPosts_missingRequiredColumns_throwsIllegalArgumentException() throws Exception {
        // Header thiếu cột platform_post_id
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "title", "content"},
                new String[]{"FACEBOOK", "Title 1", "Content 1"}
        );

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        // Lưu ý: buildHeaderIndex ném exception TRƯỚC khi createProcessingBatch được gọi
        // (xem thứ tự dòng 57 vs 61 trong ExcelImportService) → không cần mock createProcessingBatch

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> excelImportService.importPosts(file));

        // Thông báo lỗi phải đề cập cột còn thiếu
        assertThat(ex.getMessage()).containsIgnoringCase("platform_post_id");

        // Không được lưu bất kỳ post nào
        verify(postRepository, never()).saveAll(any());
        verify(importBatchService, never()).createProcessingBatch(any(), any());
    }

    // -------------------------------------------------------------------------
    // T03: file chỉ có header, không có dòng dữ liệu — IllegalArgumentException
    // -------------------------------------------------------------------------

    @Test
    void importPosts_emptyFile_throwsIllegalArgumentException() throws Exception {
        // Chỉ có header row, không có data row
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id", "title"}
                // không truyền data rows
        );

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        // Với file header-only, sheet.getLastRowNum() == 0 → exception TRƯỚC createProcessingBatch
        // → không cần mock createProcessingBatch

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> excelImportService.importPosts(file));

        assertThat(ex.getMessage()).containsIgnoringCase("no data rows");

        // Không có batch, không có post
        verify(importBatchService, never()).createProcessingBatch(any(), any());
        verify(postRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // T04: platform không hợp lệ (INSTAGRAM) — batch FAILED, không lưu post
    // -------------------------------------------------------------------------

    @Test
    void importPosts_unknownPlatform_returnsFailed() throws Exception {
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"INSTAGRAM", "ig-001", "Instagram Post"}
        );

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        when(importBatchService.createProcessingBatch(eq(seedUser), anyString()))
                .thenReturn(processingBatch);
        when(importBatchService.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportBatchResponse response = excelImportService.importPosts(file);

        // Platform null → BR-001/BR-002 lỗi → batch FAILED
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.successRecords()).isEqualTo(0);

        // Không lưu post nào
        verify(postRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // T05: trùng lặp trong file — batch FAILED, không lưu post
    // -------------------------------------------------------------------------

    @Test
    void importPosts_inFileDuplicate_returnsFailed() throws Exception {
        // Hai dòng cùng (FACEBOOK, fb-dup) — trùng lặp trong file
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"FACEBOOK", "fb-dup", "Post A"},
                new String[]{"FACEBOOK", "fb-dup", "Post B"}
        );

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        when(importBatchService.createProcessingBatch(eq(seedUser), anyString()))
                .thenReturn(processingBatch);
        // Dòng thứ hai bị loại do trùng trong file TRƯỚC khi vào DB-check;
        // chỉ một platformPostId FACEBOOK hợp lệ đến checkDbDuplicates → chỉ mock FACEBOOK.
        // TWITTER list rỗng → checkDbDuplicates trả về sớm, không gọi repository.
        when(postRepository.findByStatusAndPlatformAndPlatformPostIdIn(
                eq(PostStatus.ACTIVE), eq(SocialProvider.FACEBOOK), anyList()))
                .thenReturn(List.of());
        when(importBatchService.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportBatchResponse response = excelImportService.importPosts(file);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.successRecords()).isEqualTo(0);
        verify(postRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // T06: trùng lặp với DB (ACTIVE) — batch FAILED, không lưu post
    // -------------------------------------------------------------------------

    @Test
    void importPosts_dbDuplicate_returnsFailed() throws Exception {
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id", "title"},
                new String[]{"FACEBOOK", "fb-existing", "Existing Post"}
        );

        // Giả lập bản ghi đã tồn tại ACTIVE trong DB
        Post existingPost = Post.builder()
                .id(99L)
                .platform(SocialProvider.FACEBOOK)
                .platformPostId("fb-existing")
                .build();

        when(importBatchService.resolveSeedUser()).thenReturn(seedUser);
        when(importBatchService.createProcessingBatch(eq(seedUser), anyString()))
                .thenReturn(processingBatch);
        // File chỉ có một post FACEBOOK → checkDbDuplicates chỉ query FACEBOOK;
        // TWITTER list rỗng → không gọi repository → không mock TWITTER.
        when(postRepository.findByStatusAndPlatformAndPlatformPostIdIn(
                eq(PostStatus.ACTIVE), eq(SocialProvider.FACEBOOK), anyList()))
                .thenReturn(List.of(existingPost));
        when(importBatchService.save(any(ImportBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportBatchResponse response = excelImportService.importPosts(file);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.successRecords()).isEqualTo(0);
        verify(postRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // T07: file không phải .xlsx — IllegalArgumentException trước khi tạo batch
    // -------------------------------------------------------------------------

    @Test
    void importPosts_nonXlsxFile_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "platform,platform_post_id\nFACEBOOK,fb-001".getBytes());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> excelImportService.importPosts(file));

        assertThat(ex.getMessage()).containsIgnoringCase("xlsx");

        // Không tạo batch, không lưu post
        verify(importBatchService, never()).createProcessingBatch(any(), any());
        verify(postRepository, never()).saveAll(any());
    }

    // -------------------------------------------------------------------------
    // T08: không tìm thấy seed user — IllegalStateException (HTTP 500)
    // -------------------------------------------------------------------------

    @Test
    void importPosts_seedUserMissing_throwsIllegalStateException() throws Exception {
        MockMultipartFile file = ExcelFixtureBuilder.build("test.xlsx",
                new String[]{"platform", "platform_post_id"},
                new String[]{"FACEBOOK", "fb-001"}
        );

        when(importBatchService.resolveSeedUser())
                .thenThrow(new IllegalStateException(
                        "No seed user found — environment misconfigured (no User row in DB)"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> excelImportService.importPosts(file));

        assertThat(ex.getMessage()).containsIgnoringCase("seed user");
        verify(importBatchService, never()).createProcessingBatch(any(), any());
        verify(postRepository, never()).saveAll(any());
    }
}
