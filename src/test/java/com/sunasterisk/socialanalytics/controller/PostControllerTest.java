package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.PostResponse;
import com.sunasterisk.socialanalytics.service.PostService;
import com.sunasterisk.socialanalytics.util.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@ActiveProfiles("test")
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @Test
    void list_returns200WithPageJson() throws Exception {
        PostResponse post = new PostResponse(
                1L, "FACEBOOK", "fb-1", "Title 1", "Content 1",
                "https://facebook.com/posts/1",
                Instant.parse("2026-07-08T09:00:00Z"), "ACTIVE",
                Instant.parse("2026-07-08T09:00:00Z"));
        Pageable pageable = PageRequest.of(0, 20);
        when(postService.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        mockMvc.perform(get("/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].platform").value("FACEBOOK"))
                .andExpect(jsonPath("$.content[0].platformPostId").value("fb-1"))
                .andExpect(jsonPath("$.content[0].title").value("Title 1"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));

        // Khóa contract @PageableDefault: size 20, sort createdAt DESC
        verify(postService).findAll(argThat(p -> {
            Sort.Order order = p.getSort().getOrderFor("createdAt");
            return p.getPageSize() == 20 && order != null && order.getDirection() == Sort.Direction.DESC;
        }));
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/posts/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(postService).deleteById(1L);
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        // GlobalExceptionHandler map ResourceNotFoundException → 404 {"error": message}
        doThrow(new ResourceNotFoundException("Post not found: 99"))
                .when(postService).deleteById(99L);

        mockMvc.perform(delete("/posts/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Post not found: 99"));
    }
}
