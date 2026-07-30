package org.example.finzin.todo.dto;

/** Voice-assistant entry point — no list to pick, so the service resolves/creates a default "Tasks" list. */
public record QuickItemRequest(String title, String dueDate) {}
