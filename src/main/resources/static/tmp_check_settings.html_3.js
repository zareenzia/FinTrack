// ============== Settings storage ==============
function getUserStorageKey(baseKey) {
  try {
    var raw = localStorage.getItem('finzin_user') || localStorage.getItem('user');
    var u = JSON.parse(raw);
    if (u && u.id) return baseKey + '_' + u.id;
  } catch(e) {}
  return baseKey;
}
function SETTINGS_KEY()    { return getUserStorageKey('fintrack_settings'); }
const DEFAULTS = {
  sidebar:'expand', layout:'comfortable',
  animations:true, fontSize:'medium', currencyInCharts:true,
  currency:'BDT', currencySymbol:'৳', symbolPosition:'before',
  decimalPlaces:0, thousandSeparator:',', negativeFormat:'minus',
  weekStartsOn:'sunday', dateFormat:'MM/DD/YYYY', financialYear:'january',
  hideValues:false
};
function getSettings() {
  try { return Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem(SETTINGS_KEY())||'{}')); }
  catch(e) { return Object.assign({}, DEFAULTS); }
}
function saveSettings(patch) {
  var s = Object.assign({}, getSettings(), patch);
  localStorage.setItem(SETTINGS_KEY(), JSON.stringify(s));
  return s;
}

// ============== Auth ==============
function getAuthToken() {
  var cookie = document.cookie.split(';').find(function(c){ return c.trim().startsWith('Authorization='); });
  return cookie ? cookie.trim().split('=')[1] : null;
}
function authHeaders() {
  var t = getAuthToken();
  return t ? { 'Authorization': 'Bearer ' + t, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

// ============== Apply settings ==============
function applySettings(s) {
  var sizes = { small:'13px', medium:'15px', large:'17px' };
  document.documentElement.style.fontSize = sizes[s.fontSize] || '15px';
  document.documentElement.classList.toggle('reduce-motion', !s.animations);
  if (s.hideValues) document.documentElement.classList.remove('hide-values');
  // hide-values is JS-only on stat cards — no global class needed
}

// ============== Notifications ==============
var TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };
var TOAST_TYPE_ALIAS = { danger: 'error' };
function showNotification(message, type) {
  type = TOAST_TYPE_ALIAS[type] || type || 'success';
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

// ============== Icon picker (used per-row by the bulk category form below) ==============
var CAT_ICONS = ['tag','shopping-cart','home','car','utensils','heart','briefcase','gift','plane','gamepad','book','music','mobile-alt','bolt','graduation-cap','piggy-bank','chart-line','coffee','pizza-slice','baby','film','dumbbell','paw','leaf'];

// ============== Nav switching ==============
document.querySelectorAll('.settings-nav-item').forEach(function(item){
  item.addEventListener('click', function(e){
    e.preventDefault();
    var section = item.dataset.section;
    document.querySelectorAll('.settings-nav-item').forEach(function(i){ i.classList.remove('active'); });
    item.classList.add('active');
    document.querySelectorAll('.settings-section').forEach(function(s){ s.classList.remove('active'); });
    var sec = document.getElementById('section-' + section);
    if (sec) sec.classList.add('active');
    if (section === 'categories' && allCategories.length === 0) loadCategories();
    if (section === 'security') loadUserProfile();
    if (section === 'account-config') { if (typeof loadAll === 'function') loadAll(); }
    if (section === 'ai-assistant') loadAiSettings();
    if (section === 'receipt-scanner') loadReceiptScannerSettings();
    if (section === 'voice-assistant') { loadVoiceAssistantSettings(); loadVoiceHistory(); }
    if (section === 'gamification') loadGamificationSettings();
  });
});

// Jump straight to a section when linked as /settings?section=account-config (e.g. from the User Manual's quick links)
(function openSectionFromQuery(){
  var section = new URLSearchParams(window.location.search).get('section');
  if (!section) return;
  var navItem = document.querySelector('.settings-nav-item[data-section="' + section + '"]');
  if (navItem) navItem.click();
})();

// ============== Receipt Scanner Settings ==============
function loadReceiptScannerSettings() {
  fetch('/api/receipts/settings').then(function(r){ return r.json(); }).then(function(s){
    document.getElementById('receiptScannerEnabled').checked = s.enabled !== false;
  }).catch(function(){ showNotification('Unable to load Receipt Scanner settings.', 'danger'); });
}

document.getElementById('receiptScannerSaveBtn').addEventListener('click', function(){
  fetch('/api/receipts/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled: document.getElementById('receiptScannerEnabled').checked })
  }).then(function(r){
    if (r.ok) {
      showNotification('Receipt Scanner settings saved.', 'success');
    } else {
      return r.json().then(function(err){ showNotification((err && err.error) || 'Failed to save settings.', 'danger'); });
    }
  }).catch(function(){ showNotification('Unable to save settings right now.', 'danger'); });
});

// ============== AI Settings ==============
function loadAiSettings() {
  fetch('/api/ai/settings').then(function(r){ return r.json(); }).then(function(s){
    document.getElementById('aiSettingsEnabled').checked = s.enabled !== false;
    document.getElementById('aiSettingsDeveloperMode').checked = s.developerMode === true;
    document.getElementById('aiSettingsModel').value = s.model || 'gpt-5';
    document.getElementById('aiSettingsMaxTokens').value = s.maxTokens || 800;
    document.getElementById('aiSettingsTemperature').value = s.temperature != null ? s.temperature : 0.3;
    document.getElementById('aiSettingsTempLabel').textContent = (s.temperature != null ? s.temperature : 0.3);
    document.getElementById('aiSettingsProactiveInsights').checked = s.enableProactiveInsights !== false;
    document.getElementById('aiSettingsBudgetCoaching').checked = s.enableBudgetCoaching !== false;
    document.getElementById('aiSettingsSavingsCoaching').checked = s.enableSavingsCoaching !== false;
    document.getElementById('aiSettingsMonthlyReports').checked = s.enableMonthlyReports !== false;
    document.getElementById('aiSettingsDashboardSummary').checked = s.enableDashboardSummary !== false;
  }).catch(function(){ showNotification('Unable to load AI settings.', 'danger'); });
}

document.getElementById('aiSettingsTemperature').addEventListener('input', function(){
  document.getElementById('aiSettingsTempLabel').textContent = this.value;
});

document.getElementById('aiSettingsSaveBtn').addEventListener('click', function(){
  var body = {
    model: document.getElementById('aiSettingsModel').value.trim(),
    maxTokens: parseInt(document.getElementById('aiSettingsMaxTokens').value, 10),
    temperature: parseFloat(document.getElementById('aiSettingsTemperature').value),
    enabled: document.getElementById('aiSettingsEnabled').checked,
    developerMode: document.getElementById('aiSettingsDeveloperMode').checked,
    enableProactiveInsights: document.getElementById('aiSettingsProactiveInsights').checked,
    enableBudgetCoaching: document.getElementById('aiSettingsBudgetCoaching').checked,
    enableSavingsCoaching: document.getElementById('aiSettingsSavingsCoaching').checked,
    enableMonthlyReports: document.getElementById('aiSettingsMonthlyReports').checked,
    enableDashboardSummary: document.getElementById('aiSettingsDashboardSummary').checked
  };
  fetch('/api/ai/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then(function(r){
    if (r.ok) {
      showNotification('AI settings saved.', 'success');
    } else {
      return r.json().then(function(err){ showNotification((err && err.error) || 'Failed to save AI settings.', 'danger'); });
    }
  }).catch(function(){ showNotification('Unable to save AI settings right now.', 'danger'); });
});

