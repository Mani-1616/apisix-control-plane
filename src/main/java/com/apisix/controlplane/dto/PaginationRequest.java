package com.apisix.controlplane.dto;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;

    @Parameter(description = "Page number (1-indexed)", example = "1")
    @Min(value = 1, message = "Page number must be at least 1")
    @Builder.Default
    private int page = DEFAULT_PAGE;

    @Parameter(description = "Number of items per page", example = "10")
    @Min(value = 1, message = "Page size must be at least 1")
    @Builder.Default
    private int size = DEFAULT_SIZE;

    public PageRequest toPageable() {
        return PageRequest.of(page - 1, size);
    }
}
