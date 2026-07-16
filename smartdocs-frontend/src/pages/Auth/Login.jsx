import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { FiMail, FiLock, FiEye, FiEyeOff } from 'react-icons/fi';
import { toast } from 'react-toastify';

const Login = () => {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      return toast.warn('Please fill in all required fields');
    }

    setSubmitting(true);
    try {
      await login(email, password, rememberMe);
      toast.success('Welcome back to SmartDocs!');
      navigate('/dashboard');
    } catch (err) {
      toast.error(err || 'Invalid email or password');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-split-container">
      {/* Left Branding Panel */}
      <div className="auth-left-panel">
        <div className="auth-left-content">
          <h1 className="auth-left-title">SmartDocs</h1>
          <p className="auth-left-subtitle">
            Enterprise Digital Document Management System
          </p>
          <ul className="auth-feature-list">
            <li className="auth-feature-item">Secure File Storage</li>
            <li className="auth-feature-item">Document Version Control</li>
            <li className="auth-feature-item">Role-Based Access</li>
            <li className="auth-feature-item">Advanced Search System</li>
          </ul>
        </div>
      </div>

      {/* Right Login Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in">
          <h2 className="auth-title">Welcome Back</h2>
          <p className="auth-subtitle">Login to continue</p>

          <form onSubmit={handleSubmit}>
            {/* Email Field */}
            <div className="auth-input-group">
              <label className="auth-input-label">Email Address</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiMail />
                </span>
                <input
                  type="email"
                  className="auth-input-field"
                  placeholder="you@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Password Field */}
            <div className="auth-input-group">
              <label className="auth-input-label">Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiLock />
                </span>
                <input
                  type={showPassword ? 'text' : 'password'}
                  className="auth-input-field"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
            </div>

            {/* Remember Me & Forgot Password */}
            <div className="d-flex justify-content-between align-items-center mb-4">
              <div className="form-check m-0">
                <input
                  type="checkbox"
                  className="form-check-input"
                  id="rememberMe"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                <label className="form-check-label text-muted small" htmlFor="rememberMe" style={{ cursor: 'pointer' }}>
                  Remember Me
                </label>
              </div>
              <Link to="/forgot-password" style={{ fontSize: '0.85rem', textDecoration: 'none', fontWeight: '500' }}>
                Forgot Password?
              </Link>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              className="auth-btn"
              disabled={submitting}
            >
              {submitting ? 'Authenticating...' : 'Login'}
            </button>
          </form>

          {/* Footer Registration Link */}
          <div className="text-center mt-4">
            <p className="text-muted small m-0">
              Don't have an account?{' '}
              <Link to="/register" className="fw-semibold text-decoration-none">
                Register
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;

