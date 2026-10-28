package org.example.finzin.web;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.entity.AssetEntity;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.NoteEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.gamification.GamificationEvent;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.AssetRepository;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.repository.NoteRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.repository.UserRepository;
import org.example.finzin.service.AccountBalanceService;
import org.example.finzin.service.CreditCardValidationException;
import org.example.finzin.service.FinancialSummaryService;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FinanceApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = org.example.finzin.config.JwtAuthFilter.class))
@RecordApplicationEvents
class FinanceApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents applicationEvents;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private AssetRepository assetRepository;

    @MockitoBean
    private NoteRepository noteRepository;

    @MockitoBean
    private FinancialSummaryService financialSummaryService;

    @MockitoBean
    private DocumentIndexer documentIndexer;

    @MockitoBean
    private AccountBalanceService accountBalanceService;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private UserRepository userRepository;

    private CategoryEntity category(Long id, String categoryType) {
        CategoryEntity c = new CategoryEntity(USER_ID, "Food", "desc", "#3498db", "tag");
        c.setId(id);
        c.setCategoryType(categoryType);
        return c;
    }

    private AccountEntity accountEntity(Long id, String type) {
        AccountEntity a = new AccountEntity();
        a.setId(id);
        a.setUserId(USER_ID);
        a.setAccountType(type);
        a.setAccountNickname("Nickname" + id);
        a.setOpeningBalance(0.0);
        a.setCurrentBalance(0.0);
        a.setStatus("ACTIVE");
        a.setCreditLimitBehavior("WARN");
        return a;
    }

    private TransactionEntity transaction(Long id, double amount, String txType, CategoryEntity category,
                                          LocalDateTime date, Long sourceAccountId, Long destinationAccountId) {
        TransactionEntity tx = new TransactionEntity(USER_ID, amount, "Tx" + id, category, txType, date, date);
        tx.setId(id);
        tx.setSourceAccountId(sourceAccountId);
        tx.setDestinationAccountId(destinationAccountId);
        return tx;
    }

    // ══════════════════ CATEGORY ══════════════════

    @Test
    void getCategoriesReturnsMappedListWithTransactionCounts() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(cat));
        TransactionEntity tx = new TransactionEntity(USER_ID, 100.0, "desc", cat, "expense", LocalDateTime.now(), LocalDateTime.now());
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/categories").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Food"))
                .andExpect(jsonPath("$[0].categoryType").value("expense"))
                .andExpect(jsonPath("$[0].transactionCount").value(1));
    }

    @Test
    void getCategoriesFiltersByTypeWhenProvided() throws Exception {
        when(categoryRepository.findByUserIdAndCategoryTypeOrGeneral(USER_ID, "income")).thenReturn(List.of());
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/categories").requestAttr("userId", USER_ID).param("type", "income"))
                .andExpect(status().isOk());

        verify(categoryRepository).findByUserIdAndCategoryTypeOrGeneral(USER_ID, "income");
    }

    @Test
    void createCategoryReturnsBadRequestWhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Category name is required"));
    }

    @Test
    void createCategoryReturnsBadRequestWhenNameAlreadyExists() throws Exception {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Food")).thenReturn(true);

        mockMvc.perform(post("/api/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Food\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Category name already exists"));
    }

    @Test
    void createCategoryHandlesDataIntegrityViolationAsCleanBadRequest() throws Exception {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Food")).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenThrow(new DataIntegrityViolationException("dup"));

        mockMvc.perform(post("/api/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Food\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Category name already exists"));
    }

    @Test
    void createCategoryReturnsCreatedOnSuccess() throws Exception {
        when(categoryRepository.existsByUserIdAndNameIgnoreCase(USER_ID, "Food")).thenReturn(false);
        when(categoryRepository.save(any(CategoryEntity.class))).thenAnswer(inv -> {
            CategoryEntity c = inv.getArgument(0);
            c.setId(3L);
            return c;
        });

        mockMvc.perform(post("/api/categories")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Food\",\"color\":\"#ff0000\",\"icon\":\"utensils\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    void updateCategoryReturns404WhenNotOwned() throws Exception {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/categories/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCategoryReturnsBadRequestWhenDuplicateNameAmongOthers() throws Exception {
        CategoryEntity existing = category(1L, "expense");
        CategoryEntity another = category(2L, "expense");
        another.setName("Transport");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(existing, another));

        mockMvc.perform(put("/api/categories/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Transport\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Category name already exists"));
    }

    @Test
    void deleteCategoryReturns404WhenNotOwned() throws Exception {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/categories/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategoryReturnsNoContentOnSuccess() throws Exception {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category(1L, "expense")));

        mockMvc.perform(delete("/api/categories/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(categoryRepository).deleteById(1L);
    }

    // ══════════════════ ASSET ══════════════════

    @Test
    void createAssetReturnsBadRequestWhenMissingFields() throws Exception {
        mockMvc.perform(post("/api/assets")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Asset name and value are required"));
    }

    @Test
    void createAssetReturnsCreatedOnSuccess() throws Exception {
        when(assetRepository.save(any(AssetEntity.class))).thenAnswer(inv -> {
            AssetEntity a = inv.getArgument(0);
            a.setId(4L);
            return a;
        });

        mockMvc.perform(post("/api/assets")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Car\",\"value\":500000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.name").value("Car"))
                .andExpect(jsonPath("$.value").value(500000.0));
    }

    @Test
    void updateAssetReturns404WhenNotOwned() throws Exception {
        when(assetRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/assets/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Car\",\"value\":1000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAssetReturnsNoContentOnSuccess() throws Exception {
        AssetEntity existing = new AssetEntity(USER_ID, "Car", "Vehicle", "", 500000.0, LocalDateTime.now());
        existing.setId(1L);
        when(assetRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/assets/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(assetRepository).deleteById(1L);
    }

    // ══════════════════ TRANSACTION ══════════════════

    @Test
    void getTransactionsReturnsMappedListWithAccountNames() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        AccountEntity account = accountEntity(5L, "BANK");
        TransactionEntity tx = new TransactionEntity(USER_ID, 250.0, "Lunch", cat, "expense",
                LocalDateTime.of(2026, 7, 1, 12, 0), LocalDateTime.of(2026, 7, 1, 12, 0));
        tx.setId(10L);
        tx.setSourceAccountId(5L);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(tx));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(account));

        mockMvc.perform(get("/api/transactions").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].amount").value(250.0))
                .andExpect(jsonPath("$[0].category_name").value("Food"))
                .andExpect(jsonPath("$[0].account_name").value("Nickname5"));
    }

    @Test
    void createTransactionReturnsBadRequestWhenMissingFields() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields"));
    }

    @Test
    void createTransactionReturnsBadRequestForInvalidType() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"X\",\"amount\":10,\"transaction_type\":\"bogus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("transaction_type must be income, expense, savings, or transfer"));
    }

    @Test
    void createTransactionRejectsTransferWithNoAccountsAtAll() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Move\",\"amount\":10,\"transaction_type\":\"transfer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A transfer needs at least one tracked account — select a From or To account."));
    }

    @Test
    void createTransactionRejectsMissingCategoryForNonTransfer() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Lunch\",\"amount\":10,\"transaction_type\":\"expense\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing required fields"));
    }

    @Test
    void createTransactionRejectsInvalidCategoryOwnership() throws Exception {
        CategoryEntity foreign = category(1L, "expense");
        foreign.setUserId(999L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(foreign));

        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Lunch\",\"amount\":10,\"transaction_type\":\"expense\",\"category_id\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid category"));
    }

    @Test
    void createTransactionRejectsExpenseWithNonCreditCardDestination() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        AccountEntity bankAccount = accountEntity(2L, "BANK");
        when(accountRepository.findById(2L)).thenReturn(Optional.of(bankAccount));

        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Bill\",\"amount\":10,\"transaction_type\":\"expense\"," +
                                "\"category_id\":1,\"destinationAccountId\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The linked account for a bill payment must be one of your credit cards."));
    }

    @Test
    void createTransactionReturnsCreatedOnSuccessWithWarning() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of());

        TransactionEntity saved = new TransactionEntity(USER_ID, 500.0, "Groceries", cat, "expense",
                LocalDateTime.of(2026, 7, 1, 10, 0), LocalDateTime.now());
        saved.setId(20L);
        when(accountBalanceService.createTransaction(eq(USER_ID), any(TransactionEntity.class)))
                .thenReturn(new AccountBalanceService.TransactionSaveResult(saved, "This purchase exceeds your available credit limit."));

        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Groceries\",\"amount\":500,\"transaction_type\":\"expense\"," +
                                "\"category_id\":1,\"date\":\"2026-07-01T10:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.warning").value("This purchase exceeds your available credit limit."));

        verify(documentIndexer).indexTransaction(saved);
    }

    @Test
    void createTransactionReturnsBadRequestWhenCreditCardValidationThrows() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));
        when(accountBalanceService.createTransaction(eq(USER_ID), any(TransactionEntity.class)))
                .thenThrow(new CreditCardValidationException("Payment exceeds current outstanding balance."));

        mockMvc.perform(post("/api/transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Groceries\",\"amount\":500,\"transaction_type\":\"expense\",\"category_id\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Payment exceeds current outstanding balance."));
    }

    @Test
    void updateTransactionReturns404WhenNotOwned() throws Exception {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/transactions/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"X\",\"amount\":10,\"transaction_type\":\"expense\",\"category_id\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Transaction not found"));
    }

    @Test
    void deleteTransactionReturns404WhenNotOwned() throws Exception {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/transactions/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTransactionReturnsNoContentOnSuccess() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        TransactionEntity existing = new TransactionEntity(USER_ID, 100.0, "X", cat, "expense", LocalDateTime.now(), LocalDateTime.now());
        existing.setId(1L);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/transactions/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(accountBalanceService).deleteTransaction(USER_ID, existing);
        verify(documentIndexer).deleteTransaction(USER_ID, 1L);
    }

    // ══════════════════ NOTE ══════════════════

    @Test
    void createNoteReturnsBadRequestWhenTitleMissing() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Title is required"));
    }

    @Test
    void createNoteReturnsCreatedAndPublishesGamificationEvent() throws Exception {
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(inv -> {
            NoteEntity n = inv.getArgument(0);
            n.setId(7L);
            n.setCreatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            n.setUpdatedAt(LocalDateTime.of(2026, 7, 1, 0, 0));
            return n;
        });

        mockMvc.perform(post("/api/notes")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Groceries\",\"content\":\"Buy milk\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Groceries"))
                .andExpect(jsonPath("$.preview").value("Buy milk"));

        // ApplicationEventPublisher can't be @MockitoBean'd here: Spring registers it as a resolvable
        // dependency bound directly to the real ApplicationContext, bypassing normal bean-type lookup,
        // so the controller always gets the real publisher regardless of any mock bean of that type.
        // ApplicationEvents (enabled via @RecordApplicationEvents) is the supported way to assert an
        // event was actually published.
        assertEquals(1, applicationEvents.stream(GamificationEvent.class).count());
    }

    @Test
    void updateNoteReturns404WhenNotOwned() throws Exception {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/notes/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateNotePartiallyAppliesOnlyProvidedFields() throws Exception {
        NoteEntity existing = new NoteEntity();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        existing.setTitle("Old Title");
        existing.setContent("Old content");
        existing.setColor("#FFE082");
        existing.setPinned(false);
        existing.setArchived(false);
        existing.setDone(false);
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(NoteEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/notes/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Old Title"))
                .andExpect(jsonPath("$.pinned").value(true));
    }

    @Test
    void deleteNoteReturns404WhenNotOwned() throws Exception {
        when(noteRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/notes/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNoteReturnsNoContentOnSuccess() throws Exception {
        NoteEntity existing = new NoteEntity();
        existing.setId(1L);
        existing.setUserId(USER_ID);
        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(delete("/api/notes/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(noteRepository).deleteById(1L);
        verify(documentIndexer).deleteNote(USER_ID, 1L);
    }

    // ══════════════════ ANALYTICS ══════════════════

    @Test
    void summaryReturnsAggregatedFinancialTotals() throws Exception {
        when(financialSummaryService.getTotalIncome(USER_ID)).thenReturn(10000.0);
        when(financialSummaryService.getTotalExpense(USER_ID)).thenReturn(4000.0);
        when(financialSummaryService.getTotalSavings(USER_ID)).thenReturn(2000.0);
        when(financialSummaryService.getBalance(USER_ID)).thenReturn(4000.0);
        when(financialSummaryService.getTotalAssets(USER_ID)).thenReturn(50000.0);
        when(financialSummaryService.getSavingsRate(USER_ID)).thenReturn(20.0);
        when(financialSummaryService.getNetWorth(USER_ID)).thenReturn(56000.0);
        when(transactionRepository.countByUserId(USER_ID)).thenReturn(15L);

        mockMvc.perform(get("/api/analytics/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_income").value(10000.0))
                .andExpect(jsonPath("$.total_expense").value(4000.0))
                .andExpect(jsonPath("$.total_savings").value(2000.0))
                .andExpect(jsonPath("$.balance").value(4000.0))
                .andExpect(jsonPath("$.savings_rate").value(20.0))
                .andExpect(jsonPath("$.total_assets").value(50000.0))
                .andExpect(jsonPath("$.net_worth").value(56000.0))
                .andExpect(jsonPath("$.transaction_count").value(15));
    }

    @Test
    void categoryBreakdownGroupsByCategoryForGivenType() throws Exception {
        CategoryEntity cat = category(1L, "expense");
        TransactionEntity tx = new TransactionEntity(USER_ID, 300.0, "Lunch", cat, "expense", LocalDateTime.now(), LocalDateTime.now());
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(tx));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(cat));

        mockMvc.perform(get("/api/analytics/category-breakdown").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Food"))
                .andExpect(jsonPath("$[0].total").value(300.0))
                .andExpect(jsonPath("$[0].count").value(1));
    }

    @Test
    void monthlyGroupsTotalsByMonthAndType() throws Exception {
        CategoryEntity cat = category(1L, "income");
        TransactionEntity tx = new TransactionEntity(USER_ID, 1000.0, "Salary", cat, "income",
                LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.now());
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/analytics/monthly").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'income' && @.month == '2026-06')].total").value(1000.0));
    }

    @Test
    void creditCardSpendingIncludesCreditCardExpenseAndExcludesNonCreditCardAndTransferAndBillPayment() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        CategoryEntity shopping = category(2L, "expense");
        shopping.setName("Shopping");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food, shopping));

        AccountEntity card = accountEntity(101L, "CREDIT_CARD");
        AccountEntity bank = accountEntity(201L, "BANK");
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(card, bank));

        TransactionEntity creditCardPurchase = transaction(1L, 2000.0, "expense", food, LocalDateTime.of(2026, 8, 10, 10, 0), 101L, null);
        TransactionEntity bankExpense = transaction(2L, 3000.0, "expense", shopping, LocalDateTime.of(2026, 8, 11, 10, 0), 201L, null);
        TransactionEntity creditCardBillPaymentAsExpense = transaction(3L, 1000.0, "expense", shopping, LocalDateTime.of(2026, 8, 12, 10, 0), 201L, 101L);
        TransactionEntity transfer = transaction(4L, 500.0, "transfer", food, LocalDateTime.of(2026, 8, 13, 10, 0), 101L, 201L);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(
                creditCardPurchase, bankExpense, creditCardBillPaymentAsExpense, transfer
        ));

        mockMvc.perform(get("/api/analytics/credit-card-spending").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(2000.0))
                .andExpect(jsonPath("$.breakdown.length()").value(1))
                .andExpect(jsonPath("$.breakdown[0].category").value("Food"))
                .andExpect(jsonPath("$.breakdown[0].amount").value(2000.0));
    }

    @Test
    void creditCardSpendingAppliesRefundAsReduction() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(accountEntity(101L, "CREDIT_CARD")));

        TransactionEntity purchase = transaction(1L, 2000.0, "expense", food, LocalDateTime.of(2026, 8, 10, 10, 0), 101L, null);
        TransactionEntity refund = transaction(2L, 500.0, "income", food, LocalDateTime.of(2026, 8, 11, 10, 0), 101L, null);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(purchase, refund));

        mockMvc.perform(get("/api/analytics/credit-card-spending").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(1500.0))
                .andExpect(jsonPath("$.breakdown[0].amount").value(1500.0));
    }

    @Test
    void creditCardSpendingAggregatesAcrossMultipleCards() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(
                accountEntity(101L, "CREDIT_CARD"),
                accountEntity(102L, "CREDIT_CARD")
        ));

        TransactionEntity card1 = transaction(1L, 1200.0, "expense", food, LocalDateTime.of(2026, 8, 10, 10, 0), 101L, null);
        TransactionEntity card2 = transaction(2L, 800.0, "expense", food, LocalDateTime.of(2026, 8, 11, 10, 0), 102L, null);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(card1, card2));

        mockMvc.perform(get("/api/analytics/credit-card-spending").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(2000.0))
                .andExpect(jsonPath("$.breakdown[0].amount").value(2000.0));
    }

    @Test
    void creditCardSpendingFiltersBySingleCreditCardAndCategoryTogether() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        CategoryEntity shopping = category(2L, "expense");
        shopping.setName("Shopping");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food, shopping));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(
                accountEntity(101L, "CREDIT_CARD"),
                accountEntity(102L, "CREDIT_CARD")
        ));

        TransactionEntity card1Food = transaction(1L, 1500.0, "expense", food, LocalDateTime.of(2026, 8, 10, 10, 0), 101L, null);
        TransactionEntity card1Shopping = transaction(2L, 700.0, "expense", shopping, LocalDateTime.of(2026, 8, 11, 10, 0), 101L, null);
        TransactionEntity card2Food = transaction(3L, 900.0, "expense", food, LocalDateTime.of(2026, 8, 12, 10, 0), 102L, null);
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of(card1Food, card1Shopping, card2Food));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("accountId", "101")
                        .param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(1500.0))
                .andExpect(jsonPath("$.breakdown.length()").value(1))
                .andExpect(jsonPath("$.breakdown[0].category").value("Food"))
                .andExpect(jsonPath("$.breakdown[0].amount").value(1500.0));
    }

    @Test
    void creditCardSpendingUsesDateRangeAndRepositoryRangeQueryWhenBothDatesProvided() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(accountEntity(101L, "CREDIT_CARD")));

        TransactionEntity august = transaction(1L, 700.0, "expense", food, LocalDateTime.of(2026, 8, 15, 10, 0), 101L, null);
        when(transactionRepository.findByUserIdAndDateRange(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(august));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(700.0))
                .andExpect(jsonPath("$.breakdown[0].amount").value(700.0));

        verify(transactionRepository).findByUserIdAndDateRange(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(transactionRepository, never()).findByUserId(USER_ID);
    }

    @Test
    void creditCardSpendingRejectsForeignAccountAndForeignCategoryFilters() throws Exception {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(accountEntity(101L, "CREDIT_CARD")));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(category(1L, "expense")));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("accountId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid credit card account."));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("categoryId", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid category."));
    }

    @Test
    void creditCardSpendingReturnsEmptyBreakdownWhenNoCreditCardSpending() throws Exception {
        CategoryEntity food = category(1L, "expense");
        food.setName("Food");
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(food));
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(accountEntity(101L, "CREDIT_CARD")));
        when(transactionRepository.findByUserId(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/credit-card-spending").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpending").value(0.0))
                .andExpect(jsonPath("$.topCategory").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.breakdown.length()").value(0));
    }

    @Test
    void creditCardSpendingRejectsInvalidDateParameters() throws Exception {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(accountEntity(101L, "CREDIT_CARD")));
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(category(1L, "expense")));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("startDate", "2026-08-99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid startDate. Use YYYY-MM-DD."));

        mockMvc.perform(get("/api/analytics/credit-card-spending")
                        .requestAttr("userId", USER_ID)
                        .param("startDate", "2026-08-20")
                        .param("endDate", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("endDate must be on or after startDate."));
    }
}
