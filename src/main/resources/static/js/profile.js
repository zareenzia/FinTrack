'use strict';

let _profileData = null;
let _usernameTimer = null;
let _usernameValid = true;

function getToken() {
    return localStorage.getItem('token');
}

function authHeaders() {
    return { 'Authorization': 'Bearer ' + getToken() };
}

// ─── Open modal ────────────────────────────────────────────────────────────────
function openProfileModal() {
    const modalEl = document.getElementById('userProfileModal');
    if (!modalEl) return;
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
    loadProfileData();
}

// ─── Setup event listeners ─────────────────────────────────────────────────────
// profile.js is loaded dynamically AFTER DOMContentLoaded fires, so we cannot use
// document.addEventListener('DOMContentLoaded', …). Instead we run setup immediately
// since the modal HTML is injected into the DOM before this script is appended.
function _setupProfileListeners() {
    const modalEl = document.getElementById('userProfileModal');
    if (!modalEl) {
        // Should not happen, but guard anyway
        return;
    }

    modalEl.addEventListener('hidden.bs.modal', resetModal);

    const pwdSection = document.getElementById('pwdSection');
    if (pwdSection) {
        pwdSection.addEventListener('show.bs.collapse', function () {
            const chev = document.querySelector('.toggle-chev');
            if (chev) chev.classList.add('rotated');
        });
        pwdSection.addEventListener('hide.bs.collapse', function () {
            const chev = document.querySelector('.toggle-chev');
            if (chev) chev.classList.remove('rotated');
        });
    }

    const usernameInput = document.getElementById('profileUsername');
    if (usernameInput) {
        usernameInput.addEventListener('input', onUsernameInput);
        usernameInput.addEventListener('blur', function () {
            const val = this.value.trim();
            if (val) {
                clearTimeout(_usernameTimer);
                checkUsernameAvailability(val);
            }
        });
    }

    const picInput = document.getElementById('profilePictureInput');
    if (picInput) picInput.addEventListener('change', onPictureSelected);

    // Expose upload trigger globally
    window._profileUploadTrigger = function () {
        var inp = document.getElementById('profilePictureInput');
        if (inp) inp.click();
    };
}

// Run setup immediately (modal HTML is already in the DOM when this script executes)
_setupProfileListeners();

// ─── Load profile ──────────────────────────────────────────────────────────────
async function loadProfileData() {
    try {
        const res = await fetch('/api/auth/me', { headers: authHeaders() });
        if (res.status === 401) { window.location.href = '/login'; return; }
        if (!res.ok) throw new Error('Failed to load profile');
        _profileData = await res.json();
        populateModal(_profileData);
    } catch (err) {
        console.error(err);
        showToast('Could not load profile data.', 'error');
    }
}