// ============== Voice Assistant Settings ==============
function loadVoiceAssistantSettings() {
  fetch('/api/voice/settings').then(function(r){ return r.json(); }).then(function(s){
    document.getElementById('voiceSettingsEnabled').checked = s.enabled !== false;
    document.getElementById('voiceSettingsLanguage').value = s.language || 'en-US';
    document.getElementById('voiceSettingsSilence').value = s.autoStopSilenceSeconds || 3;
    document.getElementById('voiceSettingsMaxLength').value = s.maxRecordingLengthSeconds || 60;
    document.getElementById('voiceSettingsSpeed').value = s.speechSpeed || 1.0;
    document.getElementById('voiceSettingsNoiseReduction').checked = s.noiseReduction === true;
    document.getElementById('voiceSettingsSaveAudio').checked = s.saveAudioRecordings === true;
  }).catch(function(){ showNotification('Unable to load Voice Assistant settings.', 'danger'); });
}

document.getElementById('voiceSettingsSaveBtn').addEventListener('click', function(){
  var body = {
    enabled: document.getElementById('voiceSettingsEnabled').checked,
    language: document.getElementById('voiceSettingsLanguage').value,
    autoStopSilenceSeconds: parseInt(document.getElementById('voiceSettingsSilence').value, 10),
    maxRecordingLengthSeconds: parseInt(document.getElementById('voiceSettingsMaxLength').value, 10)
  };
  fetch('/api/voice/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then(function(r){
    if (r.ok) {
      showNotification('Voice Assistant settings saved.', 'success');
    } else {
      return r.json().then(function(err){ showNotification((err && err.error) || 'Failed to save Voice Assistant settings.', 'danger'); });
    }
  }).catch(function(){ showNotification('Unable to save Voice Assistant settings right now.', 'danger'); });
});

// ============== Achievements (Gamification) ==============
function loadGamificationSettings() {
  fetch('/api/gamification/settings').then(function(r){ return r.json(); }).then(function(s){
    document.getElementById('gamificationSettingsEnabled').checked = s.enabled !== false;
    document.getElementById('gamificationSettingsNotifications').checked = s.enableNotifications !== false;
    document.getElementById('gamificationSettingsDashboardWidget').checked = s.showDashboardWidget !== false;
    document.getElementById('gamificationSettingsCelebrations').checked = s.enableCelebrations !== false;
    document.getElementById('gamificationSettingsChallenges').checked = s.enableChallenges !== false;
    document.getElementById('gamificationSettingsStreakTracking').checked = s.enableStreakTracking !== false;
    document.getElementById('gamificationSettingsShowXp').checked = s.showXp !== false;
  }).catch(function(){ showNotification('Unable to load Achievement settings.', 'danger'); });
}

document.getElementById('gamificationSettingsSaveBtn').addEventListener('click', function(){
  var body = {
    enabled: document.getElementById('gamificationSettingsEnabled').checked,
    enableNotifications: document.getElementById('gamificationSettingsNotifications').checked,
    showDashboardWidget: document.getElementById('gamificationSettingsDashboardWidget').checked,
    enableCelebrations: document.getElementById('gamificationSettingsCelebrations').checked,
    enableChallenges: document.getElementById('gamificationSettingsChallenges').checked,
    enableStreakTracking: document.getElementById('gamificationSettingsStreakTracking').checked,
    showXp: document.getElementById('gamificationSettingsShowXp').checked
  };
  fetch('/api/gamification/settings', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  }).then(function(r){
    if (r.ok) {
      showNotification('Achievement settings saved.', 'success');
    } else {
      return r.json().then(function(err){ showNotification((err && err.error) || 'Failed to save Achievement settings.', 'danger'); });
    }
  }).catch(function(){ showNotification('Unable to save Achievement settings right now.', 'danger'); });
});

document.getElementById('gamificationResetBtn').addEventListener('click', function(){
  confirmAction('Permanently reset all XP, levels, achievements, streaks, and challenge progress? This cannot be undone.',
      { title: 'Reset Progress', confirmText: 'Reset Progress', confirmClass: 'btn-danger' }).then(function(confirmed){
    if (!confirmed) return;
    fetch('/api/gamification/reset', { method: 'DELETE' }).then(function(r){
      if (r.ok) showNotification('Achievement progress reset.', 'success');
      else showNotification('Failed to reset progress.', 'danger');
    }).catch(function(){ showNotification('Unable to reset progress right now.', 'danger'); });
  });
});

var VOICE_INTENT_ICONS = {
  EXPENSE: 'fa-money-bill-wave', INCOME: 'fa-hand-holding-dollar', SAVINGS: 'fa-piggy-bank',
  TRANSFER: 'fa-right-left', NOTE: 'fa-sticky-note', TODO: 'fa-list-check',
  QUERY: 'fa-comment-dots', UNKNOWN: 'fa-question'
};
var VOICE_STATUS_LABELS = {
  completed: 'Completed', pending: 'Not confirmed', awaiting_followup: 'Awaiting follow-up',
  unresolved: 'Unresolved', routed_to_chat: 'Sent to AI chat'
};

function loadVoiceHistory() {
  var container = document.getElementById('voiceHistoryList');
  container.innerHTML = '<p class="text-muted small mb-0">Loading…</p>';
  fetch('/api/voice/history').then(function(r){ return r.json(); }).then(function(items){
    if (!items.length) {
      container.innerHTML = '<p class="text-muted small mb-0">No voice commands yet — try the microphone button on any page.</p>';
      return;
    }
    container.innerHTML = items.map(function(item){
      var icon = VOICE_INTENT_ICONS[item.intent] || 'fa-microphone';
      var statusLabel = VOICE_STATUS_LABELS[item.status] || item.status;
      var statusBadgeClass = item.status === 'completed' ? 'bg-success' : (item.status === 'unresolved' ? 'bg-secondary' : 'bg-warning text-dark');
      var when = item.createdAt ? new Date(item.createdAt).toLocaleString() : '';
      return '<div class="d-flex justify-content-between align-items-start py-2 border-bottom voice-history-row" data-id="' + item.id + '">' +
        '<div class="d-flex gap-2">' +
          '<i class="fas ' + icon + ' mt-1" style="color:var(--accent-color)"></i>' +
          '<div>' +
            '<div class="small">' + escHtml(item.originalTranscript) + '</div>' +
            '<div class="text-muted" style="font-size:0.75rem">' + escHtml(when) + ' &middot; <span class="badge ' + statusBadgeClass + '">' + escHtml(statusLabel) + '</span></div>' +
          '</div>' +
        '</div>' +
        '<button type="button" class="btn btn-sm btn-link text-danger voice-history-delete-btn" title="Delete" aria-label="Delete"><i class="fas fa-trash-alt"></i></button>' +
      '</div>';
    }).join('');
    container.querySelectorAll('.voice-history-delete-btn').forEach(function(btn){
      btn.addEventListener('click', function(){
        var row = btn.closest('.voice-history-row');
        var id = row.getAttribute('data-id');
        fetch('/api/voice/history/' + id, { method: 'DELETE' }).then(function(r){
          if (r.ok) { row.remove(); if (!container.querySelector('.voice-history-row')) loadVoiceHistory(); }
          else showNotification('Failed to delete that entry.', 'danger');
        }).catch(function(){ showNotification('Unable to delete that entry right now.', 'danger'); });
      });
    });
  }).catch(function(){ container.innerHTML = '<p class="text-muted small mb-0">Unable to load voice command history.</p>'; });
}

