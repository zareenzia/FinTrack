package org.example.finzin.todo.dto;

import java.util.List;

public record TodoItemDetailResponse(TodoItemResponse item, List<TodoItemResponse> subItems) {}