function populateModal(data) {
    const fullName = data.fullName || '';
    const username = data.username || '';
    const email    = data.email || '';
    const picUrl   = data.profilePicture || null;

    renderAvatar(picUrl, fullName);

    const displayEl = document.getElementById('profileDisplayName');
    if (displayEl) displayEl.textContent = fullName || email;

    const unameEl = document.getElementById('profileDisplayUsername');
    if (unameEl) unameEl.textContent = username ? '@' + username : '';

    setVal('profileFullName', fullName);
    setVal('profileUsername', username);
    setVal('profileEmail', email);

    if (data.createdAt) {
        const d = document.getElementById('profileMemberSince');
        if (d) d.textContent = new Date(data.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
    }

    refreshPhotoButtons(!!picUrl);
    _usernameValid = true;
    clearAllValidation();
}

// ─── Avatar ────────────────────────────────────────────────────────────────────
function renderAvatar(picUrl, fullName) {
    const img  = document.getElementById('profileAvatarImg');
    const init = document.getElementById('profileAvatarInitials');
    const initial = (fullName || '?').trim()[0].toUpperCase();

    if (picUrl) {
        if (img) { img.src = picUrl + '?t=' + Date.now(); img.classList.remove('d-none'); }
        if (init) init.classList.add('d-none');
    } else {
        if (img) img.classList.add('d-none');
        if (init) { init.textContent = initial; init.classList.remove('d-none'); }
    }

    // Update sidebar avatar
    updateSidebarAvatarUI(picUrl, fullName);
}

function updateSidebarAvatarUI(picUrl, fullName) {
    const sidebarAvatar = document.getElementById('sidebarAvatar');
    if (!sidebarAvatar) return;

    if (picUrl) {
        sidebarAvatar.innerHTML = `<img src="${picUrl}?t=${Date.now()}" alt="avatar" style="width:100%;height:100%;object-fit:cover;border-radius:50%;">`;
    } else {
        const initials = (fullName || '?').trim().split(/\s+/).map(w => w[0]).slice(0, 2).join('').toUpperCase();
        sidebarAvatar.textContent = initials || '?';
        sidebarAvatar.style.backgroundImage = '';
    }

    const sidebarName = document.getElementById('sidebarUserName');
    if (sidebarName && fullName) sidebarName.textContent = fullName;
}

// Expose for sidebar.js
window.updateSidebarAvatar = function (userData) {
    if (!userData) return;
    updateSidebarAvatarUI(userData.profilePicture, userData.fullName);
};

function refreshPhotoButtons(hasPic) {
    const removeBtn = document.getElementById('btnRemovePhoto');
    const uploadBtn = document.getElementById('btnUploadPhoto');
    if (removeBtn) removeBtn.classList.toggle('d-none', !hasPic);
    if (uploadBtn) uploadBtn.title = hasPic ? 'Change Photo' : 'Upload Photo';
}

// ─── Picture Upload ────────────────────────────────────────────────────────────
function onPictureSelected(e) {
    const file = e.target.files[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
        showToast('Unsupported format. Use JPG, PNG, or WebP.', 'error');
        e.target.value = '';
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        showToast('File is too large. Maximum size is 5 MB.', 'error');
        e.target.value = '';
        return;
    }

    // Instant local preview
    const reader = new FileReader();
    reader.onload = function (ev) {
        const img  = document.getElementById('profileAvatarImg');
        const init = document.getElementById('profileAvatarInitials');
        if (img)  { img.src = ev.target.result; img.classList.remove('d-none'); }
        if (init) init.classList.add('d-none');
    };
    reader.readAsDataURL(file);

    uploadPicture(file);
    e.target.value = '';
}

async function uploadPicture(file) {
    showUploadProgress(true);
    setDisabled(['btnUploadPhoto', 'btnRemovePhoto'], true);

    const formData = new FormData();
    formData.append('picture', file);

    try {
        const res = await fetch('/api/auth/profile-picture', {
            method: 'POST',
            headers: authHeaders(),
            body: formData
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Upload failed');

        if (_profileData) _profileData.profilePicture = data.profilePicture;
        renderAvatar(data.profilePicture, _profileData ? _profileData.fullName : '');
        refreshPhotoButtons(true);

        const stored = JSON.parse(localStorage.getItem('user') || '{}');
        stored.profilePicture = data.profilePicture;
        localStorage.setItem('user', JSON.stringify(stored));

        showToast('Profile picture updated!');
    } catch (err) {
        console.error(err);
        showToast(err.message || 'Failed to upload picture.', 'error');
        if (_profileData) renderAvatar(_profileData.profilePicture, _profileData.fullName);
    } finally {
        showUploadProgress(false);
        setDisabled(['btnUploadPhoto', 'btnRemovePhoto'], false);
    }
}

async function removeProfilePicture() {
    if (!confirm('Remove your profile picture?')) return;

    setDisabled(['btnRemovePhoto'], true);
    try {
        const res = await fetch('/api/auth/profile-picture', {
            method: 'DELETE',
            headers: authHeaders()
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Remove failed');

        if (_profileData) _profileData.profilePicture = null;
        renderAvatar(null, _profileData ? _profileData.fullName : '');
        refreshPhotoButtons(false);

        const stored = JSON.parse(localStorage.getItem('user') || '{}');
        delete stored.profilePicture;
        localStorage.setItem('user', JSON.stringify(stored));

        showToast('Profile picture removed.');
    } catch (err) {
        console.error(err);
        showToast(err.message || 'Failed to remove picture.', 'error');
    } finally {
        setDisabled(['btnRemovePhoto'], false);
    }
}

// ─── Username validation ───────────────────────────────────────────────────────
function onUsernameInput() {
    const val = document.getElementById('profileUsername').value.trim();
    clearUsernameStatus();
    _usernameValid = true;

    if (!val) { hide('usernameStatusIcon'); return; }

    const err = clientValidateUsername(val);
    if (err) { setUsernameError(err); return; }

    clearTimeout(_usernameTimer);
    show('usernameStatusIcon');
    showEls(['usernameSpinner']);
    hideEls(['usernameOk', 'usernameFail']);
    _usernameTimer = setTimeout(function () { checkUsernameAvailability(val); }, 500);
}

async function checkUsernameAvailability(username) {
    try {
        show('usernameStatusIcon');
        showEls(['usernameSpinner']);
        const res = await fetch('/api/auth/check-username?username=' + encodeURIComponent(username), {
            headers: authHeaders()
        });
        const data = await res.json();
        hideEls(['usernameSpinner']);
        if (data.available) {
            showEls(['usernameOk']);
            setUsernameSuccess('Username is available \u2713');
        } else {
            showEls(['usernameFail']);
            setUsernameError(data.error || 'Username is taken');
        }
    } catch (_) {
        hideEls(['usernameSpinner', 'usernameOk', 'usernameFail']);
        hide('usernameStatusIcon');
    }
}

function clientValidateUsername(val) {
    if (val.length < 3)  return 'Username must be at least 3 characters';
    if (val.length > 30) return 'Username must not exceed 30 characters';
    if (!/^[a-zA-Z0-9_.]+$/.test(val)) return 'Only letters, numbers, _ and . allowed';
    return null;
}

function setUsernameError(msg) {
    _usernameValid = false;
    show('usernameStatusIcon');
    hideEls(['usernameSpinner', 'usernameOk']);
    showEls(['usernameFail']);
    setFieldError('profileUsername', 'usernameError', msg);
    hide('usernameSuccess');
}

function setUsernameSuccess(msg) {
    _usernameValid = true;
    const el = document.getElementById('usernameSuccess');
    if (el) { el.textContent = msg; el.classList.remove('d-none'); }
    const errEl = document.getElementById('usernameError');
    if (errEl) errEl.classList.add('d-none');
    const inp = document.getElementById('profileUsername');
    if (inp) { inp.classList.remove('is-invalid'); inp.classList.add('is-valid'); }
}

function clearUsernameStatus() {
    ['usernameError', 'usernameSuccess'].forEach(hide);
    const inp = document.getElementById('profileUsername');
    if (inp) inp.classList.remove('is-invalid', 'is-valid');
    hideEls(['usernameOk', 'usernameFail', 'usernameSpinner']);
}

// ─── Save Profile ──────────────────────────────────────────────────────────────
async function saveProfile() {
    clearAllValidation();

    const fullName = getVal('profileFullName').trim();
    const username = getVal('profileUsername').trim();

    if (!fullName) {
        setFieldError('profileFullName', 'fullNameError', 'Full name cannot be empty');
        return;
    }

    if (username) {
        const err = clientValidateUsername(username);
        if (err) { setUsernameError(err); return; }
        if (!_usernameValid) { setUsernameError('Please resolve the username issue'); return; }
    }

    const payload = {};
    if (fullName !== ((_profileData && _profileData.fullName) || '')) payload.fullName = fullName;
    if (username !== ((_profileData && _profileData.username) || '')) payload.username = username;

    if (Object.keys(payload).length === 0) {
        showToast('No changes to save.', 'info');
        return;
    }

    setBtnLoading('btnSaveProfile', 'btn-save-txt', 'btn-save-ldg', true);
    try {
        const res = await fetch('/api/auth/profile', {
            method: 'PUT',
            headers: Object.assign({}, authHeaders(), { 'Content-Type': 'application/json' }),
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Save failed');

        _profileData = Object.assign({}, _profileData, data.user);
        const displayEl = document.getElementById('profileDisplayName');
        if (displayEl && data.user.fullName) displayEl.textContent = data.user.fullName;
        const unameEl = document.getElementById('profileDisplayUsername');
        if (unameEl) unameEl.textContent = data.user.username ? '@' + data.user.username : '';

        // Persist to localStorage
        const stored = JSON.parse(localStorage.getItem('user') || '{}');
        Object.assign(stored, data.user);
        localStorage.setItem('user', JSON.stringify(stored));

        updateSidebarAvatarUI(_profileData.profilePicture, _profileData.fullName);
        clearUsernameStatus();
        showToast('Profile saved successfully!');
    } catch (err) {
        console.error(err);
        showToast(err.message || 'Failed to save profile.', 'error');
    } finally {
        setBtnLoading('btnSaveProfile', 'btn-save-txt', 'btn-save-ldg', false);
    }
}

// ─── Password Change ───────────────────────────────────────────────────────────
function clientValidateNewPassword(pw) {
    if (pw.length < 8)              return 'Password must be at least 8 characters';
    if (!/[A-Z]/.test(pw))          return 'Must contain at least one uppercase letter';
    if (!/[a-z]/.test(pw))          return 'Must contain at least one lowercase letter';
    if (!/\d/.test(pw))             return 'Must contain at least one number';
    if (!/[^A-Za-z0-9]/.test(pw))   return 'Must contain at least one special character (e.g. !@#$)';
    return null;
}

async function submitPasswordChange() {
    clearPwdErrors();

    const cur  = getVal('currentPassword');
    const nw   = getVal('newPassword');
    const conf = getVal('confirmPassword');

    let ok = true;
    if (!cur)  { setFieldError('currentPassword', 'currentPwdError', 'Current password is required'); ok = false; }
    if (!nw)   { setFieldError('newPassword',      'newPwdError',     'New password is required');     ok = false; }
    if (!conf) { setFieldError('confirmPassword',  'confirmPwdError', 'Please confirm the new password'); ok = false; }
    if (!ok) return;

    if (nw !== conf) { setFieldError('confirmPassword', 'confirmPwdError', 'Passwords do not match'); return; }

    const pwErr = clientValidateNewPassword(nw);
    if (pwErr) { setFieldError('newPassword', 'newPwdError', pwErr); return; }

    if (nw === cur) { setFieldError('newPassword', 'newPwdError', 'New password must differ from the current one'); return; }

    setBtnLoading('btnChangePwd', 'btn-pwd-txt', 'btn-pwd-ldg', true);
    try {
        const res = await fetch('/api/auth/change-password', {
            method: 'POST',
            headers: Object.assign({}, authHeaders(), { 'Content-Type': 'application/json' }),
            body: JSON.stringify({ currentPassword: cur, newPassword: nw, confirmPassword: conf })
        });
        const data = await res.json();
        if (!res.ok) {
            const msg = data.error || 'Failed to change password';
            const lower = msg.toLowerCase();
            if (lower.includes('current password') || lower.includes('incorrect')) {
                setFieldError('currentPassword', 'currentPwdError', msg);
            } else if (lower.includes('match') || lower.includes('confirm')) {
                setFieldError('confirmPassword', 'confirmPwdError', msg);
            } else {
                setFieldError('newPassword', 'newPwdError', msg);
            }
            return;
        }
        clearPasswordFields();
        const sec = document.getElementById('pwdSection');
        if (sec) {
            const instance = bootstrap.Collapse.getInstance(sec);
            if (instance) instance.hide();
        }
        showToast('Password changed successfully!');
    } catch (err) {
        console.error(err);
        showToast(err.message || 'Failed to change password.', 'error');
    } finally {
        setBtnLoading('btnChangePwd', 'btn-pwd-txt', 'btn-pwd-ldg', false);
    }
}

// ─── Password Strength ─────────────────────────────────────────────────────────
function updatePwdStrength(value) {
    const wrap  = document.getElementById('pwdStrengthWrap');
    const fill  = document.getElementById('pwdStrengthFill');
    const label = document.getElementById('pwdStrengthLbl');
    if (!wrap) return;
    if (!value) { wrap.classList.add('d-none'); return; }
    wrap.classList.remove('d-none');

    var s = 0;
    if (value.length >= 8)       s++;
    if (/[A-Z]/.test(value))     s++;
    if (/[a-z]/.test(value))     s++;
    if (/\d/.test(value))        s++;
    if (/[^A-Za-z0-9]/.test(value)) s++;

    var levels = [
        { w: '20%', cls: 'bg-danger',  t: 'Very Weak' },
        { w: '40%', cls: 'bg-danger',  t: 'Weak' },
        { w: '60%', cls: 'bg-warning', t: 'Fair' },
        { w: '80%', cls: 'bg-info',    t: 'Good' },
        { w: '100%',cls: 'bg-success', t: 'Strong \u2713' }
    ];
    var lv = levels[s - 1] || levels[0];
    fill.style.width = lv.w;
    fill.className   = 'pwd-strength-fill ' + lv.cls;
    label.textContent = lv.t;
    label.className  = 'pwd-strength-lbl ' + lv.cls.replace('bg-', 'text-');
}

// ─── Logout ────────────────────────────────────────────────────────────────────
function doLogout() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = '/login';
}

// ─── Toggle password visibility ────────────────────────────────────────────────
function togglePwdVis(inputId, btn) {
    const input = document.getElementById(inputId);
    const icon  = btn.querySelector('i');
    if (!input || !icon) return;
    const hidden = input.type === 'password';
    input.type = hidden ? 'text' : 'password';
    icon.classList.toggle('fa-eye',       !hidden);
    icon.classList.toggle('fa-eye-slash',  hidden);
}

// ─── Utility helpers ───────────────────────────────────────────────────────────
function resetModal() {
    clearPasswordFields();
    clearAllValidation();
    _usernameValid = true;
    const sec = document.getElementById('pwdSection');
    if (sec) {
        const instance = bootstrap.Collapse.getInstance(sec);
        if (instance) instance.hide();
    }
}

function clearPasswordFields() {
    ['currentPassword', 'newPassword', 'confirmPassword'].forEach(function (id) {
        setVal(id, '');
    });
    const wrap = document.getElementById('pwdStrengthWrap');
    if (wrap) wrap.classList.add('d-none');
}

function clearPwdErrors() {
    ['currentPassword', 'newPassword', 'confirmPassword'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el) el.classList.remove('is-invalid');
    });
    ['currentPwdError', 'newPwdError', 'confirmPwdError'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.classList.add('d-none'); }
    });
}

function clearAllValidation() {
    clearUsernameStatus();
    clearPwdErrors();
    ['profileFullName'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el) el.classList.remove('is-invalid', 'is-valid');
    });
    ['fullNameError'].forEach(function (id) {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.classList.add('d-none'); }
    });
}

