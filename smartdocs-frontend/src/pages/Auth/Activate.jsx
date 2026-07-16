import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../../services/api';
import { FiLock, FiEye, FiEyeOff, FiCheckCircle, FiAlertTriangle, FiMail } from 'react-icons/fi';
import { toast } from 'react-toastify';

const Activate = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [userDetails, setUserDetails] = useState(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  // Expiration resend states
  const [resendEmail, setResendEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resendSuccess, setResendSuccess] = useState(false);

  const queryParams = new URLSearchParams(window.location.search);
  const token = queryParams.get('token');

  useEffect(() => {
    if (!token) {
      setError('Activation token is missing. Please check your invitation email.');
      setLoading(false);
      return;
    }

    const verifyToken = async () => {
      try {
        const res = await api.get(`/auth/activate/verify?token=${token}`);
        setUserDetails(res.data);
      } catch (err) {
        setError(err.response?.data?.message || 'Activation link is invalid or expired.');
      } finally {
        setLoading(false);
      }
    };

    verifyToken();
  }, [token]);

  const handleActivateSubmit = async (e) => {
    e.preventDefault();
    if (!password || !confirmPassword) {
      return toast.warn('Please fill in all required fields');
    }
    if (password.length < 8) {
      return toast.warn('Password must be at least 8 characters long');
    }
    if (password !== confirmPassword) {
      return toast.error('Passwords do not match');
    }

    setSubmitting(true);
    try {
      await api.post('/auth/activate', {
        token,
        password,
        confirmPassword
      });
      setSuccess(true);
      toast.success('Account activated successfully!');
      setTimeout(() => {
        navigate('/login');
      }, 3000);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Activation failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleResendLink = async (e) => {
    e.preventDefault();
    if (!resendEmail) {
      return toast.warn('Please enter your email address');
    }

    setResending(true);
    try {
      await api.post(`/auth/activate/resend?email=${encodeURIComponent(resendEmail)}`);
      setResendSuccess(true);
      toast.success('A new activation email has been sent!');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to resend activation link.');
    } finally {
      setResending(false);
    }
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center vh-100 bg-light">
        <div className="text-center">
          <div className="spinner-border text-primary mb-3" role="status"></div>
          <p className="text-muted">Verifying secure activation token...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-split-container">
      {/* Left Panel */}
      <div className="auth-left-panel">
        <div className="auth-left-content">
          <h1 className="auth-left-title">SmartDocs</h1>
          <p className="auth-left-subtitle">
            Enterprise Digital Document Management System
          </p>
          <ul className="auth-feature-list">
            <li className="auth-feature-item">Secure Account Onboarding</li>
            <li className="auth-feature-item">Enterprise Password Management</li>
            <li className="auth-feature-item">Role-Based Document Sharing</li>
            <li className="auth-feature-item">Compliance & Audit Trails</li>
          </ul>
        </div>
      </div>

      {/* Right Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in">
          {success ? (
            <div className="text-center py-4">
              <FiCheckCircle size={54} className="text-success mb-3 animate-bounce" />
              <h3 className="fw-bold mb-2">Account Activated!</h3>
              <p className="text-muted mb-4">
                Your email has been verified, and your account status is now set to <strong>Active</strong>.
              </p>
              <p className="text-primary small">Redirecting you to login...</p>
              <Link to="/login" className="btn btn-primary w-100 mt-2">Go to Login</Link>
            </div>
          ) : error ? (
            <div className="py-2">
              <div className="text-center mb-4 text-danger">
                <FiAlertTriangle size={54} className="mb-2" />
                <h4 className="fw-bold text-dark mt-2">Activation Issue</h4>
                <p className="text-muted small">{error}</p>
              </div>

              {resendSuccess ? (
                <div className="alert alert-success text-center small">
                  A fresh activation link has been dispatched to your inbox. Please check your email to complete onboarding.
                </div>
              ) : (
                <form onSubmit={handleResendLink} className="border-top pt-4">
                  <h6 className="fw-semibold text-dark mb-2">Request New Activation Link</h6>
                  <p className="text-muted small mb-3">
                    Enter the email address your administrator invited, and we will send a new activation email.
                  </p>
                  <div className="auth-input-group mb-3">
                    <span className="auth-input-icon"><FiMail /></span>
                    <input 
                      type="email" 
                      className="auth-input" 
                      placeholder="Enter your email address"
                      value={resendEmail}
                      onChange={(e) => setResendEmail(e.target.value)}
                      required 
                    />
                  </div>
                  <button type="submit" className="btn btn-primary w-100" disabled={resending}>
                    {resending ? 'Sending Link...' : 'Send Activation Link'}
                  </button>
                </form>
              )}

              <div className="text-center mt-4 border-top pt-3">
                <Link to="/login" className="text-decoration-none small fw-semibold text-primary">
                  Back to Login
                </Link>
              </div>
            </div>
          ) : (
            <div>
              <h2 className="auth-title">Activate Account</h2>
              <p className="auth-subtitle">
                Welcome to <strong>{userDetails?.workspaceName}</strong>. Activate your account as an <strong>{userDetails?.role}</strong> by setting your password.
              </p>

              <form onSubmit={handleActivateSubmit}>
                {/* Username Display */}
                <div className="mb-3 p-3 bg-light rounded border border-light-subtle">
                  <div className="small text-muted fw-semibold">Invited Name</div>
                  <div className="fw-bold text-dark mb-2">{userDetails?.name}</div>
                  <div className="small text-muted fw-semibold">Email Address</div>
                  <div className="fw-bold text-dark text-truncate">{userDetails?.email}</div>
                </div>

                {/* Password Field */}
                <div className="auth-input-group">
                  <span className="auth-input-icon"><FiLock /></span>
                  <input 
                    type={showPassword ? 'text' : 'password'} 
                    className="auth-input" 
                    placeholder="Create a strong password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required 
                  />
                  <button 
                    type="button" 
                    className="auth-password-toggle"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? <FiEyeOff /> : <FiEye />}
                  </button>
                </div>
                <div className="text-muted small mb-3" style={{ fontSize: '0.75rem', marginTop: '-8px' }}>
                  Must be at least 8 characters.
                </div>

                {/* Confirm Password Field */}
                <div className="auth-input-group">
                  <span className="auth-input-icon"><FiLock /></span>
                  <input 
                    type={showConfirmPassword ? 'text' : 'password'} 
                    className="auth-input" 
                    placeholder="Confirm your password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    required 
                  />
                  <button 
                    type="button" 
                    className="auth-password-toggle"
                    onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  >
                    {showConfirmPassword ? <FiEyeOff /> : <FiEye />}
                  </button>
                </div>

                <button type="submit" className="btn btn-primary w-100 mt-2" disabled={submitting}>
                  {submitting ? 'Activating Account...' : 'Activate Account'}
                </button>
              </form>

              <div className="text-center mt-4 border-top pt-3">
                <Link to="/login" className="text-decoration-none small fw-semibold text-primary">
                  Back to Login
                </Link>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Activate;
