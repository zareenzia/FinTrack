package org.example.finzin.family.dto;

public record SettlementRequest(Long toUserId, Double amount, String note) {}
