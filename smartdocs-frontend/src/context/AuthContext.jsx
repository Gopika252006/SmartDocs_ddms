import React, { createContext, useState, useEffect, useContext } from 'react';
import api from '../services/api';

const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    try {
      const storedToken = localStorage.getItem('token') || sessionStorage.getItem('token');
      const storedUser = localStorage.getItem('user') || sessionStorage.getItem('user');

      if (storedToken && storedUser && storedUser !== 'undefined') {
        setToken(storedToken);
        setUser(JSON.parse(storedUser));
      }
    } catch (error) {
      console.error('Session parsing failed, clearing credentials:', error);
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      sessionStorage.removeItem('token');
      sessionStorage.removeItem('user');
    } finally {
      setLoading(false);
    }
  }, []);

  const login = async (email, password, rememberMe) => {
    try {
      const response = await api.post('/auth/login', { email, password, rememberMe });
      const { token, ...userData } = response.data;

      if (rememberMe) {
        localStorage.setItem('token', token);
        localStorage.setItem('user', JSON.stringify(userData));
      } else {
        sessionStorage.setItem('token', token);
        sessionStorage.setItem('user', JSON.stringify(userData));
      }

      setToken(token);
      setUser(userData);
      return response.data;
    } catch (error) {
      throw error.response?.data?.message || 'Login failed';
    }
  };

  const logout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  const register = async (name, email, password, confirmPassword, workspaceName, workspaceType) => {
    try {
      const response = await api.post('/auth/register', { name, email, password, confirmPassword, workspaceName, workspaceType });
      return response.data;
    } catch (error) {
      throw error.response?.data?.message || 'Registration failed';
    }
  };

  const verifyEmail = async (email, otpCode) => {
    try {
      const response = await api.post('/auth/verify-email', { email, otpCode, purpose: 'EMAIL_VERIFICATION' });
      return response.data;
    } catch (error) {
      throw error.response?.data?.message || 'Verification failed';
    }
  };

  const forgotPassword = async (email) => {
    try {
      const response = await api.post('/auth/forgot-password', { email });
      return response.data;
    } catch (error) {
      throw error.response?.data?.message || 'Request failed';
    }
  };

  const resetPassword = async (email, otpCode, password, confirmPassword) => {
    try {
      const response = await api.post('/auth/reset-password', { email, otpCode, password, confirmPassword });
      return response.data;
    } catch (error) {
      throw error.response?.data?.message || 'Reset failed';
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        loading,
        login,
        logout,
        register,
        verifyEmail,
        forgotPassword,
        resetPassword,
        setUser
      }}
    >
      {!loading && children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
