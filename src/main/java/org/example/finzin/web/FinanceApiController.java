package org.example.finzin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.example.finzin.entity.AssetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.NoteEntity;
import org.example.finzin.entity.TodoEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.NoteRepository;
import org.example.finzin.repository.TodoRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.gold.GoldAssetService;
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
    private final NoteRepository noteRepository;
    private final TodoRepository todoRepository;
    private final GoldAssetService goldAssetService;

    public FinanceApiController(CategoryRepository categoryRepository, TransactionRepository transactionRepository, AssetRepository assetRepository, NoteRepository noteRepository, TodoRepository todoRepository, GoldAssetService goldAssetService) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.noteRepository = noteRepository;
        this.todoRepository = todoRepository;
        this.goldAssetService = goldAssetService;
    }
    
    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? (Long) userId : 1L; // Default to Leah (user 1) if not authenticated
    }

    // ============== CATEGORY ENDPOINTS ==============
    @GetMapping("/categories")
    public List<Map<String, Object>> getCategories(
            HttpServletRequest request,
            @RequestParam(required = false, name = "type") String type) {
        Long userId = getUserId(request);
        List<CategoryEntity> categories = (type != null && !type.isBlank())
                ? categoryRepository.findByUserIdAndCategoryTypeOrGeneral(userId, type.toLowerCase(Locale.ROOT))
                : categoryRepository.findByUserId(userId);

        // Build transaction-count map in a single pass
        Map<Long, Long> txCounts = transactionRepository.findByUserId(userId).stream()
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(), Collectors.counting()));

        return categories.stream()
                .sorted(Comparator.comparingLong(CategoryEntity::getId))
                .map(cat -> {
                    Map<String, Object> resp = new LinkedHashMap<>(toCategoryResponse(cat));
                    resp.put("transactionCount", txCounts.getOrDefault(cat.getId(), 0L));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(HttpServletRequest request, @RequestBody CategoryRequest body) {
        Long userId = getUserId(request);
        
        if (body == null || body.name == null || body.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, body.name.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        CategoryEntity entity = new CategoryEntity(
                userId,
                body.name.trim(),
                body.description == null ? "" : body.description,
                body.color == null || body.color.isBlank() ? "#3498db" : body.color,
                body.icon == null || body.icon.isBlank() ? "tag" : body.icon
        );
        if (body.categoryType != null && !body.categoryType.isBlank()) {
            entity.setCategoryType(body.categoryType.toLowerCase(Locale.ROOT));
        }
        CategoryEntity saved = categoryRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toCategoryResponse(saved));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<?> updateCategory(HttpServletRequest request, @PathVariable Long id, @RequestBody CategoryRequest body) {
        Long userId = getUserId(request);
        CategoryEntity existing = categoryRepository.findById(id).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        if (body == null || body.name == null || body.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name is required"));
        }

        String updatedName = body.name.trim();
        boolean duplicate = categoryRepository.findByUserId(userId).stream()
                .anyMatch(c -> !c.getId().equals(id) && c.getName().equalsIgnoreCase(updatedName));
        if (duplicate) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category name already exists"));
        }

        existing.setName(updatedName);
        if (body.description != null) {
            existing.setDescription(body.description);
        }
        if (body.color != null && !body.color.isBlank()) {
            existing.setColor(body.color);
        }
        if (body.icon != null && !body.icon.isBlank()) {
            existing.setIcon(body.icon);
        }
        if (body.categoryType != null && !body.categoryType.isBlank()) {
            existing.setCategoryType(body.categoryType.toLowerCase(Locale.ROOT));
        }
        CategoryEntity updated = categoryRepository.save(existing);
        return ResponseEntity.ok(toCategoryResponse(updated));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        CategoryEntity entity = categoryRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Category not found"));
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== ASSET ENDPOINTS ==============
    @GetMapping("/assets")
    public List<Map<String, Object>> getAssets(HttpServletRequest request) {
        Long userId = getUserId(request);
        return assetRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparingLong(AssetEntity::getId))
                .map(this::toAssetResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/assets")
    public ResponseEntity<?> createAsset(HttpServletRequest request, @RequestBody AssetRequest body) {
        Long userId = getUserId(request);
        
        if (body == null || body.name == null || body.name.isBlank() || body.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        AssetEntity entity = new AssetEntity(
                userId,
                body.name.trim(),
                body.type == null ? "General" : body.type,
                body.description == null ? "" : body.description,
                body.value,
                LocalDateTime.now()
        );
        AssetEntity saved = assetRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssetResponse(saved));
    }

    @PutMapping("/assets/{id}")
    public ResponseEntity<?> updateAsset(HttpServletRequest request, @PathVariable Long id, @RequestBody AssetRequest body) {
        Long userId = getUserId(request);
        AssetEntity existing = assetRepository.findById(id).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        if (body == null || body.name == null || body.name.isBlank() || body.value == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Asset name and value are required"));
        }

        existing.setName(body.name.trim());
        if (body.type != null) {
            existing.setType(body.type);
        }
        if (body.description != null) {
            existing.setDescription(body.description);
        }
        existing.setValue(body.value);
        existing.setCreatedAt(LocalDateTime.now());
        AssetEntity updated = assetRepository.save(existing);
        return ResponseEntity.ok(toAssetResponse(updated));
    }

    @DeleteMapping("/assets/{id}")
    public ResponseEntity<?> deleteAsset(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        AssetEntity entity = assetRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Asset not found"));
        }
        assetRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== TRANSACTION ENDPOINTS ==============
    @GetMapping("/transactions")
    public List<Map<String, Object>> getTransactions(
            HttpServletRequest request,
            @RequestParam(required = false, name = "category_id") Long categoryIdFilter,
            @RequestParam(required = false, name = "type") String type,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        Long userId = getUserId(request);
        List<TransactionEntity> transactions = transactionRepository.findByUserId(userId);
        
        return transactions.stream()
                .filter(t -> categoryIdFilter == null || t.getCategory().getId().equals(categoryIdFilter))
                .filter(t -> type == null || type.isBlank() || t.getTransactionType().equalsIgnoreCase(type))
                .sorted(Comparator.comparing(TransactionEntity::getDate).reversed())
                .limit(limit)
                .map(this::toTransactionResponse)
                .collect(Collectors.toList());
    }

    @PostMapping("/transactions")
    public ResponseEntity<?> createTransaction(HttpServletRequest request, @RequestBody TransactionRequest body) {
        Long userId = getUserId(request);
        
        if (body == null
                || body.description == null
                || body.description.isBlank()
                || body.amount == null
                || body.category_id == null
                || body.transaction_type == null
                || body.transaction_type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        CategoryEntity category = categoryRepository.findById(body.category_id).orElse(null);
        if (category == null || !category.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }

        String normalizedType = body.transaction_type.toLowerCase(Locale.ROOT);
        if (!normalizedType.equals("income") && !normalizedType.equals("expense") && !normalizedType.equals("savings")) {
            return ResponseEntity.badRequest().body(Map.of("error", "transaction_type must be income, expense, or savings"));
        }

        LocalDateTime date = parseDate(body.date);
        TransactionEntity entity = new TransactionEntity(
                userId,
                body.amount,
                body.description.trim(),
                category,
                normalizedType,
                date,
                LocalDateTime.now()
        );
        TransactionEntity saved = transactionRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTransactionResponse(saved));
    }

    @PutMapping("/transactions/{id}")
    public ResponseEntity<?> updateTransaction(HttpServletRequest request, @PathVariable Long id, @RequestBody TransactionRequest body) {
        Long userId = getUserId(request);
        TransactionEntity entity = transactionRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transaction not found"));
        }

        if (body == null
                || body.description == null || body.description.isBlank()
                || body.amount == null
                || body.category_id == null
                || body.transaction_type == null || body.transaction_type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        CategoryEntity category = categoryRepository.findById(body.category_id).orElse(null);
        if (category == null || !category.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid category"));
        }

        String normalizedType = body.transaction_type.toLowerCase(Locale.ROOT);
        if (!normalizedType.equals("income") && !normalizedType.equals("expense") && !normalizedType.equals("savings")) {
            return ResponseEntity.badRequest().body(Map.of("error", "transaction_type must be income, expense, or savings"));
        }

        entity.setDescription(body.description.trim());
        entity.setAmount(body.amount);
        entity.setCategory(category);
        entity.setTransactionType(normalizedType);
        if (body.date != null && !body.date.isBlank()) {
            entity.setDate(parseDate(body.date));
        }
        TransactionEntity saved = transactionRepository.save(entity);
        return ResponseEntity.ok(toTransactionResponse(saved));
    }

    @DeleteMapping("/transactions/{id}")
    public ResponseEntity<?> deleteTransaction(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        TransactionEntity entity = transactionRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Transaction not found"));
        }
        transactionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== NOTE ENDPOINTS ==============
    @GetMapping("/notes")
    public List<Map<String, Object>> getNotes(HttpServletRequest request, @RequestParam(required = false) String search) {
        Long userId = getUserId(request);
        List<NoteEntity> notes = search != null && !search.isBlank() ? 
                noteRepository.searchByUserIdAndContent(userId, search) : 
                noteRepository.findByUserIdAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(userId);
        
        return notes.stream().map(this::toNoteResponse).collect(Collectors.toList());
    }

    @PostMapping("/notes")
    public ResponseEntity<?> createNote(HttpServletRequest request, @RequestBody NoteRequest body) {
        Long userId = getUserId(request);
        
        if (body.title == null || body.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        NoteEntity entity = new NoteEntity();
        entity.setUserId(userId);
        entity.setTitle(body.title.trim());
        entity.setContent(body.content != null ? body.content : "");
        entity.setColor(body.color != null ? body.color : "#FFE082");
        entity.setTags(body.tags != null ? body.tags : "");
        entity.setPinned(body.pinned != null ? body.pinned : false);
        entity.setArchived(false);

        NoteEntity saved = noteRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNoteResponse(saved));
    }

    @PutMapping("/notes/{id}")
    public ResponseEntity<?> updateNote(HttpServletRequest request, @PathVariable Long id, @RequestBody NoteRequest body) {
        Long userId = getUserId(request);
        NoteEntity entity = noteRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        if (body.title != null && !body.title.isBlank()) {
            entity.setTitle(body.title.trim());
        }
        if (body.content != null) {
            entity.setContent(body.content);
        }
        if (body.color != null) {
            entity.setColor(body.color);
        }
        if (body.tags != null) {
            entity.setTags(body.tags);
        }
        if (body.pinned != null) {
            entity.setPinned(body.pinned);
        }
        if (body.archived != null) {
            entity.setArchived(body.archived);
        }

        NoteEntity updated = noteRepository.save(entity);
        return ResponseEntity.ok(toNoteResponse(updated));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<?> deleteNote(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        NoteEntity entity = noteRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        noteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== TODO ENDPOINTS ==============
    @GetMapping("/todos")
    public List<Map<String, Object>> getTodos(
            HttpServletRequest request,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority
    ) {
        Long userId = getUserId(request);
        List<TodoEntity> todos;

        if (search != null && !search.isBlank()) {
            todos = todoRepository.searchByUserIdAndTitle(userId, search);
        } else {
            todos = todoRepository.findByUserIdAndCompletedFalse(userId);
        }

        if (status != null && !status.isBlank()) {
            todos = todos.stream().filter(t -> t.getStatus().equalsIgnoreCase(status)).collect(Collectors.toList());
        }

        if (priority != null && !priority.isBlank()) {
            todos = todos.stream().filter(t -> t.getPriority().equalsIgnoreCase(priority)).collect(Collectors.toList());
        }

        return todos.stream().map(this::toTodoResponse).collect(Collectors.toList());
    }

    @PostMapping("/todos")
    public ResponseEntity<?> createTodo(HttpServletRequest request, @RequestBody TodoRequest body) {
        Long userId = getUserId(request);
        
        if (body.title == null || body.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        TodoEntity entity = new TodoEntity();
        entity.setUserId(userId);
        entity.setTitle(body.title.trim());
        entity.setDescription(body.description != null ? body.description : "");
        entity.setDueDate(body.dueDate);
        entity.setDueTime(body.dueTime);
        entity.setPriority(body.priority != null ? body.priority : "medium");
        entity.setCategory(body.category != null ? body.category : "");
        entity.setStatus(body.status != null ? body.status : "pending");
        entity.setCompleted(false);
        entity.setColor(body.color != null ? body.color : "#29B6F6");

        TodoEntity saved = todoRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTodoResponse(saved));
    }

    @PutMapping("/todos/{id}")
    public ResponseEntity<?> updateTodo(HttpServletRequest request, @PathVariable Long id, @RequestBody TodoRequest body) {
        Long userId = getUserId(request);
        TodoEntity entity = todoRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        if (body.title != null && !body.title.isBlank()) {
            entity.setTitle(body.title.trim());
        }
        if (body.description != null) {
            entity.setDescription(body.description);
        }
        if (body.dueDate != null) {
            entity.setDueDate(body.dueDate);
        }
        if (body.dueTime != null) {
            entity.setDueTime(body.dueTime);
        }
        if (body.priority != null) {
            entity.setPriority(body.priority);
        }
        if (body.category != null) {
            entity.setCategory(body.category);
        }
        if (body.status != null) {
            entity.setStatus(body.status);
        }
        if (body.completed != null) {
            entity.setCompleted(body.completed);
            if (body.completed) {
                entity.setStatus("completed");
            }
        }
        if (body.color != null) {
            entity.setColor(body.color);
        }

        TodoEntity updated = todoRepository.save(entity);
        return ResponseEntity.ok(toTodoResponse(updated));
    }

    @DeleteMapping("/todos/{id}")
    public ResponseEntity<?> deleteTodo(HttpServletRequest request, @PathVariable Long id) {
        Long userId = getUserId(request);
        TodoEntity entity = todoRepository.findById(id).orElse(null);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        todoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== ANALYTICS ENDPOINTS ==============
    @GetMapping("/analytics/summary")
    public Map<String, Object> summary(HttpServletRequest request) {
        Long userId = getUserId(request);
        double income       = getTotalIncome(userId);
        double expense      = getTotalExpense(userId);
        double savingsTx    = getTotalSavings(userId);
        double balance      = income - expense - savingsTx;          // available balance
        double totalAssets  = getTotalAssets(userId);
        double savingsRate  = income == 0 ? 0 : (savingsTx / income) * 100;
        double netWorth     = balance + savingsTx + totalAssets;

        return Map.of(
                "total_income",   income,
                "total_expense",  expense,
                "total_savings",  savingsTx,
                "balance",        balance,
                "savings_rate",   savingsRate,
                "total_assets",   totalAssets,
                "net_worth",      netWorth,
                "transaction_count", transactionRepository.countByUserId(userId)
        );
    }

    @GetMapping("/analytics/category-breakdown")
    public List<Map<String, Object>> categoryBreakdown(
            HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "expense") String type) {
        Long userId = getUserId(request);
        String filterType = type.toLowerCase(Locale.ROOT);
        Map<Long, BreakdownAccumulator> grouped = new LinkedHashMap<>();
        transactionRepository.findByUserId(userId).stream()
                .filter(t -> t.getTransactionType().equals(filterType))
                .forEach(t -> {
                    BreakdownAccumulator acc = grouped.computeIfAbsent(t.getCategory().getId(), k -> new BreakdownAccumulator());
                    acc.total += t.getAmount();
                    acc.count += 1;
                });

        List<Map<String, Object>> response = new ArrayList<>();
        for (Map.Entry<Long, BreakdownAccumulator> entry : grouped.entrySet()) {
            CategoryEntity category = categoryRepository.findById(entry.getKey()).orElse(null);
            if (category == null) continue;
            response.add(Map.of(
                    "category", category.getName(),
                    "color",    category.getColor(),
                    "total",    entry.getValue().total,
                    "count",    entry.getValue().count
            ));
        }
        return response;
    }

    @GetMapping("/analytics/monthly")
    public List<Map<String, Object>> monthly(HttpServletRequest request) {
        Long userId = getUserId(request);
        Map<YearMonth, Totals> grouped = new LinkedHashMap<>();
        for (TransactionEntity t : transactionRepository.findByUserId(userId)) {
            YearMonth month = YearMonth.from(t.getDate());
            Totals totals = grouped.computeIfAbsent(month, m -> new Totals());
            switch (t.getTransactionType()) {
                case "income"  -> totals.income  += t.getAmount();
                case "expense" -> totals.expense += t.getAmount();
                case "savings" -> totals.savings += t.getAmount();
            }
        }

        List<Map<String, Object>> response = new ArrayList<>();
        grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String month = entry.getKey().toString();
                    response.add(Map.of("month", month, "type", "income",  "total", entry.getValue().income));
                    response.add(Map.of("month", month, "type", "expense", "total", entry.getValue().expense));
                    response.add(Map.of("month", month, "type", "savings", "total", entry.getValue().savings));
                });
        return response;
    }

    // ============== HELPER METHODS ==============
    private Map<String, Object> toCategoryResponse(CategoryEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",           entity.getId());
        map.put("name",         entity.getName());
        map.put("description",  entity.getDescription());
        map.put("color",        entity.getColor());
        map.put("icon",         entity.getIcon());
        map.put("categoryType", entity.getCategoryType() != null ? entity.getCategoryType() : "general");
        return map;
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


    private double getTotalIncome(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "income");
        return sum == null ? 0 : sum;
    }

    private double getTotalExpense(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "expense");
        return sum == null ? 0 : sum;
    }

    private double getTotalSavings(Long userId) {
        Double sum = transactionRepository.sumByUserIdAndTransactionType(userId, "savings");
        return sum == null ? 0 : sum;
    }

    private double getTotalAssets(Long userId) {
        Double sum = assetRepository.sumValuesByUserId(userId);
        double regularAssets = sum == null ? 0 : sum;
        double goldAssets = goldAssetService.getTotalGoldValueForUser(userId);
        return regularAssets + goldAssets;
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
        private double savings;
    }

    private record CategoryRequest(String name, String description, String color, String icon, String categoryType) {
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

    private record NoteRequest(
            String title,
            String content,
            String color,
            String tags,
            Boolean pinned,
            Boolean archived
    ) {
    }

    private record TodoRequest(
            String title,
            String description,
            java.time.LocalDate dueDate,
            String dueTime,
            String priority,
            String category,
            String status,
            Boolean completed,
            String color
    ) {
    }

    private Map<String, Object> toNoteResponse(NoteEntity entity) {
        String plain = stripNoteHtml(entity.getContent());
        String preview = plain.length() > 150 ? plain.substring(0, 150) + "…" : plain;

        return Map.of(
                "id", entity.getId(),
                "title", entity.getTitle(),
                "content", entity.getContent() != null ? entity.getContent() : "",
                "preview", preview,
                "color", entity.getColor() != null ? entity.getColor() : "#FEF3C7",
                "tags", entity.getTags() != null ? entity.getTags() : "",
                "pinned", entity.getPinned() != null ? entity.getPinned() : false,
                "archived", entity.getArchived() != null ? entity.getArchived() : false,
                "created_at", entity.getCreatedAt().toString(),
                "updated_at", entity.getUpdatedAt().toString()
        );
    }

    /** Strip HTML tags and decode common entities for plain-text preview. */
    private String stripNoteHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html
                .replaceAll("<[^>]*>", " ")          // remove all tags
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#039;", "'")
                .replaceAll("\\s{2,}", " ")           // collapse whitespace
                .trim();
    }

    private Map<String, Object> toTodoResponse(TodoEntity entity) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", entity.getId());
        response.put("title", entity.getTitle());
        response.put("description", entity.getDescription() != null ? entity.getDescription() : "");
        response.put("due_date", entity.getDueDate() != null ? entity.getDueDate().toString() : "");
        response.put("due_time", entity.getDueTime() != null ? entity.getDueTime() : "");
        response.put("priority", entity.getPriority());
        response.put("category", entity.getCategory() != null ? entity.getCategory() : "");
        response.put("status", entity.getStatus());
        response.put("completed", entity.getCompleted() != null ? entity.getCompleted() : false);
        response.put("color", entity.getColor() != null ? entity.getColor() : "#29B6F6");
        response.put("created_at", entity.getCreatedAt().toString());
        response.put("updated_at", entity.getUpdatedAt().toString());
        return response;
    }
}
