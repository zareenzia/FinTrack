package org.example.finzin.service;

import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.gold.GoldAssetService;
import org.springframework.stereotype.Service;

/**
 * Shared financial totals used by both the dashboard analytics endpoint and the AI Assistant's
 * getNetWorth tool, so the two never compute a different number from the same underlying data.
 */
@Service
public class FinancialSummaryService {
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final GoldAssetService goldAssetService;

    public FinancialSummaryService(TransactionRepository transactionRepository, AssetRepository assetRepository, GoldAssetService goldAssetService) {
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.goldAssetService = goldAssetService;
    }

    public double getTotalIncome(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "income");
        return sum == null ? 0 : sum;
    }

    public double getTotalExpense(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "expense");
        return sum == null ? 0 : sum;
    }

    public double getTotalSavings(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "savings");
        return sum == null ? 0 : sum;
    }

    public double getTotalAssets(Long userId) {
        Double sum = assetRepository.sumValuesByUserId(userId);
        double regularAssets = sum == null ? 0 : sum;
        double goldAssets = goldAssetService.getTotalGoldValueForUser(userId);
        return regularAssets + goldAssets;
    }

    public double getBalance(Long userId) {
        return getTotalIncome(userId) - getTotalExpense(userId) - getTotalSavings(userId);
    }

    public double getSavingsRate(Long userId) {
        double income = getTotalIncome(userId);
        return income == 0 ? 0 : (getTotalSavings(userId) / income) * 100;
    }

    public double getNetWorth(Long userId) {
        return getBalance(userId) + getTotalSavings(userId) + getTotalAssets(userId);
    }
}
