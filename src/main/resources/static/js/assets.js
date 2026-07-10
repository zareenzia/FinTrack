/**
 * assets.js — Gold Assets module for FinTrack
 * Handles CRUD for gold assets, gold price sync, pricing modes,
 * weight conversions, and dashboard rendering.
 */
(function () {
    'use strict';

    // ── Constants ────────────────────────────────────────────────────────────
    const VORI_PER_GRAM  = 1 / 11.664;
    const ANA_PER_GRAM   = 16 / 11.664;
    const RATI_PER_GRAM  = 96 / 11.664;
    const POINT_PER_GRAM = 480 / 11.664;

    const PAGE_SIZE = 10;

    // ── State ─────────────────────────────────────────────────────────────────
    let allAssets    = [];
    let currentPrices = null;   // latest prices from /api/gold/prices/current
    let summary      = null;
    let currentPage  = 1;
    let syncPollTimer = null;

    // ── Init ──────────────────────────────────────────────────────────────────
    document.addEventListener('DOMContentLoaded', function () {
        loadAll();
    });

    function loadAll() {
        loadSummary();
        loadPrices();
        loadAssets();
    }

    // ── API helpers ──────────────────────────────────────────────────────────

    async function apiFetch(url, options) {
        try {
            const resp = await fetchWithAuth(url, options);
            if (!resp) return null;
            if (resp.status === 204) return null;
            const ct = resp.headers.get('content-type') || '';
            const data = ct.includes('application/json') ? await resp.json() : await resp.text();
            if (!resp.ok) {
                const msg = (data && data.error) ? data.error : (typeof data === 'string' ? data : 'Request failed');
                throw new Error(msg);
            }
            return data;
        } catch (err) {
            throw err;
        }
    }

    // ── Weight Conversion (local, no network) ────────────────────────────────

    function toGrams(value, unit) {
        const v = parseFloat(value) || 0;
        switch ((unit || '').toUpperCase()) {
            case 'GRAM':  return v;
            case 'VORI':  return v * 11.664;
            case 'ANA':   return v * 11.664 / 16;
            case 'RATI':  return v * 11.664 / 96;
            case 'POINT': return v * 11.664 / 480;
            default:      return v;
        }
    }

    function convertAll(value, unit) {
        const g = toGrams(value, unit);
        return {
            GRAM:  round6(g),
            VORI:  round6(g * VORI_PER_GRAM),
            ANA:   round6(g * ANA_PER_GRAM),
            RATI:  round6(g * RATI_PER_GRAM),
            POINT: round6(g * POINT_PER_GRAM)
        };
    }

    function round6(v) { return Math.round(v * 1e6) / 1e6; }

    // Weight input → update conversion table in Add/Edit modal
    window.onWeightInput = function () {
        const val  = document.getElementById('weight').value;
        const unit = document.getElementById('weightUnit').value;
        if (!val || isNaN(parseFloat(val))) {
            ['GRAM','VORI','ANA','RATI','POINT'].forEach(u => setEl('conv-' + u, '—'));
            return;
        }
        const conv = convertAll(val, unit);
        for (const [u, v] of Object.entries(conv)) {
            const el = document.getElementById('conv-' + u);
            if (el) el.textContent = v.toLocaleString('en', { maximumFractionDigits: 6 });
        }
    };

    // ── SUMMARY ───────────────────────────────────────────────────────────────

    async function loadSummary() {
        try {
            summary = await apiFetch('/api/gold/summary');
            if (!summary) return;
            setEl('totalGoldValue', fmt(summary.totalGoldValue));
            setEl('numberOfAssets', summary.numberOfAssets);
            const vori = summary.totalWeightVori != null
                ? summary.totalWeightVori.toFixed(4) + ' Vori'
                : (summary.totalWeightGrams != null ? summary.totalWeightGrams.toFixed(2) + ' g' : '—');
            setEl('totalWeight', vori);
            const p22 = summary.pricesPerGram && summary.pricesPerGram['22K'];
            setEl('currentGoldPrice', p22 ? '৳' + fmt(p22) + ' /g' : '—');
            updatePriceModeUI(summary.priceMode);
        } catch (e) {
            console.error('loadSummary error:', e);
        }
    }

    // ── PRICES ─────────────────────────────────────────────────────────────────

    async function loadPrices() {
        try {
            currentPrices = await apiFetch('/api/gold/prices/current');
            if (!currentPrices) return;
            renderPriceTable(currentPrices.prices || []);
            renderSyncStatus(currentPrices);
            updatePriceModeUI(currentPrices.mode);
        } catch (e) {
            console.error('loadPrices error:', e);
            setSyncStatus('error', 'Failed to load price data');
        }
    }

    function renderSyncStatus(data) {
        const { lastSyncTime, lastSyncError, syncInProgress, mode } = data;

        const dot  = document.getElementById('syncDot');
        const text = document.getElementById('syncStatusText');
        const errB = document.getElementById('syncErrorBlock');
        const mBadge = document.getElementById('syncModeBadge');

        if (mBadge) {
            mBadge.textContent = mode === 'AUTOMATIC' ? 'Auto' : 'Manual';
            mBadge.className = 'gold-badge ' + (mode === 'AUTOMATIC' ? 'mode-auto' : 'mode-manual');
        }

        if (syncInProgress) {
            setSyncStatus('syncing', 'Syncing prices…');
            if (!syncPollTimer) {
                syncPollTimer = setInterval(() => {
                    loadPrices().then(() => {
                        if (currentPrices && !currentPrices.syncInProgress) {
                            clearInterval(syncPollTimer);
                            syncPollTimer = null;
                            loadSummary();
                            loadAssets();
                        }
                    });
                }, 3000);
            }
        } else if (lastSyncError) {
            setSyncStatus('error', 'Last sync failed');
            if (errB) { errB.classList.remove('d-none'); errB.textContent = lastSyncError; }
        } else if (lastSyncTime) {
            setSyncStatus('success', 'Prices up to date');
            if (errB) errB.classList.add('d-none');
        } else {
            setSyncStatus('error', 'No price data available. Click "Sync Gold Prices".');
        }

        const timeEl = document.getElementById('lastSyncTime');
        if (timeEl) timeEl.textContent = lastSyncTime ? 'Last sync: ' + fmtDateTime(lastSyncTime) : 'Never synced';

        const updEl = document.getElementById('priceTableUpdated');
        if (updEl && lastSyncTime) updEl.textContent = 'Updated: ' + fmtDateTime(lastSyncTime);
    }

    function setSyncStatus(state, message) {
        const dot  = document.getElementById('syncDot');
        const text = document.getElementById('syncStatusText');
        if (dot)  { dot.className  = 'sync-dot ' + state; }
        if (text) { text.textContent = message; }
        const syncBtn  = document.getElementById('syncPricesBtn');
        const syncIcon = document.getElementById('syncIcon');
        if (state === 'syncing') {
            if (syncBtn)  syncBtn.disabled = true;
            if (syncIcon) { syncIcon.className = 'fas fa-sync-alt fa-spin me-1'; }
        } else {
            if (syncBtn)  syncBtn.disabled = false;
            if (syncIcon) { syncIcon.className = 'fas fa-sync-alt me-1'; }
        }
    }

    function renderPriceTable(prices) {
        const tbody = document.getElementById('priceTableBody');
        if (!tbody) return;

        // Group by purity — for each purity collect unit prices
        const byPurity = {};
        for (const p of prices) {
            if (!byPurity[p.purity]) byPurity[p.purity] = {};
            byPurity[p.purity][p.unit] = p;
        }

        const purities = ['22K', '21K', '18K', 'TRADITIONAL', '24K'];
        const rows = purities.filter(pu => byPurity[pu]);

        if (rows.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No price data. Click "Sync Gold Prices".</td></tr>';
            return;
        }

        // If we only have VORI prices (common with goldr.org), derive others via conversion
        // For GRAM column: if no GRAM record, derive from VORI price / 11.664
        tbody.innerHTML = rows.map(purity => {
            const rec = byPurity[purity];
            const gramPrice  = rec['GRAM']  ? rec['GRAM'].marketPrice  : (rec['VORI'] ? rec['VORI'].marketPrice / 11.664 : null);
            const voriPrice  = rec['VORI']  ? rec['VORI'].marketPrice  : (gramPrice   ? gramPrice * 11.664 : null);
            const anaPrice   = rec['ANA']   ? rec['ANA'].marketPrice   : (gramPrice   ? gramPrice * 11.664 / 16 : null);
            const ratiPrice  = rec['RATI']  ? rec['RATI'].marketPrice  : (gramPrice   ? gramPrice * 11.664 / 96 : null);
            const retrievedAt = (rec['GRAM'] || rec['VORI'] || Object.values(rec)[0] || {}).retrievedAt;
            return `<tr>
                <td>${purityBadge(purity)}</td>
                <td>${fmtPrice(gramPrice)}</td>
                <td>${fmtPrice(voriPrice)}</td>
                <td>${fmtPrice(anaPrice)}</td>
                <td>${fmtPrice(ratiPrice)}</td>
                <td style="font-size:0.8rem;color:var(--text-muted-custom);">${retrievedAt ? fmtDateTime(retrievedAt) : '—'}</td>
            </tr>`;
        }).join('');
    }

    // ── ASSETS ──────────────────────────────────────────────────────────────────

    async function loadAssets() {
        try {
            allAssets = await apiFetch('/api/gold/assets') || [];
            renderTable();
        } catch (e) {
            console.error('loadAssets error:', e);
            showToast('Failed to load assets', 'error');
        }
    }

    window.renderTable = function () {
        const query   = (document.getElementById('searchInput')?.value || '').toLowerCase();
        const purity  = document.getElementById('filterPurity')?.value || '';
        const type    = document.getElementById('filterType')?.value || '';

        let filtered = allAssets.filter(a => {
            const matchSearch = !query || a.assetName.toLowerCase().includes(query) || (a.description || '').toLowerCase().includes(query);
            const matchPurity = !purity || a.purity === purity;
            const matchType   = !type   || a.goldType === type;
            return matchSearch && matchPurity && matchType;
        });

        const total = filtered.length;
        const pages = Math.ceil(total / PAGE_SIZE) || 1;
        if (currentPage > pages) currentPage = pages;
        const start = (currentPage - 1) * PAGE_SIZE;
        const page  = filtered.slice(start, start + PAGE_SIZE);

        const tbody = document.getElementById('assetTableBody');
        if (!tbody) return;

        if (page.length === 0) {
            tbody.innerHTML = `<tr><td colspan="9"><div class="empty-state">
                <div class="empty-state-icon"><i class="fas fa-coins"></i></div>
                <h5 class="text-muted">${total === 0 ? 'No gold assets yet' : 'No assets match your filters'}</h5>
                ${total === 0 ? `<p class="text-muted mb-3">Click "Add Gold Asset" to start tracking.</p>
                <button class="btn btn-primary btn-sm" onclick="openAddAssetModal()"><i class="fas fa-plus me-1"></i> Add Gold Asset</button>` : ''}
            </div></td></tr>`;
        } else {
            tbody.innerHTML = page.map(a => renderAssetRow(a)).join('');
        }

        renderPagination(total, pages);
    };

    function renderAssetRow(a) {
        const gain     = (a.gainLoss   || 0);
        const gainPct  = (a.gainLossPct || 0);
        const gainCls  = gain >= 0 ? 'gain-positive' : 'gain-negative';
        const gainSign = gain >= 0 ? '+' : '';
        const gainHtml = a.purchasePrice
            ? `<span class="${gainCls}">${gainSign}৳${fmt(Math.abs(gain))} (${gainSign}${gainPct.toFixed(1)}%)</span>`
            : '<span class="text-muted">—</span>';

        const conv = a.weightConversions || convertAll(a.weight, a.weightUnit);
        const weightDisplay = `${a.weight} ${a.weightUnit.charAt(0) + a.weightUnit.slice(1).toLowerCase()}
            <small class="d-block text-muted">${conv.GRAM?.toFixed(3)} g / ${conv.VORI?.toFixed(4)} Vori</small>`;

        return `<tr>
            <td>
                <div class="d-flex align-items-center gap-2">
                    <div class="asset-type-icon">${goldTypeIcon(a.goldType)}</div>
                    <div>
                        <div class="fw-semibold" style="cursor:pointer;" onclick="openDetailModal(${a.id})">${escHtml(a.assetName)}</div>
                        ${a.description ? `<small class="text-muted">${escHtml(a.description)}</small>` : ''}
                    </div>
                </div>
            </td>
            <td><small class="text-muted">${fmtGoldType(a.goldType)}</small></td>
            <td>${purityBadge(a.purity)}</td>
            <td style="font-size:0.85rem;">${weightDisplay}</td>
            <td class="fw-semibold">${a.currentValue ? '৳' + fmt(a.currentValue) : '<span class="text-muted">—</span>'}</td>
            <td>${a.purchasePrice ? '৳' + fmt(a.purchasePrice) : '<span class="text-muted">—</span>'}</td>
            <td>${gainHtml}</td>
            <td style="font-size:0.8rem;color:var(--text-muted-custom);">${a.updatedAt ? fmtDate(a.updatedAt) : '—'}</td>
            <td>
                <div class="d-flex gap-1">
                    <button class="btn btn-xs btn-outline-primary" title="Edit" onclick="openEditModal(${a.id})" style="padding:3px 7px;font-size:0.78rem;">
                        <i class="fas fa-edit"></i>
                    </button>
                    <button class="btn btn-xs btn-outline-danger" title="Delete" onclick="openDeleteModal(${a.id}, '${escHtml(a.assetName)}')" style="padding:3px 7px;font-size:0.78rem;">
                        <i class="fas fa-trash"></i>
                    </button>
                </div>
            </td>
        </tr>`;
    }

    function renderPagination(total, pages) {
        const bar  = document.getElementById('paginationBar');
        const info = document.getElementById('paginationInfo');
        const ul   = document.getElementById('paginationLinks');
        if (!bar) return;
        if (total === 0) { bar.style.display = 'none'; return; }
        bar.style.display = 'flex';
        const start = (currentPage - 1) * PAGE_SIZE + 1;
        const end   = Math.min(currentPage * PAGE_SIZE, total);
        if (info) info.textContent = `Showing ${start}–${end} of ${total}`;
        if (ul) {
            let html = `<li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="goPage(${currentPage - 1});return false;">&laquo;</a></li>`;
            for (let i = 1; i <= pages; i++) {
                html += `<li class="page-item ${i === currentPage ? 'active' : ''}">
                    <a class="page-link" href="#" onclick="goPage(${i});return false;">${i}</a></li>`;
            }
            html += `<li class="page-item ${currentPage === pages ? 'disabled' : ''}">
                <a class="page-link" href="#" onclick="goPage(${currentPage + 1});return false;">&raquo;</a></li>`;
            ul.innerHTML = html;
        }
    }

    window.goPage = function (p) {
        currentPage = p;
        renderTable();
    };

    // ── ADD / EDIT ASSET MODAL ───────────────────────────────────────────────────

    window.openAddAssetModal = function () {
        document.getElementById('assetModalLabel').innerHTML = '<i class="fas fa-plus-circle me-2"></i>Add Gold Asset';
        document.getElementById('saveAssetBtnText').textContent = 'Save Asset';
        document.getElementById('editAssetId').value = '';
        document.getElementById('assetForm').reset();
        document.getElementById('goldType').value = 'ORNAMENT';
        document.getElementById('purity').value = '22K';
        document.getElementById('weightUnit').value = 'GRAM';
        onWeightInput();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('assetModal')).show();
    };

    window.openEditModal = function (id) {
        const asset = allAssets.find(a => a.id === id);
        if (!asset) return;
        document.getElementById('assetModalLabel').innerHTML = '<i class="fas fa-edit me-2"></i>Edit Gold Asset';
        document.getElementById('saveAssetBtnText').textContent = 'Update Asset';
        document.getElementById('editAssetId').value = id;
        document.getElementById('assetName').value = asset.assetName || '';
        document.getElementById('goldType').value = asset.goldType || 'ORNAMENT';
        document.getElementById('purity').value = asset.purity || '22K';
        document.getElementById('weight').value = asset.weight || '';
        document.getElementById('weightUnit').value = asset.weightUnit || 'GRAM';
        document.getElementById('purchaseDate').value = asset.purchaseDate || '';
        document.getElementById('purchasePrice').value = asset.purchasePrice || '';
        document.getElementById('assetDescription').value = asset.description || '';
        document.getElementById('assetNotes').value = asset.notes || '';
        onWeightInput();
        bootstrap.Modal.getOrCreateInstance(document.getElementById('assetModal')).show();
    };

    window.saveAsset = async function () {
        const id   = document.getElementById('editAssetId').value;
        const name = document.getElementById('assetName').value.trim();
        const wt   = parseFloat(document.getElementById('weight').value);
        if (!name) { showToast('Asset name is required', 'error'); return; }
        if (!wt || wt <= 0) { showToast('Weight must be a positive number', 'error'); return; }

        const body = {
            assetName:    name,
            description:  document.getElementById('assetDescription').value.trim() || null,
            goldType:     document.getElementById('goldType').value,
            purity:       document.getElementById('purity').value,
            weight:       wt,
            weightUnit:   document.getElementById('weightUnit').value,
            purchaseDate: document.getElementById('purchaseDate').value || null,
            purchasePrice: parseFloat(document.getElementById('purchasePrice').value) || null,
            notes:        document.getElementById('assetNotes').value.trim() || null
        };

        const btn = document.getElementById('saveAssetBtn');
        btn.disabled = true;
        try {
            if (id) {
                await apiFetch('/api/gold/assets/' + id, { method: 'PUT', body: JSON.stringify(body) });
                showToast('Asset updated successfully', 'success');
            } else {
                await apiFetch('/api/gold/assets', { method: 'POST', body: JSON.stringify(body) });
                showToast('Asset added successfully', 'success');
            }
            bootstrap.Modal.getOrCreateInstance(document.getElementById('assetModal')).hide();
            loadAll();
        } catch (e) {
            showToast(e.message || 'Save failed', 'error');
        } finally {
            btn.disabled = false;
        }
    };

    // ── DETAIL MODAL ───────────────────────────────────────────────────────────

    window.openDetailModal = function (id) {
        const asset = allAssets.find(a => a.id === id);
        if (!asset) return;
        const conv = asset.weightConversions || convertAll(asset.weight, asset.weightUnit);
        const gain = asset.gainLoss || 0;
        const gainPct = asset.gainLossPct || 0;
        const gainCls = gain >= 0 ? 'gain-positive' : 'gain-negative';
        const gainSign = gain >= 0 ? '+' : '';

        document.getElementById('detailModalLabel').innerHTML =
            `<i class="fas fa-coins me-2"></i>${escHtml(asset.assetName)}`;

        document.getElementById('detailModalBody').innerHTML = `
            <div class="row g-3">
                <div class="col-md-6">
                    <h6 class="fw-bold mb-3"><i class="fas fa-info-circle me-2 text-muted"></i>Asset Information</h6>
                    <div class="detail-row"><span class="detail-label">Asset Name</span><span class="detail-value">${escHtml(asset.assetName)}</span></div>
                    <div class="detail-row"><span class="detail-label">Gold Type</span><span class="detail-value">${fmtGoldType(asset.goldType)}</span></div>
                    <div class="detail-row"><span class="detail-label">Purity</span><span class="detail-value">${purityBadge(asset.purity)}</span></div>
                    <div class="detail-row"><span class="detail-label">Weight</span><span class="detail-value">${asset.weight} ${asset.weightUnit}</span></div>
                    ${asset.description ? `<div class="detail-row"><span class="detail-label">Description</span><span class="detail-value">${escHtml(asset.description)}</span></div>` : ''}
                    ${asset.notes ? `<div class="detail-row"><span class="detail-label">Notes</span><span class="detail-value">${escHtml(asset.notes)}</span></div>` : ''}
                    <div class="detail-row"><span class="detail-label">Added</span><span class="detail-value">${asset.createdAt ? fmtDate(asset.createdAt) : '—'}</span></div>
                </div>
                <div class="col-md-6">
                    <h6 class="fw-bold mb-3"><i class="fas fa-balance-scale me-2 text-muted"></i>Weight Conversions</h6>
                    <div class="detail-row"><span class="detail-label">Gram</span><span class="detail-value">${conv.GRAM?.toLocaleString('en', { maximumFractionDigits: 6 })} g</span></div>
                    <div class="detail-row"><span class="detail-label">Vori (ভরি)</span><span class="detail-value">${conv.VORI?.toLocaleString('en', { maximumFractionDigits: 6 })}</span></div>
                    <div class="detail-row"><span class="detail-label">Ana (আনা)</span><span class="detail-value">${conv.ANA?.toLocaleString('en', { maximumFractionDigits: 6 })}</span></div>
                    <div class="detail-row"><span class="detail-label">Rati (রতি)</span><span class="detail-value">${conv.RATI?.toLocaleString('en', { maximumFractionDigits: 6 })}</span></div>
                    <div class="detail-row"><span class="detail-label">Point</span><span class="detail-value">${conv.POINT?.toLocaleString('en', { maximumFractionDigits: 6 })}</span></div>
                </div>
                <div class="col-md-12">
                    <h6 class="fw-bold mb-3"><i class="fas fa-chart-line me-2 text-muted"></i>Valuation</h6>
                    <div class="row g-2">
                        <div class="col-6 col-md-3">
                            <div class="card text-center p-3" style="border-color:var(--border-color);">
                                <div class="text-muted mb-1" style="font-size:0.8rem;">Current Value</div>
                                <div class="fw-bold">${asset.currentValue ? '৳' + fmt(asset.currentValue) : '—'}</div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card text-center p-3" style="border-color:var(--border-color);">
                                <div class="text-muted mb-1" style="font-size:0.8rem;">Purchase Price</div>
                                <div class="fw-bold">${asset.purchasePrice ? '৳' + fmt(asset.purchasePrice) : '—'}</div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card text-center p-3" style="border-color:var(--border-color);">
                                <div class="text-muted mb-1" style="font-size:0.8rem;">Gain / Loss</div>
                                <div class="fw-bold ${gainCls}">${asset.purchasePrice ? gainSign + '৳' + fmt(Math.abs(gain)) : '—'}</div>
                            </div>
                        </div>
                        <div class="col-6 col-md-3">
                            <div class="card text-center p-3" style="border-color:var(--border-color);">
                                <div class="text-muted mb-1" style="font-size:0.8rem;">% Change</div>
                                <div class="fw-bold ${gainCls}">${asset.purchasePrice ? gainSign + gainPct.toFixed(2) + '%' : '—'}</div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>`;

        const editBtn = document.getElementById('detailEditBtn');
        if (editBtn) editBtn.onclick = function () {
            bootstrap.Modal.getOrCreateInstance(document.getElementById('detailModal')).hide();
            setTimeout(() => openEditModal(id), 300);
        };

        bootstrap.Modal.getOrCreateInstance(document.getElementById('detailModal')).show();
    };

    // ── DELETE MODAL ───────────────────────────────────────────────────────────

    window.openDeleteModal = function (id, name) {
        setEl('deleteAssetName', name);
        const btn = document.getElementById('confirmDeleteBtn');
        btn.onclick = async function () {
            btn.disabled = true;
            try {
                await apiFetch('/api/gold/assets/' + id, { method: 'DELETE' });
                bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteModal')).hide();
                showToast('Asset deleted', 'success');
                loadAll();
            } catch (e) {
                showToast(e.message || 'Delete failed', 'error');
            } finally {
                btn.disabled = false;
            }
        };
        bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteModal')).show();
    };

    // ── PRICE SYNC ─────────────────────────────────────────────────────────────

    window.triggerPriceSync = async function () {
        try {
            setSyncStatus('syncing', 'Syncing prices…');
            await apiFetch('/api/gold/prices/sync', { method: 'POST' });
            // Poll until done
            if (!syncPollTimer) {
                syncPollTimer = setInterval(() => {
                    loadPrices().then(() => {
                        if (currentPrices && !currentPrices.syncInProgress) {
                            clearInterval(syncPollTimer);
                            syncPollTimer = null;
                            loadSummary();
                            loadAssets();
                            showToast('Gold prices synced successfully!', 'success');
                        }
                    });
                }, 2000);
            }
        } catch (e) {
            showToast('Sync failed: ' + (e.message || 'Unknown error'), 'error');
            setSyncStatus('error', 'Sync failed');
        }
    };

    // ── PRICING MODE ───────────────────────────────────────────────────────────

    function updatePriceModeUI(mode) {
        const label = document.getElementById('priceModeLabel');
        if (label) {
            label.textContent = mode === 'MANUAL' ? 'Manual Mode' : 'Automatic Mode';
            label.className = 'gold-badge ' + (mode === 'MANUAL' ? 'mode-manual' : 'mode-auto');
        }
        const autoBtn = document.getElementById('btnAutoMode');
        const manBtn  = document.getElementById('btnManualMode');
        if (autoBtn) autoBtn.classList.toggle('active', mode === 'AUTOMATIC');
        if (manBtn)  manBtn.classList.toggle('active', mode === 'MANUAL');
    }

    window.setPriceMode = async function (mode) {
        try {
            await apiFetch('/api/gold/prices/mode', {
                method: 'POST',
                body: JSON.stringify({ mode })
            });
            updatePriceModeUI(mode);
            showToast('Switched to ' + (mode === 'AUTOMATIC' ? 'Automatic' : 'Manual') + ' pricing mode', 'success');
            loadAll();
        } catch (e) {
            showToast(e.message || 'Failed to switch mode', 'error');
        }
    };

    window.openManualPriceModal = function () {
        // Pre-fill with current summary prices if available
        if (summary && summary.pricesPerGram) {
            const pm = summary.pricesPerGram;
            ['22K','21K','18K','24K','TRADITIONAL'].forEach(pu => {
                const el = document.getElementById('mp' + pu.replace('K', 'K'));
                if (el && pm[pu]) el.value = pm[pu];
            });
        }
        bootstrap.Modal.getOrCreateInstance(document.getElementById('manualPriceModal')).show();
    };

    window.saveManualPrices = async function () {
        const prices = {};
        const purities = [
            { id: 'mp22K', key: '22K' },
            { id: 'mp21K', key: '21K' },
            { id: 'mp18K', key: '18K' },
            { id: 'mp24K', key: '24K' },
            { id: 'mpTRADITIONAL', key: 'TRADITIONAL' },
            { id: 'mpCUSTOM', key: 'CUSTOM' }
        ];
        let hasAny = false;
        for (const { id, key } of purities) {
            const val = parseFloat(document.getElementById(id)?.value);
            if (val > 0) { prices[key] = val; hasAny = true; }
        }
        if (!hasAny) {
            showToast('Please enter at least one price', 'error');
            return;
        }
        try {
            await apiFetch('/api/gold/prices/mode', {
                method: 'POST',
                body: JSON.stringify({ mode: 'MANUAL', manualPricesJson: JSON.stringify(prices) })
            });
            bootstrap.Modal.getOrCreateInstance(document.getElementById('manualPriceModal')).hide();
            showToast('Manual prices applied', 'success');
            loadAll();
        } catch (e) {
            showToast(e.message || 'Failed to set manual prices', 'error');
        }
    };

    // ── FORMATTING HELPERS ──────────────────────────────────────────────────────

    function fmt(v) {
        if (v == null || isNaN(v)) return '—';
        return parseFloat(v).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }

    function fmtPrice(v) {
        if (v == null || isNaN(v) || v === 0) return '<span class="text-muted">—</span>';
        return '৳' + parseFloat(v).toLocaleString('en-BD', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
    }

    function fmtDate(str) {
        if (!str) return '—';
        try { return new Date(str).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }); }
        catch { return str; }
    }

    function fmtDateTime(str) {
        if (!str) return '—';
        try { return new Date(str).toLocaleString('en-GB', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }); }
        catch { return str; }
    }

    function purityBadge(purity) {
        const map = { '22K': 'gold-badge-22k', '21K': 'gold-badge-21k', '18K': 'gold-badge-18k',
                      '24K': 'gold-badge-24k', 'TRADITIONAL': 'gold-badge-trad', 'CUSTOM': 'gold-badge-cust' };
        const cls = map[purity] || 'gold-badge-cust';
        const label = purity === 'TRADITIONAL' ? 'Traditional' : (purity || '—');
        return `<span class="gold-badge ${cls}">${label}</span>`;
    }

    function fmtGoldType(t) {
        if (!t) return '—';
        const map = { ORNAMENT: 'Ornament', BAR: 'Bar', COIN: 'Coin', CUSTOM: 'Custom' };
        return map[t] || t;
    }

    function goldTypeIcon(type) {
        const map = { ORNAMENT: '💍', BAR: '🏅', COIN: '🪙', CUSTOM: '✨' };
        return map[type] || '🏅';
    }

    function escHtml(s) {
        if (!s) return '';
        return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    }

    function setEl(id, val) {
        const el = document.getElementById(id);
        if (el) el.textContent = val != null ? val : '—';
    }

    // ── TOAST ──────────────────────────────────────────────────────────────────

    function showToast(message, type) {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        const id = 'toast-' + Date.now();
        const iconMap = { success: 'fa-check-circle text-success', error: 'fa-exclamation-circle text-danger', info: 'fa-info-circle text-info' };
        const icon = iconMap[type] || iconMap.info;
        const el = document.createElement('div');
        el.id = id;
        el.className = 'notification-toast show';
        el.innerHTML = `<div class="toast-body d-flex align-items-center gap-2">
            <i class="fas ${icon}"></i><span>${escHtml(message)}</span>
            <button type="button" class="btn-close btn-close-sm ms-auto" onclick="document.getElementById('${id}').remove()"></button>
        </div>`;
        container.appendChild(el);
        setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 400); }, 4000);
    }

})();
