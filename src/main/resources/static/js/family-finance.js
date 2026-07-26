/**
 * family-finance.js
 * Household creation, invitations, roles, shared expenses (split methods), and settlement
 * tracking — Phase 1 of the Family Finance module.
 */
(function () {
    'use strict';

    // ── Shared helpers (local copies, matching financial-planner.js's established pattern) ──────
    function fmtCurrency(val) {
        if (typeof getCurrencySettings === 'function') {
            var c = getCurrencySettings();
            var num = Number(val || 0).toFixed(c.decimals);
            return c.position === 'before' ? c.symbol + num : num + ' ' + c.symbol;
        }
        return '৳' + Number(val || 0).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }
    function escHtml(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    var TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };
    function showToast(message, type) {
        type = type || 'success';
        var container = document.getElementById('toastContainer');
        if (!container) return;
        var toast = document.createElement('div');
        toast.className = 'notification-toast notification-' + type;
        toast.innerHTML = '<i class="fas ' + (TOAST_ICONS[type] || TOAST_ICONS.success) + ' notification-toast-icon"></i>' +
            '<span class="notification-toast-message"></span>' +
            '<button type="button" class="notification-toast-close" aria-label="Dismiss">&times;</button>';
        toast.querySelector('.notification-toast-message').textContent = message;
        var dismiss = function () { toast.classList.remove('show'); setTimeout(function () { toast.remove(); }, 300); };
        toast.querySelector('.notification-toast-close').addEventListener('click', dismiss);
        container.appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add('show'); });
        setTimeout(dismiss, 5000);
    }

    var BASE = '/api/households';
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

    // ── State ────────────────────────────────────────────────────────────────
    var ffHousehold = null;      // HouseholdResponse
    var ffCurrentUserId = null;  // resolved from ffHousehold members ("You" tag / self checks)
    var ffCategoriesCache = null;
    var ffSavingsCategoriesCache = null;
    var ffAccountsCache = null;
    var ffConfirmActionFn = null;
    var ffEditingBudgetId = null;
    var ffEditingGoalId = null;
    var ffContributeGoalId = null;
    var ffGoalsCache = [];

    function myUserId() {
        return ffCurrentUserId;
    }

    // ── Init / top-level load ────────────────────────────────────────────────
    async function loadHouseholdState() {
        var data = await apiFetch(BASE + '/mine');
        if (!data) return;
        if (!data.hasHousehold) {
            ffHousehold = null;
            document.getElementById('ffNoHouseholdState').classList.remove('d-none');
            document.getElementById('ffHouseholdView').classList.add('d-none');
            await loadPendingInvitations();
            return;
        }
        ffHousehold = data;
        document.getElementById('ffNoHouseholdState').classList.add('d-none');
        document.getElementById('ffHouseholdView').classList.remove('d-none');
        resolveMyUserId();
        renderHouseholdView();
        loadDashboardSummary();
        loadSettlementSummary();
        loadSharedExpenses();
        loadHouseholdBudgets();
        loadHouseholdGoals();
    }

    // ── Internal tabs (Overview / Budgets & Goals) ───────────────────────────
    window.ffSwitchTab = function (tab) {
        document.querySelectorAll('.ff-tab-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        document.querySelectorAll('.ff-tab-pane').forEach(function (pane) {
            pane.classList.toggle('active', pane.id === 'ff-tab-' + tab);
        });
    };

    function resolveMyUserId() {
        // The API doesn't return "my" userId directly, so read the app's existing logged-in-user
        // cache (same lookup sidebar.js's getUser()/getUserStorageKey() use).
        try {
            var cached = localStorage.getItem('finzin_user') || localStorage.getItem('user');
            if (cached) {
                var parsed = JSON.parse(cached);
                if (parsed && parsed.id) { ffCurrentUserId = parsed.id; return; }
            }
        } catch (e) { /* ignore */ }
        ffCurrentUserId = null;
    }

    // ── No-household state ───────────────────────────────────────────────────
    async function loadPendingInvitations() {
        var invites = await apiFetch(BASE + '/invitations/mine');
        var panel = document.getElementById('ffPendingInvitesPanel');
        var list = document.getElementById('ffPendingInvitesList');
        if (!invites || !invites.length) {
            panel.style.display = 'none';
            return;
        }
        panel.style.display = '';
        list.innerHTML = invites.map(function (inv) {
            return '<div class="ff-member-row">' +
                '<div><div class="ff-row-name">' + escHtml(inv.householdName) + '</div>' +
                '<div class="ff-row-meta">Invited by ' + escHtml(inv.invitedByName || 'a member') +
                (inv.relationshipLabel ? ' · as ' + escHtml(inv.relationshipLabel) : '') + '</div></div>' +
                '<div class="ff-row-actions">' +
                '<button class="btn btn-sm btn-success" onclick="acceptInvite(' + inv.id + ')"><i class="fas fa-check me-1"></i>Accept</button>' +
                '<button class="btn btn-sm btn-outline-danger" onclick="rejectInvite(' + inv.id + ')"><i class="fas fa-xmark me-1"></i>Decline</button>' +
                '</div></div>';
        }).join('');
    }

    window.acceptInvite = async function (id) {
        var result = await apiFetch(BASE + '/invitations/' + id + '/accept', { method: 'POST' });
        if (result && !result.error) {
            showToast('Welcome to the household!');
            loadHouseholdState();
        } else if (result) {
            showToast(result.error || 'Failed to accept invitation.', 'error');
        }
    };

    window.rejectInvite = async function (id) {
        var result = await apiFetch(BASE + '/invitations/' + id + '/reject', { method: 'POST' });
        if (result !== null) {
            showToast('Invitation declined.');
            loadPendingInvitations();
        }
    };

    window.createHousehold = async function () {
        var name = document.getElementById('ffNewHouseholdName').value.trim();
        if (!name) { showToast('Household name is required.', 'error'); return; }
        var result = await apiFetch(BASE, { method: 'POST', body: JSON.stringify({ name: name }) });
        if (result && !result.error) {
            showToast('Household created!');
            loadHouseholdState();
        } else if (result) {
            showToast(result.error || 'Failed to create household.', 'error');
        }
    };

    // ── Household view ───────────────────────────────────────────────────────
    function isAdmin() {
        return ffHousehold && ffHousehold.myRole === 'ADMIN';
    }

    function renderHouseholdView() {
        document.getElementById('ffHouseholdName').textContent = ffHousehold.name;
        document.getElementById('ffMemberCount').textContent = ffHousehold.members.length;
        document.getElementById('ffStatMembers').textContent = ffHousehold.members.length;

        var adminOnly = ['ffRenameBtn', 'ffTransferBtn', 'ffInviteBtn', 'ffDeleteHouseholdBtn', 'ffAddBudgetBtn'];
        adminOnly.forEach(function (id) {
            document.getElementById(id).classList.toggle('d-none', !isAdmin());
        });

        renderMembersList();
    }

    function renderMembersList() {
        var container = document.getElementById('ffMembersList');
        container.innerHTML = ffHousehold.members.map(function (m) {
            var isSelf = myUserId() != null && m.userId === myUserId();
            var isOwner = ffHousehold.ownerId === m.userId;
            var canRemove = isAdmin() && !isSelf && !isOwner;
            return '<div class="ff-member-row">' +
                '<div><div class="ff-row-name">' + escHtml(m.fullName || m.username || ('User ' + m.userId)) +
                (isSelf ? ' <span class="ff-badge ff-badge-you">You</span>' : '') + '</div>' +
                '<div class="ff-row-meta">' + (m.relationshipLabel ? escHtml(m.relationshipLabel) + ' · ' : '') +
                escHtml(m.email || '') + '</div></div>' +
                '<div class="ff-row-actions">' +
                '<span class="ff-badge ff-badge-' + m.role + '">' + (isOwner ? 'Owner' : m.role) + '</span>' +
                (canRemove ? '<button class="ff-action-btn danger" title="Remove" onclick="confirmRemoveMember(' + m.userId + ',\'' + escHtml(m.fullName || m.username || 'this member').replace(/'/g, "") + '\')"><i class="fas fa-user-minus"></i></button>' : '') +
                '</div></div>';
        }).join('');
    }

    // ── Rename ───────────────────────────────────────────────────────────────
    window.openRenameHouseholdModal = function () {
        document.getElementById('ffRenameInput').value = ffHousehold.name;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffRenameModal')).show();
    };
    window.saveRename = async function () {
        var name = document.getElementById('ffRenameInput').value.trim();
        if (!name) { showToast('Household name is required.', 'error'); return; }
        var result = await apiFetch(BASE + '/' + ffHousehold.id, { method: 'PUT', body: JSON.stringify({ name: name }) });
        if (result && !result.error) {
            showToast('Household renamed.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffRenameModal')).hide();
            loadHouseholdState();
        } else if (result) {
            showToast(result.error || 'Failed to rename.', 'error');
        }
    };

    // ── Invite ───────────────────────────────────────────────────────────────
    window.openInviteModal = function () {
        document.getElementById('ffInviteQuery').value = '';
        document.getElementById('ffInviteRelationship').value = '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffInviteModal')).show();
    };
    window.sendInvitation = async function () {
        var query = document.getElementById('ffInviteQuery').value.trim();
        var relationship = document.getElementById('ffInviteRelationship').value;
        if (!query) { showToast('Email or username is required.', 'error'); return; }
        var result = await apiFetch(BASE + '/' + ffHousehold.id + '/invitations', {
            method: 'POST', body: JSON.stringify({ emailOrUsername: query, relationshipLabel: relationship || null })
        });
        if (result && !result.error) {
            showToast('Invitation sent.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffInviteModal')).hide();
        } else if (result) {
            showToast(result.error || 'Failed to send invitation.', 'error');
        }
    };

    // ── Transfer ownership ───────────────────────────────────────────────────
    window.openTransferOwnershipModal = function () {
        var select = document.getElementById('ffTransferToUser');
        var others = ffHousehold.members.filter(function (m) { return m.userId !== ffHousehold.ownerId; });
        if (!others.length) { showToast('There are no other members to transfer ownership to.', 'error'); return; }
        select.innerHTML = others.map(function (m) {
            return '<option value="' + m.userId + '">' + escHtml(m.fullName || m.username || ('User ' + m.userId)) + '</option>';
        }).join('');
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffTransferModal')).show();
    };
    window.saveTransferOwnership = async function () {
        var newOwnerUserId = parseInt(document.getElementById('ffTransferToUser').value, 10);
        var result = await apiFetch(BASE + '/' + ffHousehold.id + '/transfer-ownership', {
            method: 'POST', body: JSON.stringify({ newOwnerUserId: newOwnerUserId })
        });
        if (result && !result.error) {
            showToast('Ownership transferred.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffTransferModal')).hide();
            loadHouseholdState();
        } else if (result) {
            showToast(result.error || 'Failed to transfer ownership.', 'error');
        }
    };

    // ── Generic confirm modal ────────────────────────────────────────────────
    function openConfirm(message, actionFn) {
        document.getElementById('ffConfirmMsg').textContent = message;
        ffConfirmActionFn = actionFn;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffConfirmModal')).show();
    }
    document.addEventListener('DOMContentLoaded', function () {
        var btn = document.getElementById('ffConfirmBtn');
        if (btn) {
            btn.addEventListener('click', function () {
                var fn = ffConfirmActionFn;
                bootstrap.Modal.getOrCreateInstance(document.getElementById('ffConfirmModal')).hide();
                if (typeof fn === 'function') fn();
            });
        }
    });

    window.confirmLeaveHousehold = function () {
        openConfirm('Leave "' + ffHousehold.name + '"? Your past shared expenses stay visible to the household for accountability.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/leave', { method: 'POST' });
            if (result !== null) { showToast('You left the household.'); loadHouseholdState(); }
        });
    };

    window.confirmDeleteHousehold = function () {
        openConfirm('Permanently delete "' + ffHousehold.name + '"? This removes it for every member. Nobody\'s personal transactions are affected.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id, { method: 'DELETE' });
            if (result !== null) { showToast('Household deleted.'); loadHouseholdState(); }
        });
    };

    window.confirmRemoveMember = function (userId, name) {
        openConfirm('Remove ' + name + ' from the household?', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/members/' + userId, { method: 'DELETE' });
            if (result !== null) { showToast('Member removed.'); loadHouseholdState(); }
        });
    };

    // ── Dashboard summary ────────────────────────────────────────────────────
    async function loadDashboardSummary() {
        var data = await apiFetch(BASE + '/dashboard-summary');
        if (!data || !data.hasHousehold) return;
        document.getElementById('ffStatThisMonth').textContent = fmtCurrency(data.thisMonthSharedTotal);
        document.getElementById('ffStatExpenseCount').textContent = data.sharedExpenseCountThisMonth;
        var balanceEl = document.getElementById('ffStatBalance');
        var balance = data.yourNetBalance || 0;
        balanceEl.textContent = (balance >= 0 ? '+' : '') + fmtCurrency(balance);
        balanceEl.className = 'ff-stat-value ' + (balance >= 0 ? 'ff-positive' : 'ff-negative');
    }

    // ── Settlement ───────────────────────────────────────────────────────────
    async function loadSettlementSummary() {
        var summary = await apiFetch(BASE + '/' + ffHousehold.id + '/settlements/summary');
        if (summary && !summary.error) {
            document.getElementById('ffBalancesList').innerHTML = summary.balances.map(function (b) {
                var cls = b.netBalance >= 0 ? 'ff-positive' : 'ff-negative';
                return '<div class="ff-settlement-row">' +
                    '<div class="ff-row-name">' + escHtml(b.userName || 'User') + '</div>' +
                    '<div class="ff-row-meta">Paid ' + fmtCurrency(b.totalPaid) + ' · Fair share ' + fmtCurrency(b.fairShare) + '</div>' +
                    '<div class="ff-row-actions ' + cls + '" style="font-weight:700;">' + (b.netBalance >= 0 ? '+' : '') + fmtCurrency(b.netBalance) + '</div>' +
                    '</div>';
            }).join('') || '<div class="ff-row-meta">No shared expenses yet.</div>';

            document.getElementById('ffSuggestedTransfers').innerHTML = summary.suggestedTransfers.length
                ? summary.suggestedTransfers.map(function (t) {
                    return '<div class="ff-settlement-row"><div>' + escHtml(t.fromUserName) + ' owes ' + escHtml(t.toUserName) +
                        ' <strong>' + fmtCurrency(t.amount) + '</strong></div>' +
                        (myUserId() === t.fromUserId ? '<button class="btn btn-sm btn-outline-primary ff-row-actions" onclick="openSettlementModal(' + t.toUserId + ',' + t.amount + ')">Record</button>' : '') +
                        '</div>';
                }).join('')
                : '<div class="ff-row-meta">Everyone is settled up.</div>';
        }

        var history = await apiFetch(BASE + '/' + ffHousehold.id + '/settlements');
        if (history && !history.error) {
            document.getElementById('ffSettlementHistory').innerHTML = history.length
                ? history.slice(0, 10).map(function (s) {
                    return '<div class="ff-settlement-row"><div class="ff-row-meta">' + escHtml(s.fromUserName) + ' → ' + escHtml(s.toUserName) +
                        ': ' + fmtCurrency(s.amount) + (s.note ? ' (' + escHtml(s.note) + ')' : '') + '</div></div>';
                }).join('')
                : '<div class="ff-row-meta">No settlements recorded yet.</div>';
        }
    }

    window.openSettlementModal = function (toUserId, amount) {
        var select = document.getElementById('ffSettlementToUser');
        var others = ffHousehold.members.filter(function (m) { return m.userId !== myUserId(); });
        select.innerHTML = others.map(function (m) {
            return '<option value="' + m.userId + '">' + escHtml(m.fullName || m.username || ('User ' + m.userId)) + '</option>';
        }).join('');
        if (toUserId) select.value = toUserId;
        document.getElementById('ffSettlementAmount').value = amount || '';
        document.getElementById('ffSettlementNote').value = '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffSettlementModal')).show();
    };

    window.saveSettlement = async function () {
        var toUserId = parseInt(document.getElementById('ffSettlementToUser').value, 10);
        var amount = parseFloat(document.getElementById('ffSettlementAmount').value);
        var note = document.getElementById('ffSettlementNote').value.trim();
        if (!amount || amount <= 0) { showToast('Enter a valid amount.', 'error'); return; }
        var result = await apiFetch(BASE + '/' + ffHousehold.id + '/settlements', {
            method: 'POST', body: JSON.stringify({ toUserId: toUserId, amount: amount, note: note || null })
        });
        if (result && !result.error) {
            showToast('Settlement recorded.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffSettlementModal')).hide();
            loadSettlementSummary();
            loadDashboardSummary();
        } else if (result) {
            showToast(result.error || 'Failed to record settlement.', 'error');
        }
    };

    // ── Shared expenses ──────────────────────────────────────────────────────
    window.loadSharedExpenses = async function () {
        var month = document.getElementById('ffExpenseMonthFilter').value;
        var url = BASE + '/' + ffHousehold.id + '/expenses' + (month ? '?month=' + month : '');
        var expenses = await apiFetch(url);
        var container = document.getElementById('ffExpensesList');
        if (!expenses || !expenses.length) {
            container.innerHTML = '<div class="ff-empty"><i class="fas fa-cart-shopping"></i><p>No shared expenses yet. Click <strong>Log Shared Expense</strong> to add one.</p></div>';
            return;
        }
        container.innerHTML = expenses.map(function (e) {
            var myShare = e.shares.find(function (s) { return s.userId === myUserId(); });
            var canUnshare = isAdmin() || e.payerUserId === myUserId();
            return '<div class="ff-expense-row">' +
                '<div><div class="ff-row-name">' + escHtml(e.description) + '</div>' +
                '<div class="ff-row-meta">' + escHtml(e.expenseDate) + (e.category ? ' · ' + escHtml(e.category) : '') +
                ' · Paid by ' + escHtml(e.payerName || 'someone') + '</div></div>' +
                '<span class="ff-badge ff-badge-' + e.splitMethod + '">' + e.splitMethod.replace('_', ' ') + '</span>' +
                '<div class="ff-row-name">' + fmtCurrency(e.totalAmount) + '</div>' +
                (myShare ? '<div class="ff-row-meta">Your share: ' + fmtCurrency(myShare.shareAmount) + '</div>' : '') +
                (canUnshare ? '<div class="ff-row-actions"><button class="ff-action-btn danger" title="Unshare" onclick="confirmUnshareExpense(' + e.id + ')"><i class="fas fa-link-slash"></i></button></div>' : '') +
                '</div>';
        }).join('');
    };

    window.confirmUnshareExpense = function (id) {
        openConfirm('Remove this expense from the household ledger? Your personal transaction record is not affected.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/expenses/' + id, { method: 'DELETE' });
            if (result !== null) { showToast('Removed from shared ledger.'); loadSharedExpenses(); loadSettlementSummary(); loadDashboardSummary(); }
        });
    };

    // ── Log Shared Expense modal ─────────────────────────────────────────────
    async function ensureExpenseFormOptionsLoaded() {
        var catSel = document.getElementById('ffExpenseCategory');
        var acctSel = document.getElementById('ffExpenseAccount');
        if (ffCategoriesCache === null) {
            try { ffCategoriesCache = await fetch('/api/categories?type=expense').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { ffCategoriesCache = []; }
        }
        if (ffAccountsCache === null) {
            try { ffAccountsCache = await fetch('/api/accounts').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { ffAccountsCache = []; }
        }
        catSel.innerHTML = '<option value="">Select category</option>' + ffCategoriesCache.map(function (c) {
            return '<option value="' + c.id + '">' + escHtml(c.name) + '</option>';
        }).join('');
        var acctLabel = function (a) { return a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : ''); };
        acctSel.innerHTML = '<option value="">-- No account --</option>' +
            '<option value="SAVINGS">💰 Savings (spend from savings)</option>' +
            ffAccountsCache.map(function (a) { return '<option value="' + a.id + '">' + escHtml(acctLabel(a)) + '</option>'; }).join('');
    }

    window.openLogExpenseModal = async function () {
        await ensureExpenseFormOptionsLoaded();
        document.getElementById('ffExpenseDescription').value = '';
        document.getElementById('ffExpenseAmount').value = '';
        document.getElementById('ffExpenseCategory').value = '';
        document.getElementById('ffExpenseAccount').value = '';
        document.getElementById('ffExpenseDate').value = new Date().toISOString().slice(0, 10);
        document.getElementById('ffExpenseSplitMethod').value = 'EQUAL';
        renderContributorRows();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffExpenseModal')).show();
    };

    window.renderContributorRows = function () {
        var method = document.getElementById('ffExpenseSplitMethod').value;
        var container = document.getElementById('ffExpenseContributors');
        container.innerHTML = ffHousehold.members.map(function (m) {
            var extra = '';
            if (method === 'PERCENTAGE') extra = '<input type="number" class="form-control form-control-sm ff-contrib-value" data-user="' + m.userId + '" placeholder="%" step="0.1" min="0" max="100">';
            else if (method === 'FIXED_AMOUNT') extra = '<input type="number" class="form-control form-control-sm ff-contrib-value" data-user="' + m.userId + '" placeholder="৳" step="0.01" min="0">';
            return '<div class="ff-contrib-row">' +
                '<div class="form-check flex-grow-1">' +
                '<input class="form-check-input ff-contrib-check" type="checkbox" data-user="' + m.userId + '" id="ffContrib' + m.userId + '" checked>' +
                '<label class="form-check-label" for="ffContrib' + m.userId + '">' + escHtml(m.fullName || m.username || ('User ' + m.userId)) + '</label>' +
                '</div>' + extra + '</div>';
        }).join('');
        document.getElementById('ffContribTotalCheck').textContent = method === 'EQUAL' ? 'split evenly' : (method === 'PERCENTAGE' ? 'must total 100%' : 'must total the expense amount');
    };

    window.saveSharedExpense = async function () {
        var description = document.getElementById('ffExpenseDescription').value.trim();
        var amount = parseFloat(document.getElementById('ffExpenseAmount').value);
        var categoryId = document.getElementById('ffExpenseCategory').value;
        var accountVal = document.getElementById('ffExpenseAccount').value;
        var date = document.getElementById('ffExpenseDate').value;
        var splitMethod = document.getElementById('ffExpenseSplitMethod').value;

        if (!description || !amount || amount <= 0 || !date) {
            showToast('Description, a valid amount, and a date are required.', 'error'); return;
        }

        var checks = document.querySelectorAll('.ff-contrib-check');
        var shares = [];
        checks.forEach(function (c) {
            if (c.checked) {
                var userId = parseInt(c.dataset.user, 10);
                var valueInput = document.querySelector('.ff-contrib-value[data-user="' + userId + '"]');
                var value = valueInput ? parseFloat(valueInput.value) || 0 : null;
                if (splitMethod === 'PERCENTAGE') shares.push({ userId: userId, amount: null, percent: value });
                else if (splitMethod === 'FIXED_AMOUNT') shares.push({ userId: userId, amount: value, percent: null });
                else shares.push({ userId: userId, amount: null, percent: null });
            }
        });
        if (!shares.length) { showToast('Select at least one contributor.', 'error'); return; }

        // Step 1: create the real personal transaction (reuses the exact existing transaction system).
        var txPayload = {
            description: description,
            amount: amount,
            category_id: categoryId ? parseInt(categoryId, 10) : null,
            transaction_type: 'expense',
            date: date,
            sourceAccountId: accountVal && accountVal !== 'SAVINGS' ? parseInt(accountVal, 10) : null,
            destinationAccountId: null,
            fromSavings: accountVal === 'SAVINGS'
        };
        try {
            var txRes = await fetch('/api/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(txPayload) });
            var txData = await txRes.json().catch(function () { return null; });
            if (!txRes.ok) { showToast((txData && txData.error) || 'Failed to save the expense.', 'error'); return; }

            // Step 2: link it into the household ledger.
            var linkResult = await apiFetch(BASE + '/' + ffHousehold.id + '/expenses', {
                method: 'POST', body: JSON.stringify({ transactionId: txData.id, splitMethod: splitMethod, shares: shares })
            });
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffExpenseModal')).hide();
            if (linkResult && !linkResult.error) {
                showToast('Shared expense logged.');
            } else {
                showToast('Expense saved, but sharing it failed: ' + ((linkResult && linkResult.error) || 'unknown error'), 'warning');
            }
            loadSharedExpenses();
            loadSettlementSummary();
            loadDashboardSummary();
        } catch (e) {
            showToast('Network error while saving the expense.', 'error');
        }
    };

    // ── Household Budgets ────────────────────────────────────────────────────
    window.loadHouseholdBudgets = async function () {
        var budgets = await apiFetch(BASE + '/' + ffHousehold.id + '/budgets');
        var container = document.getElementById('ffBudgetsList');
        if (!budgets || !budgets.length) {
            container.innerHTML = '<div class="ff-empty"><i class="fas fa-wallet"></i><p>No household budgets yet.' +
                (isAdmin() ? ' Click <strong>Add Budget</strong> to set a category spending cap.' : '') + '</p></div>';
            return;
        }
        container.innerHTML = budgets.map(function (b) {
            var pct = Math.min(b.percentUsed || 0, 100);
            var barClass = b.status === 'EXCEEDED' ? 'over' : (b.status === 'NEAR_LIMIT' ? 'warn' : '');
            return '<div class="ff-budget-card">' +
                '<div class="d-flex justify-content-between align-items-start mb-2">' +
                '<div><div class="ff-row-name">' + escHtml(b.categoryName) + '</div>' +
                '<div class="ff-row-meta">' + fmtCurrency(b.spentThisMonth) + ' of ' + fmtCurrency(b.monthlyLimit) + ' this month</div></div>' +
                (isAdmin() ? '<div class="ff-row-actions"><button class="ff-action-btn" title="Edit" onclick="openBudgetModal(' + b.id + ')"><i class="fas fa-pen"></i></button>' +
                    '<button class="ff-action-btn danger" title="Delete" onclick="confirmDeleteBudget(' + b.id + ')"><i class="fas fa-trash-alt"></i></button></div>' : '') +
                '</div>' +
                '<div class="ff-progress"><div class="ff-progress-bar ' + barClass + '" style="width:' + pct + '%"></div></div>' +
                '</div>';
        }).join('');
    };

    window.openBudgetModal = async function (budgetId) {
        ffEditingBudgetId = budgetId || null;
        document.getElementById('ffBudgetModalTitle').textContent = budgetId ? 'Edit Household Budget' : 'Add Household Budget';
        var catInput = document.getElementById('ffBudgetCategory');
        catInput.value = '';
        catInput.disabled = false;
        document.getElementById('ffBudgetLimit').value = '';
        if (budgetId) {
            var budgets = await apiFetch(BASE + '/' + ffHousehold.id + '/budgets');
            var b = budgets && budgets.find(function (x) { return x.id === budgetId; });
            if (b) {
                catInput.value = b.categoryName;
                catInput.disabled = true; // category can't be changed once set — delete and recreate instead
                document.getElementById('ffBudgetLimit').value = b.monthlyLimit;
            }
        }
        var suggestions = await apiFetch(BASE + '/' + ffHousehold.id + '/budgets/category-suggestions');
        document.getElementById('ffBudgetCategorySuggestions').innerHTML = (suggestions || []).map(function (c) {
            return '<option value="' + escHtml(c) + '"></option>';
        }).join('');
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffBudgetModal')).show();
    };

    window.saveBudget = async function () {
        var categoryName = document.getElementById('ffBudgetCategory').value.trim();
        var monthlyLimit = parseFloat(document.getElementById('ffBudgetLimit').value);
        if (!categoryName || !monthlyLimit || monthlyLimit <= 0) { showToast('A category and a positive monthly limit are required.', 'error'); return; }
        var url = BASE + '/' + ffHousehold.id + '/budgets' + (ffEditingBudgetId ? '/' + ffEditingBudgetId : '');
        var result = await apiFetch(url, {
            method: ffEditingBudgetId ? 'PUT' : 'POST',
            body: JSON.stringify({ categoryName: categoryName, monthlyLimit: monthlyLimit })
        });
        if (result && !result.error) {
            showToast(ffEditingBudgetId ? 'Budget updated.' : 'Budget added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffBudgetModal')).hide();
            loadHouseholdBudgets();
        } else if (result) {
            showToast(result.error || 'Failed to save budget.', 'error');
        }
    };

    window.confirmDeleteBudget = function (id) {
        openConfirm('Delete this household budget? This does not affect anyone\'s shared expenses.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/budgets/' + id, { method: 'DELETE' });
            if (result && !result.error) { showToast('Budget deleted.'); loadHouseholdBudgets(); }
            else if (result) { showToast(result.error || 'Failed to delete budget.', 'error'); }
        });
    };

    // ── Household Goals ──────────────────────────────────────────────────────
    window.loadHouseholdGoals = async function () {
        var goals = await apiFetch(BASE + '/' + ffHousehold.id + '/goals');
        ffGoalsCache = goals || [];
        var container = document.getElementById('ffGoalsList');
        if (!ffGoalsCache.length) {
            container.innerHTML = '<div class="ff-empty"><i class="fas fa-piggy-bank"></i><p>No household goals yet. Click <strong>New Goal</strong> to start one together.</p></div>';
            return;
        }
        container.innerHTML = ffGoalsCache.map(function (g) {
            var pct = Math.min(g.percentComplete || 0, 100);
            var canManage = isAdmin() || g.createdByUserId === myUserId();
            var statusBadge = g.status === 'ACHIEVED' ? '<span class="ff-badge" style="background:rgba(34,197,94,0.15);color:#22c55e;">Achieved</span>' :
                g.status === 'ARCHIVED' ? '<span class="ff-badge ff-badge-you">Archived</span>' : '';
            var contributorChips = (g.contributions || []).map(function (c) {
                var initial = (c.userName || 'U').trim().charAt(0).toUpperCase();
                var canRemove = isAdmin() || c.userId === myUserId();
                return '<span class="ff-contributor-chip"><span class="ff-chip-avatar">' + initial + '</span>&nbsp;' + escHtml(c.userName || 'User') + ': ' + fmtCurrency(c.amount) +
                    (canRemove ? ' <a href="javascript:void(0)" onclick="confirmRemoveContribution(' + g.id + ',' + c.id + ')" title="Remove" style="color:inherit;opacity:0.6;"><i class="fas fa-xmark"></i></a>' : '') +
                    '</span>';
            }).join('');
            return '<div class="ff-goal-card">' +
                '<div class="d-flex justify-content-between align-items-start mb-2">' +
                '<div><div class="ff-row-name">' + escHtml(g.name) + ' ' + statusBadge + '</div>' +
                '<div class="ff-row-meta">' + fmtCurrency(g.totalContributed) + ' of ' + fmtCurrency(g.targetAmount) +
                (g.targetDate ? ' · by ' + escHtml(g.targetDate) : '') + '</div></div>' +
                '<div class="ff-row-actions">' +
                (g.status !== 'ARCHIVED' ? '<button class="btn btn-sm btn-outline-primary" onclick="openContributeModal(' + g.id + ',\'' + escHtml(g.name).replace(/'/g, '') + '\')"><i class="fas fa-plus me-1"></i>Contribute</button>' : '') +
                (canManage ? '<button class="ff-action-btn" title="Edit" onclick="openGoalModal(' + g.id + ')"><i class="fas fa-pen"></i></button>' : '') +
                (canManage && g.status !== 'ARCHIVED' ? '<button class="ff-action-btn" title="Archive" onclick="confirmArchiveGoal(' + g.id + ')"><i class="fas fa-box-archive"></i></button>' : '') +
                (canManage ? '<button class="ff-action-btn danger" title="Delete" onclick="confirmDeleteGoal(' + g.id + ')"><i class="fas fa-trash-alt"></i></button>' : '') +
                '</div></div>' +
                '<div class="ff-progress mb-2"><div class="ff-progress-bar" style="width:' + pct + '%"></div></div>' +
                (contributorChips ? '<div>' + contributorChips + '</div>' : '<div class="ff-row-meta">No contributions yet.</div>') +
                '</div>';
        }).join('');
    };

    window.openGoalModal = function (goalId) {
        ffEditingGoalId = goalId || null;
        document.getElementById('ffGoalModalTitle').textContent = goalId ? 'Edit Household Goal' : 'New Household Goal';
        var existing = goalId ? ffGoalsCache.find(function (g) { return g.id === goalId; }) : null;
        document.getElementById('ffGoalName').value = existing ? existing.name : '';
        document.getElementById('ffGoalTarget').value = existing ? existing.targetAmount : '';
        document.getElementById('ffGoalDate').value = existing && existing.targetDate ? existing.targetDate : '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffGoalModal')).show();
    };

    window.saveGoal = async function () {
        var name = document.getElementById('ffGoalName').value.trim();
        var targetAmount = parseFloat(document.getElementById('ffGoalTarget').value);
        var targetDate = document.getElementById('ffGoalDate').value || null;
        if (!name || !targetAmount || targetAmount <= 0) { showToast('A name and a positive target amount are required.', 'error'); return; }
        var url = BASE + '/' + ffHousehold.id + '/goals' + (ffEditingGoalId ? '/' + ffEditingGoalId : '');
        var result = await apiFetch(url, {
            method: ffEditingGoalId ? 'PUT' : 'POST',
            body: JSON.stringify({ name: name, targetAmount: targetAmount, targetDate: targetDate })
        });
        if (result && !result.error) {
            showToast(ffEditingGoalId ? 'Goal updated.' : 'Goal created.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('ffGoalModal')).hide();
            loadHouseholdGoals();
        } else if (result) {
            showToast(result.error || 'Failed to save goal.', 'error');
        }
    };

    window.confirmArchiveGoal = function (id) {
        openConfirm('Archive this goal? It stays visible but stops accepting new contributions.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/goals/' + id + '/archive', { method: 'PATCH' });
            if (result && !result.error) { showToast('Goal archived.'); loadHouseholdGoals(); }
            else if (result) { showToast(result.error || 'Failed to archive goal.', 'error'); }
        });
    };

    window.confirmDeleteGoal = function (id) {
        openConfirm('Delete this goal? This only works if it has no contributions yet — otherwise archive it instead.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/goals/' + id, { method: 'DELETE' });
            if (result && !result.error) { showToast('Goal deleted.'); loadHouseholdGoals(); }
            else if (result) { showToast(result.error || 'Failed to delete goal.', 'error'); }
        });
    };

    window.confirmRemoveContribution = function (goalId, contributionId) {
        openConfirm('Remove this contribution from the goal? Your personal transaction record is not affected.', async function () {
            var result = await apiFetch(BASE + '/' + ffHousehold.id + '/goals/' + goalId + '/contributions/' + contributionId, { method: 'DELETE' });
            if (result && !result.error) { showToast('Contribution removed.'); loadHouseholdGoals(); }
            else if (result) { showToast(result.error || 'Failed to remove contribution.', 'error'); }
        });
    };

    // ── Contribute to Goal modal ─────────────────────────────────────────────
    async function ensureContributeFormOptionsLoaded() {
        var catSel = document.getElementById('ffContributeCategory');
        var acctSel = document.getElementById('ffContributeAccount');
        if (ffSavingsCategoriesCache === null) {
            try { ffSavingsCategoriesCache = await fetch('/api/categories?type=savings').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { ffSavingsCategoriesCache = []; }
        }
        if (ffAccountsCache === null) {
            try { ffAccountsCache = await fetch('/api/accounts').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { ffAccountsCache = []; }
        }
        catSel.innerHTML = '<option value="">Select category</option>' + ffSavingsCategoriesCache.map(function (c) {
            return '<option value="' + c.id + '">' + escHtml(c.name) + '</option>';
        }).join('');
        var acctLabel = function (a) { return a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : ''); };
        acctSel.innerHTML = '<option value="">-- No account --</option>' +
            ffAccountsCache.map(function (a) { return '<option value="' + a.id + '">' + escHtml(acctLabel(a)) + '</option>'; }).join('');
    }

    window.openContributeModal = async function (goalId, goalName) {
        ffContributeGoalId = goalId;
        document.getElementById('ffContributeGoalName').textContent = goalName || 'Goal';
        await ensureContributeFormOptionsLoaded();
        document.getElementById('ffContributeDescription').value = '';
        document.getElementById('ffContributeAmount').value = '';
        document.getElementById('ffContributeCategory').value = '';
        document.getElementById('ffContributeAccount').value = '';
        document.getElementById('ffContributeDate').value = new Date().toISOString().slice(0, 10);
        document.getElementById('ffContributeNote').value = '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('ffContributeModal')).show();
    };

    window.saveContribution = async function () {
        var description = document.getElementById('ffContributeDescription').value.trim();
        var amount = parseFloat(document.getElementById('ffContributeAmount').value);
        var categoryId = document.getElementById('ffContributeCategory').value;
        var accountVal = document.getElementById('ffContributeAccount').value;
        var date = document.getElementById('ffContributeDate').value;
        var note = document.getElementById('ffContributeNote').value.trim();

        if (!description || !amount || amount <= 0 || !date) {
            showToast('Description, a valid amount, and a date are required.', 'error'); return;
        }

        // Step 1: create the real personal transaction (type=savings — reuses the exact existing
        // transaction system, same as a shared expense reuses it for type=expense).
        var txPayload = {
            description: description,
            amount: amount,
            category_id: categoryId ? parseInt(categoryId, 10) : null,
            transaction_type: 'savings',
            date: date,
            sourceAccountId: accountVal ? parseInt(accountVal, 10) : null,
            destinationAccountId: null,
            fromSavings: false
        };
        try {
            var txRes = await fetch('/api/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(txPayload) });
            var txData = await txRes.json().catch(function () { return null; });
            if (!txRes.ok) { showToast((txData && txData.error) || 'Failed to save the contribution.', 'error'); return; }

            // Step 2: link it into the goal's contribution ledger.
            var linkResult = await apiFetch(BASE + '/' + ffHousehold.id + '/goals/' + ffContributeGoalId + '/contributions', {
                method: 'POST', body: JSON.stringify({ transactionId: txData.id, note: note || null })
            });
            if (linkResult && !linkResult.error) {
                showToast('Contribution added.');
                bootstrap.Modal.getOrCreateInstance(document.getElementById('ffContributeModal')).hide();
            } else {
                showToast('Saved, but linking it to the goal failed: ' + ((linkResult && linkResult.error) || 'unknown error'), 'warning');
            }
            loadHouseholdGoals();
        } catch (e) {
            showToast('Network error while saving the contribution.', 'error');
        }
    };

    // ── Init ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        loadHouseholdState();
    });

})();
