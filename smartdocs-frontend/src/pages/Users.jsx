import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { FiUsers, FiUserCheck, FiUserX, FiShield, FiTrash2, FiSearch } from 'react-icons/fi';
import { toast } from 'react-toastify';
import OverlayPortal from '../components/OverlayPortal';

const Users = () => {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [showAddEmployeeModal, setShowAddEmployeeModal] = useState(false);
  const [empName, setEmpName] = useState('');
  const [empEmail, setEmpEmail] = useState('');
  const [empRole, setEmpRole] = useState('EMPLOYEE');
  const [creatingEmployee, setCreatingEmployee] = useState(false);

  useEffect(() => {
    fetchUsers();
  }, []);

  const fetchUsers = async (queryText = '') => {
    setLoading(true);
    try {
      const url = queryText.trim() ? `/users?query=${encodeURIComponent(queryText)}` : '/users';
      const res = await api.get(url);
      setUsers(res.data);
    } catch (err) {
      toast.error('Failed to load user manager panel');
    } finally {
      setLoading(false);
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    fetchUsers(searchQuery);
  };

  const handleRoleChange = async (userId, newRole) => {
    try {
      await api.put(`/users/${userId}/role?role=${newRole}`);
      toast.success('User role updated successfully');
      fetchUsers(searchQuery);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to update user role');
    }
  };

  const handleStatusToggle = async (userId, currentStatus) => {
    const actionText = currentStatus ? 'suspend' : 'activate';
    if (!window.confirm(`Are you sure you want to ${actionText} this user's account?`)) return;

    try {
      await api.put(`/users/${userId}/status?active=${!currentStatus}`);
      toast.info(`User account has been ${currentStatus ? 'suspended' : 'activated'}`);
      fetchUsers(searchQuery);
    } catch (err) {
      toast.error('Failed to update user account status');
    }
  };

  const handleDeleteUser = async (userId, email) => {
    if (!window.confirm(`CRITICAL WARNING: Are you sure you want to permanently delete user "${email}"?\n\nThis will purge their user record, folders, documents, version logs, share records, and DELETE all their physical files on disk.`)) return;

    try {
      await api.delete(`/users/${userId}`);
      toast.success(`User "${email}" and all associated files deleted successfully.`);
      fetchUsers(searchQuery);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete user');
    }
  };

  const isPersonalWorkspace = currentUser?.workspaceType === 'PERSONAL';

  const handleResendInvitation = async (userId, email) => {
    try {
      await api.post(`/users/${userId}/resend-invitation`);
      toast.success(`Invitation email has been resent to ${email}`);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to resend invitation');
    }
  };

  const handleResetPassword = async (userId, email) => {
    if (!window.confirm(`Are you sure you want to reset the password for ${email}? This will suspend their current password and send them an activation link to set a new password.`)) return;
    try {
      await api.post(`/users/${userId}/reset-password`);
      toast.success(`Password reset link sent to ${email}`);
      fetchUsers(searchQuery);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to reset password');
    }
  };

  const handleAddEmployeeSubmit = async (e) => {
    e.preventDefault();
    if (!empName || !empEmail || !empRole) {
      return toast.warn('All fields are required');
    }

    setCreatingEmployee(true);
    try {
      await api.post('/users/employee', {
        name: empName,
        email: empEmail,
        role: empRole
      });
      toast.success('Invitation email sent successfully!');
      setShowAddEmployeeModal(false);
      setEmpName('');
      setEmpEmail('');
      setEmpRole('EMPLOYEE');
      fetchUsers(searchQuery);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to invite user');
    } finally {
      setCreatingEmployee(false);
    }
  };

  return (
    <div className="container-fluid px-0">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="font-title m-0">User Management</h2>
          <p className="text-muted small m-0">Control directory user accounts, roles, and compliance statuses</p>
        </div>
        {!isPersonalWorkspace && currentUser?.role === 'ADMIN' && (
          <button className="btn btn-primary" onClick={() => setShowAddEmployeeModal(true)}>
            Add User
          </button>
        )}
      </div>

      {/* Search Row */}
      <div className="bg-white p-3 rounded mb-4 shadow-sm">
        <form onSubmit={handleSearchSubmit} className="row g-2">
          <div className="col-12 col-md-10">
            <div className="input-group">
              <span className="input-group-text bg-light border-end-0"><FiSearch /></span>
              <input 
                type="text" 
                className="form-control bg-light border-start-0 ps-0" 
                placeholder="Search users by name or email address..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
          <div className="col-12 col-md-2">
            <button type="submit" className="btn btn-primary w-100">Search</button>
          </div>
        </form>
      </div>

      {/* Users table */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : users.length === 0 ? (
        <div className="text-center py-5 bg-white rounded shadow-sm">
          <FiUsers size={48} className="text-muted mb-3" />
          <h5>No users matched your query</h5>
        </div>
      ) : (
        <div className="table-responsive shadow-sm rounded">
          <table className="table custom-table m-0">
            <thead>
              <tr>
                <th>User Details</th>
                <th>Access Role</th>
                <th>Verification</th>
                <th>Status</th>
                <th className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map(u => {
                const isSelf = u.id === currentUser?.id;
                return (
                  <tr key={u.id}>
                    <td>
                      <div className="d-flex align-items-center gap-2">
                        <div className="p-2 bg-light rounded text-secondary" style={{ fontWeight: 'bold' }}>
                          {u.name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                          <span className="fw-semibold text-dark">{u.name} {isSelf && <small className="text-muted">(You)</small>}</span>
                          <span className="d-block text-muted" style={{ fontSize: '0.75rem' }}>{u.email}</span>
                        </div>
                      </div>
                    </td>
                    <td>
                      <span 
                        className="badge d-inline-block text-uppercase fw-bold" 
                        style={
                          u.role === 'SUPER_ADMIN' 
                            ? { backgroundColor: 'rgba(111, 66, 193, 0.12)', color: '#6f42c1', border: '1px solid rgba(111, 66, 193, 0.25)', fontSize: '0.7rem', padding: '4px 8px' }
                            : u.role === 'ADMIN'
                            ? { backgroundColor: 'rgba(26, 115, 232, 0.12)', color: '#1a73e8', border: '1px solid rgba(26, 115, 232, 0.25)', fontSize: '0.7rem', padding: '4px 8px' }
                            : { backgroundColor: 'rgba(108, 117, 125, 0.12)', color: '#6c757d', border: '1px solid rgba(108, 117, 125, 0.25)', fontSize: '0.7rem', padding: '4px 8px' }
                        }
                      >
                        {u.role === 'SUPER_ADMIN' ? 'Super Admin' : u.role === 'ADMIN' ? 'Admin' : 'User'}
                      </span>
                      {(!isSelf && !(currentUser.role === 'ADMIN' && u.role === 'SUPER_ADMIN')) && (
                        <select 
                          className="form-select form-select-sm mt-1.5" 
                          value={u.role}
                          onChange={(e) => handleRoleChange(u.id, e.target.value)}
                          style={{ width: '130px', fontSize: '0.75rem', padding: '2px 4px' }}
                        >
                          <option value="EMPLOYEE">Make User</option>
                          <option value="ADMIN">Make Admin</option>
                          <option value="SUPER_ADMIN">Make Super Admin</option>
                        </select>
                      )}
                    </td>
                    <td>
                      {u.emailVerified ? (
                        <span className="badge bg-success" style={{ fontSize: '0.7rem' }}>Verified</span>
                      ) : (
                        <span className="badge bg-warning text-dark" style={{ fontSize: '0.7rem' }}>Pending</span>
                      )}
                    </td>
                    <td>
                      {u.active ? (
                        <span className="badge bg-success" style={{ fontSize: '0.7rem' }}>Active</span>
                      ) : u.emailVerified ? (
                        <span className="badge bg-danger" style={{ fontSize: '0.7rem' }}>Disabled</span>
                      ) : (
                        <span className="badge bg-secondary" style={{ fontSize: '0.7rem' }}>Pending</span>
                      )}
                    </td>
                    <td className="text-end">
                      <div className="d-flex align-items-center justify-content-end gap-2">
                        {!u.emailVerified && (
                          <button 
                            className="btn btn-sm btn-outline-primary" 
                            title="Resend Invitation"
                            onClick={() => handleResendInvitation(u.id, u.email)}
                            style={{ fontSize: '0.75rem', padding: '2px 6px' }}
                          >
                            Resend
                          </button>
                        )}
                        {!isSelf && (
                          <button 
                            className={`btn btn-sm ${u.active ? 'btn-outline-warning' : 'btn-outline-success'}`}
                            title={u.active ? "Disable User" : "Enable User"}
                            onClick={() => handleStatusToggle(u.id, u.active)}
                            style={{ fontSize: '0.75rem', padding: '2px 6px' }}
                          >
                            {u.active ? 'Disable' : 'Enable'}
                          </button>
                        )}
                        {u.emailVerified && (
                          <button 
                            className="btn btn-sm btn-outline-info" 
                            title="Reset Password"
                            onClick={() => handleResetPassword(u.id, u.email)}
                            style={{ fontSize: '0.75rem', padding: '2px 6px' }}
                          >
                            Reset
                          </button>
                        )}
                        <button 
                          className="btn btn-sm btn-light border text-danger" 
                          title="Delete User"
                          onClick={() => handleDeleteUser(u.id, u.email)}
                          disabled={isSelf || (currentUser.role === 'ADMIN' && u.role === 'SUPER_ADMIN')}
                          style={{ fontSize: '0.75rem', padding: '3px 6px' }}
                        >
                          <FiTrash2 size={12} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {/* Add User Modal */}
      <OverlayPortal
        isOpen={showAddEmployeeModal}
        onClose={() => setShowAddEmployeeModal(false)}
      >
        <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <h5 className="modal-title font-title">Add New User</h5>
                <button type="button" className="btn-close" onClick={() => setShowAddEmployeeModal(false)}></button>
              </div>
              <form onSubmit={handleAddEmployeeSubmit}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Full Name</label>
                    <input 
                      type="text" 
                      className="form-control" 
                      placeholder="John Doe" 
                      value={empName}
                      onChange={(e) => setEmpName(e.target.value)}
                      required 
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Email Address</label>
                    <input 
                      type="email" 
                      className="form-control" 
                      placeholder="john.doe@company.com" 
                      value={empEmail}
                      onChange={(e) => setEmpEmail(e.target.value)}
                      required 
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Role</label>
                    <select 
                      className="form-select"
                      value={empRole}
                      onChange={(e) => setEmpRole(e.target.value)}
                      required
                    >
                      <option value="EMPLOYEE">User</option>
                      <option value="ADMIN">Workspace Admin</option>
                    </select>
                  </div>
                </div>
                <div className="modal-footer border-top">
                  <button type="button" className="btn btn-light" onClick={() => setShowAddEmployeeModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={creatingEmployee}>
                    {creatingEmployee ? 'Inviting...' : 'Invite User'}
                  </button>
                </div>
              </form>
            </div>
      </OverlayPortal>
    </div>
  );
};

export default Users;
