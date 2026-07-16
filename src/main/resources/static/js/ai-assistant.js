(function () {
    'use strict';

    const SUGGESTED_QUESTIONS = [
        'Where did I spend the most this month?',
        'How much money do I currently have?',
        'Show my highest expenses.',
        'How much did I save this year?',
        'How much gold do I own?',
        'Which account has the highest balance?',
        'Summarize this month\'s finances.',
        'What are my top spending categories?'
    ];

    const QUICK_ACTIONS = [
        'Summarize this month',
        'Analyze my spending',
        'How can I save more?',
        'Check my budget',
        'Show my assets',
        'Find unusual expenses',
        'Explain my balance',
        'Review my subscriptions',
        'Review savings',
        'Create a budget'
    ];

    let conversations = [];
    let currentConversationId = null;
    let aiEnabled = true;
    let sending = false;
    let deleteConvoModal = null;
    let deleteTargetId = null;

    const TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };

    function showTopNotification(message, type) {
        type = type || 'success';
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

    async function safeJson(response) {
        try { return await response.json(); } catch (e) { return null; }
    }

    function renderMarkdown(text) {
        try {
            const html = window.marked ? window.marked.parse(text) : text;
            return window.DOMPurify ? window.DOMPurify.sanitize(html) : html;
        } catch (e) {
            return document.createTextNode(text).textContent;
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        deleteConvoModal = new bootstrap.Modal(document.getElementById('aiDeleteConvoModal'));

        document.getElementById('newChatBtn').addEventListener('click', startNewChat);
        document.getElementById('aiInputForm').addEventListener('submit', onSubmit);
        document.getElementById('aiConfirmDeleteConvoBtn').addEventListener('click', confirmDeleteConversation);
        document.getElementById('toggleConvoPanelBtn').addEventListener('click', function () {
            document.getElementById('aiConvoPanel').classList.toggle('collapsed');
        });

        const textarea = document.getElementById('aiMessageInput');
        textarea.addEventListener('input', function () {
            this.style.height = 'auto';
            this.style.height = Math.min(this.scrollHeight, 120) + 'px';
        });
        textarea.addEventListener('keydown', function (e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                document.getElementById('aiInputForm').requestSubmit();
            }
        });

        renderSuggestions();
        renderQuickActions();
        loadSettings();
        loadConversations();
    });

    function renderQuickActions() {
        const container = document.getElementById('aiQuickActions');
        container.innerHTML = QUICK_ACTIONS.map(q =>
            `<button type="button" class="ai-suggestion-chip">${escapeHtml(q)}</button>`
        ).join('');
        container.querySelectorAll('.ai-suggestion-chip').forEach((chip, i) => {
            chip.addEventListener('click', () => {
                if (sending) return;
                sendMessage(QUICK_ACTIONS[i]);
            });
        });
    }

    function renderSuggestions() {
        const container = document.getElementById('aiSuggestions');
        container.innerHTML = SUGGESTED_QUESTIONS.map(q =>
            `<button type="button" class="ai-suggestion-chip">${escapeHtml(q)}</button>`
        ).join('');
        container.querySelectorAll('.ai-suggestion-chip').forEach((chip, i) => {
            chip.addEventListener('click', () => {
                const textarea = document.getElementById('aiMessageInput');
                textarea.value = SUGGESTED_QUESTIONS[i];
                textarea.focus();
            });
        });
    }

    function escapeHtml(text) {
        const map = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;' };
        return text.replace(/[&<>"']/g, m => map[m]);
    }

    async function loadSettings() {
        try {
            const settings = await fetch('/api/ai/settings').then(r => r.json());
            aiEnabled = settings.enabled !== false;
            document.getElementById('aiDisabledBanner').classList.toggle('d-none', aiEnabled);
            document.getElementById('aiSendBtn').disabled = !aiEnabled;
            document.getElementById('aiMessageInput').disabled = !aiEnabled;
        } catch (e) {
            // If settings can't load, leave the input enabled — the backend enforces the real gate.
        }
    }

    async function loadConversations() {
        try {
            conversations = await fetch('/api/ai/conversations').then(r => r.json());
            renderConvoList();
            if (conversations.length) {
                selectConversation(conversations[0].id);
            } else {
                showEmptyState();
            }
        } catch (e) {
            showTopNotification('Unable to load your conversations.', 'error');
        }
    }

    function renderConvoList() {
        const container = document.getElementById('aiConvoList');
        if (!conversations.length) {
            container.innerHTML = '<p class="text-muted small text-center mt-3">No conversations yet.</p>';
            return;
        }
        container.innerHTML = conversations.map(c => `
            <div class="ai-convo-item${c.id === currentConversationId ? ' active' : ''}" data-id="${c.id}">
                <span class="ai-convo-title">${escapeHtml(c.title || 'New chat')}</span>
                <button type="button" class="ai-convo-action-btn" title="Delete" data-delete-id="${c.id}"><i class="fas fa-trash"></i></button>
            </div>
        `).join('');
        container.querySelectorAll('.ai-convo-item').forEach(el => {
            el.addEventListener('click', function (e) {
                if (e.target.closest('[data-delete-id]')) return;
                selectConversation(parseInt(this.dataset.id, 10));
            });
        });
        container.querySelectorAll('[data-delete-id]').forEach(btn => {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                deleteTargetId = parseInt(this.dataset.deleteId, 10);
                deleteConvoModal.show();
            });
        });
    }

    function startNewChat() {
        currentConversationId = null;
        renderConvoList();
        showEmptyState();
    }

    function showEmptyState() {
        document.getElementById('aiMessages').innerHTML = `
            <div class="ai-empty-state" id="aiEmptyState">
                <i class="fas fa-sparkles"></i>
                <h5>Ask me anything about your finances</h5>
                <p class="text-muted small">I can look at your transactions, accounts, budgets, savings, and assets.</p>
                <div class="ai-suggestions" id="aiSuggestions"></div>
            </div>`;
        renderSuggestions();
    }

    async function selectConversation(id) {
        currentConversationId = id;
        renderConvoList();
        const container = document.getElementById('aiMessages');
        container.innerHTML = '<div class="text-center text-muted small mt-3">Loading…</div>';
        try {
            const messages = await fetch(`/api/ai/conversations/${id}/messages`).then(r => r.json());
            if (!messages.length) {
                showEmptyState();
                return;
            }
            container.innerHTML = '';
            messages.forEach(m => appendBubble(m.role, m.content));
            scrollToBottom();
        } catch (e) {
            showTopNotification('Unable to load this conversation.', 'error');
        }
    }

    function appendBubble(role, content, debug) {
        const container = document.getElementById('aiMessages');
        const emptyState = document.getElementById('aiEmptyState');
        if (emptyState) emptyState.remove();

        const bubble = document.createElement('div');
        bubble.className = role === 'user' ? 'ai-msg ai-msg-user' : 'ai-msg ai-msg-assistant';
        if (role === 'user') {
            bubble.textContent = content;
        } else {
            bubble.innerHTML = renderMarkdown(content);
        }
        container.appendChild(bubble);
        if (role === 'assistant' && debug) {
            container.appendChild(renderDebugPanel(debug));
        }
        return bubble;
    }

    function renderDebugPanel(debug) {
        const docs = Array.isArray(debug.retrievedDocuments) ? debug.retrievedDocuments : [];
        const entityTypes = docs.map(d => d.entityType).join(', ') || '—';
        const scores = docs.map(d => (typeof d.score === 'number' ? d.score.toFixed(3) : d.score)).join(', ') || '—';
        const orDash = v => (v === null || v === undefined) ? '—' : escapeHtml(String(v));

        const panel = document.createElement('details');
        panel.className = 'ai-debug-panel';
        panel.innerHTML = `
            <summary><i class="fas fa-bug me-1"></i>Developer info</summary>
            <dl>
                <dt>Embedding Provider</dt><dd>${orDash(debug.embeddingProvider)}</dd>
                <dt>Number of Documents</dt><dd>${orDash(debug.documentCount)}</dd>
                <dt>Retrieved Entity Types</dt><dd>${escapeHtml(entityTypes)}</dd>
                <dt>Similarity Scores</dt><dd>${escapeHtml(scores)}</dd>
                <dt>Tool Calls</dt><dd>${orDash(debug.toolCalls)}</dd>
                <dt>Execution Time</dt><dd>${orDash(debug.executionTimeMs)} ms</dd>
                <dt>Prompt Tokens</dt><dd>${orDash(debug.promptTokens)}</dd>
                <dt>Completion Tokens</dt><dd>${orDash(debug.completionTokens)}</dd>
                <dt>Total Tokens</dt><dd>${orDash(debug.totalTokens)}</dd>
            </dl>`;
        return panel;
    }

    function appendTypingIndicator() {
        const container = document.getElementById('aiMessages');
        const el = document.createElement('div');
        el.className = 'ai-typing';
        el.id = 'aiTypingIndicator';
        el.innerHTML = '<span></span><span></span><span></span>';
        container.appendChild(el);
        scrollToBottom();
        return el;
    }

    function appendErrorBubble(message, retryText) {
        const container = document.getElementById('aiMessages');
        const bubble = document.createElement('div');
        bubble.className = 'ai-msg ai-msg-error';
        bubble.innerHTML = `<div>${escapeHtml(message)}</div>`;
        const retryBtn = document.createElement('button');
        retryBtn.type = 'button';
        retryBtn.className = 'btn btn-sm btn-outline-danger';
        retryBtn.innerHTML = '<i class="fas fa-rotate-right me-1"></i>Retry';
        retryBtn.addEventListener('click', function () {
            bubble.remove();
            sendMessage(retryText);
        });
        bubble.appendChild(retryBtn);
        container.appendChild(bubble);
        scrollToBottom();
    }

    function scrollToBottom() {
        const container = document.getElementById('aiMessages');
        container.scrollTop = container.scrollHeight;
    }

    function onSubmit(e) {
        e.preventDefault();
        const textarea = document.getElementById('aiMessageInput');
        const text = textarea.value.trim();
        if (!text || sending) return;
        textarea.value = '';
        textarea.style.height = 'auto';
        sendMessage(text);
    }

    async function sendMessage(text) {
        if (!aiEnabled) {
            showTopNotification('AI Assistant is currently disabled. Enable it in Settings.', 'error');
            return;
        }
        sending = true;
        document.getElementById('aiSendBtn').disabled = true;
        appendBubble('user', text);
        appendTypingIndicator();
        scrollToBottom();

        try {
            const resp = await fetch('/api/ai/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ conversationId: currentConversationId, message: text })
            });
            document.getElementById('aiTypingIndicator')?.remove();

            if (resp.ok) {
                const data = await resp.json();
                currentConversationId = data.conversationId;
                appendBubble('assistant', data.message, data.debug);
                scrollToBottom();
                refreshConversationList();
            } else {
                const err = await safeJson(resp);
                appendErrorBubble((err && err.error) || 'Something went wrong. Please try again.', text);
            }
        } catch (e) {
            document.getElementById('aiTypingIndicator')?.remove();
            appendErrorBubble('Unable to reach the AI assistant right now.', text);
        } finally {
            sending = false;
            document.getElementById('aiSendBtn').disabled = !aiEnabled;
        }
    }

    // Refresh the conversation list (titles/order) without disrupting the currently-open thread.
    async function refreshConversationList() {
        try {
            conversations = await fetch('/api/ai/conversations').then(r => r.json());
            renderConvoList();
        } catch (e) { /* non-fatal */ }
    }

    async function confirmDeleteConversation() {
        if (deleteTargetId === null) return;
        try {
            const resp = await fetch(`/api/ai/conversations/${deleteTargetId}`, { method: 'DELETE' });
            deleteConvoModal.hide();
            if (resp.ok || resp.status === 204) {
                const wasCurrent = deleteTargetId === currentConversationId;
                deleteTargetId = null;
                await loadConversations();
                if (wasCurrent) startNewChat();
                showTopNotification('Conversation deleted.', 'success');
            } else {
                showTopNotification('Failed to delete conversation.', 'error');
            }
        } catch (e) {
            showTopNotification('Unable to delete conversation right now.', 'error');
        }
    }
})();