function setFieldError(inputId, errorId, msg) {
    const inp = document.getElementById(inputId);
    if (inp) inp.classList.add('is-invalid');
    const el = document.getElementById(errorId);
    if (el) { el.textContent = msg; el.classList.remove('d-none'); }
}

function setBtnLoading(btnId, txtClass, ldgClass, loading) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = loading;
    const txtEl = btn.querySelector('.' + txtClass);
    const ldgEl = btn.querySelector('.' + ldgClass);
    if (txtEl) txtEl.classList.toggle('d-none', loading);
    if (ldgEl) ldgEl.classList.toggle('d-none', !loading);
}

function showUploadProgress(show) {
    const el = document.getElementById('uploadProgressWrap');
    if (el) el.classList.toggle('d-none', !show);
}

function getVal(id) {
    const el = document.getElementById(id);
    return el ? el.value : '';
}

function setVal(id, val) {
    const el = document.getElementById(id);
    if (el) el.value = val;
}

function show(id)  { const el = document.getElementById(id); if (el) el.classList.remove('d-none'); }
function hide(id)  { const el = document.getElementById(id); if (el) el.classList.add('d-none'); }
function showEls(ids) { ids.forEach(show); }
function hideEls(ids) { ids.forEach(hide); }

function setDisabled(ids, v) {
    ids.forEach(function (id) {
        const el = document.getElementById(id);
        if (el) el.disabled = v;
    });
}

