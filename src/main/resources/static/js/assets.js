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
    let sellDiscountPct = 17;   // default sell deduction %

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

    window.setSellDiscount = function (val) {
        const n = parseFloat(val);
        sellDiscountPct = (!isNaN(n) && n >= 0 && n <= 100) ? n : 17;
        renderTable();
    };

    function getFilteredAssets() {
        const query   = (document.getElementById('searchInput')?.value || '').toLowerCase();
        const purity  = document.getElementById('filterPurity')?.value || '';
        const type    = document.getElementById('filterType')?.value || '';

        return allAssets.filter(a => {
            const matchSearch = !query || a.assetName.toLowerCase().includes(query) || (a.description || '').toLowerCase().includes(query);
            const matchPurity = !purity || a.purity === purity;
            const matchType   = !type   || a.goldType === type;
            return matchSearch && matchPurity && matchType;
        });
    }

    window.renderTable = function () {
        let filtered = getFilteredAssets();

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
        renderSummaryFoot(filtered);
    };

    function formatVoriBreakdown(grams) {
        const totalVori = grams * VORI_PER_GRAM;
        const vori  = Math.floor(totalVori);
        const remAna = (totalVori - vori) * 16;
        const ana   = Math.floor(remAna);
        const remRati = (remAna - ana) * 6;
        const rati  = Math.floor(remRati);
        const point = Math.round((remRati - rati) * 5);
        const parts = [];
        if (vori > 0) parts.push(`${vori} vori`);
        parts.push(`${ana} ana`);
        parts.push(`${rati} roti`);
        parts.push(`${point} point`);
        return parts.join(' ');
    }

    function renderAssetRow(a) {
        const gain     = (a.gainLoss   || 0);
        const gainPct  = (a.gainLossPct || 0);
        const gainCls  = gain >= 0 ? 'gain-positive' : 'gain-negative';
        const gainSign = gain >= 0 ? '+' : '';
        const gainHtml = a.purchasePrice
            ? `<span class="${gainCls}">${gainSign}৳${fmt(Math.abs(gain))} (${gainSign}${gainPct.toFixed(1)}%)</span>`
            : '<span class="text-muted">—</span>';

        const conv = a.weightConversions || convertAll(a.weight, a.weightUnit);
        const grams = conv.GRAM || 0;
        const unitLabel = a.weightUnit.charAt(0) + a.weightUnit.slice(1).toLowerCase();
        const weightDisplay = `${a.weight} ${unitLabel}<small class="d-block text-muted">${formatVoriBreakdown(grams)}</small>`;

        const sellPrice = a.currentValue ? a.currentValue * (1 - sellDiscountPct / 100) : null;

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
            <td class="fw-semibold">${sellPrice ? '৳' + fmt(sellPrice) : '<span class="text-muted">—</span>'}</td>
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

    function renderSummaryFoot(assets) {
        const tfoot = document.getElementById('assetTableFoot');
        if (!tfoot) return;
        if (!assets || assets.length === 0) { tfoot.innerHTML = ''; return; }

        let totalCurrentValue = 0, totalPurchasePrice = 0, totalGain = 0, totalSellPrice = 0;
        let hasCurrentValue = false, hasPurchase = false;

        for (const a of assets) {
            if (a.currentValue) { totalCurrentValue += a.currentValue; hasCurrentValue = true; }
            if (a.purchasePrice) { totalPurchasePrice += a.purchasePrice; hasPurchase = true; }
            if (a.gainLoss) totalGain += a.gainLoss;
            if (a.currentValue) totalSellPrice += a.currentValue * (1 - sellDiscountPct / 100);
        }

        const gainCls  = totalGain >= 0 ? 'gain-positive' : 'gain-negative';
        const gainSign = totalGain >= 0 ? '+' : '';

        tfoot.innerHTML = `<tr>
            <td colspan="4" style="color:var(--text-muted-custom);font-size:0.78rem;">Totals (${assets.length} asset${assets.length !== 1 ? 's' : ''})</td>
            <td>${hasCurrentValue ? '৳' + fmt(totalCurrentValue) : '<span class="text-muted">—</span>'}</td>
            <td>${hasPurchase ? '৳' + fmt(totalPurchasePrice) : '<span class="text-muted">—</span>'}</td>
            <td><span class="${gainCls}">${hasPurchase ? gainSign + '৳' + fmt(Math.abs(totalGain)) : '<span class="text-muted">—</span>'}</span></td>
            <td>${hasCurrentValue ? '৳' + fmt(totalSellPrice) : '<span class="text-muted">—</span>'}</td>
            <td></td>
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

    // ── CSV EXPORT / IMPORT ──────────────────────────────────────────────────

    const VALID_GOLD_TYPES = ['ORNAMENT', 'BAR', 'COIN', 'CUSTOM'];
    const VALID_PURITIES   = ['22K', '21K', '18K', '24K', 'TRADITIONAL', 'CUSTOM'];
    const VALID_WEIGHT_UNITS = ['GRAM', 'VORI', 'ANA', 'RATI', 'POINT'];

    function csvEscape(val) {
        const s = String(val == null ? '' : val);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }

    window.exportFilteredAssetsToCsv = async function () {
        const filtered = getFilteredAssets();
        if (!filtered.length) {
            showToast('No assets to export.', 'error');
            return;
        }
        const ok = await confirmAction(`Export ${filtered.length} asset${filtered.length > 1 ? 's' : ''} to CSV?`, { title: 'Export to CSV', confirmText: 'Export' });
        if (!ok) return;
        const headers = ['Asset Name', 'Description', 'Gold Type', 'Purity', 'Weight', 'Weight Unit', 'Purchase Date', 'Purchase Price', 'Notes', 'Current Value', 'Gain/Loss', 'Gain/Loss %', 'Created At'];
        const rows = filtered.map(a => [
            a.assetName,
            a.description || '',
            a.goldType,
            a.purity,
            a.weight,
            a.weightUnit,
            a.purchaseDate || '',
            a.purchasePrice != null ? a.purchasePrice : '',
            a.notes || '',
            a.currentValue != null ? a.currentValue : '',
            a.gainLoss != null ? a.gainLoss : '',
            a.gainLossPct != null ? a.gainLossPct : '',
            a.createdAt || ''
        ]);
        const csvContent = [headers, ...rows].map(r => r.map(csvEscape).join(',')).join('\r\n');
        const blob = new Blob(['﻿' + csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const stamp = new Date().toISOString().slice(0, 10);
        a.href = url;
        a.download = `gold_assets_${stamp}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showToast(`Exported ${filtered.length} asset${filtered.length > 1 ? 's' : ''} to CSV.`, 'success');
    };

    function parseCsv(text) {
        const rows = [];
        let row = [], field = '', inQuotes = false;
        for (let i = 0; i < text.length; i++) {
            const c = text[i];
            if (inQuotes) {
                if (c === '"') {
                    if (text[i + 1] === '"') { field += '"'; i++; }
                    else inQuotes = false;
                } else field += c;
            } else if (c === '"') {
                inQuotes = true;
            } else if (c === ',') {
                row.push(field); field = '';
            } else if (c === '\r') {
                // skip, \n handles the line break
            } else if (c === '\n') {
                row.push(field); rows.push(row); row = []; field = '';
            } else {
                field += c;
            }
        }
        if (field.length || row.length) { row.push(field); rows.push(row); }
        return rows.filter(r => r.some(c => c.trim() !== ''));
    }

    window.handleAssetImportFile = function (inputEl) {
        const file = inputEl.files[0];
        inputEl.value = '';
        if (file) importAssetsFromCsv(file);
    };

    async function importAssetsFromCsv(file) {
        const text = (await file.text()).replace(/^﻿/, '');
        const rows = parseCsv(text);
        if (rows.length < 2) {
            showToast('CSV file has no data rows.', 'error');
            return;
        }
        const header = rows[0].map(h => h.trim().toLowerCase());
        const idx = {
            name: header.indexOf('asset name'),
            description: header.indexOf('description'),
            goldType: header.indexOf('gold type'),
            purity: header.indexOf('purity'),
            weight: header.indexOf('weight'),
            weightUnit: header.indexOf('weight unit'),
            purchaseDate: header.indexOf('purchase date'),
            purchasePrice: header.indexOf('purchase price'),
            notes: header.indexOf('notes')
        };
        if (idx.name === -1 || idx.weight === -1) {
            showToast('CSV is missing required columns (Asset Name, Weight).', 'error');
            return;
        }

        showToast('Importing…', 'info');
        let succeeded = 0, failed = 0;
        const errors = [];
        for (const r of rows.slice(1)) {
            const name = (r[idx.name] || '').trim();
            const weight = parseFloat(String(r[idx.weight] || '').replace(/[^\d.-]/g, ''));
            const goldTypeRaw = idx.goldType !== -1 ? (r[idx.goldType] || '').trim().toUpperCase() : '';
            const purityRaw = idx.purity !== -1 ? (r[idx.purity] || '').trim().toUpperCase() : '';
            const weightUnitRaw = idx.weightUnit !== -1 ? (r[idx.weightUnit] || '').trim().toUpperCase() : '';
            const description = idx.description !== -1 ? (r[idx.description] || '').trim() : '';
            const purchaseDate = idx.purchaseDate !== -1 ? (r[idx.purchaseDate] || '').trim().slice(0, 10) : '';
            const purchasePriceRaw = idx.purchasePrice !== -1 ? (r[idx.purchasePrice] || '').trim() : '';
            const notes = idx.notes !== -1 ? (r[idx.notes] || '').trim() : '';

            if (!name || !weight || weight <= 0) {
                failed++; errors.push(`"${name || 'row'}" skipped — name and a positive weight are required.`);
                continue;
            }
            if (goldTypeRaw && !VALID_GOLD_TYPES.includes(goldTypeRaw)) {
                failed++; errors.push(`"${name}" skipped — invalid Gold Type "${r[idx.goldType]}".`);
                continue;
            }
            if (purityRaw && !VALID_PURITIES.includes(purityRaw)) {
                failed++; errors.push(`"${name}" skipped — invalid Purity "${r[idx.purity]}".`);
                continue;
            }
            if (weightUnitRaw && !VALID_WEIGHT_UNITS.includes(weightUnitRaw)) {
                failed++; errors.push(`"${name}" skipped — invalid Weight Unit "${r[idx.weightUnit]}".`);
                continue;
            }
            const purchasePrice = purchasePriceRaw ? parseFloat(purchasePriceRaw.replace(/[^\d.-]/g, '')) : null;

            const body = {
                assetName: name,
                description: description || null,
                goldType: goldTypeRaw || 'ORNAMENT',
                purity: purityRaw || '22K',
                weight: weight,
                weightUnit: weightUnitRaw || 'GRAM',
                purchaseDate: purchaseDate || null,
                purchasePrice: (purchasePrice && purchasePrice > 0) ? purchasePrice : null,
                notes: notes || null
            };

            try {
                await apiFetch('/api/gold/assets', { method: 'POST', body: JSON.stringify(body) });
                succeeded++;
            } catch (e) {
                failed++; errors.push(`"${name}" failed — ${e.message || 'server error'}.`);
            }
        }

        if (succeeded) loadAll();
        if (failed === 0) {
            showToast(`Imported ${succeeded} asset${succeeded > 1 ? 's' : ''} successfully.`, 'success');
        } else {
            showToast(`${succeeded} imported, ${failed} failed. ${errors.slice(0, 2).join(' ')}`, succeeded ? 'warning' : 'error');
        }
    }

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

    const TOAST_ICONS = { success: 'fa-circle-check', error: 'fa-circle-exclamation', warning: 'fa-triangle-exclamation', info: 'fa-circle-info' };

    function showToast(message, type) {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        type = type || 'info';
        const el = document.createElement('div');
        el.className = `notification-toast notification-${type}`;
        el.innerHTML = `<i class="fas ${TOAST_ICONS[type] || TOAST_ICONS.info} notification-toast-icon"></i>` +
            `<span class="notification-toast-message"></span>` +
            `<button type="button" class="notification-toast-close" aria-label="Dismiss">&times;</button>`;
        el.querySelector('.notification-toast-message').textContent = message;
        const dismiss = () => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); };
        el.querySelector('.notification-toast-close').addEventListener('click', dismiss);
        container.appendChild(el);
        requestAnimationFrame(() => el.classList.add('show'));
        setTimeout(dismiss, 5000);
    }

})();
