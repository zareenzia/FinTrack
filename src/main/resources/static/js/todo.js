/**
 * todo.js
 * Folders -> Lists -> Items (+ one level of sub-items) To-Do page.
 * Native HTML5 drag-and-drop, modeled directly on wishlist-planner.js's Kanban reorder pattern.
 */
(function () {
    'use strict';

    var BASE = '/api/todo';
    var AUTO_SAVE_DEBOUNCE_MS = 1200;

    // ── State ────────────────────────────────────────────────────────────────
    var tdFolders = [];
    var tdLists = [];
    var tdSelectedListId = null;
    var tdItems = [];
    var tdSelectedItemId = null;
    var tdSelectedItemDetail = null;
    var tdCollapsedFolders = {};
    var tdPendingDelete = null; // { type: 'folder'|'list'|'item', id }
    var tdDetailAutosaveTimer = null;

    // Drag state
    var tdDragKind = null; // 'folder' | 'list' | 'item' | 'subitem'
    var tdDragListId = null;
    var tdDragListSourceFolderId = undefined; // undefined = "not set" (distinct from null = standalone)

    // ── Shared toast (matches dashboard.html / notes.js) ────────────────────
    var TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };
    function showNotification(message, type) {
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

    function escHtml(text) {
        var map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return text ? String(text).replace(/[&<>"']/g, function (m) { return map[m]; }) : '';
    }

    async function apiFetch(url, options) {
        try {
            options = options || {};
            var headers = Object.assign({ 'Content-Type': 'application/json' }, options.headers || {});
            var res = await fetch(url, Object.assign({}, options, { headers: headers }));
            if (res.status === 401) { window.location.href = '/login'; return null; }
            if (res.status === 204) return {};
            return await res.json();
        } catch (e) {
            showNotification('Network error: ' + e.message, 'error');
            return null;
        }
    }

    // ── Data loading ─────────────────────────────────────────────────────────
    async function loadAll() {
        var results = await Promise.all([apiFetch(BASE + '/folders'), apiFetch(BASE + '/lists')]);
        tdFolders = results[0] || [];
        tdLists = results[1] || [];
        renderSidebarTree();
        if (tdSelectedListId && !tdLists.some(function (l) { return l.id === tdSelectedListId; })) {
            tdSelectedListId = null;
            closeDetailPanel();
        }
        if (tdSelectedListId) await loadItemsForSelectedList();
    }

    async function loadItemsForSelectedList() {
        tdItems = await apiFetch(BASE + '/lists/' + tdSelectedListId + '/items') || [];
        renderItems();
        if (tdSelectedItemId && !tdItems.some(function (i) { return i.id === tdSelectedItemId; })) {
            closeDetailPanel();
        }
    }

    // ── Sidebar tree rendering ───────────────────────────────────────────────
    function renderSidebarTree() {
        var container = document.getElementById('tdSidebarTree');
        var folderListsByFolder = {};
        var standaloneLists = [];
        tdLists.forEach(function (l) {
            if (l.folderId != null) {
                (folderListsByFolder[l.folderId] = folderListsByFolder[l.folderId] || []).push(l);
            } else {
                standaloneLists.push(l);
            }
        });

        var html = tdFolders.map(function (f) {
            var lists = folderListsByFolder[f.id] || [];
            var collapsed = !!tdCollapsedFolders[f.id];
            return '<div class="td-folder' + (collapsed ? ' collapsed' : '') + '" data-folder-id="' + f.id + '">' +
                '<div class="td-folder-header" onclick="window.tdToggleFolderCollapse(' + f.id + ')">' +
                    '<i class="fas fa-chevron-down td-chevron"></i>' +
                    '<i class="fas fa-folder"></i>' +
                    '<span class="flex-grow-1">' + escHtml(f.name) + '</span>' +
                    '<button class="td-icon-btn" onclick="event.stopPropagation(); window.tdOpenCreateListModal(' + f.id + ')" title="Add list"><i class="fas fa-plus"></i></button>' +
                    '<button class="td-icon-btn" onclick="event.stopPropagation(); window.tdOpenRenameFolderModal(' + f.id + ')" title="Rename"><i class="fas fa-pen"></i></button>' +
                    '<button class="td-icon-btn" onclick="event.stopPropagation(); window.tdConfirmDeleteFolder(' + f.id + ')" title="Delete"><i class="fas fa-trash"></i></button>' +
                '</div>' +
                '<div class="td-folder-lists" data-folder-id="' + f.id + '">' + lists.map(buildListRowHtml).join('') + '</div>' +
            '</div>';
        }).join('');

        html += '<div class="td-standalone-lists" id="tdStandaloneLists" data-folder-id="">' +
            standaloneLists.map(buildListRowHtml).join('') + '</div>';

        container.innerHTML = html;

        container.querySelectorAll('.td-folder-header').forEach(function (headerEl) {
            var folderId = parseInt(headerEl.closest('.td-folder').dataset.folderId, 10);
            wireDraggableFolder(headerEl, folderId);
            wireFolderHeaderListDrop(headerEl, folderId);
        });
        container.querySelectorAll('.td-list-row').forEach(function (rowEl) {
            var listId = parseInt(rowEl.dataset.id, 10);
            var folderId = rowEl.dataset.folderId === '' ? null : parseInt(rowEl.dataset.folderId, 10);
            wireDraggableList(rowEl, listId, folderId);
        });
        container.querySelectorAll('.td-folder-lists, .td-standalone-lists').forEach(function (bucketEl) {
            var folderId = bucketEl.dataset.folderId === '' ? null : parseInt(bucketEl.dataset.folderId, 10);
            wireListBucketDrop(bucketEl, folderId);
        });
        wireSidebarTreeDrop(container);
    }

    function buildListRowHtml(l) {
        var countLabel = l.itemCount > 0 ? (l.itemCount - l.completedItemCount) + ' left' : '';
        return '<div class="td-list-row' + (l.id === tdSelectedListId ? ' active' : '') + '" data-id="' + l.id +
            '" data-folder-id="' + (l.folderId == null ? '' : l.folderId) + '" onclick="window.tdSelectList(' + l.id + ')">' +
            '<i class="fas fa-list-ul"></i><span class="td-list-name">' + escHtml(l.name) + '</span>' +
            '<span class="td-list-count">' + countLabel + '</span>' +
            '<button class="td-icon-btn" onclick="event.stopPropagation(); window.tdOpenRenameListModal(' + l.id + ')" title="Rename"><i class="fas fa-pen"></i></button>' +
            '<button class="td-icon-btn" onclick="event.stopPropagation(); window.tdConfirmDeleteList(' + l.id + ')" title="Delete"><i class="fas fa-trash"></i></button>' +
        '</div>';
    }

    window.tdToggleFolderCollapse = function (folderId) {
        tdCollapsedFolders[folderId] = !tdCollapsedFolders[folderId];
        renderSidebarTree();
    };

    window.tdSelectList = async function (listId) {
        tdSelectedListId = listId;
        closeDetailPanel();
        renderSidebarTree();
        var list = tdLists.find(function (l) { return l.id === listId; });
        document.getElementById('tdSelectedListName').textContent = list ? list.name : '';
        document.getElementById('tdQuickAddRow').classList.remove('d-none');
        await loadItemsForSelectedList();
    };

    // ── Items (middle pane) ──────────────────────────────────────────────────
    function renderItems() {
        var container = document.getElementById('tdItemsContainer');
        if (!tdItems.length) {
            container.innerHTML = '<div class="td-empty"><i class="fas fa-clipboard-check"></i><p class="mb-0">No tasks yet — add one above.</p></div>';
            return;
        }
        container.innerHTML = tdItems.map(buildItemRowHtml).join('');
        container.querySelectorAll('.td-item-row').forEach(wireDraggableItem);
        wireItemContainerDrop(container);
    }

    function buildItemRowHtml(item) {
        var subBadge = item.subItemCount > 0 ? '<span class="td-item-sub-badge">' + item.subItemCompletedCount + ' of ' + item.subItemCount + '</span>' : '';
        return '<div class="td-item-row' + (item.completed ? ' completed' : '') + (item.id === tdSelectedItemId ? ' selected' : '') +
            '" data-id="' + item.id + '" onclick="window.tdOpenItemDetail(' + item.id + ')">' +
            '<input type="checkbox" class="td-checkbox" ' + (item.completed ? 'checked' : '') +
                ' onclick="event.stopPropagation()" onchange="window.tdToggleItemCompleted(' + item.id + ', this.checked)">' +
            '<span class="td-item-title">' + escHtml(item.title) + '</span>' + subBadge +
            '<button class="td-star-btn' + (item.important ? ' active' : '') + '" onclick="event.stopPropagation(); window.tdToggleItemImportant(' + item.id + ', ' + !item.important + ')"><i class="fas fa-star"></i></button>' +
            '<button class="td-item-delete" onclick="event.stopPropagation(); window.tdConfirmDeleteItem(' + item.id + ')"><i class="fas fa-trash"></i></button>' +
        '</div>';
    }

    window.tdAddQuickTask = async function () {
        var input = document.getElementById('tdQuickAddInput');
        var title = input.value.trim();
        if (!title || !tdSelectedListId) return;
        input.value = '';
        var result = await apiFetch(BASE + '/items', { method: 'POST', body: JSON.stringify({ title: title, listId: tdSelectedListId }) });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not add task.', 'error'); return; }
        await loadItemsForSelectedList();
        await refreshListCounts();
    };

    window.tdToggleItemCompleted = async function (id, completed) {
        var item = tdItems.find(function (i) { return i.id === id; });
        if (item) item.completed = completed;
        renderItems();
        var result = await apiFetch(BASE + '/items/' + id + '/complete', { method: 'PATCH', body: JSON.stringify({ completed: completed }) });
        if (!result || result.error) {
            if (item) item.completed = !completed;
            renderItems();
            showNotification((result && result.error) || "Couldn't update — reverted.", 'error');
        } else {
            await refreshListCounts();
            if (tdSelectedItemId === id) await window.tdOpenItemDetail(id);
        }
    };

    window.tdToggleItemImportant = async function (id, important) {
        var item = tdItems.find(function (i) { return i.id === id; });
        if (item) item.important = important;
        renderItems();
        var result = await apiFetch(BASE + '/items/' + id + '/important', { method: 'PATCH', body: JSON.stringify({ important: important }) });
        if (!result || result.error) {
            if (item) item.important = !important;
            renderItems();
            showNotification((result && result.error) || "Couldn't update — reverted.", 'error');
        }
    };

    async function refreshListCounts() {
        tdLists = await apiFetch(BASE + '/lists') || [];
        renderSidebarTree();
    }

    // ── Item detail panel (right pane) ──────────────────────────────────────
    window.tdOpenItemDetail = async function (id) {
        tdSelectedItemId = id;
        document.getElementById('tdLayout').classList.remove('td-no-detail');
        document.getElementById('tdDetailPanel').classList.remove('d-none');
        renderItems();

        var detail = await apiFetch(BASE + '/items/' + id);
        if (!detail || detail.error) { showNotification('Could not load task.', 'error'); return; }
        tdSelectedItemDetail = detail;
        renderDetailPanel();
    };

    function closeDetailPanel() {
        tdSelectedItemId = null;
        tdSelectedItemDetail = null;
        document.getElementById('tdLayout').classList.add('td-no-detail');
        document.getElementById('tdDetailPanel').classList.add('d-none');
    }

    function renderDetailPanel() {
        var item = tdSelectedItemDetail.item;
        var subItems = tdSelectedItemDetail.subItems || [];
        var panel = document.getElementById('tdDetailPanel');
        panel.innerHTML =
            '<div class="d-flex align-items-center gap-2 mb-2">' +
                '<input type="checkbox" class="td-checkbox" id="tdDetailCompleted" ' + (item.completed ? 'checked' : '') + '>' +
                '<input type="text" class="td-detail-title" id="tdDetailTitleInput" value="' + escHtml(item.title) + '">' +
                '<button class="td-star-btn' + (item.important ? ' active' : '') + '" id="tdDetailStarBtn"><i class="fas fa-star"></i></button>' +
                '<button class="td-icon-btn" id="tdDetailDeleteBtn" title="Delete"><i class="fas fa-trash"></i></button>' +
                '<button class="td-icon-btn" id="tdDetailCloseBtn" title="Close"><i class="fas fa-xmark"></i></button>' +
            '</div>' +
            '<div class="td-detail-row"><label>Notes</label><textarea class="td-detail-notes" id="tdDetailNotes" placeholder="Add notes…">' + escHtml(item.notes || '') + '</textarea></div>' +
            '<div class="td-detail-row"><label>Due date</label><input type="date" class="form-control form-control-sm" id="tdDetailDueDate" value="' + (item.dueDate || '') + '"></div>' +
            '<div class="td-detail-row"><label>Steps (' + (subItems.filter(function (s) { return s.completed; }).length) + ' of ' + subItems.length + ')</label>' +
                '<div id="tdSubItemsList">' + subItems.map(buildSubItemRowHtml).join('') + '</div>' +
                '<input type="text" class="td-add-step-input" id="tdAddStepInput" placeholder="+ Add a step" onkeydown="if(event.key===\'Enter\') window.tdAddStep()">' +
            '</div>' +
            '<div class="td-detail-footer">Created ' + (item.createdAt ? new Date(item.createdAt).toLocaleString() : '') + '</div>';

        document.getElementById('tdDetailCompleted').addEventListener('change', function (e) { window.tdToggleItemCompleted(item.id, e.target.checked); });
        document.getElementById('tdDetailStarBtn').addEventListener('click', function () { window.tdToggleItemImportant(item.id, !item.important); });
        document.getElementById('tdDetailDeleteBtn').addEventListener('click', function () { window.tdConfirmDeleteItem(item.id); });
        document.getElementById('tdDetailCloseBtn').addEventListener('click', function () { closeDetailPanel(); renderItems(); });
        document.getElementById('tdDetailTitleInput').addEventListener('input', scheduleDetailAutosave);
        document.getElementById('tdDetailNotes').addEventListener('input', scheduleDetailAutosave);
        document.getElementById('tdDetailDueDate').addEventListener('change', saveDetailFieldsNow);

        document.querySelectorAll('#tdSubItemsList .td-substep-row').forEach(wireDraggableSubItem);
        wireItemContainerDrop(document.getElementById('tdSubItemsList'), true);
    }

    function buildSubItemRowHtml(s) {
        return '<div class="td-substep-row' + (s.completed ? ' completed' : '') + '" data-id="' + s.id + '">' +
            '<input type="checkbox" class="td-checkbox" ' + (s.completed ? 'checked' : '') + ' onchange="window.tdToggleSubItemCompleted(' + s.id + ', this.checked)">' +
            '<span class="td-substep-title">' + escHtml(s.title) + '</span>' +
            '<button class="td-item-delete" onclick="window.tdDeleteSubItem(' + s.id + ')"><i class="fas fa-xmark"></i></button>' +
        '</div>';
    }

    function scheduleDetailAutosave() {
        clearTimeout(tdDetailAutosaveTimer);
        tdDetailAutosaveTimer = setTimeout(saveDetailFieldsNow, AUTO_SAVE_DEBOUNCE_MS);
    }

    async function saveDetailFieldsNow() {
        if (!tdSelectedItemId) return;
        var title = document.getElementById('tdDetailTitleInput').value.trim();
        if (!title) return;
        var notes = document.getElementById('tdDetailNotes').value;
        var dueDate = document.getElementById('tdDetailDueDate').value || null;
        var result = await apiFetch(BASE + '/items/' + tdSelectedItemId, { method: 'PUT', body: JSON.stringify({ title: title, notes: notes, dueDate: dueDate }) });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not save.', 'error'); return; }
        await loadItemsForSelectedList();
    }

    window.tdAddStep = async function () {
        var input = document.getElementById('tdAddStepInput');
        var title = input.value.trim();
        if (!title || !tdSelectedItemId) return;
        input.value = '';
        var result = await apiFetch(BASE + '/items', { method: 'POST', body: JSON.stringify({ title: title, parentItemId: tdSelectedItemId }) });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not add step.', 'error'); return; }
        await window.tdOpenItemDetail(tdSelectedItemId);
        await loadItemsForSelectedList();
    };

    window.tdToggleSubItemCompleted = async function (id, completed) {
        var result = await apiFetch(BASE + '/items/' + id + '/complete', { method: 'PATCH', body: JSON.stringify({ completed: completed }) });
        if (!result || result.error) { showNotification((result && result.error) || "Couldn't update — reverted.", 'error'); }
        await window.tdOpenItemDetail(tdSelectedItemId);
        await loadItemsForSelectedList();
    };

    window.tdDeleteSubItem = async function (id) {
        var result = await apiFetch(BASE + '/items/' + id, { method: 'DELETE' });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not delete step.', 'error'); return; }
        await window.tdOpenItemDetail(tdSelectedItemId);
        await loadItemsForSelectedList();
    };

    // ── Folder create/rename modal ───────────────────────────────────────────
    window.tdOpenCreateFolderModal = function () {
        document.getElementById('tdFolderId').value = '';
        document.getElementById('tdFolderName').value = '';
        document.getElementById('tdFolderModalTitle').textContent = 'New Folder';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdFolderModal')).show();
    };

    window.tdOpenRenameFolderModal = function (id) {
        var f = tdFolders.find(function (x) { return x.id === id; });
        if (!f) return;
        document.getElementById('tdFolderId').value = f.id;
        document.getElementById('tdFolderName').value = f.name;
        document.getElementById('tdFolderModalTitle').textContent = 'Rename Folder';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdFolderModal')).show();
    };

    window.tdSaveFolder = async function () {
        var id = document.getElementById('tdFolderId').value;
        var name = document.getElementById('tdFolderName').value.trim();
        if (!name) { showNotification('Folder name is required.', 'error'); return; }
        var url = id ? BASE + '/folders/' + id : BASE + '/folders';
        var result = await apiFetch(url, { method: id ? 'PUT' : 'POST', body: JSON.stringify({ name: name }) });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not save folder.', 'error'); return; }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdFolderModal')).hide();
        await loadAll();
    };

    window.tdConfirmDeleteFolder = function (id) {
        tdPendingDelete = { type: 'folder', id: id };
        document.getElementById('tdDeleteModalTitle').textContent = 'Delete Folder';
        document.getElementById('tdDeleteModalBody').textContent = 'This removes the folder — its lists stay, just no longer grouped.';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdDeleteModal')).show();
    };

    // ── List create/rename modal ─────────────────────────────────────────────
    function populateFolderSelect(selectedId) {
        var sel = document.getElementById('tdListFolder');
        sel.innerHTML = '<option value="">No folder (standalone)</option>' +
            tdFolders.map(function (f) { return '<option value="' + f.id + '">' + escHtml(f.name) + '</option>'; }).join('');
        sel.value = selectedId != null ? String(selectedId) : '';
    }

    window.tdOpenCreateListModal = function (folderId) {
        document.getElementById('tdListId').value = '';
        document.getElementById('tdListName').value = '';
        populateFolderSelect(folderId);
        document.getElementById('tdListModalTitle').textContent = 'New List';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdListModal')).show();
    };

    window.tdOpenRenameListModal = function (id) {
        var l = tdLists.find(function (x) { return x.id === id; });
        if (!l) return;
        document.getElementById('tdListId').value = l.id;
        document.getElementById('tdListName').value = l.name;
        populateFolderSelect(l.folderId);
        document.getElementById('tdListModalTitle').textContent = 'Rename List';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdListModal')).show();
    };

    window.tdSaveList = async function () {
        var id = document.getElementById('tdListId').value;
        var name = document.getElementById('tdListName').value.trim();
        var folderRaw = document.getElementById('tdListFolder').value;
        var folderId = folderRaw ? parseInt(folderRaw, 10) : null;
        if (!name) { showNotification('List name is required.', 'error'); return; }

        var result;
        if (id) {
            result = await apiFetch(BASE + '/lists/' + id, { method: 'PUT', body: JSON.stringify({ name: name, folderId: folderId }) });
            if (result && !result.error) {
                result = await apiFetch(BASE + '/lists/' + id + '/folder', { method: 'PATCH', body: JSON.stringify({ folderId: folderId }) });
            }
        } else {
            result = await apiFetch(BASE + '/lists', { method: 'POST', body: JSON.stringify({ name: name, folderId: folderId }) });
        }
        if (!result || result.error) { showNotification((result && result.error) || 'Could not save list.', 'error'); return; }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdListModal')).hide();
        await loadAll();
    };

    window.tdConfirmDeleteList = function (id) {
        tdPendingDelete = { type: 'list', id: id };
        document.getElementById('tdDeleteModalTitle').textContent = 'Delete List';
        document.getElementById('tdDeleteModalBody').textContent = 'This permanently deletes the list and all of its tasks. This can\'t be undone.';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdDeleteModal')).show();
    };

    window.tdConfirmDeleteItem = function (id) {
        tdPendingDelete = { type: 'item', id: id };
        document.getElementById('tdDeleteModalTitle').textContent = 'Delete Task';
        document.getElementById('tdDeleteModalBody').textContent = 'Delete this task and any of its steps?';
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdDeleteModal')).show();
    };

    window.tdConfirmDelete = async function () {
        if (!tdPendingDelete) return;
        var pending = tdPendingDelete;
        tdPendingDelete = null;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('tdDeleteModal')).hide();

        var url = pending.type === 'folder' ? BASE + '/folders/' + pending.id
            : pending.type === 'list' ? BASE + '/lists/' + pending.id
            : BASE + '/items/' + pending.id;
        var result = await apiFetch(url, { method: 'DELETE' });
        if (!result || result.error) { showNotification((result && result.error) || 'Could not delete.', 'error'); return; }
        showNotification('Deleted.');

        if (pending.type === 'list' && pending.id === tdSelectedListId) {
            tdSelectedListId = null;
            document.getElementById('tdSelectedListName').textContent = 'Select a list';
            document.getElementById('tdQuickAddRow').classList.add('d-none');
            closeDetailPanel();
        }
        if (pending.type === 'item' && pending.id === tdSelectedItemId) {
            closeDetailPanel();
        }
        await loadAll();
        if (tdSelectedListId) renderItems();
    };

    // ── Drag and drop (native HTML5 DnD, mirrors wishlist-planner.js) ───────
    function getDragAfterElement(container, y, selector) {
        var candidates = Array.prototype.slice.call(container.querySelectorAll(selector));
        return candidates.reduce(function (closest, child) {
            var box = child.getBoundingClientRect();
            var offset = y - box.top - box.height / 2;
            if (offset < 0 && offset > closest.offset) return { offset: offset, element: child };
            return closest;
        }, { offset: Number.NEGATIVE_INFINITY, element: null }).element;
    }

    // Folders — single bucket (all of the user's folders), reorder only.
    function wireDraggableFolder(headerEl, folderId) {
        headerEl.setAttribute('draggable', 'true');
        var folderEl = headerEl.closest('.td-folder');
        headerEl.addEventListener('dragstart', function (e) {
            e.stopPropagation();
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', String(folderId));
            tdDragKind = 'folder';
            folderEl.classList.add('dragging');
        });
        headerEl.addEventListener('dragend', function () {
            folderEl.classList.remove('dragging');
            tdDragKind = null;
            renderSidebarTree();
        });
    }

    function wireSidebarTreeDrop(containerEl) {
        containerEl.addEventListener('dragover', function (e) {
            if (tdDragKind !== 'folder') return;
            e.preventDefault();
            var draggingEl = containerEl.querySelector('.td-folder.dragging');
            if (!draggingEl) return;
            var afterElement = getDragAfterElement(containerEl, e.clientY, '.td-folder:not(.dragging)');
            var standaloneEl = document.getElementById('tdStandaloneLists');
            if (afterElement == null) containerEl.insertBefore(draggingEl, standaloneEl);
            else containerEl.insertBefore(draggingEl, afterElement);
        });
        containerEl.addEventListener('drop', function (e) {
            if (tdDragKind !== 'folder') return;
            e.preventDefault();
            handleFolderReorder(containerEl);
        });
    }

    async function handleFolderReorder(containerEl) {
        var orderedIds = Array.prototype.map.call(containerEl.querySelectorAll('.td-folder'), function (el) {
            return parseInt(el.dataset.folderId, 10);
        });
        var previous = tdFolders.map(function (f) { return f.id; });
        var byId = {};
        tdFolders.forEach(function (f) { byId[f.id] = f; });
        tdFolders = orderedIds.map(function (id) { return byId[id]; }).filter(Boolean);
        renderSidebarTree();

        var result = await apiFetch(BASE + '/folders/reorder', { method: 'PATCH', body: JSON.stringify({ orderedIds: orderedIds }) });
        if (!result || result.error) {
            var byIdPrev = {};
            tdFolders.forEach(function (f) { byIdPrev[f.id] = f; });
            tdFolders = previous.map(function (id) { return byIdPrev[id]; }).filter(Boolean);
            renderSidebarTree();
            showNotification((result && result.error) || "Couldn't reorder folders — reverted.", 'error');
        }
    }

    // Lists — bucket = folderId (null = standalone). Same-bucket drag reorders live; cross-bucket just moves.
    function wireDraggableList(rowEl, listId, folderId) {
        rowEl.setAttribute('draggable', 'true');
        rowEl.addEventListener('dragstart', function (e) {
            e.stopPropagation();
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', String(listId));
            tdDragKind = 'list';
            tdDragListId = listId;
            tdDragListSourceFolderId = folderId;
            rowEl.classList.add('dragging');
        });
        rowEl.addEventListener('dragend', function () {
            rowEl.classList.remove('dragging');
            tdDragKind = null;
            renderSidebarTree();
        });
    }

    /** Dropping a list directly on a folder's header files it into that folder — the main way to
     *  move a list into a folder that has none yet (its .td-folder-lists area is otherwise ~0-height). */
    function wireFolderHeaderListDrop(headerEl, folderId) {
        headerEl.addEventListener('dragover', function (e) {
            if (tdDragKind !== 'list' || tdDragListSourceFolderId === folderId) return;
            e.preventDefault();
            headerEl.classList.add('drag-over');
        });
        headerEl.addEventListener('dragleave', function () { headerEl.classList.remove('drag-over'); });
        headerEl.addEventListener('drop', function (e) {
            if (tdDragKind !== 'list' || tdDragListSourceFolderId === folderId) return;
            e.preventDefault();
            e.stopPropagation();
            headerEl.classList.remove('drag-over');
            handleListFolderMove(tdDragListId, folderId);
        });
    }

    function wireListBucketDrop(bucketEl, folderId) {
        bucketEl.addEventListener('dragover', function (e) {
            if (tdDragKind !== 'list') return;
            e.preventDefault();
            bucketEl.classList.add('drag-over');
            if (tdDragListSourceFolderId !== folderId) return;
            var draggingEl = document.querySelector('.td-list-row.dragging');
            if (!draggingEl) return;
            var afterElement = getDragAfterElement(bucketEl, e.clientY, '.td-list-row:not(.dragging)');
            if (afterElement == null) bucketEl.appendChild(draggingEl);
            else bucketEl.insertBefore(draggingEl, afterElement);
        });
        bucketEl.addEventListener('dragleave', function () { bucketEl.classList.remove('drag-over'); });
        bucketEl.addEventListener('drop', function (e) {
            if (tdDragKind !== 'list') return;
            e.preventDefault();
            bucketEl.classList.remove('drag-over');
            if (tdDragListSourceFolderId === folderId) handleListReorder(bucketEl, folderId);
            else handleListFolderMove(tdDragListId, folderId);
        });
    }

    async function handleListReorder(bucketEl, folderId) {
        var orderedIds = Array.prototype.map.call(bucketEl.querySelectorAll('.td-list-row'), function (el) {
            return parseInt(el.dataset.id, 10);
        });
        var previous = tdLists.slice();
        orderedIds.forEach(function (id, idx) {
            var l = tdLists.find(function (x) { return x.id === id; });
            if (l) l.boardPosition = idx;
        });
        renderSidebarTree();

        var result = await apiFetch(BASE + '/lists/reorder', { method: 'PATCH', body: JSON.stringify({ folderId: folderId, orderedIds: orderedIds }) });
        if (!result || result.error) {
            tdLists = previous;
            renderSidebarTree();
            showNotification((result && result.error) || "Couldn't reorder lists — reverted.", 'error');
        }
    }

    async function handleListFolderMove(listId, folderId) {
        var list = tdLists.find(function (l) { return l.id === listId; });
        if (!list) return;
        var previousFolderId = list.folderId;
        list.folderId = folderId;
        renderSidebarTree();

        var result = await apiFetch(BASE + '/lists/' + listId + '/folder', { method: 'PATCH', body: JSON.stringify({ folderId: folderId }) });
        if (!result || result.error) {
            list.folderId = previousFolderId;
            renderSidebarTree();
            showNotification((result && result.error) || "Couldn't move list — reverted.", 'error');
        }
    }

    // Items and sub-items — single bucket each (only one list / one parent is ever shown at a time), reorder only.
    function wireDraggableItem(rowEl) {
        rowEl.setAttribute('draggable', 'true');
        rowEl.addEventListener('dragstart', function (e) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', rowEl.dataset.id);
            tdDragKind = 'item';
            rowEl.classList.add('dragging');
        });
        rowEl.addEventListener('dragend', function () {
            rowEl.classList.remove('dragging');
            tdDragKind = null;
        });
    }

    function wireDraggableSubItem(rowEl) {
        rowEl.setAttribute('draggable', 'true');
        rowEl.addEventListener('dragstart', function (e) {
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', rowEl.dataset.id);
            tdDragKind = 'subitem';
            rowEl.classList.add('dragging');
        });
        rowEl.addEventListener('dragend', function () {
            rowEl.classList.remove('dragging');
            tdDragKind = null;
        });
    }

    /** Shared drop wiring for the items container and the sub-items container. */
    function wireItemContainerDrop(containerEl, isSubItems) {
        var kind = isSubItems ? 'subitem' : 'item';
        var rowSelector = isSubItems ? '.td-substep-row' : '.td-item-row';
        containerEl.addEventListener('dragover', function (e) {
            if (tdDragKind !== kind) return;
            e.preventDefault();
            var draggingEl = containerEl.querySelector(rowSelector + '.dragging');
            if (!draggingEl) return;
            var afterElement = getDragAfterElement(containerEl, e.clientY, rowSelector + ':not(.dragging)');
            if (afterElement == null) containerEl.appendChild(draggingEl);
            else containerEl.insertBefore(draggingEl, afterElement);
        });
        containerEl.addEventListener('drop', function (e) {
            if (tdDragKind !== kind) return;
            e.preventDefault();
            if (isSubItems) handleSubItemReorder(containerEl);
            else handleItemReorder(containerEl);
        });
    }

    async function handleItemReorder(containerEl) {
        var orderedIds = Array.prototype.map.call(containerEl.querySelectorAll('.td-item-row'), function (el) {
            return parseInt(el.dataset.id, 10);
        });
        var previous = tdItems.slice();
        orderedIds.forEach(function (id, idx) {
            var it = tdItems.find(function (x) { return x.id === id; });
            if (it) it.boardPosition = idx;
        });
        renderItems();

        var result = await apiFetch(BASE + '/items/reorder', { method: 'PATCH', body: JSON.stringify({ listId: tdSelectedListId, parentItemId: null, orderedIds: orderedIds }) });
        if (!result || result.error) {
            tdItems = previous;
            renderItems();
            showNotification((result && result.error) || "Couldn't reorder tasks — reverted.", 'error');
        }
    }

    async function handleSubItemReorder(containerEl) {
        var orderedIds = Array.prototype.map.call(containerEl.querySelectorAll('.td-substep-row'), function (el) {
            return parseInt(el.dataset.id, 10);
        });
        var result = await apiFetch(BASE + '/items/reorder', { method: 'PATCH', body: JSON.stringify({ listId: null, parentItemId: tdSelectedItemId, orderedIds: orderedIds }) });
        if (!result || result.error) {
            showNotification((result && result.error) || "Couldn't reorder steps — reverted.", 'error');
            await window.tdOpenItemDetail(tdSelectedItemId);
        }
    }

    // ── Init ─────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        loadAll();
    });
})();