// ─── Toast notifications ───────────────────────────────────────────────────────
function showToast(message, type) {
    type = type || 'success';
    var container = document.getElementById('profileToastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'profileToastContainer';
        container.style.cssText = 'position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:9999;width:min(92vw,380px);pointer-events:none;';
        document.body.appendChild(container);
    }
    var icons = { success: 'fa-check-circle', error: 'fa-exclamation-circle', info: 'fa-info-circle' };
    var colors = { success: '#1F7D53', error: '#e74c3c', info: '#255F38' };
    var toast = document.createElement('div');
    toast.style.cssText = 'background:#fff;border-left:4px solid ' + (colors[type] || colors.success) + ';border-radius:8px;box-shadow:0 8px 24px rgba(0,0,0,.18);padding:12px 16px;margin-bottom:10px;font-weight:600;color:#1f2937;opacity:0;transform:translateY(-10px);transition:opacity .3s,transform .3s;font-size:.9rem;';
    toast.innerHTML = '<i class="fas ' + (icons[type] || icons.info) + '" style="color:' + (colors[type] || colors.success) + ';margin-right:8px;"></i>' + message;
    container.appendChild(toast);
    requestAnimationFrame(function () {
        toast.style.opacity = '1';
        toast.style.transform = 'translateY(0)';
    });
    setTimeout(function () {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px)';
        setTimeout(function () { toast.remove(); }, 350);
    }, 3500);
}
