package org.example.finzin.service;

import org.example.finzin.entity.BudgetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.BudgetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, CategoryRepository categoryRepository,
                          TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<BudgetEntity> getForUserAndPeriod(Long userId, String period) {
        return budgetRepository.findByUserIdAndPeriod(userId, period);
    }

    public BudgetEntity upsert(Long userId, Long categoryId, String period, Double budgetAmount) {
        BudgetEntity entity = budgetRepository.findByUserIdAndCategoryIdAndPeriod(userId, categoryId, period)
                .orElseGet(BudgetEntity::new);
        entity.setUserId(userId);
        entity.setCategoryId(categoryId);
        entity.setPeriod(period);
        entity.setBudgetAmount(budgetAmount);
        return budgetRepository.save(entity);
    }

    public BudgetEntity findOwnedById(Long id, Long userId) {
        BudgetEntity entity = budgetRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return entity;
    }

    public void delete(Long id) {
        budgetRepository.deleteById(id);
    }

    /** Per-category budgeted vs actual spend for the given "yyyy-MM" period. */
    public List<Map<String, Object>> getStatus(Long userId, String period) {
        YearMonth yearMonth = YearMonth.parse(period);
        List<BudgetEntity> budgets = budgetRepository.findByUserIdAndPeriod(userId, period);

        Map<Long, Double> actualByCategory = transactionRepository.findByUserId(userId).stream()
                .filter(t -> "expense".equals(t.getTransactionType()))
                .filter(t -> t.getCategory() != null)
                .filter(t -> YearMonth.from(t.getDate()).equals(yearMonth))
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(), Collectors.summingDouble(TransactionEntity::getAmount)));

        return budgets.stream().map(b -> {
            CategoryEntity category = categoryRepository.findById(b.getCategoryId()).orElse(null);
            double actual = actualByCategory.getOrDefault(b.getCategoryId(), 0.0);
            double percentUsed = b.getBudgetAmount() == 0 ? 0 : (actual / b.getBudgetAmount()) * 100;

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", b.getId());
            map.put("categoryId", b.getCategoryId());
            map.put("categoryName", category != null ? category.getName() : "Unknown");
            map.put("categoryColor", category != null ? category.getColor() : "#6c757d");
            map.put("period", b.getPeriod());
            map.put("budgetAmount", b.getBudgetAmount());
            map.put("actualAmount", actual);
            map.put("percentUsed", percentUsed);
            return map;
        }).collect(Collectors.toList());
    }
}
