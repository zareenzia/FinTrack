package org.example.finzin.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.finzin.entity.AssetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.TransactionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class DataMigrationRunner implements ApplicationRunner {
    private static final Path DATA_PATH = Paths.get("data", "fintrack-data.json");
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final AssetRepository assetRepository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public DataMigrationRunner(CategoryRepository categoryRepository, TransactionRepository transactionRepository, AssetRepository assetRepository) {
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.assetRepository = assetRepository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (Files.exists(DATA_PATH) && categoryRepository.count() == 0) {
            System.out.println("🚀 Starting data migration from JSON to PostgreSQL...");
            migrateData();
            System.out.println("✅ Data migration completed successfully!");
        }
    }

    private void migrateData() throws Exception {
        JsonNode root = objectMapper.readTree(DATA_PATH.toFile());
        Map<Long, CategoryEntity> categoryMap = new HashMap<>();

        // Migrate categories
        JsonNode categoryNodes = root.path("categories");
        if (categoryNodes.isArray()) {
            for (JsonNode node : categoryNodes) {
                CategoryEntity entity = new CategoryEntity(
                        node.path("name").asText(""),
                        node.path("description").asText(""),
                        node.path("color").asText("#3498db"),
                        node.path("icon").asText("tag")
                );
                CategoryEntity saved = categoryRepository.save(entity);
                categoryMap.put(node.path("id").asLong(), saved);
                System.out.println("✓ Migrated category: " + saved.getName());
            }
        }

        // Migrate transactions
        JsonNode transactionNodes = root.path("transactions");
        if (transactionNodes.isArray()) {
            for (JsonNode node : transactionNodes) {
                Long oldCategoryId = node.path("category_id").asLong();
                CategoryEntity category = categoryMap.get(oldCategoryId);

                if (category != null) {
                    TransactionEntity entity = new TransactionEntity(
                            node.path("amount").asDouble(),
                            node.path("description").asText(""),
                            category,
                            node.path("transaction_type").asText("").toLowerCase(Locale.ROOT),
                            parseDate(node.path("date").asText("")),
                            parseDate(node.path("created_at").asText(""))
                    );
                    transactionRepository.save(entity);
                    System.out.println("✓ Migrated transaction: " + entity.getDescription());
                }
            }
        }

        // Migrate assets
        JsonNode assetNodes = root.path("assets");
        if (assetNodes.isArray()) {
            for (JsonNode node : assetNodes) {
                AssetEntity entity = new AssetEntity(
                        node.path("name").asText(""),
                        node.path("type").asText("General"),
                        node.path("description").asText(""),
                        node.path("value").asDouble(),
                        parseDate(node.path("created_at").asText(""))
                );
                assetRepository.save(entity);
                System.out.println("✓ Migrated asset: " + entity.getName());
            }
        }
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
}
