package org.example.finzin.family.dto;

/** netBalance > 0 means this member is owed money by the household; < 0 means they owe. */
public record MemberBalance(Long userId, String userName, Double totalPaid, Double fairShare, Double netBalance) {}
