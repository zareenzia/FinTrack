/**
 * FinTrack Built-in Calculator
 * Floating popup with full arithmetic, memory, history, drag, and theme support.
 * Persists history and memory via localStorage.
 * Integrates with the FinTrack theme system (light / dark / blue / green).
 */
(function () {
    'use strict';

    /* ── Constants ─────────────────────────────────────────────────────────── */
    const HISTORY_KEY = 'fintrack-calc-history';
    const MEMORY_KEY  = 'fintrack-calc-memory';
    const MAX_HISTORY = 15;

    /* ── State ─────────────────────────────────────────────────────────────── */
    const state = {
        display:         '0',
        expression:      '',
        operator:        null,      // '+' | '-' | '*' | '/'
        operand1:        null,
        waitingForNext:  false,     // true after an operator key
        justCalced:      false,     // true right after pressing =
        memory:          0,
        history:         [],
        isOpen:          false,
        isMinimized:     false,
        isPinned:        false,
        historyOpen:     false,
    };

    /* ── DOM helpers ───────────────────────────────────────────────────────── */
    const $  = (id) => document.getElementById(id);
    const el = {
        popup:         () => $('calcPopup'),
        expr:          () => $('calcExpr'),
        result:        () => $('calcResult'),
        memInd:        () => $('calcMemIndicator'),
        histList:      () => $('calcHistoryList'),
        histPanel:     () => $('calcHistoryPanel'),
        histToggle:    () => $('calcHistoryToggle'),
        histClearBtn:  () => $('calcHistoryClearBtn'),
        copyBtn:       () => $('calcCopyBtn'),
        pinBtn:        () => $('calcPinBtn'),
        minBtn:        () => $('calcMinBtn'),
        closeBtn:      () => $('calcCloseBtn'),
        fabBtn:        () => $('calcFabBtn'),
        delBtn:        () => $('calcDelBtn'),
    };

    /* ════════════════════════════════════════════════════════════════════════
       INIT
    ════════════════════════════════════════════════════════════════════════ */
    function init() {
        if (!el.popup()) return; // not logged in

        // Load persisted data
        loadHistory();
        state.memory = parseFloat(localStorage.getItem(MEMORY_KEY) || '0');

        // FAB / open trigger
        const fab = el.fabBtn();
        if (fab) fab.addEventListener('click', toggleOpen);

        // Header controls
        el.closeBtn().addEventListener('click', closeCalc);
        el.minBtn().addEventListener('click', toggleMinimize);
        el.pinBtn().addEventListener('click', togglePin);

        // Calculator buttons
        document.querySelectorAll('#calcPopup .calc-btn').forEach(btn => {
            btn.addEventListener('click', handleButton);
        });

        // Backspace (in display)
        if (el.delBtn()) el.delBtn().addEventListener('click', () => { backspace(); render(); });

        // Memory buttons
        document.querySelectorAll('#calcPopup .calc-mem-btn').forEach(btn => {
            btn.addEventListener('click', handleMemory);
        });

        // History
        el.histToggle().addEventListener('click', toggleHistory);
        el.histClearBtn().addEventListener('click', clearHistory);

        // Copy
        el.copyBtn().addEventListener('click', copyResult);

        // Keyboard
        document.addEventListener('keydown', handleKeyboard);

        // Drag support
        initDrag();

        // React to theme changes made on the settings page (other tab or same tab)
        window.addEventListener('storage', (e) => {
            if (e.key === 'fintrack-theme') applyTheme();
        });

        // Also patch into the applyTheme / applyThemeStyles functions if they exist
        // so the calculator updates immediately when the user changes theme on settings
        const origApply = window.applyTheme;
        if (typeof origApply === 'function') {
            window.applyTheme = function (theme) {
                origApply.call(this, theme);
                applyThemeToCalc();
            };
        }

        applyTheme();
        render();
        renderHistory();
    }

    /* ════════════════════════════════════════════════════════════════════════
       OPEN / CLOSE / MINIMIZE / PIN
    ════════════════════════════════════════════════════════════════════════ */
    function toggleOpen() {
        if (state.isOpen) closeCalc(); else openCalc();
    }

    function openCalc() {
        state.isOpen = true;
        state.isMinimized = false;
        const p = el.popup();
        p.classList.remove('calc-hidden', 'calc-minimized');
        p.classList.add('calc-entering');
        setTimeout(() => p.classList.remove('calc-entering'), 300);
        applyTheme();
        render();
        renderHistory();
    }

    function closeCalc() {
        state.isOpen = false;
        el.popup().classList.add('calc-hidden');
    }

    function toggleMinimize() {
        state.isMinimized = !state.isMinimized;
        el.popup().classList.toggle('calc-minimized', state.isMinimized);
        const icon = el.minBtn().querySelector('i');
        if (icon) icon.className = state.isMinimized ? 'fas fa-expand-alt' : 'fas fa-minus';
        el.minBtn().title = state.isMinimized ? 'Restore' : 'Minimize';
    }

    function togglePin() {
        state.isPinned = !state.isPinned;
        el.popup().classList.toggle('calc-pinned', state.isPinned);
        el.pinBtn().classList.toggle('pinned-active', state.isPinned);
        el.pinBtn().title = state.isPinned ? 'Unpin' : 'Pin';
    }

    /* ════════════════════════════════════════════════════════════════════════
       THEME
    ════════════════════════════════════════════════════════════════════════ */
    function applyTheme() {
        applyThemeToCalc();
    }

    function applyThemeToCalc() {
        const theme = localStorage.getItem('fintrack-theme') || 'light';
        const p = el.popup();
        if (p) p.setAttribute('data-calc-theme', theme);
    }

    /* ════════════════════════════════════════════════════════════════════════
       RENDER
    ════════════════════════════════════════════════════════════════════════ */
    function render() {
        const resultEl = el.result();
        if (!resultEl) return;

        const displayStr = formatDisplay(state.display);
        resultEl.textContent = displayStr;

        // Adjust font size for long numbers
        resultEl.classList.remove('size-md', 'size-sm');
        if (displayStr.length > 14) resultEl.classList.add('size-sm');
        else if (displayStr.length > 9) resultEl.classList.add('size-md');

        // Expression line
        if (el.expr()) el.expr().textContent = state.expression;

        // Memory indicator
        const memInd = el.memInd();
        if (memInd) {
            memInd.textContent = state.memory !== 0
                ? 'M = ' + formatNumber(state.memory)
                : '';
        }

        // Memory button highlight
        document.querySelectorAll('#calcPopup .calc-mem-btn').forEach(b => {
            b.classList.toggle('mem-active', b.dataset.mem === 'mr' && state.memory !== 0);
        });

        // Highlight active operator button
        document.querySelectorAll('#calcPopup .calc-btn-op').forEach(b => {
            const opMap = { divide: '/', multiply: '*', subtract: '-', add: '+' };
            b.classList.toggle('op-active',
                state.waitingForNext && opMap[b.dataset.action] === state.operator);
        });

        // AC vs C label
        updateAcLabel();
    }

    function formatDisplay(val) {
        if (val === 'Error' || val === 'Overflow') return val;
        // Preserve trailing decimal or trailing zeros while user is typing
        const trailingDot = val.endsWith('.');
        const trailingZeros = /\.\d*0+$/.test(val) && !trailingDot;
        const n = parseFloat(val);
        if (isNaN(n)) return val;
        const base = formatNumber(n);
        if (trailingDot) return base + '.';
        if (trailingZeros) {
            const decimals = val.split('.')[1] || '';
            return base.split('.')[0] + (decimals ? '.' + decimals : '');
        }
        return base;
    }

    function formatNumber(n) {
        if (!isFinite(n)) return 'Error';
        if (Math.abs(n) >= 1e15) return 'Overflow';
        // Split integer and decimal parts, add thousands separators to integer part
        const str   = n.toString();
        const parts = str.split('.');
        parts[0]    = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        return parts.join('.');
    }

    /* ════════════════════════════════════════════════════════════════════════
       BUTTON HANDLER
    ════════════════════════════════════════════════════════════════════════ */
    function handleButton(e) {
        const btn    = e.currentTarget;
        const action = btn.dataset.action;
        const val    = btn.dataset.val;

        switch (action) {
            case 'num':         inputNum(val);      break;
            case 'decimal':     inputDecimal();     break;
            case 'ac':          clearOrAllClear();  break;
            case 'toggle-sign': toggleSign();       break;
            case 'percent':     percent();          break;
            case 'add':         setOperator('+');   break;
            case 'subtract':    setOperator('-');   break;
            case 'multiply':    setOperator('*');   break;
            case 'divide':      setOperator('/');   break;
            case 'equals':      calculate();        break;
        }
        render();
    }

    /* ════════════════════════════════════════════════════════════════════════
       CORE CALCULATION LOGIC
    ════════════════════════════════════════════════════════════════════════ */
    function inputNum(digit) {
        if (state.display === 'Error' || state.display === 'Overflow') {
            allClear();
        }
        if (state.waitingForNext) {
            state.display       = digit === '0' ? '0' : digit;
            state.waitingForNext = false;
        } else {
            // Don't append leading zeros
            if (state.display === '0' && digit !== '.') {
                state.display = digit;
            } else {
                // Limit to 15 significant digits
                const raw = state.display.replace(/[^0-9]/g, '');
                if (raw.length >= 15) return;
                state.display += digit;
            }
        }
        if (state.justCalced) {
            state.expression = '';
            state.justCalced = false;
        }
    }

    function inputDecimal() {
        if (state.display === 'Error' || state.display === 'Overflow') {
            allClear();
        }
        if (state.waitingForNext) {
            state.display        = '0.';
            state.waitingForNext = false;
            return;
        }
        if (!state.display.includes('.')) {
            state.display += '.';
        }
        if (state.justCalced) {
            state.expression = '';
            state.justCalced = false;
        }
    }

    function toggleSign() {
        if (state.display === '0' || state.display === 'Error') return;
        state.display = state.display.startsWith('-')
            ? state.display.slice(1)
            : '-' + state.display;
    }

    function percent() {
        const n = parseFloat(state.display);
        if (isNaN(n)) return;
        // If chaining, calculate percentage relative to first operand
        const result = (state.operand1 !== null && state.operator)
            ? state.operand1 * n / 100
            : n / 100;
        state.display = cleanNumber(result);
    }

    function setOperator(op) {
        if (state.display === 'Error' || state.display === 'Overflow') return;
        const current = parseFloat(state.display);

        if (state.operand1 !== null && !state.waitingForNext) {
            // Chain calculation
            const result = compute(state.operand1, current, state.operator);
            if (result === null) {
                setError();
                return;
            }
            state.display    = cleanNumber(result);
            state.operand1   = result;
            state.expression = formatNumber(result) + ' ' + opSymbol(op);
        } else {
            state.operand1   = current;
            state.expression = formatNumber(current) + ' ' + opSymbol(op);
        }

        state.operator       = op;
        state.waitingForNext = true;
        state.justCalced     = false;
    }

    function calculate() {
        if (state.operand1 === null || state.operator === null) return;
        const current = parseFloat(state.display);
        const expr    = formatNumber(state.operand1) + ' ' + opSymbol(state.operator) + ' ' + formatNumber(current);
        const result  = compute(state.operand1, current, state.operator);

        if (result === null) {
            state.expression = expr + ' = Error';
            setError();
            return;
        }

        state.expression = expr + ' =';
        state.display    = cleanNumber(result);

        // Persist to history
        pushHistory({
            expr:      expr,
            result:    formatNumber(result),
            timestamp: new Date().toISOString(),
        });
        renderHistory();

        state.operand1       = null;
        state.operator       = null;
        state.waitingForNext = false;
        state.justCalced     = true;
    }

    function compute(a, b, op) {
        switch (op) {
            case '+': return roundResult(a + b);
            case '-': return roundResult(a - b);
            case '*': return roundResult(a * b);
            case '/':
                if (b === 0) return null; // division by zero
                return roundResult(a / b);
        }
        return null;
    }

    /** Avoid floating-point artifacts (e.g. 0.1 + 0.2 = 0.30000000004) */
    function roundResult(n) {
        return parseFloat(n.toPrecision(12));
    }

    function cleanNumber(n) {
        if (!isFinite(n)) return 'Error';
        if (Math.abs(n) >= 1e15) return 'Overflow';
        return String(n);
    }

    function setError() {
        state.display        = 'Error';
        state.operand1       = null;
        state.operator       = null;
        state.waitingForNext = false;
    }

    function clearOrAllClear() {
        // Behave as C when there's current input, AC when fully clear
        if (state.display !== '0') {
            state.display        = '0';
            state.waitingForNext = false;
        } else {
            allClear();
        }
    }

    function allClear() {
        state.display        = '0';
        state.expression     = '';
        state.operator       = null;
        state.operand1       = null;
        state.waitingForNext = false;
        state.justCalced     = false;
    }

    function backspace() {
        if (state.display === 'Error' || state.display === 'Overflow') {
            allClear();
            return;
        }
        if (state.waitingForNext) {
            // Cancel the pending operator
            state.waitingForNext = false;
            state.operator       = null;
            state.expression     = '';
            return;
        }
        const isNegativeWithOneDigit = state.display.length === 2 && state.display.startsWith('-');
        if (state.display.length <= 1 || isNegativeWithOneDigit) {
            state.display = '0';
        } else {
            state.display = state.display.slice(0, -1);
        }
    }

    function updateAcLabel() {
        const acBtn = document.querySelector('#calcPopup .calc-btn[data-action="ac"]');
        if (!acBtn) return;
        acBtn.textContent = (state.display !== '0' || state.expression) ? 'C' : 'AC';
    }

    function opSymbol(op) {
        return { '+': '+', '-': '−', '*': '×', '/': '÷' }[op] || op;
    }

    /* ════════════════════════════════════════════════════════════════════════
       MEMORY FUNCTIONS
    ════════════════════════════════════════════════════════════════════════ */
    function handleMemory(e) {
        const op      = e.currentTarget.dataset.mem;
        const current = parseFloat(state.display) || 0;

        switch (op) {
            case 'mc':
                state.memory = 0;
                localStorage.removeItem(MEMORY_KEY);
                break;
            case 'mr':
                if (state.memory !== 0) {
                    state.display        = String(state.memory);
                    state.waitingForNext = false;
                    state.justCalced     = false;
                }
                break;
            case 'mplus':
                state.memory = roundResult(state.memory + current);
                localStorage.setItem(MEMORY_KEY, state.memory);
                break;
            case 'mminus':
                state.memory = roundResult(state.memory - current);
                localStorage.setItem(MEMORY_KEY, state.memory);
                break;
        }
        render();
    }

    /* ════════════════════════════════════════════════════════════════════════
       HISTORY
    ════════════════════════════════════════════════════════════════════════ */
    function loadHistory() {
        try {
            state.history = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
        } catch (_) {
            state.history = [];
        }
    }

    function pushHistory(entry) {
        state.history.unshift(entry);
        if (state.history.length > MAX_HISTORY) state.history.pop();
        localStorage.setItem(HISTORY_KEY, JSON.stringify(state.history));
    }

    function renderHistory() {
        const list = el.histList();
        if (!list) return;

        if (!state.history.length) {
            list.innerHTML = '<li class="calc-history-empty">No calculations yet</li>';
            return;
        }

        list.innerHTML = state.history.map((h, i) => `
            <li class="calc-history-item" data-idx="${i}" title="Click to reuse this result">
                <div class="calc-history-expr">${escHtml(h.expr)}</div>
                <div class="calc-history-result">= ${escHtml(h.result)}</div>
                <div class="calc-history-time">${formatTime(h.timestamp)}</div>
            </li>
        `).join('');

        list.querySelectorAll('.calc-history-item').forEach(item => {
            item.addEventListener('click', () => {
                const entry = state.history[parseInt(item.dataset.idx)];
                if (!entry) return;
                // Strip thousands separators before storing as display
                state.display        = entry.result.replace(/,/g, '');
                state.expression     = '';
                state.operator       = null;
                state.operand1       = null;
                state.waitingForNext = false;
                state.justCalced     = true;
                render();
            });
        });
    }

    function clearHistory() {
        if (!confirm('Clear all calculation history?')) return;
        state.history = [];
        localStorage.removeItem(HISTORY_KEY);
        renderHistory();
    }

    function toggleHistory() {
        state.historyOpen = !state.historyOpen;
        el.histPanel().classList.toggle('hist-open', state.historyOpen);
        el.histToggle().classList.toggle('hist-open', state.historyOpen);
    }

    function formatTime(iso) {
        try {
            return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' });
        } catch (_) { return ''; }
    }

    function escHtml(s) {
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    /* ════════════════════════════════════════════════════════════════════════
       COPY TO CLIPBOARD
    ════════════════════════════════════════════════════════════════════════ */
    function copyResult() {
        const raw = state.display.replace(/,/g, '');
        const btn = el.copyBtn();

        const doFeedback = () => {
            if (!btn) return;
            const orig = btn.innerHTML;
            btn.innerHTML = '<i class="fas fa-check"></i> Copied!';
            setTimeout(() => { btn.innerHTML = orig; }, 1500);
        };

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(raw).then(doFeedback).catch(() => fallbackCopy(raw, doFeedback));
        } else {
            fallbackCopy(raw, doFeedback);
        }
    }

    function fallbackCopy(text, cb) {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;top:-9999px;left:-9999px';
        document.body.appendChild(ta);
        ta.focus();
        ta.select();
        try { document.execCommand('copy'); cb(); } catch (_) {}
        document.body.removeChild(ta);
    }

    /* ════════════════════════════════════════════════════════════════════════
       KEYBOARD SUPPORT
    ════════════════════════════════════════════════════════════════════════ */
    function handleKeyboard(e) {
        if (!state.isOpen || state.isMinimized) return;

        // Don't intercept when a text input or Bootstrap modal is focused
        const tag = document.activeElement ? document.activeElement.tagName : '';
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
        if (document.querySelector('.modal.show')) return;

        const k = e.key;

        if (k >= '0' && k <= '9')       { inputNum(k);       render(); return; }

        switch (k) {
            case '+':        setOperator('+');  render(); break;
            case '-':        setOperator('-');  render(); break;
            case '*':        setOperator('*');  render(); break;
            case '/':        e.preventDefault(); setOperator('/'); render(); break;
            case '%':        percent();         render(); break;
            case '.':
            case ',':        inputDecimal();    render(); break;
            case 'Enter':
            case '=':        calculate();       render(); break;
            case 'Backspace':backspace();        render(); break;
            case 'Escape':   allClear();         render(); break;
            case 'Delete':   allClear();         render(); break;
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       DRAG SUPPORT
    ════════════════════════════════════════════════════════════════════════ */
    function initDrag() {
        const header = $('calcHeader');
        if (!header) return;

        let isDragging = false;
        let startClientX, startClientY, startLeft, startTop;

        function getClient(e) {
            return e.touches
                ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
                : { x: e.clientX,            y: e.clientY            };
        }

        function onDown(e) {
            if (e.target.closest('.calc-ctrl-btn')) return;
            // Don't drag on mobile bottom sheet
            if (window.innerWidth <= 480) return;

            const p    = el.popup();
            const rect = p.getBoundingClientRect();
            const { x, y } = getClient(e);

            isDragging  = true;
            startClientX = x;
            startClientY = y;
            startLeft    = rect.left;
            startTop     = rect.top;

            p.style.transition = 'none';
            e.preventDefault();
        }

        function onMove(e) {
            if (!isDragging) return;
            const { x, y } = getClient(e);
            const p  = el.popup();
            const pw = p.offsetWidth;
            const ph = p.offsetHeight;
            const ww = window.innerWidth;
            const wh = window.innerHeight;

            const newLeft = Math.max(0, Math.min(startLeft + x - startClientX, ww - pw));
            const newTop  = Math.max(0, Math.min(startTop  + y - startClientY, wh - ph));

            p.style.left   = newLeft + 'px';
            p.style.top    = newTop  + 'px';
            p.style.right  = 'auto';
            p.style.bottom = 'auto';
        }

        function onUp() {
            if (!isDragging) return;
            isDragging = false;
            el.popup().style.transition = '';
        }

        header.addEventListener('mousedown',  onDown);
        header.addEventListener('touchstart', onDown, { passive: false });
        document.addEventListener('mousemove',  onMove);
        document.addEventListener('touchmove',  onMove, { passive: false });
        document.addEventListener('mouseup',   onUp);
        document.addEventListener('touchend',  onUp);
    }

    /* ── Kick off after DOM is ready ───────────────────────────────────────── */
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
