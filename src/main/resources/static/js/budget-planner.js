(function () {
    'use strict';

    // ============== State ==============
    let currentPlanId = null;
    let currentPlanData = null;
    let planModal, categoryBudgetModal, savingsBudgetModal, templateModal, applyTemplateModal, bpDeleteModal;
    let planSaveMode = 'create'; // 'create' | 'edit' | 'copy' | 'duplicate'
    let duplicateSourceId = null;
    let deleteContext = null;
    let allocationChart = null, vsActualChart = null, trendChart = null;
    let templateRows = [];
    let currentTemplateId = null;
    let templateCategoriesCache = null;
    let applyTemplateId = null;
    let accountsCache = null;
    let savingsEditId = null; // set when editing an existing savings goal, null when creating

    // ============== Helpers ==============
    const CURRENCY_DEFAULTS = { currencySymbol: '৳', symbolPosition: 'before', decimalPlaces: 0 };

    function getUserStorageKey(baseKey) {
        try {
            const raw = localStorage.getItem('finzin_user') || localStorage.getItem('user');
            const u = JSON.parse(raw);
            if (u && u.id) return baseKey + '_' + u.id;
        } catch (e) {}
        return baseKey;
    }

    function getCurrencySettings() {
        try {
            const s = Object.assign({}, CURRENCY_DEFAULTS, JSON.parse(localStorage.getItem(getUserStorageKey('fintrack_settings')) || '{}'));
            return { symbol: s.currencySymbol || '৳', position: s.symbolPosition || 'before', decimals: Number(s.decimalPlaces) >= 0 ? Number(s.decimalPlaces) : 0 };
        } catch (e) { return { symbol: '৳', position: 'before', decimals: 0 }; }
    }

    function formatAmount(value) {
        const c = getCurrencySettings();
        const num = Number(value || 0).toFixed(c.decimals);
        return c.position === 'before' ? c.symbol + num : num + ' ' + c.symbol;
    }

    function showTopNotification(message, type) {
        type = type || 'success';
        const container = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = `notification-toast notification-${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 5000);
    }

    function ymd(d) {
        return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
    }

    async function safeJson(response) {
        try { return await response.json(); } catch (e) { return null; }
    }

    // ============== Init ==============
    document.addEventListener('DOMContentLoaded', function () {
        planModal = new bootstrap.Modal(document.getElementById('planModal'));
        categoryBudgetModal = new bootstrap.Modal(document.getElementById('categoryBudgetModal'));
        savingsBudgetModal = new bootstrap.Modal(document.getElementById('savingsBudgetModal'));
        templateModal = new bootstrap.Modal(document.getElementById('templateModal'));
        applyTemplateModal = new bootstrap.Modal(document.getElementById('applyTemplateModal'));
        bpDeleteModal = new bootstrap.Modal(document.getElementById('bpDeleteModal'));

        document.getElementById('planSelect').addEventListener('change', function () { loadPlan(this.value); });
        document.getElementById('openCreatePlanBtn').addEventListener('click', openCreatePlanModal);
        document.getElementById('copyPreviousBtn').addEventListener('click', copyPreviousBudget);
        document.getElementById('savePlanBtn').addEventListener('click', savePlan);
        document.getElementById('saveCategoryBudgetBtn').addEventListener('click', saveCategoryBudget);
        document.getElementById('saveSavingsBudgetBtn').addEventListener('click', saveSavingsBudget);
        document.getElementById('toggleNewSavingsCategoryLink').addEventListener('click', toggleNewSavingsCategoryFields);
        document.getElementById('savingsBudgetSourceAccount').addEventListener('change', handleSourceAccountChange);
        document.getElementById('saveTemplateBtn').addEventListener('click', saveTemplate);
        document.getElementById('confirmApplyTemplateBtn').addEventListener('click', confirmApplyTemplate);
        document.getElementById('bpConfirmDeleteBtn').addEventListener('click', handleConfirmedDelete);

        loadPlanList();
    });

    // ============== Plan list / selection ==============
    async function loadPlanList(selectId) {
        const plans = await fetch('/api/budget-plans').then(r => r.json());
        const select = document.getElementById('planSelect');
        if (!plans.length) {
            select.innerHTML = '<option value="">No budgets yet</option>';
            document.getElementById('bpNoPlanState').classList.remove('d-none');
            document.getElementById('bpPlanContent').classList.add('d-none');
            currentPlanId = null;
            currentPlanData = null;
            return;
        }
        select.innerHTML = plans.map(p => `<option value="${p.id}">${p.name} (${p.period})</option>`).join('');
        let idToSelect = selectId;
        if (!idToSelect) {
            const currentMonthKey = ymd(new Date()).slice(0, 7);
            const match = plans.find(p => p.status === 'ACTIVE' && p.period === currentMonthKey) || plans[0];
            idToSelect = match.id;
        }
        select.value = idToSelect;
        await loadPlan(idToSelect);
    }
    window.loadPlanList = loadPlanList;

    async function loadPlan(id) {
        if (!id) {
            document.getElementById('bpNoPlanState').classList.remove('d-none');
            document.getElementById('bpPlanContent').classList.add('d-none');
            return;
        }
        const data = await fetch(`/api/budget-plans/${id}/full`).then(r => r.json());
        currentPlanId = Number(id);
        currentPlanData = data;
        document.getElementById('bpNoPlanState').classList.add('d-none');
        document.getElementById('bpPlanContent').classList.remove('d-none');
        renderOverview();
        [allocationChart, vsActualChart, trendChart].forEach(c => { if (c) c.destroy(); });
        allocationChart = vsActualChart = trendChart = null;
    }
    window.loadPlan = loadPlan;

    // ============== Overview rendering ==============
    function renderOverview() {
        const p = currentPlanData;
        document.getElementById('bpPlanName').textContent = p.name;
        document.getElementById('bpPlanMeta').textContent = `${p.periodType} · ${p.period} · ${p.startDate} to ${p.endDate}`;
        const badge = document.getElementById('bpPlanStatusBadge');
        badge.className = 'bp-status-badge bp-status-' + p.status;
        badge.textContent = p.status;

        renderSummaryCards();
        renderCategoryCards();
        renderSavingsCards();
        renderScore();
    }

    function renderSummaryCards() {
        const s = currentPlanData.summary;
        const cards = [
            { label: 'Planned Income', value: formatAmount(s.plannedIncome), icon: 'fa-arrow-trend-up', color: '#28a745' },
            { label: 'Planned Expenses', value: formatAmount(s.plannedExpense), icon: 'fa-arrow-trend-down', color: '#dc3545' },
            { label: 'Planned Savings', value: formatAmount(s.plannedSavings), icon: 'fa-piggy-bank', color: '#3b82f6' },
            { label: 'Remaining Budget', value: formatAmount(s.remaining), icon: 'fa-wallet', color: s.remaining >= 0 ? '#28a745' : '#dc3545' },
            { label: 'Utilization', value: s.utilizationPercent.toFixed(0) + '%', icon: 'fa-gauge-high', color: '#fd7e14' },
            { label: 'Active Budgets', value: s.activeBudgetsCount, icon: 'fa-list-check', color: 'var(--accent-color)' }
        ];
        document.getElementById('bpSummaryCards').innerHTML = cards.map(c => `
            <div class="col-md-2 col-6">
                <div class="card bp-stat-card h-100">
                    <i class="fas ${c.icon} mb-2" style="color:${c.color}; font-size:1.3rem;"></i>
                    <div class="bp-stat-value">${c.value}</div>
                    <div class="bp-stat-label">${c.label}</div>
                </div>
            </div>`).join('');
    }

    function renderCategoryCards() {
        const container = document.getElementById('bpCategoryCards');
        const cats = currentPlanData.categories;
        if (!cats.length) {
            container.innerHTML = '<div class="col-12"><p class="text-muted small">No category budgets yet. Add one to start tracking.</p></div>';
            return;
        }
        container.innerHTML = cats.map(c => {
            const pct = Math.min(100, c.percentUsed);
            const barClass = c.percentUsed > 100 ? 'over' : (c.percentUsed >= 80 ? 'warn' : '');
            return `
            <div class="col-md-6">
                <div class="card bp-card h-100" style="border-left-color: ${c.categoryColor}">
                    <div class="card-body">
                        <div class="d-flex justify-content-between align-items-start">
                            <h6 class="mb-0"><i class="fas fa-${c.categoryIcon || 'tag'} me-1"></i> ${c.categoryName}</h6>
                            <div class="d-flex align-items-center gap-1">
                                <span class="bp-status-badge bp-status-${c.status}">${c.status.replace('_', ' ')}</span>
                                <button class="bp-action-btn" title="Edit" onclick="BudgetPlanner.openCategoryBudgetModal(${c.categoryId}, ${c.budgetAmount})"><i class="fas fa-pen"></i></button>
                                <button class="bp-action-btn danger" title="Delete" onclick="BudgetPlanner.confirmDeleteCategoryBudget(${c.id})"><i class="fas fa-trash"></i></button>
                            </div>
                        </div>
                        <div class="bp-progress my-2"><div class="bp-progress-bar ${barClass}" style="width:${pct}%"></div></div>
                        <div class="d-flex justify-content-between small text-muted">
                            <span>${formatAmount(c.actualAmount)} spent</span>
                            <span>of ${formatAmount(c.budgetAmount)}</span>
                        </div>
                        ${c.suggestion ? `<div class="bp-suggestion"><i class="fas fa-lightbulb me-1"></i>${c.suggestion}</div>` : ''}
                    </div>
                </div>
            </div>`;
        }).join('');
    }

    function renderSavingsCards() {
        const container = document.getElementById('bpSavingsCards');
        const rows = currentPlanData.savings;
        if (!rows.length) {
            container.innerHTML = '<p class="text-muted small">No savings goals yet.</p>';
            return;
        }
        container.innerHTML = rows.map(s => {
            const pct = Math.min(100, s.percentUsed);
            const accountLines = [];
            if (s.storageAccountName) accountLines.push(`<i class="fas fa-university me-1"></i>Stored in: ${s.storageAccountName}`);
            if (s.sourceAccountName) accountLines.push(`<i class="fas fa-money-bill-wave me-1"></i>Funded from: ${s.sourceAccountName}`);
            else if (s.sourceDescription) accountLines.push(`<i class="fas fa-money-bill-wave me-1"></i>Funded from: ${s.sourceDescription} <span class="text-muted">(external)</span>`);
            return `
            <div class="card bp-card mb-2" style="border-left-color:${s.categoryColor}">
                <div class="card-body py-2">
                    <div class="d-flex justify-content-between align-items-center">
                        <h6 class="mb-0 small">${s.categoryName}</h6>
                        <div class="d-flex align-items-center gap-1">
                            <span class="bp-status-badge bp-status-${s.status}">${s.status === 'GOAL_ACHIEVED' ? 'Achieved' : 'In Progress'}</span>
                            <button class="bp-action-btn" title="Edit" onclick="BudgetPlanner.openEditSavingsBudgetModal(${s.id})"><i class="fas fa-pen"></i></button>
                            <button class="bp-action-btn danger" title="Delete" onclick="BudgetPlanner.confirmDeleteSavingsBudget(${s.id})"><i class="fas fa-trash"></i></button>
                        </div>
                    </div>
                    <div class="bp-progress my-1"><div class="bp-progress-bar" style="width:${pct}%"></div></div>
                    <div class="d-flex justify-content-between small text-muted">
                        <span>${formatAmount(s.currentAmount)} saved${s.initialAmount ? ' (incl. ' + formatAmount(s.initialAmount) + ' already saved)' : ''}</span>
                        <span>of ${formatAmount(s.targetAmount)}</span>
                    </div>
                    ${accountLines.length ? `<div class="small text-muted mt-1">${accountLines.join(' &middot; ')}</div>` : ''}
                </div>
            </div>`;
        }).join('');
    }

    function renderScore() {
        document.getElementById('bpScoreCircle').style.setProperty('--score', currentPlanData.score);
        document.getElementById('bpScoreValue').textContent = currentPlanData.score;
    }

    // ============== Plan create/edit/copy/duplicate ==============
    function openCreatePlanModal() {
        planSaveMode = 'create';
        document.getElementById('planModalTitle').innerHTML = '<i class="fas fa-wallet me-2"></i>Create Budget';
        document.getElementById('planName').value = '';
        const now = new Date();
        document.getElementById('planPeriodType').value = 'MONTH';
        document.getElementById('planPeriod').value = ymd(now).slice(0, 7);
        const first = new Date(now.getFullYear(), now.getMonth(), 1);
        const last = new Date(now.getFullYear(), now.getMonth() + 1, 0);
        document.getElementById('planStartDate').value = ymd(first);
        document.getElementById('planEndDate').value = ymd(last);
        document.getElementById('planIncome').value = '';
        document.getElementById('planSavings').value = '';
        document.getElementById('planNotes').value = '';
        planModal.show();
    }
    window.openCreatePlanModal = openCreatePlanModal;

    function openEditPlanModal() {
        if (!currentPlanData) return;
        planSaveMode = 'edit';
        document.getElementById('planModalTitle').innerHTML = '<i class="fas fa-pen me-2"></i>Edit Budget';
        document.getElementById('planName').value = currentPlanData.name;
        document.getElementById('planPeriodType').value = currentPlanData.periodType;
        document.getElementById('planPeriod').value = currentPlanData.period;
        document.getElementById('planStartDate').value = currentPlanData.startDate;
        document.getElementById('planEndDate').value = currentPlanData.endDate;
        document.getElementById('planIncome').value = currentPlanData.plannedIncome;
        document.getElementById('planSavings').value = currentPlanData.plannedSavings;
        document.getElementById('planNotes').value = currentPlanData.notes || '';
        planModal.show();
    }
    window.openEditPlanModal = openEditPlanModal;

    function copyPreviousBudget() {
        openCreatePlanModal();
        planSaveMode = 'copy';
        document.getElementById('planModalTitle').innerHTML = '<i class="fas fa-clone me-2"></i>Copy Last Month';
    }

    function openDuplicateModal(id) {
        planSaveMode = 'duplicate';
        duplicateSourceId = id;
        document.getElementById('planModalTitle').innerHTML = '<i class="fas fa-clone me-2"></i>Duplicate Budget';
        fetch(`/api/budget-plans/${id}/full`).then(r => r.json()).then(p => {
            document.getElementById('planName').value = p.name + ' (Copy)';
            document.getElementById('planPeriodType').value = p.periodType;
            document.getElementById('planPeriod').value = p.period;
            document.getElementById('planStartDate').value = p.startDate;
            document.getElementById('planEndDate').value = p.endDate;
            document.getElementById('planIncome').value = p.plannedIncome;
            document.getElementById('planSavings').value = p.plannedSavings;
            document.getElementById('planNotes').value = p.notes || '';
            planModal.show();
        });
    }
    window.openDuplicateModal = openDuplicateModal;

    async function savePlan() {
        const payload = {
            name: document.getElementById('planName').value.trim(),
            periodType: document.getElementById('planPeriodType').value,
            period: document.getElementById('planPeriod').value.trim(),
            startDate: document.getElementById('planStartDate').value,
            endDate: document.getElementById('planEndDate').value,
            plannedIncome: parseFloat(document.getElementById('planIncome').value) || 0,
            plannedSavings: parseFloat(document.getElementById('planSavings').value) || 0,
            notes: document.getElementById('planNotes').value.trim()
        };
        if (!payload.name) { showTopNotification('Budget name is required.', 'error'); return; }
        if (!payload.period) { showTopNotification('Period label is required.', 'error'); return; }
        if (!payload.startDate || !payload.endDate) { showTopNotification('Start and end dates are required.', 'error'); return; }

        let url, method;
        if (planSaveMode === 'edit') { url = `/api/budget-plans/${currentPlanId}`; method = 'PUT'; }
        else if (planSaveMode === 'copy') { url = '/api/budget-plans/copy-previous'; method = 'POST'; }
        else if (planSaveMode === 'duplicate') { url = `/api/budget-plans/${duplicateSourceId}/duplicate`; method = 'POST'; }
        else { url = '/api/budget-plans'; method = 'POST'; }

        try {
            const resp = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (resp.ok) {
                const saved = await resp.json();
                planModal.hide();
                showTopNotification(planSaveMode === 'edit' ? 'Budget updated.' : 'Budget saved.', 'success');
                await loadPlanList(saved.id);
            } else {
                const err = await safeJson(resp);
                showTopNotification((err && err.error) || 'Failed to save budget.', 'error');
            }
        } catch (e) {
            showTopNotification('Unable to save budget right now.', 'error');
        }
    }

    function confirmDeletePlan(id) {
        deleteContext = { type: 'plan', id };
        document.getElementById('bpDeleteModalTitle').textContent = 'Delete Budget';
        document.getElementById('bpDeleteModalBody').textContent = 'Delete this budget and all its category/savings allocations? This cannot be undone.';
        bpDeleteModal.show();
    }
    window.confirmDeletePlan = confirmDeletePlan;

    async function toggleArchive(id, status) {
        if (status === 'ARCHIVED') { showTopNotification('This budget is already archived.', 'error'); return; }
        const resp = await fetch(`/api/budget-plans/${id}/archive`, { method: 'PATCH' });
        if (resp.ok) {
            showTopNotification('Budget archived.', 'success');
            loadHistory();
            if (Number(id) === currentPlanId) loadPlan(id);
        } else {
            showTopNotification('Failed to archive.', 'error');
        }
    }
    window.toggleArchive = toggleArchive;

    // ============== Category budget modal ==============
    async function openCategoryBudgetModal(categoryId, amount) {
        document.getElementById('categoryBudgetAmount').value = amount || '';
        try {
            const cats = await fetch('/api/categories?type=expense').then(r => r.json());
            document.getElementById('categoryBudgetCategory').innerHTML = '<option value="">Select category</option>' +
                cats.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
        } catch (e) { showTopNotification('Unable to load categories.', 'error'); }
        if (categoryId) document.getElementById('categoryBudgetCategory').value = categoryId;
        categoryBudgetModal.show();
    }
    window.openCategoryBudgetModal = openCategoryBudgetModal;

    async function saveCategoryBudget() {
        const categoryId = parseInt(document.getElementById('categoryBudgetCategory').value, 10);
        const amount = parseFloat(document.getElementById('categoryBudgetAmount').value);
        if (!categoryId || !amount || amount <= 0) { showTopNotification('Select a category and enter a valid amount.', 'error'); return; }
        try {
            const resp = await fetch(`/api/budget-plans/${currentPlanId}/categories`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ categoryId, amount })
            });
            if (resp.ok) {
                categoryBudgetModal.hide();
                showTopNotification('Category budget saved.', 'success');
                loadPlan(currentPlanId);
            } else {
                const err = await safeJson(resp);
                showTopNotification((err && err.error) || 'Failed to save.', 'error');
            }
        } catch (e) { showTopNotification('Unable to save right now.', 'error'); }
    }

    function confirmDeleteCategoryBudget(id) {
        deleteContext = { type: 'category', id };
        document.getElementById('bpDeleteModalTitle').textContent = 'Delete Category Budget';
        document.getElementById('bpDeleteModalBody').textContent = 'Remove this category budget allocation?';
        bpDeleteModal.show();
    }
    window.confirmDeleteCategoryBudget = confirmDeleteCategoryBudget;

    // ============== Savings budget modal ==============
    function accountLabel(a) {
        return a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : '');
    }

    async function loadAccountsCache() {
        if (accountsCache) return accountsCache;
        try {
            accountsCache = await fetch('/api/accounts').then(r => r.json());
        } catch (e) { accountsCache = []; }
        return accountsCache;
    }

    async function populateSavingsAccountSelects(storageAccountId, sourceAccountId, sourceDescription) {
        const accounts = await loadAccountsCache();
        const accountOptions = accounts.map(a => `<option value="${a.id}">${accountLabel(a)}</option>`).join('');
        const storageSel = document.getElementById('savingsBudgetStorageAccount');
        const sourceSel = document.getElementById('savingsBudgetSourceAccount');
        const sourceDescInput = document.getElementById('savingsBudgetSourceDescription');
        storageSel.innerHTML = '<option value="">-- Not specified --</option>' + accountOptions;
        sourceSel.innerHTML = '<option value="">-- Not specified --</option>' +
            '<option value="__other__">Other (bonus, gift, etc.) — describe...</option>' + accountOptions;
        storageSel.value = storageAccountId || '';
        if (sourceAccountId) {
            sourceSel.value = sourceAccountId;
            sourceDescInput.classList.add('d-none');
            sourceDescInput.value = '';
        } else if (sourceDescription) {
            sourceSel.value = '__other__';
            sourceDescInput.classList.remove('d-none');
            sourceDescInput.value = sourceDescription;
        } else {
            sourceSel.value = '';
            sourceDescInput.classList.add('d-none');
            sourceDescInput.value = '';
        }
    }

    function handleSourceAccountChange() {
        const sourceSel = document.getElementById('savingsBudgetSourceAccount');
        const sourceDescInput = document.getElementById('savingsBudgetSourceDescription');
        const isOther = sourceSel.value === '__other__';
        sourceDescInput.classList.toggle('d-none', !isOther);
        if (isOther) sourceDescInput.focus(); else sourceDescInput.value = '';
    }

    function toggleNewSavingsCategoryFields() {
        const fields = document.getElementById('newSavingsCategoryFields');
        const select = document.getElementById('savingsBudgetCategory');
        const link = document.getElementById('toggleNewSavingsCategoryLink');
        const showingNew = fields.classList.contains('d-none');
        fields.classList.toggle('d-none', !showingNew);
        select.classList.toggle('d-none', showingNew);
        link.textContent = showingNew ? '✕ Cancel' : '+ Create new category';
        if (showingNew) { select.value = ''; document.getElementById('newSavingsCategoryName').focus(); }
    }

    async function refreshSavingsCategoryOptions(selectedId) {
        try {
            const cats = await fetch('/api/categories?type=savings').then(r => r.json());
            document.getElementById('savingsBudgetCategory').innerHTML = '<option value="">Select category</option>' +
                cats.map(c => `<option value="${c.id}">${c.name}</option>`).join('');
        } catch (e) { showTopNotification('Unable to load categories.', 'error'); }
        if (selectedId) document.getElementById('savingsBudgetCategory').value = selectedId;
    }

    async function openSavingsBudgetModal(categoryId, amount) {
        savingsEditId = null;
        document.getElementById('savingsBudgetModalTitle').innerHTML = '<i class="fas fa-piggy-bank me-2"></i>New Savings Goal';
        document.getElementById('savingsBudgetAmount').value = amount || '';
        document.getElementById('savingsBudgetInitialAmount').value = '';
        document.getElementById('newSavingsCategoryName').value = '';
        document.getElementById('newSavingsCategoryFields').classList.add('d-none');
        document.getElementById('toggleNewSavingsCategoryLink').classList.remove('d-none');
        document.getElementById('toggleNewSavingsCategoryLink').textContent = '+ Create new category';
        document.getElementById('savingsBudgetCategory').classList.remove('d-none');
        document.getElementById('savingsBudgetCategoryReadonly').classList.add('d-none');
        document.getElementById('savingsBudgetContributedInfo').classList.add('d-none');
        await refreshSavingsCategoryOptions(categoryId);
        await populateSavingsAccountSelects(null, null, null);
        savingsBudgetModal.show();
    }
    window.openSavingsBudgetModal = openSavingsBudgetModal;

    async function openEditSavingsBudgetModal(id) {
        const row = currentPlanData.savings.find(s => s.id === id);
        if (!row) return;
        savingsEditId = id;
        document.getElementById('savingsBudgetModalTitle').innerHTML = '<i class="fas fa-pen me-2"></i>Edit Savings Goal';
        document.getElementById('savingsBudgetAmount').value = row.targetAmount;
        document.getElementById('savingsBudgetInitialAmount').value = row.initialAmount || '';
        document.getElementById('newSavingsCategoryFields').classList.add('d-none');
        document.getElementById('toggleNewSavingsCategoryLink').classList.add('d-none');
        // Category is the natural key for this row — lock it during edit to avoid creating a duplicate goal.
        document.getElementById('savingsBudgetCategory').classList.add('d-none');
        const readonly = document.getElementById('savingsBudgetCategoryReadonly');
        readonly.textContent = row.categoryName;
        readonly.classList.remove('d-none');
        document.getElementById('savingsBudgetCategory').innerHTML = `<option value="${row.categoryId}">${row.categoryName}</option>`;
        document.getElementById('savingsBudgetCategory').value = row.categoryId;
        const contributedInfo = document.getElementById('savingsBudgetContributedInfo');
        if (row.contributedAmount) {
            contributedInfo.textContent = `${formatAmount(row.contributedAmount)} of this goal is already tracked automatically from your transactions — no need to add it below.`;
            contributedInfo.classList.remove('d-none');
        } else {
            contributedInfo.classList.add('d-none');
        }
        await populateSavingsAccountSelects(row.storageAccountId, row.sourceAccountId, row.sourceDescription);
        savingsBudgetModal.show();
    }
    window.openEditSavingsBudgetModal = openEditSavingsBudgetModal;

    async function saveSavingsBudget() {
        let categoryId = parseInt(document.getElementById('savingsBudgetCategory').value, 10);
        const creatingNew = !document.getElementById('newSavingsCategoryFields').classList.contains('d-none');
        const amount = parseFloat(document.getElementById('savingsBudgetAmount').value);
        const initialAmountRaw = document.getElementById('savingsBudgetInitialAmount').value;
        const initialAmount = initialAmountRaw === '' ? 0 : parseFloat(initialAmountRaw);
        const storageAccountId = document.getElementById('savingsBudgetStorageAccount').value || null;
        const sourceSelectValue = document.getElementById('savingsBudgetSourceAccount').value;
        const isExternalSource = sourceSelectValue === '__other__';
        const sourceAccountId = (sourceSelectValue && !isExternalSource) ? sourceSelectValue : null;
        const sourceDescription = isExternalSource ? document.getElementById('savingsBudgetSourceDescription').value.trim() : null;

        if (isExternalSource && !sourceDescription) { showTopNotification('Describe where this money came from (e.g., Bonus, Gift).', 'error'); return; }

        if (creatingNew) {
            const name = document.getElementById('newSavingsCategoryName').value.trim();
            if (!name) { showTopNotification('Enter a name for the new savings category.', 'error'); return; }
            const color = document.getElementById('newSavingsCategoryColor').value;
            try {
                const catResp = await fetch('/api/categories', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name, categoryType: 'savings', color })
                });
                if (!catResp.ok) {
                    const err = await safeJson(catResp);
                    showTopNotification((err && err.error) || 'Failed to create category.', 'error');
                    return;
                }
                const cat = await catResp.json();
                categoryId = cat.id;
            } catch (e) { showTopNotification('Unable to create category right now.', 'error'); return; }
        }

        if (!categoryId || !amount || amount <= 0) { showTopNotification('Select a category and enter a valid target.', 'error'); return; }
        if (initialAmount < 0) { showTopNotification('Already-saved amount cannot be negative.', 'error'); return; }
        try {
            const resp = await fetch(`/api/budget-plans/${currentPlanId}/savings`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ categoryId, amount, initialAmount, storageAccountId, sourceAccountId, sourceDescription })
            });
            if (resp.ok) {
                savingsBudgetModal.hide();
                showTopNotification(savingsEditId ? 'Savings goal updated.' : 'Savings goal saved.', 'success');
                loadPlan(currentPlanId);
            } else {
                const err = await safeJson(resp);
                showTopNotification((err && err.error) || 'Failed to save.', 'error');
            }
        } catch (e) { showTopNotification('Unable to save right now.', 'error'); }
    }

    function confirmDeleteSavingsBudget(id) {
        deleteContext = { type: 'savings', id };
        document.getElementById('bpDeleteModalTitle').textContent = 'Delete Savings Goal';
        document.getElementById('bpDeleteModalBody').textContent = 'Remove this savings goal?';
        bpDeleteModal.show();
    }
    window.confirmDeleteSavingsBudget = confirmDeleteSavingsBudget;

    // ============== Generic delete handler ==============
    async function handleConfirmedDelete() {
        if (!deleteContext) return;
        const { type, id } = deleteContext;
        let url;
        if (type === 'category') url = `/api/budget-plans/${currentPlanId}/categories/${id}`;
        else if (type === 'savings') url = `/api/budget-plans/${currentPlanId}/savings/${id}`;
        else if (type === 'plan') url = `/api/budget-plans/${id}`;
        else if (type === 'template') url = `/api/budget-templates/${id}`;
        try {
            const resp = await fetch(url, { method: 'DELETE' });
            bpDeleteModal.hide();
            if (resp.ok || resp.status === 204) {
                showTopNotification('Deleted.', 'success');
                if (type === 'plan') { currentPlanId = null; await loadPlanList(); }
                else if (type === 'template') { loadTemplates(); }
                else { loadPlan(currentPlanId); }
            } else {
                showTopNotification('Failed to delete.', 'error');
            }
        } catch (e) {
            showTopNotification('Unable to delete right now.', 'error');
        }
        deleteContext = null;
    }

    // ============== Export ==============
    async function exportPlan(format) {
        if (!currentPlanId) { showTopNotification('No budget selected.', 'error'); return; }
        try {
            const resp = await fetch(`/api/budget-plans/${currentPlanId}/export/${format}`);
            if (!resp.ok) { showTopNotification('Export failed.', 'error'); return; }
            const blob = await resp.blob();
            const ext = format === 'excel' ? 'xlsx' : format;
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `budget-${currentPlanData.period}.${ext}`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
        } catch (e) {
            showTopNotification('Export failed.', 'error');
        }
    }
    window.exportPlan = exportPlan;

    // ============== Charts ==============
    function renderCharts() {
        if (!currentPlanData) return;
        renderAllocationChart();
        renderVsActualChart();
        renderTrendChart();
    }
    window.renderCharts = renderCharts;

    function renderAllocationChart() {
        const canvas = document.getElementById('bpAllocationChart');
        if (!canvas) return;
        if (allocationChart) allocationChart.destroy();
        const cats = currentPlanData.categories;
        allocationChart = new Chart(canvas.getContext('2d'), {
            type: 'doughnut',
            data: { labels: cats.map(c => c.categoryName), datasets: [{ data: cats.map(c => c.budgetAmount), backgroundColor: cats.map(c => c.categoryColor) }] },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    function renderVsActualChart() {
        const canvas = document.getElementById('bpVsActualChart');
        if (!canvas) return;
        if (vsActualChart) vsActualChart.destroy();
        const cats = currentPlanData.categories;
        vsActualChart = new Chart(canvas.getContext('2d'), {
            type: 'bar',
            data: {
                labels: cats.map(c => c.categoryName),
                datasets: [
                    { label: 'Budget', data: cats.map(c => c.budgetAmount), backgroundColor: '#3b82f6' },
                    { label: 'Actual', data: cats.map(c => c.actualAmount), backgroundColor: '#f59e0b' }
                ]
            },
            options: { responsive: true, maintainAspectRatio: false }
        });
    }

    async function renderTrendChart() {
        const canvas = document.getElementById('bpTrendChart');
        if (!canvas) return;
        if (trendChart) trendChart.destroy();
        const plans = await fetch('/api/budget-plans?sort=startDate').then(r => r.json());
        const sorted = plans.slice();
        trendChart = new Chart(canvas.getContext('2d'), {
            type: 'line',
            data: {
                labels: sorted.map(p => p.period),
                datasets: [
                    { label: 'Planned Expense', data: sorted.map(p => p.summary.plannedExpense), borderColor: '#3b82f6', tension: 0.3 },
                    { label: 'Actual Expense', data: sorted.map(p => p.summary.actualExpense), borderColor: '#dc3545', tension: 0.3 },
                    { label: 'Utilization %', data: sorted.map(p => p.summary.utilizationPercent), borderColor: '#28a745', tension: 0.3, yAxisID: 'y1' }
                ]
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                scales: { y1: { position: 'right', grid: { drawOnChartArea: false } } }
            }
        });
    }

    // ============== Calendar ==============
    async function renderCalendar() {
        if (!currentPlanData) return;
        const grid = document.getElementById('bpCalendarGrid');
        grid.innerHTML = '<p class="text-muted small">Loading…</p>';

        const start = new Date(currentPlanData.startDate + 'T00:00:00');
        const end = new Date(currentPlanData.endDate + 'T23:59:59');
        const txs = await fetch('/api/transactions?limit=1000').then(r => r.json());
        const inRange = txs.filter(t => { const d = new Date(t.date); return d >= start && d <= end; });

        const byDay = {};
        inRange.forEach(t => {
            const key = t.date.slice(0, 10);
            if (!byDay[key]) byDay[key] = { income: false, expense: false, savings: false };
            if (t.transaction_type === 'income') byDay[key].income = true;
            else if (t.transaction_type === 'expense') byDay[key].expense = true;
            else if (t.transaction_type === 'savings') byDay[key].savings = true;
        });

        const catBudgetMap = {};
        currentPlanData.categories.forEach(c => { catBudgetMap[c.categoryId] = c.budgetAmount; });
        const cumSpend = {};
        const exceededDays = new Set();
        inRange.filter(t => t.transaction_type === 'expense' && t.category_id)
            .sort((a, b) => new Date(a.date) - new Date(b.date))
            .forEach(t => {
                const catId = t.category_id;
                if (!(catId in catBudgetMap)) return;
                cumSpend[catId] = (cumSpend[catId] || 0) + t.amount;
                if (cumSpend[catId] > catBudgetMap[catId]) exceededDays.add(t.date.slice(0, 10));
            });

        const gridMonth = new Date(start.getFullYear(), start.getMonth(), 1);
        const firstDow = gridMonth.getDay();
        const daysInMonth = new Date(gridMonth.getFullYear(), gridMonth.getMonth() + 1, 0).getDate();

        let html = '';
        for (let i = 0; i < firstDow; i++) html += '<div class="bp-cal-cell empty"></div>';
        for (let d = 1; d <= daysInMonth; d++) {
            const key = gridMonth.getFullYear() + '-' + String(gridMonth.getMonth() + 1).padStart(2, '0') + '-' + String(d).padStart(2, '0');
            const info = byDay[key];
            let dots = '';
            if (info) {
                if (info.income) dots += '<span class="bp-cal-dot bp-dot-income"></span>';
                if (info.expense) dots += '<span class="bp-cal-dot bp-dot-expense"></span>';
                if (info.savings) dots += '<span class="bp-cal-dot bp-dot-savings"></span>';
            }
            if (exceededDays.has(key)) dots += '<span class="bp-cal-dot bp-dot-exceeded"></span>';
            html += `<div class="bp-cal-cell"><div class="bp-cal-date">${d}</div><div class="bp-cal-dots">${dots}</div></div>`;
        }
        grid.innerHTML = html;
    }
    window.renderCalendar = renderCalendar;

    // ============== History ==============
    async function loadHistory() {
        const search = document.getElementById('bpHistorySearch').value;
        const status = document.getElementById('bpHistoryStatus').value;
        const sort = document.getElementById('bpHistorySort').value;
        const params = new URLSearchParams();
        if (search) params.set('search', search);
        if (status) params.set('status', status);
        if (sort) params.set('sort', sort);

        const tbody = document.getElementById('bpHistoryBody');
        try {
            const plans = await fetch('/api/budget-plans?' + params.toString()).then(r => r.json());
            if (!plans.length) {
                tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">No budgets found.</td></tr>';
                return;
            }
            tbody.innerHTML = plans.map(p => `
                <tr class="bp-history-row">
                    <td>${p.name}</td>
                    <td>${p.period}</td>
                    <td>${formatAmount(p.summary.actualIncome)}</td>
                    <td>${formatAmount(p.summary.actualExpense)}</td>
                    <td>${formatAmount(p.summary.actualSavings)}</td>
                    <td><div class="bp-score-mini" style="--score:${p.score}"><div class="bp-score-mini-inner">${p.score}</div></div></td>
                    <td><span class="bp-status-badge bp-status-${p.status}">${p.status}</span></td>
                    <td class="text-end">
                        <button class="bp-action-btn" title="Open" onclick="BudgetPlanner.selectPlanFromHistory(${p.id})"><i class="fas fa-eye"></i></button>
                        <button class="bp-action-btn" title="Duplicate" onclick="BudgetPlanner.openDuplicateModal(${p.id})"><i class="fas fa-clone"></i></button>
                        ${p.status !== 'ARCHIVED' ? `<button class="bp-action-btn" title="Archive" onclick="BudgetPlanner.toggleArchive(${p.id}, '${p.status}')"><i class="fas fa-box-archive"></i></button>` : ''}
                        <button class="bp-action-btn danger" title="Delete" onclick="BudgetPlanner.confirmDeletePlan(${p.id})"><i class="fas fa-trash"></i></button>
                    </td>
                </tr>`).join('');
        } catch (e) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">Unable to load budget history.</td></tr>';
        }
    }
    window.loadHistory = loadHistory;

    function selectPlanFromHistory(id) {
        document.getElementById('planSelect').value = id;
        loadPlan(id);
        const overviewTabBtn = document.querySelector('#bpTabs .nav-link');
        bootstrap.Tab.getOrCreateInstance(overviewTabBtn).show();
    }
    window.selectPlanFromHistory = selectPlanFromHistory;

    // ============== Templates ==============
    async function loadTemplates() {
        const container = document.getElementById('bpTemplateCards');
        try {
            const templates = await fetch('/api/budget-templates').then(r => r.json());
            if (!templates.length) {
                container.innerHTML = '<div class="col-12"><p class="text-muted">No templates yet. Create one to reuse across budgets — e.g. Student Budget, Family Budget, Minimal Budget, High Savings Budget.</p></div>';
                return;
            }
            container.innerHTML = templates.map(t => `
                <div class="col-md-4">
                    <div class="card h-100">
                        <div class="card-body">
                            <h6>${t.name}</h6>
                            <p class="small text-muted mb-2">Income ${formatAmount(t.plannedIncome)} · Savings ${formatAmount(t.plannedSavings)}</p>
                            <p class="small text-muted mb-3">${t.categories.length} categor${t.categories.length === 1 ? 'y' : 'ies'} allocated</p>
                            <div class="d-flex gap-2">
                                <button class="btn btn-sm btn-primary" onclick="BudgetPlanner.openApplyTemplateModal(${t.id})"><i class="fas fa-play me-1"></i> Apply</button>
                                <button class="btn btn-sm btn-outline-secondary" onclick="BudgetPlanner.openTemplateModal(${t.id})"><i class="fas fa-pen"></i></button>
                                <button class="btn btn-sm btn-outline-danger" onclick="BudgetPlanner.confirmDeleteTemplate(${t.id})"><i class="fas fa-trash"></i></button>
                            </div>
                        </div>
                    </div>
                </div>`).join('');
        } catch (e) {
            container.innerHTML = '<div class="col-12"><p class="text-muted">Unable to load templates.</p></div>';
        }
    }
    window.loadTemplates = loadTemplates;

    async function ensureCategoriesCache() {
        if (templateCategoriesCache) return templateCategoriesCache;
        templateCategoriesCache = await fetch('/api/categories').then(r => r.json());
        return templateCategoriesCache;
    }

    async function openTemplateModal(id) {
        currentTemplateId = id || null;
        document.getElementById('templateName').value = '';
        document.getElementById('templateIncome').value = '';
        document.getElementById('templateSavings').value = '';
        templateRows = [];
        await ensureCategoriesCache();
        if (id) {
            const templates = await fetch('/api/budget-templates').then(r => r.json());
            const t = templates.find(x => x.id === id);
            if (t) {
                document.getElementById('templateName').value = t.name;
                document.getElementById('templateIncome').value = t.plannedIncome;
                document.getElementById('templateSavings').value = t.plannedSavings;
                templateRows = t.categories.map(c => ({ categoryId: c.categoryId, plannedAmount: c.plannedAmount, isSavings: c.isSavings }));
            }
        }
        renderTemplateRows();
        templateModal.show();
    }
    window.openTemplateModal = openTemplateModal;

    function addTemplateRow() {
        templateRows.push({ categoryId: null, plannedAmount: 0, isSavings: false });
        renderTemplateRows();
    }
    window.addTemplateRow = addTemplateRow;

    function removeTemplateRow(idx) {
        templateRows.splice(idx, 1);
        renderTemplateRows();
    }
    window.removeTemplateRow = removeTemplateRow;

    function updateTemplateRow(idx, field, value) {
        if (field === 'plannedAmount') templateRows[idx][field] = parseFloat(value) || 0;
        else if (field === 'isSavings') { templateRows[idx][field] = value === 'true'; templateRows[idx].categoryId = null; }
        else templateRows[idx][field] = parseInt(value, 10) || null;
        if (field === 'isSavings') renderTemplateRows();
    }
    window.updateTemplateRow = updateTemplateRow;

    function renderTemplateRows() {
        const cats = templateCategoriesCache || [];
        const container = document.getElementById('templateRowsContainer');
        if (!templateRows.length) {
            container.innerHTML = '<p class="text-muted small">No allocations yet. Click "Add Row".</p>';
            return;
        }
        container.innerHTML = templateRows.map((row, idx) => {
            const filtered = cats.filter(c => row.isSavings ? c.categoryType === 'savings' : (c.categoryType === 'expense' || c.categoryType === 'general' || !c.categoryType));
            return `
            <div class="row g-2 mb-2 align-items-center">
                <div class="col-3">
                    <select class="form-select form-select-sm" onchange="BudgetPlanner.updateTemplateRow(${idx},'isSavings',this.value)">
                        <option value="false" ${!row.isSavings ? 'selected' : ''}>Expense</option>
                        <option value="true" ${row.isSavings ? 'selected' : ''}>Savings</option>
                    </select>
                </div>
                <div class="col-5">
                    <select class="form-select form-select-sm" onchange="BudgetPlanner.updateTemplateRow(${idx},'categoryId',this.value)">
                        <option value="">Select category</option>
                        ${filtered.map(c => `<option value="${c.id}" ${row.categoryId === c.id ? 'selected' : ''}>${c.name}</option>`).join('')}
                    </select>
                </div>
                <div class="col-3">
                    <input type="number" class="form-control form-control-sm" value="${row.plannedAmount || ''}" placeholder="Amount" oninput="BudgetPlanner.updateTemplateRow(${idx},'plannedAmount',this.value)">
                </div>
                <div class="col-1">
                    <button class="btn btn-sm btn-outline-danger" onclick="BudgetPlanner.removeTemplateRow(${idx})"><i class="fas fa-times"></i></button>
                </div>
            </div>`;
        }).join('');
    }

    async function saveTemplate() {
        const name = document.getElementById('templateName').value.trim();
        if (!name) { showTopNotification('Template name is required.', 'error'); return; }
        const payload = {
            name,
            plannedIncome: parseFloat(document.getElementById('templateIncome').value) || 0,
            plannedSavings: parseFloat(document.getElementById('templateSavings').value) || 0,
            categories: templateRows.filter(r => r.categoryId && r.plannedAmount > 0)
        };
        const url = currentTemplateId ? `/api/budget-templates/${currentTemplateId}` : '/api/budget-templates';
        const method = currentTemplateId ? 'PUT' : 'POST';
        try {
            const resp = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            if (resp.ok) {
                templateModal.hide();
                showTopNotification('Template saved.', 'success');
                loadTemplates();
            } else {
                const err = await safeJson(resp);
                showTopNotification((err && err.error) || 'Failed to save template.', 'error');
            }
        } catch (e) { showTopNotification('Unable to save template right now.', 'error'); }
    }

    function confirmDeleteTemplate(id) {
        deleteContext = { type: 'template', id };
        document.getElementById('bpDeleteModalTitle').textContent = 'Delete Template';
        document.getElementById('bpDeleteModalBody').textContent = 'Delete this budget template?';
        bpDeleteModal.show();
    }
    window.confirmDeleteTemplate = confirmDeleteTemplate;

    function openApplyTemplateModal(id) {
        applyTemplateId = id;
        const now = new Date();
        document.getElementById('applyName').value = '';
        document.getElementById('applyPeriodType').value = 'MONTH';
        document.getElementById('applyPeriod').value = ymd(now).slice(0, 7);
        const first = new Date(now.getFullYear(), now.getMonth(), 1);
        const last = new Date(now.getFullYear(), now.getMonth() + 1, 0);
        document.getElementById('applyStartDate').value = ymd(first);
        document.getElementById('applyEndDate').value = ymd(last);
        applyTemplateModal.show();
    }
    window.openApplyTemplateModal = openApplyTemplateModal;

    async function confirmApplyTemplate() {
        const payload = {
            name: document.getElementById('applyName').value.trim(),
            periodType: document.getElementById('applyPeriodType').value,
            period: document.getElementById('applyPeriod').value.trim(),
            startDate: document.getElementById('applyStartDate').value,
            endDate: document.getElementById('applyEndDate').value
        };
        if (!payload.periodType || !payload.period || !payload.startDate || !payload.endDate) {
            showTopNotification('Period, start date, and end date are required.', 'error');
            return;
        }
        try {
            const resp = await fetch(`/api/budget-templates/${applyTemplateId}/apply`, {
                method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
            });
            if (resp.ok) {
                applyTemplateModal.hide();
                showTopNotification('Budget created from template.', 'success');
                await loadPlanList();
            } else {
                const err = await safeJson(resp);
                showTopNotification((err && err.error) || 'Failed to apply template.', 'error');
            }
        } catch (e) { showTopNotification('Unable to apply template right now.', 'error'); }
    }

    // Expose functions referenced from inline HTML onclick attributes under one namespace.
    window.BudgetPlanner = {
        openCategoryBudgetModal, confirmDeleteCategoryBudget,
        openSavingsBudgetModal, openEditSavingsBudgetModal, confirmDeleteSavingsBudget,
        selectPlanFromHistory, openDuplicateModal, toggleArchive, confirmDeletePlan,
        openTemplateModal, addTemplateRow, removeTemplateRow, updateTemplateRow,
        confirmDeleteTemplate, openApplyTemplateModal
    };
})();
