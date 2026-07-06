/* ============================================================
   FinTrack – Profile Modal
   Handles: load, avatar upload/remove, name/username edit,
            password change, save, logout, notifications.
   ============================================================ */

'use strict';

// ─── State ────────────────────────────────────────────────────────────────────
let _profileData      = null;
let _pendingChanges   = {};
let _usernameTimer    = null;
let _usernameValid    = true;   // true means "no blocking error"

// ─── Open / Close ─────────────────────────────────────────────────────────────
function openProfileModal() {
    const modalEl = document.getElementById('profileModal');
    if (!modalEl) return;
    const modal = bootstrap.Modal.getOrCreate(modalEl);
    modal.show();
    loadProfileData();
}

// Reset state when modal closes
document.addEventListener('DOMContentLoaded', () => {
    const modalEl = document.getElementById('profileModal');
    if (modalEl) {
        modalEl.addEventListener('hidden.bs.modal', resetModalState);

        // Animate chevron on password collapse toggle
        const passwordSection = document.getElementById('passwordSection');
        if (passwordSection) {
            passwordSection.addEventListener('show.bs.collapse', () => {
                document.querySelector('.toggle-chevron')?.classList.add('rotated');
            });
            passwordSection.addEventListener('hide.bs.collapse', () => {
                document.querySelector('.toggle-chevron')?.classList.remove('rotated');
            });
        }
    }

    // Username real-time validation
    const usernameInput = document.getElementById('profileUsername');
    if (usernameInput) {
        usernameInput.addEventListener('input', onUsernameInput);
        usernameInput.addEventListener('blur', () => {
            clearTimeout(_usernameTimer);
            if (usernameInput.value.trim()) checkUsernameAvailability(usernameInput.value.trim());
        });
    }

    // Track changes on name/username inputs
    ['profileFullName', 'profileUsername'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', () => trackFieldChange(id));
    });
});

// ─── Load ─────────────────────────────────────────────────────────────────────
async function loadProfileData() {
    setModalLoading(true);
    try {
        const res = await fetch('/api/profile');
        if (res.status === 401) { window.location.href = '/'; return; }
        if (!res.ok) throw new Error('Failed to load profile');
        _profileData = await res.json();
        populateModal(_profileData);
    } catch (err) {
        console.error(err);
        showNotification('Could not load profile data.', 'error');
    } finally {
        setModalLoading(false);
    }
}

function setModalLoading(loading) {
    const btn = document.getElementById('btnSaveProfile');
    if (btn) btn.disabled = loading;
}

// ─── Populate ─────────────────────────────────────────────────────────────────
function populateModal(data) {
    // Avatar
    renderAvatar(data.profile_picture, data.full_name);

    // Name preview
    document.getElementById('profileDisplayName').textContent = data.full_name || '';
    document.getElementById('profileDisplayUsername').textContent =
        data.username ? `@${data.username}` : '';

    // Form fields
    document.getElementById('profileFullName').value = data.full_name || '';
    document.getElementById('profileUsername').value = data.username || '';
    document.getElementById('profileEmail').value   = data.email || '';

    // Member since
    if (data.created_at) {
        document.getElementById('profileMemberSince').textContent =
            new Date(data.created_at).toLocaleDateString('en-US', {
                year: 'numeric', month: 'long', day: 'numeric'
            });
    }

    // Photo buttons
    refreshPhotoButtons(!!data.profile_picture);

    // Reset dirty tracking
    _pendingChanges = {};
    _usernameValid  = true;
    clearAllValidation();
}

// ─── Avatar render ─────────────────────────────────────────────────────────────
function renderAvatar(picUrl, fullName) {
    const img      = document.getElementById('profileAvatarImg');
    const initials = document.getElementById('profileAvatarInitials');
    const navImg   = document.getElementById('navbarAvatarImg');
    const navInit  = document.getElementById('navbarAvatarInitials');
    const initial  = fullName ? fullName.trim()[0].toUpperCase() : '?';

    if (picUrl) {
        img.src = picUrl + '?t=' + Date.now();
        img.classList.remove('d-none');
        initials.classList.add('d-none');
        if (navImg)  { navImg.src = img.src; navImg.classList.remove('d-none'); }
        if (navInit) navInit.classList.add('d-none');
    } else {
        img.classList.add('d-none');
        initials.textContent = initial;
        initials.classList.remove('d-none');
        if (navImg)  navImg.classList.add('d-none');
        if (navInit) { navInit.textContent = initial; navInit.classList.remove('d-none'); }
    }
}

