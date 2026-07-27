package org.example.finzin.todo.dto;

/** folderId may be null to move a list back out to standalone/top-level. */
public record FolderMoveRequest(Long folderId) {}
