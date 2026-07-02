// Authentication UI helpers for protected pages

// Check if user is authenticated, redirect to login if not
function checkAuthentication() {
    const token = localStorage.getItem('token');
    const user = localStorage.getItem('user');
    
    if (!token || !user) {
        console.log('No authentication token found, redirecting to login...');
        window.location.href = '/login';
        return false;
    }
    return true;
}

// Get authenticated user info
function getAuthenticatedUser() {
    const userJson = localStorage.getItem('user');
    return userJson ? JSON.parse(userJson) : null;
}

// Logout
function logout() {
    console.log('Logging out...');
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = '/login';
}

// Setup header with user info and logout button
function setupAuthHeader() {
    const user = getAuthenticatedUser();
    
    if (!user) {
        console.log('No authenticated user, skipping header setup');
        return;
    }
    
    // Create header if it doesn't exist
    let header = document.querySelector('.auth-header');
    if (!header) {
        header = document.createElement('div');
        header.className = 'auth-header';
        document.body.prepend(header);
    }
    
    // Add user info and logout button
    header.innerHTML = `
        <div class="auth-header-content">
            <div class="user-info">
                <span class="user-icon">👤</span>
                <span class="user-name">${user.fullName || user.username}</span>
            </div>
            <button class="logout-btn" onclick="logout()">Logout</button>
        </div>
    `;
    
    // Add styles
    addAuthHeaderStyles();
}

// Add header styles
function addAuthHeaderStyles() {
    if (document.getElementById('auth-header-styles')) {
        return; // Already added
    }
    
    const style = document.createElement('style');
    style.id = 'auth-header-styles';
    style.textContent = `
        .auth-header {
            background: linear-gradient(135deg, #255F38 0%, #1F7D53 100%);
            padding: 12px 24px;
            display: flex;
            justify-content: flex-end;
            align-items: center;
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
            position: sticky;
            top: 0;
            z-index: 1000;
        }
        
        .auth-header-content {
            display: flex;
            align-items: center;
            gap: 20px;
        }
        
        .user-info {
            display: flex;
            align-items: center;
            gap: 10px;
            color: white;
            font-weight: 500;
        }
        
        .user-icon {
            font-size: 1.2rem;
        }
        
        .user-name {
            font-size: 0.95rem;
        }
        
        .logout-btn {
            background: rgba(255, 255, 255, 0.2);
            border: 1px solid rgba(255, 255, 255, 0.5);
            color: white;
            padding: 8px 16px;
            border-radius: 6px;
            cursor: pointer;
            font-weight: 600;
            transition: all 0.3s ease;
        }
        
        .logout-btn:hover {
            background: rgba(255, 255, 255, 0.3);
            border-color: rgba(255, 255, 255, 0.8);
        }
        
        .logout-btn:active {
            transform: scale(0.98);
        }
    `;
    document.head.appendChild(style);
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    // Check authentication first
    if (!checkAuthentication()) {
        return;
    }
    
    // Setup header
    setupAuthHeader();
});