function refreshPhotoButtons(hasPicture) {
    const btnRemove  = document.getElementById('btnRemovePhoto');
    const btnText    = document.getElementById('photoUploadBtnText');
    if (hasPicture) {
        btnRemove.classList.remove('d-none');
        btnText.textContent = 'Change Photo';
    } else {
        btnRemove.classList.add('d-none');
        btnText.textContent = 'Upload Photo';
    }
}

// ─── Profile Picture Upload ────────────────────────────────────────────────────
function triggerPhotoUpload() {
    document.getElementById('profilePictureInput').click();
}

document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('profilePictureInput');
    if (input) input.addEventListener('change', onPictureFileSelected);
});

function onPictureFileSelected(e) {
    const file = e.target.files[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
        showNotification('Unsupported format. Please use JPG, PNG, or WebP.', 'error');
        e.target.value = '';
        return;
    }
    if (file.size > 5 * 1024 * 1024) {
        showNotification('File is too large. Maximum size is 5 MB.', 'error');
        e.target.value = '';
        return;
    }

    // Show local preview immediately
    const reader = new FileReader();
    reader.onload = ev => {
        const img      = document.getElementById('profileAvatarImg');
        const initials = document.getElementById('profileAvatarInitials');
        img.src = ev.target.result;
        img.classList.remove('d-none');
        initials.classList.add('d-none');
    };
    reader.readAsDataURL(file);

    uploadProfilePicture(file);
    e.target.value = '';
}

async function uploadProfilePicture(file) {
    const progress = document.getElementById('uploadProgress');
    progress.classList.remove('d-none');
    document.getElementById('btnUploadPhoto').disabled = true;
    document.getElementById('btnRemovePhoto').disabled = true;

    const formData = new FormData();
    formData.append('picture', file);

    try {
        const res = await fetch('/api/profile/picture', { method: 'POST', body: formData });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Upload failed');

        // Update stored profile URL
        if (_profileData) _profileData.profile_picture = data.profile_picture;
        renderAvatar(data.profile_picture, _profileData?.full_name || '');
        refreshPhotoButtons(true);
        showNotification('Profile picture updated!');

        // Update navbar avatar with new image
        const navImg = document.getElementById('navbarAvatarImg');
        if (navImg) navImg.src = data.profile_picture + '?t=' + Date.now();

    } catch (err) {
        console.error(err);
        showNotification(err.message || 'Failed to upload picture.', 'error');
        // Revert preview
        if (_profileData) renderAvatar(_profileData.profile_picture, _profileData.full_name);
    } finally {
        progress.classList.add('d-none');
        document.getElementById('btnUploadPhoto').disabled = false;
        document.getElementById('btnRemovePhoto').disabled = false;
    }
}

async function removeProfilePicture() {
    if (!confirm('Remove your profile picture?')) return;

    document.getElementById('btnRemovePhoto').disabled = true;
    try {
        const res = await fetch('/api/profile/picture', { method: 'DELETE' });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Could not remove picture');

        if (_profileData) _profileData.profile_picture = null;
        renderAvatar(null, _profileData?.full_name || '');
        refreshPhotoButtons(false);
        showNotification('Profile picture removed.');
    } catch (err) {
        console.error(err);
        showNotification(err.message || 'Failed to remove picture.', 'error');
    } finally {
        document.getElementById('btnRemovePhoto').disabled = false;
    }
}

// ─── Username validation ───────────────────────────────────────────────────────
function onUsernameInput() {
    const val = document.getElementById('profileUsername').value.trim();
    clearUsernameStatus();
    _usernameValid = true;

    if (!val) { hideElement('usernameStatusIcon'); return; }

    const err = clientValidateUsername(val);
    if (err) {
        setUsernameError(err);
        return;
    }

    // Debounce server check
    clearTimeout(_usernameTimer);
    showElement('usernameStatusIcon');
    showSpinner('usernameSpinner');
    _usernameTimer = setTimeout(() => checkUsernameAvailability(val), 500);
}

