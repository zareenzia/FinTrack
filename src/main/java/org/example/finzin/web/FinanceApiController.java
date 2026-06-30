package org.example.finzin.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
public class FinanceApiController {
    private static final Path DATA_PATH = Paths.get("data", "fintrack-data.json");

    private final AtomicLong categoryId = new AtomicLong(0);
    private final AtomicLong transactionId = new AtomicLong(0);
    private final AtomicLong assetId = new AtomicLong(0);
    private final Map<Long, Category> categories = new LinkedHashMap<>();
    private final Map<Long, TransactionItem> transactions = new LinkedHashMap<>();
    private final Map<Long, AssetItem> assets = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public FinanceApiController() {
        loadData();
    }

    @GetMapping("/categories")
    public List<Category> getCategories() {
        return categories.values().stream()
                .sorted(Comparator.comparingLong(Category::id))
                .toList();
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody CategoryRequest request) {
        if (request == null || request.name == null || request.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        boolean exists = categories.values().stream()
                .anyMatch(c -> c.name().equalsIgnoreCase(request.name.trim()));
        if (exists) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        Category category = createCategoryInternal(request);
        persistData();
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        Category existing = categories.get(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        if (request == null || request.name == null || request.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        String updatedName = request.name.trim();
        boolean duplicate = categories.values().stream()
                .anyMatch(c -> !c.id().equals(id) && c.name().equalsIgnoreCase(updatedName));
        if (duplicate) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        Category updated = new Category(
                id,
                updatedName,
                request.description == null ? existing.description() : request.description,
                request.color == null || request.color.isBlank() ? existing.color() : request.color,
                request.icon == null || request.icon.isBlank() ? existing.icon() : request.icon
        );
        categories.put(id, updated);
        persistData();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        if (!categories.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        categories.remove(id);
        transactions.values().removeIf(t -> t.categoryId().equals(id));
        persistData();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/assets")
    public List<AssetItem> getAssets() {
        return assets.values().stream()
                .sorted(Comparator.comparingLong(AssetItem::id))
                .toList();
    }

    @PostMapping("/assets")
    public ResponseEntity<?> createAsset(@RequestBody AssetRequest request) {
        if (request == null || request.name == null || request.name.isBlank() || request.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        AssetItem asset = new AssetItem(
                assetId.incrementAndGet(),
                request.name.trim(),
                request.type == null ? "General" : request.type,
                request.description == null ? "" : request.description,
                request.value,
                LocalDateTime.now()
        );
        assets.put(asset.id(), asset);
        persistData();
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<?> updateAsset(@PathVariable Long id, @RequestBody AssetRequest request) {
        AssetItem existing = assets.get(id);
        if (existing == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        if (request == null || request.name == null || request.name.isBlank() || request.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        AssetItem updated = new AssetItem(
                id,
                request.name.trim(),
                request.type == null ? existing.type() : request.type,
                request.description == null ? existing.description() : request.description,
                request.value,
                LocalDateTime.now()
        );
        assets.put(id, updated);
        persistData();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<?> deleteAsset(@PathVariable Long id) {
        if (!assets.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        assets.remove(id);
        persistData();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions(
            @RequestParam(required = false, name = "category_id") Long categoryIdFilter,
            @RequestParam(required = false, name = "type") String type,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        return transactions.values().stream()
                .filter(t -> categoryIdFilter == null || t.categoryId().equals(categoryIdFilter))
                .filter(t -> type == null || type.isBlank() || t.transactionType().equalsIgnoreCase(type))
                .sorted(Comparator.comparing(TransactionItem::date).reversed())
                .limit(limit)
                .map(this::toTransactionResponse)
                .toList();
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
        if (!categories.containsKey(request.category_id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }
        String normalizedType = request.transaction_type.toLowerCase(Locale.ROOT);
        if (!normalizedType.equals("income") && !normalizedType.equals("expense")) {
            return ResponseEntity.badRequest().body(Map.of("error", "transaction_type must be income or expense"));
        }

        LocalDateTime date = parseDate(request.date);
        TransactionItem item = new TransactionItem(
                transactionId.incrementAndGet(),
                request.amount,
                request.description.trim(),
                request.category_id,
                normalizedType,
                date,
                LocalDateTime.now()
        );
        transactions.put(item.id(), item);
        persistData();
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(item));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        if (!transactions.containsKey(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transaction not found"));
        }
        transactions.remove(id);
        persistData();
        return ResponseEntity.noContent().build();
    }

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
                "transaction_count", transactions.size()
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
        transactions.values().stream()
                .filter(t -> t.transactionType().equals("expense"))
                .forEach(t -> {
                    BreakdownAccumulator acc = grouped.computeIfAbsent(t.categoryId(), k -> new BreakdownAccumulator());
                    acc.total += t.amount();
                    acc.count += 1;
                });

        List<Map<String, Object>> response = new ArrayList<>();
        for (Map.Entry<Long, BreakdownAccumulator> entry : grouped.entrySet()) {
            Category category = categories.get(entry.getKey());
            if (category == null) {
                continue;
            }
            response.add(Map.of(
                    "category", category.name(),
                    "color", category.color(),
                    "total", entry.getValue().total,
                    "count", entry.getValue().count
            ));
        }
        return response;
    }

    @GetMapping("/analytics/monthly")
    public List<Map<String, Object>> monthly() {
        Map<YearMonth, Totals> grouped = new LinkedHashMap<>();
        for (TransactionItem t : transactions.values()) {
            YearMonth month = YearMonth.from(t.date());
            Totals totals = grouped.computeIfAbsent(month, m -> new Totals());
            if (t.transactionType().equals("income")) {
                totals.income += t.amount();
            } else {
                totals.expense += t.amount();
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

    private Category createCategoryInternal(CategoryRequest request) {
        Category category = new Category(
                categoryId.incrementAndGet(),
                request.name.trim(),
                request.description == null ? "" : request.description,
                request.color == null || request.color.isBlank() ? "#3498db" : request.color,
                request.icon == null || request.icon.isBlank() ? "tag" : request.icon
        );
        categories.put(category.id(), category);
        return category;
    }

    private Map<String, Object> toTransactionResponse(TransactionItem item) {
        Category category = categories.get(item.categoryId());
        return Map.of(
                "id", item.id(),
                "amount", item.amount(),
                "description", item.description(),
                "category_id", item.categoryId(),
                "category_name", category == null ? "Uncategorized" : category.name(),
                "transaction_type", item.transactionType(),
                "date", item.date().toString(),
                "created_at", item.createdAt().toString()
        );
    }

    private double getTotalIncome() {
        return transactions.values().stream()
                .filter(t -> t.transactionType().equals("income"))
                .mapToDouble(TransactionItem::amount)
                .sum();
    }

    private double getTotalExpense() {
        return transactions.values().stream()
                .filter(t -> t.transactionType().equals("expense"))
                .mapToDouble(TransactionItem::amount)
                .sum();
    }

    private double getTotalAssets() {
        return assets.values().stream()
                .mapToDouble(AssetItem::value)
                .sum();
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

    private void loadData() {
        if (Files.exists(DATA_PATH)) {
            try {
                JsonNode root = objectMapper.readTree(DATA_PATH.toFile());
                JsonNode categoryNodes = root.path("categories");
                if (categoryNodes.isArray()) {
                    for (JsonNode node : categoryNodes) {
                        Long id = node.path("id").asLong();
                        String name = node.path("name").asText("");
                        if (name.isBlank()) {
                            continue;
                        }
                        String description = node.path("description").asText("");
                        String color = node.path("color").asText("#3498db");
                        String icon = node.path("icon").asText("tag");
                        categories.put(id, new Category(id, name, description, color, icon));
                    }
                }

                JsonNode transactionNodes = root.path("transactions");
                if (transactionNodes.isArray()) {
                    for (JsonNode node : transactionNodes) {
                        Long id = node.path("id").asLong();
                        Double amount = node.path("amount").asDouble();
                        String description = node.path("description").asText("");
                        Long category = node.has("category_id") ? node.path("category_id").asLong() : node.path("categoryId").asLong();
                        String type = node.has("transaction_type") ? node.path("transaction_type").asText("") : node.path("transactionType").asText("");
                        String dateRaw = node.path("date").asText("");
                        String createdRaw = node.has("created_at") ? node.path("created_at").asText("") : node.path("createdAt").asText("");
                        if (description.isBlank() || type.isBlank()) {
                            continue;
                        }
                        transactions.put(id, new TransactionItem(
                                id,
                                amount,
                                description,
                                category,
                                type.toLowerCase(Locale.ROOT),
                                parseDate(dateRaw),
                                parseDate(createdRaw)
                        ));
                    }
                }
                JsonNode assetNodes = root.path("assets");
                if (assetNodes.isArray()) {
                    for (JsonNode node : assetNodes) {
                        Long id = node.path("id").asLong();
                        String name = node.path("name").asText("");
                        if (name.isBlank()) {
                            continue;
                        }
                        String type = node.path("type").asText("General");
                        String description = node.path("description").asText("");
                        Double value = node.path("value").asDouble();
                        String updatedAt = node.has("updated_at") ? node.path("updated_at").asText("") : node.path("updatedAt").asText("");
                        assets.put(id, new AssetItem(id, name, type, description, value, parseDate(updatedAt)));
                    }
                }
                categoryId.set(categories.keySet().stream().mapToLong(Long::longValue).max().orElse(0));
                transactionId.set(transactions.keySet().stream().mapToLong(Long::longValue).max().orElse(0));
                assetId.set(assets.keySet().stream().mapToLong(Long::longValue).max().orElse(0));
                if (!categories.isEmpty()) {
                    return;
                }
            } catch (IOException ignored) {
                categories.clear();
                transactions.clear();
                assets.clear();
            }
        }

        createCategoryInternal(new CategoryRequest("Groceries", "", "#27ae60", "tag"));
        createCategoryInternal(new CategoryRequest("Transport", "", "#3498db", "tag"));
        createCategoryInternal(new CategoryRequest("Salary", "", "#f39c12", "tag"));
        persistData();
    }

    private synchronized void persistData() {
        try {
            Files.createDirectories(DATA_PATH.getParent());
            DataStore data = new DataStore();
            data.categories = new ArrayList<>(categories.values());
            data.transactions = new ArrayList<>();
            for (TransactionItem t : transactions.values()) {
                data.transactions.add(new TransactionPersisted(
                        t.id(),
                        t.amount(),
                        t.description(),
                        t.categoryId(),
                        t.transactionType(),
                        t.date().toString(),
                        t.createdAt().toString()
                ));
            }
            data.assets = new ArrayList<>();
            for (AssetItem a : assets.values()) {
                data.assets.add(new AssetPersisted(
                        a.id(),
                        a.name(),
                        a.type(),
                        a.description(),
                        a.value(),
                        a.updatedAt().toString()
                ));
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(DATA_PATH.toFile(), data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist financial data", e);
        }
    }

    private static class BreakdownAccumulator {
        private double total;
        private int count;
    }

    private static class Totals {
        private double income;
        private double expense;
    }

    private record Category(Long id, String name, String description, String color, String icon) {
    }

    private record TransactionItem(
            Long id,
            Double amount,
            String description,
            Long categoryId,
            String transactionType,
            LocalDateTime date,
            LocalDateTime createdAt
    ) {
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

    private record AssetItem(
            Long id,
            String name,
            String type,
            String description,
            Double value,
            LocalDateTime updatedAt
    ) {
    }

    private record AssetRequest(
            String name,
            String type,
            String description,
            Double value
    ) {
    }

    private static class DataStore {
        public List<Category> categories;
        public List<TransactionPersisted> transactions;
        public List<AssetPersisted> assets;

        public DataStore() {
        }
    }

    private record TransactionPersisted(
            Long id,
            Double amount,
            String description,
            Long category_id,
            String transaction_type,
            String date,
            String created_at
    ) {
    }

    private record AssetPersisted(
            Long id,
            String name,
            String type,
            String description,
            Double value,
            String updated_at
    ) {
    }
}
