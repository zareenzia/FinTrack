package org.example.finzin.web;

import org.example.finzin.entity.AssetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class FinanceApiController {
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;

    public FinanceApiController(CategoryRepository categoryRepository, TransactionRepository transactionRepository, AssetRepository assetRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
    }

    // ============== CATEGORY ENDPOINTS ==============
    @GetMapping("/categories")
    public List<Map<String, Object>> getCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingLong(CategoryEntity::getId))
                .map(this::toCategoryResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody CategoryRequest request) {
        if (request == null || request.name == null || request.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        if (categoryRepository.existsByNameIgnoreCase(request.name.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        CategoryEntity entity = new CategoryEntity(
                request.name.trim(),
                request.description == null ? "" : request.description,
                request.color == null || request.color.isBlank() ? "#3498db" : request.color,
                request.icon == null || request.icon.isBlank() ? "tag" : request.icon
        );
        CategoryEntity saved = categoryRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCategoryResponse(saved));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        CategoryEntity existing = categoryRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        if (request == null || request.name == null || request.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        String updatedName = request.name.trim();
        boolean duplicate = categoryRepository.findAll().stream()
                .anyMatch(c -> !c.getId().equals(id) && c.getName().equalsIgnoreCase(updatedName));
        if (duplicate) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        existing.setName(updatedName);
        if (request.description != null) {
            existing.setDescription(request.description);
        }
        if (request.color != null && !request.color.isBlank()) {
            existing.setColor(request.color);
        }
        if (request.icon != null && !request.icon.isBlank()) {
            existing.setIcon(request.icon);
        }
        CategoryEntity updated = categoryRepository.save(existing);
        return ResponseEntity.ok(toCategoryResponse(updated));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== ASSET ENDPOINTS ==============
    @GetMapping("/assets")
    public List<Map<String, Object>> getAssets() {
        return assetRepository.findAll().stream()
                .sorted(Comparator.comparingLong(AssetEntity::getId))
                .map(this::toAssetResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/assets")
    public ResponseEntity<?> createAsset(@RequestBody AssetRequest request) {
        if (request == null || request.name == null || request.name.isBlank() || request.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        AssetEntity entity = new AssetEntity(
                request.name.trim(),
                request.type == null ? "General" : request.type,
                request.description == null ? "" : request.description,
                request.value,
                LocalDateTime.now()
        );
        AssetEntity saved = assetRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssetResponse(saved));
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<?> updateAsset(@PathVariable Long id, @RequestBody AssetRequest request) {
        AssetEntity existing = assetRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        if (request == null || request.name == null || request.name.isBlank() || request.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        existing.setName(request.name.trim());
        if (request.type != null) {
            existing.setType(request.type);
        }
        if (request.description != null) {
            existing.setDescription(request.description);
        }
        existing.setValue(request.value);
        existing.setCreatedAt(LocalDateTime.now());
        AssetEntity updated = assetRepository.save(existing);
        return ResponseEntity.ok(toAssetResponse(updated));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<?> deleteAsset(@PathVariable Long id) {
        if (!assetRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        assetRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== TRANSACTION ENDPOINTS ==============
    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions(
            @RequestParam(required = false, name = "category_id") Long categoryIdFilter,
            @RequestParam(required = false, name = "type") String type,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        List<TransactionEntity> transactions = transactionRepository.findAll();
        
        return transactions.stream()
                .filter(t -> categoryIdFilter == null || t.getCategory().getId().equals(categoryIdFilter))
                .filter(t -> type == null || type.isBlank() || t.getTransactionType().equalsIgnoreCase(type))
                .sorted(Comparator.comparing(TransactionEntity::getDate).reversed())
                .limit(limit)
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> createTransaction(@RequestBody TransactionRequest request) {
        if (request == null
                || request.description == null
                || request.description.isBlank()
                || request.amount == null
                || request.category_id == null
                || request.transaction_type == null
                || request.transaction_type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        CategoryEntity category = categoryRepository.findById(request.category_id).orElse(null);
        if (category == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }

        String normalizedType = request.transaction_type.toLowerCase(Locale.ROOT);
        if (!normalizedType.equals("income") && !normalizedType.equals("expense")) {
            return ResponseEntity.badRequest().body(Map.of("error", "transaction_type must be income or expense"));
        }

        LocalDateTime date = parseDate(request.date);
        TransactionEntity entity = new TransactionEntity(
                request.amount,
                request.description.trim(),
                category,
                normalizedType,
                date,
                LocalDateTime.now()
        );
        TransactionEntity saved = transactionRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(saved));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        if (!transactionRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transaction not found"));
        }
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== ANALYTICS ENDPOINTS ==============
    @GetMapping("/analytics/summary")
    public Map<String, Object> summary() {
        double income = getTotalIncome();
        double expense = getTotalExpense();
        double savings = income - expense;
        double totalAssets = getTotalAssets();
        double savingsRate = income == 0 ? 0 : (savings / income) * 100;
        
        return Map.of(
                "total_income", income,
                "total_expense", expense,
                "balance", savings,
                "total_savings", savings,
                "savings_rate", savingsRate,
                "total_assets", totalAssets,
                "net_worth", savings + totalAssets,
                "transaction_count", transactionRepository.count()
        );
    }

    @GetMapping("/analytics/savings")
    public Map<String, Object> savings() {
        double income = getTotalIncome();
        double expense = getTotalExpense();
        double totalSavings = income - expense;
        double savingsRate = income == 0 ? 0 : (totalSavings / income) * 100;
        return Map.of(
                "total_income", income,
                "total_expense", expense,
                "total_savings", totalSavings,
                "savings_rate", savingsRate
        );
    }

    @GetMapping("/analytics/category-breakdown")
    public List<Map<String, Object>> categoryBreakdown() {
        Map<Long, BreakdownAccumulator> grouped = new LinkedHashMap<>();
        transactionRepository.findAll().stream()
                .filter(t -> t.getTransactionType().equals("expense"))
                .forEach(t -> {
                    BreakdownAccumulator acc = grouped.computeIfAbsent(t.getCategory().getId(), k -> new BreakdownAccumulator());
                    acc.total += t.getAmount();
                    acc.count += 1;
                });

        List<Map<String, Object>> response = new ArrayList<>();
        for (Map.Entry<Long, BreakdownAccumulator> entry : grouped.entrySet()) {
            CategoryEntity category = categoryRepository.findById(entry.getKey()).orElse(null);
            if (category == null) {
                continue;
            }
            response.add(Map.of(
                    "category", category.getName(),
                    "color", category.getColor(),
                    "total", entry.getValue().total,
                    "count", entry.getValue().count
            ));
        }
        return response;
    }

    @GetMapping("/analytics/monthly")
    public List<Map<String, Object>> monthly() {
        Map<YearMonth, Totals> grouped = new LinkedHashMap<>();
        for (TransactionEntity t : transactionRepository.findAll()) {
            YearMonth month = YearMonth.from(t.getDate());
            Totals totals = grouped.computeIfAbsent(month, m -> new Totals());
            if (t.getTransactionType().equals("income")) {
                totals.income += t.getAmount();
            } else {
                totals.expense += t.getAmount();
            }
        }

        List<Map<String, Object>> response = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String month = entry.getKey().toString();
                    response.add(Map.of("month", month, "type", "income", "total", entry.getValue().income));
                    response.add(Map.of("month", month, "type", "expense", "total", entry.getValue().expense));
                });
        return response;
    }

    // ============== HELPER METHODS ==============
    private Map<String, Object> toCategoryResponse(CategoryEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "name", entity.getName(),
                "description", entity.getDescription(),
                "color", entity.getColor(),
                "icon", entity.getIcon()
        );
    }

    private Map<String, Object> toAssetResponse(AssetEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "name", entity.getName(),
                "type", entity.getType(),
                "description", entity.getDescription(),
                "value", entity.getValue(),
                "created_at", entity.getCreatedAt().toString()
        );
    }

    private Map<String, Object> toTransactionResponse(TransactionEntity entity) {
        return Map.of(
                "id", entity.getId(),
                "amount", entity.getAmount(),
                "description", entity.getDescription(),
                "category_id", entity.getCategory().getId(),
                "category_name", entity.getCategory().getName(),
                "transaction_type", entity.getTransactionType(),
                "date", entity.getDate().toString(),
                "created_at", entity.getCreatedAt().toString()
        );
    }


    private double getTotalIncome() {
        Double sum = transactionRepository.sumByTransactionType("income");
        return sum == null ? 0 : sum;
    }

    private double getTotalExpense() {
        Double sum = transactionRepository.sumByTransactionType("expense");
        return sum == null ? 0 : sum;
    }

    private double getTotalAssets() {
        Double sum = assetRepository.sumAllValues();
        return sum == null ? 0 : sum;
    }

    private LocalDateTime parseDate(String dateString) {
        if (dateString == null || dateString.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(dateString);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(dateString).atStartOfDay();
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    // ============== INNER CLASSES ==============
    private static class BreakdownAccumulator {
        private double total;
        private int count;
    }

    private static class Totals {
        private double income;
        private double expense;
    }

    private record CategoryRequest(String name, String description, String color, String icon) {
    }

    private record TransactionRequest(
            Double amount,
            String description,
            Long category_id,
            String transaction_type,
            String date
    ) {
    }

    private record AssetRequest(
            String name,
            String type,
            String description,
            Double value
    ) {
    }
}
