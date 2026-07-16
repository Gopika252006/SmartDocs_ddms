import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { 
  FiShield, FiCheckCircle, FiAlertCircle, FiClock, 
  FiLock, FiUser, FiGlobe, FiDatabase 
} from 'react-icons/fi';
import { toast } from 'react-toastify';

const Security = () => {
  const { user, setUser } = useAuth();
  const [profileName, setProfileName] = useState(user?.name || '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  // Security status & history state
  const [secStatus, setSecStatus] = useState({
    emailVerified: false,
    lastLogin: null,
    passwordChangedDate: null,
    failedLoginAttempts: 0,
    accountStatus: 'ACTIVE',
    jwtSessionExpiry: null,
    securityScore: 100
  });
  const [loginHistory, setLoginHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [savingProfile, setSavingProfile] = useState(false);
  const [updatingPassword, setUpdatingPassword] = useState(false);

  useEffect(() => {
    fetchSecurityData();
  }, []);

  const fetchSecurityData = async () => {
    setLoading(true);
    try {
      const resStatus = await api.get('/security/status');
      setSecStatus(resStatus.data);

      const resHistory = await api.get('/security/login-history');
      setLoginHistory(resHistory.data);
    } catch (err) {
      toast.error('Failed to load security logs');
    } finally {
      setLoading(false);
    }
  };

  const handleProfileSubmit = async (e) => {
    e.preventDefault();
    if (!profileName.trim()) return toast.warn('Name cannot be empty');

    setSavingProfile(true);
    try {
      const res = await api.put('/users/profile', { name: profileName.trim() });
      toast.success('Profile updated successfully!');
      // Update global user context state
      const updatedUser = { ...user, name: res.data.name };
      setUser(updatedUser);
      localStorage.setItem('user', JSON.stringify(updatedUser));
    } catch (err) {
      toast.error('Profile update failed');
    } finally {
      setSavingProfile(false);
    }
  };

  const handlePasswordSubmit = async (e) => {
    e.preventDefault();
    if (!currentPassword || !newPassword || !confirmPassword) {
      return toast.warn('Please fill in all password fields');
    }

    if (newPassword !== confirmPassword) {
      return toast.error('New passwords do not match');
    }

    setUpdatingPassword(true);
    try {
      await api.put('/users/change-password', {
        currentPassword,
        newPassword,
        confirmPassword
      });
      toast.success('Password changed successfully!');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      fetchSecurityData(); // Refresh security score
    } catch (err) {
      toast.error(err.response?.data?.message || 'Password update failed');
    } finally {
      setUpdatingPassword(false);
    }
  };

  // Color mappings for security score rating
  const getScoreColorClass = (score) => {
    if (score >= 90) return 'text-success border-success';
    if (score >= 70) return 'text-warning border-warning';
    return 'text-danger border-danger';
  };

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ height: '70vh' }}>
        <div className="spinner-border text-primary" role="status"></div>
      </div>
    );
  }

  return (
    <div className="container-fluid px-0">
      <div className="mb-4">
        <h2 className="font-title m-0">Security Center</h2>
        <p className="text-muted small m-0">Audit login activities, update password credentials, and analyze workspace security rating</p>
      </div>

      <div className="row g-4 mb-4">
        {/* Security Score Panel */}
        <div className="col-12 col-lg-4">
          <div className="smart-card d-flex flex-column align-items-center justify-content-center text-center p-4" style={{ minHeight: '320px' }}>
            <h6 className="text-muted text-uppercase mb-3" style={{ fontSize: '0.75rem', fontWeight: 600, letterSpacing: '0.5px' }}>Overall Security Rating</h6>
            
            {/* Circular score display */}
            <div 
              className={`rounded-circle border border-5 d-flex flex-column align-items-center justify-content-center mb-3 ${getScoreColorClass(secStatus.securityScore)}`}
              style={{ width: '130px', height: '130px' }}
            >
              <h2 className="m-0 fw-bold">{secStatus.securityScore}</h2>
              <span className="small text-muted" style={{ fontSize: '0.7rem' }}>of 100</span>
            </div>

            <div>
              <h5 className="font-title mb-1">
                {secStatus.securityScore >= 90 ? 'Excellent protection' : 
                 secStatus.securityScore >= 70 ? 'Satisfactory protection' : 'Requires security audit'}
              </h5>
              <p className="text-muted small m-0">
                {secStatus.emailVerified ? 'MFA/Email is verified.' : 'Verify email to secure your account.'}
              </p>
            </div>
          </div>
        </div>

        {/* Security Checklist summary */}
        <div className="col-12 col-lg-8">
          <div className="smart-card p-4" style={{ minHeight: '320px' }}>
            <h5 className="mb-4 font-title fw-semibold">Workspace Security Audit</h5>
            <div className="d-flex flex-column gap-3">
              {/* Email verify status */}
              <div className="d-flex align-items-center justify-content-between p-2 bg-light rounded">
                <div className="d-flex align-items-center gap-3">
                  {secStatus.emailVerified ? (
                    <FiCheckCircle size={20} className="text-success" />
                  ) : (
                    <FiAlertCircle size={20} className="text-warning" />
                  )}
                  <div>
                    <h6 className="m-0 fw-semibold" style={{ fontSize: '0.9rem' }}>Email Verification status</h6>
                    <small className="text-muted">Requires OTP code verification on signup</small>
                  </div>
                </div>
                <span className={`badge ${secStatus.emailVerified ? 'bg-success' : 'bg-warning'}`}>
                  {secStatus.emailVerified ? 'Verified' : 'Pending Verification'}
                </span>
              </div>

              {/* Login failures check */}
              <div className="d-flex align-items-center justify-content-between p-2 bg-light rounded">
                <div className="d-flex align-items-center gap-3">
                  {secStatus.failedLoginAttempts === 0 ? (
                    <FiCheckCircle size={20} className="text-success" />
                  ) : (
                    <FiAlertCircle size={20} className="text-danger" />
                  )}
                  <div>
                    <h6 className="m-0 fw-semibold" style={{ fontSize: '0.9rem' }}>Failed login attempts (24h)</h6>
                    <small className="text-muted">Unsuccessful credential attempts trace</small>
                  </div>
                </div>
                <span className={`badge ${secStatus.failedLoginAttempts === 0 ? 'bg-success' : 'bg-danger'}`}>
                  {secStatus.failedLoginAttempts} attempts
                </span>
              </div>

              {/* Session timer details */}
              <div className="d-flex align-items-center justify-content-between p-2 bg-light rounded">
                <div className="d-flex align-items-center gap-3">
                  <FiClock size={20} className="text-primary" />
                  <div>
                    <h6 className="m-0 fw-semibold" style={{ fontSize: '0.9rem' }}>Active JWT Session timeout</h6>
                    <small className="text-muted">Automatic token-expiration threshold</small>
                  </div>
                </div>
                <span className="badge bg-primary">24 Hours (Stateless)</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="row g-4 mb-4">
        {/* Profile Details Update Form */}
        <div className="col-12 col-md-6">
          <div className="smart-card p-4">
            <h5 className="mb-4 font-title fw-semibold d-flex align-items-center gap-2">
              <FiUser className="text-primary" />
              <span>Workspace Profile details</span>
            </h5>
            <form onSubmit={handleProfileSubmit}>
              <div className="mb-3">
                <label className="form-label text-muted small fw-semibold">Your Display Name</label>
                <input 
                  type="text" 
                  className="form-control bg-light" 
                  value={profileName}
                  onChange={(e) => setProfileName(e.target.value)}
                  required 
                />
              </div>
              <div className="mb-3">
                <label className="form-label text-muted small fw-semibold">Account Email Address (Immutable)</label>
                <input type="text" className="form-control bg-light text-muted" value={user?.email || ''} disabled />
              </div>
              <button type="submit" className="btn btn-primary rounded-pill px-4" disabled={savingProfile}>
                {savingProfile ? 'Saving...' : 'Update Details'}
              </button>
            </form>
          </div>
        </div>

        {/* Change Password Form */}
        <div className="col-12 col-md-6">
          <div className="smart-card p-4">
            <h5 className="mb-4 font-title fw-semibold d-flex align-items-center gap-2">
              <FiLock className="text-primary" />
              <span>Update Credentials</span>
            </h5>
            <form onSubmit={handlePasswordSubmit}>
              <div className="mb-3">
                <label className="form-label text-muted small fw-semibold">Current Password</label>
                <input 
                  type="password" 
                  className="form-control bg-light" 
                  placeholder="••••••••"
                  value={currentPassword}
                  onChange={(e) => setCurrentPassword(e.target.value)}
                  required 
                />
              </div>
              <div className="mb-3">
                <label className="form-label text-muted small fw-semibold">New Passcode</label>
                <input 
                  type="password" 
                  className="form-control bg-light" 
                  placeholder="••••••••"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required 
                />
              </div>
              <div className="mb-3">
                <label className="form-label text-muted small fw-semibold">Confirm New Passcode</label>
                <input 
                  type="password" 
                  className="form-control bg-light" 
                  placeholder="••••••••"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required 
                />
              </div>
              <button type="submit" className="btn btn-primary rounded-pill px-4" disabled={updatingPassword}>
                {updatingPassword ? 'Updating...' : 'Change Password'}
              </button>
            </form>
          </div>
        </div>
      </div>

      {/* Login History Logs */}
      <div className="smart-card p-4">
        <h5 className="mb-4 font-title fw-semibold d-flex align-items-center gap-2">
          <FiGlobe className="text-primary" />
          <span>Login History Feed</span>
        </h5>
        <div className="table-responsive">
          <table className="table custom-table m-0" style={{ fontSize: '0.8rem' }}>
            <thead>
              <tr>
                <th>Browser Client</th>
                <th>OS</th>
                <th>Device</th>
                <th>IP Address</th>
                <th>Geo Location</th>
                <th>Access Date</th>
              </tr>
            </thead>
            <tbody>
              {loginHistory.map(h => (
                <tr key={h.id}>
                  <td>{h.browser}</td>
                  <td>{h.os}</td>
                  <td>{h.device}</td>
                  <td><code>{h.ipAddress}</code></td>
                  <td>{h.location}</td>
                  <td className="text-muted">
                    {new Date(h.createdAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default Security;
