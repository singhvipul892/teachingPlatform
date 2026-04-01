// ============================================================================
// Router Library - Handles role-based navigation and access control
// ============================================================================

// ============================================================================
// Auth Check Functions
// ============================================================================

function checkAuth() {
    const token = getToken();

    if (!token) {
        // No token - redirect to login
        window.location.href = '/web/auth/login.html';
        return { authenticated: false, role: null };
    }

    const role = getUserRole();
    if (!role) {
        // Invalid session - redirect to login
        clearSession();
        window.location.href = '/web/auth/login.html';
        return { authenticated: false, role: null };
    }

    return { authenticated: true, role };
}

/**
 * Non-blocking auth check with timeout.
 * Returns immediately if auth info is in cache/localStorage.
 * Does NOT redirect on auth failure - caller must handle.
 * Used when you need auth info but don't want to block page init.
 */
function checkAuthNonBlocking(timeoutMs = 3000) {
    try {
        const token = getToken();
        if (!token) {
            return { authenticated: false, role: null };
        }

        const role = getUserRole();
        if (!role) {
            clearSession();
            return { authenticated: false, role: null };
        }

        return { authenticated: true, role };
    } catch (error) {
        console.error('Auth check error:', error);
        return { authenticated: false, role: null };
    }
}

function requireRole(allowedRoles) {
    const auth = checkAuth();

    if (!auth.authenticated) {
        return false; // checkAuth already redirected
    }

    if (!allowedRoles.includes(auth.role)) {
        // User has wrong role - redirect based on role
        redirectByRole(auth.role);
        return false;
    }

    return true; // User is allowed
}

// ============================================================================
// Role-Based Redirect
// ============================================================================

function redirectByRole(role) {
    if (role === 'ADMIN') {
        window.location.href = '/web/admin/index.html';
    } else if (role === 'USER' || role === 'TEACHER') {
        window.location.href = '/web/student/index.html';
    } else {
        // Unknown role - logout
        clearSession();
        window.location.href = '/web/auth/login.html';
    }
}

// ============================================================================
// Logout Helper
// ============================================================================

function logout() {
    clearSession();
    window.location.href = '/web/auth/login.html';
}

// Export functions
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        checkAuth,
        requireRole,
        redirectByRole,
        logout
    };
}
