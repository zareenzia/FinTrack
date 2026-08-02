package org.example.finzin.web;

import org.example.finzin.entity.InvestmentEntity;
import org.example.finzin.entity.LoanEntity;
import org.example.finzin.entity.PurchaseItemEntity;
import org.example.finzin.entity.SubscriptionEntity;
import org.example.finzin.repository.InvestmentRepository;
import org.example.finzin.repository.LoanRepository;
import org.example.finzin.repository.PurchaseItemRepository;
import org.example.finzin.repository.SubscriptionRepository;
import org.example.finzin.service.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialPlannerApiController.class)
class FinancialPlannerApiControllerTest {

    private static final Long USER_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private InvestmentRepository investmentRepository;

    @MockitoBean
    private LoanRepository loanRepository;

    @MockitoBean
    private SubscriptionRepository subscriptionRepository;

    @MockitoBean
    private PurchaseItemRepository purchaseItemRepository;

    // ── GET /api/financial-planner/summary ────────────────────────────────────

    @Test
    void getDashboardSummaryAggregatesAcrossAllModules() throws Exception {
        InvestmentEntity investment = new InvestmentEntity();
        investment.setId(1L);
        investment.setUserId(USER_ID);
        investment.setQuantity(10.0);
        investment.setCurrentPrice(120.0);
        investment.setPurchasePrice(100.0);
        when(investmentRepository.findByUserId(USER_ID)).thenReturn(List.of(investment));

        LoanEntity loan = new LoanEntity();
        loan.setId(1L);
        loan.setUserId(USER_ID);
        loan.setStatus("ACTIVE");
        loan.setRemainingBalance(5000.0);
        when(loanRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(loan));

        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setId(1L);
        sub.setUserId(USER_ID);
        sub.setStatus("ACTIVE");
        sub.setRenewalDate(LocalDate.now().plusDays(10));
        when(subscriptionRepository.findByUserIdAndStatus(USER_ID, "ACTIVE")).thenReturn(List.of(sub));

        PurchaseItemEntity item = new PurchaseItemEntity();
        item.setId(1L);
        item.setUserId(USER_ID);
        item.setEstimatedPrice(2000.0);
        item.setStatus("READY");
        when(purchaseItemRepository.findByUserIdAndStatusIn(USER_ID, List.of("PLANNING", "WAITING", "READY")))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/financial-planner/summary").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investmentValue").value(1200.0))
                .andExpect(jsonPath("$.investmentCount").value(1))
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andExpect(jsonPath("$.loanRemainingBalance").value(5000.0))
                .andExpect(jsonPath("$.activeSubscriptions").value(1))
                .andExpect(jsonPath("$.upcomingRenewals").value(1))
                .andExpect(jsonPath("$.wishlistValue").value(2000.0))
                .andExpect(jsonPath("$.wishlistCount").value(1))
                .andExpect(jsonPath("$.wishlistReadyCount").value(1));
    }

    // ── INVESTMENTS ────────────────────────────────────────────────────────────

    @Test
    void getInvestmentsReturnsMappedListWithComputedGains() throws Exception {
        InvestmentEntity investment = new InvestmentEntity();
        investment.setId(1L);
        investment.setUserId(USER_ID);
        investment.setName("Grameenphone Shares");
        investment.setInvestmentType("STOCKS");
        investment.setPurchaseDate(LocalDate.of(2025, 1, 1));
        investment.setQuantity(10.0);
        investment.setPurchasePrice(100.0);
        investment.setCurrentPrice(150.0);
        when(investmentRepository.findByUserId(USER_ID)).thenReturn(List.of(investment));

        mockMvc.perform(get("/api/financial-planner/investments").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Grameenphone Shares"))
                .andExpect(jsonPath("$[0].currentValue").value(1500.0))
                .andExpect(jsonPath("$[0].profitLoss").value(500.0))
                .andExpect(jsonPath("$[0].returnPercent").value(50.0));
    }

    @Test
    void createInvestmentReturnsCreatedOnSuccess() throws Exception {
        when(investmentRepository.save(any(InvestmentEntity.class))).thenAnswer(inv -> {
            InvestmentEntity e = inv.getArgument(0);
            e.setId(9L);
            return e;
        });

        mockMvc.perform(post("/api/financial-planner/investments")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grameenphone Shares\",\"investmentType\":\"STOCKS\",\"platform\":\"DSE\"," +
                                "\"purchaseDate\":\"2025-01-01\",\"quantity\":10,\"purchasePrice\":100,\"currentPrice\":150}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.name").value("Grameenphone Shares"));
    }

    @Test
    void createInvestmentReturnsBadRequestWhenPurchaseDateUnparseable() throws Exception {
        mockMvc.perform(post("/api/financial-planner/investments")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Date\",\"purchaseDate\":\"not-a-date\",\"quantity\":1,\"purchasePrice\":1,\"currentPrice\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateInvestmentReturns404WhenNotOwned() throws Exception {
        when(investmentRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/financial-planner/investments/1")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"purchaseDate\":\"2025-01-01\",\"quantity\":1,\"purchasePrice\":1,\"currentPrice\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInvestmentReturns404WhenOwnedByAnotherUser() throws Exception {
        InvestmentEntity owned = new InvestmentEntity();
        owned.setId(1L);
        owned.setUserId(999L);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(owned));

        mockMvc.perform(delete("/api/financial-planner/investments/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteInvestmentReturnsNoContentOnSuccess() throws Exception {
        InvestmentEntity owned = new InvestmentEntity();
        owned.setId(1L);
        owned.setUserId(USER_ID);
        when(investmentRepository.findById(1L)).thenReturn(Optional.of(owned));

        mockMvc.perform(delete("/api/financial-planner/investments/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNoContent());
    }

    // ── LOANS ──────────────────────────────────────────────────────────────────

    @Test
    void getLoansReturnsMappedListWithProgress() throws Exception {
        LoanEntity loan = new LoanEntity();
        loan.setId(1L);
        loan.setUserId(USER_ID);
        loan.setLoanName("Car Loan");
        loan.setLoanType("CAR");
        loan.setPrincipalAmount(100000.0);
        loan.setRemainingBalance(60000.0);
        loan.setLoanStartDate(LocalDate.of(2024, 1, 1));
        loan.setStatus("ACTIVE");
        when(loanRepository.findByUserId(USER_ID)).thenReturn(List.of(loan));

        mockMvc.perform(get("/api/financial-planner/loans").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].loanName").value("Car Loan"))
                .andExpect(jsonPath("$[0].paidAmount").value(40000.0))
                .andExpect(jsonPath("$[0].progressPercent").value(40.0));
    }

    @Test
    void payEmiReducesRemainingBalanceAndClosesLoanWhenFullyPaid() throws Exception {
        LoanEntity loan = new LoanEntity();
        loan.setId(1L);
        loan.setUserId(USER_ID);
        loan.setPrincipalAmount(1000.0);
        loan.setRemainingBalance(500.0);
        loan.setEmiAmount(500.0);
        loan.setStatus("ACTIVE");
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan));
        when(loanRepository.save(any(LoanEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/financial-planner/loans/1/pay-emi").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingBalance").value(0.0))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void payEmiReturns404WhenNotOwned() throws Exception {
        when(loanRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/financial-planner/loans/1/pay-emi").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }

    // ── SUBSCRIPTIONS ──────────────────────────────────────────────────────────

    @Test
    void getSubscriptionsComputesMonthlyAndYearlyCost() throws Exception {
        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setId(1L);
        sub.setUserId(USER_ID);
        sub.setName("Netflix");
        sub.setBillingCycle("YEARLY");
        sub.setCost(1200.0);
        sub.setAutoRenewal(true);
        sub.setStatus("ACTIVE");
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));

        mockMvc.perform(get("/api/financial-planner/subscriptions").requestAttr("userId", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Netflix"))
                .andExpect(jsonPath("$[0].monthlyCost").value(100.0))
                .andExpect(jsonPath("$[0].yearlyCost").value(1200.0));
    }

    @Test
    void createSubscriptionReturnsCreatedOnSuccess() throws Exception {
        when(subscriptionRepository.save(any(SubscriptionEntity.class))).thenAnswer(inv -> {
            SubscriptionEntity e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        mockMvc.perform(post("/api/financial-planner/subscriptions")
                        .requestAttr("userId", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Spotify\",\"billingCycle\":\"MONTHLY\",\"cost\":300}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Spotify"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteSubscriptionReturns404WhenNotOwned() throws Exception {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/financial-planner/subscriptions/1").requestAttr("userId", USER_ID))
                .andExpect(status().isNotFound());
    }
}
