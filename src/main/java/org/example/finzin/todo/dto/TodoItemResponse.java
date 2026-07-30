package org.example.finzin.todo.dto;

public record TodoItemResponse(
        Long id,
        String title,
        String notes,
        Boolean completed,
        Boolean important,
        String dueDate,
        Long listId,
        Long parentItemId,
        Integer boardPosition,
        int subItemCount,
        int subItemCompletedCount,
        String completedAt,
        String createdAt,
        String updatedAt
) {}
