import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import DashboardLayout from './layouts/DashboardLayout';
import Login from './pages/Auth/Login';
import Register from './pages/Auth/Register';
import OTPVerify from './pages/Auth/OTPVerify';
import ForgotPassword from './pages/Auth/ForgotPassword';
import Activate from './pages/Auth/Activate';
import ForceChangePassword from './pages/Auth/ForceChangePassword';
import Dashboard from './pages/Dashboard';
import Files from './pages/Files';
import Folders from './pages/Folders';
import Shared from './pages/Shared';
import Security from './pages/Security';
import Trash from './pages/Trash';
import Users from './pages/Users';
import AuditLogs from './pages/AuditLogs';
import Reports from './pages/Reports';
import PlatformPortal from './pages/PlatformPortal';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'react-toastify/dist/ReactToastify.css';
import { ToastContainer } from 'react-toastify';

// Route Guard: Checks if authenticated
const ProtectedRoute = ({ children }) => {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.firstLogin) {
    return <Navigate to="/force-change-password" replace />;
  }
  return children;
};

// Route Guard: Checks if admin
const AdminRoute = ({ children }) => {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.firstLogin) {
    return <Navigate to="/force-change-password" replace />;
  }
  const isAdmin = user.role === 'ADMIN' || user.role === 'SUPER_ADMIN';
  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
};

// Route Guard: Checks if super admin
const SuperAdminRoute = ({ children }) => {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.firstLogin) {
    return <Navigate to="/force-change-password" replace />;
  }
  const isSuper = user.role === 'SUPER_ADMIN';
  if (!isSuper) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
};

// Route Guard: Checks if first login is required
const FirstLoginRoute = ({ children }) => {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (!user.firstLogin) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
};

const AppContent = () => {
  const { user } = useAuth();

  return (
    <>
      <Routes>
        {/* Auth routes */}
        <Route path="/login" element={user ? (user.firstLogin ? <Navigate to="/force-change-password" replace /> : <Navigate to="/dashboard" replace />) : <Login />} />
        <Route path="/register" element={user ? <Navigate to="/dashboard" replace /> : <Register />} />
        <Route path="/verify-email" element={<OTPVerify />} />
        <Route path="/forgot-password" element={user ? <Navigate to="/dashboard" replace /> : <ForgotPassword />} />
        <Route path="/activate-account" element={<Activate />} />
        <Route path="/force-change-password" element={
          <FirstLoginRoute>
            <ForceChangePassword />
          </FirstLoginRoute>
        } />

        {/* Protected Dashboard Workspace routes */}
        <Route path="/dashboard" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Dashboard />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/documents" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Files />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/folders" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Folders />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/shared" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Shared />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/security" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Security />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/trash" element={
          <ProtectedRoute>
            <DashboardLayout>
              <Trash />
            </DashboardLayout>
          </ProtectedRoute>
        } />

        {/* Admin only routes */}
        <Route path="/users" element={
          <AdminRoute>
            <DashboardLayout>
              <Users />
            </DashboardLayout>
          </AdminRoute>
        } />
        <Route path="/audit-logs" element={
          <ProtectedRoute>
            <DashboardLayout>
              <AuditLogs />
            </DashboardLayout>
          </ProtectedRoute>
        } />
        <Route path="/reports" element={
          <AdminRoute>
            <DashboardLayout>
              <Reports />
            </DashboardLayout>
          </AdminRoute>
        } />

        {/* Super Admin platform route */}
        <Route path="/platform" element={
          <SuperAdminRoute>
            <DashboardLayout>
              <PlatformPortal />
            </DashboardLayout>
          </SuperAdminRoute>
        } />

        {/* Fallback route */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
      <ToastContainer position="top-right" autoClose={3000} hideProgressBar={false} />
    </>
  );
};

function App() {
  return (
    <Router>
      <AuthProvider>
        <AppContent />
      </AuthProvider>
    </Router>
  );
}

export default App;
