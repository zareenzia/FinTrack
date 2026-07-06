/**
 * FinTrack Built-in Calculator
 * Floating popup — state and history persisted in localStorage.
 * Exposes window.toggleCalc / window.openCalc / window.closeCalc for sidebar.
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
        operator:        null,   // '+' | '-' | '*' | '/'
        operand1:        null,
        waitingForNext:  false,
        justCalced:      false,
        memory:          0,
        history:         [],
        isOpen:          false,
        isMinimized:     false,
        isPinned:        false,
        historyOpen:     false,
    };

    /* ── DOM helpers ───────────────────────────────────────────────────────── */
    const $  = (id) => document.getElementById(id);

    /* ════════════════════════════════════════════════════════════════════════
       INIT — called once the popup is in the DOM
    ════════════════════════════════════════════════════════════════════════ */
    function init() {
        const popup = $('calcPopup');
        if (!popup) return;

        // Restore persisted data
        loadHistory();
        state.memory = parseFloat(localStorage.getItem(MEMORY_KEY) || '0');

        // Header controls
        $('calcCloseBtn').addEventListener('click', closeCalc);
        $('calcMinBtn').addEventListener('click', toggleMinimize);
        $('calcPinBtn').addEventListener('click', togglePin);

        // Calculator buttons
        popup.querySelectorAll('.calc-btn').forEach(function (btn) {
            btn.addEventListener('click', handleButton);
        });

        // Backspace button inside display
        var delBtn = $('calcDelBtn');
        if (delBtn) delBtn.addEventListener('click', function () { backspace(); render(); });

        // Memory buttons
        popup.querySelectorAll('.calc-mem-btn').forEach(function (btn) {
            btn.addEventListener('click', handleMemory);
        });

        // History
        $('calcHistoryToggle').addEventListener('click', toggleHistory);
        $('calcHistoryClearBtn').addEventListener('click', clearHistory);

        // History toggle keyboard a11y
        $('calcHistoryToggle').addEventListener('keydown', function (e) {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); toggleHistory(); }
        });

        // Copy
        $('calcCopyBtn').addEventListener('click', copyResult);

        // Global keyboard handler
        document.addEventListener('keydown', handleKeyboard);

        // Drag
        initDrag();

        render();
        renderHistory();
    }

    /* ════════════════════════════════════════════════════════════════════════
       OPEN / CLOSE / MINIMIZE / PIN
    ════════════════════════════════════════════════════════════════════════ */
    function toggleCalc() {
        if (state.isOpen) closeCalc(); else openCalc();
    }

    function openCalc() {
        state.isOpen     = true;
        state.isMinimized = false;
        var p = $('calcPopup');
        p.classList.remove('calc-hidden', 'calc-minimized');
        p.classList.add('calc-entering');
        setTimeout(function () { p.classList.remove('calc-entering'); }, 300);
        render();
        renderHistory();
    }

    function closeCalc() {
        state.isOpen = false;
        $('calcPopup').classList.add('calc-hidden');
    }

    function toggleMinimize() {
        state.isMinimized = !state.isMinimized;
        $('calcPopup').classList.toggle('calc-minimized', state.isMinimized);
        var icon = $('calcMinBtn').querySelector('i');
        if (icon) icon.className = state.isMinimized ? 'fas fa-expand-alt' : 'fas fa-minus';
        $('calcMinBtn').title = state.isMinimized ? 'Restore' : 'Minimize';
    }

    function togglePin() {
        state.isPinned = !state.isPinned;
        $('calcPinBtn').classList.toggle('pinned-active', state.isPinned);
        $('calcPinBtn').title = state.isPinned ? 'Unpin' : 'Pin';
    }

    /* ════════════════════════════════════════════════════════════════════════
       RENDER
    ════════════════════════════════════════════════════════════════════════ */
    function render() {
        var resultEl = $('calcResult');
        var exprEl   = $('calcExpr');
        if (!resultEl) return;

        var displayStr = formatDisplay(state.display);
        resultEl.textContent = displayStr;

        resultEl.classList.remove('size-md', 'size-sm');
        if (displayStr.length > 14) resultEl.classList.add('size-sm');
        else if (displayStr.length > 9)  resultEl.classList.add('size-md');

        if (exprEl) exprEl.textContent = state.expression;

        // Memory indicator
        var memInd = $('calcMemIndicator');
        if (memInd) {
            memInd.textContent = state.memory !== 0
                ? 'M = ' + formatNumber(state.memory)
                : '';
        }

        // Highlight active memory recall button
        document.querySelectorAll('#calcPopup .calc-mem-btn').forEach(function (b) {
            b.classList.toggle('mem-active', b.dataset.mem === 'mr' && state.memory !== 0);
        });

        // Highlight active operator button
        var opMap = { divide: '/', multiply: '*', subtract: '-', add: '+' };
        document.querySelectorAll('#calcPopup .calc-btn-op').forEach(function (b) {
            b.classList.toggle('op-active',
                state.waitingForNext && opMap[b.dataset.action] === state.operator);
        });

        updateAcLabel();
    }

    function formatDisplay(val) {
        if (val === 'Error' || val === 'Overflow') return val;
        var trailingDot   = val.endsWith('.');
        var trailingZeros = /\.\d*0+$/.test(val) && !trailingDot;
        var n = parseFloat(val);
        if (isNaN(n)) return val;
        var base = formatNumber(n);
        if (trailingDot)   return base + '.';
        if (trailingZeros) {
            var decimals = val.split('.')[1] || '';
            return base.split('.')[0] + (decimals ? '.' + decimals : '');
        }
        return base;
    }

    function formatNumber(n) {
        if (!isFinite(n)) return 'Error';
        if (Math.abs(n) >= 1e15) return 'Overflow';
        var str   = n.toString();
        var parts = str.split('.');
        parts[0]  = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        return parts.join('.');
    }

    /* ════════════════════════════════════════════════════════════════════════
       BUTTON HANDLER
    ════════════════════════════════════════════════════════════════════════ */
    function handleButton(e) {
        var btn    = e.currentTarget;
        var action = btn.dataset.action;
        var val    = btn.dataset.val;

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
        if (state.display === 'Error' || state.display === 'Overflow') allClear();
        if (state.waitingForNext) {
            state.display        = digit === '0' ? '0' : digit;
            state.waitingForNext = false;
        } else {
            if (state.display === '0' && digit !== '.') {
                state.display = digit;
            } else {
                if (state.display.replace(/[^0-9]/g, '').length >= 15) return;
                state.display += digit;
            }
        }
        if (state.justCalced) { state.expression = ''; state.justCalced = false; }
    }

    function inputDecimal() {
        if (state.display === 'Error' || state.display === 'Overflow') allClear();
        if (state.waitingForNext) { state.display = '0.'; state.waitingForNext = false; return; }
        if (!state.display.includes('.')) state.display += '.';
        if (state.justCalced) { state.expression = ''; state.justCalced = false; }
    }

    function toggleSign() {
        if (state.display === '0' || state.display === 'Error') return;
        state.display = state.display.startsWith('-')
            ? state.display.slice(1) : '-' + state.display;
    }

    function percent() {
        var n = parseFloat(state.display);
        if (isNaN(n)) return;
        var result = (state.operand1 !== null && state.operator)
            ? state.operand1 * n / 100
            : n / 100;
        state.display = cleanNumber(result);
    }

    function setOperator(op) {
        if (state.display === 'Error' || state.display === 'Overflow') return;
        var current = parseFloat(state.display);

        if (state.operand1 !== null && !state.waitingForNext) {
            var result = compute(state.operand1, current, state.operator);
            if (result === null) { setError(); return; }
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
        var current = parseFloat(state.display);
        var expr    = formatNumber(state.operand1) + ' ' + opSymbol(state.operator) + ' ' + formatNumber(current);
        var result  = compute(state.operand1, current, state.operator);

        if (result === null) {
            state.expression = expr + ' = Error';
            setError();
            return;
        }
        state.expression = expr + ' =';
        state.display    = cleanNumber(result);

        pushHistory({ expr: expr, result: formatNumber(result), timestamp: new Date().toISOString() });
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
            case '/': return b === 0 ? null : roundResult(a / b);
        }
        return null;
    }

    /** Prevent floating-point drift (e.g. 0.1 + 0.2 = 0.30000000004) */
    function roundResult(n) { return parseFloat(n.toPrecision(12)); }

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
        if (state.display === 'Error' || state.display === 'Overflow') { allClear(); return; }
        if (state.waitingForNext) {
            state.waitingForNext = false;
            state.operator       = null;
            state.expression     = '';
            return;
        }
        var negOne = state.display.length === 2 && state.display.startsWith('-');
        state.display = (state.display.length <= 1 || negOne) ? '0' : state.display.slice(0, -1);
    }

    function updateAcLabel() {
        var acBtn = document.querySelector('#calcPopup .calc-btn[data-action="ac"]');
        if (acBtn) acBtn.textContent = (state.display !== '0' || state.expression) ? 'C' : 'AC';
    }

    function opSymbol(op) {
        return { '+': '+', '-': '−', '*': '×', '/': '÷' }[op] || op;
    }

    /* ════════════════════════════════════════════════════════════════════════
       MEMORY
    ════════════════════════════════════════════════════════════════════════ */
    function handleMemory(e) {
        var op      = e.currentTarget.dataset.mem;
        var current = parseFloat(state.display) || 0;

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
        try { state.history = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]'); }
        catch (_) { state.history = []; }
    }

    function pushHistory(entry) {
        state.history.unshift(entry);
        if (state.history.length > MAX_HISTORY) state.history.pop();
        localStorage.setItem(HISTORY_KEY, JSON.stringify(state.history));
    }

    function renderHistory() {
        var list = $('calcHistoryList');
        if (!list) return;

        if (!state.history.length) {
            list.innerHTML = '<li class="calc-history-empty">No calculations yet</li>';
            return;
        }

        list.innerHTML = state.history.map(function (h, i) {
            return '<li class="calc-history-item" data-idx="' + i + '" title="Click to reuse this result">' +
                '<div class="calc-history-expr">' + escHtml(h.expr) + '</div>' +
                '<div class="calc-history-result">= ' + escHtml(h.result) + '</div>' +
                '<div class="calc-history-time">' + formatTime(h.timestamp) + '</div>' +
                '</li>';
        }).join('');

        list.querySelectorAll('.calc-history-item').forEach(function (item) {
            item.addEventListener('click', function () {
                var entry = state.history[parseInt(item.dataset.idx)];
                if (!entry) return;
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
        state.history = [];
        localStorage.removeItem(HISTORY_KEY);
        renderHistory();
    }

    function toggleHistory() {
        state.historyOpen = !state.historyOpen;
        $('calcHistoryPanel').classList.toggle('hist-open', state.historyOpen);
        $('calcHistoryToggle').classList.toggle('hist-open', state.historyOpen);
        $('calcHistoryToggle').setAttribute('aria-expanded', String(state.historyOpen));
    }

    function formatTime(iso) {
        try { return new Date(iso).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }); }
        catch (_) { return ''; }
    }

    function escHtml(s) {
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    /* ════════════════════════════════════════════════════════════════════════
       COPY TO CLIPBOARD
    ════════════════════════════════════════════════════════════════════════ */
    function copyResult() {
        var raw = state.display.replace(/,/g, '');
        var btn = $('calcCopyBtn');

        function feedback() {
            if (!btn) return;
            var orig = btn.innerHTML;
            btn.innerHTML = '<i class="fas fa-check"></i> Copied!';
            setTimeout(function () { btn.innerHTML = orig; }, 1500);
        }

        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(raw).then(feedback).catch(function () { fallbackCopy(raw, feedback); });
        } else {
            fallbackCopy(raw, feedback);
        }
    }

    function fallbackCopy(text, cb) {
        var ta = document.createElement('textarea');
        ta.value = text;
        ta.style.cssText = 'position:fixed;top:-9999px;left:-9999px';
        document.body.appendChild(ta);
        ta.focus(); ta.select();
        try { document.execCommand('copy'); cb(); } catch (_) {}
        document.body.removeChild(ta);
    }

    /* ════════════════════════════════════════════════════════════════════════
       KEYBOARD
    ════════════════════════════════════════════════════════════════════════ */
    function handleKeyboard(e) {
        if (!state.isOpen || state.isMinimized) return;
        var tag = document.activeElement ? document.activeElement.tagName : '';
        if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;
        if (document.querySelector('.modal.show')) return;

        var k = e.key;
        if (k >= '0' && k <= '9') { inputNum(k); render(); return; }

        switch (k) {
            case '+':        setOperator('+'); render(); break;
            case '-':        setOperator('-'); render(); break;
            case '*':        setOperator('*'); render(); break;
            case '/':        e.preventDefault(); setOperator('/'); render(); break;
            case '%':        percent();         render(); break;
            case '.': case ',': inputDecimal(); render(); break;
            case 'Enter': case '=': calculate();  render(); break;
            case 'Backspace':    backspace();     render(); break;
            case 'Escape': case 'Delete': allClear(); render(); break;
        }
    }

    /* ════════════════════════════════════════════════════════════════════════
       DRAG
    ════════════════════════════════════════════════════════════════════════ */
    function initDrag() {
        var header = $('calcHeader');
        if (!header) return;

        var dragging = false, sx, sy, sl, st;

        function getClient(e) {
            return e.touches
                ? { x: e.touches[0].clientX, y: e.touches[0].clientY }
                : { x: e.clientX, y: e.clientY };
        }

        function onDown(e) {
            if (e.target.closest('.calc-ctrl-btn')) return;
            if (window.innerWidth <= 480) return; // mobile sheet, no drag
            var p = $('calcPopup');
            var r = p.getBoundingClientRect();
            var c = getClient(e);
            dragging = true; sx = c.x; sy = c.y; sl = r.left; st = r.top;
            p.style.transition = 'none';
            e.preventDefault();
        }

        function onMove(e) {
            if (!dragging) return;
            var c  = getClient(e);
            var p  = $('calcPopup');
            var pw = p.offsetWidth, ph = p.offsetHeight;
            var nl = Math.max(0, Math.min(sl + c.x - sx, window.innerWidth  - pw));
            var nt = Math.max(0, Math.min(st + c.y - sy, window.innerHeight - ph));
            p.style.left   = nl + 'px';
            p.style.top    = nt + 'px';
            p.style.right  = 'auto';
            p.style.bottom = 'auto';
        }

        function onUp() {
            if (!dragging) return;
            dragging = false;
            $('calcPopup').style.transition = '';
        }

        header.addEventListener('mousedown',  onDown);
        header.addEventListener('touchstart', onDown, { passive: false });
        document.addEventListener('mousemove',  onMove);
        document.addEventListener('touchmove',  onMove, { passive: false });
        document.addEventListener('mouseup',   onUp);
        document.addEventListener('touchend',  onUp);
    }

    /* ════════════════════════════════════════════════════════════════════════
       PUBLIC API — exposed on window for sidebar.js to call
    ════════════════════════════════════════════════════════════════════════ */
    window.toggleCalc = toggleCalc;
    window.openCalc   = openCalc;
    window.closeCalc  = closeCalc;

    /* Init runs after the popup HTML exists in the DOM */
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