async function checkUsernameAvailability(username) {
    try {
        showElement('usernameStatusIcon');
        showSpinner('usernameSpinner');
        const res = await fetch(`/api/profile/check-username?username=${encodeURIComponent(username)}`);
        const data = await res.json();
        hideSpinner('usernameSpinner');
        if (data.available) {
            showIcon('usernameOk');
            setUsernameSuccess(data.message || 'Username is available');
            _usernameValid = true;
        } else {
            showIcon('usernameFail');
            setUsernameError(data.error || 'Username is taken');
            _usernameValid = false;
        }
    } catch (_) {
        hideSpinner('usernameSpinner');
        hideElement('usernameStatusIcon');
    }
}

function clientValidateUsername(val) {
    if (val.length < 3) return 'Username must be at least 3 characters';
    if (val.length > 30) return 'Username must not exceed 30 characters';
    if (!/^[a-zA-Z0-9_.]+$/.test(val)) return 'Only letters, numbers, _ and . allowed';
    return null;
}

function setUsernameError(msg) {
    _usernameValid = false;
    showElement('usernameStatusIcon');
    hideSpinner('usernameSpinner');
    showIcon('usernameFail');
    const el = document.getElementById('usernameError');
    el.textContent = msg;
    el.classList.remove('d-none');
    document.getElementById('usernameSuccess').classList.add('d-none');
    document.getElementById('profileUsername').classList.add('is-invalid');
    document.getElementById('profileUsername').classList.remove('is-valid');
}

function setUsernameSuccess(msg) {
    _usernameValid = true;
    const el = document.getElementById('usernameSuccess');
    el.textContent = msg;
    el.classList.remove('d-none');
    document.getElementById('usernameError').classList.add('d-none');
    document.getElementById('profileUsername').classList.remove('is-invalid');
    document.getElementById('profileUsername').classList.add('is-valid');
}

function clearUsernameStatus() {
    ['usernameError', 'usernameSuccess'].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.classList.add('d-none'); el.textContent = ''; }
    });
    const input = document.getElementById('profileUsername');
    if (input) { input.classList.remove('is-invalid', 'is-valid'); }
    hideIcon('usernameOk');
    hideIcon('usernameFail');
}

// ─── Track field changes ───────────────────────────────────────────────────────
function trackFieldChange(fieldId) {
    if (!_profileData) return;
    const el = document.getElementById(fieldId);
    if (!el) return;
    const key = fieldId === 'profileFullName' ? 'full_name' : 'username';
    const orig = key === 'full_name' ? (_profileData.full_name || '') : (_profileData.username || '');
    if (el.value.trim() !== orig) {
        _pendingChanges[key] = el.value.trim();
    } else {
        delete _pendingChanges[key];
    }

    // Live-update the display name
    if (fieldId === 'profileFullName' && el.value.trim()) {
        document.getElementById('profileDisplayName').textContent = el.value.trim();
    }
    if (fieldId === 'profileUsername') {
        const uname = el.value.trim();
        document.getElementById('profileDisplayUsername').textContent =
            uname ? `@${uname}` : '';
    }
}

// ─── Save profile (name + username) ───────────────────────────────────────────
async function saveProfile() {
    clearAllValidation();

    // Validate full name
    const fullName = document.getElementById('profileFullName').value.trim();
    if (!fullName) {
        showFieldError('profileFullName', 'fullNameError', 'Full name cannot be empty');
        return;
    }

    // Validate username if changed
    const username = document.getElementById('profileUsername').value.trim();
    if (username) {
        const err = clientValidateUsername(username);
        if (err) { setUsernameError(err); return; }
        if (!_usernameValid) {
            setUsernameError('Please resolve the username issue before saving');
            return;
        }
    }

    // Build payload with only changed fields
    const payload = {};
    const origName = _profileData?.full_name || '';
    const origUser = _profileData?.username || '';
    if (fullName !== origName)   payload.full_name = fullName;
    if (username !== origUser)   payload.username  = username;

    if (Object.keys(payload).length === 0) {
        showNotification('No changes to save.', 'info');
        return;
    }

    setBtnLoading('btnSaveProfile', 'btn-save-text', 'btn-save-loading', true);
    try {
        const res = await fetch('/api/profile', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.error || 'Save failed');

        _profileData = data.user;
        _pendingChanges = {};

        // Refresh display
        document.getElementById('profileDisplayName').textContent = data.user.full_name;
        document.getElementById('profileDisplayUsername').textContent =
            data.user.username ? `@${data.user.username}` : '';

        // Refresh navbar initials if no picture
        if (!data.user.profile_picture) {
            const navInit = document.getElementById('navbarAvatarInitials');
            if (navInit) navInit.textContent = data.user.full_name[0].toUpperCase();
        }

        showNotification('Profile saved successfully!');
        clearUsernameStatus();
    } catch (err) {
        console.error(err);
        showNotification(err.message || 'Failed to save profile.', 'error');
    } finally {
        setBtnLoading('btnSaveProfile', 'btn-save-text', 'btn-save-loading', false);
    }
}

