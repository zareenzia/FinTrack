/**
 * accounts.js — Account Configuration module for FinTrack
 */
(function () {
    'use strict';

    let allAccounts = [];
    let editAccountId = null;
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
            <td><span class="${balClass}">${balPrefix}৳${fmt(a.currentBalance)}</span></td>
            <td>${statusBadge}</td>
            <td>
                <div class="d-flex gap-1 flex-wrap">
                    <button class="btn btn-xs btn-outline-primary" style="font-size:0.75rem;padding:2px 8px;" onclick="openEditModal(${a.id})" title="Edit"><i class="fas fa-edit"></i></button>
                    <button class="btn btn-xs btn-outline-secondary" style="font-size:0.75rem;padding:2px 8px;" onclick="toggleStatus(${a.id},'${a.status}')" title="${toggleLabel}"><i class="fas ${toggleIcon}"></i></button>
                    <button class="btn btn-xs btn-outline-danger" style="font-size:0.75rem;padding:2px 8px;" onclick="openDeleteModal(${a.id},'${escHtml(a.accountNickname)}')" title="Delete"><i class="fas fa-trash"></i></button>
                </div>
            </td>
        </tr>`;
    }

    // ── Add / Edit Modal ─────────────────────────────────────────────────────

    window.openAddModal = function () {
        editAccountId = null;
        document.getElementById('accountModalTitle').innerHTML = '<i class="fas fa-plus-circle me-2"></i>Add Account';
        document.getElementById('editAccountId').value = '';
        resetForm();
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
        document.getElementById('acctOpeningBalance').value = a.openingBalance != null ? a.openingBalance : 0;
        document.getElementById('acctStatus').value         = a.status || 'ACTIVE';
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

        const label = document.getElementById('openingBalanceLabel');
        const hint  = document.getElementById('openingBalanceHint');
        if (type === 'CREDIT_CARD') {
            if (label) label.textContent = 'Current Outstanding Balance';
            if (hint)  hint.textContent  = 'Amount you currently owe on this card.';
        } else {
            if (label) label.textContent = 'Opening Balance';
            if (hint)  hint.textContent  = 'Starting balance for this account.';
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
            openingBalance:   parseFloat(document.getElementById('acctOpeningBalance').value) || 0,
            status:           document.getElementById('acctStatus').value || 'ACTIVE'
        };

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