document.getElementById('voiceHistoryClearBtn').addEventListener('click', function(){
  confirmAction('Delete all voice command history? This cannot be undone.', { title: 'Clear Voice History', confirmText: 'Clear All', confirmClass: 'btn-danger' }).then(function(confirmed){
    if (!confirmed) return;
    fetch('/api/voice/history', { method: 'DELETE' }).then(function(r){
      if (r.ok) { showNotification('Voice command history cleared.', 'success'); loadVoiceHistory(); }
      else showNotification('Failed to clear history.', 'danger');
    }).catch(function(){ showNotification('Unable to clear history right now.', 'danger'); });
  });
});

// ============== Color Theme ==============
function COLOR_THEME_KEY() { return getUserStorageKey('fintrack_color_theme'); }
function THEME_KEY()       { return getUserStorageKey('fintrack_theme'); }

// Theme + color theme are per-user, not per-browser — best-effort sync to the server so
// switching devices/browsers shows the same appearance instead of each one defaulting fresh.
function syncAppearanceToServer(patch) {
  fetch('/api/appearance-preferences', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(patch)
  }).catch(function(){});
}

// Pure DOM/localStorage apply — no network. Used both for a genuine user pick (see the click
// handler below, which separately syncs to the server) and for passively re-rendering an
// already-known value (e.g. page load, or reconciling with the server's value) — that second
// case must NOT re-PUT, or a stale/default local value would clobber the real server value.
function applyColorTheme(key) {
  if (!key || key === 'forest') {
    document.documentElement.removeAttribute('data-color-theme');
  } else {
    document.documentElement.setAttribute('data-color-theme', key);
  }
  localStorage.setItem(COLOR_THEME_KEY(), key || 'forest');
}

function syncThemeGrid() {
  var current = localStorage.getItem(COLOR_THEME_KEY()) || 'forest';
  document.querySelectorAll('.theme-option').forEach(function(opt){
    opt.classList.toggle('active', opt.dataset.themeKey === current);
  });
}

function initThemeGrid() {
  syncThemeGrid();
  document.querySelectorAll('.theme-option').forEach(function(opt){
    opt.addEventListener('click', function(){
      applyColorTheme(opt.dataset.themeKey);
      syncThemeGrid();
      syncAppearanceToServer({ colorTheme: opt.dataset.themeKey });
    });
  });
}

// ============== Display Mode ==============
function syncDisplayModeButtons() {
  var saved = localStorage.getItem(THEME_KEY()) || 'light';
  document.querySelectorAll('.display-mode-btn').forEach(function(btn){
    btn.classList.toggle('active', btn.dataset.mode === saved);
    btn.classList.toggle('btn-primary', btn.dataset.mode === saved);
    btn.classList.toggle('btn-outline-secondary', btn.dataset.mode !== saved);
  });
}

function initDisplayMode() {
  syncDisplayModeButtons();
  document.querySelectorAll('.display-mode-btn').forEach(function(btn){
    btn.addEventListener('click', function(){
      document.querySelectorAll('.display-mode-btn').forEach(function(b){
        b.classList.remove('active','btn-primary');
        b.classList.add('btn-outline-secondary');
      });
      btn.classList.add('active','btn-primary');
      btn.classList.remove('btn-outline-secondary');
      var mode = btn.dataset.mode;
      if (mode === 'system') {
        var prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
        mode = prefersDark ? 'dark' : 'light';
      }
      document.documentElement.setAttribute('data-theme', mode);
      localStorage.setItem(THEME_KEY(), mode);
      syncAppearanceToServer({ theme: mode });
    });
  });
}

// Sidebar.js resolves the server's per-user theme/color-theme asynchronously (it must not
// block first paint); when it lands after our own localStorage-based initial render, re-sync
// this page's controls so they don't show a stale value from before the reconcile.
window.addEventListener('finzin:appearance-synced', function() {
  syncThemeGrid();
  syncDisplayModeButtons();
});

// ============== Appearance ==============
function initAppearanceUI() {
  var s = getSettings();

  document.querySelectorAll('.option-pill').forEach(function(pill){
    pill.addEventListener('click', function(){
      var group = pill.closest('[data-pill-group]');
      if (!group) return;
      group.querySelectorAll('.option-pill').forEach(function(p){ p.classList.remove('active'); });
      pill.classList.add('active');
      var key = group.dataset.pillGroup;
      var val = pill.dataset.value;
      var patch = {};
      patch[key] = val;
      saveSettings(patch);
      applySettings(getSettings());
      if (['symbolPosition','thousandSeparator','negativeFormat'].indexOf(key) !== -1) {
        updateCurrencyPreview();
      }
    });
  });

  var selectAnimations = document.getElementById('selectAnimations');
  if (selectAnimations) {
    selectAnimations.value = String(s.animations !== false);
    selectAnimations.addEventListener('change', function(e){
      saveSettings({ animations: e.target.value === 'true' });
      applySettings(getSettings());
    });
  }
  var selectCurrencyInCharts = document.getElementById('selectChartCurrency');
  if (selectCurrencyInCharts) {
    selectCurrencyInCharts.value = String(s.currencyInCharts !== false);
    selectCurrencyInCharts.addEventListener('change', function(e){ saveSettings({ currencyInCharts: e.target.value === 'true' }); });
  }
  var selectFontSize = document.getElementById('selectFontSize');
  if (selectFontSize) {
    selectFontSize.value = s.fontSize || 'medium';
    selectFontSize.addEventListener('change', function(e){
      saveSettings({ fontSize: e.target.value });
      applySettings(getSettings());
    });
  }
  var selectLayout = document.getElementById('selectLayout');
  if (selectLayout) {
    selectLayout.value = s.layout || 'comfortable';
    selectLayout.addEventListener('change', function(e){
      saveSettings({ layout: e.target.value });
      applySettings(getSettings());
    });
  }

  syncAllPills(s);
}

function syncAllPills(s) {
  document.querySelectorAll('[data-pill-group]').forEach(function(group){
    var key = group.dataset.pillGroup;
    var val = String(s[key] !== undefined ? s[key] : '');
    group.querySelectorAll('.option-pill').forEach(function(p){
      p.classList.toggle('active', p.dataset.value === val);
    });
  });
}

// ============== Currency preview ==============
function formatAmount(amount, s) {
  var abs = Math.abs(amount);
  var dp = Number(s.decimalPlaces);
  var parts = abs.toFixed(dp).split('.');
  var sepMap = { ',':',', '.':'.',' ':'\u00a0', 'none':'' };
  var sep = Object.prototype.hasOwnProperty.call(sepMap, s.thousandSeparator) ? sepMap[s.thousandSeparator] : ',';
  if (sep !== '') {
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, sep);
  }
  var num = dp > 0 ? parts.join('.') : parts[0];
  var formatted = s.symbolPosition === 'before' ? s.currencySymbol + num : num + '\u00a0' + s.currencySymbol;
  if (amount < 0) {
    formatted = s.negativeFormat === 'parentheses' ? '(' + formatted + ')' : '-' + formatted;
  }
  return formatted;
}

function updateCurrencyPreview() {
  var s = getSettings();
  var pos = document.getElementById('previewPositive');
  var neg = document.getElementById('previewNegative');
  if (pos) pos.textContent = formatAmount(10000, s);
  if (neg) neg.textContent = formatAmount(-500, s);
}

