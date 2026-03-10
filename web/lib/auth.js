// ============================================================================
// Authentication Library - Handles JWT token management and API integration
// ============================================================================

// In-memory session cache
let sessionCache = {
    token: null,
    userId: null,
    email: null,
    firstName: null,
    role: null
};

// ============================================================================
// Token Management Functions
// ============================================================================

function getToken() {
    if (sessionCache.token) {
        return sessionCache.token;
    }
    const token = localStorage.getItem('token');
    if (token) {
        sessionCache.token = token;
    }
    return token;
}

function setToken(token) {
    sessionCache.token = token;
    if (token) {
        localStorage.setItem('token', token);
    } else {
        localStorage.removeItem('token');
    }
}

function clearToken() {
    sessionCache.token = null;
    localStorage.removeItem('token');
}

// ============================================================================
// Session Management Functions
// ============================================================================

function saveSession(authResponse) {
    const { token, userId, email, firstName, role } = authResponse;

    setToken(token);
    sessionCache.userId = userId;
    sessionCache.email = email;
    sessionCache.firstName = firstName;
    sessionCache.role = role;

    localStorage.setItem('userId', userId);
    localStorage.setItem('email', email);
    localStorage.setItem('firstName', firstName);
    localStorage.setItem('role', role);
}

function clearSession() {
    sessionCache = {
        token: null,
        userId: null,
        email: null,
        firstName: null,
        role: null
    };

    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('email');
    localStorage.removeItem('firstName');
    localStorage.removeItem('role');
}

function getUserInfo() {
    return {
        userId: sessionCache.userId || localStorage.getItem('userId'),
        email: sessionCache.email || localStorage.getItem('email'),
        firstName: sessionCache.firstName || localStorage.getItem('firstName'),
        role: sessionCache.role || localStorage.getItem('role')
    };
}

function getUserRole() {
    return sessionCache.role || localStorage.getItem('role');
}

// ============================================================================
// API Helper with Auto-Authorization and Timeout Protection
// ============================================================================

async function apiFetch(path, options = {}) {
    const token = getToken();
    const base = (typeof API_BASE !== 'undefined') ? API_BASE : 'http://localhost:8080';
    const url = path.startsWith('http') ? path : base + path;
    const timeout = options.timeout || 15000; // Default 15 second timeout

    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers
    };
    delete config.timeout; // Remove custom timeout option

    try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);
        config.signal = controller.signal;

        const response = await fetch(url, config);
        clearTimeout(timeoutId);

        // Handle 401 Unauthorized - redirect to login (but not for auth endpoints)
        if (response.status === 401 && !path.includes('/api/auth/')) {
            clearSession();
            window.location.href = '/web/auth/login.html';
            return null;
        }

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        }

        return await response.text();
    } catch (error) {
        if (error.name === 'AbortError') {
            console.error('API request timeout:', path);
            throw new Error(`Request to ${path} timed out after ${timeout}ms`);
        }
        console.error('API Error:', error);
        throw error;
    }
}

// ============================================================================
// Login Helper
// ============================================================================

async function loginUser(username, password) {
    const response = await apiFetch('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username, password })
    });

    if (response && response.token) {
        saveSession(response);
        return response;
    }

    throw new Error('Login failed');
}

async function signupUser(firstName, lastName, email, mobileNumber, password) {
    const response = await apiFetch('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
            firstName,
            lastName,
            email,
            mobileNumber,
            password
        })
    });

    if (response && response.token) {
        saveSession(response);
        return response;
    }

    throw new Error('Signup failed');
}

// Export functions
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
        getToken,
        setToken,
        clearToken,
        saveSession,
        clearSession,
        getUserInfo,
        getUserRole,
        apiFetch,
        loginUser,
        signupUser
    };
}
