/**
 * accounts.js — Account Configuration module for FinTrack
 */
(function () {
    'use strict';

    let allAccounts = [];
    let editAccountId = null;
    let editingOriginalCurrentBalance = null; // set when opening edit modal, used to detect an explicit override at save time
    let deleteAccountId = null;
    let accountModal = null;
    let deleteModal = null;

    document.addEventListener('DOMContentLoaded', function () {
        const modalEl = document.getElementById('accountModal');
        const deleteModalEl = document.getElementById('deleteFinAccountModal');
        if (modalEl) accountModal = new bootstrap.Modal(modalEl);
        if (deleteModalEl) deleteModal = new bootstrap.Modal(deleteModalEl);
        // On the standalone /accounts page, auto-load. On settings, loading is triggered by section click.
        if (document.getElementById('accountTableBody') && !document.querySelector('.settings-nav-item')) {
            loadAll();
        }
    });

    // Exposed so settings.html nav-click can trigger it
    window.loadAll = function () {
        loadSummary();
        loadAccounts();
    };

    // ── API helpers ──────────────────────────────────────────────────────────

    async function apiFetch(url, options) {
        try {
            const token = localStorage.getItem('token');
            if (!token) { window.location.href = '/login'; return null; }
            const headers = Object.assign({ 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token }, (options && options.headers) || {});
            const resp = await fetch(url, Object.assign({}, options, { headers }));
            if (resp.status === 401) { window.location.href = '/login'; return null; }
            if (resp.status === 204) return null;
            const ct = resp.headers.get('content-type') || '';
            const data = ct.includes('application/json') ? await resp.json() : await resp.text();
            if (!resp.ok) {
                const msg = (data && data.error) ? data.error : (typeof data === 'string' ? data : 'Request failed');
                throw new Error(msg);
            }
            return data;
        } catch (err) {
            throw err;
        }
    }

    // ── Summary ──────────────────────────────────────────────────────────────

    async function loadSummary() {
        try {
            const s = await apiFetch('/api/accounts/summary');
            if (!s) return;
            setEl('summaryBank',             '৳' + fmt(s.totalBank));
            setEl('summaryMfs',              '৳' + fmt(s.totalMfs));
            setEl('summaryCash',             '৳' + fmt(s.totalCash));
            setEl('summaryCreditOutstanding','৳' + fmt(s.totalCreditOutstanding));
            setEl('summaryAvailable',        '৳' + fmt(s.totalAvailable));
        } catch (e) { console.error('loadSummary error', e); }
    }

    // ── Accounts list ────────────────────────────────────────────────────────

    async function loadAccounts() {
        try {
            const data = await apiFetch('/api/accounts');
            allAccounts = data || [];
            window.renderTable();
            renderSummaryBreakdown();
        } catch (e) {
            showToast('Failed to load accounts.', 'error');
        }
    }

    function renderSummaryBreakdown() {
        const active = allAccounts.filter(a => a.status === 'ACTIVE');

        function fillBreakdown(elId, accounts, isCredit) {
            const el = document.getElementById(elId);
            if (!el) return;
            if (accounts.length === 0) { el.innerHTML = ''; return; }
            el.innerHTML = accounts.map(a => {
                const name = escHtml(a.accountNickname);
                const bal  = fmt(a.currentBalance);
                const cls  = isCredit ? 'bal-credit' : '';
                return `<div class="acct-breakdown-row">
                    <span class="acct-breakdown-name">${name}</span>
                    <span class="acct-breakdown-bal ${cls}">৳${bal}</span>
                </div>`;
            }).join('');
        }

        fillBreakdown('summaryBankSubs',   active.filter(a => a.accountType === 'BANK' || a.accountType === 'DEBIT_CARD'), false);
        fillBreakdown('summaryMfsSubs',    active.filter(a => a.accountType === 'MFS'), false);
        fillBreakdown('summaryCashSubs',   active.filter(a => a.accountType === 'CASH'), false);
        fillBreakdown('summaryCreditSubs', active.filter(a => a.accountType === 'CREDIT_CARD'), true);
    }

    window.renderTable = function renderTable() {
        const search = (document.getElementById('acctSearch').value || '').toLowerCase();
        const typeF  = document.getElementById('acctTypeFilter').value;
        const statF  = document.getElementById('acctStatusFilter').value;

        const filtered = allAccounts.filter(a => {
            const matchSearch = !search ||
                a.accountNickname.toLowerCase().includes(search) ||
                (a.bankName || '').toLowerCase().includes(search) ||
                (a.provider || '').toLowerCase().includes(search);
            const matchType   = !typeF || a.accountType === typeF;
            const matchStatus = !statF || a.status === statF;
            return matchSearch && matchType && matchStatus;
        });

        const tbody = document.getElementById('accountTableBody');
        if (filtered.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6"><div class="empty-state"><div class="empty-state-icon"><i class="fas fa-wallet"></i></div><p class="text-muted">No accounts found.</p><button class="btn btn-primary btn-sm mt-2" onclick="openAddModal()"><i class="fas fa-plus me-1"></i>Add Account</button></div></td></tr>`;
            return;
        }
        tbody.innerHTML = filtered.map(a => renderAccountRow(a)).join('');
    }

    function renderAccountRow(a) {
        const iconClass = acctIconClass(a.accountType);
        const iconEmoji = acctIconEmoji(a.accountType);
        const typeName  = acctTypeName(a.accountType);
        const provider  = a.bankName || a.provider || '—';
        const isCreditCard = a.accountType === 'CREDIT_CARD';
        const balClass  = isCreditCard ? 'balance-credit' : (a.currentBalance >= 0 ? 'balance-positive' : 'balance-negative');
        const balPrefix = isCreditCard ? 'Outstanding: ' : '';
        const statusBadge = a.status === 'ACTIVE'
            ? '<span class="status-badge-active">Active</span>'
            : '<span class="status-badge-inactive">Inactive</span>';
        const toggleIcon  = a.status === 'ACTIVE' ? 'fa-toggle-off' : 'fa-toggle-on';
        const toggleLabel = a.status === 'ACTIVE' ? 'Deactivate' : 'Activate';
        const ledgerBtn = `<button class="btn btn-xs btn-outline-info" style="font-size:0.75rem;padding:2px 8px;" onclick="openLedgerModal(${a.id},'${escHtml(a.accountNickname)}')" title="View Ledger"><i class="fas fa-list-ul"></i></button>`;
        const utilizationRow = isCreditCard ? renderUtilizationRow(a) : '';
        return `<tr>
            <td>
                <div class="d-flex align-items-center gap-2">
                    <div class="acct-icon ${iconClass}">${iconEmoji}</div>
                    <div>
                        <div style="font-weight:600;font-size:0.88rem;">${escHtml(a.accountNickname)}</div>
                        ${a.accountNumber ? `<div style="font-size:0.75rem;color:var(--text-muted-custom);">${escHtml(a.accountNumber)}</div>` : ''}
                    </div>
                </div>
            </td>
            <td><span style="font-size:0.82rem;">${typeName}</span></td>
            <td style="font-size:0.85rem;">${escHtml(provider)}</td>
            <td><span class="${balClass}">${balPrefix}৳${fmt(a.currentBalance)}</span>${utilizationRow}</td>
            <td>${statusBadge}</td>
            <td>
                <div class="d-flex gap-1 flex-wrap">
                    <button class="btn btn-xs btn-outline-primary" style="font-size:0.75rem;padding:2px 8px;" onclick="openEditModal(${a.id})" title="Edit"><i class="fas fa-edit"></i></button>
                    ${ledgerBtn}
                    <button class="btn btn-xs btn-outline-secondary" style="font-size:0.75rem;padding:2px 8px;" onclick="toggleStatus(${a.id},'${a.status}')" title="${toggleLabel}"><i class="fas ${toggleIcon}"></i></button>
                    <button class="btn btn-xs btn-outline-danger" style="font-size:0.75rem;padding:2px 8px;" onclick="openDeleteModal(${a.id},'${escHtml(a.accountNickname)}')" title="Delete"><i class="fas fa-trash"></i></button>
                </div>
            </td>
        </tr>`;
    }

    function renderUtilizationRow(a) {
        const util = Math.max(0, a.utilizationPercent || 0);
        const barPct = Math.min(100, util);
        const barColor = util >= 90 ? '#dc2626' : (util >= 70 ? '#f59e0b' : '#16a34a');
        const available = a.availableCredit != null ? fmt(a.availableCredit) : '—';
        return `<div class="mt-1" style="min-width:150px;">
            <div class="progress" style="height:5px;">
                <div class="progress-bar" role="progressbar" style="width:${barPct}%;background:${barColor};"></div>
            </div>
            <div style="font-size:0.7rem;color:var(--text-muted-custom);margin-top:2px;">
                ${util.toFixed(1)}% used · Available ৳${available}
            </div>
        </div>`;
    }

    // ── Add / Edit Modal ─────────────────────────────────────────────────────

    window.openAddModal = function () {
        editAccountId = null;
        document.getElementById('accountModalTitle').innerHTML = '<i class="fas fa-plus-circle me-2"></i>Add Account';
        document.getElementById('editAccountId').value = '';
        resetForm();
        const curWrap = document.getElementById('currentBalanceWrap');
        if (curWrap) curWrap.style.display = 'none';
        accountModal.show();
    };

    window.openEditModal = function (id) {
        const a = allAccounts.find(acc => acc.id === id);
        if (!a) return;
        editAccountId = id;
        document.getElementById('accountModalTitle').innerHTML = '<i class="fas fa-edit me-2"></i>Edit Account';
        document.getElementById('editAccountId').value = id;
        document.getElementById('acctType').value           = a.accountType || '';
        document.getElementById('acctNickname').value       = a.accountNickname || '';
        document.getElementById('acctBankName').value       = a.bankName || '';
        document.getElementById('acctNumber').value         = a.accountNumber || '';
        document.getElementById('acctCardType').value       = a.cardType || '';
        document.getElementById('acctProviderBank').value   = a.provider || 'Savings';
        document.getElementById('acctProviderMfs').value    = a.provider || 'bKash';
        document.getElementById('acctMobile').value         = a.mobileNumber || '';
        document.getElementById('acctCreditLimit').value    = a.creditLimit || '';
        document.getElementById('acctStatementDay').value   = a.statementDay || '';
        document.getElementById('acctDueDay').value         = a.dueDay || '';
        document.getElementById('acctCreditLimitBehavior').value = a.creditLimitBehavior || 'WARN';
        document.getElementById('acctOpeningBalance').value = a.openingBalance != null ? a.openingBalance : 0;
        editingOriginalCurrentBalance = a.currentBalance != null ? a.currentBalance : 0;
        document.getElementById('acctCurrentBalance').value = editingOriginalCurrentBalance;
        document.getElementById('acctStatus').value         = a.status || 'ACTIVE';
        const curWrap = document.getElementById('currentBalanceWrap');
        if (curWrap) curWrap.style.display = '';
        onAccountTypeChange();
        if (a.linkedAccountId) {
            loadBankAccountsForSelect('acctLinkedAccount', a.linkedAccountId);
        }
        accountModal.show();
    };

    function resetForm() {
        ['acctType','acctNickname','acctBankName','acctNumber','acctCardType',
         'acctProviderBank','acctProviderMfs','acctMobile','acctCreditLimit',
         'acctStatementDay','acctDueDay'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
        const obEl = document.getElementById('acctOpeningBalance');
        if (obEl) obEl.value = '0';
        const stEl = document.getElementById('acctStatus');
        if (stEl) stEl.value = 'ACTIVE';
        const behaviorEl = document.getElementById('acctCreditLimitBehavior');
        if (behaviorEl) behaviorEl.value = 'WARN';
        hideAllTypeFields();
    }

    window.onAccountTypeChange = function () {
        hideAllTypeFields();
        const type = document.getElementById('acctType').value;
        const typeClasses = {
            'BANK':        ['field-bank'],
            'DEBIT_CARD':  ['field-debit'],
            'CREDIT_CARD': ['field-credit'],
            'MFS':         ['field-mfs'],
            'CASH':        []
        };
        const classes = typeClasses[type] || [];
        classes.forEach(cls => {
            document.querySelectorAll('.' + cls).forEach(el => el.style.display = '');
        });
        // Also show account number for BANK, DEBIT, CREDIT, MFS
        if (['BANK','DEBIT_CARD','CREDIT_CARD','MFS'].includes(type)) {
            document.querySelectorAll('.field-bank.field-debit.field-credit.field-mfs').forEach(el => el.style.display = '');
        }

        const curLabel = document.getElementById('currentBalanceLabel');
        const curHint  = document.getElementById('currentBalanceHint');
        if (type === 'CREDIT_CARD') {
            if (curLabel) curLabel.textContent = 'Current Outstanding Balance';
            if (curHint)  curHint.textContent  = 'Amount you currently owe on this card — edit to correct or reconcile.';
        } else {
            if (curLabel) curLabel.textContent = 'Current Balance';
            if (curHint)  curHint.textContent  = 'The actual balance right now — edit to correct or reconcile.';
        }

        if (type === 'DEBIT_CARD') {
            loadBankAccountsForSelect('acctLinkedAccount', null);
        }
    };

    function hideAllTypeFields() {
        document.querySelectorAll('.field-bank,.field-debit,.field-credit,.field-mfs').forEach(el => {
            el.style.display = 'none';
        });
    }

    async function loadBankAccountsForSelect(selectId, selectedId) {
        const sel = document.getElementById(selectId);
        if (!sel) return;
        try {
            const accounts = await apiFetch('/api/accounts');
            const banks = (accounts || []).filter(a => a.accountType === 'BANK');
            sel.innerHTML = '<option value="">-- None --</option>' + banks.map(b =>
                `<option value="${b.id}"${b.id === selectedId ? ' selected' : ''}>${escHtml(b.accountNickname)}</option>`
            ).join('');
        } catch (e) { sel.innerHTML = '<option value="">Error loading</option>'; }
    }

    window.saveAccount = async function () {
        const type     = document.getElementById('acctType').value;
        const nickname = document.getElementById('acctNickname').value.trim();
        if (!type || !nickname) {
            showToast('Account type and nickname are required.', 'error');
            return;
        }

        let provider = null;
        if (type === 'BANK') {
            provider = document.getElementById('acctProviderBank').value || null;
        } else if (type === 'MFS') {
            provider = document.getElementById('acctProviderMfs').value || null;
        }

        const body = {
            accountType:      type,
            accountNickname:  nickname,
            bankName:         document.getElementById('acctBankName').value || null,
            accountNumber:    document.getElementById('acctNumber').value || null,
            cardType:         document.getElementById('acctCardType').value || null,
            linkedAccountId:  document.getElementById('acctLinkedAccount').value ? parseInt(document.getElementById('acctLinkedAccount').value, 10) : null,
            provider:         provider,
            mobileNumber:     document.getElementById('acctMobile').value || null,
            creditLimit:      document.getElementById('acctCreditLimit').value ? parseFloat(document.getElementById('acctCreditLimit').value) : null,
            statementDay:     document.getElementById('acctStatementDay').value ? parseInt(document.getElementById('acctStatementDay').value, 10) : null,
            dueDay:           document.getElementById('acctDueDay').value ? parseInt(document.getElementById('acctDueDay').value, 10) : null,
            creditLimitBehavior: document.getElementById('acctCreditLimitBehavior').value || 'WARN',
            openingBalance:   parseFloat(document.getElementById('acctOpeningBalance').value) || 0,
            status:           document.getElementById('acctStatus').value || 'ACTIVE'
        };

        // Only sent when the user actually typed a different value — otherwise an untouched,
        // merely-prefilled Current Balance field would override a concurrent Opening Balance edit
        // (see AccountApiController#updateAccount for how an explicit currentBalance takes priority).
        if (editAccountId) {
            const curVal = parseFloat(document.getElementById('acctCurrentBalance').value);
            if (!isNaN(curVal) && curVal !== editingOriginalCurrentBalance) {
                body.currentBalance = curVal;
            }
        }

        const btn = document.getElementById('saveAccountBtn');
        btn.disabled = true;
        try {
            if (editAccountId) {
                await apiFetch(`/api/accounts/${editAccountId}`, {
                    method: 'PUT',
                    body: JSON.stringify(body)
                });
                showToast('Account updated successfully.', 'success');
            } else {
                await apiFetch('/api/accounts', {
                    method: 'POST',
                    body: JSON.stringify(body)
                });
                showToast('Account created successfully.', 'success');
            }
            accountModal.hide();
            loadAll();
        } catch (e) {
            showToast(e.message || 'Failed to save account.', 'error');
        } finally {
            btn.disabled = false;
        }
    };

    // ── Credit Card Ledger ───────────────────────────────────────────────────

    let ledgerAccountId = null;
    let ledgerModal = null;

    window.openLedgerModal = function (id, nickname) {
        ledgerAccountId = id;
        document.getElementById('ledgerModalTitle').textContent = nickname + ' — Ledger';
        document.getElementById('ledgerStartDate').value = '';
        document.getElementById('ledgerEndDate').value = '';
        document.getElementById('ledgerTypeFilter').value = '';
        document.getElementById('ledgerSearch').value = '';
        if (!ledgerModal) ledgerModal = new bootstrap.Modal(document.getElementById('ledgerModal'));
        ledgerModal.show();
        loadLedger();
    };

    window.applyLedgerFilters = function () {
        loadLedger();
    };

    async function loadLedger() {
        const tbody = document.getElementById('ledgerTableBody');
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">Loading…</td></tr>';
        const params = new URLSearchParams();
        const start = document.getElementById('ledgerStartDate').value;
        const end = document.getElementById('ledgerEndDate').value;
        const type = document.getElementById('ledgerTypeFilter').value;
        const search = document.getElementById('ledgerSearch').value.trim();
        if (start) params.set('startDate', start);
        if (end) params.set('endDate', end);
        if (type) params.set('type', type);
        if (search) params.set('merchant', search);
        try {
            const entries = await apiFetch(`/api/accounts/${ledgerAccountId}/ledger?${params.toString()}`);
            if (!entries || !entries.length) {
                tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted">No transactions found.</td></tr>';
                return;
            }
            tbody.innerHTML = entries.map(e => {
                const amtClass = e.amount >= 0 ? 'balance-credit' : 'balance-positive';
                return `<tr>
                    <td style="font-size:0.82rem;">${escHtml(e.date)}</td>
                    <td style="font-size:0.82rem;">${escHtml(e.description || '')}</td>
                    <td style="font-size:0.82rem;">${escHtml(e.category || '—')}</td>
                    <td style="font-size:0.82rem;">${escHtml(e.transactionType)}</td>
                    <td class="text-end ${amtClass}" style="font-size:0.82rem;">${e.amount >= 0 ? '+' : ''}৳${fmt(e.amount)}</td>
                    <td class="text-end" style="font-size:0.82rem;">৳${fmt(e.runningBalance)}</td>
                </tr>`;
            }).join('');
        } catch (e) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger">Failed to load ledger.</td></tr>';
        }
    }

    // ── Toggle Status ────────────────────────────────────────────────────────

    window.toggleStatus = async function (id, currentStatus) {
        try {
            await apiFetch(`/api/accounts/${id}/status`, { method: 'PATCH' });
            const newStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
            showToast(`Account ${newStatus.toLowerCase()}.`, 'success');
            loadAll();
        } catch (e) {
            showToast(e.message || 'Failed to toggle status.', 'error');
        }
    };

    // ── Delete ───────────────────────────────────────────────────────────────

    window.openDeleteModal = function (id, name) {
        deleteAccountId = id;
        document.getElementById('deleteAccountName').textContent = name;
        deleteModal.show();
    };

    window.confirmDelete = async function () {
        if (!deleteAccountId) return;
        const id = deleteAccountId;
        deleteAccountId = null;
        deleteModal.hide();
        try {
            await apiFetch(`/api/accounts/${id}`, { method: 'DELETE' });
            showToast('Account deleted.', 'success');
            loadAll();
        } catch (e) {
            showToast(e.message || 'Failed to delete account.', 'error');
        }
    };

    // ── Helpers ──────────────────────────────────────────────────────────────

    function fmt(v) {
        return Number(v || 0).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }

    function escHtml(s) {
        return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function setEl(id, val) {
        const el = document.getElementById(id);
        if (el) el.textContent = val;
    }

    function acctTypeName(type) {
        const m = { BANK:'Bank', DEBIT_CARD:'Debit Card', CREDIT_CARD:'Credit Card', MFS:'MFS', CASH:'Cash' };
        return m[type] || type;
    }

    function acctIconClass(type) {
        const m = { BANK:'acct-icon-bank', DEBIT_CARD:'acct-icon-card', CREDIT_CARD:'acct-icon-credit', MFS:'acct-icon-mfs', CASH:'acct-icon-cash' };
        return m[type] || 'acct-icon-bank';
    }

    function acctIconEmoji(type) {
        const m = { BANK:'🏦', DEBIT_CARD:'💳', CREDIT_CARD:'💳', MFS:'📱', CASH:'💵' };
        return m[type] || '💰';
    }

    function showToast(message, type) {
        const container = document.getElementById('toastContainer');
        if (!container) { console.log(message); return; }
        const toast = document.createElement('div');
        toast.className = `notification-toast notification-${type || 'success'}`;
        toast.textContent = message;
        container.appendChild(toast);
        requestAnimationFrame(() => toast.classList.add('show'));
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 5000);
    }

})();