function initCurrencyUI() {
  var s = getSettings();
  var currSel = document.getElementById('currencySelect');
  if (currSel) {
    currSel.value = s.currency;
    currSel.addEventListener('change', function(e){
      var opt = currSel.options[currSel.selectedIndex];
      saveSettings({ currency: e.target.value, currencySymbol: opt.dataset.symbol });
      updateCurrencyPreview();
    });
  }
  var symSel = document.getElementById('symbolPositionSelect');
  if (symSel) {
    symSel.value = s.symbolPosition || 'before';
    symSel.addEventListener('change', function(e){ saveSettings({ symbolPosition: e.target.value }); updateCurrencyPreview(); });
  }
  var decSel = document.getElementById('decimalSelect');
  if (decSel) {
    decSel.value = String(s.decimalPlaces);
    decSel.addEventListener('change', function(e){
      saveSettings({ decimalPlaces: Number(e.target.value) });
      updateCurrencyPreview();
    });
  }
  var thouSel = document.getElementById('thousandSeparatorSelect');
  if (thouSel) {
    thouSel.value = s.thousandSeparator || ',';
    thouSel.addEventListener('change', function(e){ saveSettings({ thousandSeparator: e.target.value }); updateCurrencyPreview(); });
  }
  var negSel = document.getElementById('negativeFormatSelect');
  if (negSel) {
    negSel.value = s.negativeFormat || 'minus';
    negSel.addEventListener('change', function(e){ saveSettings({ negativeFormat: e.target.value }); updateCurrencyPreview(); });
  }
  var dateSel = document.getElementById('dateFormatSelect');
  if (dateSel) {
    dateSel.value = s.dateFormat;
    dateSel.addEventListener('change', function(e){ saveSettings({ dateFormat: e.target.value }); });
  }
  var weekSel = document.getElementById('weekStartsOnSelect');
  if (weekSel) {
    weekSel.value = s.weekStartsOn || 'sunday';
    weekSel.addEventListener('change', function(e){ saveSettings({ weekStartsOn: e.target.value }); });
  }
  var finSel = document.getElementById('financialYearSelect');
  if (finSel) {
    finSel.value = s.financialYear;
    finSel.addEventListener('change', function(e){ saveSettings({ financialYear: e.target.value }); });
  }
  updateCurrencyPreview();
}

// ============== Reset Appearance ==============
var btnResetAppearance = document.getElementById('btnResetAppearance');
if (btnResetAppearance) {
  btnResetAppearance.addEventListener('click', function(){
    var keys = ['layout','animations','fontSize','currencyInCharts'];
    var patch = {};
    keys.forEach(function(k){ patch[k] = DEFAULTS[k]; });
    var s = saveSettings(patch);
    localStorage.setItem(THEME_KEY(), 'light');
    document.documentElement.setAttribute('data-theme','light');
    document.documentElement.removeAttribute('data-theme-variant');
    applySettings(s);
    var sa = document.getElementById('selectAnimations');
    if (sa) sa.value = String(DEFAULTS.animations !== false);
    var scc = document.getElementById('selectChartCurrency');
    if (scc) scc.value = String(DEFAULTS.currencyInCharts !== false);
    var sfs = document.getElementById('selectFontSize');
    if (sfs) sfs.value = DEFAULTS.fontSize || 'medium';
    var sl = document.getElementById('selectLayout');
    if (sl) sl.value = DEFAULTS.layout || 'comfortable';
    // Reset compact mode
    var sidebarCompactKey = getUserStorageKey('sidebar_compact_mode');
    localStorage.removeItem(sidebarCompactKey);
    document.body.classList.remove('sidebar-compact');
    var sm = document.getElementById('selectSidebarMode');
    if (sm) sm.value = 'false';
    syncAllPills(s);
    showNotification('Appearance reset to defaults', 'info');
  });
}

// Compact mode select
(function() {
  var sidebarCompactKey = getUserStorageKey('sidebar_compact_mode');
  var sm = document.getElementById('selectSidebarMode');
  if (sm) {
    sm.value = localStorage.getItem(sidebarCompactKey) === 'true' ? 'true' : 'false';
    sm.addEventListener('change', function() {
      var compact = this.value === 'true';
      localStorage.setItem(sidebarCompactKey, String(compact));
      document.body.classList.toggle('sidebar-compact', compact);
      showNotification(compact ? 'Compact sidebar enabled' : 'Expanded sidebar enabled', 'success');
    });
  }
})();

// ============== Categories ==============
var allCategories = [];
var catFilter = 'all';
var catSortVal = 'name';
var editingCatId = null;
var deleteCatId = null;

async function loadCategories() {
  var list = document.getElementById('catList');
  var loading = document.getElementById('catLoading');
  if (loading) loading.style.display = 'block';
  if (list) list.innerHTML = '';
  try {
    var token = getAuthToken();
    var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
    var res = await fetch('/api/categories', { headers: headers });
    if (!res.ok) throw new Error('Failed to load');
    allCategories = await res.json();
    renderCategories();
  } catch(e) {
    if (list) list.innerHTML = '<p class="text-muted text-center py-3">Failed to load categories. <button class="btn btn-sm btn-link" onclick="loadCategories()">Retry</button></p>';
  } finally {
    if (loading) loading.style.display = 'none';
  }
}

