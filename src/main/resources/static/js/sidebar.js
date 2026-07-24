(function () {
    'use strict';

    function getUserStorageKey(baseKey) {
        try {
            var raw = localStorage.getItem('finzin_user') || localStorage.getItem('user');
            var u = JSON.parse(raw);
            if (u && u.id) return baseKey + '_' + u.id;
        } catch(e) {}
        return baseKey;
    }

    const COLLAPSED_KEY  = getUserStorageKey('sidebar_collapsed');
    const THEME_KEY      = getUserStorageKey('fintrack_theme');
    const SETTINGS_KEY   = getUserStorageKey('fintrack_settings');
    const COMPACT_KEY    = getUserStorageKey('sidebar_compact_mode');

    // ── All available sidebar modules (master registry) ──────────────────────
    const SIDEBAR_MODULES = [
        { id: 'dashboard',         label: 'Home',              icon: 'fas fa-home',            href: '/dashboard',         locked: true  },
        { id: 'transactions',      label: 'Transactions',      icon: 'fas fa-exchange-alt',    href: '/transactions'                     },
        { id: 'budget-planner',    label: 'Budget Planner',    icon: 'fas fa-wallet',          href: '/budget-planner'                   },
        { id: 'notes',             label: 'Notes',             icon: 'fas fa-sticky-note',     href: '/notes'                            },
        { id: 'todos',             label: 'To-Do',             icon: 'fas fa-tasks',           href: '/todos'                            },
        { id: 'assets',            label: 'Assets',            icon: 'fas fa-coins',           href: '/assets'                           },
        { id: 'financial-planner', label: 'Financial Planner', icon: 'fas fa-bullseye',        href: '/financial-planner'                },
        { id: 'calculator',        label: 'Calculator',        icon: 'fas fa-calculator',      href: null,   special: 'calculator'       },
        { id: 'settings',          label: 'Settings',          icon: 'fas fa-cog',             href: '/settings',          locked: true  },
    ];

    // ── Sidebar preference helpers ────────────────────────────────────────────
    function getSidebarPrefsKey() { return getUserStorageKey('sidebar_prefs'); }

    function getDefaultPrefs() {
        return SIDEBAR_MODULES.map(function(m, i) {
            return { id: m.id, visible: true, pinned: false, displayOrder: i };
        });
    }

    function loadSidebarPrefs() {
        try {
            var raw = localStorage.getItem(getSidebarPrefsKey());
            if (!raw) return getDefaultPrefs();
            var saved = JSON.parse(raw);
            // Merge: add any new modules not in saved prefs
            var savedIds = saved.map(function(p) { return p.id; });
            var maxOrder = saved.reduce(function(m,p) { return Math.max(m, p.displayOrder || 0); }, 0);
            SIDEBAR_MODULES.forEach(function(m, i) {
                if (savedIds.indexOf(m.id) === -1) {
                    saved.push({ id: m.id, visible: true, pinned: false, displayOrder: maxOrder + i + 1 });
                }
            });
            return saved;
        } catch(e) {
            return getDefaultPrefs();
        }
    }

    function saveSidebarPrefs(prefs) {
        try { localStorage.setItem(getSidebarPrefsKey(), JSON.stringify(prefs)); } catch(e) {}
        // Best-effort server sync (non-blocking)
        fetch('/api/sidebar-preferences', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ preferencesJson: JSON.stringify(prefs) })
        }).catch(function() {});
    }

    function getOrderedModules(prefs) {
        var prefMap = {};
        prefs.forEach(function(p) { prefMap[p.id] = p; });

        var pinned = [], unpinned = [];
        SIDEBAR_MODULES.forEach(function(m) {
            var p = prefMap[m.id] || { visible: true, pinned: false, displayOrder: 99 };
            if (!p.visible) return;
            if (p.pinned && !m.locked) pinned.push({ module: m, pref: p });
            else unpinned.push({ module: m, pref: p });
        });
        pinned.sort(function(a,b) { return (a.pref.displayOrder||0) - (b.pref.displayOrder||0); });
        // locked modules stay at their natural position in unpinned
        var lockedAtStart = unpinned.filter(function(x) { return x.module.locked && x.module.id === 'dashboard'; });
        var lockedAtEnd   = unpinned.filter(function(x) { return x.module.locked && x.module.id !== 'dashboard'; });
        var free          = unpinned.filter(function(x) { return !x.module.locked; });
        free.sort(function(a,b) { return (a.pref.displayOrder||0) - (b.pref.displayOrder||0); });
        return lockedAtStart.concat(pinned).concat(free).concat(lockedAtEnd);
    }

    // ── Apply global settings (font-size, animations, color theme) immediately
    (function applyGlobalSettingsEarly() {
        try {
            var s = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}');
            var sizes = { small: '13px', medium: '15px', large: '17px' };
            if (s.fontSize && sizes[s.fontSize]) {
                document.documentElement.style.fontSize = sizes[s.fontSize];
            }
            if (s.animations === false) {
                document.documentElement.classList.add('reduce-motion');
            }
        } catch (e) { /* silently ignore */ }
    })();

    const COLOR_THEME_KEY = getUserStorageKey('fintrack_color_theme');

    function applyColorTheme(key) {
        if (!key || key === 'forest') {
            document.documentElement.removeAttribute('data-color-theme');
        } else {
            document.documentElement.setAttribute('data-color-theme', key);
        }
    }

    // ── Apply saved color theme immediately (before first paint)
    (function applyColorThemeEarly() {
        applyColorTheme(localStorage.getItem(COLOR_THEME_KEY) || 'forest');
    })();

    // Apply theme immediately on script load to prevent flash of wrong theme
    (function applyThemeEarly() {
        var s = {};
        try { s = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}'); } catch(e){}
        var saved = localStorage.getItem(THEME_KEY) || s.theme;
        var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        var theme;
        if (s.displayMode === 'system') {
            theme = prefersDark ? 'dark' : 'light';
        } else {
            theme = saved || (prefersDark ? 'dark' : 'light');
        }
        document.documentElement.setAttribute('data-theme', theme);
    })();

    /* ── Theme helpers ─────────────────────────────────────────── */

    function getPreferredTheme() {
        const saved = localStorage.getItem(THEME_KEY);
        if (saved) return saved;
        return (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
            ? 'dark' : 'light';
    }

    function applyTheme(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        const icon  = document.getElementById('themeToggleIcon');
        const label = document.getElementById('themeToggleLbl');
        if (icon) {
            icon.className = (theme === 'dark')
                ? 'fas fa-sun sidebar-icon theme-icon-spin'
                : 'fas fa-moon sidebar-icon theme-icon-spin';
            // Remove animation class after it plays so it re-triggers next click
            setTimeout(function () {
                if (icon) icon.classList.remove('theme-icon-spin');
            }, 450);
        }
        if (label) {
            label.textContent = (theme === 'dark') ? 'Light Mode' : 'Dark Mode';
        }
    }

    function toggleTheme() {
        const current = document.documentElement.getAttribute('data-theme') || 'light';
        const next = (current === 'dark') ? 'light' : 'dark';
        localStorage.setItem(THEME_KEY, next);
        applyTheme(next);
        syncAppearanceToServer({ theme: next });
    }

    function initTheme() {
        const theme = getPreferredTheme();
        applyTheme(theme);
        // Follow system preference changes only when user has no saved preference
        if (window.matchMedia) {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function (e) {
                if (!localStorage.getItem(THEME_KEY)) {
                    applyTheme(e.matches ? 'dark' : 'light');
                }
            });
        }
    }

    // ── Cross-device sync ─────────────────────────────────────────
    // Theme/color-theme are cached in localStorage (per-browser) so the page can apply
    // them before first paint with no flash, but the source of truth is per-user on the
    // server — otherwise the same account shows a different theme in every browser/device.
    function syncAppearanceToServer(patch) {
        fetch('/api/appearance-preferences', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(patch)
        }).catch(function () {});
    }

    function syncAppearanceFromServer() {
        fetch('/api/appearance-preferences')
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (data) {
                if (!data) return;
                var changed = false;
                if (data.theme && data.theme !== localStorage.getItem(THEME_KEY)) {
                    localStorage.setItem(THEME_KEY, data.theme);
                    applyTheme(data.theme);
                    changed = true;
                }
                if (data.colorTheme && data.colorTheme !== localStorage.getItem(COLOR_THEME_KEY)) {
                    localStorage.setItem(COLOR_THEME_KEY, data.colorTheme);
                    applyColorTheme(data.colorTheme);
                    changed = true;
                }
                if (changed) {
                    if (typeof window.reloadSidebar === 'function') window.reloadSidebar();
                    window.dispatchEvent(new CustomEvent('finzin:appearance-synced', { detail: data }));
                }
            })
            .catch(function () {});
    }

    /* ── User helpers ──────────────────────────────────────────── */

    function getUser() {
        try {
            const u = localStorage.getItem('user');
            return u ? JSON.parse(u) : null;
        } catch (e) {
            return null;
        }
    }

    function getInitials(name) {
        if (!name) return '?';
        return name.trim().split(/\s+/).map(function (w) { return w[0]; }).slice(0, 2).join('').toUpperCase();
    }

    function buildSidebar() {
        const user = getUser();
        const userName = user ? (user.fullName || user.username || user.name || 'User') : 'User';
        const initials = getInitials(userName);
        const profilePicUrl = user && user.profilePicture ? user.profilePicture : null;
        const isDark = (localStorage.getItem(THEME_KEY) || getPreferredTheme()) === 'dark';
        const themeIcon  = isDark ? 'fas fa-sun sidebar-icon' : 'fas fa-moon sidebar-icon';
        const themeLabel = isDark ? 'Light Mode' : 'Dark Mode';

        const aside = document.createElement('aside');
        aside.id = 'sidebar';

        // Build nav items from preferences
        const prefs = loadSidebarPrefs();
        const ordered = getOrderedModules(prefs);
        const prefMap = {};
        prefs.forEach(function(p) { prefMap[p.id] = p; });

        var navItemsHtml = ordered.map(function(entry) {
            var m = entry.module;
            var p = entry.pref;
            var pinBadge = (p.pinned && !m.locked)
                ? '<span class="sidebar-pin-dot" title="Pinned" aria-label="Pinned"></span>' : '';

            if (m.special === 'calculator') {
                return '<button class="sidebar-item" id="calcSidebarBtn" title="' + m.label + '" aria-label="' + m.label + '" style="background:none;border:none;width:100%;text-align:left;">' +
                    '<i class="' + m.icon + ' sidebar-icon"></i>' +
                    '<span class="sidebar-label">' + m.label + '</span>' + pinBadge +
                    '</button>';
            }
            return '<a href="' + m.href + '" class="sidebar-item" data-path="' + m.id + '" title="' + m.label + '" aria-label="' + m.label + '">' +
                '<i class="' + m.icon + ' sidebar-icon"></i>' +
                '<span class="sidebar-label">' + m.label + '</span>' + pinBadge +
                '</a>';
        }).join('');

        aside.innerHTML = `
            <div class="sidebar-header">
                <div class="sidebar-brand">
                    <img src="/images/logo.png" alt="FinTrack" class="sidebar-brand-logo">
                    <span class="sidebar-label">FinTrack</span>
                </div>
                <button class="sidebar-toggle-btn" id="sidebarToggleBtn" title="Toggle sidebar" aria-label="Toggle sidebar">
                    <i class="fas fa-chevron-left" id="sidebarToggleIcon"></i>
                </button>
            </div>

            <nav class="sidebar-nav" id="sidebarNav" aria-label="Main navigation">
                ${navItemsHtml}
            </nav>

            <div class="sidebar-spacer"></div>

            <div class="sidebar-bottom">
                <div class="sidebar-icon-row">
                    <button class="sidebar-item sidebar-icon-only" id="notificationBellBtn" title="Notifications" aria-label="Notifications" style="background:none;border:none;position:relative;">
                        <i class="fas fa-bell sidebar-icon"></i>
                        <span class="sidebar-label">Notifications</span>
                        <span id="notifBadge" class="d-none" style="position:absolute; top:6px; right:6px; background:#dc3545; color:#fff; border-radius:999px; font-size:0.65rem; padding:1px 6px; font-weight:600;" aria-live="polite"></span>
                    </button>
                    <a href="/ai-assistant" class="sidebar-item sidebar-icon-only" id="aiAssistantIconBtn" data-path="ai-assistant" title="AI Assistant" aria-label="AI Assistant">
                        <i class="fas fa-robot sidebar-icon"></i>
                        <span class="sidebar-label">AI Assistant</span>
                    </a>
                    <button class="sidebar-item sidebar-icon-only" id="userManualBtn" title="User Manual &amp; Getting Started" aria-label="User Manual and Getting Started Guide" style="background:none;border:none;position:relative;">
                        <i class="fas fa-book-open sidebar-icon"></i>
                        <span class="sidebar-label">User Manual</span>
                        <span id="manualBadgeDot" class="manual-badge-dot d-none" aria-hidden="true"></span>
                    </button>
                </div>
                <button class="sidebar-item sidebar-theme-toggle" id="themeToggleBtn" title="Toggle theme" aria-label="Toggle theme">
                    <span class="theme-icon-wrap">
                        <i class="${themeIcon}" id="themeToggleIcon"></i>
                    </span>
                    <span class="sidebar-label" id="themeToggleLbl">${themeLabel}</span>
                </button>
                <div class="sidebar-item sidebar-profile" id="sidebarProfileBtn" title="${userName}" tabindex="0" role="button" aria-label="Profile: ${userName}">
                    <div class="sidebar-avatar" id="sidebarAvatar" style="overflow:hidden;">${profilePicUrl ? `<img src="${profilePicUrl}" alt="avatar" style="width:100%;height:100%;object-fit:cover;border-radius:50%;">` : initials}</div>
                    <span class="sidebar-label" id="sidebarUserName">${userName}</span>
                </div>
                <a href="#" class="sidebar-item sidebar-logout" id="sidebarLogoutBtn" title="Logout" aria-label="Logout">
                    <i class="fas fa-sign-out-alt sidebar-icon"></i>
                    <span class="sidebar-label">Logout</span>
                </a>
            </div>
        `;
        return aside;
    }

    function setActiveItem(sidebar) {
        const path = window.location.pathname.replace(/^\//, '');
        sidebar.querySelectorAll('.sidebar-item[data-path]').forEach(function (item) {
            const p = item.getAttribute('data-path');
            const isActive = path === p || (path === '' && p === 'dashboard') || (path === 'dashboard' && p === 'dashboard');
            item.classList.toggle('active', isActive);
        });
    }

    function applyCollapseState(sidebar, collapsed) {
        sidebar.classList.toggle('collapsed', collapsed);
        const icon = document.getElementById('sidebarToggleIcon');
        if (icon) icon.className = collapsed ? 'fas fa-chevron-right' : 'fas fa-chevron-left';
    }

    function toggleSidebar() {
        const sidebar = document.getElementById('sidebar');
        const collapsed = !sidebar.classList.contains('collapsed');
        localStorage.setItem(COLLAPSED_KEY, collapsed);
        applyCollapseState(sidebar, collapsed);
    }

    function openMobileSidebar() {
        const sidebar = document.getElementById('sidebar');
        const overlay = document.getElementById('sidebarOverlay');
        if (sidebar) sidebar.classList.add('mobile-open');
        if (overlay) overlay.classList.add('visible');
        document.body.style.overflow = 'hidden';
    }

    function closeMobileSidebar() {
        const sidebar = document.getElementById('sidebar');
        const overlay = document.getElementById('sidebarOverlay');
        if (sidebar) sidebar.classList.remove('mobile-open');
        if (overlay) overlay.classList.remove('visible');
        document.body.style.overflow = '';
    }

    function openProfileModal() {
        const modal = document.getElementById('userProfileModal');
        if (modal && window.bootstrap) {
            bootstrap.Modal.getOrCreateInstance(modal).show();
            // loadProfileData is defined at the top level of profile.js, so it's available globally
            if (typeof loadProfileData === 'function') {
                loadProfileData();
            } else {
                // Profile.js may still be loading – wait for it
                var waitAttempts = 0;
                var waitForProfile = setInterval(function () {
                    if (typeof loadProfileData === 'function') {
                        clearInterval(waitForProfile);
                        loadProfileData();
                    } else if (++waitAttempts > 30) {
                        clearInterval(waitForProfile);
                    }
                }, 100);
            }
        } else {
            // Fallback: try again shortly in case Bootstrap isn't loaded yet
            setTimeout(function () {
                var m = document.getElementById('userProfileModal');
                if (m && window.bootstrap) bootstrap.Modal.getOrCreateInstance(m).show();
            }, 200);
        }
    }

    function injectProfileModal() {
        if (document.getElementById('userProfileModal')) return;

        var modalHtml = '<div class="modal fade" id="userProfileModal" tabindex="-1" aria-hidden="true">' +
  '<div class="modal-dialog modal-dialog-centered modal-dialog-scrollable profile-modal-dialog">' +
    '<div class="modal-content profile-modal-content">' +
      '<div class="profile-banner">' +
        '<button type="button" class="btn-close btn-close-white profile-modal-close" data-bs-dismiss="modal" aria-label="Close"></button>' +
        '<div class="profile-banner-deco"></div>' +
      '</div>' +
      '<div class="profile-avatar-section">' +
        '<div class="profile-avatar-container" id="profileAvatarContainer" onclick="window._profileUploadTrigger&&window._profileUploadTrigger()" title="Click to change photo">' +
          '<img src="" alt="" class="profile-avatar-img d-none" id="profileAvatarImg">' +
          '<div class="profile-avatar-initials" id="profileAvatarInitials">?</div>' +
          '<div class="profile-avatar-overlay"><i class="fas fa-camera"></i><span>Change</span></div>' +
        '</div>' +
        '<input type="file" id="profilePictureInput" accept=".jpg,.jpeg,.png,.webp" style="display:none">' +
        '<div class="profile-name-preview mt-2">' +
          '<h5 class="profile-display-name" id="profileDisplayName">Loading\u2026</h5>' +
          '<span class="profile-display-username" id="profileDisplayUsername"></span>' +
        '</div>' +
        '<div class="profile-pic-icon-btns d-flex gap-2 justify-content-center mt-2">' +
          '<button class="pf-icon-btn" id="btnUploadPhoto" onclick="window._profileUploadTrigger&&window._profileUploadTrigger()" title="Upload Photo">' +
            '<i class="fas fa-camera"></i>' +
          '</button>' +
          '<button class="pf-icon-btn pf-icon-btn--danger d-none" id="btnRemovePhoto" onclick="removeProfilePicture()" title="Remove Photo">' +
            '<i class="fas fa-trash-alt"></i>' +
          '</button>' +
        '</div>' +
        '<div id="uploadProgressWrap" class="d-none mt-2 text-center">' +
          '<div class="progress" style="height:3px;width:200px;margin:0 auto;">' +
            '<div class="progress-bar bg-success progress-bar-striped progress-bar-animated w-100"></div>' +
          '</div>' +
          '<small class="text-muted">Uploading\u2026</small>' +
        '</div>' +
      '</div>' +
      '<div class="modal-body profile-modal-body">' +
        '<div class="profile-section">' +
          '<div class="profile-section-title"><i class="fas fa-user-circle me-2"></i>Personal Information</div>' +
          '<div class="mb-3">' +
            '<label class="profile-field-label"><i class="fas fa-id-badge me-1"></i>Full Name</label>' +
            '<input type="text" class="form-control profile-input" id="profileFullName" placeholder="Your full name" maxlength="100" autocomplete="name">' +
            '<div class="field-error d-none" id="fullNameError"></div>' +
          '</div>' +
          '<div class="mb-3">' +
            '<label class="profile-field-label"><i class="fas fa-at me-1"></i>Username <span class="badge bg-secondary ms-1" style="font-size:.6rem;vertical-align:middle">optional</span></label>' +
            '<div class="input-group">' +
              '<span class="input-group-text">@</span>' +
              '<input type="text" class="form-control profile-input" id="profileUsername" placeholder="your_username" maxlength="30" autocomplete="username" oninput="onUsernameInput()">' +
              '<span class="input-group-text uname-status d-none" id="usernameStatusIcon">' +
                '<i class="fas fa-circle-notch fa-spin" id="usernameSpinner"></i>' +
                '<i class="fas fa-check-circle text-success d-none" id="usernameOk"></i>' +
                '<i class="fas fa-times-circle text-danger d-none" id="usernameFail"></i>' +
              '</span>' +
            '</div>' +
            '<div class="form-text">3\u201330 chars \u00b7 letters, numbers, <code>_</code> and <code>.</code> only</div>' +
            '<div class="field-error d-none" id="usernameError"></div>' +
            '<div class="field-success d-none" id="usernameSuccess"></div>' +
          '</div>' +
          '<div class="mb-2">' +
            '<label class="profile-field-label"><i class="fas fa-envelope me-1"></i>Email Address</label>' +
            '<div class="input-group">' +
              '<span class="input-group-text"><i class="fas fa-lock" style="font-size:.75rem;opacity:.6"></i></span>' +
              '<input type="email" class="form-control profile-input profile-email-readonly" id="profileEmail" readonly disabled>' +
            '</div>' +
            '<div class="form-text">Email cannot be changed \u2014 it is your unique account identifier.</div>' +
          '</div>' +
        '</div>' +
        '<div class="profile-section">' +
          '<div class="profile-section-title d-flex justify-content-between align-items-center">' +
            '<span><i class="fas fa-shield-alt me-2"></i>Security</span>' +
            '<button class="btn btn-sm btn-link p-0 profile-toggle-pwd" type="button" data-bs-toggle="collapse" data-bs-target="#pwdSection" aria-expanded="false">Change Password <i class="fas fa-chevron-down ms-1 toggle-chev"></i></button>' +
          '</div>' +
          '<div class="collapse" id="pwdSection">' +
            '<div class="pwd-section-body">' +
              '<div class="mb-3">' +
                '<label class="profile-field-label"><i class="fas fa-key me-1"></i>Current Password</label>' +
                '<div class="input-group">' +
                  '<input type="password" class="form-control profile-input" id="currentPassword" placeholder="Current password" autocomplete="current-password">' +
                  '<button class="btn btn-outline-secondary" type="button" onclick="togglePwdVis(\'currentPassword\',this)" tabindex="-1"><i class="fas fa-eye"></i></button>' +
                '</div>' +
                '<div class="field-error d-none" id="currentPwdError"></div>' +
              '</div>' +
              '<div class="mb-3">' +
                '<label class="profile-field-label"><i class="fas fa-lock me-1"></i>New Password</label>' +
                '<div class="input-group">' +
                  '<input type="password" class="form-control profile-input" id="newPassword" placeholder="New password" autocomplete="new-password" oninput="updatePwdStrength(this.value)">' +
                  '<button class="btn btn-outline-secondary" type="button" onclick="togglePwdVis(\'newPassword\',this)" tabindex="-1"><i class="fas fa-eye"></i></button>' +
                '</div>' +
                '<div class="pwd-strength-wrap d-none" id="pwdStrengthWrap">' +
                  '<div class="pwd-strength-bar"><div class="pwd-strength-fill" id="pwdStrengthFill"></div></div>' +
                  '<small class="pwd-strength-lbl" id="pwdStrengthLbl"></small>' +
                '</div>' +
                '<div class="field-error d-none" id="newPwdError"></div>' +
              '</div>' +
              '<div class="mb-3">' +
                '<label class="profile-field-label"><i class="fas fa-lock me-1"></i>Confirm New Password</label>' +
                '<div class="input-group">' +
                  '<input type="password" class="form-control profile-input" id="confirmPassword" placeholder="Confirm new password" autocomplete="new-password">' +
                  '<button class="btn btn-outline-secondary" type="button" onclick="togglePwdVis(\'confirmPassword\',this)" tabindex="-1"><i class="fas fa-eye"></i></button>' +
                '</div>' +
                '<div class="field-error d-none" id="confirmPwdError"></div>' +
              '</div>' +
              '<button class="btn btn-warning w-100" id="btnChangePwd" onclick="submitPasswordChange()">' +
                '<span class="btn-pwd-txt"><i class="fas fa-key me-1"></i>Update Password</span>' +
                '<span class="btn-pwd-ldg d-none"><i class="fas fa-spinner fa-spin me-1"></i>Updating\u2026</span>' +
              '</button>' +
            '</div>' +
          '</div>' +
        '</div>' +
        '<div class="profile-meta"><i class="fas fa-calendar-alt me-1"></i>Member since <strong id="profileMemberSince">\u2014</strong></div>' +
      '</div>' +
      '<div class="modal-footer profile-modal-footer">' +
        '<button class="pf-icon-btn pf-icon-btn--logout" onclick="doLogout()" title="Sign Out"><i class="fas fa-sign-out-alt"></i></button>' +
        '<div class="ms-auto d-flex gap-2 align-items-center">' +
          '<button class="pf-icon-btn pf-icon-btn--cancel" data-bs-dismiss="modal" title="Discard &amp; Close"><i class="fas fa-times"></i></button>' +
          '<button class="pf-icon-btn pf-icon-btn--save" id="btnSaveProfile" onclick="saveProfile()" title="Save Changes">' +
            '<span class="btn-save-txt"><i class="fas fa-check"></i></span>' +
            '<span class="btn-save-ldg d-none"><i class="fas fa-spinner fa-spin"></i></span>' +
          '</button>' +
        '</div>' +
      '</div>' +
    '</div>' +
  '</div>' +
'</div>';

        var div = document.createElement('div');
        div.innerHTML = modalHtml;
        document.body.appendChild(div.firstElementChild);

        if (!document.querySelector('script[src="/js/profile.js"]')) {
            var script = document.createElement('script');
            script.src = '/js/profile.js';
            document.body.appendChild(script);
        }
    }

    function injectCalculator() {
        // Load CSS
        if (!document.querySelector('link[href="/css/calculator.css"]')) {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = '/css/calculator.css';
            document.head.appendChild(link);
        }
        // Load mobile CSS (global, idempotent)
        if (!document.querySelector('link[href="/css/mobile.css"]')) {
            var mlink = document.createElement('link');
            mlink.rel = 'stylesheet';
            mlink.href = '/css/mobile.css';
            document.head.appendChild(mlink);
        }

        // Inject popup HTML (idempotent)
        if (document.getElementById('calcPopup')) return;

        var calcHtml =
            '<div class="calc-popup calc-hidden" id="calcPopup" role="dialog" aria-label="Calculator" aria-modal="false">' +
              '<div class="calc-header" id="calcHeader">' +
                '<div class="calc-header-title"><i class="fas fa-calculator"></i> Calculator</div>' +
                '<div class="calc-header-actions">' +
                  '<button class="calc-ctrl-btn" id="calcPinBtn" title="Pin" aria-label="Pin"><i class="fas fa-thumbtack"></i></button>' +
                  '<button class="calc-ctrl-btn" id="calcMinBtn" title="Minimize" aria-label="Minimize"><i class="fas fa-minus"></i></button>' +
                  '<button class="calc-ctrl-btn" id="calcCloseBtn" title="Close" aria-label="Close"><i class="fas fa-times"></i></button>' +
                '</div>' +
              '</div>' +
              '<div class="calc-body" id="calcBody">' +
                '<div class="calc-display">' +
                  '<div class="calc-expr-row">' +
                    '<div class="calc-expr" id="calcExpr" aria-live="polite"></div>' +
                    '<button class="calc-del-btn" id="calcDelBtn" title="Backspace" aria-label="Backspace">&#x232B;</button>' +
                  '</div>' +
                  '<div class="calc-result" id="calcResult" aria-live="polite" aria-atomic="true">0</div>' +
                '</div>' +
                '<div class="calc-memory-bar" role="toolbar" aria-label="Memory">' +
                  '<button class="calc-mem-btn" data-mem="mc" title="Memory Clear">MC</button>' +
                  '<button class="calc-mem-btn" data-mem="mr" title="Memory Recall">MR</button>' +
                  '<button class="calc-mem-btn" data-mem="mplus" title="Memory Add">M+</button>' +
                  '<button class="calc-mem-btn" data-mem="mminus" title="Memory Subtract">M\u2212</button>' +
                  '<span class="calc-mem-indicator" id="calcMemIndicator" aria-live="polite"></span>' +
                '</div>' +
                '<div class="calc-buttons" role="group" aria-label="Calculator buttons">' +
                  '<button class="calc-btn calc-btn-fn calc-btn-danger" data-action="ac" aria-label="All Clear">AC</button>' +
                  '<button class="calc-btn calc-btn-fn" data-action="toggle-sign" aria-label="Toggle sign">+/\u2212</button>' +
                  '<button class="calc-btn calc-btn-fn" data-action="percent" aria-label="Percent">%</button>' +
                  '<button class="calc-btn calc-btn-op" data-action="divide" aria-label="Divide">\u00f7</button>' +
                  '<button class="calc-btn" data-action="num" data-val="7" aria-label="7">7</button>' +
                  '<button class="calc-btn" data-action="num" data-val="8" aria-label="8">8</button>' +
                  '<button class="calc-btn" data-action="num" data-val="9" aria-label="9">9</button>' +
                  '<button class="calc-btn calc-btn-op" data-action="multiply" aria-label="Multiply">\u00d7</button>' +
                  '<button class="calc-btn" data-action="num" data-val="4" aria-label="4">4</button>' +
                  '<button class="calc-btn" data-action="num" data-val="5" aria-label="5">5</button>' +
                  '<button class="calc-btn" data-action="num" data-val="6" aria-label="6">6</button>' +
                  '<button class="calc-btn calc-btn-op" data-action="subtract" aria-label="Subtract">\u2212</button>' +
                  '<button class="calc-btn" data-action="num" data-val="1" aria-label="1">1</button>' +
                  '<button class="calc-btn" data-action="num" data-val="2" aria-label="2">2</button>' +
                  '<button class="calc-btn" data-action="num" data-val="3" aria-label="3">3</button>' +
                  '<button class="calc-btn calc-btn-op" data-action="add" aria-label="Add">+</button>' +
                  '<button class="calc-btn calc-btn-zero" data-action="num" data-val="0" aria-label="0">0</button>' +
                  '<button class="calc-btn calc-btn-fn" data-action="decimal" aria-label="Decimal">.</button>' +
                  '<button class="calc-btn calc-btn-eq" data-action="equals" aria-label="Equals">=</button>' +
                '</div>' +
                '<div class="calc-history-toggle" id="calcHistoryToggle" role="button" tabindex="0" aria-expanded="false" aria-controls="calcHistoryPanel">' +
                  '<span><i class="fas fa-history" style="margin-right:5px"></i>History</span>' +
                  '<i class="fas fa-chevron-down calc-hist-chevron"></i>' +
                '</div>' +
                '<div class="calc-history-panel" id="calcHistoryPanel">' +
                  '<ul class="calc-history-list" id="calcHistoryList"></ul>' +
                  '<button class="calc-history-clear-btn" id="calcHistoryClearBtn" title="Clear history" aria-label="Clear history"><i class="fas fa-trash-alt"></i></button>' +
                '</div>' +
                '<div class="calc-footer">' +
                  '<button class="calc-copy-btn" id="calcCopyBtn"><i class="fas fa-copy"></i> Copy Result</button>' +
                  '<button class="calc-done-btn calc-hidden" id="calcDoneBtn"><i class="fas fa-check"></i> Done</button>' +
                  '<span class="calc-sci-badge" title="Coming soon"><i class="fas fa-flask" style="margin-right:4px"></i>Sci (soon)</span>' +
                '</div>' +
              '</div>' +
            '</div>';

        var wrapper = document.createElement('div');
        wrapper.innerHTML = calcHtml;
        document.body.appendChild(wrapper.firstElementChild);

        // Load calculator logic
        if (!document.querySelector('script[src="/js/calculator.js"]')) {
            var script = document.createElement('script');
            script.src = '/js/calculator.js';
            document.body.appendChild(script);
        }
    }

    /* ── Voice Assistant ───────────────────────────────────────── */

    function injectVoiceAssistantWidget() {
        if (!document.querySelector('link[href="/css/voice-assistant.css"]')) {
            var link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = '/css/voice-assistant.css';
            document.head.appendChild(link);
        }

        if (document.getElementById('voiceAssistantFab')) return;

        var fab = document.createElement('button');
        fab.id = 'voiceAssistantFab';
        fab.className = 'va-fab';
        fab.type = 'button';
        fab.title = 'Voice Assistant (Ctrl+Shift+V)';
        fab.setAttribute('aria-label', 'Voice Assistant');
        fab.innerHTML = '<i class="fas fa-microphone"></i>';
        document.body.appendChild(fab);

        var modalHtml =
            '<div class="modal fade" id="voiceAssistantModal" tabindex="-1" aria-labelledby="voiceAssistantModalTitle" aria-modal="true" role="dialog">' +
              '<div class="modal-dialog modal-dialog-centered modal-lg">' +
                '<div class="modal-content">' +
                  '<div class="modal-header">' +
                    '<h5 class="modal-title" id="voiceAssistantModalTitle"><i class="fas fa-microphone me-2"></i>Voice Assistant</h5>' +
                    '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>' +
                  '</div>' +
                  '<div class="modal-body">' +
                    '<div id="vaScreenRecording" class="va-screen active">' +
                      '<div class="va-mic-wrap">' +
                        '<div class="va-mic-pulse"></div>' +
                        '<button type="button" id="vaMicButton" class="va-mic-btn" aria-label="Start or stop recording"><i class="fas fa-microphone"></i></button>' +
                      '</div>' +
                      '<canvas id="vaWaveformCanvas" class="va-waveform" width="400" height="60"></canvas>' +
                      '<div class="va-timer" id="vaTimer">00:00</div>' +
                      '<div class="va-status" id="vaStatus">Tap the mic to start</div>' +
                      '<div class="va-transcript" id="vaTranscript"></div>' +
                      '<div class="va-followup d-none" id="vaFollowupBanner"></div>' +
                      '<div class="va-controls">' +
                        '<button type="button" class="btn btn-outline-secondary va-ctrl-btn d-none" id="vaPauseBtn"><i class="fas fa-pause"></i> Pause</button>' +
                        '<button type="button" class="btn btn-outline-secondary va-ctrl-btn d-none" id="vaResumeBtn"><i class="fas fa-play"></i> Resume</button>' +
                        '<button type="button" class="btn btn-danger va-ctrl-btn d-none" id="vaStopBtn"><i class="fas fa-stop"></i> Stop</button>' +
                        '<button type="button" class="btn btn-outline-secondary va-ctrl-btn" id="vaCancelBtn"><i class="fas fa-times"></i> Cancel</button>' +
                      '</div>' +
                    '</div>' +
                    '<div id="vaScreenConfirm" class="va-screen"></div>' +
                    '<div id="vaScreenSuccess" class="va-screen"></div>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>';
        var wrapper = document.createElement('div');
        wrapper.innerHTML = modalHtml;
        document.body.appendChild(wrapper.firstElementChild);

        if (!document.querySelector('script[src="/js/voice-assistant.js"]')) {
            var script = document.createElement('script');
            script.src = '/js/voice-assistant.js';
            document.body.appendChild(script);
        }
    }

    /* ── Notifications ─────────────────────────────────────────── */

    function injectNotificationPanel() {
        if (document.getElementById('notifDropdownPanel')) return;

        var panel = document.createElement('div');
        panel.id = 'notifDropdownPanel';
        panel.style.cssText = 'display:none; position:fixed; bottom:70px; left:90px; width:320px; max-height:400px; overflow-y:auto; background:var(--bg-modal); border:1px solid var(--border-color); border-radius:10px; box-shadow:0 8px 24px rgba(0,0,0,0.25); z-index:2000; padding:8px;';
        panel.innerHTML = '<div style="display:flex; justify-content:space-between; align-items:center; padding:6px 8px; font-weight:600; color:var(--text-primary-custom);">' +
            '<span><i class="fas fa-bell me-1"></i>Notifications</span>' +
            '<button type="button" id="notifMarkAllReadBtn" style="background:none; border:none; color:var(--accent-color); font-size:0.75rem; font-weight:600; cursor:pointer; padding:2px 4px;">Mark all as read</button>' +
            '</div><div id="notifListContainer"></div>';
        document.body.appendChild(panel);

        document.getElementById('notifMarkAllReadBtn').addEventListener('click', function (e) {
            e.stopPropagation();
            markAllNotificationsRead();
        });

        document.addEventListener('click', function (e) {
            var panelEl = document.getElementById('notifDropdownPanel');
            var bellBtn = document.getElementById('notificationBellBtn');
            if (!panelEl || panelEl.style.display === 'none') return;
            if (!panelEl.contains(e.target) && e.target !== bellBtn && !bellBtn.contains(e.target)) {
                panelEl.style.display = 'none';
            }
        });
    }

    function toggleNotificationPanel() {
        var panel = document.getElementById('notifDropdownPanel');
        if (!panel) return;
        var isOpen = panel.style.display !== 'none';
        if (isOpen) {
            panel.style.display = 'none';
            return;
        }
        panel.style.display = 'block';
        loadNotificationList();
    }

    function loadNotificationList() {
        var container = document.getElementById('notifListContainer');
        if (!container) return;
        container.innerHTML = '<p class="text-muted small p-2 mb-0">Loading…</p>';
        fetch('/api/notifications')
            .then(function (r) { return r.json(); })
            .then(function (items) {
                if (!items.length) {
                    container.innerHTML = '<p class="text-muted small p-2 mb-0">No notifications yet.</p>';
                    return;
                }
                container.innerHTML = items.slice(0, 20).map(function (n) {
                    var unreadStyle = n.isRead ? '' : 'background:var(--bg-table-stripe);';
                    return '<div style="padding:8px; border-bottom:1px solid var(--border-input); cursor:pointer; ' + unreadStyle + '" onclick="window.__markNotifRead(' + n.id + ')">' +
                        '<div style="font-weight:600; font-size:0.85rem; color:var(--text-primary-custom);">' + n.title + '</div>' +
                        '<div style="font-size:0.8rem; color:var(--text-secondary-custom);">' + n.message + '</div>' +
                        '</div>';
                }).join('');
            })
            .catch(function () {
                container.innerHTML = '<p class="text-muted small p-2 mb-0">Unable to load notifications.</p>';
            });
    }

    function markNotificationRead(id) {
        fetch('/api/notifications/' + id + '/read', { method: 'PATCH' })
            .then(function () {
                loadNotificationList();
                refreshUnreadBadge();
            })
            .catch(function () {});
    }
    window.__markNotifRead = markNotificationRead;

    function markAllNotificationsRead() {
        fetch('/api/notifications/read-all', { method: 'PATCH' })
            .then(function () {
                refreshUnreadBadge();
                var panel = document.getElementById('notifDropdownPanel');
                if (panel) panel.style.display = 'none';
            })
            .catch(function () {});
    }

    function refreshUnreadBadge() {
        fetch('/api/notifications/unread-count')
            .then(function (r) { return r.json(); })
            .then(function (data) {
                var badge = document.getElementById('notifBadge');
                if (!badge) return;
                if (data.unreadCount > 0) {
                    badge.textContent = data.unreadCount > 99 ? '99+' : data.unreadCount;
                    badge.classList.remove('d-none');
                } else {
                    badge.classList.add('d-none');
                }
            })
            .catch(function () {});
    }

    // ── Global confirmation modal ─────────────────────────────────────────────
    // Replaces the native confirm() dialog (a browser/localhost-styled alert, not part of the
    // app's UI) with an in-app Bootstrap modal so confirmations look and feel consistent with
    // the rest of FinTrack. Usage: `if (await confirmAction('Delete this?')) { ... }`.
    function injectConfirmModal() {
        if (document.getElementById('appConfirmModal')) return;
        var wrapper = document.createElement('div');
        wrapper.innerHTML =
            '<div class="modal fade" id="appConfirmModal" tabindex="-1" aria-hidden="true">' +
              '<div class="modal-dialog modal-dialog-centered">' +
                '<div class="modal-content">' +
                  '<div class="modal-header">' +
                    '<h5 class="modal-title" id="appConfirmModalTitle"><i class="fas fa-circle-question me-2"></i>Confirm</h5>' +
                    '<button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>' +
                  '</div>' +
                  '<div class="modal-body" id="appConfirmModalBody">Are you sure?</div>' +
                  '<div class="modal-footer">' +
                    '<button type="button" class="btn btn-secondary" id="appConfirmModalCancelBtn" data-bs-dismiss="modal">Cancel</button>' +
                    '<button type="button" class="btn btn-primary" id="appConfirmModalOkBtn">Confirm</button>' +
                  '</div>' +
                '</div>' +
              '</div>' +
            '</div>';
        document.body.appendChild(wrapper.firstElementChild);
    }

    function confirmAction(message, opts) {
        opts = opts || {};
        injectConfirmModal();
        var modalEl = document.getElementById('appConfirmModal');
        document.getElementById('appConfirmModalTitle').innerHTML =
            '<i class="fas fa-circle-question me-2"></i>' + (opts.title || 'Confirm');
        document.getElementById('appConfirmModalBody').textContent = message;
        var okBtn = document.getElementById('appConfirmModalOkBtn');
        okBtn.textContent = opts.confirmText || 'Confirm';
        okBtn.className = 'btn ' + (opts.confirmClass || 'btn-primary');
        var modal = bootstrap.Modal.getOrCreateInstance(modalEl);

        return new Promise(function (resolve) {
            var settled = false;
            function cleanup() {
                okBtn.removeEventListener('click', onOk);
                modalEl.removeEventListener('hidden.bs.modal', onHidden);
            }
            function onOk() {
                settled = true;
                cleanup();
                modal.hide();
                resolve(true);
            }
            function onHidden() {
                cleanup();
                if (!settled) resolve(false);
            }
            okBtn.addEventListener('click', onOk);
            modalEl.addEventListener('hidden.bs.modal', onHidden);
            modal.show();
        });
    }
    window.confirmAction = confirmAction;

    // ── User Manual / Getting Started Guide ───────────────────────────────────
    const MANUAL_CHECKLIST = [
        { id: 'accounts',     icon: 'fa-building-columns', title: 'Set up your accounts',
          desc: 'Add your Bank, Mobile Money (MFS), Cash, and Credit Card accounts with their starting balances — everything else builds on this.',
          linkLabel: 'Open Account Settings', href: '/settings?section=account-config' },
        { id: 'transactions', icon: 'fa-right-left', title: 'Log your first transactions',
          desc: 'Add income & expenses manually, import a CSV in bulk, or scan a receipt with OCR.',
          linkLabel: 'Open Transactions', href: '/transactions' },
        { id: 'recurring',    icon: 'fa-rotate', title: 'Set up recurring bills',
          desc: 'Add subscriptions, EMIs, and credit card due dates so forecasts and reminders stay accurate.',
          linkLabel: 'Open Transactions', href: '/transactions' },
        { id: 'budget',       icon: 'fa-wallet', title: "Create this month's budget",
          desc: 'Allocate a planned amount per category and track what you have left to spend, in real time.',
          linkLabel: 'Open Budget Planner', href: '/budget-planner' },
        { id: 'planner',      icon: 'fa-bullseye', title: 'Set your financial goals',
          desc: 'Plan investments, loans, renewals, and long-term savings goals in the Financial Planner.',
          linkLabel: 'Open Financial Planner', href: '/financial-planner' },
        { id: 'ai',           icon: 'fa-robot', title: 'Meet your AI Financial Coach',
          desc: 'Once you have a bit of history, ask the AI for a health score, insights, and personalized recommendations.',
          linkLabel: 'Open AI Assistant', href: '/ai-assistant' },
        { id: 'personalize',  icon: 'fa-palette', title: 'Personalize FinTrack',
          desc: 'Pick a color theme, reorder your sidebar, and choose your currency & date format.',
          linkLabel: 'Open Appearance Settings', href: '/settings?section=appearance' }
    ];

    const MANUAL_WORKFLOW = [
        { icon: 'fa-building-columns', title: 'Set Up Accounts',   desc: 'Bank, MFS, Cash & Credit Cards — the foundation everything else builds on.' },
        { icon: 'fa-right-left',       title: 'Track Transactions', desc: 'Log as you spend, import in bulk, or scan receipts — daily upkeep takes seconds.' },
        { icon: 'fa-wallet',           title: 'Plan Your Budget',   desc: 'Set a per-category budget each month and watch the dashboard track your progress.' },
        { icon: 'fa-robot',            title: 'Get AI Insights',    desc: 'The AI Coach reads your real data to flag risks and suggest where to cut back.' },
        { icon: 'fa-bullseye',         title: 'Grow Your Wealth',   desc: 'Use the Financial Planner for goals, investments, loans, and long-term renewals.' }
    ];

    const MANUAL_FEATURES = [
        { icon: 'fa-home',        title: 'Dashboard',          desc: 'Your financial command center — balances, recent activity, budget status, forecasts, and AI insights at a glance.', href: '/dashboard' },
        { icon: 'fa-right-left',  title: 'Transactions',       desc: 'Log income, expenses & transfers. Bulk CSV import/export, receipt OCR scanning, and recurring bills.', href: '/transactions' },
        { icon: 'fa-wallet',      title: 'Budget Planner',     desc: 'Set monthly, quarterly, or yearly budgets per category and track what is left to spend.', href: '/budget-planner' },
        { icon: 'fa-bullseye',    title: 'Financial Planner',  desc: 'Plan investments, loans, renewals, and long-term savings goals.', href: '/financial-planner' },
        { icon: 'fa-sticky-note', title: 'Notes',              desc: 'Jot down financial notes and reminders tied to your money management.', href: '/notes' },
        { icon: 'fa-tasks',       title: 'To-Do',              desc: 'Track financial tasks — bills to pay, documents to file, calls to make.', href: '/todos' },
        { icon: 'fa-coins',       title: 'Assets',             desc: 'Track gold and other valuable assets alongside your cash accounts.', href: '/assets' },
        { icon: 'fa-robot',       title: 'AI Financial Coach', desc: 'Chat for a health score, personalized insights, and spending recommendations.', href: '/ai-assistant' },
        { icon: 'fa-calculator',  title: 'Calculator',         desc: 'A quick on-screen calculator with history, available from any page.', action: 'calculator' },
        { icon: 'fa-bell',        title: 'Notifications',      desc: 'Budget alerts, bill reminders, and account activity — always one click away.', action: 'notifications' },
        { icon: 'fa-cog',         title: 'Settings',           desc: 'Manage accounts, categories, profile, appearance, and sidebar layout.', href: '/settings' }
    ];

    function getManualChecklistKey() { return getUserStorageKey('finzin_manual_checklist'); }
    function getManualSeenKey() { return getUserStorageKey('finzin_manual_seen'); }

    function loadManualChecklistDone() {
        try { return JSON.parse(localStorage.getItem(getManualChecklistKey()) || '[]'); } catch (e) { return []; }
    }
    function saveManualChecklistDone(arr) {
        try { localStorage.setItem(getManualChecklistKey(), JSON.stringify(arr)); } catch (e) {}
    }

    function injectUserManualModal() {
        if (document.getElementById('userManualModal')) return;

        var featureCardsHtml = MANUAL_FEATURES.map(function (f) {
            return '<div class="um-feature-card" data-href="' + (f.href || '') + '" data-action="' + (f.action || '') + '" tabindex="0" role="button" aria-label="' + f.title + '">' +
                '<div class="um-feature-icon"><i class="fas ' + f.icon + '"></i></div>' +
                '<div class="um-feature-title">' + f.title + '</div>' +
                '<div class="um-feature-desc">' + f.desc + '</div>' +
                '<span class="um-feature-link">Open <i class="fas fa-arrow-right ms-1"></i></span>' +
                '</div>';
        }).join('');

        var workflowHtml = MANUAL_WORKFLOW.map(function (w, i) {
            var arrow = i < MANUAL_WORKFLOW.length - 1 ? '<div class="um-flow-arrow"><i class="fas fa-chevron-right"></i></div>' : '';
            return '<div class="um-flow-step" style="--um-d:' + i + '">' +
                '<div class="um-flow-num">' + (i + 1) + '</div>' +
                '<div class="um-flow-icon"><i class="fas ' + w.icon + '"></i></div>' +
                '<div class="um-flow-title">' + w.title + '</div>' +
                '<div class="um-flow-desc">' + w.desc + '</div>' +
                '</div>' + arrow;
        }).join('');

        var wrapper = document.createElement('div');
        wrapper.innerHTML = `
        <div class="modal fade" id="userManualModal" tabindex="-1" aria-labelledby="userManualModalTitle" aria-modal="true" role="dialog">
          <div class="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content">
              <div class="modal-header">
                <h5 class="modal-title" id="userManualModalTitle"><i class="fas fa-book-open me-2"></i>User Manual &amp; Getting Started</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
              </div>
              <div class="modal-body p-0">
                <div class="um-layout">
                  <div class="um-nav" role="tablist" aria-orientation="vertical">
                    <button class="um-nav-item active" data-um-tab="welcome" type="button" role="tab"><i class="fas fa-hand-sparkles"></i><span>Welcome</span></button>
                    <button class="um-nav-item" data-um-tab="checklist" type="button" role="tab"><i class="fas fa-list-check"></i><span>Getting Started</span><span class="um-nav-badge" id="umChecklistBadge"></span></button>
                    <button class="um-nav-item" data-um-tab="workflow" type="button" role="tab"><i class="fas fa-diagram-project"></i><span>How It Works</span></button>
                    <button class="um-nav-item" data-um-tab="features" type="button" role="tab"><i class="fas fa-shapes"></i><span>Features &amp; Quick Links</span></button>
                  </div>
                  <div class="um-content">
                    <div class="um-pane active" data-um-pane="welcome">
                      <div class="um-welcome-hero">
                        <i class="fas fa-seedling um-hero-icon"></i>
                        <h4>Welcome to FinTrack!</h4>
                        <p class="text-muted">Your all-in-one personal finance companion — track spending, plan budgets, manage assets, and get AI-powered coaching, all in one place.</p>
                      </div>
                      <div class="um-welcome-grid">
                        <div class="um-welcome-item"><i class="fas fa-bolt"></i><div><strong>Built for speed</strong><span>Bulk import, receipt scanning, and quick-add shortcuts.</span></div></div>
                        <div class="um-welcome-item"><i class="fas fa-chart-line"></i><div><strong>Always in view</strong><span>One dashboard for balances, budgets, and forecasts.</span></div></div>
                        <div class="um-welcome-item"><i class="fas fa-robot"></i><div><strong>AI on your side</strong><span>Personalized insights as soon as you have a bit of history.</span></div></div>
                      </div>
                      <button class="btn btn-primary mt-2" id="umGoToChecklistBtn" type="button"><i class="fas fa-list-check me-1"></i>Start the Getting Started Checklist <i class="fas fa-arrow-right ms-1"></i></button>
                    </div>
                    <div class="um-pane" data-um-pane="checklist">
                      <div class="um-checklist-head">
                        <h5><i class="fas fa-flag-checkered me-2"></i>What to set up first</h5>
                        <p class="text-muted small mb-2">New here? Follow this order — each step builds on the last, so your budgets and AI insights stay accurate from day one.</p>
                        <div class="um-progress-track"><div class="um-progress-fill" id="umProgressFill" style="width:0%"></div></div>
                        <div class="um-progress-label" id="umProgressLabel">0 of ${MANUAL_CHECKLIST.length} complete</div>
                      </div>
                      <div class="um-checklist" id="umChecklist"></div>
                    </div>
                    <div class="um-pane" data-um-pane="workflow">
                      <h5><i class="fas fa-diagram-project me-2"></i>How FinTrack Fits Together</h5>
                      <p class="text-muted small">The typical flow through the app, month after month:</p>
                      <div class="um-flow">${workflowHtml}</div>
                      <div class="um-flow-note"><i class="fas fa-lightbulb me-2"></i>At the start of each month: review last month in the AI Coach, set a fresh Budget Plan, then just keep logging as you go — the dashboard and forecasts stay current automatically.</div>
                    </div>
                    <div class="um-pane" data-um-pane="features">
                      <h5><i class="fas fa-shapes me-2"></i>Features &amp; Quick Links</h5>
                      <p class="text-muted small">Click any card to jump straight there.</p>
                      <div class="um-feature-grid">${featureCardsHtml}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>`;
        document.body.appendChild(wrapper.firstElementChild);
        wireUserManualModal();
    }

    function wireUserManualModal() {
        var modalEl = document.getElementById('userManualModal');
        if (!modalEl) return;

        modalEl.querySelectorAll('.um-nav-item').forEach(function (btn) {
            btn.addEventListener('click', function () { switchManualTab(btn.dataset.umTab); });
        });

        var goBtn = document.getElementById('umGoToChecklistBtn');
        if (goBtn) goBtn.addEventListener('click', function () { switchManualTab('checklist'); });

        modalEl.querySelectorAll('.um-feature-card').forEach(function (card) {
            function activate() {
                var action = card.dataset.action;
                var href = card.dataset.href;
                if (action === 'calculator') {
                    bootstrap.Modal.getInstance(modalEl).hide();
                    if (typeof window.toggleCalc === 'function') window.toggleCalc();
                } else if (action === 'notifications') {
                    bootstrap.Modal.getInstance(modalEl).hide();
                    toggleNotificationPanel();
                } else if (href) {
                    window.location.href = href;
                }
            }
            card.addEventListener('click', activate);
            card.addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); activate(); }
            });
        });
    }

    function switchManualTab(tab) {
        var modalEl = document.getElementById('userManualModal');
        if (!modalEl) return;
        modalEl.querySelectorAll('.um-nav-item').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.umTab === tab);
        });
        modalEl.querySelectorAll('.um-pane').forEach(function (pane) {
            pane.classList.toggle('active', pane.dataset.umPane === tab);
        });
    }

    function renderManualChecklist() {
        var container = document.getElementById('umChecklist');
        if (!container) return;
        var done = loadManualChecklistDone();
        container.innerHTML = MANUAL_CHECKLIST.map(function (step, i) {
            var isDone = done.indexOf(step.id) !== -1;
            return '<div class="um-check-item' + (isDone ? ' done' : '') + '" data-step="' + step.id + '">' +
                '<button type="button" class="um-check-box" aria-label="Mark ' + step.title + '" aria-pressed="' + isDone + '"><i class="fas fa-check"></i></button>' +
                '<div class="um-check-num">' + (i + 1) + '</div>' +
                '<div class="um-check-icon"><i class="fas ' + step.icon + '"></i></div>' +
                '<div class="um-check-text"><div class="um-check-title">' + step.title + '</div><div class="um-check-desc">' + step.desc + '</div></div>' +
                '<a href="' + step.href + '" class="btn btn-sm btn-outline-primary um-check-link">' + step.linkLabel + ' <i class="fas fa-arrow-right ms-1"></i></a>' +
                '</div>';
        }).join('');

        container.querySelectorAll('.um-check-box').forEach(function (box) {
            box.addEventListener('click', function () {
                var item = box.closest('.um-check-item');
                toggleManualStep(item.dataset.step, item);
            });
        });

        updateManualProgress(done);
    }

    function toggleManualStep(stepId, itemEl) {
        var done = loadManualChecklistDone();
        var idx = done.indexOf(stepId);
        if (idx === -1) { done.push(stepId); } else { done.splice(idx, 1); }
        saveManualChecklistDone(done);

        var nowDone = done.indexOf(stepId) !== -1;
        if (itemEl) {
            itemEl.classList.toggle('done', nowDone);
            var box = itemEl.querySelector('.um-check-box');
            if (box) box.setAttribute('aria-pressed', nowDone);
            if (nowDone) {
                itemEl.classList.add('um-pop');
                setTimeout(function () { itemEl.classList.remove('um-pop'); }, 400);
            }
        }
        updateManualProgress(done);
    }

    function updateManualProgress(done) {
        var total = MANUAL_CHECKLIST.length;
        var count = done.length;
        var pct = total ? Math.round((count / total) * 100) : 0;
        var fill = document.getElementById('umProgressFill');
        var label = document.getElementById('umProgressLabel');
        var navBadge = document.getElementById('umChecklistBadge');
        if (fill) fill.style.width = pct + '%';
        if (label) label.textContent = count + ' of ' + total + ' complete' + (count === total ? ' 🎉' : '');
        if (navBadge) navBadge.textContent = count > 0 ? (count + '/' + total) : '';
    }

    function updateManualBadgeVisibility() {
        var dot = document.getElementById('manualBadgeDot');
        if (!dot) return;
        var seen = localStorage.getItem(getManualSeenKey()) === 'true';
        dot.classList.toggle('d-none', seen);
    }

    function markManualSeen() {
        try { localStorage.setItem(getManualSeenKey(), 'true'); } catch (e) {}
        updateManualBadgeVisibility();
    }

    function openUserManual() {
        injectUserManualModal();
        renderManualChecklist();
        markManualSeen();
        var modalEl = document.getElementById('userManualModal');
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
    window.openUserManual = openUserManual;

    // ── Sidebar Customizer Modal ──────────────────────────────────────────────
    function injectSidebarCustomizerModal() {
        if (document.getElementById('sidebarCustomizerModal')) return;
        var modal = document.createElement('div');
        modal.id = 'sidebarCustomizerModal';
        modal.className = 'modal fade';
        modal.setAttribute('tabindex', '-1');
        modal.setAttribute('aria-labelledby', 'sidebarCustomizerTitle');
        modal.setAttribute('aria-modal', 'true');
        modal.setAttribute('role', 'dialog');
        modal.innerHTML = `
        <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title" id="sidebarCustomizerTitle"><i class="fas fa-sliders-h me-2"></i>Customize Sidebar</h5>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
              <p class="text-muted small mb-3">Drag modules to reorder. Toggle visibility. Pin favorites to the top. <strong>Home</strong>, <strong>Settings</strong>, and <strong>Logout</strong> cannot be hidden.</p>
              <div class="input-group mb-3">
                <span class="input-group-text"><i class="fas fa-search"></i></span>
                <input type="text" id="customizerSearch" class="form-control" placeholder="Search modules…" aria-label="Search modules">
              </div>
              <div id="customizerList" class="list-group" role="list" aria-label="Sidebar modules">
              </div>
            </div>
            <div class="modal-footer d-flex justify-content-between">
              <button type="button" class="btn btn-outline-danger btn-sm" id="customizerRestoreBtn">
                <i class="fas fa-undo me-1"></i>Restore Defaults
              </button>
              <div class="d-flex gap-2">
                <button type="button" class="btn btn-secondary btn-sm" data-bs-dismiss="modal">Cancel</button>
                <button type="button" class="btn btn-primary btn-sm" id="customizerSaveBtn">
                  <i class="fas fa-save me-1"></i>Save Changes
                </button>
              </div>
            </div>
          </div>
        </div>`;
        document.body.appendChild(modal);
    }

    function openSidebarCustomizer() {
        injectSidebarCustomizerModal();
        var prefs = loadSidebarPrefs();
        var prefMap = {};
        prefs.forEach(function(p) { prefMap[p.id] = p; });

        // Build working copy
        var working = SIDEBAR_MODULES.map(function(m, i) {
            var p = prefMap[m.id] || { id: m.id, visible: true, pinned: false, displayOrder: i };
            return { id: m.id, visible: !!p.visible, pinned: !!p.pinned, displayOrder: p.displayOrder || i,
                     label: m.label, icon: m.icon, locked: !!m.locked };
        });
        working.sort(function(a,b) { return a.displayOrder - b.displayOrder; });

        function renderList(filter) {
            var list = document.getElementById('customizerList');
            list.innerHTML = '';
            working.forEach(function(item, idx) {
                if (filter && item.label.toLowerCase().indexOf(filter.toLowerCase()) === -1) return;
                var li = document.createElement('div');
                li.className = 'list-group-item list-group-item-action d-flex align-items-center gap-3 px-3 py-2';
                li.setAttribute('draggable', 'true');
                li.setAttribute('data-id', item.id);
                li.setAttribute('role', 'listitem');
                li.style.cursor = item.locked ? 'default' : 'grab';

                var dragHandle = item.locked ? '<span style="width:16px;display:inline-block;"></span>' :
                    '<span class="drag-handle text-muted" title="Drag to reorder" style="cursor:grab;font-size:1.1rem;"><i class="fas fa-grip-vertical"></i></span>';

                var pinClass = item.pinned ? 'text-warning' : 'text-muted';
                var pinTitle = item.pinned ? 'Unpin' : 'Pin to top';
                var pinBtn = item.locked ? '<span style="width:28px;display:inline-block;"></span>' :
                    '<button class="btn btn-sm p-0 border-0 pin-btn ' + pinClass + '" title="' + pinTitle + '" style="width:28px;" aria-label="' + pinTitle + '" aria-pressed="' + item.pinned + '">' +
                    '<i class="fas fa-thumbtack"></i></button>';

                var toggleDisabled = item.locked ? 'disabled title="Cannot hide"' : '';
                var toggleChecked = item.visible ? 'checked' : '';
                var toggleHtml = '<div class="form-check form-switch mb-0" style="padding-left:0;">' +
                    '<input class="form-check-input vis-toggle" type="checkbox" ' + toggleChecked + ' ' + toggleDisabled +
                    ' id="vis_' + item.id + '" aria-label="Toggle visibility of ' + item.label + '" style="cursor:' + (item.locked ? 'not-allowed' : 'pointer') + ';margin-left:0;"></div>';

                li.innerHTML = dragHandle + '<i class="' + item.icon + ' sidebar-icon me-1" style="width:18px;text-align:center;"></i>' +
                    '<span class="flex-grow-1 fw-medium">' + item.label + (item.locked ? ' <span class="badge bg-secondary ms-1" style="font-size:0.65rem;">locked</span>' : '') + '</span>' +
                    pinBtn + toggleHtml;

                // Visibility toggle
                var toggle = li.querySelector('.vis-toggle');
                if (toggle && !item.locked) {
                    toggle.addEventListener('change', function() {
                        item.visible = this.checked;
                        li.classList.toggle('opacity-50', !this.checked);
                    });
                }
                if (!item.visible) li.classList.add('opacity-50');

                // Pin button
                var pinBtnEl = li.querySelector('.pin-btn');
                if (pinBtnEl) {
                    pinBtnEl.addEventListener('click', function() {
                        item.pinned = !item.pinned;
                        renderList(document.getElementById('customizerSearch').value);
                    });
                }

                // Drag & drop
                li.addEventListener('dragstart', function(e) {
                    e.dataTransfer.effectAllowed = 'move';
                    e.dataTransfer.setData('text/plain', item.id);
                    li.classList.add('dragging');
                });
                li.addEventListener('dragend', function() { li.classList.remove('dragging'); });
                li.addEventListener('dragover', function(e) {
                    e.preventDefault(); e.dataTransfer.dropEffect = 'move';
                    li.classList.add('drag-over');
                });
                li.addEventListener('dragleave', function() { li.classList.remove('drag-over'); });
                li.addEventListener('drop', function(e) {
                    e.preventDefault(); li.classList.remove('drag-over');
                    var fromId = e.dataTransfer.getData('text/plain');
                    var fromIdx = working.findIndex(function(x) { return x.id === fromId; });
                    var toIdx   = working.findIndex(function(x) { return x.id === item.id; });
                    if (fromIdx !== -1 && toIdx !== -1 && fromIdx !== toIdx) {
                        var moved = working.splice(fromIdx, 1)[0];
                        working.splice(toIdx, 0, moved);
                        working.forEach(function(x, i) { x.displayOrder = i; });
                        renderList(document.getElementById('customizerSearch').value);
                    }
                });

                list.appendChild(li);
            });
        }

        renderList('');

        document.getElementById('customizerSearch').oninput = function() { renderList(this.value); };

        document.getElementById('customizerSaveBtn').onclick = function() {
            working.forEach(function(x, i) { x.displayOrder = i; });
            saveSidebarPrefs(working.map(function(x) {
                return { id: x.id, visible: x.visible, pinned: x.pinned, displayOrder: x.displayOrder };
            }));
            bootstrap.Modal.getInstance(document.getElementById('sidebarCustomizerModal')).hide();
            if (typeof window.reloadSidebar === 'function') window.reloadSidebar();
        };

        document.getElementById('customizerRestoreBtn').onclick = function() {
            if (!confirm('Restore the default sidebar layout? This will remove all customizations.')) return;
            var defaults = getDefaultPrefs();
            saveSidebarPrefs(defaults);
            fetch('/api/sidebar-preferences', { method: 'DELETE' }).catch(function(){});
            bootstrap.Modal.getInstance(document.getElementById('sidebarCustomizerModal')).hide();
            if (typeof window.reloadSidebar === 'function') window.reloadSidebar();
        };

        var bsModal = new bootstrap.Modal(document.getElementById('sidebarCustomizerModal'));
        bsModal.show();
    }

    function init() {
        // Apply theme immediately to prevent flash of wrong theme
        initTheme();
        // Reconcile with the server's per-user value (e.g. this is a new browser/device)
        syncAppearanceFromServer();

        const sidebar = buildSidebar();

        // Overlay for mobile
        const overlay = document.createElement('div');
        overlay.id = 'sidebarOverlay';
        overlay.className = 'sidebar-overlay';
        overlay.addEventListener('click', closeMobileSidebar);

        // Mobile hamburger (fixed top-left when sidebar is hidden)
        const mobileToggle = document.createElement('button');
        mobileToggle.id = 'sidebarMobileToggle';
        mobileToggle.className = 'sidebar-mobile-toggle';
        mobileToggle.setAttribute('aria-label', 'Open navigation');
        mobileToggle.innerHTML = '<i class="fas fa-bars"></i>';
        mobileToggle.addEventListener('click', openMobileSidebar);

        // Wrap all existing body content in #main-content
        const mainContent = document.createElement('div');
        mainContent.id = 'main-content';
        while (document.body.firstChild) {
            mainContent.appendChild(document.body.firstChild);
        }

        document.body.classList.add('has-sidebar');
        document.body.appendChild(sidebar);
        document.body.appendChild(mainContent);
        document.body.appendChild(overlay);
        document.body.appendChild(mobileToggle);

        // Apply saved collapse state (default: expanded)
        const collapsed = localStorage.getItem(COLLAPSED_KEY) === 'true';
        applyCollapseState(sidebar, collapsed);

        // Highlight active link
        setActiveItem(sidebar);

        // Wire up buttons
        document.getElementById('sidebarToggleBtn').addEventListener('click', toggleSidebar);
        document.getElementById('themeToggleBtn').addEventListener('click', toggleTheme);
        document.getElementById('sidebarProfileBtn').addEventListener('click', openProfileModal);

        document.getElementById('sidebarLogoutBtn').addEventListener('click', function (e) {
            e.preventDefault();
            if (typeof logout === 'function') {
                logout();
            } else {
                localStorage.removeItem('token');
                localStorage.removeItem('user');
                window.location.href = '/login';
            }
        });
        injectProfileModal();
        injectCalculator();
        injectVoiceAssistantWidget();
        injectNotificationPanel();
        injectConfirmModal();
        injectUserManualModal();

        // Wire up calculator sidebar button
        var calcBtn = document.getElementById('calcSidebarBtn');
        if (calcBtn) {
            calcBtn.addEventListener('click', function () {
                if (typeof window.toggleCalc === 'function') {
                    window.toggleCalc();
                }
            });
        }

        // Wire up notification bell
        var notifBtn = document.getElementById('notificationBellBtn');
        if (notifBtn) {
            notifBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                toggleNotificationPanel();
            });
        }
        refreshUnreadBadge();

        // Wire up User Manual button
        var manualBtn = document.getElementById('userManualBtn');
        if (manualBtn) {
            manualBtn.addEventListener('click', function (e) {
                e.stopPropagation();
                openUserManual();
            });
        }
        updateManualBadgeVisibility();

        // Wire up Voice Assistant FAB + keyboard shortcut
        var voiceFab = document.getElementById('voiceAssistantFab');
        if (voiceFab) {
            voiceFab.addEventListener('click', function () {
                if (typeof window.openVoiceAssistant === 'function') window.openVoiceAssistant();
            });
        }
        document.addEventListener('keydown', function (e) {
            if (e.ctrlKey && e.shiftKey && (e.key === 'V' || e.key === 'v')) {
                e.preventDefault();
                if (typeof window.openVoiceAssistant === 'function') window.openVoiceAssistant();
            }
        });

        // First-time visitors landing on the dashboard get a one-time automatic
        // intro to the User Manual, answering "what should I set up first?"
        var landingPath = window.location.pathname.replace(/^\//, '');
        if ((landingPath === 'dashboard' || landingPath === '') && localStorage.getItem(getManualSeenKey()) !== 'true') {
            setTimeout(function () { openUserManual(); }, 1200);
        }

        // Apply compact mode
        if (localStorage.getItem(COMPACT_KEY) === 'true') {
            document.body.classList.add('sidebar-compact');
        }

        // Expose customizer globally
        window.openSidebarCustomizer = openSidebarCustomizer;
        window.reloadSidebar = function() {
            var old = document.getElementById('sidebar');
            if (old) {
                var newSidebar = buildSidebar();
                old.parentNode.replaceChild(newSidebar, old);
                initWireup(newSidebar);
            }
        };
    }

    function initWireup(sidebar) {
        setActiveItem(sidebar);
        var t = document.getElementById('sidebarToggleBtn');
        if (t) t.addEventListener('click', toggleSidebar);
        var th = document.getElementById('themeToggleBtn');
        if (th) th.addEventListener('click', toggleTheme);
        var pr = document.getElementById('sidebarProfileBtn');
        if (pr) pr.addEventListener('click', openProfileModal);
        var lo = document.getElementById('sidebarLogoutBtn');
        if (lo) lo.addEventListener('click', function(e) {
            e.preventDefault();
            if (typeof logout === 'function') logout();
            else { localStorage.removeItem('token'); localStorage.removeItem('user'); window.location.href = '/login'; }
        });
        var calcBtn = document.getElementById('calcSidebarBtn');
        if (calcBtn) calcBtn.addEventListener('click', function() { if (typeof window.toggleCalc === 'function') window.toggleCalc(); });
        var notifBtn = document.getElementById('notificationBellBtn');
        if (notifBtn) notifBtn.addEventListener('click', function(e) { e.stopPropagation(); toggleNotificationPanel(); });
        refreshUnreadBadge();
        var manualBtn = document.getElementById('userManualBtn');
        if (manualBtn) manualBtn.addEventListener('click', function(e) { e.stopPropagation(); openUserManual(); });
        updateManualBadgeVisibility();
    }

    // Expose for debugging/external use
    window.toggleSidebar = toggleSidebar;
    window.toggleTheme = toggleTheme;
    window.openMobileSidebar = openMobileSidebar;
    window.closeMobileSidebar = closeMobileSidebar;

    document.addEventListener('DOMContentLoaded', init);
})();
