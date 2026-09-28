package org.example.finzin.web;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.CategoryEntity;
import org.example.finzin.entity.RecurringTransactionEntity;
import org.example.finzin.entity.TransactionEntity;
import org.example.finzin.repository.CategoryRepository;
import org.example.finzin.service.AccountBalanceService;
import org.example.finzin.service.CreditCardValidationException;
import org.example.finzin.service.JwtTokenProvider;
import org.example.finzin.service.RecurringTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecurringTransactionApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class RecurringTransactionApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RecurringTransactionService recurringTransactionService;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private AccountBalanceService accountBalanceService;

    @MockitoBean
    private DocumentIndexer documentIndexer;

    private RecurringTransactionEntity recurring(Long id, String status, LocalDate nextExecutionDate) {
        RecurringTransactionEntity e = new RecurringTransactionEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setTransactionName("Netflix");
        e.setDescription("Streaming subscription");
        e.setTransactionType("expense");
        e.setCategoryId(3L);
        e.setAmount(500.0);
        e.setFrequency("MONTHLY");
        e.setIntervalValue(1);
        e.setStartDate(LocalDate.of(2026, 1, 1));
        e.setNextExecutionDate(nextExecutionDate);
        e.setStatus(status);
        return e;
    }

    // ── GET /api/recurring-transactions ───────────────────────────────────────

    @Test
    void getRecurringTransactionsReturnsMappedList() throws Exception {
        RecurringTransactionEntity entity = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 15));
        when(recurringTransactionService.getForUser(USER_ID)).thenReturn(List.of(entity));
        CategoryEntity category = new CategoryEntity(USER_ID, "Entertainment", "", "#fff", "tag");
        category.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));

        mockMvc.perform(get("/api/recurring-transactions").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].transactionName").value("Netflix"))
                .andExpect(jsonPath("$[0].categoryName").value("Entertainment"))
                .andExpect(jsonPath("$[0].amount").value(500.0))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    // ── GET /api/recurring-transactions/upcoming ──────────────────────────────

    @Test
    void getUpcomingPassesDaysParamThrough() throws Exception {
        when(recurringTransactionService.getUpcoming(USER_ID, 7)).thenReturn(List.of());

        mockMvc.perform(get("/api/recurring-transactions/upcoming").requestAttr("userId", USER_ID).param("days", "7"))
                .andExpect(status().isOk());

        verify(recurringTransactionService).getUpcoming(USER_ID, 7);
    }

    @Test
    void getUpcomingDefaultsToThreeDays() throws Exception {
        when(recurringTransactionService.getUpcoming(USER_ID, 3)).thenReturn(List.of());

        mockMvc.perform(get("/api/recurring-transactions/upcoming").requestAttr("userId", USER_ID))
                .andExpect(status().isOk());

        verify(recurringTransactionService).getUpcoming(USER_ID, 3);
    }

    // ── GET /api/recurring-transactions/pending ───────────────────────────────

    @Test
    void getPendingReturnsOnlyActiveAndDueEntries() throws Exception {
        LocalDate today = LocalDate.now();
        RecurringTransactionEntity due = recurring(1L, "ACTIVE", today.minusDays(1));
        RecurringTransactionEntity notYetDue = recurring(2L, "ACTIVE", today.plusDays(5));
        RecurringTransactionEntity paused = recurring(3L, "PAUSED", today.minusDays(1));
        when(recurringTransactionService.getForUser(USER_ID)).thenReturn(List.of(due, notYetDue, paused));

        mockMvc.perform(get("/api/recurring-transactions/pending").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ── POST /api/recurring-transactions ──────────────────────────────────────

    @Test
    void createReturnsBadRequestWhenBodyMissing() throws Exception {
        // A literal JSON "null" body never reaches the controller: Spring rejects a null-deserialized
        // non-optional @RequestBody with HttpMessageNotReadableException before the handler method
        // runs, so the response is a framework-generated 400 with no body (not the controller's
        // "Missing request body" JSON) and the service is never invoked.
        mockMvc.perform(post("/api/recurring-transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recurringTransactionService);
    }

    @Test
    void createReturnsBadRequestWhenValidationFails() throws Exception {
        when(recurringTransactionService.validate(eq(USER_ID), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("A positive amount is required");

        mockMvc.perform(post("/api/recurring-transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":-5," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A positive amount is required"));
    }

    @Test
    void createReturnsCreatedOnSuccess() throws Exception {
        when(recurringTransactionService.validate(eq(USER_ID), eq("Netflix"), eq("expense"), eq(500.0), eq(3L),
                eq("MONTHLY"), eq(1), eq(LocalDate.of(2026, 8, 1)))).thenReturn(null);
        when(recurringTransactionService.save(any(RecurringTransactionEntity.class))).thenAnswer(inv -> {
            RecurringTransactionEntity e = inv.getArgument(0);
            e.setId(10L);
            return e;
        });

        mockMvc.perform(post("/api/recurring-transactions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":500.0," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.transactionName").value("Netflix"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextExecutionDate").value("2026-08-01"));
    }

    // ── PUT /api/recurring-transactions/{id} ──────────────────────────────────

    @Test
    void updateReturns404WhenNotOwned() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(put("/api/recurring-transactions/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":500.0," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRejectsNextExecutionDateInThePast() throws Exception {
        RecurringTransactionEntity existing = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 15));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(existing);
        when(recurringTransactionService.validate(eq(USER_ID), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(put("/api/recurring-transactions/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":500.0," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"," +
                                "\"nextExecutionDate\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Next Run cannot be set to a date in the past."));
    }

    @Test
    void updateRejectsNextExecutionDateNotAfterLastExecutionDate() throws Exception {
        // Both dates are relative to "today" so this assertion never goes flaky no matter when the
        // suite actually runs: nextExecutionDate is in the future (passes the "not in the past"
        // check) but still not after lastExecutionDate (must still be rejected).
        LocalDate lastExecutionDate = LocalDate.now().plusDays(20);
        LocalDate candidateNextExecutionDate = LocalDate.now().plusDays(5);
        RecurringTransactionEntity existing = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 15));
        existing.setLastExecutionDate(lastExecutionDate);
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(existing);
        when(recurringTransactionService.validate(eq(USER_ID), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(put("/api/recurring-transactions/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":500.0," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"," +
                                "\"nextExecutionDate\":\"" + candidateNextExecutionDate + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Next Run must be after the last executed date (" + lastExecutionDate + ")."));
    }

    @Test
    void updateAppliesExplicitNextExecutionDateWhenValid() throws Exception {
        RecurringTransactionEntity existing = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 15));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(existing);
        when(recurringTransactionService.validate(eq(USER_ID), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(recurringTransactionService.save(any(RecurringTransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate farFuture = LocalDate.now().plusMonths(2);
        mockMvc.perform(put("/api/recurring-transactions/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionName\":\"Netflix\",\"transactionType\":\"expense\",\"amount\":500.0," +
                                "\"categoryId\":3,\"frequency\":\"MONTHLY\",\"intervalValue\":1,\"startDate\":\"2026-08-01\"," +
                                "\"nextExecutionDate\":\"" + farFuture + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextExecutionDate").value(farFuture.toString()));

        verify(recurringTransactionService, never()).recalculateNextExecutionDateForEdit(any(), any(), anyInt(), any());
    }

    // ── PATCH /api/recurring-transactions/{id}/status ─────────────────────────

    @Test
    void updateStatusReturns404WhenNotOwned() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(patch("/api/recurring-transactions/1/status")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatusReturnsBadRequestForInvalidStatus() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurring(1L, "ACTIVE", LocalDate.now()));
        when(recurringTransactionService.isValidStatus("BOGUS")).thenReturn(false);

        mockMvc.perform(patch("/api/recurring-transactions/1/status")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("status must be ACTIVE, PAUSED, or COMPLETED"));
    }

    @Test
    void updateStatusResumingFromPausedSkipsToNextOccurrenceOnOrAfterToday() throws Exception {
        RecurringTransactionEntity paused = recurring(1L, "PAUSED", LocalDate.now().minusMonths(3));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(paused);
        // The controller validates the raw request value before uppercasing it (uppercasing happens
        // only after this check passes), so the stub must match the lowercase value actually sent.
        when(recurringTransactionService.isValidStatus("active")).thenReturn(true);
        LocalDate resumedNext = LocalDate.now().plusDays(2);
        when(recurringTransactionService.nextOccurrenceOnOrAfterToday(paused)).thenReturn(resumedNext);
        when(recurringTransactionService.save(any(RecurringTransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(patch("/api/recurring-transactions/1/status")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextExecutionDate").value(resumedNext.toString()));
    }

    // ── POST /api/recurring-transactions/{id}/confirm ─────────────────────────

    @Test
    void confirmReturns404WhenNotOwned() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/api/recurring-transactions/1/confirm").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmReturnsBadRequestWhenAlreadyCompleted() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurring(1L, "COMPLETED", LocalDate.now()));

        mockMvc.perform(post("/api/recurring-transactions/1/confirm").requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This recurring transaction has already completed."));
    }

    @Test
    void confirmCreatesTransactionAndAdvancesSchedule() throws Exception {
        RecurringTransactionEntity recurringEntity = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurringEntity);
        CategoryEntity category = new CategoryEntity(USER_ID, "Entertainment", "", "#fff", "tag");
        category.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));

        TransactionEntity savedTransaction = new TransactionEntity(USER_ID, 500.0, "Netflix", category, "expense",
                LocalDate.of(2026, 8, 1).atStartOfDay(), java.time.LocalDateTime.now());
        savedTransaction.setId(555L);
        when(accountBalanceService.createTransaction(eq(USER_ID), any(TransactionEntity.class)))
                .thenReturn(new AccountBalanceService.TransactionSaveResult(savedTransaction, null));
        when(recurringTransactionService.save(any(RecurringTransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/recurring-transactions/1/confirm")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(555))
                .andExpect(jsonPath("$.recurringTransaction.id").value(1))
                .andExpect(jsonPath("$.recurringTransaction.nextExecutionDate").value("2026-09-01"));

        verify(documentIndexer).indexTransaction(savedTransaction);
    }

    @Test
    void confirmReturnsBadRequestWhenCreditCardValidationFails() throws Exception {
        RecurringTransactionEntity recurringEntity = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurringEntity);
        CategoryEntity category = new CategoryEntity(USER_ID, "Entertainment", "", "#fff", "tag");
        category.setId(3L);
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(category));
        when(accountBalanceService.createTransaction(eq(USER_ID), any(TransactionEntity.class)))
                .thenThrow(new CreditCardValidationException("This purchase exceeds your available credit limit."));

        mockMvc.perform(post("/api/recurring-transactions/1/confirm")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("This purchase exceeds your available credit limit."));
    }

    // ── POST /api/recurring-transactions/{id}/skip ────────────────────────────

    @Test
    void skipReturns404WhenNotOwned() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/api/recurring-transactions/1/skip").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void skipAdvancesScheduleWithoutCreatingATransaction() throws Exception {
        RecurringTransactionEntity recurringEntity = recurring(1L, "ACTIVE", LocalDate.of(2026, 8, 1));
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurringEntity);
        when(recurringTransactionService.save(any(RecurringTransactionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/recurring-transactions/1/skip").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextExecutionDate").value("2026-09-01"));

        verify(accountBalanceService, never()).createTransaction(any(), any());
    }

    // ── DELETE /api/recurring-transactions/{id} ───────────────────────────────

    @Test
    void deleteReturns404WhenNotOwned() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(null);

        mockMvc.perform(delete("/api/recurring-transactions/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());

        verify(recurringTransactionService, never()).delete(any());
    }

    @Test
    void deleteReturnsNoContentOnSuccess() throws Exception {
        when(recurringTransactionService.findOwnedById(1L, USER_ID)).thenReturn(recurring(1L, "ACTIVE", LocalDate.now()));

        mockMvc.perform(delete("/api/recurring-transactions/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(recurringTransactionService).delete(1L);
    }
}
