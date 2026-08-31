package org.example.finzin.web;

import org.example.finzin.ai.rag.DocumentIndexer;
import org.example.finzin.entity.AccountEntity;
import org.example.finzin.repository.AccountRepository;
import org.example.finzin.repository.TransactionRepository;
import org.example.finzin.service.CreditCardService;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.example.finzin.config.JwtAuthFilter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountApiController.class, excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
class AccountApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AccountRepository accountRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @MockitoBean
    private DocumentIndexer documentIndexer;

    @MockitoBean
    private CreditCardService creditCardService;

    private AccountEntity account(Long id, String type, double currentBalance) {
        AccountEntity e = new AccountEntity();
        e.setId(id);
        e.setUserId(USER_ID);
        e.setAccountType(type);
        e.setAccountNickname("My " + type);
        e.setOpeningBalance(1000.0);
        e.setCurrentBalance(currentBalance);
        e.setStatus("ACTIVE");
        e.setCreditLimitBehavior("WARN");
        e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return e;
    }

    // ── GET /api/accounts ──────────────────────────────────────────────────────

    @Test
    void getAccountsReturnsMappedListForUser() throws Exception {
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(account(1L, "BANK", 5000.0)));

        mockMvc.perform(get("/api/accounts").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].accountType").value("BANK"))
                .andExpect(jsonPath("$[0].currentBalance").value(5000.0))
                .andExpect(jsonPath("$[0].availableCredit").doesNotExist());
    }

    @Test
    void getAccountsIncludesCreditCardStatsForCreditCardAccounts() throws Exception {
        AccountEntity card = account(2L, "CREDIT_CARD", 3000.0);
        when(accountRepository.findByUserId(USER_ID)).thenReturn(List.of(card));
        when(creditCardService.getStats(card)).thenReturn(
                new CreditCardService.CreditCardStats(7000.0, 30.0, 500.0, 15));

        mockMvc.perform(get("/api/accounts").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableCredit").value(7000.0))
                .andExpect(jsonPath("$[0].utilizationPercent").value(30.0))
                .andExpect(jsonPath("$[0].minimumPaymentEstimate").value(500.0))
                .andExpect(jsonPath("$[0].daysUntilDue").value(15));
    }

    // ── GET /api/accounts/summary ──────────────────────────────────────────────

    @Test
    void getSummaryAggregatesBalancesByAccountType() throws Exception {
        when(accountRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(
                account(1L, "BANK", 5000.0), account(2L, "CASH", 200.0), account(3L, "CREDIT_CARD", 1000.0)));

        mockMvc.perform(get("/api/accounts/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBank").value(5000.0))
                .andExpect(jsonPath("$.totalCash").value(200.0))
                .andExpect(jsonPath("$.totalCreditOutstanding").value(1000.0))
                .andExpect(jsonPath("$.totalAvailable").value(5200.0));
    }

    // ── POST /api/accounts ─────────────────────────────────────────────────────

    @Test
    void createAccountReturnsBadRequestWhenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("accountType and accountNickname are required"));
    }

    @Test
    void createAccountReturnsCreatedOnSuccess() throws Exception {
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> {
            AccountEntity e = inv.getArgument(0);
            e.setId(10L);
            e.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            e.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            return e;
        });

        mockMvc.perform(post("/api/accounts")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"BANK\",\"accountNickname\":\"Salary Account\",\"openingBalance\":2000.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.accountNickname").value("Salary Account"))
                .andExpect(jsonPath("$.currentBalance").value(2000.0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(documentIndexer).indexAccount(any(AccountEntity.class));
    }

    // ── PUT /api/accounts/{id} ─────────────────────────────────────────────────

    @Test
    void updateAccountReturns404WhenNotOwned() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/accounts/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"BANK\",\"accountNickname\":\"X\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Account not found"));
    }

    @Test
    void updateAccountReturnsBadRequestWhenRequiredFieldsMissing() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "BANK", 5000.0)));

        mockMvc.perform(put("/api/accounts/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("accountType and accountNickname are required"));
    }

    @Test
    void updateAccountShiftsOpeningBalanceWhenCurrentBalanceIsDirectlyCorrected() throws Exception {
        AccountEntity existing = account(1L, "BANK", 5000.0); // openingBalance=1000, currentBalance=5000
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/accounts/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"BANK\",\"accountNickname\":\"Salary\",\"currentBalance\":6000.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance").value(6000.0))
                // openingBalance shifts by the same +1000 delta so history stays consistent.
                .andExpect(jsonPath("$.openingBalance").value(2000.0));
    }

    @Test
    void updateAccountReturns404WhenOwnedByAnotherUser() throws Exception {
        AccountEntity other = account(1L, "BANK", 5000.0);
        other.setUserId(999L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(other));

        mockMvc.perform(put("/api/accounts/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"BANK\",\"accountNickname\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/accounts/{id} ──────────────────────────────────────────────

    @Test
    void deleteAccountReturns404WhenNotOwned() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/accounts/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteAccountReturnsBadRequestWhenTransactionsExist() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "BANK", 5000.0)));
        when(transactionRepository.existsBySourceAccountIdOrDestinationAccountId(1L, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/accounts/1").requestAttr("userId", USER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Cannot delete account with associated transactions"));

        verify(accountRepository, never()).deleteById(any());
    }

    @Test
    void deleteAccountReturnsNoContentOnSuccess() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "BANK", 5000.0)));
        when(transactionRepository.existsBySourceAccountIdOrDestinationAccountId(1L, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/accounts/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());

        verify(accountRepository).deleteById(1L);
        verify(documentIndexer).deleteAccount(USER_ID, 1L);
    }

    // ── PATCH /api/accounts/{id}/status ───────────────────────────────────────

    @Test
    void toggleStatusReturns404WhenNotOwned() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/accounts/1/status").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void toggleStatusFlipsActiveToInactive() throws Exception {
        AccountEntity existing = account(1L, "BANK", 5000.0);
        existing.setStatus("ACTIVE");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(existing));

        mockMvc.perform(patch("/api/accounts/1/status").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── GET /api/accounts/{id}/ledger ──────────────────────────────────────────

    @Test
    void getLedgerReturns404WhenNotOwned() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/accounts/1/ledger").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLedgerReturnsEntriesFromCreditCardService() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "BANK", 5000.0)));
        when(creditCardService.getLedger(eq(USER_ID), eq(1L), eq(null), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(List.of(new CreditCardService.LedgerEntry("2026-07-01", "Salary", null, "income", 5000.0, 5000.0)));

        mockMvc.perform(get("/api/accounts/1/ledger").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-07-01"))
                .andExpect(jsonPath("$[0].description").value("Salary"))
                .andExpect(jsonPath("$[0].amount").value(5000.0));
    }

    @Test
    void getLedgerParsesDateRangeParams() throws Exception {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account(1L, "BANK", 5000.0)));
        when(creditCardService.getLedger(eq(USER_ID), eq(1L), any(), any(), eq("Food"), eq("expense"), eq("KFC")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/accounts/1/ledger")
                        .requestAttr("userId", USER_ID)
                        .param("startDate", "2026-07-01")
                        .param("endDate", "2026-07-31")
                        .param("category", "Food")
                        .param("type", "expense")
                        .param("merchant", "KFC"))
                .andExpect(status().isOk());

        verify(creditCardService).getLedger(USER_ID, 1L,
                java.time.LocalDate.of(2026, 7, 1), java.time.LocalDate.of(2026, 7, 31), "Food", "expense", "KFC");
    }
}
