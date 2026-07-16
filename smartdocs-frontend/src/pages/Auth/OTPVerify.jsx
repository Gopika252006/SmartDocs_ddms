import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import { FiShield } from 'react-icons/fi';
import { toast } from 'react-toastify';

const OTPVerify = () => {
  const { verifyEmail } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [timer, setTimer] = useState(120); // 2 minutes cooldown (120 seconds)
  const [resendAttempts, setResendAttempts] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [canResend, setCanResend] = useState(false);
  
  const timerRef = useRef(null);

  useEffect(() => {
    // Load email from localStorage
    const savedEmail = localStorage.getItem('verifyEmailAddress');
    if (!savedEmail) {
      toast.error('No verification email found. Please register.');
      navigate('/register');
      return;
    }
    setEmail(savedEmail);

    // Start cooldown timer
    startTimer();

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const startTimer = () => {
    setCanResend(false);
    setTimer(120);
    if (timerRef.current) clearInterval(timerRef.current);
    
    timerRef.current = setInterval(() => {
      setTimer((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current);
          setCanResend(true);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  const handleVerify = async (e) => {
    e.preventDefault();
    if (!code || code.length !== 6) {
      return toast.warn('Please enter the 6-digit OTP code');
    }

    setSubmitting(true);
    try {
      await verifyEmail(email, code);
      toast.success('Email verified successfully! You can now sign in.');
      localStorage.removeItem('verifyEmailAddress');
      navigate('/login');
    } catch (err) {
      toast.error(err || 'Verification failed. Please check the code.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (!canResend) return;
    if (resendAttempts >= 3) {
      return toast.error('OTP resend limit reached. Please wait 30 minutes.');
    }

    try {
      await api.post(`/auth/resend-otp?email=${encodeURIComponent(email)}&purpose=EMAIL_VERIFICATION`);
      toast.success('A new OTP has been sent to your email.');
      setResendAttempts((prev) => prev + 1);
      startTimer();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to resend OTP.');
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

      {/* Right OTP Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in">
          <h2 className="auth-title">Verify Your Email</h2>
          <p className="auth-subtitle">We've sent a 6-digit verification passcode to:</p>
          
          <div className="p-3 mb-4 rounded text-truncate text-center" style={{ backgroundColor: '#edf2f7', color: '#2d3748', fontWeight: '600', fontSize: '0.95rem' }}>
            {email}
          </div>

          {/* OTP Input Form */}
          <form onSubmit={handleVerify}>
            <div className="auth-input-group">
              <label className="auth-input-label text-center d-block mb-3">One-Time Passcode</label>
              <input
                type="text"
                maxLength="6"
                className="form-control text-center bg-light border-0 py-3 fw-bold"
                style={{ fontSize: '2rem', letterSpacing: '8px', borderRadius: '12px' }}
                placeholder="000000"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                required
              />
            </div>

            <button
              type="submit"
              className="auth-btn mb-3"
              disabled={submitting}
            >
              {submitting ? 'Verifying...' : 'Verify & Continue'}
            </button>
          </form>

          {/* Resend Cooldown Section */}
          <div className="text-center mt-3 pt-3 border-top">
            {resendAttempts >= 3 ? (
              <div className="alert alert-danger small py-2 m-0">
                Resend attempts exceeded. Requests blocked for 30 minutes.
              </div>
            ) : (
              <div>
                {canResend ? (
                  <button 
                    onClick={handleResend} 
                    className="btn btn-link text-decoration-none p-0 fw-semibold"
                    style={{ fontSize: '0.85rem' }}
                  >
                    Resend Passcode
                  </button>
                ) : (
                  <span className="text-muted small">
                    Resend passcode in <strong className="text-dark">{timer}s</strong>
                  </span>
                )}
                {resendAttempts > 0 && (
                  <div className="text-muted small mt-1" style={{ fontSize: '0.7rem' }}>
                    Resend attempt {resendAttempts} of 3
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default OTPVerify;

