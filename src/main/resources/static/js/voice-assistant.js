(function () {
    'use strict';

    var SPEECH_STATE = { IDLE: 'idle', LISTENING: 'listening', PROCESSING: 'processing', ERROR: 'error' };

    var NOTE_COLORS = ['#FFFFFF', '#F3F4F6', '#DBEAFE', '#BFDBFE', '#D1FAE5', '#DCFCE7',
        '#FEF3C7', '#FED7AA', '#FBCFE8', '#E9D5FF', '#DDD6FE', '#FDE68A'];

    var state = {
        speechState: SPEECH_STATE.IDLE,
        recognition: null,
        mediaStream: null,
        audioContext: null,
        analyser: null,
        waveformRAF: null,
        timerInterval: null,
        startTime: null,
        pausedElapsedMs: 0,
        finalTranscript: '',
        interimTranscript: '',
        priorState: null,
        historyId: null,
        silenceTimer: null,
        maxLengthTimer: null,
        userPaused: false,
        settings: { language: 'en-US', autoStopSilenceSeconds: 3, maxRecordingLengthSeconds: 60, enabled: true }
    };

    /* ── Small helpers ──────────────────────────────────────────── */

    function escHtml(s) {
        return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function todayIso() {
        var d = new Date();
        var mm = String(d.getMonth() + 1).padStart(2, '0');
        var dd = String(d.getDate()).padStart(2, '0');
        return d.getFullYear() + '-' + mm + '-' + dd;
    }

    function capitalize(s) {
        return s.charAt(0).toUpperCase() + s.slice(1);
    }

    function fieldRow(id, label, inputHtml) {
        return '<div class="va-field-row"><label for="' + id + '">' + escHtml(label) + '</label>' + inputHtml + '</div>';
    }

    function heuristicBanner() {
        return '<div class="va-heuristic-banner"><i class="fas fa-triangle-exclamation me-1"></i>' +
            'Parsed without AI assistance — please double-check the details below.</div>';
    }

    function confirmFooterButtons(saveLabel) {
        return '<div class="d-flex justify-content-end gap-2 mt-3">' +
            '<button type="button" class="btn btn-outline-secondary" id="vaConfirmCancelBtn">Cancel</button>' +
            '<button type="button" class="btn btn-primary" id="vaConfirmSaveBtn"><i class="fas fa-check me-1"></i>' +
            escHtml(saveLabel || 'Save') + '</button>' +
            '</div>';
    }

    /* ── Screen switching / status ──────────────────────────────── */

    function switchScreen(name) {
        ['recording', 'confirm', 'success'].forEach(function (n) {
            var el = document.getElementById('vaScreen' + capitalize(n));
            if (el) el.classList.toggle('active', n === name);
        });
    }

    function setStatus(text, isError) {
        var el = document.getElementById('vaStatus');
        if (!el) return;
        el.textContent = text;
        el.classList.toggle('va-error-text', !!isError);
    }

    function setMicVisualState(mode) {
        var wrap = document.querySelector('.va-mic-wrap');
        if (!wrap) return;
        wrap.classList.toggle('listening', mode === 'listening');
        wrap.classList.toggle('error', mode === 'error');
    }

    function showControlButtons(opts) {
        var map = { pause: 'vaPauseBtn', resume: 'vaResumeBtn', stop: 'vaStopBtn', cancel: 'vaCancelBtn' };
        Object.keys(map).forEach(function (key) {
            var el = document.getElementById(map[key]);
            if (el) el.classList.toggle('d-none', !opts[key]);
        });
    }

    /* ── Voice settings (language, silence timeout, max length) ───── */

    function loadVoiceSettingsOnce() {
        return fetch('/api/voice/settings')
            .then(function (r) { return r.json(); })
            .then(function (s) { state.settings = s; return s; })
            .catch(function () { return state.settings; });
    }

    /* ── Open / close / reset ──────────────────────────────────── */

    function openVoiceAssistant() {
        loadVoiceSettingsOnce().then(function (settings) {
            var modalEl = document.getElementById('voiceAssistantModal');
            if (!modalEl) return;
            if (settings.enabled === false) {
                resetToRecordingScreen();
                setMicVisualState('error');
                setStatus('Voice Assistant is disabled. Enable it in Settings to use voice commands.', true);
                bootstrap.Modal.getOrCreateInstance(modalEl).show();
                return;
            }
            resetToRecordingScreen();
            bootstrap.Modal.getOrCreateInstance(modalEl).show();
        });
    }
    window.openVoiceAssistant = openVoiceAssistant;

    function closeVoiceAssistant() {
        stopEverything();
        var modalEl = document.getElementById('voiceAssistantModal');
        var modal = modalEl && bootstrap.Modal.getInstance(modalEl);
        if (modal) modal.hide();
    }

    function resetToRecordingScreen() {
        stopEverything();
        state.finalTranscript = '';
        state.interimTranscript = '';
        state.priorState = null;
        state.historyId = null;
        state.pausedElapsedMs = 0;
        state.speechState = SPEECH_STATE.IDLE;
        switchScreen('recording');
        var transcriptEl = document.getElementById('vaTranscript');
        if (transcriptEl) transcriptEl.textContent = '';
        var followup = document.getElementById('vaFollowupBanner');
        if (followup) followup.classList.add('d-none');
        var timerEl = document.getElementById('vaTimer');
        if (timerEl) timerEl.textContent = '00:00';
        setMicVisualState('idle');
        setStatus('Tap the mic to start');
        showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
    }

    /* ── Recording lifecycle ────────────────────────────────────── */

    function startRecording() {
        var SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SpeechRecognitionCtor) {
            setMicVisualState('error');
            setStatus('Voice input isn\'t supported in this browser. Try Chrome or Edge.', true);
            showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
            return;
        }
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
            state.mediaStream = stream;
            startWaveform(stream);
            startTimer();
            startSpeechRecognition(SpeechRecognitionCtor);
            setMicVisualState('listening');
            setStatus('Listening…');
            showControlButtons({ pause: true, resume: false, stop: true, cancel: true });
        }).catch(function (err) {
            setMicVisualState('error');
            if (err && err.name === 'NotAllowedError') {
                setStatus('Microphone access was denied. Please allow microphone access in your browser settings and try again.', true);
            } else if (err && err.name === 'NotFoundError') {
                setStatus('No microphone was found on this device.', true);
            } else {
                setStatus('Could not access the microphone. Please try again.', true);
            }
            showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
        });
    }

    function startSpeechRecognition(Ctor) {
        var recognition = new Ctor();
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.lang = state.settings.language || 'en-US';

        recognition.onresult = function (event) {
            var interim = '';
            for (var i = event.resultIndex; i < event.results.length; i++) {
                var piece = event.results[i][0].transcript;
                if (event.results[i].isFinal) {
                    state.finalTranscript = (state.finalTranscript + ' ' + piece).trim();
                } else {
                    interim += piece;
                }
            }
            // The speech engine only marks a segment "final" after a pause — if the user stops
            // talking right as the silence timer fires (the common case), the last few words can
            // still be sitting here as unfinalized interim text. Keep it so finishListeningAndSubmit
            // can fall back to it instead of silently discarding whatever was on screen.
            state.interimTranscript = interim;
            var transcriptEl = document.getElementById('vaTranscript');
            if (transcriptEl) transcriptEl.textContent = (state.finalTranscript + ' ' + interim).trim();
            resetSilenceTimer();
        };

        recognition.onerror = function (event) {
            if (event.error === 'no-speech') return; // benign — the silence timer decides, not a hard error
            if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
                handleRecognitionError('Microphone access was denied. Please allow microphone access and try again.');
            } else if (event.error === 'network') {
                handleRecognitionError('A network issue interrupted listening. Please try again.');
            } else if (event.error === 'audio-capture') {
                handleRecognitionError('No microphone was found on this device.');
            } else {
                handleRecognitionError('Something went wrong while listening. Please try again.');
            }
        };

        recognition.onend = function () {
            if (state.userPaused || state.speechState !== SPEECH_STATE.LISTENING) return;
            // Recognizer stopped on its own (browser-side timeout) — finalize whatever we have.
            finishListeningAndSubmit();
        };

        recognition.start();
        state.recognition = recognition;
        state.speechState = SPEECH_STATE.LISTENING;
        resetSilenceTimer();

        clearTimeout(state.maxLengthTimer);
        state.maxLengthTimer = setTimeout(function () {
            if (state.speechState === SPEECH_STATE.LISTENING) finishListeningAndSubmit();
        }, (state.settings.maxRecordingLengthSeconds || 60) * 1000);
    }

    function resetSilenceTimer() {
        clearTimeout(state.silenceTimer);
        var seconds = state.settings.autoStopSilenceSeconds || 3;
        state.silenceTimer = setTimeout(function () {
            if (state.speechState === SPEECH_STATE.LISTENING) finishListeningAndSubmit();
        }, seconds * 1000);
    }

    function handleRecognitionError(message) {
        stopEverything();
        setMicVisualState('error');
        setStatus(message, true);
        showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
    }

    function pauseRecording() {
        state.userPaused = true;
        if (state.recognition) {
            state.recognition.onend = null;
            try { state.recognition.stop(); } catch (e) { /* already stopped */ }
            state.recognition = null;
        }
        stopWaveform();
        stopTimer();
        clearTimeout(state.silenceTimer);
        clearTimeout(state.maxLengthTimer);
        if (state.mediaStream) {
            state.mediaStream.getTracks().forEach(function (t) { t.stop(); });
            state.mediaStream = null;
        }
        setStatus('Paused');
        showControlButtons({ pause: false, resume: true, stop: true, cancel: true });
    }

    function resumeRecording() {
        state.userPaused = false;
        var SpeechRecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
            state.mediaStream = stream;
            startWaveform(stream);
            startTimer();
            startSpeechRecognition(SpeechRecognitionCtor);
            setMicVisualState('listening');
            setStatus('Listening…');
            showControlButtons({ pause: true, resume: false, stop: true, cancel: true });
        }).catch(function () {
            handleRecognitionError('Could not resume listening. Please try again.');
        });
    }

    function finishListeningAndSubmit() {
        // Fall back to whatever was still interim (unfinalized) — the alternative is silently
        // discarding the very words the user is looking at on screen.
        var transcript = (state.finalTranscript + ' ' + state.interimTranscript).trim();
        stopEverything();
        if (!transcript) {
            setMicVisualState('error');
            setStatus('I didn\'t catch that — please try again.', true);
            showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
            return;
        }
        state.speechState = SPEECH_STATE.PROCESSING;
        setMicVisualState('idle');
        setStatus('Understanding…');
        showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
        submitTranscript(transcript);
    }

    function stopEverything() {
        clearTimeout(state.silenceTimer);
        clearTimeout(state.maxLengthTimer);
        if (state.recognition) {
            state.recognition.onend = null;
            try { state.recognition.stop(); } catch (e) { /* already stopped */ }
            state.recognition = null;
        }
        stopWaveform();
        stopTimer();
        if (state.mediaStream) {
            state.mediaStream.getTracks().forEach(function (t) { t.stop(); });
            state.mediaStream = null;
        }
    }

    /* ── Timer ──────────────────────────────────────────────────── */

    function startTimer() {
        state.startTime = Date.now() - state.pausedElapsedMs;
        clearInterval(state.timerInterval);
        state.timerInterval = setInterval(updateTimerDisplay, 500);
    }

    function stopTimer() {
        if (state.startTime) state.pausedElapsedMs = Date.now() - state.startTime;
        clearInterval(state.timerInterval);
    }

    function updateTimerDisplay() {
        var elapsedSec = Math.floor((Date.now() - state.startTime) / 1000);
        var m = Math.floor(elapsedSec / 60), s = elapsedSec % 60;
        var el = document.getElementById('vaTimer');
        if (el) el.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
    }

    /* ── Waveform (Web Audio API — purely cosmetic, never blocks recording) ─ */

    function startWaveform(stream) {
        try {
            var Ctor = window.AudioContext || window.webkitAudioContext;
            state.audioContext = new Ctor();
            var source = state.audioContext.createMediaStreamSource(stream);
            state.analyser = state.audioContext.createAnalyser();
            state.analyser.fftSize = 256;
            source.connect(state.analyser);
            drawWaveformLoop();
        } catch (e) {
            // Waveform is cosmetic only — ignore if unsupported.
        }
    }

    function drawWaveformLoop() {
        var canvas = document.getElementById('vaWaveformCanvas');
        if (!canvas || !state.analyser) return;
        var ctx = canvas.getContext('2d');
        var bufferLength = state.analyser.frequencyBinCount;
        var dataArray = new Uint8Array(bufferLength);

        function draw() {
            if (!state.analyser) return;
            state.waveformRAF = requestAnimationFrame(draw);
            state.analyser.getByteTimeDomainData(dataArray);
            var accent = getComputedStyle(document.documentElement).getPropertyValue('--accent-color').trim() || '#255F38';
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.lineWidth = 2;
            ctx.strokeStyle = accent;
            ctx.beginPath();
            var sliceWidth = canvas.width / bufferLength;
            var x = 0;
            for (var i = 0; i < bufferLength; i++) {
                var v = dataArray[i] / 128.0;
                var y = (v * canvas.height) / 2;
                if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
                x += sliceWidth;
            }
            ctx.lineTo(canvas.width, canvas.height / 2);
            ctx.stroke();
        }
        draw();
    }

    function stopWaveform() {
        if (state.waveformRAF) cancelAnimationFrame(state.waveformRAF);
        state.waveformRAF = null;
        if (state.audioContext) {
            try { state.audioContext.close(); } catch (e) { /* already closed */ }
            state.audioContext = null;
        }
        state.analyser = null;
        var canvas = document.getElementById('vaWaveformCanvas');
        if (canvas) canvas.getContext('2d').clearRect(0, 0, canvas.width, canvas.height);
    }

    /* ── Submit transcript to the backend parser ───────────────── */

    function submitTranscript(transcript) {
        fetch('/api/voice/parse', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ transcript: transcript, priorState: state.priorState })
        }).then(function (r) {
            if (!r.ok) throw new Error('parse failed');
            return r.json();
        }).then(function (result) {
            state.historyId = result.historyId || state.historyId;
            state.finalTranscript = '';
            state.interimTranscript = '';

            if (result.intent === 'QUERY') {
                handleQueryIntent(transcript);
                return;
            }
            if (result.giveUp || result.isComplete) {
                state.priorState = null;
                renderConfirmationScreen(result);
                return;
            }
            // Follow-up needed — show the question and keep listening for the answer.
            state.priorState = result.priorState;
            var banner = document.getElementById('vaFollowupBanner');
            if (banner) {
                banner.classList.remove('d-none');
                banner.innerHTML = '<i class="fas fa-comment-dots me-1"></i>' + escHtml(result.followUpQuestion || 'Can you tell me more?');
            }
            switchScreen('recording');
            startRecording();
        }).catch(function () {
            setMicVisualState('error');
            setStatus('Something went wrong understanding that. Please try again.', true);
            showControlButtons({ pause: false, resume: false, stop: false, cancel: true });
        });
    }

    function handleQueryIntent(transcript) {
        switchScreen('recording');
        setMicVisualState('idle');
        setStatus('Thinking…');
        var transcriptEl = document.getElementById('vaTranscript');
        if (transcriptEl) transcriptEl.textContent = '';
        showControlButtons({ pause: false, resume: false, stop: false, cancel: true });

        fetch('/api/ai/chat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ conversationId: null, message: transcript })
        }).then(function (r) {
            return r.json().then(function (data) { return { ok: r.ok, data: data }; });
        }).then(function (res) {
            if (!res.ok) {
                setStatus((res.data && res.data.error) || 'Could not reach the AI assistant.', true);
                return;
            }
            if (transcriptEl) transcriptEl.textContent = res.data.message || '';
            setStatus('Tap the mic to ask something else, or log another command.');
        }).catch(function () {
            setStatus('Could not reach the AI assistant.', true);
        });
    }

    /* ── Confirmation screens (per intent) ─────────────────────── */

    function renderConfirmationScreen(result) {
        switchScreen('confirm');
        switch (result.intent) {
            case 'EXPENSE':
            case 'INCOME':
            case 'SAVINGS':
                renderTransactionConfirm(result);
                break;
            case 'TRANSFER':
                renderTransferConfirm(result);
                break;
            case 'NOTE':
                renderNoteConfirm(result);
                break;
            case 'TODO':
                renderTodoConfirm(result);
                break;
            default:
                renderUnknownConfirm(result);
        }
    }

    function wireConfirmFooter(buildRequest) {
        var cancelBtn = document.getElementById('vaConfirmCancelBtn');
        if (cancelBtn) cancelBtn.addEventListener('click', resetToRecordingScreen);

        var saveBtn = document.getElementById('vaConfirmSaveBtn');
        if (!saveBtn) return;
        saveBtn.addEventListener('click', function () {
            var req = buildRequest();
            if (req.validationError) {
                showConfirmError(req.validationError);
                return;
            }
            saveBtn.disabled = true;
            var originalHtml = saveBtn.innerHTML;
            saveBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Saving…';

            fetch(req.url, {
                method: req.method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(req.body)
            }).then(function (r) {
                return r.json().then(function (data) { return { ok: r.ok, data: data }; });
            }).then(function (res) {
                if (!res.ok) throw new Error((res.data && res.data.error) || 'Save failed');
                if (state.historyId) {
                    fetch('/api/voice/history/' + state.historyId, {
                        method: 'PATCH',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ status: 'completed', resolvedEntityType: req.entityType, resolvedEntityId: res.data.id })
                    }).catch(function () { /* history is an audit log, not load-bearing */ });
                }
                showSuccessScreen(req.entityType);
            }).catch(function (e) {
                saveBtn.disabled = false;
                saveBtn.innerHTML = originalHtml;
                showConfirmError(e.message || 'Could not save — please try again.');
            });
        });
    }

    function showConfirmError(message) {
        var existing = document.getElementById('vaConfirmError');
        if (existing) existing.remove();
        var footer = document.querySelector('#vaScreenConfirm .d-flex.justify-content-end');
        if (!footer) return;
        var div = document.createElement('div');
        div.id = 'vaConfirmError';
        div.className = 'text-danger small mb-2';
        div.textContent = message;
        footer.parentNode.insertBefore(div, footer);
    }

    function populateCategorySelect(selectId, type, selectedId) {
        var sel = document.getElementById(selectId);
        if (!sel) return;
        fetch('/api/categories?type=' + encodeURIComponent(type))
            .then(function (r) { return r.json(); })
            .then(function (categories) {
                sel.innerHTML = '<option value="">-- Select a category --</option>' +
                    categories.map(function (c) {
                        var isSelected = selectedId != null && String(selectedId) === String(c.id);
                        return '<option value="' + c.id + '"' + (isSelected ? ' selected' : '') + '>' + escHtml(c.name) + '</option>';
                    }).join('');
            }).catch(function () {
                sel.innerHTML = '<option value="">Unable to load categories</option>';
            });
    }

    function populateAccountSelect(selectId, selectedId, noneLabel) {
        var sel = document.getElementById(selectId);
        if (!sel) return;
        fetch('/api/accounts')
            .then(function (r) { return r.json(); })
            .then(function (accounts) {
                sel.innerHTML = '<option value="">' + escHtml(noneLabel) + '</option>' +
                    accounts.filter(function (a) { return a.status === 'ACTIVE'; }).map(function (a) {
                        var isSelected = selectedId != null && String(selectedId) === String(a.id);
                        return '<option value="' + a.id + '"' + (isSelected ? ' selected' : '') + '>' + escHtml(a.accountNickname) + '</option>';
                    }).join('');
            }).catch(function () {
                sel.innerHTML = '<option value="">Unable to load accounts</option>';
            });
    }

    function renderColorSwatches(containerId, colors, selected) {
        var el = document.getElementById(containerId);
        if (!el) return;
        el.innerHTML = colors.map(function (c) {
            return '<div class="va-color-swatch' + (c === selected ? ' selected' : '') + '" data-color="' + c +
                '" style="background:' + c + '" role="button" tabindex="0" aria-label="' + c + '"></div>';
        }).join('');
        el.querySelectorAll('.va-color-swatch').forEach(function (sw) {
            sw.addEventListener('click', function () {
                el.querySelectorAll('.va-color-swatch').forEach(function (s) { s.classList.remove('selected'); });
                sw.classList.add('selected');
            });
        });
    }

    function getSelectedColor(containerId) {
        var el = document.getElementById(containerId);
        var sel = el && el.querySelector('.va-color-swatch.selected');
        return sel ? sel.dataset.color : NOTE_COLORS[0];
    }

    var TX_ICON = { EXPENSE: 'fa-money-bill-wave', INCOME: 'fa-hand-holding-dollar', SAVINGS: 'fa-piggy-bank' };
    var TX_TITLE = { EXPENSE: 'Expense', INCOME: 'Income', SAVINGS: 'Savings' };

    function renderTransactionConfirm(result) {
        var f = result.fields || {};
        var intent = result.intent;
        var typeLower = intent.toLowerCase();

        var html = '<div class="va-confirm-heading"><div class="va-confirm-icon"><i class="fas ' + TX_ICON[intent] + '"></i></div>' +
            '<h5 class="mb-0">Confirm ' + TX_TITLE[intent] + '</h5></div>';
        if (result.source === 'HEURISTIC') html += heuristicBanner();
        html += fieldRow('vaAmount', 'Amount', '<input type="number" step="0.01" class="form-control" id="vaAmount" value="' +
            (f.amount != null ? f.amount : '') + '">');
        html += '<div class="va-field-row"><label for="vaCategory">Category</label><select class="form-select" id="vaCategory"><option value="">Loading…</option></select>' +
            (f.categoryPhrase && !f.categoryId ? '<div class="va-unresolved-hint">Heard "' + escHtml(f.categoryPhrase) + '" — please pick the closest match.</div>' : '') +
            '</div>';
        html += '<div class="va-field-row"><label for="vaAccount">Account (optional)</label><select class="form-select" id="vaAccount"><option value="">Loading…</option></select>' +
            (f.accountPhrase && !f.accountId ? '<div class="va-unresolved-hint">Heard "' + escHtml(f.accountPhrase) + '" — please pick the closest match.</div>' : '') +
            '</div>';
        html += fieldRow('vaDescription', 'Description', '<input type="text" class="form-control" id="vaDescription" value="' +
            escHtml(f.description || f.categoryName || '') + '">');
        html += fieldRow('vaDate', 'Date', '<input type="date" class="form-control" id="vaDate" value="' + (f.date || todayIso()) + '">');
        html += confirmFooterButtons();

        document.getElementById('vaScreenConfirm').innerHTML = html;
        populateCategorySelect('vaCategory', typeLower, f.categoryId);
        populateAccountSelect('vaAccount', f.accountId, 'None / untracked');

        wireConfirmFooter(function () {
            var amount = parseFloat(document.getElementById('vaAmount').value);
            if (!amount || amount <= 0) return { validationError: 'Please enter a valid amount.' };
            var categoryId = parseInt(document.getElementById('vaCategory').value, 10);
            if (!categoryId) return { validationError: 'Please select a category.' };
            var accountVal = document.getElementById('vaAccount').value;
            return {
                url: '/api/transactions',
                method: 'POST',
                entityType: 'transaction',
                body: {
                    amount: amount,
                    description: document.getElementById('vaDescription').value || TX_TITLE[intent],
                    transaction_type: typeLower,
                    category_id: categoryId,
                    date: document.getElementById('vaDate').value,
                    sourceAccountId: accountVal ? parseInt(accountVal, 10) : null
                }
            };
        });
    }

    function renderTransferConfirm(result) {
        var f = result.fields || {};
        var html = '<div class="va-confirm-heading"><div class="va-confirm-icon"><i class="fas fa-right-left"></i></div><h5 class="mb-0">Confirm Transfer</h5></div>';
        if (result.source === 'HEURISTIC') html += heuristicBanner();
        html += fieldRow('vaAmount', 'Amount', '<input type="number" step="0.01" class="form-control" id="vaAmount" value="' +
            (f.amount != null ? f.amount : '') + '">');
        html += '<div class="va-field-row"><label for="vaFromAccount">From (optional — external if blank)</label><select class="form-select" id="vaFromAccount"><option value="">Loading…</option></select></div>';
        html += '<div class="va-field-row"><label for="vaToAccount">To (optional — external if blank)</label><select class="form-select" id="vaToAccount"><option value="">Loading…</option></select></div>';
        html += fieldRow('vaDescription', 'Description', '<input type="text" class="form-control" id="vaDescription" value="' +
            escHtml(f.description || 'Transfer') + '">');
        html += fieldRow('vaDate', 'Date', '<input type="date" class="form-control" id="vaDate" value="' + (f.date || todayIso()) + '">');
        html += confirmFooterButtons();

        document.getElementById('vaScreenConfirm').innerHTML = html;
        populateAccountSelect('vaFromAccount', f.sourceAccountId, 'External');
        populateAccountSelect('vaToAccount', f.destinationAccountId, 'External');

        wireConfirmFooter(function () {
            var amount = parseFloat(document.getElementById('vaAmount').value);
            if (!amount || amount <= 0) return { validationError: 'Please enter a valid amount.' };
            var fromVal = document.getElementById('vaFromAccount').value;
            var toVal = document.getElementById('vaToAccount').value;
            if (!fromVal && !toVal) return { validationError: 'Select at least a From or To account.' };
            return {
                url: '/api/transactions',
                method: 'POST',
                entityType: 'transaction',
                body: {
                    amount: amount,
                    description: document.getElementById('vaDescription').value || 'Transfer',
                    transaction_type: 'transfer',
                    category_id: null,
                    date: document.getElementById('vaDate').value,
                    sourceAccountId: fromVal ? parseInt(fromVal, 10) : null,
                    destinationAccountId: toVal ? parseInt(toVal, 10) : null
                }
            };
        });
    }

    function renderNoteConfirm(result) {
        var f = result.fields || {};
        var html = '<div class="va-confirm-heading"><div class="va-confirm-icon"><i class="fas fa-sticky-note"></i></div><h5 class="mb-0">Confirm Note</h5></div>';
        if (result.source === 'HEURISTIC') html += heuristicBanner();
        html += fieldRow('vaNoteTitle', 'Title', '<input type="text" class="form-control" id="vaNoteTitle" value="' + escHtml(f.noteTitle || '') + '">');
        html += '<div class="va-field-row"><label for="vaNoteContent">Content</label><textarea class="form-control" id="vaNoteContent" rows="4">' +
            escHtml(f.noteContent || '') + '</textarea></div>';
        html += '<div class="va-field-row"><label>Color</label><div class="va-color-swatches" id="vaNoteColors"></div></div>';
        html += confirmFooterButtons();

        document.getElementById('vaScreenConfirm').innerHTML = html;
        renderColorSwatches('vaNoteColors', NOTE_COLORS, NOTE_COLORS[0]);

        wireConfirmFooter(function () {
            var title = document.getElementById('vaNoteTitle').value.trim();
            var content = document.getElementById('vaNoteContent').value;
            if (!title) return { validationError: 'Please enter a title.' };
            return {
                url: '/api/notes',
                method: 'POST',
                entityType: 'note',
                body: { title: title, content: content, color: getSelectedColor('vaNoteColors'), tags: '' }
            };
        });
    }

    function renderTodoConfirm(result) {
        var f = result.fields || {};
        var html = '<div class="va-confirm-heading"><div class="va-confirm-icon"><i class="fas fa-list-check"></i></div><h5 class="mb-0">Confirm To-Do</h5></div>';
        if (result.source === 'HEURISTIC') html += heuristicBanner();
        html += fieldRow('vaTodoTitle', 'Title', '<input type="text" class="form-control" id="vaTodoTitle" value="' + escHtml(f.todoTitle || '') + '">');
        html += fieldRow('vaTodoDue', 'Due Date (optional)', '<input type="date" class="form-control" id="vaTodoDue" value="' + (f.todoDueDate || '') + '">');
        var priority = f.todoPriority || 'medium';
        html += '<div class="va-field-row"><label for="vaTodoPriority">Priority</label><select class="form-select" id="vaTodoPriority">' +
            ['low', 'medium', 'high'].map(function (p) {
                return '<option value="' + p + '"' + (p === priority ? ' selected' : '') + '>' + capitalize(p) + '</option>';
            }).join('') + '</select></div>';
        html += confirmFooterButtons();

        document.getElementById('vaScreenConfirm').innerHTML = html;

        wireConfirmFooter(function () {
            var title = document.getElementById('vaTodoTitle').value.trim();
            if (!title) return { validationError: 'Please enter a title.' };
            return {
                url: '/api/todos',
                method: 'POST',
                entityType: 'todo',
                body: {
                    title: title,
                    dueDate: document.getElementById('vaTodoDue').value || null,
                    priority: document.getElementById('vaTodoPriority').value,
                    status: 'pending'
                }
            };
        });
    }

    function renderUnknownConfirm(result) {
        var html = '<div class="va-confirm-heading"><div class="va-confirm-icon" style="background:var(--text-secondary-custom, #6c757d)">' +
            '<i class="fas fa-question"></i></div><h5 class="mb-0">Not sure what you meant</h5></div>' +
            '<p class="text-muted small">I couldn\'t tell what you wanted to do' +
            (result.followUpQuestion ? ': ' + escHtml(result.followUpQuestion) : '.') +
            ' Try rephrasing, or open the page directly to enter it manually.</p>' +
            '<div class="d-flex justify-content-end gap-2 mt-3"><button type="button" class="btn btn-primary" id="vaTryAgainBtn">Try Again</button></div>';
        document.getElementById('vaScreenConfirm').innerHTML = html;
        document.getElementById('vaTryAgainBtn').addEventListener('click', resetToRecordingScreen);
    }

    /* ── Success screen ─────────────────────────────────────────── */

    function showSuccessScreen(entityType) {
        var labelMap = { transaction: 'Transaction logged!', note: 'Note saved!', todo: 'To-do added!' };
        document.getElementById('vaScreenSuccess').innerHTML =
            '<div class="va-success-wrap">' +
            '<div class="va-success-icon"><i class="fas fa-circle-check"></i></div>' +
            '<div class="va-success-title">' + escHtml(labelMap[entityType] || 'Saved!') + '</div>' +
            '<div class="va-success-sub">Ready for your next command.</div>' +
            '<div class="d-flex justify-content-center gap-2">' +
            '<button type="button" class="btn btn-primary" id="vaKeepGoingBtn"><i class="fas fa-microphone me-1"></i>Log Another</button>' +
            '<button type="button" class="btn btn-outline-secondary" id="vaDoneBtn">Done</button>' +
            '</div></div>';
        switchScreen('success');
        document.getElementById('vaKeepGoingBtn').addEventListener('click', resetToRecordingScreen);
        document.getElementById('vaDoneBtn').addEventListener('click', closeVoiceAssistant);
    }

    /* ── Wire the static (always-present) buttons in the recording screen ─ */

    function wireStaticButtons() {
        var micBtn = document.getElementById('vaMicButton');
        if (micBtn) micBtn.addEventListener('click', function () {
            if (state.speechState === SPEECH_STATE.LISTENING) finishListeningAndSubmit();
            else startRecording();
        });
        var pauseBtn = document.getElementById('vaPauseBtn');
        if (pauseBtn) pauseBtn.addEventListener('click', pauseRecording);
        var resumeBtn = document.getElementById('vaResumeBtn');
        if (resumeBtn) resumeBtn.addEventListener('click', resumeRecording);
        var stopBtn = document.getElementById('vaStopBtn');
        if (stopBtn) stopBtn.addEventListener('click', finishListeningAndSubmit);
        var cancelBtn = document.getElementById('vaCancelBtn');
        if (cancelBtn) cancelBtn.addEventListener('click', closeVoiceAssistant);
        var modalEl = document.getElementById('voiceAssistantModal');
        if (modalEl) modalEl.addEventListener('hidden.bs.modal', stopEverything);
    }

    wireStaticButtons();
})();
