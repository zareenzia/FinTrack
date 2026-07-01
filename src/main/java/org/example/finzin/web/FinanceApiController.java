package org.example.finzin.web;

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

    public FinanceApiController(CategoryRepository categoryRepository, TransactionRepository transactionRepository, AssetRepository assetRepository, NoteRepository noteRepository, TodoRepository todoRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
        this.noteRepository = noteRepository;
        this.todoRepository = todoRepository;
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

    // ============== NOTE ENDPOINTS ==============
    @GetMapping("/notes")
    public List<Map<String, Object>> getNotes(@RequestParam(required = false) String search) {
        List<NoteEntity> notes = search != null && !search.isBlank() ? 
                noteRepository.searchNotes(search) : 
                noteRepository.findByArchivedFalseOrderByPinnedDescUpdatedAtDesc(false);
        
        return notes.stream().map(this::toNoteResponse).collect(Collectors.toList());
    }

    @PostMapping("/notes")
    public ResponseEntity<?> createNote(@RequestBody NoteRequest request) {
        if (request.title == null || request.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        NoteEntity entity = new NoteEntity();
        entity.setTitle(request.title.trim());
        entity.setContent(request.content != null ? request.content : "");
        entity.setColor(request.color != null ? request.color : "#FFE082");
        entity.setTags(request.tags != null ? request.tags : "");
        entity.setPinned(request.pinned != null ? request.pinned : false);
        entity.setArchived(false);

        NoteEntity saved = noteRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toNoteResponse(saved));
    }

    @PutMapping("/notes/{id}")
    public ResponseEntity<?> updateNote(@PathVariable Long id, @RequestBody NoteRequest request) {
        NoteEntity entity = noteRepository.findById(id).orElse(null);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.title != null && !request.title.isBlank()) {
            entity.setTitle(request.title.trim());
        }
        if (request.content != null) {
            entity.setContent(request.content);
        }
        if (request.color != null) {
            entity.setColor(request.color);
        }
        if (request.tags != null) {
            entity.setTags(request.tags);
        }
        if (request.pinned != null) {
            entity.setPinned(request.pinned);
        }
        if (request.archived != null) {
            entity.setArchived(request.archived);
        }

        NoteEntity updated = noteRepository.save(entity);
        return ResponseEntity.ok(toNoteResponse(updated));
    }

    @DeleteMapping("/notes/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable Long id) {
        if (!noteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        noteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============== TODO ENDPOINTS ==============
    @GetMapping("/todos")
    public List<Map<String, Object>> getTodos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority
    ) {
        List<TodoEntity> todos;

        if (search != null && !search.isBlank()) {
            todos = todoRepository.searchTodos(search);
        } else {
            todos = todoRepository.findActiveTodos();
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
    public ResponseEntity<?> createTodo(@RequestBody TodoRequest request) {
        if (request.title == null || request.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        TodoEntity entity = new TodoEntity();
        entity.setTitle(request.title.trim());
        entity.setDescription(request.description != null ? request.description : "");
        entity.setDueDate(request.dueDate);
        entity.setDueTime(request.dueTime);
        entity.setPriority(request.priority != null ? request.priority : "medium");
        entity.setCategory(request.category != null ? request.category : "");
        entity.setStatus(request.status != null ? request.status : "pending");
        entity.setCompleted(false);
        entity.setColor(request.color != null ? request.color : "#29B6F6");

        TodoEntity saved = todoRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toTodoResponse(saved));
    }

    @PutMapping("/todos/{id}")
    public ResponseEntity<?> updateTodo(@PathVariable Long id, @RequestBody TodoRequest request) {
        TodoEntity entity = todoRepository.findById(id).orElse(null);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }

        if (request.title != null && !request.title.isBlank()) {
            entity.setTitle(request.title.trim());
        }
        if (request.description != null) {
            entity.setDescription(request.description);
        }
        if (request.dueDate != null) {
            entity.setDueDate(request.dueDate);
        }
        if (request.dueTime != null) {
            entity.setDueTime(request.dueTime);
        }
        if (request.priority != null) {
            entity.setPriority(request.priority);
        }
        if (request.category != null) {
            entity.setCategory(request.category);
        }
        if (request.status != null) {
            entity.setStatus(request.status);
        }
        if (request.completed != null) {
            entity.setCompleted(request.completed);
            if (request.completed) {
                entity.setStatus("completed");
            }
        }
        if (request.color != null) {
            entity.setColor(request.color);
        }

        TodoEntity updated = todoRepository.save(entity);
        return ResponseEntity.ok(toTodoResponse(updated));
    }

    @DeleteMapping("/todos/{id}")
    public ResponseEntity<?> deleteTodo(@PathVariable Long id) {
        if (!todoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        todoRepository.deleteById(id);
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
        String preview = entity.getContent() != null && entity.getContent().length() > 100 ?
                entity.getContent().substring(0, 100) + "..." :
                (entity.getContent() != null ? entity.getContent() : "");

        return Map.of(
                "id", entity.getId(),
                "title", entity.getTitle(),
                "content", entity.getContent() != null ? entity.getContent() : "",
                "preview", preview,
                "color", entity.getColor() != null ? entity.getColor() : "#FFE082",
                "tags", entity.getTags() != null ? entity.getTags() : "",
                "pinned", entity.getPinned() != null ? entity.getPinned() : false,
                "archived", entity.getArchived() != null ? entity.getArchived() : false,
                "created_at", entity.getCreatedAt().toString(),
                "updated_at", entity.getUpdatedAt().toString()
        );
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
