package org.example.finzin.family.dto;

public record SuggestedTransfer(Long fromUserId, String fromUserName, Long toUserId, String toUserName, Double amount) {}
