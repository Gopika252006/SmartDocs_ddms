import React, { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import api from '../../services/api';
import { FiMail, FiLock, FiShield, FiKey, FiEye, FiEyeOff } from 'react-icons/fi';
import { toast } from 'react-toastify';

const ForgotPassword = () => {
  const { forgotPassword, resetPassword } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(1); // Step 1: Request OTP, Step 2: Reset Password
  const [email, setEmail] = useState('');
  const [otpCode, setOtpCode] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [timer, setTimer] = useState(120);
  const [resendAttempts, setResendAttempts] = useState(0);
  const [canResend, setCanResend] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  
  const timerRef = useRef(null);

  useEffect(() => {
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

  const handleRequestOtp = async (e) => {
    e.preventDefault();
    if (!email) return toast.warn('Please enter your email');

    setSubmitting(true);
    try {
      await forgotPassword(email);
      toast.success('Password reset OTP sent to your email.');
      setStep(2);
      startTimer();
    } catch (err) {
      toast.error(err || 'Failed to request reset OTP. Check your email address.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleResetPassword = async (e) => {
    e.preventDefault();
    if (!otpCode || !password || !confirmPassword) {
      return toast.warn('All fields are required');
    }

    if (password !== confirmPassword) {
      return toast.error('Passwords do not match');
    }

    const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/;
    if (!passwordRegex.test(password)) {
      return toast.error('Password must be at least 8 characters long and contain at least one uppercase letter, one lowercase letter, one digit, and one special character (@#$%^&+=!)');
    }

    setSubmitting(true);
    try {
      await resetPassword(email, otpCode, password, confirmPassword);
      toast.success('Password reset successfully! You can now login.');
      navigate('/login');
    } catch (err) {
      toast.error(err || 'Failed to reset password. Verify the OTP code.');
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
      await api.post(`/auth/resend-otp?email=${encodeURIComponent(email)}&purpose=PASSWORD_RESET`);
      toast.success('A new OTP has been sent successfully.');
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

      {/* Right Recovery Panel */}
      <div className="auth-right-panel">
        <div className="auth-card animate-fade-in">
          
          {/* Step 1: Submit Email */}
          {step === 1 ? (
            <div>
              <h2 className="auth-title">Recover Password</h2>
              <p className="auth-subtitle">Enter your email and we'll send you an OTP to reset your password</p>

              <form onSubmit={handleRequestOtp}>
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

                <button
                  type="submit"
                  className="auth-btn mb-3"
                  disabled={submitting}
                >
                  {submitting ? 'Sending OTP...' : 'Send Reset OTP'}
                </button>
              </form>

              <div className="text-center mt-3">
                <Link to="/login" className="text-decoration-none small fw-semibold">Back to Login</Link>
              </div>
            </div>
          ) : (
            /* Step 2: Reset Password Form */
            <div>
              <h2 className="auth-title">Reset Password</h2>
              <p className="auth-subtitle">Enter the 6-digit OTP code sent to your email and set your new password</p>

              <form onSubmit={handleResetPassword}>
                {/* OTP Code Field */}
                <div className="auth-input-group">
                  <label className="auth-input-label text-center d-block">One-Time Code</label>
                  <input
                    type="text"
                    maxLength="6"
                    className="form-control text-center bg-light border-0 py-2 fw-bold"
                    style={{ fontSize: '1.5rem', letterSpacing: '4px', borderRadius: '10px' }}
                    placeholder="000000"
                    value={otpCode}
                    onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                    required
                  />
                </div>

                {/* New Password Field */}
                <div className="auth-input-group">
                  <label className="auth-input-label">New Password</label>
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
                      placeholder="••••••••"
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

                <button
                  type="submit"
                  className="auth-btn mb-3"
                  disabled={submitting}
                >
                  {submitting ? 'Resetting...' : 'Reset Password'}
                </button>
              </form>

              {/* Resend Cooldown Section */}
              <div className="text-center mt-2 border-top pt-3">
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
                        Resend Code
                      </button>
                    ) : (
                      <span className="text-muted small">
                        Resend code in <strong className="text-dark">{timer}s</strong>
                      </span>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ForgotPassword;

