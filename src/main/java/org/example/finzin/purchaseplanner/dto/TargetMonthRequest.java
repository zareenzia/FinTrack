package org.example.finzin.purchaseplanner.dto;

/** targetMonth may be null/blank to mean "unscheduled". */
public record TargetMonthRequest(String targetMonth) {}
