// JWT Token Management and API Helper
const TOKEN_KEY = 'finzin_token';
const USER_KEY = 'finzin_user';

// Store token and user info
function setAuth(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
}

// Get stored token
function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

// Get stored user
function getUser() {
    const user = localStorage.getItem(USER_KEY);
    return user ? JSON.parse(user) : null;
}

// Clear auth
function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

// Check if authenticated
function isAuthenticated() {
    return getToken() !== null;
}

// Make authenticated API request
async function fetchWithAuth(url, options = {}) {
    const token = getToken();
    
    if (!token) {
        window.location.href = '/login';
        return;
    }
    
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        ...options.headers
    };
    
    try {
        const response = await fetch(url, {
            ...options,
            headers
        });
        
        // If unauthorized, redirect to login
        if (response.status === 401) {
            clearAuth();
            window.location.href = '/login';
            return null;
        }
        
        return response;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
}

// Logout
function logout() {
    clearAuth();
    window.location.href = '/login';
}

// Auto-redirect if not authenticated on protected pages
document.addEventListener('DOMContentLoaded', function() {
    const currentPath = window.location.pathname;
    const publicPages = ['/', '/login', '/signup', '/index.html', '/login.html', '/signup.html'];
    const isPublicPage = publicPages.includes(currentPath) || currentPath === '';
    
    if (!isPublicPage && !isAuthenticated()) {
        window.location.href = '/login';
    }
});