function escHtml(str) {
  return String(str||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function renderCategories() {
  var list = document.getElementById('catList');
  if (!list) return;
  var cats = allCategories.slice();
  if (catFilter !== 'all') cats = cats.filter(function(c){ return (c.categoryType||'').toLowerCase() === catFilter; });
  if (catSortVal === 'name') cats.sort(function(a,b){ return (a.name||'').localeCompare(b.name||''); });
  else if (catSortVal === 'usage') cats.sort(function(a,b){ return (b.transactionCount||0) - (a.transactionCount||0); });
  if (cats.length === 0) {
    list.innerHTML = '<p class="text-muted text-center py-3">No categories found. Add one above!</p>';
    return;
  }
  list.innerHTML = cats.map(function(c){
    var type = (c.categoryType||'general').toLowerCase();
    var icon = c.icon || 'tag';
    var color = c.color || '#888';
    var typeIcons = {income:'arrow-circle-down',expense:'arrow-circle-up',savings:'piggy-bank',general:'tag'};
    var txCount = c.transactionCount||0;
    return '<div class="cat-item"><div class="cat-icon" style="background:'+color+';width:32px;height:32px;font-size:.8rem;flex-shrink:0"><i class="fas fa-'+escHtml(icon)+'"></i></div><div class="cat-details" style="flex:1;min-width:0"><p class="cat-name mb-0" style="font-size:.88rem">'+escHtml(c.name)+'<span class="cat-type-badge cat-type-'+type+' ms-1"><i class="fas fa-'+typeIcons[type]+' me-1"></i>'+type+'</span></p>'+(c.description?'<p class="cat-meta mb-0" style="font-size:.75rem">'+escHtml(c.description)+'</p>':'')+'</div><span class="text-muted me-2" style="font-size:.75rem;white-space:nowrap"><i class="fas fa-exchange-alt me-1"></i>'+txCount+'</span><div class="d-flex gap-1 flex-shrink-0"><button class="btn btn-sm btn-outline-primary py-0 px-2 btn-edit-cat" data-id="'+c.id+'" title="Edit"><i class="fas fa-pen"></i></button><button class="btn btn-sm btn-outline-danger py-0 px-2 btn-del-cat" data-id="'+c.id+'" data-count="'+txCount+'" data-name="'+escHtml(c.name)+'" title="Delete"><i class="fas fa-trash"></i></button></div></div>';
  }).join('');
  list.querySelectorAll('.btn-edit-cat').forEach(function(btn){
    btn.addEventListener('click', function(){
      var cat = allCategories.find(function(c){ return String(c.id) === String(btn.dataset.id); });
      if (cat) openCatForm(cat);
    });
  });
  list.querySelectorAll('.btn-del-cat').forEach(function(btn){
    btn.addEventListener('click', function(){
      deleteCatId = btn.dataset.id;
      var count = Number(btn.dataset.count);
      var name = btn.dataset.name;
      var msg = document.getElementById('deleteCatMsg');
      if (msg) msg.innerHTML = count > 0
        ? 'This category "<strong>'+name+'</strong>" has <strong>'+count+' transactions</strong>. Deleting it may affect those records. Continue?'
        : 'Delete category "<strong>'+name+'</strong>"? This cannot be undone.';
      new bootstrap.Modal(document.getElementById('deleteCatModal')).show();
    });
  });
}

var catTabsEl = document.getElementById('catTabs');
if (catTabsEl) {
  catTabsEl.addEventListener('click', function(e){
    var btn = e.target.closest('[data-cat-filter]');
    if (!btn) return;
    catFilter = btn.dataset.catFilter;
    document.querySelectorAll('#catTabs [data-cat-filter]').forEach(function(b){ b.classList.toggle('active', b === btn); });
    renderCategories();
  });
}

var catSortEl = document.getElementById('catSort');
if (catSortEl) {
  catSortEl.addEventListener('change', function(e){
    catSortVal = e.target.value;
    renderCategories();
  });
}

var btnToggleAddCat = document.getElementById('btnToggleAddCat');
if (btnToggleAddCat) {
  btnToggleAddCat.addEventListener('click', function(){
    var form = document.getElementById('addCatForm');
    if (!form) return;
    if (form.style.display === 'none') openCatForm(null);
    else closeCatForm();
  });
}
var btnCancelAddCat = document.getElementById('btnCancelAddCat');
if (btnCancelAddCat) btnCancelAddCat.addEventListener('click', closeCatForm);

// ============== Bulk category rows ==============
var CATEGORY_COLORS = ['#E53935','#F4511E','#FB8C00','#FDD835','#7CB342','#43A047','#00897B','#00ACC1','#1E88E5','#3949AB','#5E35B1','#8E24AA','#D81B60','#EC407A','#6D4C41','#757575','#546E7A','#90A4AE','#2E7D32','#1565C0'];
var catRows = [];
var catRowSeq = 0;

function newCatRow(data) {
  data = data || {};
  return {
    uid: 'r' + (++catRowSeq),
    id: data.id || null,
    name: data.name || '',
    categoryType: (data.categoryType || 'general').toLowerCase(),
    color: data.color || CATEGORY_COLORS[8],
    icon: data.icon || 'tag',
    description: data.description || '',
    error: null
  };
}

function focusRowName(uid) {
  var rowEl = document.querySelector('[data-row-uid="' + uid + '"]');
  var input = rowEl ? rowEl.querySelector('.cat-row-name') : null;
  if (input) input.focus();
}

function renderCatRows() {
  var container = document.getElementById('catRowsContainer');
  if (!container) return;
  var isEdit = editingCatId !== null;

  container.innerHTML = catRows.map(function (row) {
    var canRemove = !isEdit && catRows.length > 1;
    return '' +
      '<div class="cat-bulk-row' + (row.error ? ' has-error' : '') + '" data-row-uid="' + row.uid + '">' +
        '<div class="d-flex align-items-start gap-2 mb-2">' +
          '<div class="cat-row-preview" style="background:' + row.color + '"><i class="fas fa-' + row.icon + '"></i></div>' +
          '<div class="flex-grow-1">' +
            '<div class="row g-2">' +
              '<div class="col-7"><input type="text" class="form-control form-control-sm cat-row-name" placeholder="Category Name *" value="' + escHtml(row.name) + '"></div>' +
              '<div class="col-5">' +
                '<select class="form-select form-select-sm cat-row-type">' +
                  '<option value="general">General</option>' +
                  '<option value="income">Income</option>' +
                  '<option value="expense">Expense</option>' +
                  '<option value="savings">Savings</option>' +
                '</select>' +
              '</div>' +
            '</div>' +
            '<input type="text" class="form-control form-control-sm cat-row-desc mt-2" placeholder="Description (optional)" value="' + escHtml(row.description) + '">' +
          '</div>' +
          '<div class="cat-row-actions">' +
            (isEdit ? '' : '<button type="button" class="btn btn-sm btn-outline-secondary py-0 px-2 cat-row-dup" title="Duplicate"><i class="fas fa-copy"></i></button>') +
            (canRemove ? '<button type="button" class="btn btn-sm btn-outline-danger py-0 px-2 cat-row-remove" title="Remove"><i class="fas fa-trash"></i></button>' : '') +
          '</div>' +
        '</div>' +
        '<div class="mb-1"><label class="form-label small mb-1 text-muted">Color</label><div class="color-picker cat-row-colors"></div></div>' +
        '<div><label class="form-label small mb-1 text-muted">Icon</label><div class="icon-picker cat-row-icons"></div></div>' +
        '<div class="cat-row-error small text-danger mt-1' + (row.error ? '' : ' d-none') + '">' + escHtml(row.error || '') + '</div>' +
      '</div>';
  }).join('');

  catRows.forEach(function (row) {
    var rowEl = container.querySelector('[data-row-uid="' + row.uid + '"]');
    if (!rowEl) return;

    var typeSel = rowEl.querySelector('.cat-row-type');
    typeSel.value = row.categoryType;
    typeSel.addEventListener('change', function () { row.categoryType = this.value; });

    rowEl.querySelector('.cat-row-name').addEventListener('input', function () { row.name = this.value; });
    rowEl.querySelector('.cat-row-desc').addEventListener('input', function () { row.description = this.value; });

    var colorsEl = rowEl.querySelector('.cat-row-colors');
    colorsEl.innerHTML = CATEGORY_COLORS.map(function (c) {
      return '<div class="color-swatch' + (c.toLowerCase() === row.color.toLowerCase() ? ' selected' : '') + '" data-color="' + c + '" style="background:' + c + '" title="' + c + '"></div>';
    }).join('');
    colorsEl.querySelectorAll('.color-swatch').forEach(function (sw) {
      sw.addEventListener('click', function () {
        row.color = sw.dataset.color;
        colorsEl.querySelectorAll('.color-swatch').forEach(function (s) { s.classList.toggle('selected', s === sw); });
        rowEl.querySelector('.cat-row-preview').style.background = row.color;
      });
    });

    var iconsEl = rowEl.querySelector('.cat-row-icons');
    iconsEl.innerHTML = CAT_ICONS.map(function (icon) {
      return '<div class="icon-option' + (icon === row.icon ? ' selected' : '') + '" data-icon="' + icon + '"><i class="fas fa-' + icon + '"></i></div>';
    }).join('');
    iconsEl.querySelectorAll('.icon-option').forEach(function (opt) {
      opt.addEventListener('click', function () {
        row.icon = opt.dataset.icon;
        iconsEl.querySelectorAll('.icon-option').forEach(function (o) { o.classList.toggle('selected', o === opt); });
        rowEl.querySelector('.cat-row-preview').innerHTML = '<i class="fas fa-' + row.icon + '"></i>';
      });
    });

    var dupBtn = rowEl.querySelector('.cat-row-dup');
    if (dupBtn) {
      dupBtn.addEventListener('click', function () {
        var idx = catRows.indexOf(row);
        var copy = newCatRow({ name: row.name ? row.name + ' Copy' : '', categoryType: row.categoryType, color: row.color, icon: row.icon, description: row.description });
        catRows.splice(idx + 1, 0, copy);
        renderCatRows();
        focusRowName(copy.uid);
      });
    }

    var removeBtn = rowEl.querySelector('.cat-row-remove');
    if (removeBtn) {
      removeBtn.addEventListener('click', function () {
        rowEl.classList.add('removing');
        setTimeout(function () {
          catRows = catRows.filter(function (r) { return r.uid !== row.uid; });
          renderCatRows();
        }, 180);
      });
    }
  });
}

var btnAddCatRow = document.getElementById('btnAddCatRow');
if (btnAddCatRow) {
  btnAddCatRow.addEventListener('click', function () {
    var row = newCatRow();
    catRows.push(row);
    renderCatRows();
    focusRowName(row.uid);
  });
}

function openCatForm(cat) {
  var form = document.getElementById('addCatForm');
  if (!form) return;
  editingCatId = cat ? cat.id : null;
  catRows = cat
    ? [newCatRow({ id: cat.id, name: cat.name, categoryType: cat.categoryType, color: cat.color, icon: cat.icon, description: cat.description })]
    : [newCatRow()];
  renderCatRows();

  var addRowBtn = document.getElementById('btnAddCatRow');
  if (addRowBtn) addRowBtn.classList.toggle('d-none', !!cat);

  var saveBtn = document.getElementById('btnSaveCat');
  if (saveBtn) saveBtn.innerHTML = cat ? '<i class="fas fa-check me-1"></i>Update' : '<i class="fas fa-check me-1"></i>Save';
  var titleEl = document.getElementById('catFormTitle');
  if (titleEl) titleEl.textContent = cat ? 'Edit Category' : 'New Categories';
  form.style.display = 'block';
  form.scrollIntoView({ behavior:'smooth', block:'nearest' });
  var toggleBtn = document.getElementById('btnToggleAddCat');
  if (toggleBtn) toggleBtn.innerHTML = '<i class="fas fa-times"></i>';
  focusRowName(catRows[0].uid);
}

function closeCatForm() {
  var form = document.getElementById('addCatForm');
  if (form) form.style.display = 'none';
  editingCatId = null;
  catRows = [];
  var toggleBtn = document.getElementById('btnToggleAddCat');
  if (toggleBtn) toggleBtn.innerHTML = '<i class="fas fa-plus"></i>';
}

var btnSaveCat = document.getElementById('btnSaveCat');
if (btnSaveCat) {
  btnSaveCat.addEventListener('click', async function(){
    catRows.forEach(function (r) { r.error = null; });

    var hasError = false;
    catRows.forEach(function (row) {
      if (!row.name || !row.name.trim()) { row.error = 'Name is required'; hasError = true; }
    });
    var seen = {};
    catRows.forEach(function (row) {
      var key = (row.name || '').trim().toLowerCase();
      if (!key || row.error) return;
      if (seen[key]) { row.error = 'Duplicate name in this batch'; seen[key].error = 'Duplicate name in this batch'; hasError = true; }
      else { seen[key] = row; }
    });
    if (hasError) {
      renderCatRows();
      showNotification('Please fix the highlighted row' + (catRows.length > 1 ? 's' : ''), 'danger');
      return;
    }

    var token = getAuthToken();
    var headers = Object.assign({ 'Content-Type':'application/json' }, token ? {'Authorization':'Bearer '+token} : {});

    if (editingCatId) {
      var row = catRows[0];
      try {
        var res = await fetch('/api/categories/' + editingCatId, {
          method: 'PUT', headers: headers,
          body: JSON.stringify({ name: row.name.trim(), categoryType: row.categoryType, color: row.color, icon: row.icon, description: row.description.trim() })
        });
        if (!res.ok) throw new Error('Failed');
        showNotification('Category updated!', 'success');
        closeCatForm();
        await loadCategories();
      } catch (e) {
        showNotification('Failed to save category', 'danger');
      }
      return;
    }

    var results = await Promise.all(catRows.map(function (row) {
      return fetch('/api/categories', {
        method: 'POST', headers: headers,
        body: JSON.stringify({ name: row.name.trim(), categoryType: row.categoryType, color: row.color, icon: row.icon, description: row.description.trim() })
      }).then(async function (res) {
        if (!res.ok) {
          var data = await res.json().catch(function () { return {}; });
          row.error = data.error || 'Failed to save';
          return false;
        }
        return true;
      }).catch(function () {
        row.error = 'Network error';
        return false;
      });
    }));

    var succeeded = results.filter(Boolean).length;
    var failed = results.length - succeeded;

    if (failed === 0) {
      showNotification(succeeded + ' ' + (succeeded === 1 ? 'Category' : 'Categories') + ' Created Successfully', 'success');
      closeCatForm();
    } else {
      catRows = catRows.filter(function (row) { return !!row.error; });
      renderCatRows();
      showNotification(succeeded + ' created, ' + failed + ' failed — see highlighted row' + (failed > 1 ? 's' : '') + ' below', 'danger');
    }
    await loadCategories();
  });
}

var btnConfirmDeleteCat = document.getElementById('btnConfirmDeleteCat');
if (btnConfirmDeleteCat) {
  btnConfirmDeleteCat.addEventListener('click', async function(){
    if (!deleteCatId) return;
    try {
      var token = getAuthToken();
      var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
      var res = await fetch('/api/categories/'+deleteCatId, { method:'DELETE', headers: headers });
      if (!res.ok) throw new Error('Failed');
      var modal = bootstrap.Modal.getInstance(document.getElementById('deleteCatModal'));
      if (modal) modal.hide();
      showNotification('Category deleted', 'success');
      await loadCategories();
    } catch(e) {
      showNotification('Failed to delete category', 'danger');
    }
  });
}

var btnDefaultCats = document.getElementById('btnDefaultCats');
if (btnDefaultCats) {
  btnDefaultCats.addEventListener('click', async function(){
    var defaults = [
      { name:'Salary', categoryType:'income', icon:'briefcase', color:'#16a34a', description:'Regular salary income' },
      { name:'Food', categoryType:'expense', icon:'utensils', color:'#ef4444', description:'Food and dining' },
      { name:'Transport', categoryType:'expense', icon:'car', color:'#f97316', description:'Transportation costs' },
      { name:'Bills', categoryType:'expense', icon:'bolt', color:'#eab308', description:'Monthly bills and utilities' },
      { name:'DPS', categoryType:'savings', icon:'piggy-bank', color:'#3b82f6', description:'Deposit Pension Scheme' }
    ];
    var existing = allCategories.map(function(c){ return (c.name||'').toLowerCase(); });
    var toCreate = defaults.filter(function(d){ return existing.indexOf(d.name.toLowerCase()) === -1; });
    if (toCreate.length === 0) { showNotification('Default categories already exist', 'info'); return; }
    var token = getAuthToken();
    var headers = Object.assign({ 'Content-Type':'application/json' }, token ? {'Authorization':'Bearer '+token} : {});
    try {
      await Promise.all(toCreate.map(function(d){ return fetch('/api/categories', { method:'POST', headers: headers, body: JSON.stringify(d) }); }));
      showNotification('Created '+toCreate.length+' default categories', 'success');
      await loadCategories();
    } catch(e) {
      showNotification('Failed to create some defaults', 'danger');
    }
  });
}

// ============== Profile ==============
var userProfile = null;

async function loadUserProfile() {
  try {
    var token = getAuthToken();
    if (!token) return;
    var res = await fetch('/api/auth/me', { headers: { 'Authorization': 'Bearer ' + token } });
    if (!res.ok) return;
    userProfile = await res.json();
    renderUserProfile();
  } catch(e) {}
}

function renderUserProfile() {
  if (!userProfile) return;
  var nameEl = document.getElementById('profileDisplayName');
  var fullNameEl = document.getElementById('profileFullName');
  var usernameEl = document.getElementById('profileUsername');
  var emailEl = document.getElementById('profileEmail');
  var avatarEl = document.getElementById('avatarDisplay');
  if (nameEl) nameEl.textContent = userProfile.fullName || userProfile.username || 'User';
  if (fullNameEl) fullNameEl.value = userProfile.fullName || '';
  if (usernameEl) usernameEl.value = userProfile.username || '';
  if (emailEl) emailEl.value = userProfile.email || '';
  if (avatarEl) {
    if (userProfile.profilePicture) {
      avatarEl.innerHTML = '<img src="'+userProfile.profilePicture+'" style="width:100%;height:100%;object-fit:cover" alt="Avatar">';
    } else {
      var name = userProfile.fullName || userProfile.username || 'U';
      var initials = name.split(' ').map(function(w){ return w[0]||''; }).join('').substring(0,2).toUpperCase();
      avatarEl.textContent = initials;
    }
  }
}

var btnChangePhoto = document.getElementById('btnChangePhoto');
if (btnChangePhoto) btnChangePhoto.addEventListener('click', function(){ var fi = document.getElementById('avatarFileInput'); if (fi) fi.click(); });

var avatarFileInput = document.getElementById('avatarFileInput');
if (avatarFileInput) {
  avatarFileInput.addEventListener('change', async function(e){
    var file = e.target.files && e.target.files[0];
    if (!file) return;
    var fd = new FormData();
    fd.append('picture', file);
    try {
      var token = getAuthToken();
      var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
      var res = await fetch('/api/auth/profile-picture', { method:'POST', headers: headers, body: fd });
      if (!res.ok) throw new Error();
      showNotification('Profile picture updated!', 'success');
      await loadUserProfile();
    } catch(e) {
      showNotification('Failed to update picture', 'danger');
    }
  });
}

var btnRemovePhoto = document.getElementById('btnRemovePhoto');
if (btnRemovePhoto) {
  btnRemovePhoto.addEventListener('click', function(){
    if (!confirm('Do you want to delete your profile picture?')) return;
    (async function(){
      try {
        var token = getAuthToken();
        var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
        var res = await fetch('/api/auth/profile-picture', { method:'DELETE', headers: headers });
        if (!res.ok) throw new Error();
        showNotification('Profile picture removed', 'success');
        await loadUserProfile();
      } catch(e) {
        showNotification('Failed to remove picture', 'danger');
      }
    })();
  });
}

var btnUpdateProfile = document.getElementById('btnUpdateProfile');
if (btnUpdateProfile) {
  btnUpdateProfile.addEventListener('click', async function(){
    var body = {
      fullName: document.getElementById('profileFullName') ? document.getElementById('profileFullName').value.trim() : '',
      username: document.getElementById('profileUsername') ? document.getElementById('profileUsername').value.trim() : ''
    };
    try {
      var token = getAuthToken();
      var headers = Object.assign({ 'Content-Type':'application/json' }, token ? {'Authorization':'Bearer '+token} : {});
      var res = await fetch('/api/auth/profile', { method:'PUT', headers: headers, body: JSON.stringify(body) });
      if (!res.ok) throw new Error();
      showNotification('Profile updated!', 'success');
      if (userProfile) { userProfile.fullName = body.fullName; userProfile.username = body.username; renderUserProfile(); }
    } catch(e) {
      showNotification('Failed to update profile', 'danger');
    }
  });
}

document.querySelectorAll('.pw-eye-toggle').forEach(function(btn){
  btn.addEventListener('click', function(){
    var input = document.getElementById(btn.dataset.target);
    if (!input) return;
    var isPassword = input.type === 'password';
    input.type = isPassword ? 'text' : 'password';
    btn.querySelector('i').className = isPassword ? 'fas fa-eye-slash' : 'fas fa-eye';
  });
});

// Change Password expand/collapse
(function(){
  var header  = document.getElementById('pwdToggleHeader');
  var section = document.getElementById('pwdFieldsSection');
  var chevron = document.getElementById('pwdChevron');
  if (!header || !section) return;
  header.addEventListener('click', function(){
    var isOpen = section.style.display !== 'none';
    section.style.display = isOpen ? 'none' : 'block';
    chevron.style.transform = isOpen ? '' : 'rotate(180deg)';
  });
})();

var newPasswordEl = document.getElementById('newPassword');
if (newPasswordEl) {
  newPasswordEl.addEventListener('input', function(e){
    var pw = e.target.value;
    var score = 0;
    if (pw.length >= 8) score++;
    if (/[A-Z]/.test(pw)) score++;
    if (/[0-9]/.test(pw)) score++;
    if (/[^A-Za-z0-9]/.test(pw)) score++;
    var bar = document.getElementById('pwStrengthBar');
    var label = document.getElementById('pwStrengthLabel');
    var levels = [
      { w:'0%', c:'#e5e7eb', t:'' },
      { w:'25%', c:'#ef4444', t:'Weak' },
      { w:'50%', c:'#f97316', t:'Fair' },
      { w:'75%', c:'#f59e0b', t:'Strong' },
      { w:'100%', c:'#22c55e', t:'Very Strong' }
    ];
    var lvl = levels[score] || levels[0];
    if (bar) { bar.style.width = lvl.w; bar.style.background = lvl.c; }
    if (label) { label.textContent = lvl.t; label.style.color = lvl.c; }
  });
}

var btnSavePassword = document.getElementById('btnSavePassword');
if (btnSavePassword) {
  btnSavePassword.addEventListener('click', async function(){
    var current = document.getElementById('currentPassword') ? document.getElementById('currentPassword').value : '';
    var newPw = document.getElementById('newPassword') ? document.getElementById('newPassword').value : '';
    var confirm = document.getElementById('confirmPassword') ? document.getElementById('confirmPassword').value : '';
    if (!current || !newPw || !confirm) { showNotification('Please fill in all password fields', 'warning'); return; }
    if (newPw !== confirm) { showNotification('New passwords do not match', 'danger'); return; }
    if (newPw.length < 6) { showNotification('Password must be at least 6 characters', 'warning'); return; }
    try {
      var token = getAuthToken();
      var headers = Object.assign({ 'Content-Type':'application/json' }, token ? {'Authorization':'Bearer '+token} : {});
      var res = await fetch('/api/auth/change-password', {
        method:'POST', headers: headers,
        body: JSON.stringify({ currentPassword: current, newPassword: newPw, confirmPassword: confirm })
      });
      if (!res.ok) {
        var err = await res.json().catch(function(){ return {}; });
        throw new Error(err.message || 'Failed to change password');
      }
      showNotification('Password changed successfully!', 'success');
      document.getElementById('currentPassword').value = '';
      document.getElementById('newPassword').value = '';
      document.getElementById('confirmPassword').value = '';
      var bar = document.getElementById('pwStrengthBar');
      if (bar) { bar.style.width = '0%'; bar.style.background = '#e5e7eb'; }
      var lbl = document.getElementById('pwStrengthLabel');
      if (lbl) lbl.textContent = '';
    } catch(e) {
      showNotification(e.message || 'Failed to change password', 'danger');
    }
  });
}

var toggleHideValues = document.getElementById('toggleHideValues');
if (toggleHideValues) {
  toggleHideValues.addEventListener('change', function(e){
    saveSettings({ hideValues: e.target.value === 'true' });
  });
}

function downloadCSV(filename, csvContent) {
  var blob = new Blob([csvContent], { type:'text/csv;charset=utf-8;' });
  var url = URL.createObjectURL(blob);
  var a = document.createElement('a');
  a.href = url; a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

var btnExportTransactions = document.getElementById('btnExportTransactions');
if (btnExportTransactions) {
  btnExportTransactions.addEventListener('click', async function(){
    try {
      var token = getAuthToken();
      var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
      var res = await fetch('/api/transactions?limit=10000', { headers: headers });
      if (!res.ok) throw new Error();
      var data = await res.json();
      var rows = data.content || data || [];
      var header = 'Date,Description,Amount,Type,Category,Notes\n';
      var csv = header + rows.map(function(t){
        return [t.date||'', '"'+(t.description||'').replace(/"/g,'""')+'"', t.amount||0, t.type||'',
          '"'+(((t.category && t.category.name)||'')).replace(/"/g,'""')+'"', '"'+(t.notes||'').replace(/"/g,'""')+'"'].join(',');
      }).join('\n');
      downloadCSV('fintrack-transactions.csv', csv);
      showNotification('Transactions exported!', 'success');
    } catch(e) {
      showNotification('Export failed', 'danger');
    }
  });
}

var btnExportCategories = document.getElementById('btnExportCategories');
if (btnExportCategories) {
  btnExportCategories.addEventListener('click', async function(){
    try {
      var token = getAuthToken();
      var headers = token ? { 'Authorization': 'Bearer ' + token } : {};
      var res = await fetch('/api/categories', { headers: headers });
      if (!res.ok) throw new Error();
      var data = await res.json();
      var header = 'Name,Type,Color,Icon,Description\n';
      var csv = header + data.map(function(c){
        return ['"'+(c.name||'').replace(/"/g,'""')+'"', c.categoryType||'', c.color||'', c.icon||'',
          '"'+(c.description||'').replace(/"/g,'""')+'"'].join(',');
      }).join('\n');
      downloadCSV('fintrack-categories.csv', csv);
      showNotification('Categories exported!', 'success');
    } catch(e) {
      showNotification('Export failed', 'danger');
    }
  });
}

var btnDeleteAccount = document.getElementById('btnDeleteAccount');
if (btnDeleteAccount) {
  btnDeleteAccount.addEventListener('click', function(){
    var input = document.getElementById('deleteConfirmInput');
    if (input) input.value = '';
    var btn = document.getElementById('btnConfirmDeleteAccount');
    if (btn) btn.disabled = true;
    new bootstrap.Modal(document.getElementById('deleteAccountModal')).show();
  });
}

var deleteConfirmInput = document.getElementById('deleteConfirmInput');
if (deleteConfirmInput) {
  deleteConfirmInput.addEventListener('input', function(e){
    var btn = document.getElementById('btnConfirmDeleteAccount');
    if (btn) btn.disabled = e.target.value !== 'DELETE';
  });
}

var btnConfirmDeleteAccount = document.getElementById('btnConfirmDeleteAccount');
if (btnConfirmDeleteAccount) {
  btnConfirmDeleteAccount.addEventListener('click', function(){
    btnConfirmDeleteAccount.disabled = true;
    var originalHtml = btnConfirmDeleteAccount.innerHTML;
    btnConfirmDeleteAccount.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Deleting…';
    fetch('/api/auth/account', { method: 'DELETE', headers: authHeaders() })
      .then(function(res){
        if (!res.ok) return res.json().then(function(err){ throw new Error((err && err.error) || 'Failed to delete account.'); });
        var modal = bootstrap.Modal.getInstance(document.getElementById('deleteAccountModal'));
        if (modal) modal.hide();
        showNotification('Account deleted. Redirecting…', 'warning');
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        document.cookie = 'Authorization=; path=/; expires=Thu, 01 Jan 1970 00:00:00 GMT';
        setTimeout(function(){ window.location.href = '/login.html'; }, 1500);
      })
      .catch(function(err){
        showNotification(err.message || 'Unable to delete account right now.', 'danger');
        btnConfirmDeleteAccount.disabled = false;
        btnConfirmDeleteAccount.innerHTML = originalHtml;
      });
  });
}

// ============== Star rating ==============
var feedbackRating = 0;
var starRatingEl = document.getElementById('starRating');
if (starRatingEl) {
  var stars = starRatingEl.querySelectorAll('i');
  stars.forEach(function(star, idx){
    star.addEventListener('mouseenter', function(){
      stars.forEach(function(s, i){ s.classList.toggle('active', i <= idx); });
    });
    star.addEventListener('click', function(){
      feedbackRating = idx + 1;
      stars.forEach(function(s, i){ s.classList.toggle('active', i < feedbackRating); });
    });
  });
  starRatingEl.addEventListener('mouseleave', function(){
    stars.forEach(function(s, i){ s.classList.toggle('active', i < feedbackRating); });
  });
}

var btnSendFeedback = document.getElementById('btnSendFeedback');
if (btnSendFeedback) {
  btnSendFeedback.addEventListener('click', function(){
    if (!feedbackRating) { showNotification('Please select a star rating', 'warning'); return; }
    showNotification('Thank you for your feedback! \u2b50', 'success');
    feedbackRating = 0;
    document.querySelectorAll('#starRating i').forEach(function(s){ s.classList.remove('active'); });
    var fb = document.getElementById('feedbackText');
    if (fb) fb.value = '';
  });
}

var btnReportBug = document.getElementById('btnReportBug');
if (btnReportBug) {
  btnReportBug.addEventListener('click', function(){
    new bootstrap.Modal(document.getElementById('bugReportModal')).show();
  });
}

var btnSubmitBugReport = document.getElementById('btnSubmitBugReport');
if (btnSubmitBugReport) {
  btnSubmitBugReport.addEventListener('click', function(){
    var subject = document.getElementById('bugSubject') ? document.getElementById('bugSubject').value.trim() : '';
    var desc = document.getElementById('bugDescription') ? document.getElementById('bugDescription').value.trim() : '';
    if (!subject || !desc) { showNotification('Please fill in all fields', 'warning'); return; }

    var mailtoLink = 'mailto:zareenzia801@gmail.com'
      + '?subject=' + encodeURIComponent('[FinTrack Bug] ' + subject)
      + '&body=' + encodeURIComponent('Bug Description:\n\n' + desc + '\n\n---\nSent from FinTrack Settings');
    window.location.href = mailtoLink;

    var modal = bootstrap.Modal.getInstance(document.getElementById('bugReportModal'));
    if (modal) modal.hide();
    showNotification('Opening your mail client\u2026 \uD83D\uDC1B', 'success');
    if (document.getElementById('bugSubject')) document.getElementById('bugSubject').value = '';
    if (document.getElementById('bugDescription')) document.getElementById('bugDescription').value = '';
  });
}

// ============== Init ==============
document.addEventListener('DOMContentLoaded', function(){
  var s = getSettings();
  applySettings(s);
  initAppearanceUI();
  initThemeGrid();
  initDisplayMode();
  initCurrencyUI();
  var hideToggle = document.getElementById('toggleHideValues');
  if (hideToggle) hideToggle.value = String(!!s.hideValues);
  var theme = localStorage.getItem(THEME_KEY()) || 'light';
  document.documentElement.setAttribute('data-theme', theme);
  var colorTheme = localStorage.getItem(COLOR_THEME_KEY()) || 'forest';
  applyColorTheme(colorTheme);
});