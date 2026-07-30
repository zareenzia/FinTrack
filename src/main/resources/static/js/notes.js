(function () {
    'use strict';

    /* ═══════════════════════════════════════════════════════════════
       Note Colors — 12 pastel swatches
    ═══════════════════════════════════════════════════════════════ */
    const NOTE_COLORS = [
        { hex: '#FFFFFF', name: 'White' },
        { hex: '#F3F4F6', name: 'Light Gray' },
        { hex: '#DBEAFE', name: 'Light Blue' },
        { hex: '#BFDBFE', name: 'Sky Blue' },
        { hex: '#D1FAE5', name: 'Mint Green' },
        { hex: '#DCFCE7', name: 'Light Green' },
        { hex: '#FEF3C7', name: 'Light Yellow' },
        { hex: '#FED7AA', name: 'Light Orange' },
        { hex: '#FBCFE8', name: 'Light Pink' },
        { hex: '#E9D5FF', name: 'Lavender' },
        { hex: '#DDD6FE', name: 'Light Purple' },
        { hex: '#FDE68A', name: 'Peach' }
    ];

    const AUTO_SAVE_DEBOUNCE_MS = 1200;

    /* ═══════════════════════════════════════════════════════════════
       State
    ═══════════════════════════════════════════════════════════════ */
    let allNotes = [];
    let currentFilter = 'all'; // all | pinned | done | archived
    let editingNoteId = null;
    let deleteNoteId = null;
    let deleteNoteModal = null;
    let quill = null;
    let selectedColor = '#FEF3C7';
    let tagChips = [];
    let autoSaveTimer = null;
    let modalIsOpen = false;

    /* ═══════════════════════════════════════════════════════════════
       Shared theme-aware toast (matches dashboard.html / todos.html)
    ═══════════════════════════════════════════════════════════════ */
    const TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };

    function showNotification(message, type = 'success') {
        const container = document.getElementById('toastContainer');
        if (!container) return;
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

    /* ═══════════════════════════════════════════════════════════════
       Init
    ═══════════════════════════════════════════════════════════════ */
    document.addEventListener('DOMContentLoaded', function () {
        deleteNoteModal = new bootstrap.Modal(document.getElementById('deleteNoteModal'));
        document.getElementById('confirmDeleteNoteBtn').addEventListener('click', confirmDeleteNote);
        buildColorSwatches();
        initQuill();
        initTagInput();
        initAutoSaveListeners();
        initModalLifecycle();
        loadNotes();
    });

    function initModalLifecycle() {
        const modalEl = document.getElementById('noteModal');
        modalEl.addEventListener('shown.bs.modal', function () { modalIsOpen = true; });
        modalEl.addEventListener('hidden.bs.modal', function () {
            modalIsOpen = false;
            clearTimeout(autoSaveTimer);
            autoSaveTimer = null;
            editingNoteId = null;
            setSaveStatus('');
        });
    }

    /* ═══════════════════════════════════════════════════════════════
       Quill editor setup
    ═══════════════════════════════════════════════════════════════ */
    function initQuill() {
        quill = new Quill('#noteEditorArea', {
            theme: 'snow',
            placeholder: 'Write your note here… Use the toolbar for rich formatting.',
            modules: { toolbar: false },
            formats: ['header', 'bold', 'italic', 'underline', 'list', 'align', 'indent']
        });

        // Wire toolbar buttons
        document.querySelectorAll('#noteEditorToolbar .tb-btn').forEach(function (btn) {
            btn.addEventListener('mousedown', function (e) {
                e.preventDefault(); // prevent editor blur
                applyFormat(btn.dataset.fmt, btn.dataset.val);
            });
        });

        // Update toolbar active state when selection or content changes. Guarded on `range` being
        // non-null: Quill's own 'selection-change' event also fires on blur (range === null), and
        // calling quill.getFormat() in that no-selection state has the side effect of forcing
        // Quill to reclaim DOM focus — which broke every other field in the modal from ever
        // keeping focus once the editor had been focused once. Only refresh while actually editing.
        quill.on('selection-change', function (range) {
            if (range) refreshToolbar();
        });
        quill.on('text-change', function (delta, oldDelta, source) {
            if (quill.hasFocus()) refreshToolbar();
            if (source === 'user') scheduleAutoSave();
        });
    }

    function applyFormat(fmt, val) {
        if (!quill) return;
        const cur = quill.getFormat();
        if (fmt === 'header') {
            const h = val ? parseInt(val) : false;
            quill.format('header', cur.header === h ? false : h, 'user');
        } else if (fmt === 'bold' || fmt === 'italic' || fmt === 'underline') {
            quill.format(fmt, !cur[fmt], 'user');
        } else if (fmt === 'list') {
            // Checklist has two "on" states (unchecked/checked) sharing one toolbar button — treat
            // either as already-a-checklist so clicking again turns it off, rather than just
            // resetting an already-checked item back to unchecked.
            const isChecklist = val === 'unchecked' && (cur.list === 'checked' || cur.list === 'unchecked');
            quill.format('list', isChecklist ? false : (cur.list === val ? false : val), 'user');
        } else if (fmt === 'align') {
            const v = val || false;
            quill.format('align', (cur.align || '') === (val || '') ? false : v, 'user');
        }
        quill.focus();
        setTimeout(refreshToolbar, 10);
    }

    function refreshToolbar() {
        if (!quill) return;
        const fmt = quill.getFormat();
        document.querySelectorAll('#noteEditorToolbar .tb-btn').forEach(function (btn) {
            const f = btn.dataset.fmt, v = btn.dataset.val || '';
            let active = false;
            if (f === 'header')    active = String(fmt.header || '') === v;
            else if (f === 'bold')      active = !!fmt.bold;
            else if (f === 'italic')    active = !!fmt.italic;
            else if (f === 'underline') active = !!fmt.underline;
            else if (f === 'list')      active = v === 'unchecked' ? (fmt.list === 'checked' || fmt.list === 'unchecked') : fmt.list === v;
            else if (f === 'align')     active = (fmt.align || '') === v;
            btn.classList.toggle('active', active);
        });
    }

    /* ═══════════════════════════════════════════════════════════════
       Color swatches
    ═══════════════════════════════════════════════════════════════ */
    function buildColorSwatches() {
        const container = document.getElementById('noteColorSwatches');
        container.innerHTML = NOTE_COLORS.map(function (c) {
            const hasBorder = (c.hex === '#FFFFFF' || c.hex === '#F3F4F6');
            return `<button type="button" class="color-swatch" data-color="${c.hex}"
                style="background:${c.hex};box-shadow:inset 0 0 0 ${hasBorder ? '1.5px rgba(0,0,0,0.18)' : '0px transparent'}"
                title="${c.name}" onclick="selectColor('${c.hex}')"></button>`;
        }).join('');
        selectColor(selectedColor);
    }

    function selectColor(hex) {
        selectedColor = hex;
        document.querySelectorAll('.color-swatch').forEach(function (s) {
            s.classList.toggle('selected', s.dataset.color === hex);
        });
        scheduleAutoSave();
    }

    /* ═══════════════════════════════════════════════════════════════
       Tag chip editor
    ═══════════════════════════════════════════════════════════════ */
    function initTagInput() {
        const input = document.getElementById('noteTagInput');
        input.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ',') {
                e.preventDefault();
                addTagFromInput();
            } else if (e.key === 'Backspace' && !input.value && tagChips.length) {
                tagChips.pop();
                renderTagChips();
                scheduleAutoSave();
            }
        });
        input.addEventListener('blur', addTagFromInput);
    }

    function addTagFromInput() {
        const input = document.getElementById('noteTagInput');
        const raw = input.value.trim().replace(/,$/, '');
        input.value = '';
        if (!raw) return;
        const tag = raw.slice(0, 40);
        if (!tagChips.some(function (t) { return t.toLowerCase() === tag.toLowerCase(); })) {
            tagChips.push(tag);
            renderTagChips();
            scheduleAutoSave();
        }
    }

    function renderTagChips() {
        const container = document.getElementById('noteTagChips');
        container.innerHTML = tagChips.map(function (t, i) {
            return `<span class="note-tag-editor-chip">${escapeHtml(t)}` +
                `<button type="button" class="note-tag-chip-remove" data-idx="${i}" aria-label="Remove tag ${escapeHtml(t)}">&times;</button>` +
                `</span>`;
        }).join('');
        container.querySelectorAll('.note-tag-chip-remove').forEach(function (btn) {
            btn.addEventListener('click', function () {
                tagChips.splice(parseInt(this.dataset.idx, 10), 1);
                renderTagChips();
                scheduleAutoSave();
            });
        });
    }

    function setTagsFromString(str) {
        tagChips = (str || '').split(',').map(function (t) { return t.trim(); }).filter(Boolean);
        renderTagChips();
    }

    function tagsToString() {
        return tagChips.join(', ');
    }

    /* ═══════════════════════════════════════════════════════════════
       Load notes from API
    ═══════════════════════════════════════════════════════════════ */
    async function loadNotes() {
        try {
            const url = currentFilter === 'archived' ? '/api/notes?archived=true' : '/api/notes';
            const resp = await fetch(url);
            allNotes = await resp.json();
            renderNotes(applyFilterAndSearch());
        } catch (err) {
            console.error('Error loading notes:', err);
            document.getElementById('notesContainer').innerHTML =
                '<div style="grid-column:1/-1;text-align:center;padding:40px;color:var(--text-muted)">Failed to load notes.</div>';
        }
    }

    /* ═══════════════════════════════════════════════════════════════
       Display notes
    ═══════════════════════════════════════════════════════════════ */
    function renderNotes(notes) {
        const container = document.getElementById('notesContainer');
        if (!notes.length) {
            container.innerHTML = `
                <div style="grid-column:1/-1;text-align:center;padding:56px 24px;color:var(--text-muted)">
                    <i class="fas fa-sticky-note" style="font-size:3.2rem;opacity:.35;margin-bottom:16px;display:block"></i>
                    <p style="font-size:1rem;margin:0">No notes found. Click <strong>+ New Note</strong> to get started!</p>
                </div>`;
            return;
        }

        container.innerHTML = notes.map(function (note) {
            const dateStr = new Date(note.updated_at).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
            const tagsHtml = note.tags ? note.tags.split(',').filter(Boolean).map(function (t) {
                return `<span class="note-tag-chip">${escapeHtml(t.trim())}</span>`;
            }).join('') : '';

            return `
            <div class="note-card${note.pinned ? ' pinned' : ''}${note.done ? ' done' : ''}" style="background-color:${escapeHtml(note.color || '#FEF3C7')}" onclick="window.__notesApp.editNote(${note.id})">
                <div class="note-pin-icon" title="Pinned"><i class="fas fa-thumbtack"></i></div>
                <div class="note-actions">
                    <button class="note-btn" onclick="event.stopPropagation();window.__notesApp.deleteNote(${note.id})" title="Delete">
                        <i class="fas fa-trash text-danger" style="font-size:.78rem"></i>
                    </button>
                    <button class="note-btn" onclick="event.stopPropagation();window.__notesApp.toggleArchive(${note.id},${!!note.archived})" title="${note.archived ? 'Unarchive' : 'Archive'}">
                        <i class="fas fa-box-archive ${note.archived ? 'text-warning' : 'text-muted'}" style="font-size:.78rem"></i>
                    </button>
                    <button class="note-btn" onclick="event.stopPropagation();window.__notesApp.togglePin(${note.id},${!!note.pinned})" title="${note.pinned ? 'Unpin' : 'Pin'}">
                        <i class="fas fa-thumbtack ${note.pinned ? 'text-warning' : 'text-muted'}" style="font-size:.78rem"></i>
                    </button>
                </div>
                <div class="note-card-header-row">
                    <input type="checkbox" class="note-done-checkbox" ${note.done ? 'checked' : ''}
                           onclick="event.stopPropagation()"
                           onchange="window.__notesApp.toggleDone(${note.id}, ${!!note.done})" title="Mark done">
                    <div class="note-title">${escapeHtml(note.title)}</div>
                </div>
                <div class="note-preview">${escapeHtml(note.preview)}</div>
                ${tagsHtml ? `<div class="note-tags-row">${tagsHtml}</div>` : ''}
                <div class="note-footer"><span>${dateStr}</span></div>
            </div>`;
        }).join('');
    }

    /* ═══════════════════════════════════════════════════════════════
       Open modals
    ═══════════════════════════════════════════════════════════════ */
    function openCreateNoteModal() {
        editingNoteId = null;
        document.getElementById('noteModalTitle').innerHTML = '<i class="fas fa-note-sticky me-2"></i>New Note';
        document.getElementById('noteTitle').value = '';
        setTagsFromString('');
        document.getElementById('notePinned').checked = false;
        document.getElementById('noteDone').checked = false;
        selectColorSilent('#FEF3C7');
        if (quill) {
            quill.setText('', 'silent');
            quill.history.clear();
        }
        setSaveStatus('');
        new bootstrap.Modal(document.getElementById('noteModal')).show();
        focusTitleOnceShown();
    }

    /**
     * Focuses the title field once the modal's fade-in transition completes — but only if the
     * user hasn't already clicked/focused a different field in the modal in the meantime (e.g. a
     * fast click into Tags or Content while the modal was still animating in). Without this guard,
     * `shown.bs.modal` firing after that early interaction yanks focus back to an empty title
     * field, silently discarding whatever the user just started typing elsewhere.
     */
    function focusTitleOnceShown() {
        document.getElementById('noteModal').addEventListener('shown.bs.modal', function titleFocus() {
            const modalEl = document.getElementById('noteModal');
            const active = document.activeElement;
            if (!modalEl.contains(active) || active === modalEl) {
                document.getElementById('noteTitle').focus();
            }
            this.removeEventListener('shown.bs.modal', titleFocus);
        });
    }

    /** Re-fetches the note list fresh before populating the modal, so edits always start from the true persisted state. */
    async function editNote(id) {
        await loadNotes();
        const note = allNotes.find(function (n) { return n.id === id; });
        if (!note) return;

        editingNoteId = id;
        document.getElementById('noteModalTitle').innerHTML = '<i class="fas fa-edit me-2"></i>Edit Note';
        document.getElementById('noteTitle').value = note.title;
        setTagsFromString(note.tags || '');
        document.getElementById('notePinned').checked = !!note.pinned;
        document.getElementById('noteDone').checked = !!note.done;
        selectColorSilent(note.color || '#FEF3C7');

        if (quill) {
            if (note.content && note.content.trim()) {
                quill.clipboard.dangerouslyPasteHTML(note.content, 'silent');
            } else {
                quill.setText('', 'silent');
            }
            quill.history.clear();
        }

        setSaveStatus('');
        new bootstrap.Modal(document.getElementById('noteModal')).show();
        focusTitleOnceShown();
    }

    /** Selects a color without scheduling an auto-save — used when populating the modal, not when the user picks a color. */
    function selectColorSilent(hex) {
        selectedColor = hex;
        document.querySelectorAll('.color-swatch').forEach(function (s) {
            s.classList.toggle('selected', s.dataset.color === hex);
        });
    }

    /* ═══════════════════════════════════════════════════════════════
       Save note (manual + auto-save)
    ═══════════════════════════════════════════════════════════════ */
    function buildNoteData(title) {
        const editorHtml = quill ? quill.root.innerHTML : '';
        const isEmpty = !editorHtml || editorHtml === '<p><br></p>' || editorHtml.trim() === '';
        return {
            title: title,
            content: isEmpty ? '' : editorHtml,
            color: selectedColor,
            tags: tagsToString(),
            pinned: document.getElementById('notePinned').checked,
            done: document.getElementById('noteDone').checked
        };
    }

    function setSaveStatus(text) {
        const el = document.getElementById('noteSaveStatus');
        if (el) el.textContent = text;
    }

    /** Debounced auto-save while the modal is open. Creates the note on first fire (once titled), then PUTs thereafter. */
    function scheduleAutoSave() {
        if (!modalIsOpen) return;
        const title = document.getElementById('noteTitle').value.trim();
        if (!title) { setSaveStatus(''); return; }
        setSaveStatus('Saving…');
        clearTimeout(autoSaveTimer);
        autoSaveTimer = setTimeout(async function () {
            await persistNote(buildNoteData(title), { isAuto: true });
        }, AUTO_SAVE_DEBOUNCE_MS);
    }

    async function persistNote(noteData, opts) {
        opts = opts || {};
        const wasCreate = !editingNoteId;
        try {
            const url    = editingNoteId ? `/api/notes/${editingNoteId}` : '/api/notes';
            const method = editingNoteId ? 'PUT' : 'POST';
            const resp = await fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(noteData)
            });

            if (resp.ok) {
                const saved = await resp.json();
                if (!editingNoteId) editingNoteId = saved.id;
                if (opts.isAuto) {
                    setSaveStatus('All changes saved');
                } else {
                    showNotification(wasCreate ? 'Note created ✓' : 'Note updated ✓', 'success');
                    bootstrap.Modal.getInstance(document.getElementById('noteModal')).hide();
                }
                await loadNotes();
                return true;
            }
            if (opts.isAuto) setSaveStatus('Failed to save — will retry');
            else showNotification('Failed to save note', 'error');
            return false;
        } catch (err) {
            console.error('Error saving note:', err);
            if (opts.isAuto) setSaveStatus('Failed to save — will retry');
            else showNotification('Error saving note', 'error');
            return false;
        }
    }

    async function saveNote() {
        const title = document.getElementById('noteTitle').value.trim();
        if (!title) {
            document.getElementById('noteTitle').focus();
            document.getElementById('noteTitle').classList.add('is-invalid');
            setTimeout(function () { document.getElementById('noteTitle').classList.remove('is-invalid'); }, 2000);
            return;
        }
        clearTimeout(autoSaveTimer);
        autoSaveTimer = null;
        await persistNote(buildNoteData(title), { isAuto: false });
    }

    function initAutoSaveListeners() {
        document.getElementById('noteTitle').addEventListener('input', scheduleAutoSave);
        document.getElementById('notePinned').addEventListener('change', scheduleAutoSave);
        document.getElementById('noteDone').addEventListener('change', scheduleAutoSave);
    }

    /* ═══════════════════════════════════════════════════════════════
       Delete note
    ═══════════════════════════════════════════════════════════════ */
    function deleteNote(id) {
        deleteNoteId = id;
        deleteNoteModal.show();
    }

    async function confirmDeleteNote() {
        if (deleteNoteId === null) return;
        try {
            const resp = await fetch(`/api/notes/${deleteNoteId}`, { method: 'DELETE' });
            if (resp.ok) {
                deleteNoteModal.hide();
                deleteNoteId = null;
                showNotification('Note deleted', 'success');
                await loadNotes();
            } else {
                showNotification('Failed to delete note', 'error');
            }
        } catch (err) {
            showNotification('Error deleting note', 'error');
        }
    }

    /* ═══════════════════════════════════════════════════════════════
       Toggle actions — each sends only the changed field
    ═══════════════════════════════════════════════════════════════ */
    async function patchNoteField(id, partial) {
        try {
            const resp = await fetch(`/api/notes/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(partial)
            });
            if (resp.ok) {
                await loadNotes();
            } else {
                showNotification('Failed to update note', 'error');
            }
        } catch (err) {
            console.error('Error updating note:', err);
            showNotification('Error updating note', 'error');
        }
    }

    function togglePin(id, isPinned) { return patchNoteField(id, { pinned: !isPinned }); }
    function toggleDone(id, isDone) { return patchNoteField(id, { done: !isDone }); }
    function toggleArchive(id, isArchived) { return patchNoteField(id, { archived: !isArchived }); }

    /* ═══════════════════════════════════════════════════════════════
       Search & filter (entirely client-side against the cached list)
    ═══════════════════════════════════════════════════════════════ */
    function applyFilterAndSearch() {
        let pool = applyFilter(allNotes, currentFilter);
        const q = document.getElementById('searchNotes').value.toLowerCase().trim();
        if (!q) return pool;
        return pool.filter(function (n) {
            const plain = stripHtmlClient(n.content || '');
            const tags = (n.tags || '').toLowerCase();
            return n.title.toLowerCase().includes(q) || plain.toLowerCase().includes(q) || tags.includes(q);
        });
    }

    function handleSearch() {
        renderNotes(applyFilterAndSearch());
    }

    function filterNotes(filter, btn) {
        currentFilter = filter;
        document.querySelectorAll('.filter-btn').forEach(function (b) { b.classList.remove('active'); });
        if (btn) btn.classList.add('active');
        loadNotes();
    }

    function applyFilter(notes, filter) {
        if (filter === 'pinned') return notes.filter(function (n) { return n.pinned && !n.archived; });
        if (filter === 'done') return notes.filter(function (n) { return n.done; });
        if (filter === 'archived') return notes; // already archived-only, fetched via ?archived=true
        return notes;
    }

    /* ═══════════════════════════════════════════════════════════════
       Utilities
    ═══════════════════════════════════════════════════════════════ */
    function stripHtmlClient(html) {
        var tmp = document.createElement('div');
        tmp.innerHTML = html;
        return tmp.textContent || tmp.innerText || '';
    }

    function escapeHtml(text) {
        const m = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return text ? String(text).replace(/[&<>"']/g, function (c) { return m[c]; }) : '';
    }

    // Exposed to inline onclick/onchange handlers in notes.html
    window.openCreateNoteModal = openCreateNoteModal;
    window.selectColor = selectColor;
    window.handleSearch = handleSearch;
    window.filterNotes = filterNotes;
    window.saveNote = saveNote;
    window.__notesApp = { editNote, deleteNote, togglePin, toggleDone, toggleArchive };
})();
