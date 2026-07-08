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

    // ── Apply saved color theme immediately (before first paint)
    (function applyColorThemeEarly() {
        var ct = localStorage.getItem(getUserStorageKey('fintrack_color_theme')) || 'forest';
        if (ct !== 'forest') {
            document.documentElement.setAttribute('data-color-theme', ct);
        }
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
        // Initial icon depends on current (or preferred) theme
        const isDark = (localStorage.getItem(THEME_KEY) || getPreferredTheme()) === 'dark';
        const themeIcon  = isDark ? 'fas fa-sun sidebar-icon' : 'fas fa-moon sidebar-icon';
        const themeLabel = isDark ? 'Light Mode' : 'Dark Mode';

        const aside = document.createElement('aside');
        aside.id = 'sidebar';
        aside.innerHTML = `
            <div class="sidebar-header">
                <div class="sidebar-brand">
                    <img src="/images/logo.png" alt="FinTrack" class="sidebar-brand-logo">
                    <span class="sidebar-label">FinTrack</span>
                </div>
                <button class="sidebar-toggle-btn" id="sidebarToggleBtn" title="Toggle sidebar">
                    <i class="fas fa-chevron-left" id="sidebarToggleIcon"></i>
                </button>
            </div>

            <nav class="sidebar-nav">
                <a href="/dashboard" class="sidebar-item" data-path="dashboard" title="Home">
                    <i class="fas fa-home sidebar-icon"></i>
                    <span class="sidebar-label">Home</span>
                </a>
                <a href="/transactions" class="sidebar-item" data-path="transactions" title="Transactions">
                    <i class="fas fa-exchange-alt sidebar-icon"></i>
                    <span class="sidebar-label">Transactions</span>
                </a>
                <a href="/notes" class="sidebar-item" data-path="notes" title="Notes">
                    <i class="fas fa-sticky-note sidebar-icon"></i>
                    <span class="sidebar-label">Notes</span>
                </a>
                <a href="/todos" class="sidebar-item" data-path="todos" title="To-Do">
                    <i class="fas fa-tasks sidebar-icon"></i>
                    <span class="sidebar-label">To-Do</span>
                </a>
                <button class="sidebar-item" id="calcSidebarBtn" title="Calculator" style="background:none;border:none;width:100%;text-align:left;">
                    <i class="fas fa-calculator sidebar-icon"></i>
                    <span class="sidebar-label">Calculator</span>
                </button>
                <a href="/settings" class="sidebar-item" data-path="settings" title="Settings">
                    <i class="fas fa-cog sidebar-icon"></i>
                    <span class="sidebar-label">Settings</span>
                </a>
            </nav>

            <div class="sidebar-spacer"></div>

            <div class="sidebar-bottom">
                <button class="sidebar-item sidebar-theme-toggle" id="themeToggleBtn" title="Toggle theme">
                    <span class="theme-icon-wrap">
                        <i class="${themeIcon}" id="themeToggleIcon"></i>
                    </span>
                    <span class="sidebar-label" id="themeToggleLbl">${themeLabel}</span>
                </button>
                <div class="sidebar-item sidebar-profile" id="sidebarProfileBtn" title="${userName}">
                    <div class="sidebar-avatar" id="sidebarAvatar" style="overflow:hidden;">${profilePicUrl ? `<img src="${profilePicUrl}" alt="avatar" style="width:100%;height:100%;object-fit:cover;border-radius:50%;">` : initials}</div>
                    <span class="sidebar-label" id="sidebarUserName">${userName}</span>
                </div>
                <a href="#" class="sidebar-item sidebar-logout" id="sidebarLogoutBtn" title="Logout">
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

    function init() {
        // Apply theme immediately to prevent flash of wrong theme
        initTheme();

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

        // Wire up calculator sidebar button
        var calcBtn = document.getElementById('calcSidebarBtn');
        if (calcBtn) {
            calcBtn.addEventListener('click', function () {
                if (typeof window.toggleCalc === 'function') {
                    window.toggleCalc();
                }
            });
        }
    }

    // Expose for debugging/external use
    window.toggleSidebar = toggleSidebar;
    window.toggleTheme = toggleTheme;
    window.openMobileSidebar = openMobileSidebar;
    window.closeMobileSidebar = closeMobileSidebar;

    document.addEventListener('DOMContentLoaded', init);
})();
