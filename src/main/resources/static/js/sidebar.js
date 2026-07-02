(function () {
    'use strict';

    const COLLAPSED_KEY = 'sidebar_collapsed';
    const THEME_KEY = 'fintrack_theme';

    // Apply theme immediately on script load to prevent flash of wrong theme
    (function applyThemeEarly() {
        const saved = localStorage.getItem(THEME_KEY);
        const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
        const theme = saved || (prefersDark ? 'dark' : 'light');
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
        // Initial icon depends on current (or preferred) theme
        const isDark = (localStorage.getItem(THEME_KEY) || getPreferredTheme()) === 'dark';
        const themeIcon  = isDark ? 'fas fa-sun sidebar-icon' : 'fas fa-moon sidebar-icon';
        const themeLabel = isDark ? 'Light Mode' : 'Dark Mode';

        const aside = document.createElement('aside');
        aside.id = 'sidebar';
        aside.innerHTML = `
            <div class="sidebar-header">
                <div class="sidebar-brand">
                    <i class="fas fa-wallet sidebar-icon"></i>
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
                    <div class="sidebar-avatar" id="sidebarAvatar">${initials}</div>
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
        } else {
            window.location.href = '/profile';
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
    }

    // Expose for debugging/external use
    window.toggleSidebar = toggleSidebar;
    window.toggleTheme = toggleTheme;
    window.openMobileSidebar = openMobileSidebar;
    window.closeMobileSidebar = closeMobileSidebar;

    document.addEventListener('DOMContentLoaded', init);
})();
