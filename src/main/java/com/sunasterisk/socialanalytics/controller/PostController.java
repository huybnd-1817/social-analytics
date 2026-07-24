package com.sunasterisk.socialanalytics.controller;

import com.sunasterisk.socialanalytics.dto.PostResponse;
import com.sunasterisk.socialanalytics.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management endpoints")
public class PostController {

    private final PostService postService;

    @GetMapping
    @Operation(summary = "List active posts (paginated)")
    @ApiResponse(responseCode = "200", description = "Page of active posts")
    public Page<PostResponse> list(
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return postService.findAll(pageable);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a post")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Post deleted"),
        @ApiResponse(responseCode = "404", description = "Post not found")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
