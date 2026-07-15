package it.mazzoni.vis.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record UserPageResponse(
        List<UserResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static UserPageResponse from(Page<UserResponse> page) {
        return new UserPageResponse(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
