// API Helper Functions

const API_BASE = '/api';

// Categories API
async function getCategories() {
    try {
        const response = await fetch(`${API_BASE}/categories`);
        return await response.json();
    } catch (error) {
        console.error('Error fetching categories:', error);
        return [];
    }
}

async function createCategory(categoryData) {
    try {
        const response = await fetch(`${API_BASE}/categories`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(categoryData)
        });
        return await response.json();
    } catch (error) {
        console.error('Error creating category:', error);
        throw error;
    }
}

async function updateCategory(id, categoryData) {
    try {
        const response = await fetch(`${API_BASE}/categories/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(categoryData)
        });
        return await response.json();
    } catch (error) {
        console.error('Error updating category:', error);
        throw error;
    }
}

async function deleteCategory(id) {
    try {
        const response = await fetch(`${API_BASE}/categories/${id}`, {
            method: 'DELETE'
        });
        return response.ok;
    } catch (error) {
        console.error('Error deleting category:', error);
        throw error;
    }
}

// Transactions API
async function getTransactions(filters = {}) {
    try {
        const params = new URLSearchParams(filters);
        const response = await fetch(`${API_BASE}/transactions?${params}`);
        return await response.json();
    } catch (error) {
        console.error('Error fetching transactions:', error);
        return [];
    }
}

async function createTransaction(transactionData) {
    try {
        const response = await fetch(`${API_BASE}/transactions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(transactionData)
        });
        return await response.json();
    } catch (error) {
        console.error('Error creating transaction:', error);
        throw error;
    }
}

async function updateTransaction(id, transactionData) {
    try {
        const response = await fetch(`${API_BASE}/transactions/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(transactionData)
        });
        return await response.json();
    } catch (error) {
        console.error('Error updating transaction:', error);
        throw error;
    }
}

async function deleteTransaction(id) {
    try {
        const response = await fetch(`${API_BASE}/transactions/${id}`, {
            method: 'DELETE'
        });
        return response.ok;
    } catch (error) {
        console.error('Error deleting transaction:', error);
        throw error;
    }
}

// Analytics API
async function getSummary() {
    try {
        const response = await fetch(`${API_BASE}/analytics/summary`);
        return await response.json();
    } catch (error) {
        console.error('Error fetching summary:', error);
        return { total_income: 0, total_expense: 0, balance: 0, transaction_count: 0 };
    }
}

async function getCategoryBreakdown() {
    try {
        const response = await fetch(`${API_BASE}/analytics/category-breakdown`);
        return await response.json();
    } catch (error) {
        console.error('Error fetching category breakdown:', error);
        return [];
    }
}

async function getMonthlyAnalytics() {
    try {
        const response = await fetch(`${API_BASE}/analytics/monthly`);
        return await response.json();
    } catch (error) {
        console.error('Error fetching monthly analytics:', error);
        return [];
    }
}

// Utility Functions
function formatCurrency(value) {
    return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD'
    }).format(value);
}

function formatDate(dateString) {
    return new Date(dateString).toLocaleDateString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric'
    });
}

function formatDateTime(dateString) {
    return new Date(dateString).toLocaleString('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function getBadgeClass(transactionType) {
    return transactionType === 'income' ? 'bg-success' : 'bg-danger';
}

function getTypeLabel(transactionType) {
    return transactionType.charAt(0).toUpperCase() + transactionType.slice(1);
}
