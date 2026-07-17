package org.example.finzin.service;

import org.example.finzin.entity.AccountEntity;
import org.example.finzin.repository.AccountRepository;
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
    private final AccountRepository accountRepository;

    public FinancialSummaryService(TransactionRepository transactionRepository, AssetRepository assetRepository,
                                    GoldAssetService goldAssetService, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.goldAssetService = goldAssetService;
        this.accountRepository = accountRepository;
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

    /** Sum of outstanding balances across the user's CREDIT_CARD accounts — a liability, not an asset. */
    public double getTotalCreditCardDebt(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .filter(a -> "CREDIT_CARD".equals(a.getAccountType()))
                .mapToDouble(AccountEntity::getCurrentBalance)
                .sum();
    }

    public double getNetWorth(Long userId) {
        return getBalance(userId) + getTotalSavings(userId) + getTotalAssets(userId) - getTotalCreditCardDebt(userId);
    }
}