// ─── Password Change ───────────────────────────────────────────────────────────
async function submitPasswordChange() {
    // Clear previous errors
    ['currentPasswordError', 'newPasswordError', 'confirmPasswordError'].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.classList.add('d-none'); }
    });
    ['currentPassword', 'newPassword', 'confirmPassword'].forEach(id => {
        document.getElementById(id)?.classList.remove('is-invalid');
    });

    const currentPwd = document.getElementById('currentPassword').value;
    const newPwd     = document.getElementById('newPassword').value;
    const confirmPwd = document.getElementById('confirmPassword').value;

    let hasError = false;
    if (!currentPwd) {
        showFieldError('currentPassword', 'currentPasswordError', 'Current password is required');
        hasError = true;
    }
    if (!newPwd) {
        showFieldError('newPassword', 'newPasswordError', 'New password is required');
        hasError = true;
    }
    if (!confirmPwd) {
        showFieldError('confirmPassword', 'confirmPasswordError', 'Please confirm your new password');
        hasError = true;
    }
    if (hasError) return;

    if (newPwd !== confirmPwd) {
        showFieldError('confirmPassword', 'confirmPasswordError', 'Passwords do not match');
        return;
    }

    setBtnLoading('btnChangePassword', 'btn-pwd-text', 'btn-pwd-loading', true);
    try {
        const res = await fetch('/api/profile/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                current_password: currentPwd,
                new_password:     newPwd,
                confirm_password: confirmPwd
            })
        });
        const data = await res.json();
        if (!res.ok) {
            const msg = data.error || 'Failed to change password';
            if (msg.toLowerCase().includes('current')) {
                showFieldError('currentPassword', 'currentPasswordError', msg);
            } else if (msg.toLowerCase().includes('match')) {
                showFieldError('confirmPassword', 'confirmPasswordError', msg);
            } else {
                showFieldError('newPassword', 'newPasswordError', msg);
            }
            return;
        }
        clearPasswordFields();
        // Collapse the password section
        const section = document.getElementById('passwordSection');
        if (section) bootstrap.Collapse.getInstance(section)?.hide();
        showNotification('Password changed successfully!');
    } catch (err) {
        console.error(err);
        showNotification(err.message || 'Failed to change password.', 'error');
    } finally {
        setBtnLoading('btnChangePassword', 'btn-pwd-text', 'btn-pwd-loading', false);
    }
}

