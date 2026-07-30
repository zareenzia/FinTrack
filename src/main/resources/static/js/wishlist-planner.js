/**
 * wishlist-planner.js
 * Wishlist & Purchase Planner tab (Tab 4) of the Financial Planner page.
 * Paired with financial-planner.html / financial-planner.js — reuses shared helpers via
 * window.FPShared (BASE, apiFetch, showToast, fmtCurrency, fmtNum, escHtml, clearForm),
 * mirroring budget-planner.js's window.BudgetPlanner export precedent.
 */
(function () {
    'use strict';

    var Shared = window.FPShared || {};
    var BASE = Shared.BASE;
    var apiFetch = Shared.apiFetch;
    var showToast = Shared.showToast;
    var fmtCurrency = Shared.fmtCurrency;
    var escHtml = Shared.escHtml;
    var clearForm = Shared.clearForm;

    var WP_NEED_LEVELS = ['MUST_HAVE', 'SHOULD_HAVE', 'NICE_TO_HAVE'];
    var WP_NEED_COL_IDS = { MUST_HAVE: 'wpKanbanMust', SHOULD_HAVE: 'wpKanbanShould', NICE_TO_HAVE: 'wpKanbanNice' };
    var WP_NEED_COUNT_IDS = { MUST_HAVE: 'wpKanbanMustCount', SHOULD_HAVE: 'wpKanbanShouldCount', NICE_TO_HAVE: 'wpKanbanNiceCount' };
    var WP_ACTIVE_STATUSES = ['PLANNING', 'WAITING', 'READY'];
    var WP_PAGE_SIZE = 9;

    // ── State ────────────────────────────────────────────────────────────────
    var wpData = [];
    var wpSummary = {};
    var wpAnalytics = null;
    var wpListPage = 1;
    var wpRatioChartInst = null;
    var wpMonthlyChartInst = null;
    var wpPendingCancelId = null;
    var wpPendingPurchaseId = null;
    var wpPendingImageFile = null;
    var wpCategoriesCache = null;
    var wpAccountsCache = null;
    var wpSavingsGoalsCache = null;
    var wpHasBudgetPlan = false;
    var wpBudgetRemainingAmount = null;
    var wpDraggingId = null;
    var wpDraggingNeedLevel = null;

    function setText(id, val) { var el = document.getElementById(id); if (el) el.textContent = val; }

    // ── Labels / formatting helpers ─────────────────────────────────────────
    function monthLabel(ym) {
        if (!ym) return 'Unscheduled';
        var parts = ym.split('-');
        var months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        var idx = parseInt(parts[1], 10) - 1;
        return (months[idx] || ym) + ' ' + parts[0];
    }
    function needLevelLabel(v) {
        var m = { MUST_HAVE: '🔴 Must Have', SHOULD_HAVE: '🟡 Should Have', NICE_TO_HAVE: '🟢 Nice To Have' };
        return m[v] || v;
    }
    function priorityLabel(v) {
        var m = { CRITICAL: 'Critical', HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };
        return m[v] || v;
    }
    function statusLabel(v) {
        var m = { PLANNING: 'Planning', WAITING: 'Waiting', READY: 'Ready', PURCHASED: 'Purchased', CANCELLED: 'Cancelled' };
        return m[v] || v;
    }
    function cancelReasonLabel(v) {
        var m = { TOO_EXPENSIVE: 'Too Expensive', NOT_NEEDED: 'Not Needed', CHANGED_MIND: 'Changed Mind', BOUGHT_ELSEWHERE: 'Bought Somewhere Else' };
        return m[v] || v;
    }
    function fmtDateTime(iso) {
        if (!iso) return '';
        try { return new Date(iso).toLocaleString(); } catch (e) { return iso; }
    }
    function affordabilityBadgeHtml(item) {
        var aff = item.affordability;
        if (!aff || !aff.status) return '';
        var cls = aff.status === 'CAN_BUY_NOW' ? 'can-buy' : (aff.status === 'WAIT' ? 'wait' : 'not-afford');
        return '<span class="wp-afford-badge ' + cls + '">' + escHtml(aff.statusLabel || '') + '</span>';
    }

    // ── Data loading ─────────────────────────────────────────────────────────
    window.loadWishlist = async function () {
        var results = await Promise.all([
            apiFetch(BASE + '/purchase-items'),
            apiFetch(BASE + '/purchase-items/summary')
        ]);
        wpData = results[0] || [];
        wpSummary = results[1] || {};
        renderSummaryCards();
        loadBudgetLinkWidget();
        populateWpFilterOptions();
        window.renderWishlistFiltered();
        loadWishlistAnalytics();
    };

    async function loadWishlistAnalytics() {
        wpAnalytics = await apiFetch(BASE + '/purchase-items/analytics');
        window.drawWishlistCharts();
    }

    function renderSummaryCards() {
        if (!wpSummary || wpSummary.totalWishlistValue == null) { renderSummaryCardsFromData(); return; }
        setText('wpTotalValue', fmtCurrency(wpSummary.totalWishlistValue));
        setText('wpMustCount', wpSummary.mustHaveCount);
        setText('wpShouldCount', wpSummary.shouldHaveCount);
        setText('wpNiceCount', wpSummary.niceToHaveCount);
        setText('wpReadyCount', wpSummary.readyToBuyCount);
        setText('wpPlannedThisMonth', fmtCurrency(wpSummary.moneyPlannedThisMonth));
    }

    /** Cheap client-side re-derivation used after drag-and-drop, so the summary cards update instantly
     * without waiting on a full server round trip (per the app's optimistic-update convention here). */
    function renderSummaryCardsFromData() {
        var active = wpData.filter(function (i) { return WP_ACTIVE_STATUSES.indexOf(i.status) !== -1; });
        var totalValue = active.reduce(function (s, i) { return s + (i.estimatedPrice || 0); }, 0);
        var must = active.filter(function (i) { return i.needLevel === 'MUST_HAVE'; }).length;
        var should = active.filter(function (i) { return i.needLevel === 'SHOULD_HAVE'; }).length;
        var nice = active.filter(function (i) { return i.needLevel === 'NICE_TO_HAVE'; }).length;
        var ready = active.filter(function (i) { return i.status === 'READY'; }).length;
        var currentMonth = new Date().toISOString().slice(0, 7);
        var planned = active.filter(function (i) { return i.targetMonth === currentMonth; })
            .reduce(function (s, i) { return s + (i.estimatedPrice || 0); }, 0);
        setText('wpTotalValue', fmtCurrency(totalValue));
        setText('wpMustCount', must);
        setText('wpShouldCount', should);
        setText('wpNiceCount', nice);
        setText('wpReadyCount', ready);
        setText('wpPlannedThisMonth', fmtCurrency(planned));
    }

    async function loadBudgetLinkWidget() {
        var statsEl = document.getElementById('wpBudgetLinkStats');
        var emptyEl = document.getElementById('wpNoBudgetPlan');
        try {
            var data = await fetch('/api/budget-plans/current').then(function (r) { return r.ok ? r.json() : null; });
            if (!data || !data.hasCurrent) {
                wpHasBudgetPlan = false;
                wpBudgetRemainingAmount = null;
                if (statsEl) statsEl.classList.add('d-none');
                if (emptyEl) emptyEl.classList.remove('d-none');
                return;
            }
            wpHasBudgetPlan = true;
            if (statsEl) statsEl.classList.remove('d-none');
            if (emptyEl) emptyEl.classList.add('d-none');
            var remaining = (data.summary && data.summary.remaining) || 0;
            wpBudgetRemainingAmount = remaining;
            var planned = (wpSummary && wpSummary.moneyPlannedThisMonth) || 0;
            setText('wpBudgetRemaining', fmtCurrency(remaining));
            setText('wpPlannedPurchasesStat', fmtCurrency(planned));
            setText('wpAvailableAfterPlanned', fmtCurrency(remaining - planned));
        } catch (e) { /* silently ignore, matches dashboard.html's loadFpWidget precedent */ }
    }

    // ── Filter/sort/render ───────────────────────────────────────────────────
    function populateWpFilterOptions() {
        var monthSel = document.getElementById('wpMonthFilter');
        var catSel = document.getElementById('wpCategoryFilter');
        var storeSel = document.getElementById('wpStoreFilter');
        if (monthSel) {
            var months = Array.from(new Set(wpData.map(function (i) { return i.targetMonth; }).filter(Boolean))).sort();
            var prevM = monthSel.value;
            monthSel.innerHTML = '<option value="">All Months</option>' + months.map(function (m) {
                return '<option value="' + m + '">' + escHtml(monthLabel(m)) + '</option>';
            }).join('');
            if (months.indexOf(prevM) !== -1) monthSel.value = prevM;
        }
        if (catSel) {
            var cats = Array.from(new Set(wpData.map(function (i) { return i.category; }).filter(Boolean))).sort();
            var prevC = catSel.value;
            catSel.innerHTML = '<option value="">All Categories</option>' + cats.map(function (c) {
                return '<option value="' + escHtml(c) + '">' + escHtml(c) + '</option>';
            }).join('');
            if (cats.indexOf(prevC) !== -1) catSel.value = prevC;
        }
        if (storeSel) {
            var stores = Array.from(new Set(wpData.map(function (i) { return i.store; }).filter(Boolean))).sort();
            var prevS = storeSel.value;
            storeSel.innerHTML = '<option value="">All Stores</option>' + stores.map(function (s) {
                return '<option value="' + escHtml(s) + '">' + escHtml(s) + '</option>';
            }).join('');
            if (stores.indexOf(prevS) !== -1) storeSel.value = prevS;
        }
    }

    function getFilteredSortedItems() {
        var q = (document.getElementById('wpSearch').value || '').toLowerCase();
        var need = document.getElementById('wpNeedFilter').value;
        var priority = document.getElementById('wpPriorityFilter').value;
        var status = document.getElementById('wpStatusFilter').value;
        var month = document.getElementById('wpMonthFilter').value;
        var category = document.getElementById('wpCategoryFilter').value;
        var store = document.getElementById('wpStoreFilter').value;
        var priceMinRaw = document.getElementById('wpPriceMin').value;
        var priceMaxRaw = document.getElementById('wpPriceMax').value;
        var priceMin = priceMinRaw === '' ? null : parseFloat(priceMinRaw);
        var priceMax = priceMaxRaw === '' ? null : parseFloat(priceMaxRaw);
        var sort = document.getElementById('wpSort').value;

        var list = wpData.filter(function (i) {
            return (!q || i.itemName.toLowerCase().indexOf(q) !== -1 || (i.category || '').toLowerCase().indexOf(q) !== -1 || (i.store || '').toLowerCase().indexOf(q) !== -1)
                && (!need || i.needLevel === need)
                && (!priority || i.priority === priority)
                && (!status || i.status === status)
                && (!month || i.targetMonth === month)
                && (!category || i.category === category)
                && (!store || i.store === store)
                && (priceMin === null || (i.estimatedPrice || 0) >= priceMin)
                && (priceMax === null || (i.estimatedPrice || 0) <= priceMax);
        });

        if (sort === 'price_desc') {
            list.sort(function (a, b) { return (b.estimatedPrice || 0) - (a.estimatedPrice || 0); });
        } else if (sort === 'priority') {
            var order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };
            list.sort(function (a, b) {
                var oa = order[a.priority] !== undefined ? order[a.priority] : 9;
                var ob = order[b.priority] !== undefined ? order[b.priority] : 9;
                return oa - ob;
            });
        } else if (sort === 'month') {
            list.sort(function (a, b) { return (a.targetMonth || '9999').localeCompare(b.targetMonth || '9999'); });
        } else {
            list.sort(function (a, b) { return (b.createdAt || '').localeCompare(a.createdAt || ''); });
        }
        return list;
    }

    window.renderWishlistFiltered = function () {
        renderWishlistBoard();
        window.renderWishlistList();
    };

    function renderWishlistBoard() {
        var list = getFilteredSortedItems().filter(function (i) { return i.status !== 'PURCHASED' && i.status !== 'CANCELLED'; });
        renderKanban(list);
        renderTimeline(list);
    }

    /** Kanban columns always show manual drag order (not the "Sort by" dropdown, which drives the
     *  List view below) — items never manually dragged fall back to newest-first. */
    function sortForBoard(items) {
        return items.slice().sort(function (a, b) {
            var pa = a.boardPosition == null ? Infinity : a.boardPosition;
            var pb = b.boardPosition == null ? Infinity : b.boardPosition;
            if (pa !== pb) return pa - pb;
            return (b.createdAt || '').localeCompare(a.createdAt || '');
        });
    }

    function renderKanban(list) {
        WP_NEED_LEVELS.forEach(function (level) {
            var items = sortForBoard(list.filter(function (i) { return i.needLevel === level; }));
            var countEl = document.getElementById(WP_NEED_COUNT_IDS[level]);
            if (countEl) countEl.textContent = items.length;
            var body = document.getElementById(WP_NEED_COL_IDS[level]);
            if (!body) return;
            body.innerHTML = items.length
                ? items.map(buildPurchaseCard).join('')
                : '<div class="fp-empty" style="padding:1.5rem 0.5rem;"><p class="mb-0" style="font-size:0.8rem;">Drop items here</p></div>';
            body.querySelectorAll('.wp-card').forEach(wireDraggableCard);
            wireKanbanColumnDrop(body, level);
        });
    }

    function buildTimelineMonths(list) {
        var months = [];
        var now = new Date();
        for (var i = 0; i < 12; i++) {
            var d = new Date(now.getFullYear(), now.getMonth() + i, 1);
            months.push(d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0'));
        }
        var extra = {};
        list.forEach(function (item) { if (item.targetMonth) extra[item.targetMonth] = true; });
        Object.keys(extra).forEach(function (m) { if (months.indexOf(m) === -1) months.push(m); });
        months.sort();
        var hasUnscheduled = list.some(function (i) { return !i.targetMonth; });
        if (hasUnscheduled) months.push('UNSCHEDULED');
        return months;
    }

    function renderTimeline(list) {
        var months = buildTimelineMonths(list);
        var track = document.getElementById('wpTimelineTrack');
        if (!track) return;
        track.innerHTML = months.map(function (m) {
            var items = list.filter(function (i) { return (i.targetMonth || 'UNSCHEDULED') === m; });
            var total = items.reduce(function (s, i) { return s + (i.estimatedPrice || 0); }, 0);
            var bodyId = 'wpMonth_' + m.replace('-', '_');
            return '<div class="wp-month-group" data-month="' + m + '">' +
                '<div class="wp-month-group-header"><span>' + escHtml(monthLabel(m === 'UNSCHEDULED' ? null : m)) + '</span>' +
                '<span class="wp-month-group-total">' + fmtCurrency(total) + '</span></div>' +
                '<div class="wp-month-group-body" id="' + bodyId + '">' +
                (items.length ? items.map(buildPurchaseCard).join('') : '<div class="fp-empty" style="padding:1.5rem 0.5rem;"><p class="mb-0" style="font-size:0.8rem;">No items</p></div>') +
                '</div></div>';
        }).join('');
        months.forEach(function (m) {
            var body = document.getElementById('wpMonth_' + m.replace('-', '_'));
            if (!body) return;
            body.querySelectorAll('.wp-card').forEach(wireDraggableCard);
            wireMonthGroupDrop(body, m);
        });
    }

    window.renderWishlistList = function () {
        var list = getFilteredSortedItems();
        var total = list.length;
        var totalPages = Math.ceil(total / WP_PAGE_SIZE) || 1;
        if (wpListPage > totalPages) wpListPage = totalPages;
        var start = (wpListPage - 1) * WP_PAGE_SIZE;
        var page = list.slice(start, start + WP_PAGE_SIZE);
        var grid = document.getElementById('wpListGrid');
        if (!grid) return;
        if (!total) {
            grid.innerHTML = '<div class="fp-empty" style="grid-column:1/-1"><i class="fas fa-cart-shopping"></i><p>No purchases yet. Click <strong>Add Purchase</strong> to start planning.</p></div>';
            renderWpPagination(0);
            return;
        }
        grid.innerHTML = page.map(buildPurchaseCard).join('');
        renderWpPagination(total);
    };

    function renderWpPagination(total) {
        var container = document.getElementById('wpListPagination');
        if (!container) return;
        var totalPages = Math.ceil(total / WP_PAGE_SIZE) || 1;
        if (totalPages <= 1) { container.innerHTML = ''; return; }
        var cur = wpListPage;
        var html = '<span class="fp-page-info">Page ' + cur + ' of ' + totalPages + '</span>';
        html += '<button class="fp-page-btn" onclick="wpSetPage(' + (cur - 1) + ')" ' + (cur <= 1 ? 'disabled' : '') + '><i class="fas fa-chevron-left"></i></button>';
        for (var p = Math.max(1, cur - 2); p <= Math.min(totalPages, cur + 2); p++) {
            html += '<button class="fp-page-btn' + (p === cur ? ' active' : '') + '" onclick="wpSetPage(' + p + ')">' + p + '</button>';
        }
        html += '<button class="fp-page-btn" onclick="wpSetPage(' + (cur + 1) + ')" ' + (cur >= totalPages ? 'disabled' : '') + '><i class="fas fa-chevron-right"></i></button>';
        container.innerHTML = html;
    }

    window.wpSetPage = function (p) { wpListPage = p; window.renderWishlistList(); };

    // ── Card renderer (shared by kanban / timeline / list) ──────────────────
    function buildPurchaseCard(item) {
        var imgHtml = item.imageUrl
            ? '<img class="wp-card-img" src="' + item.imageUrl + '" alt="">'
            : '<div class="wp-card-img-placeholder"><i class="fas fa-image"></i></div>';
        var progressHtml = '';
        if (item.linkedSavingsGoalId && item.linkedSavingsGoalProgressPercent != null) {
            var pct = Math.max(0, Math.min(100, Math.round(item.linkedSavingsGoalProgressPercent)));
            progressHtml = '<div class="wp-mini-progress"><div class="wp-mini-progress-bar" style="width:' + pct + '%"></div></div>' +
                '<div class="wp-card-meta">' + pct + '% saved toward goal</div>';
        }
        var isActive = item.status !== 'PURCHASED' && item.status !== 'CANCELLED';
        return '<div class="wp-card" draggable="true" data-id="' + item.id + '" onclick="openPurchaseDetailModal(' + item.id + ')">' +
            '<div class="wp-card-top">' + imgHtml +
            '<div class="flex-grow-1" style="min-width:0;">' +
                '<div class="wp-card-name text-truncate">' + escHtml(item.itemName) + '</div>' +
                '<div class="wp-card-price">' + fmtCurrency(item.estimatedPrice) + '</div>' +
                '<div class="wp-card-meta">' + escHtml(monthLabel(item.targetMonth)) + '</div>' +
            '</div>' +
            '<div class="wp-card-actions">' +
                (isActive ? '<button class="fp-action-btn" title="Mark Purchased" onclick="event.stopPropagation(); wpMarkPurchased(' + item.id + ')"><i class="fas fa-check"></i></button>' : '') +
                '<button class="fp-action-btn" title="Edit" onclick="event.stopPropagation(); openPurchaseModal(' + item.id + ')"><i class="fas fa-pen"></i></button>' +
                '<button class="fp-action-btn danger" title="Delete" onclick="event.stopPropagation(); confirmDelete(\'purchase\',' + item.id + ')"><i class="fas fa-trash"></i></button>' +
            '</div></div>' +
            '<div class="wp-card-badges">' +
                '<span class="fp-badge fp-badge-' + item.priority + '">' + priorityLabel(item.priority) + '</span>' +
                '<span class="fp-badge fp-badge-' + item.needLevel + '">' + needLevelLabel(item.needLevel) + '</span>' +
                '<span class="fp-badge fp-badge-' + item.status + '">' + statusLabel(item.status) + '</span>' +
                affordabilityBadgeHtml(item) +
                '<span class="wp-ai-badge" title="AI recommendations coming soon"><i class="fas fa-robot"></i> AI</span>' +
            '</div>' +
            progressHtml +
        '</div>';
    }

    // ── Drag and drop (native HTML5 DnD, modeled on sidebar.js's customizer) ─
    function wireDraggableCard(cardEl) {
        cardEl.addEventListener('dragstart', function (e) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', cardEl.dataset.id);
            cardEl.classList.add('dragging');
            var item = wpData.find(function (x) { return x.id === parseInt(cardEl.dataset.id, 10); });
            wpDraggingId = item ? item.id : null;
            wpDraggingNeedLevel = item ? item.needLevel : null;
        });
        cardEl.addEventListener('dragend', function () {
            cardEl.classList.remove('dragging');
            wpDraggingId = null;
            wpDraggingNeedLevel = null;
            // If the drag ended outside any valid drop zone, dragover's live DOM shuffling never got
            // persisted — re-render from wpData (the source of truth) to discard that stray preview.
            renderWishlistBoard();
        });
    }

    /** Classic "which sibling is the pointer past the midpoint of" reorder helper — finds the
     *  card the dragged item should land before, so cards visually shuffle live during drag. */
    function getDragAfterElement(container, y) {
        var candidates = Array.prototype.slice.call(container.querySelectorAll('.wp-card:not(.dragging)'));
        return candidates.reduce(function (closest, child) {
            var box = child.getBoundingClientRect();
            var offset = y - box.top - box.height / 2;
            if (offset < 0 && offset > closest.offset) {
                return { offset: offset, element: child };
            }
            return closest;
        }, { offset: Number.NEGATIVE_INFINITY, element: null }).element;
    }

    function wireKanbanColumnDrop(bodyEl, level) {
        bodyEl.addEventListener('dragover', function (e) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            bodyEl.classList.add('drag-over');
            if (wpDraggingNeedLevel === level) {
                var draggingEl = bodyEl.querySelector('.wp-card.dragging');
                if (!draggingEl) return;
                var afterElement = getDragAfterElement(bodyEl, e.clientY);
                if (afterElement == null) bodyEl.appendChild(draggingEl);
                else bodyEl.insertBefore(draggingEl, afterElement);
            }
        });
        bodyEl.addEventListener('dragleave', function () { bodyEl.classList.remove('drag-over'); });
        bodyEl.addEventListener('drop', function (e) {
            e.preventDefault();
            bodyEl.classList.remove('drag-over');
            var id = parseInt(e.dataTransfer.getData('text/plain'), 10);
            if (wpDraggingNeedLevel === level) {
                handleKanbanReorder(bodyEl, level);
            } else {
                handleKanbanDrop(id, level);
            }
        });
    }

    /** Persists the column's on-screen card order (already live-shuffled by dragover above) after a
     *  same-column drop — optimistic update + revert-on-error, matching handleKanbanDrop's convention. */
    async function handleKanbanReorder(bodyEl, level) {
        var orderedIds = Array.prototype.map.call(bodyEl.querySelectorAll('.wp-card'), function (el) {
            return parseInt(el.dataset.id, 10);
        });
        var previous = wpData.filter(function (i) { return i.needLevel === level; })
            .map(function (i) { return { id: i.id, boardPosition: i.boardPosition }; });

        orderedIds.forEach(function (id, idx) {
            var item = wpData.find(function (x) { return x.id === id; });
            if (item) item.boardPosition = idx;
        });
        renderWishlistBoard();

        var result = await apiFetch(BASE + '/purchase-items/reorder', {
            method: 'PATCH',
            body: JSON.stringify({ needLevel: level, orderedIds: orderedIds })
        });
        if (!result || result.error) {
            previous.forEach(function (p) {
                var item = wpData.find(function (x) { return x.id === p.id; });
                if (item) item.boardPosition = p.boardPosition;
            });
            renderWishlistBoard();
            showToast((result && result.error) || "Couldn't save the new order — reverted.", 'error');
        }
    }

    async function handleKanbanDrop(id, level) {
        var item = wpData.find(function (x) { return x.id === id; });
        if (!item || item.needLevel === level) return;
        var previous = item.needLevel;
        item.needLevel = level;
        renderWishlistBoard();
        renderSummaryCardsFromData();
        var result = await apiFetch(BASE + '/purchase-items/' + id + '/need-level', { method: 'PATCH', body: JSON.stringify({ needLevel: level }) });
        if (!result || result.error) {
            item.needLevel = previous;
            renderWishlistBoard();
            renderSummaryCardsFromData();
            showToast((result && result.error) || "Couldn't move item — reverted.", 'error');
        } else if (result.item) {
            Object.assign(item, result.item);
        }
    }

    function wireMonthGroupDrop(bodyEl, month) {
        bodyEl.addEventListener('dragover', function (e) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            bodyEl.classList.add('drag-over');
        });
        bodyEl.addEventListener('dragleave', function () { bodyEl.classList.remove('drag-over'); });
        bodyEl.addEventListener('drop', function (e) {
            e.preventDefault();
            bodyEl.classList.remove('drag-over');
            var id = parseInt(e.dataTransfer.getData('text/plain'), 10);
            handleTimelineDrop(id, month);
        });
    }

    async function handleTimelineDrop(id, month) {
        var item = wpData.find(function (x) { return x.id === id; });
        if (!item) return;
        var targetMonth = month === 'UNSCHEDULED' ? null : month;
        if ((item.targetMonth || null) === targetMonth) return;
        var previous = item.targetMonth;
        item.targetMonth = targetMonth;
        renderWishlistBoard();
        renderSummaryCardsFromData();
        var result = await apiFetch(BASE + '/purchase-items/' + id + '/target-month', { method: 'PATCH', body: JSON.stringify({ targetMonth: targetMonth }) });
        if (!result || result.error) {
            item.targetMonth = previous;
            renderWishlistBoard();
            renderSummaryCardsFromData();
            showToast((result && result.error) || "Couldn't move item — reverted.", 'error');
        } else if (result.item) {
            Object.assign(item, result.item);
            populateWpFilterOptions();
        }
    }

    // ── Add / Edit modal ─────────────────────────────────────────────────────
    async function loadLinkedGoalOptions(selectedId) {
        var sel = document.getElementById('purchaseLinkedGoal');
        if (!sel) return;
        if (wpSavingsGoalsCache === null) {
            wpSavingsGoalsCache = [];
            try {
                var current = await fetch('/api/budget-plans/current').then(function (r) { return r.ok ? r.json() : null; });
                if (current && current.hasCurrent && current.id) {
                    var full = await fetch('/api/budget-plans/' + current.id + '/full').then(function (r) { return r.ok ? r.json() : null; });
                    wpSavingsGoalsCache = (full && full.savings) || [];
                }
            } catch (e) { wpSavingsGoalsCache = []; }
        }
        sel.innerHTML = '<option value="">None</option>' + wpSavingsGoalsCache.map(function (g) {
            return '<option value="' + g.id + '">' + escHtml(g.categoryName) + ' (' + Math.round(g.percentUsed || 0) + '% saved)</option>';
        }).join('');
        if (selectedId) sel.value = selectedId;
    }

    window.openPurchaseModal = async function (id) {
        clearForm(['purchaseId', 'purchaseItemName', 'purchaseCategory', 'purchasePrice', 'purchaseBrand', 'purchaseStore',
            'purchaseUrl', 'purchaseTargetMonth', 'purchaseExpectedDate', 'purchaseNotes', 'purchaseImageFile']);
        document.getElementById('purchaseNeedLevel').value = 'SHOULD_HAVE';
        document.getElementById('purchasePriority').value = 'MEDIUM';
        document.getElementById('purchaseStatus').value = 'PLANNING';
        var preview = document.getElementById('purchaseImagePreview');
        preview.classList.add('d-none');
        preview.src = '';
        wpPendingImageFile = null;
        document.getElementById('purchaseModalTitle').innerHTML = '<i class="fas fa-cart-shopping me-2"></i>' + (id ? 'Edit Purchase' : 'Add Purchase');

        var item = id ? wpData.find(function (x) { return x.id === id; }) : null;
        await loadLinkedGoalOptions(item ? item.linkedSavingsGoalId : null);

        if (item) {
            document.getElementById('purchaseId').value = item.id;
            document.getElementById('purchaseItemName').value = item.itemName || '';
            document.getElementById('purchaseCategory').value = item.category || '';
            document.getElementById('purchasePrice').value = item.estimatedPrice || '';
            document.getElementById('purchaseNeedLevel').value = item.needLevel || 'SHOULD_HAVE';
            document.getElementById('purchasePriority').value = item.priority || 'MEDIUM';
            document.getElementById('purchaseBrand').value = item.brand || '';
            document.getElementById('purchaseStore').value = item.store || '';
            document.getElementById('purchaseUrl').value = item.purchaseUrl || '';
            document.getElementById('purchaseTargetMonth').value = item.targetMonth || '';
            document.getElementById('purchaseExpectedDate').value = item.expectedPurchaseDate || '';
            document.getElementById('purchaseStatus').value = WP_ACTIVE_STATUSES.indexOf(item.status) !== -1 ? item.status : 'PLANNING';
            document.getElementById('purchaseNotes').value = item.notes || '';
            if (item.imageUrl) { preview.src = item.imageUrl; preview.classList.remove('d-none'); }
        }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseModal')).show();
    };

    window.onPurchaseImageFileChange = function (e) {
        var file = e.target.files && e.target.files[0];
        wpPendingImageFile = file || null;
        if (file) {
            var preview = document.getElementById('purchaseImagePreview');
            preview.src = URL.createObjectURL(file);
            preview.classList.remove('d-none');
        }
    };

    window.savePurchaseItem = async function () {
        var id = document.getElementById('purchaseId').value;
        var linkedGoalRaw = document.getElementById('purchaseLinkedGoal').value;
        var body = {
            itemName: document.getElementById('purchaseItemName').value.trim(),
            estimatedPrice: parseFloat(document.getElementById('purchasePrice').value) || 0,
            category: document.getElementById('purchaseCategory').value.trim(),
            needLevel: document.getElementById('purchaseNeedLevel').value,
            priority: document.getElementById('purchasePriority').value,
            brand: document.getElementById('purchaseBrand').value.trim(),
            store: document.getElementById('purchaseStore').value.trim(),
            purchaseUrl: document.getElementById('purchaseUrl').value.trim(),
            notes: document.getElementById('purchaseNotes').value.trim(),
            targetMonth: document.getElementById('purchaseTargetMonth').value || null,
            expectedPurchaseDate: document.getElementById('purchaseExpectedDate').value || null,
            linkedSavingsGoalId: linkedGoalRaw ? parseInt(linkedGoalRaw, 10) : null,
            status: document.getElementById('purchaseStatus').value
        };
        if (!body.itemName || body.estimatedPrice <= 0) {
            showToast('Item name and a valid estimated price are required.', 'error'); return;
        }
        var url = id ? BASE + '/purchase-items/' + id : BASE + '/purchase-items';
        var result = await apiFetch(url, { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
        if (result && !result.error && result.item) {
            var savedId = result.item.id;
            if (wpPendingImageFile) await uploadPurchaseImage(savedId, wpPendingImageFile);
            showToast(id ? 'Purchase updated.' : 'Purchase added.');
            bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseModal')).hide();
            wpPendingImageFile = null;
            window.loadWishlist();
        } else if (result) {
            showToast(result.error || 'Save failed.', 'error');
        }
    };

    async function uploadPurchaseImage(id, file) {
        var formData = new FormData();
        formData.append('file', file);
        try {
            var res = await fetch(BASE + '/purchase-items/' + id + '/image', { method: 'POST', body: formData });
            if (res.status === 401) { window.location.href = '/login'; return; }
            var data = await res.json().catch(function () { return null; });
            if (!res.ok) showToast((data && data.error) || 'Image upload failed.', 'error');
        } catch (e) {
            showToast('Image upload failed.', 'error');
        }
    }

    // ── Purchase Details modal ───────────────────────────────────────────────
    window.openPurchaseDetailModal = async function (id) {
        var detail = await apiFetch(BASE + '/purchase-items/' + id);
        if (!detail || detail.error) { showToast((detail && detail.error) || 'Could not load purchase.', 'error'); return; }
        var item = detail.item;
        document.getElementById('purchaseDetailId').value = item.id;
        document.getElementById('purchaseDetailTitle').innerHTML = '<i class="fas fa-circle-info me-2"></i>' + escHtml(item.itemName);
        document.getElementById('purchaseDetailName').textContent = item.itemName;
        document.getElementById('purchaseDetailPrice').textContent = fmtCurrency(item.estimatedPrice);

        var img = document.getElementById('purchaseDetailImage');
        var placeholder = document.getElementById('purchaseDetailImagePlaceholder');
        if (item.imageUrl) { img.src = item.imageUrl; img.classList.remove('d-none'); placeholder.classList.add('d-none'); }
        else { img.classList.add('d-none'); placeholder.classList.remove('d-none'); }

        document.getElementById('purchaseDetailBadges').innerHTML =
            '<span class="fp-badge fp-badge-' + item.priority + ' me-1">' + priorityLabel(item.priority) + '</span>' +
            '<span class="fp-badge fp-badge-' + item.needLevel + ' me-1">' + needLevelLabel(item.needLevel) + '</span>' +
            '<span class="fp-badge fp-badge-' + item.status + '">' + statusLabel(item.status) + '</span>' +
            (item.cancelReason ? '<div class="fp-goal-meta mt-1">Reason: ' + escHtml(cancelReasonLabel(item.cancelReason)) + '</div>' : '') +
            '<div class="fp-goal-meta mt-1">Target: ' + escHtml(monthLabel(item.targetMonth)) + '</div>';

        var aff = item.affordability || {};
        var affCls = aff.status === 'CAN_BUY_NOW' ? 'can-buy' : (aff.status === 'WAIT' ? 'wait' : 'not-afford');
        document.getElementById('purchaseDetailAffordability').innerHTML = '<span class="wp-afford-badge ' + affCls + '">' + escHtml(aff.statusLabel || '—') + '</span>';
        document.getElementById('purchaseDetailWaitTime').textContent = aff.monthsRemaining != null && aff.monthsRemaining > 0
            ? aff.monthsRemaining + ' month' + (aff.monthsRemaining === 1 ? '' : 's') + ' remaining'
            : (aff.status === 'CAN_BUY_NOW' ? 'Ready now' : '—');

        renderDecisionMatrix(item.decisionMatrix);

        document.getElementById('purchaseDetailGoal').textContent = item.linkedSavingsGoalId
            ? (item.linkedSavingsGoalName || 'Linked goal') + ' — ' + Math.round(item.linkedSavingsGoalProgressPercent || 0) + '% saved'
            : 'Not linked to a savings goal.';

        document.getElementById('purchaseDetailBudget').textContent = wpHasBudgetPlan
            ? 'Monthly Budget Remaining: ' + fmtCurrency(wpBudgetRemainingAmount)
            : 'No active budget plan linked.';

        document.getElementById('purchaseDetailNotes').textContent = item.notes || 'No notes.';

        renderPriceHistory(detail.priceHistory || []);
        renderActivityTimeline(detail.activityTimeline || []);

        var isFinal = item.status === 'PURCHASED' || item.status === 'CANCELLED';
        document.getElementById('purchaseDetailPurchasedBtn').classList.toggle('d-none', isFinal);
        document.getElementById('purchaseDetailCancelBtn').classList.toggle('d-none', isFinal);

        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseDetailModal')).show();
    };

    function renderDecisionMatrix(dm) {
        var container = document.getElementById('purchaseDetailMatrix');
        if (!dm) { container.innerHTML = '—'; return; }
        var rows = [
            { label: 'Need', score: dm.needScore },
            { label: 'Urgency', score: dm.urgencyScore },
            { label: 'Budget', score: dm.budgetScore },
            { label: 'Savings', score: dm.savingsScore }
        ];
        var html = rows.map(function (r) {
            var rounded = Math.round(r.score || 0);
            var pct = (rounded / 5) * 100;
            var stars = '★'.repeat(rounded) + '☆'.repeat(Math.max(0, 5 - rounded));
            return '<div class="wp-decision-row"><span class="wp-decision-label">' + r.label + '</span>' +
                '<div class="wp-decision-bar-wrap"><div class="fp-progress"><div class="fp-progress-bar" style="width:' + pct + '%"></div></div></div>' +
                '<span style="width:70px;text-align:right;font-size:0.75rem;">' + stars + '</span></div>';
        }).join('');
        var overall = Math.round(dm.overallReadinessPercent || 0);
        var overallCls = overall >= 75 ? '' : overall >= 40 ? 'warn' : 'danger';
        html += '<div class="wp-decision-row"><span class="wp-decision-label"><strong>Readiness</strong></span>' +
            '<div class="wp-decision-bar-wrap"><div class="fp-progress wp-readiness-bar"><div class="fp-progress-bar ' + overallCls + '" style="width:' + overall + '%"></div></div></div>' +
            '<span style="width:70px;text-align:right;font-size:0.75rem;"><strong>' + overall + '%</strong></span></div>';
        container.innerHTML = html;
    }

    function renderPriceHistory(list) {
        var el = document.getElementById('purchaseDetailPriceHistory');
        if (!list.length) { el.innerHTML = '<li class="text-muted">No price changes recorded.</li>'; return; }
        el.innerHTML = list.slice().reverse().map(function (p) {
            return '<li>' + fmtCurrency(p.oldPrice) + ' &rarr; ' + fmtCurrency(p.newPrice) +
                '<div class="wp-detail-list-meta">' + escHtml(fmtDateTime(p.changedAt)) + '</div></li>';
        }).join('');
    }

    function renderActivityTimeline(list) {
        var el = document.getElementById('purchaseDetailActivity');
        if (!list.length) { el.innerHTML = '<li class="text-muted">No activity yet.</li>'; return; }
        el.innerHTML = list.map(function (a) {
            return '<li>' + escHtml(a.note || a.activityType) +
                '<div class="wp-detail-list-meta">' + escHtml(fmtDateTime(a.createdAt)) + '</div></li>';
        }).join('');
    }

    window.wpEditFromDetail = function () {
        var id = parseInt(document.getElementById('purchaseDetailId').value, 10);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseDetailModal')).hide();
        window.openPurchaseModal(id);
    };

    window.wpDeleteFromDetail = function () {
        var id = parseInt(document.getElementById('purchaseDetailId').value, 10);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseDetailModal')).hide();
        window.confirmDelete('purchase', id);
    };

    // ── Purchased -> Create Expense flow (reuses the existing transaction system) ──
    window.wpMarkPurchased = function (id) {
        wpPendingPurchaseId = id;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpPurchaseConfirmModal')).show();
    };

    window.wpMarkPurchasedFromDetail = function () {
        var id = parseInt(document.getElementById('purchaseDetailId').value, 10);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseDetailModal')).hide();
        window.wpMarkPurchased(id);
    };

    window.wpConfirmPurchaseNo = async function () {
        var id = wpPendingPurchaseId;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpPurchaseConfirmModal')).hide();
        wpPendingPurchaseId = null;
        if (!id) return;
        var result = await apiFetch(BASE + '/purchase-items/' + id + '/mark-purchased', { method: 'POST', body: JSON.stringify({ transactionId: null }) });
        if (result && !result.error) {
            showToast('Marked as purchased.');
            window.loadWishlist();
        } else if (result) {
            showToast(result.error || 'Failed to update.', 'error');
        }
    };

    window.wpConfirmPurchaseYes = function () {
        var id = wpPendingPurchaseId;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpPurchaseConfirmModal')).hide();
        if (id) openLogExpenseModal(id);
    };

    async function ensureExpenseFormOptionsLoaded() {
        var catSel = document.getElementById('wpExpenseCategory');
        var acctSel = document.getElementById('wpExpenseAccount');
        if (wpCategoriesCache === null) {
            try { wpCategoriesCache = await fetch('/api/categories?type=expense').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { wpCategoriesCache = []; }
        }
        if (wpAccountsCache === null) {
            try { wpAccountsCache = await fetch('/api/accounts').then(function (r) { return r.ok ? r.json() : []; }); }
            catch (e) { wpAccountsCache = []; }
        }
        catSel.innerHTML = '<option value="">Select category</option>' + wpCategoriesCache.map(function (c) {
            return '<option value="' + c.id + '">' + escHtml(c.name) + '</option>';
        }).join('');
        var acctLabel = function (a) { return a.accountNickname + (a.bankName ? ' (' + a.bankName + ')' : a.provider ? ' (' + a.provider + ')' : ''); };
        acctSel.innerHTML = '<option value="">-- No account --</option>' +
            '<option value="SAVINGS">💰 Savings (spend from savings)</option>' +
            wpAccountsCache.map(function (a) { return '<option value="' + a.id + '">' + escHtml(acctLabel(a)) + '</option>'; }).join('');
    }

    async function openLogExpenseModal(itemId) {
        var draft = await apiFetch(BASE + '/purchase-items/' + itemId + '/expense-draft');
        if (!draft) { showToast('Could not build expense draft.', 'error'); return; }
        await ensureExpenseFormOptionsLoaded();
        document.getElementById('wpExpenseItemId').value = itemId;
        document.getElementById('wpExpenseDescription').value = draft.description || '';
        document.getElementById('wpExpenseAmount').value = draft.amount || '';
        document.getElementById('wpExpenseDate').value = draft.date || '';
        document.getElementById('wpExpenseCategory').value = draft.categoryId || '';
        document.getElementById('wpExpenseAccount').value = '';
        document.getElementById('wpExpenseNotes').value = draft.details || '';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpExpenseModal')).show();
    }

    window.saveLogExpense = async function () {
        var itemId = parseInt(document.getElementById('wpExpenseItemId').value, 10);
        var accountVal = document.getElementById('wpExpenseAccount').value;
        var payload = {
            description: document.getElementById('wpExpenseDescription').value.trim(),
            amount: parseFloat(document.getElementById('wpExpenseAmount').value) || 0,
            category_id: document.getElementById('wpExpenseCategory').value ? parseInt(document.getElementById('wpExpenseCategory').value, 10) : null,
            transaction_type: 'expense',
            date: document.getElementById('wpExpenseDate').value,
            details: document.getElementById('wpExpenseNotes').value.trim() || null,
            sourceAccountId: accountVal && accountVal !== 'SAVINGS' ? parseInt(accountVal, 10) : null,
            destinationAccountId: null,
            fromSavings: accountVal === 'SAVINGS'
        };
        if (!payload.description || !payload.amount || payload.amount <= 0 || !payload.date) {
            showToast('Description, a valid amount, and a date are required.', 'error'); return;
        }
        try {
            var txRes = await fetch('/api/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
            var txData = await txRes.json().catch(function () { return null; });
            if (!txRes.ok) { showToast((txData && txData.error) || 'Failed to save the expense.', 'error'); return; }

            var linkResult = await apiFetch(BASE + '/purchase-items/' + itemId + '/mark-purchased', { method: 'POST', body: JSON.stringify({ transactionId: txData.id }) });
            bootstrap.Modal.getOrCreateInstance(document.getElementById('wpExpenseModal')).hide();
            if (linkResult && !linkResult.error) {
                showToast('Expense logged and purchase marked complete.');
            } else {
                showToast("Expense saved, but the purchase wasn't linked.", 'warning');
            }
            window.loadWishlist();
        } catch (e) {
            showToast('Network error while saving the expense.', 'error');
        }
    };

    // ── Cancel Purchase flow ─────────────────────────────────────────────────
    window.wpOpenCancelModal = function (id) {
        wpPendingCancelId = id;
        document.getElementById('wpCancelReason').value = 'TOO_EXPENSIVE';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpCancelModal')).show();
    };

    window.wpOpenCancelModalFromDetail = function () {
        var id = parseInt(document.getElementById('purchaseDetailId').value, 10);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('purchaseDetailModal')).hide();
        window.wpOpenCancelModal(id);
    };

    window.wpConfirmCancel = async function () {
        var id = wpPendingCancelId;
        var reason = document.getElementById('wpCancelReason').value;
        wpPendingCancelId = null;
        if (!id) return;
        var result = await apiFetch(BASE + '/purchase-items/' + id + '/cancel', { method: 'POST', body: JSON.stringify({ reason: reason }) });
        bootstrap.Modal.getOrCreateInstance(document.getElementById('wpCancelModal')).hide();
        if (result && !result.error) {
            showToast('Purchase cancelled.');
            window.loadWishlist();
        } else if (result) {
            showToast(result.error || 'Failed to cancel.', 'error');
        }
    };

    // ── Analytics charts ─────────────────────────────────────────────────────
    window.drawWishlistCharts = function () {
        if (!wpAnalytics) return;
        var textColor = getComputedStyle(document.documentElement).getPropertyValue('--text-body') || '#666';
        var mutedColor = getComputedStyle(document.documentElement).getPropertyValue('--text-muted-custom') || '#888';

        var ratioCtx = document.getElementById('wpRatioChart');
        if (ratioCtx) {
            if (wpRatioChartInst) wpRatioChartInst.destroy();
            wpRatioChartInst = new Chart(ratioCtx, {
                type: 'doughnut',
                data: {
                    labels: ['Must Have', 'Should Have', 'Nice To Have'],
                    datasets: [{
                        data: [wpAnalytics.mustHaveCount || 0, wpAnalytics.shouldHaveCount || 0, wpAnalytics.niceToHaveCount || 0],
                        backgroundColor: ['#ef4444', '#f97316', '#3b82f6'], borderWidth: 2, borderColor: 'transparent'
                    }]
                },
                options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom', labels: { color: textColor, font: { size: 11 } } } } }
            });
        }

        var monthlyCtx = document.getElementById('wpMonthlyChart');
        if (monthlyCtx) {
            if (wpMonthlyChartInst) wpMonthlyChartInst.destroy();
            var labels = (wpAnalytics.monthlyPlannedPurchases || []).map(function (m) { return monthLabel(m.month); });
            var planned = (wpAnalytics.monthlyPlannedPurchases || []).map(function (m) { return m.count; });
            var completed = (wpAnalytics.completedPurchasesPerMonth || []).map(function (m) { return m.count; });
            wpMonthlyChartInst = new Chart(monthlyCtx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [
                        { label: 'Planned', data: planned, backgroundColor: 'rgba(168,85,247,0.7)', borderRadius: 4 },
                        { label: 'Completed', data: completed, backgroundColor: 'rgba(34,197,94,0.7)', borderRadius: 4 }
                    ]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { labels: { color: textColor, font: { size: 11 } } } },
                    scales: {
                        x: { ticks: { color: mutedColor, font: { size: 10 } } },
                        y: { ticks: { color: mutedColor }, beginAtZero: true }
                    }
                }
            });
        }
    };

    // ── Init ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        window.loadWishlist();
    });

})();
