package org.example.finzin.todo.dto;

import java.util.List;

/** folderId null = reordering the standalone/top-level lists bucket. */
public record ListReorderRequest(Long folderId, List<Long> orderedIds) {}
