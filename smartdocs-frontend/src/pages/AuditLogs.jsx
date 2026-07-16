import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { FiSearch, FiFileText, FiFilter } from 'react-icons/fi';
import { toast } from 'react-toastify';
import { useAuth } from '../context/AuthContext';

const AuditLogs = () => {
  const { user } = useAuth();
  const [logs, setLogs] = useState([]);
  const [workspaces, setWorkspaces] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Filter states
  const [email, setEmail] = useState('');
  const [action, setAction] = useState('');
  const [browser, setBrowser] = useState('');
  const [os, setOs] = useState('');
  const [ipAddress, setIpAddress] = useState('');
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState('');
  
  // Paging states
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const isSuperAdmin = user?.role === 'SUPER_ADMIN';

  useEffect(() => {
    if (user && isSuperAdmin) {
      fetchWorkspaces();
    }
  }, [user, isSuperAdmin]);

  useEffect(() => {
    if (user) {
      fetchLogs();
    }
  }, [page, selectedWorkspaceId, user]);

  const fetchWorkspaces = async () => {
    try {
      const res = await api.get('/super/workspaces');
      setWorkspaces(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchLogs = async () => {
    setLoading(true);
    try {
      if (isSuperAdmin) {
        // Fetch from super endpoint (exact same call as PlatformPortal.jsx)
        const res = await api.get('/super/audit-logs');
        const rawLogs = res.data;
        
        // Filter by user input local filters
        const filtered = rawLogs.filter(l => {
          const matchWorkspace = !selectedWorkspaceId || l.workspaceName === selectedWorkspaceId;
          const matchEmail = !email.trim() || (l.userEmail && l.userEmail.toLowerCase().includes(email.trim().toLowerCase()));
          const matchAction = !action || l.action === action;
          const matchIp = !ipAddress.trim() || (l.ipAddress && l.ipAddress.includes(ipAddress.trim()));
          const matchOs = !os.trim() || (l.os && l.os.toLowerCase().includes(os.trim().toLowerCase()));
          return matchWorkspace && matchEmail && matchAction && matchIp && matchOs;
        });

        // Paginate locally
        const size = 15;
        const total = filtered.length;
        const pagesCount = Math.ceil(total / size);
        const offset = page * size;
        const pageData = filtered.slice(offset, offset + size);

        setLogs(pageData);
        setTotalPages(pagesCount || 1);
        setTotalElements(total);
      } else {
        // Fetch from regular endpoint
        const params = new URLSearchParams({
          page: page.toString(),
          size: '15'
        });
        
        if (email.trim()) params.append('email', email.trim());
        if (action.trim()) params.append('action', action.trim());
        if (browser.trim()) params.append('browser', browser.trim());
        if (os.trim()) params.append('os', os.trim());
        if (ipAddress.trim()) params.append('ipAddress', ipAddress.trim());

        const res = await api.get(`/audit?${params.toString()}`);
        setLogs(res.data.content);
        setTotalPages(res.data.totalPages);
        setTotalElements(res.data.totalElements);
      }
    } catch (err) {
      console.error("AuditLogs fetch error:", err);
      toast.error('Failed to load audit logs: ' + (err.response?.data?.message || err.message));
    } finally {
      setLoading(false);
    }
  };

  const handleFilterSubmit = (e) => {
    e.preventDefault();
    setPage(0);
    fetchLogs();
  };

  const handleReset = () => {
    setEmail('');
    setAction('');
    setBrowser('');
    setOs('');
    setIpAddress('');
    setSelectedWorkspaceId('');
    setPage(0);
    // Timeout to let state clear
    setTimeout(() => fetchLogs(), 50);
  };

  return (
    <div className="container-fluid px-0">
      <div className="mb-4">
        <h2 className="font-title m-0">{isSuperAdmin ? 'Global Security Audit Logs' : 'Audit Compliance Logs'}</h2>
        <p className="text-muted small m-0">
          {isSuperAdmin 
            ? 'Trace compliance events, logins, and document modifications platform-wide' 
            : 'Trace enterprise security events, user operations, and client devices'}
        </p>
      </div>

      {/* Filter Section */}
      <div className="bg-white p-3 rounded mb-4 shadow-sm border">
        <h6 className="mb-3 font-title d-flex align-items-center gap-2">
          <FiFilter className="text-primary" />
          <span>Search Filters</span>
        </h6>
        <form onSubmit={handleFilterSubmit} className="row g-3">
          {isSuperAdmin && (
            <div className="col-12 col-sm-6 col-md-4 col-lg-2.4">
              <select 
                className="form-select form-select-sm" 
                value={selectedWorkspaceId}
                onChange={(e) => {
                  setSelectedWorkspaceId(e.target.value);
                  setPage(0);
                }}
              >
                <option value="">All Workspaces</option>
                {workspaces.map(ws => (
                  <option key={ws.id} value={ws.name}>{ws.name}</option>
                ))}
              </select>
            </div>
          )}
          <div className="col-12 col-sm-6 col-md-4 col-lg-2.4">
            <input 
              type="text" 
              className="form-control form-control-sm" 
              placeholder="User Email..." 
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>
          <div className="col-12 col-sm-6 col-md-4 col-lg-2.4">
            <select 
              className="form-select form-select-sm" 
              value={action}
              onChange={(e) => setAction(e.target.value)}
            >
              <option value="">Choose Action...</option>
              <option value="LOGIN">LOGIN</option>
              <option value="REGISTER">REGISTER</option>
              <option value="UPLOAD_DOC">UPLOAD DOCUMENT</option>
              <option value="DOWNLOAD_DOC">DOWNLOAD DOCUMENT</option>
              <option value="SHARE_DOC">SHARE DOCUMENT</option>
              <option value="SOFT_DELETE_DOC">SOFT DELETE</option>
              <option value="RESTORE_DOC">RESTORE DOCUMENT</option>
              <option value="PERMANENT_DELETE_DOC">PERMANENT DELETE</option>
              <option value="CHANGE_ROLE">CHANGE ROLE</option>
              <option value="STATUS_CHANGE">STATUS CHANGE</option>
            </select>
          </div>
          <div className="col-12 col-sm-6 col-md-4 col-lg-2.4">
            <input 
              type="text" 
              className="form-control form-control-sm" 
              placeholder="Client IP..." 
              value={ipAddress}
              onChange={(e) => setIpAddress(e.target.value)}
            />
          </div>
          <div className="col-12 col-sm-6 col-md-4 col-lg-2.4">
            <input 
              type="text" 
              className="form-control form-control-sm" 
              placeholder="OS (e.g. Windows)..." 
              value={os}
              onChange={(e) => setOs(e.target.value)}
            />
          </div>
          <div className="col-12 col-sm-6 col-md-4 col-lg-2.4 text-end">
            <button type="submit" className="btn btn-sm btn-primary me-2 px-3">Filter</button>
            <button type="button" className="btn btn-sm btn-light border" onClick={handleReset}>Clear</button>
          </div>
        </form>
      </div>

      {/* Logs Table */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : logs.length === 0 ? (
        <div className="text-center py-5 bg-white rounded shadow-sm border">
          <FiFileText size={48} className="text-muted mb-3" />
          <h5>No compliance logs matched</h5>
        </div>
      ) : (
        <div>
          <div className="table-responsive shadow-sm rounded mb-3">
            <table className="table custom-table m-0" style={{ fontSize: '0.8rem' }}>
              <thead>
                <tr>
                  {isSuperAdmin && <th>Workspace</th>}
                  <th>User Email</th>
                  <th>Action</th>
                  <th>Document Reference</th>
                  <th>Client OS</th>
                  <th>Browser</th>
                  <th>Device</th>
                  <th>IP Address</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {logs.map(log => (
                  <tr key={log.id}>
                    {isSuperAdmin && (
                      <td>
                        <strong>{log.workspaceName || 'System'}</strong>
                      </td>
                    )}
                    <td>
                      <span className="fw-semibold text-dark">{log.userEmail || 'System'}</span>
                    </td>
                    <td>
                      <span className={`badge ${
                        log.action.includes("FAILED") ? "bg-danger" : 
                        log.action.includes("DELETE") ? "bg-warning text-dark" : "bg-light text-dark border"
                      }`}>
                        {log.action}
                      </span>
                    </td>
                    <td>
                      <span className="text-truncate d-inline-block" style={{ maxWidth: '140px' }} title={log.documentName}>
                        {log.documentName || <span className="text-muted">-</span>}
                      </span>
                    </td>
                    <td>{log.os}</td>
                    <td>{log.browser}</td>
                    <td>{log.device}</td>
                    <td><code>{log.ipAddress}</code></td>
                    <td>
                      <small className="text-muted">
                        {new Date(log.createdAt).toLocaleString()}
                      </small>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Paging controls */}
          <div className="d-flex justify-content-between align-items-center bg-white p-3 rounded shadow-sm">
            <div className="text-muted small">
              Showing logs page {page + 1} of {totalPages} (Total: {totalElements} logs)
            </div>
            <div className="d-flex gap-2">
              <button 
                className="btn btn-sm btn-outline-secondary" 
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </button>
              <button 
                className="btn btn-sm btn-outline-secondary" 
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AuditLogs;
