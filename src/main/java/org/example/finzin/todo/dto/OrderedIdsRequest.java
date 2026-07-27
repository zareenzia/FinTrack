package org.example.finzin.todo.dto;

import java.util.List;

/** Full ordered list of ids within one bucket — used for the folders reorder endpoint. */
public record OrderedIdsRequest(List<Long> orderedIds) {}
