import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { FiLock, FiEye, FiEyeOff, FiLogOut } from 'react-icons/fi';
import { toast } from 'react-toastify';
import api from '../../services/api';

const ForceChangePassword = () => {
  const { user, setUser, logout } = useAuth();
  const navigate = useNavigate();
  
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!currentPassword || !newPassword || !confirmPassword) {
      return toast.warn('Please fill in all fields');
    }
    if (newPassword.length < 8) {
      return toast.warn('New password must be at least 8 characters long');
    }
    if (newPassword !== confirmPassword) {
      return toast.error('New passwords do not match');
    }

    setSubmitting(true);
    try {
      await api.put('/users/change-password', {
        currentPassword,
        newPassword,
        confirmPassword
      });

      // Update the user session state to mark firstLogin as false
      setUser(prev => {
        const updated = { ...prev, firstLogin: false };
        if (localStorage.getItem('token')) {
          localStorage.setItem('user', JSON.stringify(updated));
        } else {
          sessionStorage.setItem('user', JSON.stringify(updated));
        }
        return updated;
      });

      toast.success('Your password has been set successfully! Welcome aboard.');
      navigate('/dashboard');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update password. Please check your credentials.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

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
            <li className="auth-feature-item">One-Time Password Updates</li>
            <li className="auth-feature-item">Strong BCrypt Hashing</li>
            <li className="auth-feature-item">Encrypted Session Security</li>
            <li className="auth-feature-item">Compliance Standards Enforced</li>
          </ul>
        </div>
      </div>

      {/* Right Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in" style={{ maxWidth: '440px' }}>
          <h2 className="auth-title">Security Verification</h2>
          <p className="auth-subtitle">Please create a new password for your account</p>

          <form onSubmit={handleSubmit}>
            {/* Temporary Password */}
            <div className="auth-input-group">
              <label className="auth-input-label">Temporary Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiLock />
                </span>
                <input
                  type={showCurrent ? 'text' : 'password'}
                  className="auth-input-field"
                  placeholder="Enter temporary password"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowCurrent(!showCurrent)}
                >
                  {showCurrent ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
            </div>

            {/* New Password */}
            <div className="auth-input-group mt-3">
              <label className="auth-input-label">New Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiLock />
                </span>
                <input
                  type={showNew ? 'text' : 'password'}
                  className="auth-input-field"
                  placeholder="Min 8 characters, letters & symbols"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowNew(!showNew)}
                >
                  {showNew ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
            </div>

            {/* Confirm New Password */}
            <div className="auth-input-group mt-3">
              <label className="auth-input-label">Confirm New Password</label>
              <div className="auth-input-wrapper">
                <span className="auth-input-icon">
                  <FiLock />
                </span>
                <input
                  type={showConfirm ? 'text' : 'password'}
                  className="auth-input-field"
                  placeholder="Repeat new password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="auth-password-toggle"
                  onClick={() => setShowConfirm(!showConfirm)}
                >
                  {showConfirm ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
            </div>

            <button type="submit" className="btn btn-primary w-100 mt-4" disabled={submitting}>
              {submitting ? 'Updating Password...' : 'Save & Continue'}
            </button>
          </form>

          <div className="text-center mt-4 pt-2 border-top">
            <button 
              onClick={handleLogout}
              className="btn btn-link text-decoration-none text-muted small d-inline-flex align-items-center gap-1"
            >
              <FiLogOut size={14} /> Log Out of Account
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ForceChangePassword;
