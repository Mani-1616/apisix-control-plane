package com.apisix.controlplane.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginatedResponse<T> {

    private List<T> content;
    private int page;           // 1-indexed
    private int size;
    private long totalElements;
    private int totalPages;

    /**
     * Create a PaginatedResponse from a Spring Page with pre-mapped content.
     */
    public static <T> PaginatedResponse<T> from(Page<?> page, List<T> mappedContent) {
        return PaginatedResponse.<T>builder()
                .content(mappedContent)
                .page(page.getNumber() + 1)  // convert 0-indexed to 1-indexed
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