// ─── Password Strength Indicator ──────────────────────────────────────────────
function updatePasswordStrength(value) {
    const wrap  = document.getElementById('passwordStrengthWrap');
    const fill  = document.getElementById('passwordStrengthFill');
    const label = document.getElementById('passwordStrengthLabel');
    if (!wrap) return;

    if (!value) { wrap.classList.add('d-none'); return; }
    wrap.classList.remove('d-none');

    let score = 0;
    if (value.length >= 8)  score++;
    if (/[A-Z]/.test(value)) score++;
    if (/[a-z]/.test(value)) score++;
    if (/\d/.test(value))    score++;
    if (/[!@#$%^&*()\-_=+\[\]{};:'",.<>/?\\|`~]/.test(value)) score++;

    const levels = [
        { pct: '20%',  cls: 'bg-danger',  text: 'Very Weak'  },
        { pct: '40%',  cls: 'bg-danger',  text: 'Weak'       },
        { pct: '60%',  cls: 'bg-warning', text: 'Fair'       },
        { pct: '80%',  cls: 'bg-info',    text: 'Good'       },
        { pct: '100%', cls: 'bg-success', text: 'Strong ✓'   }
    ];
    const level = levels[score - 1] || levels[0];
    fill.style.width = level.pct;
    fill.className   = `password-strength-fill ${level.cls}`;
    label.textContent = level.text;
    label.className   = `password-strength-label ${level.cls.replace('bg-', 'text-')}`;
}

// ─── Logout ───────────────────────────────────────────────────────────────────
async function handleLogout() {
    try {
        await fetch('/auth/logout', { method: 'POST' });
    } finally {
        window.location.href = '/';
    }
}

// ─── Utility helpers ──────────────────────────────────────────────────────────
function resetModalState() {
    _pendingChanges = {};
    _usernameValid  = true;
    clearPasswordFields();
    clearAllValidation();
    // Collapse password section
    const section = document.getElementById('passwordSection');
    if (section) {
        const inst = bootstrap.Collapse.getInstance(section);
        if (inst) inst.hide();
    }
}

function clearPasswordFields() {
    ['currentPassword', 'newPassword', 'confirmPassword'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    const wrap = document.getElementById('passwordStrengthWrap');
    if (wrap) wrap.classList.add('d-none');
}

function clearAllValidation() {
    ['profileFullName', 'profileUsername', 'currentPassword', 'newPassword', 'confirmPassword'].forEach(id => {
        document.getElementById(id)?.classList.remove('is-invalid', 'is-valid');
    });
    ['fullNameError', 'usernameError', 'usernameSuccess',
     'currentPasswordError', 'newPasswordError', 'confirmPasswordError'].forEach(id => {
        const el = document.getElementById(id);
        if (el) { el.textContent = ''; el.classList.add('d-none'); }
    });
    hideElement('usernameStatusIcon');
    hideIcon('usernameOk'); hideIcon('usernameFail');
}

function showFieldError(inputId, errorId, message) {
    const input = document.getElementById(inputId);
    const errEl = document.getElementById(errorId);
    if (input) input.classList.add('is-invalid');
    if (errEl) { errEl.textContent = message; errEl.classList.remove('d-none'); }
}

function togglePwdVisibility(inputId, btn) {
    const input = document.getElementById(inputId);
    const icon  = btn.querySelector('i');
    if (!input || !icon) return;
    if (input.type === 'password') {
        input.type = 'text';
        icon.classList.replace('fa-eye', 'fa-eye-slash');
    } else {
        input.type = 'password';
        icon.classList.replace('fa-eye-slash', 'fa-eye');
    }
}

function setBtnLoading(btnId, textClass, loadingClass, loading) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = loading;
    btn.querySelector(`.${textClass}`)?.classList.toggle('d-none', loading);
    btn.querySelector(`.${loadingClass}`)?.classList.toggle('d-none', !loading);
}

function showSpinner(id)  { document.getElementById(id)?.classList.remove('d-none'); }
function hideSpinner(id)  { document.getElementById(id)?.classList.add('d-none'); }
function showIcon(id)     { document.getElementById(id)?.classList.remove('d-none'); }
function hideIcon(id)     { document.getElementById(id)?.classList.add('d-none'); }
function showElement(id)  { document.getElementById(id)?.classList.remove('d-none'); }
function hideElement(id)  { document.getElementById(id)?.classList.add('d-none'); }

// ─── Global notification (shared across all pages) ───────────────────────────
function showNotification(message, type = 'success') {
    const container = document.getElementById('toastContainer') || (() => {
        const c = document.createElement('div');
        c.id = 'toastContainer';
        c.className = 'notification-toast-container';
        document.body.appendChild(c);
        return c;
    })();

    const toast = document.createElement('div');
    const icons = { success: 'fa-check-circle', error: 'fa-exclamation-circle', info: 'fa-info-circle' };
    const icon  = icons[type] || icons.info;
    toast.className = `notification-toast notification-${type}`;
    toast.innerHTML = `<i class="fas ${icon} me-2"></i>${message}`;
    container.appendChild(toast);

    requestAnimationFrame(() => toast.classList.add('show'));
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 350);
    }, 3500);
}
