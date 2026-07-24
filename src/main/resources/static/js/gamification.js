(function () {
    'use strict';

    var allAchievements = [];
    var summaryData = null;

    function escHtml(s) {
        if (s === null || s === undefined) return '';
        return String(s).replace(/[&<>"']/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
        });
    }

    window.switchGamTab = function (tab) {
        document.querySelectorAll('.gam-tab-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });
        document.querySelectorAll('.gam-tab-pane').forEach(function (pane) {
            pane.classList.toggle('active', pane.id === 'gam-tab-' + tab);
        });
    };

    function tierClass(tierColor) {
        var known = ['bronze', 'silver', 'gold', 'diamond'];
        var t = (tierColor || '').toLowerCase();
        return known.indexOf(t) !== -1 ? 'tier-' + t : 'tier-bronze';
    }

    function progressFraction(a) {
        return a.progressTarget ? (a.progressCurrent / a.progressTarget) : 0;
    }

    function achievementCardHtml(a) {
        var pct = a.progressTarget ? Math.max(0, Math.min(100, Math.round(progressFraction(a) * 100))) : 0;
        var locked = a.status !== 'UNLOCKED';
        var shownCurrent = Math.min(a.progressCurrent || 0, a.progressTarget || 0);
        return '<div class="gam-achv-card' + (locked ? ' locked' : '') + '">' +
            '<div class="gam-achv-icon ' + tierClass(a.tierColor) + '"><i class="fas ' + escHtml(a.icon || 'fa-trophy') + '"></i></div>' +
            '<div class="gam-achv-body">' +
                '<div class="gam-achv-name">' + escHtml(a.name) +
                    (a.isMilestone ? ' <i class="fas fa-star text-warning" title="Milestone" style="font-size:0.7rem;"></i>' : '') +
                '</div>' +
                '<div class="gam-achv-desc">' + escHtml(a.description) + '</div>' +
                '<div class="gam-achv-progress-track"><div class="gam-achv-progress-fill" style="width:' + pct + '%"></div></div>' +
                '<div class="gam-achv-meta">' +
                    '<span>' + shownCurrent + ' / ' + a.progressTarget + '</span>' +
                    (locked ? '<span>' + a.xpReward + ' XP</span>' : '<span class="gam-achv-unlocked-badge"><i class="fas fa-check-circle"></i> Unlocked</span>') +
                '</div>' +
            '</div>' +
        '</div>';
    }

    function renderOverview() {
        if (!summaryData) return;
        var s = summaryData;
        document.getElementById('gamOverviewLevelBadge').textContent = s.currentLevel;
        document.getElementById('gamOverviewLevelName').textContent = s.currentLevelName;
        document.getElementById('gamOverviewXpLabel').textContent = s.totalXp + ' XP';
        var span = Math.max(1, s.nextLevelXp ? (s.nextLevelXp - s.currentLevelXp) : 1);
        var pct = s.nextLevelXp ? Math.max(0, Math.min(100, Math.round(((s.totalXp - s.currentLevelXp) / span) * 100))) : 100;
        document.getElementById('gamOverviewProgressFill').style.width = pct + '%';
        document.getElementById('gamOverviewNextLevel').textContent = s.nextLevelName
            ? (Math.max(0, s.nextLevelXp - s.totalXp) + ' XP to ' + s.nextLevelName)
            : 'Max level reached!';
        document.getElementById('gamOverviewStreak').textContent = s.currentStreak;
        document.getElementById('gamOverviewAchvCount').textContent = s.achievementsUnlocked + '/' + s.achievementsTotal;

        var nearest = allAchievements
            .filter(function (a) { return a.status !== 'UNLOCKED'; })
            .sort(function (a, b) { return progressFraction(b) - progressFraction(a); })
            .slice(0, 6);
        var container = document.getElementById('gamOverviewNearest');
        container.innerHTML = nearest.length
            ? nearest.map(achievementCardHtml).join('')
            : '<p class="text-muted small">Nothing close yet — keep going!</p>';
    }

    function renderCategoryFilters() {
        var categories = [];
        allAchievements.forEach(function (a) { if (categories.indexOf(a.category) === -1) categories.push(a.category); });
        var container = document.getElementById('gamCategoryFilters');
        var html = '<button class="gam-cat-btn active" data-cat="" onclick="window.__gamFilterCategory(this, null)">All</button>';
        html += categories.map(function (c) {
            return '<button class="gam-cat-btn" data-cat="' + escHtml(c) + '" onclick="window.__gamFilterCategory(this, \'' +
                String(c).replace(/'/g, "\\'") + '\')">' + escHtml(c) + '</button>';
        }).join('');
        container.innerHTML = html;
    }

    function renderAchievementsGrid(filterCategory) {
        var list = filterCategory ? allAchievements.filter(function (a) { return a.category === filterCategory; }) : allAchievements;
        var container = document.getElementById('gamAchievementsGrid');
        container.innerHTML = list.length ? list.map(achievementCardHtml).join('') : '<p class="text-muted small">No achievements in this category yet.</p>';
    }

    window.__gamFilterCategory = function (btn, category) {
        document.querySelectorAll('.gam-cat-btn').forEach(function (b) { b.classList.remove('active'); });
        btn.classList.add('active');
        renderAchievementsGrid(category);
    };

    function renderBadges() {
        var unlockedCount = allAchievements.filter(function (a) { return a.status === 'UNLOCKED'; }).length;
        var pct = allAchievements.length ? Math.round((unlockedCount / allAchievements.length) * 100) : 0;
        document.getElementById('gamBadgeCompletionPct').textContent = pct + '%';
        var container = document.getElementById('gamBadgeGrid');
        container.innerHTML = allAchievements.map(function (a) {
            var locked = a.status !== 'UNLOCKED';
            return '<div class="gam-badge-tile' + (locked ? ' locked' : '') + '" title="' + escHtml(a.name) + ': ' + escHtml(a.description) + '">' +
                '<div class="gam-badge-icon ' + tierClass(a.tierColor) + '"><i class="fas ' + escHtml(a.icon || 'fa-trophy') + '"></i></div>' +
                '<div class="gam-badge-name">' + escHtml(a.name) + '</div>' +
            '</div>';
        }).join('');
    }

    function renderStatistics() {
        if (!summaryData) return;
        document.getElementById('gamStatTotalXp').textContent = summaryData.totalXp;
        document.getElementById('gamStatLevel').textContent = summaryData.currentLevel + ' · ' + summaryData.currentLevelName;
        document.getElementById('gamStatAchievements').textContent = summaryData.achievementsUnlocked + '/' + summaryData.achievementsTotal;
        document.getElementById('gamStatStreak').textContent = summaryData.currentStreak + ' / ' + summaryData.longestStreak;
    }

    function prettyReason(reason) {
        if (!reason) return 'XP earned';
        return reason.split('_').map(function (w) { return w.charAt(0) + w.slice(1).toLowerCase(); }).join(' ');
    }

    function loadXpHistory() {
        fetch('/api/gamification/history').then(function (r) { return r.json(); }).then(function (items) {
            var container = document.getElementById('gamXpHistoryList');
            if (!items.length) { container.innerHTML = '<p class="text-muted small">No XP earned yet.</p>'; return; }
            container.innerHTML = items.map(function (h) {
                var when = h.createdAt ? new Date(h.createdAt).toLocaleString() : '';
                return '<div class="d-flex justify-content-between align-items-center py-2 border-bottom">' +
                    '<div><div class="small fw-semibold">' + escHtml(prettyReason(h.reason)) + '</div>' +
                    '<div class="text-muted" style="font-size:0.75rem">' + escHtml(when) + '</div></div>' +
                    '<span class="badge bg-success">+' + h.amount + ' XP</span>' +
                '</div>';
            }).join('');
        }).catch(function () {
            document.getElementById('gamXpHistoryList').innerHTML = '<p class="text-muted small">Unable to load XP history.</p>';
        });
    }

    function renderChallenges() {
        fetch('/api/gamification/challenges').then(function (r) { return r.json(); }).then(function (items) {
            var container = document.getElementById('gamChallengesList');
            if (!items.length) { container.innerHTML = '<p class="text-muted small">No challenges this month.</p>'; return; }
            container.innerHTML = items.map(function (c) {
                var pct = c.targetValue ? Math.max(0, Math.min(100, Math.round((c.progressCurrent / c.targetValue) * 100))) : 0;
                var completed = c.status === 'COMPLETED';
                var shownCurrent = Math.min(c.progressCurrent || 0, c.targetValue || 0);
                return '<div class="gam-challenge-card' + (completed ? ' completed' : '') + '">' +
                    '<div class="d-flex justify-content-between align-items-center mb-1">' +
                        '<span class="fw-bold">' + escHtml(c.name) + (completed ? ' <i class="fas fa-check-circle text-success"></i>' : '') + '</span>' +
                        '<span class="text-muted small">' + c.xpReward + ' XP</span>' +
                    '</div>' +
                    '<div class="text-muted small mb-2">' + escHtml(c.description) + '</div>' +
                    '<div class="gam-achv-progress-track"><div class="gam-achv-progress-fill" style="width:' + pct + '%"></div></div>' +
                    '<div class="gam-achv-meta"><span>' + shownCurrent + ' / ' + c.targetValue + '</span></div>' +
                '</div>';
            }).join('');
        }).catch(function () {
            document.getElementById('gamChallengesList').innerHTML = '<p class="text-muted small">Unable to load challenges.</p>';
        });
    }

    function init() {
        Promise.all([
            fetch('/api/gamification/summary').then(function (r) { return r.json(); }),
            fetch('/api/gamification/achievements').then(function (r) { return r.json(); })
        ]).then(function (results) {
            summaryData = results[0];
            allAchievements = results[1];
            renderOverview();
            renderCategoryFilters();
            renderAchievementsGrid(null);
            renderBadges();
            renderStatistics();
        }).catch(function () {
            document.getElementById('gamOverviewNearest').innerHTML = '<p class="text-muted small">Unable to load achievements right now.</p>';
        });
        renderChallenges();
        loadXpHistory();
    }

    document.addEventListener('DOMContentLoaded', init);
})();
