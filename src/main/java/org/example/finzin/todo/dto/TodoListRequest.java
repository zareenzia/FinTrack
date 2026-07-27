package org.example.finzin.todo.dto;

/** folderId may be null to create a standalone, top-level list. */
public record TodoListRequest(String name, Long folderId) {}
