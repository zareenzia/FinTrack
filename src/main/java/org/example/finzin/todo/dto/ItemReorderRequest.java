package org.example.finzin.todo.dto;

import java.util.List;

/** parentItemId null = reordering listId's top-level items; non-null = reordering that parent's sub-items. */
public record ItemReorderRequest(Long listId, Long parentItemId, List<Long> orderedIds) {}
