package org.example.finzin.todo.dto;

public record TodoFolderResponse(
        Long id,
        String name,
        Integer boardPosition,
        String createdAt,
        String updatedAt
) {}
