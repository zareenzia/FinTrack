package org.example.finzin.todo.dto;

/** Edits a single item's editable fields — title/notes/dueDate only. Moving between
 *  list/parent after creation is out of scope; see PATCH /items/{id}/complete|important for the rest. */
public record TodoItemUpdateRequest(String title, String notes, String dueDate) {}
