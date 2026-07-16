/**
 * financial-planner.js
 * Handles all logic for the Financial Planner page:
 *   Tab 1 – Investment Portfolio
 *   Tab 2 – Loan Manager
 *   Tab 3 – Subscription Manager
 *   Tab 4 – Wishlist & Financial Goals
 */
(function () {
    'use strict';

    // ── Currency helper (reuses global getCurrencySettings if available) ──
    function fmtCurrency(val) {
        if (typeof getCurrencySettings === 'function') {
            var c = getCurrencySettings();
            var num = Number(val || 0).toFixed(c.decimals);
            return c.position === 'before' ? c.symbol + num : num + '\u00a0' + c.symbol;
        }
        return '৳' + Number(val || 0).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }
    function fmtNum(v, dp) { return Number(v || 0).toFixed(dp != null ? dp : 2); }
    function escHtml(s) {
        return String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ── Toast notifications ──────────────────────────────────────────────────
    function showToast(msg, type) {
        type = type || 'success';
        var container = document.getElementById('toastContainer');
        if (!container) return;
        var t = document.createElement('div');
        t.className = 'notification-toast notification-toast-' + type;
        t.innerHTML = '<span>' + escHtml(msg) + '</span>';
        container.appendChild(t);
        requestAnimationFrame(function () { t.classList.add('show'); });
        setTimeout(function () { t.classList.remove('show'); setTimeout(function () { t.remove(); }, 400); }, 3000);
    }

    // ── API helpers ──────────────────────────────────────────────────────────
    var BASE = '/api/financial-planner';

    async function apiFetch(url, options) {
        try {
            options = options || {};
            var headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
            var res = await fetch(url, Object.assign({}, options, { headers: headers }));
            if (res.status === 401) { window.location.href = '/login'; return null; }
            if (res.status === 204) return {};
            return await res.json();
        } catch (e) {
            showToast('Network error: ' + e.message, 'error');
            return null;
        }
    }

    // ── Pagination helper ────────────────────────────────────────────────────
    var PAGE_SIZE = 10;
    var pages = { investments: 1, loans: 1, subscriptions: 1, goals: 1 };

    function renderPagination(containerId, total, section) {
        var container = document.getElementById(containerId);
        if (!container) return;
        var totalPages = Math.ceil(total / PAGE_SIZE) || 1;
        var cur = pages[section];
        if (totalPages <= 1) { container.innerHTML = ''; return; }
        var html = '<span class="fp-page-info">Page ' + cur + ' of ' + totalPages + '</span>';
        html += '<button class="fp-page-btn" onclick="fpSetPage(\'' + section + '\',' + (cur - 1) + ')" ' + (cur <= 1 ? 'disabled' : '') + '><i class="fas fa-chevron-left"></i></button>';
        for (var p = Math.max(1, cur - 2); p <= Math.min(totalPages, cur + 2); p++) {
            html += '<button class="fp-page-btn' + (p === cur ? ' active' : '') + '" onclick="fpSetPage(\'' + section + '\',' + p + ')">' + p + '</button>';
        }
        html += '<button class="fp-page-btn" onclick="fpSetPage(\'' + section + '\',' + (cur + 1) + ')" ' + (cur >= totalPages ? 'disabled' : '') + '><i class="fas fa-chevron-right"></i></button>';
        container.innerHTML = html;
    }

    window.fpSetPage = function (section, p) {
        pages[section] = p;
        if (section === 'investments') renderInvestments();
        else if (section === 'loans') renderLoans();
        else if (section === 'subscriptions') renderSubscriptions();
        else if (section === 'goals') renderGoals();
    };

    // ══════════════════════════════════════════════════════════════════════════
    // TAB SWITCHING
    // ══════════════════════════════════════════════════════════════════════════
    window.switchTab = function (tab) {
        document.querySelectorAll('.fp-tab-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        document.querySelectorAll('.fp-tab-pane').forEach(function (pane) {
            pane.classList.toggle('active', pane.id === 'tab-' + tab);
        });
        // Redraw charts when investment tab becomes visible
        if (tab === 'investments') setTimeout(drawInvCharts, 50);
    };

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ TAB 1 – INVESTMENT PORTFOLIO
    // ══════════════════════════════════════════════════════════════════════════
    var invData = [];
    var invAllocationChartInst = null;
    var invProfitChartInst = null;

    async function loadInvestments() {
        invData = (await apiFetch(BASE + '/investments')) || [];
        updateInvSummary();
        renderInvestments();
        drawInvCharts();
    }

    function updateInvSummary() {
        var totalValue = invData.reduce(function (s, i) { return s + (i.currentValue || 0); }, 0);
        var totalCost  = invData.reduce(function (s, i) { return s + (i.quantity * i.purchasePrice); }, 0);
        var pl = totalValue - totalCost;
        var ret = totalCost > 0 ? (pl / totalCost) * 100 : 0;
        document.getElementById('invTotalValue').textContent = fmtCurrency(totalValue);
        var plEl = document.getElementById('invProfitLoss');
        plEl.textContent = (pl >= 0 ? '+' : '') + fmtCurrency(pl);
        plEl.className = 'fp-stat-value ' + (pl >= 0 ? 'fp-profit' : 'fp-loss');
        document.getElementById('invReturnPct').textContent = (ret >= 0 ? '+' : '') + fmtNum(ret, 2) + '%';
        document.getElementById('invCount').textContent = invData.length;
    }

    function getFilteredInvestments() {
        var q = (document.getElementById('invSearch').value || '').toLowerCase();
        var type = document.getElementById('invTypeFilter').value;
        var sort = document.getElementById('invSort').value;
        var list = invData.filter(function (i) {
            return (!q || i.name.toLowerCase().includes(q) || (i.platform || '').toLowerCase().includes(q))
                && (!type || i.investmentType === type);
        });
        if (sort === 'value_desc') list.sort(function (a, b) { return (b.currentValue || 0) - (a.currentValue || 0); });
        else if (sort === 'value_asc') list.sort(function (a, b) { return (a.currentValue || 0) - (b.currentValue || 0); });
        else if (sort === 'profit_desc') list.sort(function (a, b) { return (b.profitLoss || 0) - (a.profitLoss || 0); });
        else if (sort === 'date_desc') list.sort(function (a, b) { return (b.purchaseDate || '').localeCompare(a.purchaseDate || ''); });
        return list;
    }

    window.renderInvestments = function () {
        var list = getFilteredInvestments();
        var total = list.length;
        var start = (pages.investments - 1) * PAGE_SIZE;
        var page = list.slice(start, start + PAGE_SIZE);
        var tbody = document.getElementById('invTableBody');
        if (!total) {
            tbody.innerHTML = '<tr><td colspan="11"><div class="fp-empty"><i class="fas fa-chart-line"></i><p>No investments yet. Click <strong>Add Investment</strong> to get started.</p></div></td></tr>';
            renderPagination('invPagination', 0, 'investments');
            return;
        }
        tbody.innerHTML = page.map(function (inv) {
            var plCls = (inv.profitLoss || 0) >= 0 ? 'fp-profit' : 'fp-loss';
            return '<tr>' +
                '<td><strong>' + escHtml(inv.name) + '</strong></td>' +
                '<td><span class="fp-badge fp-badge-ACTIVE">' + escHtml(invTypeName(inv.investmentType)) + '</span></td>' +
                '<td>' + escHtml(inv.platform || '—') + '</td>' +
                '<td>' + escHtml(inv.purchaseDate || '—') + '</td>' +
                '<td>' + fmtNum(inv.quantity, 4) + '</td>' +
                '<td>' + fmtCurrency(inv.purchasePrice) + '</td>' +
                '<td>' + fmtCurrency(inv.currentPrice) + '</td>' +
                '<td><strong>' + fmtCurrency(inv.currentValue) + '</strong></td>' +
                '<td class="' + plCls + '">' + ((inv.profitLoss || 0) >= 0 ? '+' : '') + fmtCurrency(inv.profitLoss) + '</td>' +
                '<td class="' + plCls + '">' + ((inv.returnPercent || 0) >= 0 ? '+' : '') + fmtNum(inv.returnPercent, 2) + '%</td>' +
                '<td style="text-align:center">' +
                    '<button class="fp-action-btn" title="Edit" onclick="openInvModal(' + inv.id + ')"><i class="fas fa-pen"></i></button>' +
                    '<button class="fp-action-btn danger" title="Delete" onclick="confirmDelete(\'investment\',' + inv.id + ')"><i class="fas fa-trash"></i></button>' +
                '</td></tr>';
        }).join('');
        renderPagination('invPagination', total, 'investments');
    };

    function invTypeName(t) {
        var m = { STOCKS: 'Stocks', MUTUAL_FUNDS: 'Mutual Funds', ETF: 'ETF', BONDS: 'Bonds', CRYPTO: 'Crypto', FIXED_DEPOSIT: 'Fixed Deposit', OTHER: 'Other' };
        return m[t] || t;
    }

    function drawInvCharts() {
        if (!invData.length) return;
        var CHART_COLORS = ['#1F7D53','#3b82f6','#f97316','#a855f7','#22c55e','#ef4444','#06b6d4','#eab308'];

        // Allocation pie
        var typeMap = {};
        invData.forEach(function (i) {
            typeMap[i.investmentType] = (typeMap[i.investmentType] || 0) + (i.currentValue || 0);
        });
        var labels = Object.keys(typeMap).map(invTypeName);
        var vals   = Object.values(typeMap);
        var ctx1 = document.getElementById('invAllocationChart');
        if (ctx1) {
            if (invAllocationChartInst) invAllocationChartInst.destroy();
            invAllocationChartInst = new Chart(ctx1, {
                type: 'doughnut',
                data: { labels: labels, datasets: [{ data: vals, backgroundColor: CHART_COLORS, borderWidth: 2, borderColor: 'transparent' }] },
                options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom', labels: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-body') || '#666', font: { size: 11 } } } } }
            });
        }

        // Profit/Loss bar
        var names = invData.map(function (i) { return i.name.length > 12 ? i.name.slice(0, 12) + '…' : i.name; });
        var pls   = invData.map(function (i) { return i.profitLoss || 0; });
        var bgColors = pls.map(function (v) { return v >= 0 ? 'rgba(34,197,94,0.7)' : 'rgba(239,68,68,0.7)'; });
        var ctx2 = document.getElementById('invProfitChart');
        if (ctx2) {
            if (invProfitChartInst) invProfitChartInst.destroy();
            invProfitChartInst = new Chart(ctx2, {
                type: 'bar',
                data: { labels: names, datasets: [{ label: 'Profit / Loss', data: pls, backgroundColor: bgColors, borderRadius: 4 }] },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: {
                        x: { ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted-custom') || '#888', font: { size: 10 } } },
                        y: { ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted-custom') || '#888' } }
                    }
                }
            });
        }
    }

    // Investment Modal
    window.openInvModal = function (id) {
        clearForm(['invId','invName','invType','invPlatform','invPurchaseDate','invQty','invBuyPrice','invCurPrice','invNotes']);
        document.getElementById('invModalTitle').innerHTML = '<i class="fas fa-chart-line me-2"></i>' + (id ? 'Edit Investment' : 'Add Investment');
        if (id) {
            var inv = invData.find(function (x) { return x.id === id; });
            if (inv) {
                document.getElementById('invId').value = inv.id;
                document.getElementById('invName').value = inv.name || '';
                document.getElementById('invType').value = inv.investmentType || '';
                document.getElementById('invPlatform').value = inv.platform || '';
                document.getElementById('invPurchaseDate').value = inv.purchaseDate || '';
                document.getElementById('invQty').value = inv.quantity || '';
                document.getElementById('invBuyPrice').value = inv.purchasePrice || '';
                document.getElementById('invCurPrice').value = inv.currentPrice || '';
                document.getElementById('invNotes').value = inv.notes || '';
            }
        }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('invModal')).show();
    };

    window.saveInvestment = async function () {
        var id = document.getElementById('invId').value;
        var body = {
            name: document.getElementById('invName').value.trim(),
            investmentType: document.getElementById('invType').value,
            platform: document.getElementById('invPlatform').value.trim(),
            purchaseDate: document.getElementById('invPurchaseDate').value,
            quantity: parseFloat(document.getElementById('invQty').value) || 0,
            purchasePrice: parseFloat(document.getElementById('invBuyPrice').value) || 0,
            currentPrice: parseFloat(document.getElementById('invCurPrice').value) || 0,
            notes: document.getElementById('invNotes').value.trim()
        };
        if (!body.name || !body.investmentType || !body.purchaseDate) {
            showToast('Name, type and purchase date are required.', 'error'); return;
        }
        var url = id ? BASE + '/investments/' + id : BASE + '/investments';
        var method = id ? 'PUT' : 'POST';
        var result = await apiFetch(url, { method: method, body: JSON.stringify(body) });
        if (result && !result.error) {
            showToast(id ? 'Investment updated.' : 'Investment added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('invModal')).hide();
            loadInvestments();
        } else if (result) {
            showToast(result.error || 'Save failed.', 'error');
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ TAB 2 – LOAN MANAGER
    // ══════════════════════════════════════════════════════════════════════════
    var loanData = [];

    async function loadLoans() {
        loanData = (await apiFetch(BASE + '/loans')) || [];
        updateLoanSummary();
        renderLoans();
    }

    function updateLoanSummary() {
        var active = loanData.filter(function (l) { return l.status === 'ACTIVE'; });
        document.getElementById('loanTotal').textContent = fmtCurrency(loanData.reduce(function (s, l) { return s + (l.principalAmount || 0); }, 0));
        document.getElementById('loanRemaining').textContent = fmtCurrency(loanData.reduce(function (s, l) { return s + (l.remainingBalance || 0); }, 0));
        document.getElementById('loanEMI').textContent = fmtCurrency(active.reduce(function (s, l) { return s + (l.emiAmount || 0); }, 0));
        document.getElementById('loanInterest').textContent = fmtCurrency(loanData.reduce(function (s, l) { return s + (l.totalInterest || 0); }, 0));
        document.getElementById('loanCount').textContent = active.length;
    }

    window.renderLoans = function () {
        var q = (document.getElementById('loanSearch').value || '').toLowerCase();
        var type = document.getElementById('loanTypeFilter').value;
        var status = document.getElementById('loanStatusFilter').value;
        var list = loanData.filter(function (l) {
            return (!q || l.loanName.toLowerCase().includes(q) || (l.lenderBorrower || '').toLowerCase().includes(q))
                && (!type || l.loanType === type)
                && (!status || l.status === status);
        });
        var start = (pages.loans - 1) * PAGE_SIZE;
        var page = list.slice(start, start + PAGE_SIZE);
        var tbody = document.getElementById('loanTableBody');
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="10"><div class="fp-empty"><i class="fas fa-hand-holding-usd"></i><p>No loans yet. Click <strong>Add Loan</strong> to track one.</p></div></td></tr>';
            renderPagination('loanPagination', 0, 'loans');
            return;
        }
        tbody.innerHTML = page.map(function (l) {
            var pct = Math.round(l.progressPercent || 0);
            var barCls = pct >= 90 ? 'danger' : pct >= 60 ? 'warn' : '';
            return '<tr>' +
                '<td><strong>' + escHtml(l.loanName) + '</strong>' + (l.notes ? '<div class="fp-goal-meta">' + escHtml(l.notes.slice(0, 40)) + '</div>' : '') + '</td>' +
                '<td>' + escHtml(loanTypeName(l.loanType)) + '</td>' +
                '<td>' + escHtml(l.lenderBorrower || '—') + '</td>' +
                '<td>' + fmtCurrency(l.principalAmount) + '</td>' +
                '<td>' + (l.emiAmount ? fmtCurrency(l.emiAmount) : '—') + '</td>' +
                '<td>' + fmtCurrency(l.remainingBalance) + '</td>' +
                '<td style="min-width:120px"><div class="fp-progress"><div class="fp-progress-bar ' + barCls + '" style="width:' + pct + '%"></div></div><div class="fp-goal-meta mt-1">' + pct + '% paid</div></td>' +
                '<td>' + escHtml(l.loanEndDate || '—') + '</td>' +
                '<td><span class="fp-badge fp-badge-' + l.status + '">' + l.status + '</span></td>' +
                '<td style="text-align:center">' +
                    (l.status === 'ACTIVE' && l.emiAmount ? '<button class="fp-action-btn" title="Mark EMI Paid" onclick="payEmi(' + l.id + ')"><i class="fas fa-check"></i></button>' : '') +
                    '<button class="fp-action-btn" title="Edit" onclick="openLoanModal(' + l.id + ')"><i class="fas fa-pen"></i></button>' +
                    '<button class="fp-action-btn danger" title="Delete" onclick="confirmDelete(\'loan\',' + l.id + ')"><i class="fas fa-trash"></i></button>' +
                '</td></tr>';
        }).join('');
        renderPagination('loanPagination', list.length, 'loans');
    };

    function loanTypeName(t) {
        var m = { PERSONAL: 'Personal', HOME: 'Home', CAR: 'Car', EDUCATION: 'Education', BUSINESS: 'Business', BORROWED: 'Borrowed', LENT: 'Lent' };
        return m[t] || t;
    }

    window.openLoanModal = function (id) {
        clearForm(['loanId','loanName','loanType','loanLender','loanPrincipal','loanRate','loanEmi','loanBalance','loanStart','loanEnd','loanFreq','loanStatus','loanNotes']);
        document.getElementById('loanModalTitle').innerHTML = '<i class="fas fa-hand-holding-usd me-2"></i>' + (id ? 'Edit Loan' : 'Add Loan');
        document.getElementById('loanStatus').value = 'ACTIVE';
        if (id) {
            var loan = loanData.find(function (x) { return x.id === id; });
            if (loan) {
                document.getElementById('loanId').value = loan.id;
                document.getElementById('loanName').value = loan.loanName || '';
                document.getElementById('loanType').value = loan.loanType || '';
                document.getElementById('loanLender').value = loan.lenderBorrower || '';
                document.getElementById('loanPrincipal').value = loan.principalAmount || '';
                document.getElementById('loanRate').value = loan.interestRate || '';
                document.getElementById('loanEmi').value = loan.emiAmount || '';
                document.getElementById('loanBalance').value = loan.remainingBalance || '';
                document.getElementById('loanStart').value = loan.loanStartDate || '';
                document.getElementById('loanEnd').value = loan.loanEndDate || '';
                document.getElementById('loanFreq').value = loan.paymentFrequency || '';
                document.getElementById('loanStatus').value = loan.status || 'ACTIVE';
                document.getElementById('loanNotes').value = loan.notes || '';
            }
        }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('loanModal')).show();
    };

    window.saveLoan = async function () {
        var id = document.getElementById('loanId').value;
        var body = {
            loanName: document.getElementById('loanName').value.trim(),
            loanType: document.getElementById('loanType').value,
            lenderBorrower: document.getElementById('loanLender').value.trim(),
            principalAmount: parseFloat(document.getElementById('loanPrincipal').value) || 0,
            interestRate: document.getElementById('loanRate').value || null,
            emiAmount: document.getElementById('loanEmi').value || null,
            remainingBalance: parseFloat(document.getElementById('loanBalance').value) || 0,
            loanStartDate: document.getElementById('loanStart').value,
            loanEndDate: document.getElementById('loanEnd').value || null,
            paymentFrequency: document.getElementById('loanFreq').value || null,
            status: document.getElementById('loanStatus').value,
            notes: document.getElementById('loanNotes').value.trim()
        };
        if (!body.loanName || !body.loanType || !body.loanStartDate) {
            showToast('Loan name, type and start date are required.', 'error'); return;
        }
        var url = id ? BASE + '/loans/' + id : BASE + '/loans';
        var result = await apiFetch(url, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
        if (result && !result.error) {
            showToast(id ? 'Loan updated.' : 'Loan added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('loanModal')).hide();
            loadLoans();
        } else if (result) {
            showToast(result.error || 'Save failed.', 'error');
        }
    };

    window.payEmi = async function (id) {
        var result = await apiFetch(BASE + '/loans/' + id + '/pay-emi', { method: 'POST' });
        if (result && !result.error) {
            showToast('EMI marked as paid.');
            loadLoans();
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ TAB 3 – SUBSCRIPTION MANAGER
    // ══════════════════════════════════════════════════════════════════════════
    var subData = [];

    var SUB_TEMPLATES = [
        { name: 'Netflix', category: 'Entertainment', billingCycle: 'MONTHLY' },
        { name: 'Spotify', category: 'Entertainment', billingCycle: 'MONTHLY' },
        { name: 'ChatGPT Plus', category: 'AI Tools', billingCycle: 'MONTHLY' },
        { name: 'Claude Pro', category: 'AI Tools', billingCycle: 'MONTHLY' },
        { name: 'GitHub', category: 'Developer', billingCycle: 'MONTHLY' },
        { name: 'Adobe Creative Cloud', category: 'Design', billingCycle: 'YEARLY' },
        { name: 'Google One', category: 'Storage', billingCycle: 'MONTHLY' },
        { name: 'Microsoft 365', category: 'Productivity', billingCycle: 'YEARLY' },
        { name: 'YouTube Premium', category: 'Entertainment', billingCycle: 'MONTHLY' },
        { name: 'Amazon Prime', category: 'Shopping', billingCycle: 'YEARLY' },
        { name: 'Apple Music', category: 'Entertainment', billingCycle: 'MONTHLY' }
    ];

    function buildSubTemplateChips() {
        var container = document.getElementById('subTemplateChips');
        if (!container) return;
        container.innerHTML = '<span style="font-size:0.8rem;color:var(--text-muted-custom);padding:5px 0;align-self:center;flex-shrink:0;">Quick add:</span>' +
            SUB_TEMPLATES.map(function (t) {
                return '<span class="fp-chip" onclick="openSubModalTemplate(' + JSON.stringify(t).replace(/"/g, '&quot;') + ')">' + escHtml(t.name) + '</span>';
            }).join('');
    }

    window.openSubModalTemplate = function (tpl) {
        openSubModal(null, tpl);
    };

    async function loadSubscriptions() {
        subData = (await apiFetch(BASE + '/subscriptions')) || [];
        updateSubSummary();
        renderSubscriptions();
    }

    function updateSubSummary() {
        var active = subData.filter(function (s) { return s.status === 'ACTIVE'; });
        var monthly = active.reduce(function (sum, s) { return sum + (s.monthlyCost || 0); }, 0);
        var yearly  = active.reduce(function (sum, s) { return sum + (s.yearlyCost || 0); }, 0);
        var today = new Date(); today.setHours(0, 0, 0, 0);
        var nextMonth = new Date(today); nextMonth.setDate(today.getDate() + 30);
        var renewals = active.filter(function (s) {
            if (!s.renewalDate) return false;
            var d = new Date(s.renewalDate);
            return d >= today && d <= nextMonth;
        }).length;
        document.getElementById('subActive').textContent = active.length;
        document.getElementById('subMonthly').textContent = fmtCurrency(monthly);
        document.getElementById('subYearly').textContent = fmtCurrency(yearly);
        document.getElementById('subRenewals').textContent = renewals;
    }

    window.renderSubscriptions = function () {
        var q = (document.getElementById('subSearch').value || '').toLowerCase();
        var status = document.getElementById('subStatusFilter').value;
        var cycle = document.getElementById('subCycleFilter').value;
        var list = subData.filter(function (s) {
            return (!q || s.name.toLowerCase().includes(q) || (s.category || '').toLowerCase().includes(q))
                && (!status || s.status === status)
                && (!cycle || s.billingCycle === cycle);
        });
        list.sort(function (a, b) {
            if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1;
            if (b.status === 'ACTIVE' && a.status !== 'ACTIVE') return 1;
            return (a.renewalDate || '').localeCompare(b.renewalDate || '');
        });
        var start = (pages.subscriptions - 1) * PAGE_SIZE;
        var page = list.slice(start, start + PAGE_SIZE);
        var tbody = document.getElementById('subTableBody');
        if (!list.length) {
            tbody.innerHTML = '<tr><td colspan="10"><div class="fp-empty"><i class="fas fa-repeat"></i><p>No subscriptions yet. Click <strong>Add Subscription</strong> or use the quick-add chips above.</p></div></td></tr>';
            renderPagination('subPagination', 0, 'subscriptions');
            return;
        }
        var today = new Date(); today.setHours(0, 0, 0, 0);
        tbody.innerHTML = page.map(function (s) {
            var rowCls = '';
            if (s.renewalDate && s.status === 'ACTIVE') {
                var d = new Date(s.renewalDate);
                var diff = Math.ceil((d - today) / 86400000);
                if (diff < 0) rowCls = 'fp-renewal-overdue';
                else if (diff <= 7) rowCls = 'fp-renewal-soon';
            }
            var renewDisplay = s.renewalDate ? s.renewalDate + (s.daysUntilRenewal != null ? ' <small class="text-muted">(' + s.daysUntilRenewal + 'd)</small>' : '') : '—';
            return '<tr class="' + rowCls + '">' +
                '<td><strong>' + escHtml(s.name) + '</strong></td>' +
                '<td>' + escHtml(s.category || '—') + '</td>' +
                '<td><span class="fp-badge fp-badge-ACTIVE" style="font-size:0.7rem">' + s.billingCycle + '</span></td>' +
                '<td>' + fmtCurrency(s.cost) + '</td>' +
                '<td>' + fmtCurrency(s.monthlyCost) + '</td>' +
                '<td>' + fmtCurrency(s.yearlyCost) + '</td>' +
                '<td>' + renewDisplay + '</td>' +
                '<td>' + (s.autoRenewal ? '<i class="fas fa-check-circle text-success"></i>' : '<i class="fas fa-times-circle text-danger"></i>') + '</td>' +
                '<td><span class="fp-badge fp-badge-' + s.status + '">' + s.status + '</span></td>' +
                '<td style="text-align:center">' +
                    '<button class="fp-action-btn" title="Edit" onclick="openSubModal(' + s.id + ')"><i class="fas fa-pen"></i></button>' +
                    '<button class="fp-action-btn danger" title="Delete" onclick="confirmDelete(\'subscription\',' + s.id + ')"><i class="fas fa-trash"></i></button>' +
                '</td></tr>';
        }).join('');
        renderPagination('subPagination', list.length, 'subscriptions');
    };

    window.openSubModal = function (id, tpl) {
        clearForm(['subId','subName','subCategory','subCycle','subCost','subRenewal','subPayMethod','subPayAccount','subStatus','subNotes']);
        document.getElementById('subAutoRenew').checked = true;
        document.getElementById('subStatus').value = 'ACTIVE';
        document.getElementById('subCycle').value = 'MONTHLY';
        document.getElementById('subModalTitle').innerHTML = '<i class="fas fa-repeat me-2"></i>' + (id ? 'Edit Subscription' : 'Add Subscription');
        if (tpl) {
            document.getElementById('subName').value = tpl.name || '';
            document.getElementById('subCategory').value = tpl.category || '';
            document.getElementById('subCycle').value = tpl.billingCycle || 'MONTHLY';
        }
        if (id) {
            var sub = subData.find(function (x) { return x.id === id; });
            if (sub) {
                document.getElementById('subId').value = sub.id;
                document.getElementById('subName').value = sub.name || '';
                document.getElementById('subCategory').value = sub.category || '';
                document.getElementById('subCycle').value = sub.billingCycle || 'MONTHLY';
                document.getElementById('subCost').value = sub.cost || '';
                document.getElementById('subRenewal').value = sub.renewalDate || '';
                document.getElementById('subPayMethod').value = sub.paymentMethod || '';
                document.getElementById('subPayAccount').value = sub.paymentAccount || '';
                document.getElementById('subAutoRenew').checked = !!sub.autoRenewal;
                document.getElementById('subStatus').value = sub.status || 'ACTIVE';
                document.getElementById('subNotes').value = sub.notes || '';
            }
        }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('subModal')).show();
    };

    window.saveSubscription = async function () {
        var id = document.getElementById('subId').value;
        var body = {
            name: document.getElementById('subName').value.trim(),
            category: document.getElementById('subCategory').value.trim(),
            billingCycle: document.getElementById('subCycle').value,
            cost: parseFloat(document.getElementById('subCost').value) || 0,
            renewalDate: document.getElementById('subRenewal').value || null,
            paymentMethod: document.getElementById('subPayMethod').value.trim(),
            paymentAccount: document.getElementById('subPayAccount').value.trim(),
            autoRenewal: document.getElementById('subAutoRenew').checked,
            status: document.getElementById('subStatus').value,
            notes: document.getElementById('subNotes').value.trim()
        };
        if (!body.name || !body.billingCycle) {
            showToast('Name and billing cycle are required.', 'error'); return;
        }
        var url = id ? BASE + '/subscriptions/' + id : BASE + '/subscriptions';
        var result = await apiFetch(url, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
        if (result && !result.error) {
            showToast(id ? 'Subscription updated.' : 'Subscription added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('subModal')).hide();
            loadSubscriptions();
        } else if (result) {
            showToast(result.error || 'Save failed.', 'error');
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ TAB 4 – WISHLIST & FINANCIAL GOALS
    // ══════════════════════════════════════════════════════════════════════════
    var goalData = [];

    var GOAL_ICONS = { 'MacBook': '💻', 'Japan Trip': '🌸', 'Wedding': '💍', 'New Phone': '📱', 'Camera': '📷', 'Emergency Fund': '🛡️', 'Bike': '🏍️', 'Car': '🚗', 'House': '🏠' };

    async function loadGoals() {
        goalData = (await apiFetch(BASE + '/goals')) || [];
        updateGoalSummary();
        renderGoals();
    }

    function updateGoalSummary() {
        var completed = goalData.filter(function (g) { return g.status === 'COMPLETED'; });
        document.getElementById('goalCount').textContent = goalData.length;
        document.getElementById('goalCompleted').textContent = completed.length;
        document.getElementById('goalTargetStat').textContent = fmtCurrency(goalData.reduce(function (s, g) { return s + (g.targetAmount || 0); }, 0));
        document.getElementById('goalSavedStat').textContent = fmtCurrency(goalData.reduce(function (s, g) { return s + (g.savedAmount || 0); }, 0));
    }

    window.renderGoals = function () {
        var q = (document.getElementById('goalSearch').value || '').toLowerCase();
        var status = document.getElementById('goalStatusFilter').value;
        var priority = document.getElementById('goalPriorityFilter').value;
        var sort = document.getElementById('goalSort').value;
        var list = goalData.filter(function (g) {
            return (!q || g.goalName.toLowerCase().includes(q) || (g.category || '').toLowerCase().includes(q))
                && (!status || g.status === status)
                && (!priority || g.priority === priority);
        });
        if (sort === 'date_asc') list.sort(function (a, b) { return (a.targetDate || '9999').localeCompare(b.targetDate || '9999'); });
        else if (sort === 'progress_desc') list.sort(function (a, b) { return (b.progressPercent || 0) - (a.progressPercent || 0); });
        else if (sort === 'amount_desc') list.sort(function (a, b) { return (b.targetAmount || 0) - (a.targetAmount || 0); });
        var start = (pages.goals - 1) * PAGE_SIZE;
        var page = list.slice(start, start + PAGE_SIZE);
        var grid = document.getElementById('goalCardsGrid');
        if (!list.length) {
            grid.innerHTML = '<div class="fp-empty" style="grid-column:1/-1"><i class="fas fa-star"></i><p>No goals yet. Click <strong>Add Goal</strong> to start saving towards something.</p></div>';
            renderPagination('goalPagination', 0, 'goals');
            return;
        }
        grid.innerHTML = page.map(function (g) {
            var pct = Math.round(g.progressPercent || 0);
            var barCls = pct >= 100 ? '' : pct >= 75 ? '' : pct >= 40 ? 'warn' : 'danger';
            var icon = g.icon || GOAL_ICONS[g.goalName] || '🎯';
            var canComplete = g.status === 'IN_PROGRESS';
            return '<div class="fp-goal-card">' +
                '<div class="d-flex align-items-start gap-3 mb-3">' +
                    '<div class="fp-goal-icon">' + icon + '</div>' +
                    '<div class="flex-grow-1 min-w-0">' +
                        '<div class="fp-goal-title">' + escHtml(g.goalName) + '</div>' +
                        '<div class="fp-goal-meta">' + (g.category ? escHtml(g.category) + ' · ' : '') +
                            '<span class="fp-badge fp-badge-' + g.priority + '" style="font-size:0.65rem">' + g.priority + '</span>' +
                            ' <span class="fp-badge fp-badge-' + g.status + '" style="font-size:0.65rem">' + g.status.replace('_', ' ') + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div class="d-flex gap-1">' +
                        (canComplete ? '<button class="fp-action-btn" title="Mark Completed" onclick="completeGoal(' + g.id + ')"><i class="fas fa-check"></i></button>' : '') +
                        '<button class="fp-action-btn" title="Edit" onclick="openGoalModal(' + g.id + ')"><i class="fas fa-pen"></i></button>' +
                        '<button class="fp-action-btn danger" title="Delete" onclick="confirmDelete(\'goal\',' + g.id + ')"><i class="fas fa-trash"></i></button>' +
                    '</div>' +
                '</div>' +
                '<div class="mb-2">' +
                    '<div class="d-flex justify-content-between mb-1"><span class="fp-goal-meta">Target</span><strong>' + fmtCurrency(g.targetAmount) + '</strong></div>' +
                    '<div class="d-flex justify-content-between mb-1"><span class="fp-goal-meta">Saved</span><span class="fp-profit">' + fmtCurrency(g.savedAmount) + '</span></div>' +
                    '<div class="d-flex justify-content-between mb-2"><span class="fp-goal-meta">Remaining</span><span class="fp-loss">' + fmtCurrency(g.remainingAmount) + '</span></div>' +
                    '<div class="fp-progress mb-1"><div class="fp-progress-bar ' + barCls + '" style="width:' + Math.min(100, pct) + '%"></div></div>' +
                    '<div class="d-flex justify-content-between"><span class="fp-goal-meta">' + pct + '% complete</span>' + (g.targetDate ? '<span class="fp-goal-meta">Due: ' + escHtml(g.targetDate) + '</span>' : '') + '</div>' +
                '</div>' +
                (g.notes ? '<div class="fp-goal-meta border-top pt-2 mt-2" style="border-color:var(--border-color)!important">' + escHtml(g.notes.slice(0, 80)) + '</div>' : '') +
            '</div>';
        }).join('');
        renderPagination('goalPagination', list.length, 'goals');
    };

    var GOAL_ICON_OPTIONS = [
        '🎯','💻','📱','🎒','✈️','🌍','🏠','🚗','🏍️','📷',
        '💍','🎓','💼','🏖️','⛵','🎸','🏋️','📚','🎮','🛍️',
        '💰','🏦','🎁','🚀','⌚','🖥️','🎪','🌸','🐶','🏡',
        '🔑','💎','🛒','🎉','🌟','🏆','🎵','🍕','☕','🌴'
    ];

    function initGoalIconPicker(selected) {
        var grid = document.getElementById('goalIconGrid');
        var hidden = document.getElementById('goalIcon');
        var preview = document.getElementById('goalIconPreview');
        var custom = document.getElementById('goalIconCustom');
        if (!grid) return;

        var current = selected || '🎯';
        hidden.value = current;
        preview.textContent = current;
        custom.value = '';

        grid.innerHTML = GOAL_ICON_OPTIONS.map(function(icon) {
            var active = icon === current ? 'border-primary bg-primary bg-opacity-10' : 'border-transparent';
            return '<button type="button" class="btn border rounded goal-icon-opt p-1" data-icon="' + icon + '" ' +
                'style="font-size:1.4rem;line-height:1;width:2.4rem;height:2.4rem;display:flex;align-items:center;justify-content:center;" ' +
                'title="' + icon + '" aria-label="Select icon ' + icon + '">' + icon + '</button>';
        }).join('');

        grid.querySelectorAll('.goal-icon-opt').forEach(function(btn) {
            btn.addEventListener('click', function() {
                var icon = this.getAttribute('data-icon');
                hidden.value = icon;
                preview.textContent = icon;
                custom.value = '';
                grid.querySelectorAll('.goal-icon-opt').forEach(function(b) {
                    b.classList.remove('border-primary','bg-primary','bg-opacity-10');
                    b.style.borderColor = '';
                });
                this.classList.add('border-primary','bg-primary','bg-opacity-10');
            });
        });

        custom.oninput = function() {
            if (this.value.trim()) {
                hidden.value = this.value.trim();
                preview.textContent = this.value.trim();
                grid.querySelectorAll('.goal-icon-opt').forEach(function(b) {
                    b.classList.remove('border-primary','bg-primary','bg-opacity-10');
                });
            }
        };
    }

    window.openGoalModal = function (id) {
        clearForm(['goalId','goalName','goalCategory','goalFormTarget','goalFormSaved','goalDate','goalPriority','goalStatus','goalIcon','goalNotes','goalIconCustom']);
        document.getElementById('goalPriority').value = 'MEDIUM';
        document.getElementById('goalStatus').value = 'IN_PROGRESS';
        document.getElementById('goalFormSaved').value = '0';
        document.getElementById('goalModalTitle').innerHTML = '<i class="fas fa-star me-2"></i>' + (id ? 'Edit Goal' : 'Add Goal');
        var iconVal = '🎯';
        if (id) {
            var goal = goalData.find(function (x) { return x.id === id; });
            if (goal) {
                document.getElementById('goalId').value = goal.id;
                document.getElementById('goalName').value = goal.goalName || '';
                document.getElementById('goalCategory').value = goal.category || '';
                document.getElementById('goalFormTarget').value = goal.targetAmount || '';
                document.getElementById('goalFormSaved').value = goal.savedAmount || '0';
                document.getElementById('goalDate').value = goal.targetDate || '';
                document.getElementById('goalPriority').value = goal.priority || 'MEDIUM';
                document.getElementById('goalStatus').value = goal.status || 'IN_PROGRESS';
                document.getElementById('goalNotes').value = goal.notes || '';
                iconVal = goal.icon || '🎯';
            }
        }
        initGoalIconPicker(iconVal);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('goalModal')).show();
    };

    window.saveGoal = async function () {
        var id = document.getElementById('goalId').value;
        var body = {
            goalName: document.getElementById('goalName').value.trim(),
            category: document.getElementById('goalCategory').value.trim(),
            targetAmount: parseFloat(document.getElementById('goalFormTarget').value) || 0,
            savedAmount: parseFloat(document.getElementById('goalFormSaved').value) || 0,
            targetDate: document.getElementById('goalDate').value || null,
            priority: document.getElementById('goalPriority').value,
            status: document.getElementById('goalStatus').value,
            icon: document.getElementById('goalIcon').value.trim(),
            notes: document.getElementById('goalNotes').value.trim()
        };
        if (!body.goalName || body.targetAmount <= 0) {
            showToast('Goal name and a valid target amount are required.', 'error'); return;
        }
        var url = id ? BASE + '/goals/' + id : BASE + '/goals';
        var result = await apiFetch(url, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
        if (result && !result.error) {
            showToast(id ? 'Goal updated.' : 'Goal added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('goalModal')).hide();
            loadGoals();
        } else if (result) {
            showToast(result.error || 'Save failed.', 'error');
        }
    };

    window.completeGoal = async function (id) {
        var result = await apiFetch(BASE + '/goals/' + id + '/complete', { method: 'POST' });
        if (result && !result.error) {
            showToast('🎉 Goal completed!');
            loadGoals();
        }
    };

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ DELETE CONFIRMATION
    // ══════════════════════════════════════════════════════════════════════════
    var pendingDelete = null;

    window.confirmDelete = function (type, id) {
        var names = { investment: 'investment', loan: 'loan', subscription: 'subscription', goal: 'goal' };
        document.getElementById('fpDeleteMsg').textContent = 'Are you sure you want to delete this ' + (names[type] || 'item') + '? This action cannot be undone.';
        pendingDelete = { type: type, id: id };
        bootstrap.Modal.getOrCreateInstance(document.getElementById('fpDeleteModal')).show();
    };

    document.getElementById('fpDeleteConfirmBtn').addEventListener('click', async function () {
        if (!pendingDelete) return;
        var endpoints = { investment: 'investments', loan: 'loans', subscription: 'subscriptions', goal: 'goals' };
        var url = BASE + '/' + endpoints[pendingDelete.type] + '/' + pendingDelete.id;
        var result = await apiFetch(url, { method: 'DELETE' });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('fpDeleteModal')).hide();
        if (result !== null) {
            showToast('Deleted successfully.');
            if (pendingDelete.type === 'investment') loadInvestments();
            else if (pendingDelete.type === 'loan') loadLoans();
            else if (pendingDelete.type === 'subscription') loadSubscriptions();
            else if (pendingDelete.type === 'goal') loadGoals();
        }
        pendingDelete = null;
    });

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ UTILITIES
    // ══════════════════════════════════════════════════════════════════════════
    function clearForm(ids) {
        ids.forEach(function (id) {
            var el = document.getElementById(id);
            if (!el) return;
            if (el.type === 'checkbox') el.checked = false;
            else el.value = '';
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ▌ INIT
    // ══════════════════════════════════════════════════════════════════════════
    document.addEventListener('DOMContentLoaded', function () {
        buildSubTemplateChips();
        loadInvestments();
        loadLoans();
        loadSubscriptions();
        loadGoals();
    });

})();
