package org.example.finzin.purchaseplanner.dto;

import java.util.List;

/** Full ordered list of item ids within one Kanban column (needLevel bucket), sent after a drag-and-drop reorder. */
public record ReorderRequest(String needLevel, List<Long> orderedIds) {}
