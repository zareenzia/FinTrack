package org.example.finzin.todo.dto;

/** Exactly one of listId/parentItemId is expected: listId for a new top-level item,
 *  parentItemId for a new sub-item (its listId is copied from the parent server-side). */
public record TodoItemRequest(String title, String notes, String dueDate, Long listId, Long parentItemId) {}
