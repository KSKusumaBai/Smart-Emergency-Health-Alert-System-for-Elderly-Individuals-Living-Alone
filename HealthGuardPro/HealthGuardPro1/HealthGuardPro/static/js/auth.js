// Authentication handling
class AuthManager {
    constructor() {
        this.checkAuthStatus();
    }

    checkAuthStatus() {
        // Check if user is logged in by checking session
        // This would typically validate with the server
        const currentPath = window.location.pathname;
        
        if (currentPath === '/' || currentPath.includes('index.html')) {
            // Already on login page
            return;
        }
        
        // For demo purposes, we'll check if user data exists
        // In production, this should validate with server session
        if (!this.isLoggedIn()) {
            window.location.href = '/';
        }
    }

    isLoggedIn() {
        // Simple check - in production, validate server session
        return sessionStorage.getItem('userLoggedIn') === 'true';
    }

    async login(email, password) {
        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ email, password })
            });

            const data = await response.json();

            if (response.ok) {
                sessionStorage.setItem('userLoggedIn', 'true');
                sessionStorage.setItem('userId', data.user_id);
                window.location.href = '/dashboard';
                return { success: true };
            } else {
                return { success: false, error: data.error };
            }
        } catch (error) {
            return { success: false, error: 'Network error' };
        }
    }

    async register(name, email, password) {
        try {
            const response = await fetch('/api/register', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ name, email, password })
            });

            const data = await response.json();

            if (response.ok) {
                sessionStorage.setItem('userLoggedIn', 'true');
                sessionStorage.setItem('userId', data.user_id);
                window.location.href = '/dashboard';
                return { success: true };
            } else {
                return { success: false, error: data.error };
            }
        } catch (error) {
            return { success: false, error: 'Network error' };
        }
    }

    async logout() {
        try {
            await fetch('/api/logout', { method: 'POST' });
        } catch (error) {
            console.error('Logout error:', error);
        }
        
        sessionStorage.clear();
        window.location.href = '/';
    }
}

// Global auth manager instance
const authManager = new AuthManager();

// Login form handler
if (document.getElementById('loginFormSubmit')) {
    document.getElementById('loginFormSubmit').addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const email = document.getElementById('loginEmail').value;
        const password = document.getElementById('loginPassword').value;
        
        // Show loading
        const submitBtn = e.target.querySelector('button[type="submit"]');
        const originalText = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Signing in...';
        submitBtn.disabled = true;
        
        const result = await authManager.login(email, password);
        
        if (!result.success) {
            alert('Login failed: ' + result.error);
            submitBtn.innerHTML = originalText;
            submitBtn.disabled = false;
        }
    });
}

// Register form handler
if (document.getElementById('registerFormSubmit')) {
    document.getElementById('registerFormSubmit').addEventListener('submit', async function(e) {
        e.preventDefault();
        
        const name = document.getElementById('registerName').value;
        const email = document.getElementById('registerEmail').value;
        const password = document.getElementById('registerPassword').value;
        
        // Show loading
        const submitBtn = e.target.querySelector('button[type="submit"]');
        const originalText = submitBtn.innerHTML;
        submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Creating account...';
        submitBtn.disabled = true;
        
        const result = await authManager.register(name, email, password);
        
        if (!result.success) {
            alert('Registration failed: ' + result.error);
            submitBtn.innerHTML = originalText;
            submitBtn.disabled = false;
        }
    });
}

// Global logout function
function logout() {
    authManager.logout();
}
