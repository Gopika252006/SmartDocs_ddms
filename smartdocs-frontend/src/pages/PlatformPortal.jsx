import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { 
  FiSliders, FiGrid, FiUsers, FiFileText, FiActivity, 
  FiAlertTriangle, FiCheckCircle, FiLock, FiUnlock, FiKey, FiCpu, FiDatabase, FiLayers, FiHardDrive
} from 'react-icons/fi';
import { toast } from 'react-toastify';

const PlatformPortal = () => {
  const [activeTab, setActiveTab] = useState('analytics');
  const [workspaces, setWorkspaces] = useState([]);
  const [users, setUsers] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);

  // Search/Filters states
  const [searchWorkspace, setSearchWorkspace] = useState('');
  const [searchUser, setSearchUser] = useState('');
  const [searchDoc, setSearchDoc] = useState('');
  const [filterWorkspaceLog, setFilterWorkspaceLog] = useState('');

  useEffect(() => {
    fetchData();
  }, [activeTab]);

  const fetchData = async () => {
    setLoading(true);
    try {
      if (activeTab === 'analytics') {
        const res = await api.get('/super/analytics');
        setAnalytics(res.data);
      } else if (activeTab === 'workspaces') {
        const res = await api.get('/super/workspaces');
        setWorkspaces(res.data);
      } else if (activeTab === 'users') {
        const res = await api.get('/super/users');
        const wsRes = await api.get('/super/workspaces');
        setWorkspaces(wsRes.data);
        setUsers(res.data);
      } else if (activeTab === 'documents') {
        const res = await api.get('/super/documents');
        setDocuments(res.data);
      }
    } catch (err) {
      toast.error('Failed to load platform data');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (workspaceId, currentStatus) => {
    const nextStatus = !currentStatus;
    const actionLabel = nextStatus ? 'activate' : 'suspend';
    if (!window.confirm(`Are you sure you want to ${actionLabel} this workspace? Users belonging to it won't be able to log in if suspended.`)) {
      return;
    }
    try {
      await api.post(`/super/workspaces/${workspaceId}/status?active=${nextStatus}`);
      toast.success(`Workspace has been ${nextStatus ? 'activated' : 'suspended'}!`);
      // Reload list
      const res = await api.get('/super/workspaces');
      setWorkspaces(res.data);
    } catch (err) {
      toast.error('Failed to update workspace status');
    }
  };

  const handleResetAdmin = async (workspaceId) => {
    if (!window.confirm("Are you sure you want to reset the admin's password for this workspace to 'SecurePass123!'?")) {
      return;
    }
    try {
      await api.post(`/super/workspaces/${workspaceId}/reset-admin`);
      toast.success("Workspace Admin password reset to 'SecurePass123!' successfully!");
    } catch (err) {
      toast.error('Failed to reset workspace admin password');
    }
  };

  const handleEditQuota = async (workspaceId, workspaceName, currentQuota) => {
    const currentQuotaGB = (currentQuota / (1024 * 1024 * 1024)).toFixed(2);
    const newQuotaStr = window.prompt(`Enter new storage quota for ${workspaceName} (in Gigabytes, e.g. 5 for 5 GB, 0.1 for 100 MB):`, currentQuotaGB);
    if (newQuotaStr === null) return;
    const newQuotaGB = parseFloat(newQuotaStr);
    if (isNaN(newQuotaGB) || newQuotaGB <= 0) {
      toast.error('Invalid storage quota value');
      return;
    }
    const quotaBytes = Math.round(newQuotaGB * 1024 * 1024 * 1024);
    try {
      await api.put(`/super/workspaces/${workspaceId}/quota?quotaBytes=${quotaBytes}`);
      toast.success(`Storage quota for ${workspaceName} updated to ${newQuotaGB} GB successfully!`);
      // Reload list
      const res = await api.get('/super/workspaces');
      setWorkspaces(res.data);
    } catch (err) {
      toast.error('Failed to update storage quota');
    }
  };

  const formatBytes = (bytes, decimals = 2) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
  };

  return (
    <div className="container-fluid px-0">
      <div className="mb-4">
        <h2 className="font-title m-0">Platform Owner Portal</h2>
        <p className="text-muted small m-0">Global administration, telemetry audits, and workspace settings control</p>
      </div>

      {/* Tabs */}
      <div className="card shadow-sm border-0 mb-4 bg-white p-2" style={{ borderRadius: '12px' }}>
        <ul className="nav nav-pills gap-1">
          <li className="nav-item">
            <button 
              className={`nav-link rounded-pill px-4 fw-semibold border-0 ${activeTab === 'analytics' ? 'active btn-primary' : 'text-dark'}`}
              onClick={() => setActiveTab('analytics')}
            >
              <FiActivity className="me-2" /> Analytics & Telemetry
            </button>
          </li>
          <li className="nav-item">
            <button 
              className={`nav-link rounded-pill px-4 fw-semibold border-0 ${activeTab === 'workspaces' ? 'active btn-primary' : 'text-dark'}`}
              onClick={() => setActiveTab('workspaces')}
            >
              <FiLayers className="me-2" /> Manage Workspaces
            </button>
          </li>
          <li className="nav-item">
            <button 
              className={`nav-link rounded-pill px-4 fw-semibold border-0 ${activeTab === 'users' ? 'active btn-primary' : 'text-dark'}`}
              onClick={() => setActiveTab('users')}
            >
              <FiUsers className="me-2" /> Platform Users
            </button>
          </li>
        </ul>
      </div>

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
          <p className="text-muted mt-2">Loading platform databases...</p>
        </div>
      ) : (
        <>
          {/* TAB 1: ANALYTICS & TELEMETRY */}
          {activeTab === 'analytics' && analytics && (
            <div className="animate-fade-in">
              <div className="row g-3 mb-4">
                <div className="col-12 col-md-6 col-lg-3">
                  <div className="card shadow-sm border-0 p-3 h-100 bg-white" style={{ borderRadius: '12px' }}>
                    <div className="d-flex align-items-center gap-3">
                      <div className="rounded-circle p-3 bg-primary bg-opacity-10 text-primary">
                        <FiLayers size={24} />
                      </div>
                      <div>
                        <h6 className="text-muted small mb-1">Total Workspaces</h6>
                        <h3 className="fw-bold m-0 text-dark">{analytics.totalWorkspaces}</h3>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="col-12 col-md-6 col-lg-3">
                  <div className="card shadow-sm border-0 p-3 h-100 bg-white" style={{ borderRadius: '12px' }}>
                    <div className="d-flex align-items-center gap-3">
                      <div className="rounded-circle p-3 bg-success bg-opacity-10 text-success">
                        <FiUsers size={24} />
                      </div>
                      <div>
                        <h6 className="text-muted small mb-1">Registered Users</h6>
                        <h3 className="fw-bold m-0 text-dark">{analytics.totalUsers}</h3>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="col-12 col-md-6 col-lg-3">
                  <div className="card shadow-sm border-0 p-3 h-100 bg-white" style={{ borderRadius: '12px' }}>
                    <div className="d-flex align-items-center gap-3">
                      <div className="rounded-circle p-3 bg-info bg-opacity-10 text-info">
                        <FiDatabase size={24} />
                      </div>
                      <div>
                        <h6 className="text-muted small mb-1">Total Storage Used</h6>
                        <h3 className="fw-bold m-0 text-dark" style={{ fontSize: '1.4rem' }}>{formatBytes(analytics.totalStorage)}</h3>
                      </div>
                    </div>
                  </div>
                </div>

                <div className="col-12 col-md-6 col-lg-3">
                  <div className="card shadow-sm border-0 p-3 h-100 bg-white" style={{ borderRadius: '12px' }}>
                    <div className="d-flex align-items-center gap-3">
                      <div className="rounded-circle p-3 bg-purple bg-opacity-10 text-purple" style={{ color: '#6f42c1' }}>
                        <FiCpu size={24} />
                      </div>
                      <div>
                        <h6 className="text-muted small mb-1">AI Summaries</h6>
                        <h3 className="fw-bold m-0 text-dark">{analytics.aiSummariesCount} / {analytics.totalDocuments}</h3>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div className="row g-4">
                {/* Workspace type distribution */}
                <div className="col-12 col-lg-6">
                  <div className="card shadow-sm border-0 p-4 bg-white h-100" style={{ borderRadius: '12px' }}>
                    <h5 className="font-title mb-3 fw-bold text-dark">Workspace Types Distribution</h5>
                    <div className="d-flex flex-column gap-3">
                      {Object.entries(analytics.workspaceTypeBreakdown || {}).map(([type, count]) => {
                        const pct = analytics.totalWorkspaces ? ((count / analytics.totalWorkspaces) * 100).toFixed(0) : 0;
                        return (
                          <div key={type}>
                            <div className="d-flex justify-content-between mb-1">
                              <span className="small fw-semibold text-dark text-uppercase">{type}</span>
                              <span className="small text-muted">{count} ({pct}%)</span>
                            </div>
                            <div className="progress" style={{ height: '8px' }}>
                              <div className="progress-bar bg-primary" role="progressbar" style={{ width: `${pct}%` }}></div>
                            </div>
                          </div>
                        );
                      })}
                      {Object.keys(analytics.workspaceTypeBreakdown || {}).length === 0 && (
                        <p className="text-muted small">No workspace categories registered yet.</p>
                      )}
                    </div>
                  </div>
                </div>

                {/* Storage usage distribution */}
                <div className="col-12 col-lg-6">
                  <div className="card shadow-sm border-0 p-4 bg-white h-100" style={{ borderRadius: '12px' }}>
                    <h5 className="font-title mb-3 fw-bold text-dark">Workspace Storage Allocations</h5>
                    <div className="d-flex flex-column gap-3" style={{ maxHeight: '350px', overflowY: 'auto' }}>
                      {Object.entries(analytics.storageBreakdown || {}).map(([name, size]) => {
                        const pct = analytics.totalStorage ? ((size / analytics.totalStorage) * 100).toFixed(0) : 0;
                        return (
                          <div key={name}>
                            <div className="d-flex justify-content-between mb-1">
                              <span className="small fw-semibold text-dark">{name}</span>
                              <span className="small text-muted">{formatBytes(size)} ({pct}%)</span>
                            </div>
                            <div className="progress" style={{ height: '8px' }}>
                              <div className="progress-bar bg-success" role="progressbar" style={{ width: `${pct}%` }}></div>
                            </div>
                          </div>
                        );
                      })}
                      {Object.keys(analytics.storageBreakdown || {}).length === 0 && (
                        <p className="text-muted small">No file storage registered yet.</p>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: MANAGE WORKSPACES */}
          {activeTab === 'workspaces' && (
            <div className="card shadow-sm border-0 bg-white p-4 animate-fade-in" style={{ borderRadius: '12px' }}>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h5 className="font-title m-0 fw-bold text-dark">Registered Organizations</h5>
                <input 
                  type="text" 
                  className="form-control form-control-sm w-25" 
                  placeholder="Search workspaces..." 
                  value={searchWorkspace}
                  onChange={(e) => setSearchWorkspace(e.target.value)}
                />
              </div>

              <div className="table-responsive">
                <table className="table custom-table m-0">
                  <thead>
                    <tr>
                      <th>Workspace Name</th>
                      <th>Type</th>
                      <th>Admin Email</th>
                      <th>Users</th>
                      <th>Files</th>
                      <th>Storage</th>
                      <th>Status</th>
                      <th className="text-end">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {workspaces
                      .filter(ws => ws.name.toLowerCase().includes(searchWorkspace.toLowerCase()))
                      .map(ws => (
                        <tr key={ws.id}>
                          <td><strong>{ws.name}</strong></td>
                          <td><span className="badge bg-light text-dark border text-uppercase">{ws.workspaceType}</span></td>
                          <td>{ws.adminEmail || <span className="text-muted small">N/A</span>}</td>
                          <td>{ws.userCount}</td>
                          <td>{ws.docCount}</td>
                           <td>{formatBytes(ws.storageSize)} / <span className="text-muted">{formatBytes(ws.maxStorageLimit)}</span></td>
                          <td>
                            {ws.active ? (
                              <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25 d-inline-flex align-items-center gap-1">
                                <FiCheckCircle size={10} /> Active
                              </span>
                            ) : (
                              <span className="badge bg-danger bg-opacity-10 text-danger border border-danger border-opacity-25 d-inline-flex align-items-center gap-1">
                                <FiAlertTriangle size={10} /> Suspended
                              </span>
                            )}
                          </td>
                          <td className="text-end">
                            <div className="d-inline-flex gap-1">
                              <button 
                                className="btn btn-sm btn-outline-primary rounded-pill px-3"
                                onClick={() => handleEditQuota(ws.id, ws.name, ws.maxStorageLimit)}
                                title="Change Storage Quota"
                              >
                                <FiHardDrive size={12} className="me-1" /> Quota
                              </button>
                              <button 
                                className={`btn btn-sm ${ws.active ? 'btn-outline-danger' : 'btn-outline-success'} rounded-pill px-3`}
                                onClick={() => handleToggleStatus(ws.id, ws.active)}
                              >
                                {ws.active ? <><FiLock size={12} className="me-1" /> Suspend</> : <><FiUnlock size={12} className="me-1" /> Activate</>}
                              </button>
                              <button 
                                className="btn btn-sm btn-light border rounded-pill px-3"
                                onClick={() => handleResetAdmin(ws.id)}
                                title="Reset admin credentials to SecurePass123!"
                              >
                                <FiKey size={12} className="me-1" /> Reset Admin
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    {workspaces.length === 0 && (
                      <tr>
                        <td colSpan="8" className="text-center py-4 text-muted">No workspaces registered in database</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* TAB 3: PLATFORM USERS */}
          {activeTab === 'users' && (
            <div className="card shadow-sm border-0 bg-white p-4 animate-fade-in" style={{ borderRadius: '12px' }}>
              <div className="d-flex justify-content-between align-items-center mb-3">
                <h5 className="font-title m-0 fw-bold text-dark">Platform Registered Users</h5>
                <input 
                  type="text" 
                  className="form-control form-control-sm w-25" 
                  placeholder="Search email or name..." 
                  value={searchUser}
                  onChange={(e) => setSearchUser(e.target.value)}
                />
              </div>

              <div className="table-responsive">
                <table className="table custom-table m-0">
                  <thead>
                    <tr>
                      <th>User Name</th>
                      <th>Email Address</th>
                      <th>System Role</th>
                      <th>Workspace</th>
                      <th>Type</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users
                      .filter(u => u.name.toLowerCase().includes(searchUser.toLowerCase()) || u.email.toLowerCase().includes(searchUser.toLowerCase()))
                      .map(u => (
                        <tr key={u.id}>
                          <td><strong>{u.name}</strong></td>
                          <td>{u.email}</td>
                          <td><span className="badge bg-light text-primary border text-uppercase" style={{ fontSize: '0.7rem' }}>{u.role}</span></td>
                          <td><strong>{u.workspaceName}</strong></td>
                          <td><span className="badge bg-light text-muted border text-uppercase" style={{ fontSize: '0.65rem' }}>{u.workspaceType}</span></td>
                          <td>
                            {u.active ? (
                              <span className="badge bg-success bg-opacity-10 text-success border border-success border-opacity-10">Active</span>
                            ) : (
                              <span className="badge bg-secondary bg-opacity-10 text-muted border border-secondary border-opacity-10">Suspended</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    {users.length === 0 && (
                      <tr>
                        <td colSpan="6" className="text-center py-4 text-muted">No users registered in system</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default PlatformPortal;
