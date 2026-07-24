function getUserStorageKey(baseKey) {
        try {
            var raw = localStorage.getItem('finzin_user') || localStorage.getItem('user');
            var u = JSON.parse(raw);
            if (u && u.id) return baseKey + '_' + u.id;
        } catch(e) {}
        return baseKey;
    }
    let filteredTransactions = [];
    let currentPage        = 0;
    let pageSize           = parseInt(localStorage.getItem('tx_page_limit') || '5', 10);
    let txFilterCategory   = '';
    let txFilterType       = '';
    let txFilterAccount    = ''; // '', 'SAVINGS', 'NONE', or an account id (string)
    let txFilterSearch     = '';
    let cachedAccounts     = [];
    let txDeleteId         = null;
    let txDeleteModal      = null;
    let txEditId           = null;
    let txEditModal        = null;

    let categoryChartData    = [];
    let monthlyChartData     = [];
    let categoryChartType    = 'pie';
    let monthlyChartType     = 'line';
    let categoryBreakdownType = 'expense';   // 'expense' | 'savings'

    let allTransactions      = [];
    let selectedMonthKey     = null;   // 'YYYY-MM' or 'ALL'
    let receiptsByTransactionId = {};
    let receiptDetailModal   = null;
    let rdCurrentReceiptId   = null;

    /** 'YYYY-MM' key for a date string/object */
    function monthKeyOf(dateVal) {
        const d = new Date(dateVal);
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0');
    }

    /** 'YYYY-MM' key for right now */
    function currentMonthKey() {
        return monthKeyOf(new Date());
    }

    /** Human label for a 'YYYY-MM' key, e.g. "July 2026" */
    function monthKeyLabel(key, short) {
        const [y, m] = key.split('-');
        const d = new Date(Number(y), Number(m) - 1, 1);
        return d.toLocaleDateString('en-US', { month: short ? 'short' : 'long', year: 'numeric' });
    }

    /** Read currency settings from localStorage — defaults match settings.html DEFAULTS */
    const CURRENCY_DEFAULTS = { currencySymbol:'৳', symbolPosition:'before', decimalPlaces:0 };
    function getCurrencySettings() {
        try {
            var s = Object.assign({}, CURRENCY_DEFAULTS, JSON.parse(localStorage.getItem(getUserStorageKey('fintrack_settings')) || '{}'));
            return {
                symbol:   s.currencySymbol   || '৳',
                position: s.symbolPosition   || 'before',
                decimals: Number(s.decimalPlaces) >= 0 ? Number(s.decimalPlaces) : 0
            };
        } catch(e) { return { symbol: '৳', position: 'before', decimals: 0 }; }
    }

    /** Format a number as a currency string using saved settings */
    function formatAmount(value) {
        var c = getCurrencySettings();
        var num = Number(value).toFixed(c.decimals);
        return c.position === 'before' ? c.symbol + num : num + '\u00a0' + c.symbol;
    }
    let categoryColorMap      = {};

    /** Shrink .stat-card-amount font until text fits on one line */
    function fitStatAmounts() {
        document.querySelectorAll('.stat-card-amount').forEach(function (el) {
            const panel = el.closest('.dashboard-view-panel');
            if (panel && !panel.classList.contains('active')) return; // hidden panel — skip, sized 0
            el.style.fontSize = '';
            const cardBody = el.closest('.card-body');
            if (!cardBody) return;
            const maxW = cardBody.clientWidth - 48;
            let fs = parseFloat(window.getComputedStyle(el).fontSize);
            let safety = 0;
            while (el.scrollWidth > maxW && fs > 12 && safety++ < 25) {
                fs -= 1;
                el.style.fontSize = fs + 'px';
            }
        });
    }

    /** Toggle individual card value visibility — JS text swap only */
    function toggleCardValues(btn) {
        var amount = btn.closest('.stat-card').querySelector('.stat-card-amount');
        var icon   = btn.querySelector('i');
        if (amount.dataset.hidden === 'true') {
            amount.textContent    = amount.dataset.realValue || amount.textContent;
            amount.dataset.hidden = 'false';
            icon.className = 'fas fa-eye';
        } else {
            amount.dataset.realValue = amount.textContent;
            amount.textContent    = '•••••••';
            amount.dataset.hidden = 'true';
            icon.className = 'fas fa-eye-slash';
        }
    }

    /** Apply hide-values setting to all stat cards on load */
    function applyCardHideSetting() {
        try {
            var s = JSON.parse(localStorage.getItem(getUserStorageKey('fintrack_settings')) || '{}');
            if (s.hideValues) {
                document.querySelectorAll('.stat-card').forEach(function(card) {
                    var amount = card.querySelector('.stat-card-amount');
                    var btn    = card.querySelector('.sc-eye-btn i');
                    if (amount && amount.dataset.hidden !== 'true') {
                        amount.dataset.realValue = amount.textContent;
                        amount.textContent    = '•••••••';
                        amount.dataset.hidden = 'true';
                        if (btn) btn.className = 'fas fa-eye-slash';
                    }
                });
            }
        } catch(e) {}
    }

    function setEl(id, val) {
        var el = document.getElementById(id);
        if (el) el.textContent = val;
    }
    function escHtml(s) {
        return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }
    function fmt(v) {
        return Number(v||0).toLocaleString('en-BD', {minimumFractionDigits:0, maximumFractionDigits:2});
    }
    /* ── Dashboard view mode (Default / Compact) ───────────────────────────── */
    let currentViewMode = 'default'; // 'default' | 'compact'
    function activeSuffix() { return currentViewMode === 'compact' ? 'Compact' : ''; }

    function setDashboardView(mode, skipSave) {
        currentViewMode = mode === 'compact' ? 'compact' : 'default';
        const defPanel = document.getElementById('dashboardDefaultView');
        const cmpPanel = document.getElementById('dashboardCompactView');
        if (defPanel) defPanel.classList.toggle('active', currentViewMode === 'default');
        if (cmpPanel) cmpPanel.classList.toggle('active', currentViewMode === 'compact');
        const defBtn = document.getElementById('viewModeDefaultBtn');
        const cmpBtn = document.getElementById('viewModeCompactBtn');
        if (defBtn) defBtn.classList.toggle('active', currentViewMode === 'default');
        if (cmpBtn) cmpBtn.classList.toggle('active', currentViewMode === 'compact');

        if (!skipSave) {
            try { localStorage.setItem(getUserStorageKey('fintrack_dashboard_view'), currentViewMode); } catch(e) {}
        }

        // Charts need to be (re)drawn for the panel that just became visible —
        // canvases inside a display:none panel render at 0 size, so redraw fresh.
        if (categoryChartData.length) drawCategoryChart(categoryChartData, activeSuffix());
        if (monthlyChartData.length)  drawMonthlyChart(monthlyChartData, activeSuffix());
        fitStatAmounts();
        applyCardHideSetting();
    }

    function accountIcon(type) {
        var m = {BANK:'🏦',DEBIT_CARD:'💳',CREDIT_CARD:'💳',MFS:'📱',CASH:'💵'};
        return m[type] || '💰';
    }

    /** Populates the Account Balances card in BOTH the default and compact panels. */
    async function loadAccountBalances() {
        try {
            var token = localStorage.getItem('token');

            var summaryResp = await fetch('/api/accounts/summary', {
                headers: { 'Authorization': 'Bearer ' + (token || '') }
            });
            if (summaryResp.ok) {
                var s = await summaryResp.json();
                ['', 'Compact'].forEach(function (suf) {
                    setEl('dashboardSummaryBank' + suf, '৳' + fmt(s.totalBank));
                    setEl('dashboardSummaryMfs' + suf, '৳' + fmt(s.totalMfs));
                    setEl('dashboardSummaryCash' + suf, '৳' + fmt(s.totalCash));
                    setEl('dashboardSummaryCreditOutstanding' + suf, '৳' + fmt(s.totalCreditOutstanding));
                    setEl('dashboardSummaryAvailable' + suf, '৳' + fmt(s.totalAvailable));
                });
            }

            var acctResp = await fetch('/api/accounts', {
                headers: { 'Authorization': 'Bearer ' + (token || '') }
            });
            if (acctResp.ok) {
                var accounts = await acctResp.json() || [];
                var active = accounts.filter(function(a) { return a.status === 'ACTIVE'; });

                function fillBreakdown(elId, accts, isCredit) {
                    var el = document.getElementById(elId);
                    if (!el) return;
                    if (accts.length === 0) { el.innerHTML = ''; return; }
                    el.innerHTML = accts.map(function(a) {
                        var name = escHtml(a.accountNickname);
                        var bal = fmt(a.currentBalance);
                        var cls = isCredit ? 'bal-credit' : '';
                        return '<div class="acct-breakdown-row">' +
                            '<span class="acct-breakdown-name">' + name + '</span>' +
                            '<span class="acct-breakdown-bal ' + cls + '">৳' + bal + '</span>' +
                            '</div>';
                    }).join('');
                }

                ['', 'Compact'].forEach(function (suf) {
                    fillBreakdown('dashboardSummaryBankSubs' + suf, active.filter(function(a) {
                        return a.accountType === 'BANK' || a.accountType === 'DEBIT_CARD';
                    }), false);
                    fillBreakdown('dashboardSummaryMfsSubs' + suf, active.filter(function(a) {
                        return a.accountType === 'MFS';
                    }), false);
                    fillBreakdown('dashboardSummaryCashSubs' + suf, active.filter(function(a) {
                        return a.accountType === 'CASH';
                    }), false);
                    fillBreakdown('dashboardSummaryCreditSubs' + suf, active.filter(function(a) {
                        return a.accountType === 'CREDIT_CARD';
                    }), true);
                });
            }
        } catch(e) { console.error('loadAccountBalances error:', e); }
    }

    /** Build the list of selectable months from the transactions we have, newest first,
     *  always including the real current month even if it has no transactions yet. */
    function getAvailableMonthKeys() {
        const set = new Set();
        allTransactions.forEach(function (tx) { set.add(monthKeyOf(tx.date)); });
        set.add(currentMonthKey());
        return Array.from(set).sort().reverse();
    }

    /** Populate the "Monthly View" dropdown, preserving the current selection if possible */
    function populateMonthSelector() {
        const sel = document.getElementById('monthViewSelect');
        if (!sel) return;
        const months = getAvailableMonthKeys();
        const curKey = currentMonthKey();
        if (!selectedMonthKey) selectedMonthKey = curKey;

        let html = months.map(function (key) {
            const label = monthKeyLabel(key) + (key === curKey ? ' (Current)' : '');
            return '<option value="' + key + '"' + (key === selectedMonthKey ? ' selected' : '') + '>' + label + '</option>';
        }).join('');
        html += '<option value="ALL"' + (selectedMonthKey === 'ALL' ? ' selected' : '') + '>All Time</option>';
        sel.innerHTML = html;

        sel.onchange = function () {
            selectedMonthKey = this.value;
            onMonthViewChanged();
        };
    }

    /** Sum income / expense / savings for a given month key ('YYYY-MM' or 'ALL') */
    function computeMonthStats(monthKey) {
        const txs = monthKey === 'ALL'
            ? allTransactions
            : allTransactions.filter(function (tx) { return monthKeyOf(tx.date) === monthKey; });

        let income = 0, expense = 0, savings = 0;
        txs.forEach(function (tx) {
            const amt = Number(tx.amount) || 0;
            if (tx.transaction_type === 'income') {
                income += amt;
            } else if (tx.transaction_type === 'expense') {
                expense += amt;
                // A "spend from savings" expense still counts fully as an expense, but also draws
                // down this month's savings bucket — same netting rule the backend applies.
                if (tx.fromSavings) savings -= amt;
            } else if (tx.transaction_type === 'savings') {
                savings += amt;
            }
        });
        return { income: income, expense: expense, savings: savings, net: income - expense - savings, txs: txs };
    }

    /** Group a month's transactions by category for the given type ('expense' | 'savings') */
    function computeCategoryBreakdown(monthKey, type) {
        const stats = computeMonthStats(monthKey);
        const map = {};
        stats.txs.forEach(function (tx) {
            if (tx.transaction_type !== type) return;
            const name = tx.category_name || 'Uncategorized';
            map[name] = (map[name] || 0) + (Number(tx.amount) || 0);
        });
        return Object.keys(map).map(function (name) {
            return { category: name, total: map[name], color: categoryColorMap[name] || '#6c757d' };
        }).sort(function (a, b) { return b.total - a.total; });
    }

    /** Push the selected month's numbers into the top stat cards + subtitles + net-balance styling */
    function renderMonthCards(monthKey) {
        const stats = computeMonthStats(monthKey);
        ['', 'Compact'].forEach(function (suf) {
            setEl('total-income' + suf,  formatAmount(stats.income));
            setEl('total-expense' + suf, formatAmount(stats.expense));
            setEl('total-savings' + suf, formatAmount(stats.savings));
            setEl('balance' + suf,       formatAmount(stats.net));
        });

        const label = monthKey === 'ALL' ? 'All time' : monthKeyLabel(monthKey, true);
        document.querySelectorAll('.sc-month-label').forEach(function (el) { el.textContent = label; });

        const subtitleEl = document.getElementById('monthViewSubtitle');
        if (subtitleEl) {
            subtitleEl.textContent = monthKey === 'ALL'
                ? 'Showing data across all time'
                : 'Showing data for ' + monthKeyLabel(monthKey);
        }

        ['netBalanceCard', 'netBalanceCardCompact'].forEach(function (id) {
            const netCard = document.getElementById(id);
            if (netCard) netCard.classList.toggle('stat-card--balance-negative', stats.net < 0);
        });

        fitStatAmounts();
        applyCardHideSetting();
    }

    /** Called whenever the Monthly View dropdown changes */
    function onMonthViewChanged() {
        renderMonthCards(selectedMonthKey);

        const breakdown = computeCategoryBreakdown(selectedMonthKey, categoryBreakdownType);
        categoryChartData = breakdown;
        drawCategoryChart(breakdown, activeSuffix());

        currentPage = 0;
        applyFiltersAndDisplay();
    }

    /** Get greeting based on current time of day */
    function getTimeGreeting() {
        const hour = new Date().getHours();
        if (hour >= 5 && hour < 12) return 'Good Morning';
        if (hour >= 12 && hour < 17) return 'Good Afternoon';
        if (hour >= 17 && hour < 21) return 'Good Evening';
        return 'Good Night';
    }

    /** Update dashboard greeting with username */
    async function updateGreeting() {
        try {
            const token = localStorage.getItem('token');
            const res = await fetch('/api/auth/me', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
            if (res.ok) {
                const user = await res.json();
                const username = user.username || user.fullName || 'User';
                const greeting = getTimeGreeting();
                const greetingEl = document.getElementById('dashboardGreeting');
                if (greetingEl) {
                    greetingEl.textContent = greeting + ', ' + username;
                }
            }
        } catch (error) {
            console.error('Error loading user greeting:', error);
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        // Initialise delete modal
        txDeleteModal = new bootstrap.Modal(document.getElementById('dashboardDeleteTxModal'));
        document.getElementById('dashboardConfirmDeleteTxBtn').addEventListener('click', confirmDeleteTransaction);

        // Initialise edit modal
        txEditModal = new bootstrap.Modal(document.getElementById('dashboardEditTxModal'));
        document.getElementById('dashboardSaveEditTxBtn').addEventListener('click', saveEditTransaction);

        // "Add Transaction" opens the shared bulk-capable modal on the Transactions page
        document.getElementById('openAddTxModalBtn').addEventListener('click', openAddTransactionModal);

        // "Scan Receipt" opens the shared scanner modal on the Transactions page
        document.getElementById('openScanReceiptBtn').addEventListener('click', openScanReceiptModal);

        // Receipt detail modal (view/download/replace/delete)
        receiptDetailModal = new bootstrap.Modal(document.getElementById('receiptDetailModal'));
        document.getElementById('rdReplaceBtn').addEventListener('click', () => document.getElementById('rdReplaceFileInput').click());
        document.getElementById('rdReplaceFileInput').addEventListener('change', function (e) {
            const file = e.target.files[0];
            this.value = '';
            if (file && validateReceiptFile(file)) replaceReceiptImage(rdCurrentReceiptId, file);
        });
        document.getElementById('rdDeleteBtn').addEventListener('click', () => deleteReceipt(rdCurrentReceiptId));

        // When type changes in the edit modal, reload categories
        document.getElementById('editTxType').addEventListener('change', function () {
            if (this.value === 'transfer') {
                showEditTransferFields();
                loadEditTransferAccounts(document.getElementById('editTxTransferFrom'), document.getElementById('editTxTransferTo'), '', '');
            } else {
                showEditNormalFields();
                loadEditCategories(this.value);
                applyAccountFieldLabel('editTxAccountLabel', this.value);
                applySavingsPseudoOption(document.getElementById('editTxAccount'), this.value);
            }
        });

        // Restore saved limit and wire filter controls
        const savedLimit = localStorage.getItem('tx_page_limit') || '5';
        pageSize = parseInt(savedLimit, 10);
        const limitEl = document.getElementById('txLimitSelect');
        if (limitEl) {
            limitEl.value = savedLimit;
            limitEl.addEventListener('change', function () {
                pageSize = parseInt(this.value, 10);
                localStorage.setItem('tx_page_limit', this.value);
                currentPage = 0;
                applyFiltersAndDisplay();
            });
        }

        const searchEl = document.getElementById('txSearchFilter');
        if (searchEl) {
            searchEl.addEventListener('input', function () {
                txFilterSearch = this.value;
                currentPage = 0;
                applyFiltersAndDisplay();
            });
        }

        const catEl = document.getElementById('txCategoryFilter');
        if (catEl) {
            catEl.addEventListener('change', function () {
                txFilterCategory = this.value;
                currentPage = 0;
                applyFiltersAndDisplay();
            });
        }

        const typeEl = document.getElementById('txTypeFilter');
        if (typeEl) {
            typeEl.addEventListener('change', function () {
                txFilterType = this.value;
                currentPage = 0;
                applyFiltersAndDisplay();
            });
        }

        const acctFilterEl = document.getElementById('txAccountFilter');
        if (acctFilterEl) {
            acctFilterEl.addEventListener('change', function () {
                txFilterAccount = this.value;
                currentPage = 0;
                applyFiltersAndDisplay();
            });
        }

        const importBtn = document.getElementById('txImportBtn');
        if (importBtn) {
            importBtn.addEventListener('click', function () {
                document.getElementById('txImportFileInput').click();
            });
        }
        const importFileEl = document.getElementById('txImportFileInput');
        if (importFileEl) {
            importFileEl.addEventListener('change', function (e) {
                const file = e.target.files[0];
                this.value = '';
                if (file) importTransactionsFromCsv(file);
            });
        }
        const exportBtn = document.getElementById('txExportBtn');
        if (exportBtn) exportBtn.addEventListener('click', exportFilteredTransactionsToCsv);

        // Restore saved dashboard view (Default / Compact) before first paint of data
        const savedView = localStorage.getItem(getUserStorageKey('fintrack_dashboard_view')) || 'default';
        setDashboardView(savedView, true);

        updateGreeting();
        loadDashboard();
        loadAccountBalances();
        loadUpcomingRecurring();
        loadCurrentBudget();
        loadAiSummary();
        loadFpWidget();
        loadGamificationWidget();
        loadReceiptScannerAvailability();
    });

    async function loadUpcomingRecurring() {
        const containers = ['upcomingRecurringBody', 'upcomingRecurringBodyCompact']
            .map(id => document.getElementById(id))
            .filter(Boolean);
        if (!containers.length) return;
        try {
            const items = await fetch('/api/recurring-transactions/upcoming?days=14').then(r => r.json());
            if (!items.length) {
                containers.forEach(c => c.innerHTML = '<p class="text-muted small mb-0">No upcoming recurring transactions in the next 14 days.</p>');
                return;
            }
            const today = new Date(); today.setHours(0, 0, 0, 0);
            const dayLabel = (dateStr) => {
                const d = new Date(dateStr); d.setHours(0, 0, 0, 0);
                const diffDays = Math.round((d - today) / 86400000);
                if (diffDays === 0) return 'Today';
                if (diffDays === 1) return 'Tomorrow';
                return d.toLocaleDateString('en-US', { day: 'numeric', month: 'short' });
            };
            const html = '<div class="d-flex flex-wrap gap-3">' + items.map(r => `
                <div class="p-2 px-3 rounded" style="background: var(--bg-table-stripe); min-width: 160px;">
                    <div class="small text-muted">${dayLabel(r.nextExecutionDate)}</div>
                    <div class="fw-semibold">${r.transactionName}</div>
                    <div class="small" style="color: var(--accent-color);">${formatAmount(r.amount)}</div>
                </div>
            `).join('') + '</div>';
            containers.forEach(c => c.innerHTML = html);
        } catch (e) {
            containers.forEach(c => c.innerHTML = '<p class="text-muted small mb-0">Unable to load upcoming recurring transactions.</p>');
        }
    }

    function escapeAiSummaryText(text) {
        const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return String(text == null ? '' : text).replace(/[&<>"']/g, m => map[m]);
    }

    async function loadGamificationWidget() {
        const bodies = ['gamificationBody', 'gamificationBodyCompact'].map(id => document.getElementById(id)).filter(Boolean);
        const rows = ['gamificationRow', 'gamificationRowCompact'].map(id => document.getElementById(id)).filter(Boolean);
        if (!bodies.length) return;
        try {
            const data = await fetch('/api/gamification/summary').then(r => r.json());
            if (!data.enabled) {
                rows.forEach(r => r.classList.add('d-none'));
                return;
            }
            const span = Math.max(1, data.nextLevelXp ? (data.nextLevelXp - data.currentLevelXp) : 1);
            const pct = data.nextLevelXp ? Math.max(0, Math.min(100, Math.round(((data.totalXp - data.currentLevelXp) / span) * 100))) : 100;
            let html = '<div class="d-flex justify-content-between align-items-center mb-1">';
            html += `<span class="fw-bold small">Lvl ${data.currentLevel} · ${data.currentLevelName}</span>`;
            html += `<span class="text-muted small">${data.totalXp} XP</span></div>`;
            html += `<div class="cb-progress mb-2"><div class="cb-progress-bar" style="width:${pct}%"></div></div>`;
            html += data.nextLevelName
                ? `<p class="text-muted small mb-2">${Math.max(0, data.nextLevelXp - data.totalXp)} XP to ${data.nextLevelName}</p>`
                : '<p class="text-muted small mb-2">Max level reached!</p>';
            html += '<div class="d-flex justify-content-between small">';
            html += `<span><i class="fas fa-fire text-warning me-1"></i>${data.currentStreak}-day streak</span>`;
            html += `<span><i class="fas fa-medal text-success me-1"></i>${data.achievementsUnlocked}/${data.achievementsTotal}</span></div>`;
            bodies.forEach(b => b.innerHTML = html);
        } catch (e) {
            bodies.forEach(b => b.innerHTML = '<p class="text-muted small mb-0">Unable to load achievements.</p>');
        }
    }

    async function loadAiSummary() {
        const bodies = ['aiSummaryBody', 'aiSummaryBodyCompact'].map(id => document.getElementById(id)).filter(Boolean);
        const rows = ['aiSummaryRow', 'aiSummaryRowCompact'].map(id => document.getElementById(id)).filter(Boolean);
        if (!bodies.length) return;
        try {
            const data = await fetch('/api/ai/dashboard-summary').then(r => r.json());
            if (!data.enabled) {
                rows.forEach(r => r.classList.add('d-none'));
                return;
            }
            const scoreColor = data.healthScore >= 75 ? '#28a745' : (data.healthScore >= 50 ? '#ffc107' : '#dc3545');
            let html = '<div class="d-flex align-items-center gap-3 mb-2">';
            html += `<div class="text-center" style="min-width:70px;"><div class="fw-bold" style="font-size:1.6rem;color:${scoreColor};">${data.healthScore}</div><div class="small text-muted">Health Score</div></div>`;
            html += '<div class="flex-grow-1">';
            if (data.todaysInsight) html += `<div class="small mb-1"><i class="fas fa-lightbulb me-1 text-warning"></i>${escapeAiSummaryText(data.todaysInsight)}</div>`;
            if (data.aiRecommendation) html += `<div class="small mb-1"><i class="fas fa-wand-magic-sparkles me-1" style="color: var(--accent-color);"></i>${escapeAiSummaryText(data.aiRecommendation)}</div>`;
            if (!data.todaysInsight && !data.aiRecommendation) html += '<div class="small text-muted">No new insights right now — check back after you log more activity.</div>';
            html += '</div></div>';

            html += '<div class="row g-1 small">';
            if (data.biggestExpense) {
                html += `<div class="col-12"><span class="text-muted">Biggest Expense:</span> <strong>${formatAmount(data.biggestExpense.amount)}</strong>${data.biggestExpense.category ? ' (' + escapeAiSummaryText(data.biggestExpense.category) + ')' : ''}</div>`;
            }
            if (data.budgetStatus) {
                html += '<div class="col-12"><span class="text-muted">Budget:</span> ' +
                    (data.budgetStatus.hasBudget ? `${data.budgetStatus.utilizationPercent.toFixed(0)}% used` : 'No active budget') + '</div>';
            }
            if (data.savingsProgress) {
                html += `<div class="col-12"><span class="text-muted">Emergency Fund:</span> ${data.savingsProgress.emergencyFundProgressPercent.toFixed(0)}%</div>`;
            }
            html += '</div>';
            bodies.forEach(b => b.innerHTML = html);
        } catch (e) {
            bodies.forEach(b => b.innerHTML = '<p class="text-muted small mb-0">Unable to load AI summary.</p>');
        }
    }

    async function loadCurrentBudget() {
        const containers = ['currentBudgetBody', 'currentBudgetBodyCompact']
            .map(id => document.getElementById(id))
            .filter(Boolean);
        if (!containers.length) return;
        try {
            const data = await fetch('/api/budget-plans/current').then(r => r.json());
            if (!data.hasCurrent) {
                const html = '<p class="text-muted small mb-0">No active budget for this period. <a href="/budget-planner">Create one</a>.</p>';
                containers.forEach(c => c.innerHTML = html);
                return;
            }
            const pct = Math.min(100, data.summary.utilizationPercent);
            const barClass = data.summary.utilizationPercent > 100 ? 'over' : (data.summary.utilizationPercent >= 80 ? 'warn' : '');
            const categories = data.categories || [];
            const categoriesHtml = categories.length
                ? `<div class="small text-muted mt-2 mb-1">By Category</div>
                   <div class="cb-category-list">${categories.map(c => {
                       const catPct = Math.min(100, c.percentUsed);
                       const catBarClass = c.percentUsed > 100 ? 'over' : (c.percentUsed >= 80 ? 'warn' : '');
                       return `
                        <div class="cb-category-row">
                            <div class="d-flex justify-content-between small mb-1">
                                <span><i class="fas fa-${c.categoryIcon || 'tag'} me-1" style="color:${c.categoryColor || '#6c757d'}"></i>${escHtml(c.categoryName)}</span>
                                <span class="text-muted">${formatAmount(c.actualAmount)} / ${formatAmount(c.budgetAmount)}</span>
                            </div>
                            <div class="cb-progress cb-progress-sm"><div class="cb-progress-bar ${catBarClass}" style="width:${catPct}%"></div></div>
                        </div>`;
                   }).join('')}</div>`
                : '';
            const html = `
                <div class="d-flex justify-content-between align-items-center mb-1">
                    <span class="fw-semibold">${escHtml(data.name)}</span>
                    <span class="text-muted small">${data.period}</span>
                </div>
                <div class="d-flex justify-content-between small text-muted mb-1">
                    <span>Budget Used</span>
                    <span>${data.summary.utilizationPercent.toFixed(0)}%</span>
                </div>
                <div class="cb-progress mb-2"><div class="cb-progress-bar ${barClass}" style="width:${pct}%"></div></div>
                <div class="small text-muted">Remaining: <strong style="color: var(--accent-color);">${formatAmount(data.summary.remaining)}</strong></div>
                ${categoriesHtml}`;
            containers.forEach(c => c.innerHTML = html);
        } catch (e) {
            containers.forEach(c => c.innerHTML = '<p class="text-muted small mb-0">Unable to load current budget.</p>');
        }
    }

    async function loadFpWidget() {
        try {
            const data = await fetch('/api/financial-planner/summary').then(r => r.ok ? r.json() : null);
            if (!data) return;
            const fmtC = (v) => {
                const c = typeof getCurrencySettings === 'function' ? getCurrencySettings() : { symbol: '৳', position: 'before', decimals: 0 };
                const num = Number(v || 0).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: c.decimals });
                return c.position === 'before' ? c.symbol + num : num + '\u00a0' + c.symbol;
            };
            const el = id => document.getElementById(id);
            ['', 'Compact'].forEach(suffix => {
                if (el('fpwInvValue' + suffix)) el('fpwInvValue' + suffix).textContent = fmtC(data.investmentValue);
                if (el('fpwLoans' + suffix)) el('fpwLoans' + suffix).textContent = data.activeLoans + ' loan' + (data.activeLoans !== 1 ? 's' : '') + (data.loanRemainingBalance ? ' · ' + fmtC(data.loanRemainingBalance) : '');
                if (el('fpwRenewals' + suffix)) el('fpwRenewals' + suffix).textContent = data.upcomingRenewals + ' renewal' + (data.upcomingRenewals !== 1 ? 's' : '');
                if (el('fpwGoalPct' + suffix) && data.goalsTarget > 0) {
                    const pct = Math.min(100, Math.round((data.goalsSaved / data.goalsTarget) * 100));
                    el('fpwGoalPct' + suffix).textContent = fmtC(data.goalsSaved) + ' / ' + fmtC(data.goalsTarget) + ' (' + pct + '%)';
                    if (el('fpwGoalBar' + suffix)) el('fpwGoalBar' + suffix).style.width = pct + '%';
                } else if (el('fpwGoalPct' + suffix)) {
                    el('fpwGoalPct' + suffix).textContent = data.goalsCount + ' goal' + (data.goalsCount !== 1 ? 's' : '');
                }
            });
        } catch (e) { /* silently ignore */ }
    }

    function loadReceiptScannerAvailability() {
        fetch('/api/receipts/settings').then(r => r.ok ? r.json() : null).then(s => {
            const btn = document.getElementById('openScanReceiptBtn');
            if (btn) btn.classList.toggle('d-none', !!s && s.enabled === false);
        }).catch(() => {});
    }

    window.addEventListener('resize', fitStatAmounts);

    async function loadDashboard() {
        try {
            // Assets Value & Net Worth are all-time/portfolio figures — not scoped to a month
            const summary = await fetch('/api/analytics/summary').then(r => r.json());
            ['', 'Compact'].forEach(function (suf) {
                setEl('total-assets' + suf, formatAmount(summary.total_assets || 0));
                setEl('net-worth' + suf,    formatAmount(summary.net_worth || 0));
            });

            const categories = await fetch('/api/categories').then(r => r.json());
            categoryColorMap = {};
            categories.forEach(cat => {
                categoryColorMap[cat.name] = cat.color || '#6c757d';
            });

            // Populate category filter dropdown
            const catFilter = document.getElementById('txCategoryFilter');
            if (catFilter) {
                // Preserve current selection
                const prevCat = catFilter.value;
                catFilter.innerHTML = '<option value="">All Categories</option>';
                categories.forEach(cat => {
                    const opt = document.createElement('option');
                    opt.value       = cat.name;
                    opt.textContent = cat.name;
                    catFilter.appendChild(opt);
                });
                catFilter.value = prevCat;
            }

            const accounts = await fetch('/api/accounts').then(r => r.json()).catch(() => []);
            cachedAccounts = accounts;
            const acctFilter = document.getElementById('txAccountFilter');
            if (acctFilter) {
                const prevAcct = acctFilter.value;
                const label = a => a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : '');
                acctFilter.innerHTML = '<option value="">All Accounts</option>'
                    + '<option value="SAVINGS">💰 Savings</option>'
                    + '<option value="NONE">No Account</option>'
                    + accounts.map(a => `<option value="${a.id}">${escHtml(label(a))}</option>`).join('');
                acctFilter.value = prevAcct;
            }

            // Load all transactions once — everything month-specific is derived client-side from this
            const transactions = await fetch('/api/transactions?limit=1000').then(r => r.json());
            allTransactions = transactions.sort((a, b) => new Date(b.date) - new Date(a.date));
            await loadReceiptBadges(allTransactions.map(tx => tx.id));

            // Set up / refresh the Monthly View dropdown, defaulting to the current calendar month
            populateMonthSelector();
            renderMonthCards(selectedMonthKey);

            const breakdown = computeCategoryBreakdown(selectedMonthKey, categoryBreakdownType);
            categoryChartData = breakdown;
            drawCategoryChart(breakdown, activeSuffix());

            const monthly = await fetch('/api/analytics/monthly').then(r => r.json());
            monthlyChartData = monthly;
            drawMonthlyChart(monthly, activeSuffix());

            currentPage = 0;
            applyFiltersAndDisplay();
        } catch (error) {
            console.error('Error loading dashboard:', error);
        }
    }

    /* ── Filter logic ────────────────────────────────────────────────────── */
    function txMatchesAccountFilter(tx) {
        if (!txFilterAccount) return true;
        if (txFilterAccount === 'SAVINGS') return !!tx.fromSavings;
        if (txFilterAccount === 'NONE') return !tx.fromSavings && tx.sourceAccountId == null && tx.destinationAccountId == null;
        const id = parseInt(txFilterAccount, 10);
        return tx.sourceAccountId === id || tx.destinationAccountId === id;
    }

    function applyFiltersAndDisplay() {
        const search = txFilterSearch.trim().toLowerCase();
        filteredTransactions = allTransactions.filter(function (tx) {
            const catOk    = !txFilterCategory || tx.category_name === txFilterCategory;
            const typeOk   = !txFilterType     || tx.transaction_type === txFilterType;
            const monthOk  = !selectedMonthKey || selectedMonthKey === 'ALL' || monthKeyOf(tx.date) === selectedMonthKey;
            const acctOk   = txMatchesAccountFilter(tx);
            const searchOk = !search
                || (tx.description || '').toLowerCase().includes(search)
                || (tx.category_name || '').toLowerCase().includes(search)
                || (tx.account_name || '').toLowerCase().includes(search)
                || String(tx.amount).includes(search);
            return catOk && typeOk && monthOk && acctOk && searchOk;
        });
        displayTransactionPage();
    }

    /* ── Render one page ─────────────────────────────────────────────────── */
    function displayTransactionPage() {
        const start = currentPage * pageSize;
        const end   = start + pageSize;
        const pageTransactions = filteredTransactions.slice(start, end);
        const totalPages = Math.ceil(filteredTransactions.length / pageSize);

        const typeIcon  = { income: 'fa-arrow-up', expense: 'fa-arrow-down', savings: 'fa-piggy-bank', transfer: 'fa-right-left' };
        const txTypeIcon = (type) => `<i class="fas ${typeIcon[type] || 'fa-circle'}"></i>`;

        function getCatIcon(name) {
            if (!name) return 'fa-tag';
            const n = name.toLowerCase();
            if (n.includes('food') || n.includes('restaurant') || n.includes('eat'))        return 'fa-utensils';
            if (n.includes('transport') || n.includes('car') || n.includes('fuel'))        return 'fa-car';
            if (n.includes('shop') || n.includes('cloth') || n.includes('fashion'))        return 'fa-shopping-bag';
            if (n.includes('health') || n.includes('medical') || n.includes('doctor'))     return 'fa-heartbeat';
            if (n.includes('entertain') || n.includes('movie') || n.includes('game'))      return 'fa-film';
            if (n.includes('salary') || n.includes('income') || n.includes('wage'))        return 'fa-briefcase';
            if (n.includes('rent') || n.includes('house') || n.includes('home'))           return 'fa-home';
            if (n.includes('edu') || n.includes('school') || n.includes('book'))           return 'fa-graduation-cap';
            if (n.includes('invest') || n.includes('stock') || n.includes('dividend'))     return 'fa-chart-line';
            if (n.includes('travel') || n.includes('hotel') || n.includes('flight'))       return 'fa-plane';
            if (n.includes('phone') || n.includes('mobile') || n.includes('internet'))     return 'fa-mobile-alt';
            if (n.includes('electric') || n.includes('bill') || n.includes('utility'))     return 'fa-bolt';
            return 'fa-tag';
        }

        const container = document.getElementById('recent-tx-body');

        if (filteredTransactions.length === 0) {
            const hasFilters = txFilterCategory || txFilterType;
            container.innerHTML = `
                <div class="tx-empty">
                    <i class="fas fa-search"></i>
                    <p>${hasFilters ? 'No transactions match the current filters.' : 'No transactions yet.'}</p>
                    ${!hasFilters ? '<button type="button" class="btn btn-sm btn-primary mt-2" onclick="openAddTransactionModal()"><i class="fas fa-plus"></i> Add Transaction</button>' : ''}
                </div>`;
        } else {
            container.innerHTML = pageTransactions.map((tx, idx) => {
                const catColor  = categoryColorMap[tx.category_name] || '#6c757d';
                // An External Source -> real account transfer is money entering the system, just
                // like income — style it the same way rather than defaulting every transfer to
                // expense-red (a real account -> External Destination correctly stays expense-red).
                const isExternalIncoming = tx.transaction_type === 'transfer' && !tx.sourceAccountId && !!tx.destinationAccountId;
                const isIncome  = tx.transaction_type === 'income' || isExternalIncoming;
                const isSavings = tx.transaction_type === 'savings';
                const txClass   = isIncome ? 'income' : (isSavings ? 'savings' : 'expense');
                const dateObj   = new Date(tx.date);
                const dayName   = dateObj.toLocaleDateString('en-US', { weekday: 'short' });
                const dateStr   = dateObj.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
                const yearStr   = dateObj.getFullYear();
                const catIcon   = getCatIcon(tx.category_name);
                const amtPrefix = isIncome ? '+' : (isSavings ? '⇒' : '−');
                return `
                <div class="tx-row" style="animation-delay: ${idx * 40}ms">
                    <div class="tx-col-date">
                        <div class="tx-date-badge">
                            <span class="tx-date-day">${dayName}</span>
                            <span class="tx-date-date">${dateStr}</span>
                            <span class="tx-date-year">${yearStr}</span>
                        </div>
                    </div>
                    <div class="tx-col-desc">
                        <div class="tx-type-dot dot-${txClass}">
                            ${txTypeIcon(tx.transaction_type)}
                        </div>
                        <div class="tx-desc-text">
                            <span class="tx-desc-main">${tx.description}</span>
                        </div>
                        ${receiptsByTransactionId[tx.id] ? `<button type="button" class="tx-receipt-badge" onclick="openReceiptDetailModal(${receiptsByTransactionId[tx.id]})" title="View receipt"><i class="fas fa-receipt"></i></button>` : ''}
                    </div>
                    <div class="tx-col-cat">
                        <span class="tx-category-chip" style="--cat-color: ${catColor}">
                            <i class="fas ${catIcon}"></i>
                            ${tx.category_name || 'Uncategorized'}
                        </span>
                    </div>
                    <div class="tx-col-account">
                        <span class="tx-account-name" title="${tx.account_name || '-'}">${tx.account_name || '-'}</span>
                    </div>
                    <div class="tx-col-type">
                        <span class="tx-type-badge type-${txClass}">
                            ${txTypeIcon(tx.transaction_type)}
                            ${tx.transaction_type.charAt(0).toUpperCase() + tx.transaction_type.slice(1)}
                        </span>
                    </div>
                    <div class="tx-col-amount">
                        <span class="tx-amount amount-${txClass}">
                            ${amtPrefix}${formatAmount(tx.amount)}
                        </span>
                    </div>
                    <div class="tx-col-action">
                        <button class="tx-edit-btn" onclick="editTransaction(${tx.id})" title="Edit">
                            <i class="fas fa-pen"></i>
                        </button>
                        <button class="tx-delete-btn" onclick="deleteTransaction(${tx.id})" title="Delete">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </div>
                </div>`;
            }).join('') + (function () {
                const netTotal = filteredTransactions.reduce((sum, tx) => {
                    const isExternalIncoming = tx.transaction_type === 'transfer' && !tx.sourceAccountId && !!tx.destinationAccountId;
                    const isIncome = tx.transaction_type === 'income' || isExternalIncoming;
                    return sum + (isIncome ? tx.amount : -tx.amount);
                }, 0);
                const totalClass = netTotal >= 0 ? 'income' : 'expense';
                const totalPrefix = netTotal >= 0 ? '+' : '−';
                return `
                <div class="tx-row tx-total-row">
                    <div class="tx-total-label"><strong>Net Total (${filteredTransactions.length} transaction${filteredTransactions.length !== 1 ? 's' : ''})</strong></div>
                    <div class="tx-col-amount">
                        <span class="tx-amount amount-${totalClass}"><strong>${totalPrefix}${formatAmount(Math.abs(netTotal))}</strong></span>
                    </div>
                    <div class="tx-col-action"></div>
                </div>`;
            })();
        }

        // Sub-heading: show filtered count vs total for the selected month (or all time)
        const monthTransactions = allTransactions.filter(function (tx) {
            return !selectedMonthKey || selectedMonthKey === 'ALL' || monthKeyOf(tx.date) === selectedMonthKey;
        });
        const totalLabel = monthTransactions.length;
        const filteredLabel = filteredTransactions.length;
        const hasFilters = txFilterCategory || txFilterType;
        document.getElementById('tx-total-count').textContent =
            hasFilters
                ? `${filteredLabel} of ${totalLabel} transaction${totalLabel !== 1 ? 's' : ''}`
                : `${totalLabel} transaction${totalLabel !== 1 ? 's' : ''}`;

        // Page info
        const shown = Math.min(end, filteredTransactions.length);
        const from  = filteredTransactions.length === 0 ? 0 : start + 1;
        document.getElementById('tx-page-info').innerHTML =
            `<i class="fas fa-list-ul"></i> Showing <strong>${from}–${shown}</strong> of <strong>${filteredTransactions.length}</strong>`;

        // Page number buttons
        const numbersEl = document.getElementById('tx-page-numbers');
        let nums = '';
        const range = 2;
        for (let i = 0; i < totalPages; i++) {
            if (i === 0 || i === totalPages - 1 || (i >= currentPage - range && i <= currentPage + range)) {
                nums += `<button class="tx-page-num ${i === currentPage ? 'active' : ''}" onclick="goToPage(${i})">${i + 1}</button>`;
            } else if (i === currentPage - range - 1 || i === currentPage + range + 1) {
                nums += `<span class="tx-page-ellipsis">…</span>`;
            }
        }
        numbersEl.innerHTML = nums;

        document.getElementById('tx-prev-btn').disabled  = currentPage === 0;
        document.getElementById('tx-first-btn').disabled = currentPage === 0;
        document.getElementById('tx-next-btn').disabled  = currentPage >= totalPages - 1;
        document.getElementById('tx-last-btn').disabled  = currentPage >= totalPages - 1;
    }

    function goToPage(page)      { currentPage = page; displayTransactionPage(); }
    function goToFirstPage()     { currentPage = 0; displayTransactionPage(); }
    function goToLastPage()      { currentPage = Math.max(0, Math.ceil(filteredTransactions.length / pageSize) - 1); displayTransactionPage(); }
    function nextTransactionPage()     { if (currentPage < Math.ceil(filteredTransactions.length / pageSize) - 1) { currentPage++; displayTransactionPage(); } }
    function previousTransactionPage() { if (currentPage > 0) { currentPage--; displayTransactionPage(); } }

    /* ── CSV export / import ─────────────────────────────────────────────── */
    function csvEscape(val) {
        const s = String(val == null ? '' : val);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }

    async function exportFilteredTransactionsToCsv() {
        if (!filteredTransactions.length) {
            showNotification('No transactions to export.', 'error');
            return;
        }
        const ok = await confirmAction(`Export ${filteredTransactions.length} transaction${filteredTransactions.length > 1 ? 's' : ''} to CSV?`, { title: 'Export to CSV', confirmText: 'Export' });
        if (!ok) return;
        const headers = ['Date', 'Description', 'Category', 'Type', 'Amount', 'Account', 'From Savings', 'Transaction ID', 'Created At'];
        const rows = filteredTransactions.map(tx => [
            (tx.date || '').slice(0, 10),
            tx.description || '',
            tx.category_name || '',
            tx.transaction_type || '',
            tx.amount,
            tx.account_name || '-',
            tx.fromSavings ? 'Yes' : 'No',
            tx.id,
            tx.created_at || ''
        ]);
        const csvContent = [headers, ...rows].map(r => r.map(csvEscape).join(',')).join('\r\n');
        const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().slice(0, 10);
        a.href = url;
        a.download = `transactions_${stamp}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showNotification(`Exported ${filteredTransactions.length} transaction${filteredTransactions.length > 1 ? 's' : ''} to CSV.`, 'success');
    }

    function parseCsv(text) {
        const rows = [];
        let row = [], field = '', inQuotes = false;
        for (let i = 0; i < text.length; i++) {
            const c = text[i];
            if (inQuotes) {
                if (c === '"') {
                    if (text[i + 1] === '"') { field += '"'; i++; }
                    else inQuotes = false;
                } else field += c;
            } else if (c === '"') {
                inQuotes = true;
            } else if (c === ',') {
                row.push(field); field = '';
            } else if (c === '\r') {
                // skip, \n handles the line break
            } else if (c === '\n') {
                row.push(field); rows.push(row); row = []; field = '';
            } else {
                field += c;
            }
        }
        if (field.length || row.length) { row.push(field); rows.push(row); }
        return rows.filter(r => r.some(c => c.trim() !== ''));
    }

    async function importTransactionsFromCsv(file) {
        const text = (await file.text()).replace(/^﻿/, '');
        const rows = parseCsv(text);
        if (rows.length < 2) {
            showNotification('CSV file has no data rows.', 'error');
            return;
        }
        const header = rows[0].map(h => h.trim().toLowerCase());
        const idx = {
            date: header.indexOf('date'),
            description: header.indexOf('description'),
            category: header.indexOf('category'),
            type: header.indexOf('type'),
            amount: header.indexOf('amount'),
            account: header.indexOf('account'),
            fromSavings: header.indexOf('from savings')
        };
        if (idx.date === -1 || idx.description === -1 || idx.type === -1 || idx.amount === -1) {
            showNotification('CSV is missing required columns (Date, Description, Type, Amount).', 'error');
            return;
        }

        showNotification('Importing…', 'success');
        const categories = await fetch('/api/categories').then(r => r.json()).catch(() => []);
        const accounts = await fetch('/api/accounts').then(r => r.json()).catch(() => []);
        cachedAccounts = accounts;

        const IMPORT_CATEGORY_COLORS = ['#E53935','#F4511E','#FB8C00','#FDD835','#7CB342','#43A047','#00897B','#00ACC1','#1E88E5','#3949AB','#5E35B1','#8E24AA','#D81B60','#EC407A','#6D4C41','#757575','#546E7A','#90A4AE','#2E7D32','#1565C0'];
        const createdCategoryNames = [];

        let succeeded = 0, failed = 0;
        const errors = [];
        for (const r of rows.slice(1)) {
            const type = (r[idx.type] || '').trim().toLowerCase();
            const description = (r[idx.description] || '').trim();
            const amount = parseFloat(String(r[idx.amount] || '').replace(/[^\d.-]/g, ''));
            const date = (r[idx.date] || '').trim().slice(0, 10);
            const categoryName = idx.category !== -1 ? (r[idx.category] || '').trim() : '';
            const accountText = idx.account !== -1 ? (r[idx.account] || '').trim() : '';
            const fromSavingsText = idx.fromSavings !== -1 ? (r[idx.fromSavings] || '').trim().toLowerCase() : '';

            if (type === 'transfer') {
                failed++; errors.push(`"${description || 'row'}" skipped — Transfer rows aren't supported for import; add them via Log Cash Transfer.`);
                continue;
            }
            if (!['income', 'expense', 'savings'].includes(type)) {
                failed++; errors.push(`"${description || 'row'}" skipped — invalid type "${r[idx.type]}".`);
                continue;
            }
            if (!description || isNaN(amount) || amount <= 0 || !date) {
                failed++; errors.push(`"${description || 'row'}" skipped — missing or invalid description, amount, or date.`);
                continue;
            }
            if (!categoryName) {
                failed++; errors.push(`"${description}" skipped — no category specified.`);
                continue;
            }
            let cat = categories.find(c => c.name.toLowerCase() === categoryName.toLowerCase());
            if (!cat) {
                try {
                    const createResp = await fetch('/api/categories', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            name: categoryName,
                            color: IMPORT_CATEGORY_COLORS[categories.length % IMPORT_CATEGORY_COLORS.length],
                            icon: 'tag',
                            categoryType: type
                        })
                    });
                    if (createResp.ok) {
                        cat = await createResp.json();
                        categories.push(cat);
                        createdCategoryNames.push(categoryName);
                    } else {
                        const errData = await createResp.json().catch(() => null);
                        failed++; errors.push(`"${description}" skipped — could not create category "${categoryName}" (${(errData && errData.error) || 'server error'}).`);
                        continue;
                    }
                } catch (e) {
                    failed++; errors.push(`"${description}" skipped — could not create category "${categoryName}".`);
                    continue;
                }
            }
            const fromSavings = type === 'expense' && (fromSavingsText === 'yes' || accountText.toUpperCase() === 'SAVINGS');
            let accountId = null;
            if (!fromSavings && accountText && accountText !== '-') {
                const acct = cachedAccounts.find(a => accountText.startsWith(a.accountNickname));
                accountId = acct ? acct.id : null;
            }

            try {
                const response = await fetch('/api/transactions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        description,
                        amount,
                        category_id: cat.id,
                        transaction_type: type,
                        date,
                        sourceAccountId: accountId,
                        destinationAccountId: null,
                        fromSavings
                    })
                });
                if (response.ok) {
                    succeeded++;
                } else {
                    failed++;
                    const data = await response.json().catch(() => null);
                    errors.push(`"${description}" failed — ${(data && data.error) || 'server error'}.`);
                }
            } catch (e) {
                failed++; errors.push(`"${description}" failed — network error.`);
            }
        }

        if (succeeded) loadDashboard();
        const uniqueCreated = [...new Set(createdCategoryNames)];
        const createdNote = uniqueCreated.length
            ? ` (${uniqueCreated.length} new categor${uniqueCreated.length > 1 ? 'ies' : 'y'} created: ${uniqueCreated.join(', ')})`
            : '';
        if (failed === 0) {
            showNotification(`Imported ${succeeded} transaction${succeeded > 1 ? 's' : ''} successfully.${createdNote}`, 'success');
        } else {
            showNotification(`${succeeded} imported, ${failed} failed.${createdNote} ${errors.slice(0, 2).join(' ')}`, succeeded ? 'warning' : 'error');
        }
    }

    /* ── Edit transaction ────────────────────────────────────────────────── */
    function showEditTransferFields() {
        document.getElementById('editTxAccountWrap').classList.add('d-none');
        document.getElementById('editTxCategoryWrap').classList.add('d-none');
        document.getElementById('editTxTransferWrap').classList.remove('d-none');
    }

    function showEditNormalFields() {
        document.getElementById('editTxAccountWrap').classList.remove('d-none');
        document.getElementById('editTxCategoryWrap').classList.remove('d-none');
        document.getElementById('editTxTransferWrap').classList.add('d-none');
    }

    async function loadEditTransferAccounts(fromSel, toSel, selectedFrom, selectedTo) {
        try {
            const accounts = await fetch('/api/accounts').then(r => r.json());
            const label = a => a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : '');
            const accountOptionsHtml = accounts.map(a => `<option value="${a.id}">${label(a)}</option>`).join('');
            fromSel.innerHTML = '<option value="">Select source…</option>' + '<option value="EXTERNAL">🌐 External Source</option>' +
                (accounts.length ? '<option disabled>──────────</option>' : '') + accountOptionsHtml;
            toSel.innerHTML = '<option value="">Select destination…</option>' + '<option value="EXTERNAL">🌐 External Destination</option>' +
                (accounts.length ? '<option disabled>──────────</option>' : '') + accountOptionsHtml;
        } catch (e) {
            fromSel.innerHTML = '<option value="">Select source…</option>';
            toSel.innerHTML = '<option value="">Select destination…</option>';
        }
        fromSel.value = selectedFrom || '';
        toSel.value = selectedTo || '';
    }

    async function editTransaction(id) {
        const tx = allTransactions.find(t => t.id === id);
        if (!tx) return;
        txEditId = id;
        document.getElementById('editTxId').value          = id;
        document.getElementById('editTxDescription').value = tx.description;
        document.getElementById('editTxDetails').value      = tx.details || '';
        document.getElementById('editTxAmount').value      = Number(tx.amount).toFixed(2);
        document.getElementById('editTxDate').value        = tx.date ? tx.date.slice(0, 10) : '';
        document.getElementById('editTxType').value        = tx.transaction_type;

        if (tx.transaction_type === 'transfer') {
            showEditTransferFields();
            const selectedFrom = tx.sourceAccountId != null ? String(tx.sourceAccountId) : 'EXTERNAL';
            const selectedTo   = tx.destinationAccountId != null ? String(tx.destinationAccountId) : 'EXTERNAL';
            await loadEditTransferAccounts(document.getElementById('editTxTransferFrom'), document.getElementById('editTxTransferTo'), selectedFrom, selectedTo);
        } else {
            showEditNormalFields();
            loadEditCategories(tx.transaction_type, tx.category_id);
            applyAccountFieldLabel('editTxAccountLabel', tx.transaction_type);
            const selectedAccountValue = tx.fromSavings ? 'SAVINGS' : (tx.sourceAccountId != null ? String(tx.sourceAccountId) : '');
            loadEditTxAccounts(selectedAccountValue, tx.transaction_type);
        }
        txEditModal.show();
    }

    async function loadEditTxAccounts(selectedValue, type) {
        const sel = document.getElementById('editTxAccount');
        try {
            const accounts = await fetch('/api/accounts').then(r => r.json());
            const label = a => a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : '');
            sel.innerHTML = '<option value="">-- No account --</option>' + accounts.map(a => `<option value="${a.id}">${label(a)}</option>`).join('');
        } catch (e) {
            sel.innerHTML = '<option value="">-- No account --</option>';
        }
        applySavingsPseudoOption(sel, type);
        sel.value = selectedValue || '';
    }

    async function loadEditCategories(type, selectedId) {
        const sel = document.getElementById('editTxCategory');
        sel.innerHTML = '<option value="">Loading…</option>';
        try {
            const url = type ? `/api/categories?type=${encodeURIComponent(type)}` : '/api/categories';
            const cats = await fetch(url).then(r => r.json());
            sel.innerHTML = cats.length
                ? cats.map(c => `<option value="${c.id}"${c.id === selectedId ? ' selected' : ''}>${c.name}</option>`).join('')
                : '<option value="">No categories found</option>';
            if (selectedId && !cats.find(c => c.id === selectedId)) {
                // Selected category not in filtered list — fetch all and add it
                const all = await fetch('/api/categories').then(r => r.json());
                const cat = all.find(c => c.id === selectedId);
                if (cat) {
                    const opt = document.createElement('option');
                    opt.value = cat.id; opt.textContent = cat.name; opt.selected = true;
                    sel.insertBefore(opt, sel.firstChild);
                }
            }
        } catch (e) {
            sel.innerHTML = '<option value="">Error loading</option>';
        }
    }

    async function saveEditTransaction() {
        const id          = parseInt(document.getElementById('editTxId').value, 10);
        const description = document.getElementById('editTxDescription').value.trim();
        const details     = document.getElementById('editTxDetails').value.trim();
        const amount      = parseFloat(document.getElementById('editTxAmount').value);
        const date        = document.getElementById('editTxDate').value;
        const type        = document.getElementById('editTxType').value;

        let payload;
        if (type === 'transfer') {
            const fromVal = document.getElementById('editTxTransferFrom').value;
            const toVal   = document.getElementById('editTxTransferTo').value;
            if (!fromVal || !toVal) {
                showNotification('Select both From and To accounts.', 'error'); return;
            }
            if (fromVal === 'EXTERNAL' && toVal === 'EXTERNAL') {
                showNotification('From and To cannot both be External.', 'error'); return;
            }
            if (fromVal === toVal) {
                showNotification('From and To accounts must be different.', 'error'); return;
            }
            if (isNaN(amount) || amount <= 0 || !date) {
                showNotification('Please fill all fields correctly.', 'error'); return;
            }
            payload = {
                description: description || 'Transfer',
                details,
                amount,
                date,
                transaction_type: 'transfer',
                category_id: null,
                sourceAccountId: fromVal === 'EXTERNAL' ? null : parseInt(fromVal, 10),
                destinationAccountId: toVal === 'EXTERNAL' ? null : parseInt(toVal, 10)
            };
        } else {
            const categoryId = parseInt(document.getElementById('editTxCategory').value, 10);
            const acctVal    = document.getElementById('editTxAccount').value;
            if (!description || isNaN(amount) || amount <= 0 || !date || !categoryId) {
                showNotification('Please fill all fields correctly.', 'error');
                return;
            }
            payload = {
                description,
                details,
                amount,
                date,
                transaction_type: type,
                category_id: categoryId,
                sourceAccountId: acctVal && acctVal !== 'SAVINGS' ? parseInt(acctVal, 10) : null,
                destinationAccountId: null,
                fromSavings: acctVal === 'SAVINGS'
            };
        }

        try {
            const response = await fetch(`/api/transactions/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await response.json().catch(() => ({}));
            if (response.ok) {
                txEditModal.hide();
                txEditId = null;
                showNotification(data.warning || 'Transaction updated successfully', data.warning ? 'warning' : 'success');
                loadDashboard();
            } else {
                showNotification(data.error || 'Failed to update transaction', 'error');
            }
        } catch (e) {
            showNotification('Error updating transaction', 'error');
        }
    }

    /* ── Add transaction ─────────────────────────────────────────────────── */
    function openAddTransactionModal() {
        window.location.href = 'transactions.html?openAddTx=1';
    }

    function openScanReceiptModal() {
        window.location.href = 'transactions.html?openReceiptScanner=1';
    }

    async function loadReceiptBadges(transactionIds) {
        if (!transactionIds.length) { receiptsByTransactionId = {}; return; }
        try {
            const res = await fetch('/api/receipts/by-transaction?ids=' + transactionIds.join(','));
            receiptsByTransactionId = res.ok ? await res.json() : {};
        } catch (e) {
            receiptsByTransactionId = {};
        }
    }

    function openReceiptDetailModal(receiptId) {
        rdCurrentReceiptId = receiptId;
        const url = `/api/receipts/${receiptId}/image`;
        document.getElementById('rdPreviewImg').src = url;
        document.getElementById('rdDownloadLink').href = url;
        receiptDetailModal.show();
    }

    async function replaceReceiptImage(receiptId, file) {
        try {
            const fd = new FormData();
            fd.append('file', file);
            const res = await fetch(`/api/receipts/${receiptId}/image`, { method: 'PUT', body: fd });
            const data = await res.json().catch(() => null);
            if (!res.ok) {
                showNotification((data && data.error) || 'Failed to replace receipt image.', 'error');
                return;
            }
            document.getElementById('rdPreviewImg').src = `/api/receipts/${receiptId}/image?t=` + Date.now();
            showNotification('Receipt image replaced.', 'success');
        } catch (e) {
            showNotification('Unable to replace this receipt right now.', 'error');
        }
    }

    async function deleteReceipt(receiptId) {
        if (!confirm('Delete this receipt? The transaction itself will not be affected.')) return;
        try {
            const res = await fetch(`/api/receipts/${receiptId}`, { method: 'DELETE' });
            if (!res.ok && res.status !== 204) {
                showNotification('Failed to delete receipt.', 'error');
                return;
            }
            receiptDetailModal.hide();
            showNotification('Receipt deleted.', 'success');
            loadDashboard();
        } catch (e) {
            showNotification('Unable to delete this receipt right now.', 'error');
        }
    }

    function validateReceiptFile(file) {
        const allowed = ['image/jpeg', 'image/png', 'image/webp'];
        if (!allowed.includes(file.type)) {
            showNotification('Please choose a JPG, PNG, or WEBP image.', 'error');
            return false;
        }
        if (file.size > 10 * 1024 * 1024) {
            showNotification('Image is too large — please choose a file under 10MB.', 'error');
            return false;
        }
        return true;
    }

    const ACCOUNT_FIELD_LABELS = { expense: 'Paid From', income: 'Received Into', savings: 'Savings Account' };
    function applyAccountFieldLabel(labelElId, type) {
        const labelEl = document.getElementById(labelElId);
        if (!labelEl) return;
        labelEl.innerHTML = (ACCOUNT_FIELD_LABELS[type] || 'Paid From') + ' <small class="text-muted">(optional)</small>';
    }
    function applySavingsPseudoOption(selectEl, type) {
        if (!selectEl) return;
        const existing = selectEl.querySelector('option[value="SAVINGS"]');
        if (type === 'expense') {
            if (!existing) {
                const opt = document.createElement('option');
                opt.value = 'SAVINGS';
                opt.textContent = '💰 Savings (spend from savings)';
                selectEl.insertBefore(opt, selectEl.options[1] || null);
            }
        } else if (existing) {
            if (selectEl.value === 'SAVINGS') selectEl.value = '';
            existing.remove();
        }
    }

    /* ── Delete transaction — custom modal ───────────────────────────────── */
    function deleteTransaction(id) {
        txDeleteId = id;
        txDeleteModal.show();
    }

    async function confirmDeleteTransaction() {
        if (txDeleteId === null) return;
        const id = txDeleteId;
        txDeleteId = null;
        txDeleteModal.hide();
        try {
            const response = await fetch(`/api/transactions/${id}`, { method: 'DELETE' });
            if (response.ok) {
                showNotification('Transaction deleted successfully', 'success');
                loadDashboard();
            } else {
                showNotification('Failed to delete transaction', 'error');
            }
        } catch (error) {
            console.error('Error deleting transaction:', error);
            showNotification('Error deleting transaction', 'error');
        }
    }

    /* ── Charts ──────────────────────────────────────────────────────────── */

    let categoryChartInstances = {}; // suffix -> Chart instance ('' = default, 'Compact' = compact)

    function drawCategoryChart(data, suffix) {
        suffix = suffix || '';
        const canvasEl = document.getElementById('categoryChart' + suffix);
        if (!canvasEl) return; // that panel isn't in the DOM (shouldn't happen, but stay safe)
        const ctx = canvasEl.getContext('2d');
        if (categoryChartInstances[suffix]) {
            categoryChartInstances[suffix].destroy();
        }

        if (categoryChartType === 'pie') {
            categoryChartInstances[suffix] = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: data.map(d => d.category),
                    datasets: [{
                        data: data.map(d => d.total),
                        backgroundColor: data.map(d => d.color || '#3498db')
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: {display: false}
                    }
                }
            });
            const legendWrap = document.getElementById('categoryLegendContainer' + suffix);
            if (legendWrap) legendWrap.style.display = 'block';
        } else if (categoryChartType === 'bar') {
            categoryChartInstances[suffix] = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: data.map(d => d.category),
                    datasets: [{
                        label: 'Expense',
                        data: data.map(d => d.total),
                        backgroundColor: data.map(d => d.color || '#3498db'),
                        borderRadius: 6,
                        borderSkipped: false
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    indexAxis: 'y',
                    plugins: {
                        legend: { display: false }
                    },
                    scales: {
                        x: {
                            beginAtZero: true,
                            ticks: {
                                callback: function(value) {
                                    return getCurrencySettings().symbol + value;
                                }
                            }
                        }
                    }
                }
            });
            const legendWrap = document.getElementById('categoryLegendContainer' + suffix);
            if (legendWrap) legendWrap.style.display = 'none';
        }

        // Generate simple category legend
        const legendContainer = document.getElementById('categoryLegend' + suffix);
        if (legendContainer) {
            legendContainer.innerHTML = data.map(d => `
                <div class="d-flex align-items-center mb-2">
                    <div style="width: 16px; height: 16px; background-color: ${d.color || '#3498db'}; border-radius: 3px; margin-right: 8px; flex-shrink: 0;"></div>
                    <span style="font-size: 0.9rem;">${d.category}</span>
                </div>
            `).join('');
        }
    }

    function switchCategoryChart(type) {
        categoryChartType = type;
        ['', 'Compact'].forEach(function (suf) {
            const pieBtn = document.getElementById('categoryPieBtn' + suf);
            const barBtn = document.getElementById('categoryBarBtn' + suf);
            if (pieBtn) pieBtn.classList.toggle('active', type === 'pie');
            if (barBtn) barBtn.classList.toggle('active', type === 'bar');
        });
        drawCategoryChart(categoryChartData, activeSuffix());
    }

    function switchCategoryBreakdown(type) {
        categoryBreakdownType = type;
        const data = computeCategoryBreakdown(selectedMonthKey, categoryBreakdownType);
        categoryChartData = data;
        drawCategoryChart(data, activeSuffix());
    }

    let monthlyChartInstances = {}; // suffix -> Chart instance ('' = default, 'Compact' = compact)

    function drawMonthlyChart(data, suffix) {
        suffix = suffix || '';
        const canvasEl = document.getElementById('monthlyChart' + suffix);
        if (!canvasEl) return;
        const fiscalYear = ['Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'];
        const monthMap = {
            '07': 'Jul', '08': 'Aug', '09': 'Sep', '10': 'Oct', '11': 'Nov', '12': 'Dec',
            '01': 'Jan', '02': 'Feb', '03': 'Mar', '04': 'Apr', '05': 'May', '06': 'Jun'
        };

        const months = {};
        fiscalYear.forEach(m => { months[m] = { income: 0, expense: 0, savings: 0 }; });

        data.forEach(d => {
            if (d.month) {
                const monthNum = d.month.toString().padStart(7, '0').slice(-2);  // last 2 chars of YYYY-MM
                const mm = d.month.split('-')[1];
                const monthLabel = monthMap[mm];
                if (monthLabel && months[monthLabel]) {
                    if (d.type === 'income')  months[monthLabel].income  = Number(d.total);
                    if (d.type === 'expense') months[monthLabel].expense = Number(d.total);
                    if (d.type === 'savings') months[monthLabel].savings = Number(d.total);
                }
            }
        });

        const ctx = canvasEl.getContext('2d');
        if (monthlyChartInstances[suffix]) monthlyChartInstances[suffix].destroy();

        const commonOptions = {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'top' } },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: { callback: v => getCurrencySettings().symbol + v }
                }
            }
        };

        const incomeDataset  = { label: 'Income',  data: fiscalYear.map(m => months[m].income),  borderColor: '#28a745', backgroundColor: 'rgba(40,167,69,0.1)',  tension: 0.4, fill: true };
        const expenseDataset = { label: 'Expense', data: fiscalYear.map(m => months[m].expense), borderColor: '#dc3545', backgroundColor: 'rgba(220,53,69,0.1)',   tension: 0.4, fill: true };
        const savingsDataset = { label: 'Savings', data: fiscalYear.map(m => months[m].savings), borderColor: '#3b82f6', backgroundColor: 'rgba(59,130,246,0.1)',  tension: 0.4, fill: true };

        if (monthlyChartType === 'bar') {
            incomeDataset.backgroundColor  = '#28a745';
            incomeDataset.borderRadius     = 6;
            incomeDataset.borderSkipped    = false;
            delete incomeDataset.borderColor; delete incomeDataset.tension; delete incomeDataset.fill;
            expenseDataset.backgroundColor = '#dc3545';
            expenseDataset.borderRadius    = 6;
            expenseDataset.borderSkipped   = false;
            delete expenseDataset.borderColor; delete expenseDataset.tension; delete expenseDataset.fill;
            savingsDataset.backgroundColor = '#3b82f6';
            savingsDataset.borderRadius    = 6;
            savingsDataset.borderSkipped   = false;
            delete savingsDataset.borderColor; delete savingsDataset.tension; delete savingsDataset.fill;
        }

        monthlyChartInstances[suffix] = new Chart(ctx, {
            type: monthlyChartType === 'bar' ? 'bar' : 'line',
            data: { labels: fiscalYear, datasets: [incomeDataset, expenseDataset, savingsDataset] },
            options: commonOptions
        });
    }

    function switchMonthlyChart(type) {
        monthlyChartType = type;
        ['', 'Compact'].forEach(function (suf) {
            const lineBtn = document.getElementById('monthlyLineBtn' + suf);
            const barBtn  = document.getElementById('monthlyBarBtn' + suf);
            if (lineBtn) lineBtn.classList.toggle('active', type === 'line');
            if (barBtn)  barBtn.classList.toggle('active', type === 'bar');
        });
        drawMonthlyChart(monthlyChartData, activeSuffix());
    }

    const TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };

    function showNotification(message, type = 'success') {
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `notification-toast notification-${type}`;
        toast.innerHTML = `<i class="fas ${TOAST_ICONS[type] || TOAST_ICONS.success} notification-toast-icon"></i>` +
            `<span class="notification-toast-message"></span>` +
            `<button type="button" class="notification-toast-close" aria-label="Dismiss">&times;</button>`;
        toast.querySelector('.notification-toast-message').textContent = message;
        const dismiss = () => { toast.classList.remove('show'); setTimeout(() => toast.remove(), 300); };
        toast.querySelector('.notification-toast-close').addEventListener('click', dismiss);
        container.appendChild(toast);
        requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(dismiss, 5000);
    }