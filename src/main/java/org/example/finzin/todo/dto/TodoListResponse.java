package org.example.finzin.todo.dto;

public record TodoListResponse(
        Long id,
        String name,
        Long folderId,
        Integer boardPosition,
        long itemCount,
        long completedItemCount,
        String createdAt,
        String updatedAt
) {}
