import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { FiUser, FiMail, FiLock, FiEye, FiEyeOff } from 'react-icons/fi';
import { toast } from 'react-toastify';

const Register = () => {
  const { register } = useAuth();
  const navigate = useNavigate();
  
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [workspaceType, setWorkspaceType] = useState('PERSONAL');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!name || !email || !password || !confirmPassword) {
      return toast.warn('All fields are required');
    }

    if (password !== confirmPassword) {
      return toast.error('Passwords do not match');
    }

    // Client-side quick password strength validation
    const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/;
    if (!passwordRegex.test(password)) {
      return toast.error('Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!)');
    }

    setSubmitting(true);
    try {
      const response = await register(name, email, password, confirmPassword, workspaceName, workspaceType);
      toast.success(response.message || 'Registration successful!');
      
      // If first user, verification is bypassed. Go straight to login.
      if (response.message && response.message.toLowerCase().includes('bypassed')) {
        navigate('/login');
      } else {
        localStorage.setItem('verifyEmailAddress', email);
        navigate('/verify-email');
      }
    } catch (err) {
      toast.error(err || 'Registration failed. Email might be in use.');
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

      {/* Right Register Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in" style={{ padding: '2.5rem 2.5rem' }}>
          <h2 className="auth-title">Create Account</h2>
          <p className="auth-subtitle">Register for SmartDocs digital workspace</p>

          <form onSubmit={handleSubmit}>
            {/* Full Name Field */}
            <div className="auth-input-group">
              <label className="auth-input-label">Full Name</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiUser />
                </span>
                <input
                  type="text"
                  className="auth-input-field"
                  placeholder="Jane Doe"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </div>
            </div>

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
                  placeholder="jane.doe@company.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            {/* Workspace Type Selector */}
            <div className="auth-input-group animate-fade-in" style={{ marginBottom: '1.25rem' }}>
              <label className="auth-input-label">Workspace Type</label>
              <div className="auth-input-wrapper">
                <select
                  className="auth-input-field"
                  style={{ paddingLeft: '1rem', color: '#4a5568', background: '#fff', border: '1px solid #e2e8f0', borderRadius: '8px', width: '100%', height: '45px', appearance: 'auto' }}
                  value={workspaceType}
                  onChange={(e) => setWorkspaceType(e.target.value)}
                  required
                >
                  <option value="PERSONAL">Personal Documents</option>
                  <option value="COLLEGE">College / University</option>
                  <option value="COMPANY">Company / Business</option>
                  <option value="HOSPITAL">Hospital / Healthcare</option>
                  <option value="SCHOOL">School / K-12</option>
                  <option value="GOVERNMENT">Government / Agency</option>
                  <option value="STARTUP">Startup / Tech</option>
                  <option value="NGO">NGO / Non-Profit</option>
                </select>
              </div>
            </div>

            {/* Workspace Name Field */}
            {workspaceType !== 'PERSONAL' && (
              <div className="auth-input-group animate-fade-in" style={{ marginBottom: '1.25rem' }}>
                <label className="auth-input-label">Workspace Name</label>
                <div className="auth-input-wrapper">
                  <span className="auth-input-icon">
                    <FiUser />
                  </span>
                  <input
                    type="text"
                    className="auth-input-field"
                    placeholder="e.g. Acme Corporation"
                    value={workspaceName}
                    onChange={(e) => setWorkspaceName(e.target.value)}
                    required={workspaceType !== 'PERSONAL'}
                  />
                </div>
              </div>
            )}

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
                  placeholder="Min 8 characters"
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

            {/* Confirm Password Field */}
            <div className="auth-input-group">
              <label className="auth-input-label">Confirm Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiLock />
                </span>
                <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  className="auth-input-field"
                  placeholder="Repeat password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
                >
                  {showConfirmPassword ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
            </div>

            <div className="mb-4 text-muted small bg-light p-2.5 rounded" style={{ fontSize: '0.75rem', border: '1px solid #edf2f7', lineHeight: '1.4' }}>
              <strong>Security requirements:</strong> Min 8 chars, 1 uppercase, 1 lowercase, 1 number, 1 symbol (@#$%^&+=!).
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              className="auth-btn"
              disabled={submitting}
            >
              {submitting ? 'Registering...' : 'Register'}
            </button>
          </form>

          {/* Footer Login Link */}
          <div className="text-center mt-4">
            <p className="text-muted small m-0">
              Already have an account?{' '}
              <Link to="/login" className="fw-semibold text-decoration-none">
                Sign In
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Register;

