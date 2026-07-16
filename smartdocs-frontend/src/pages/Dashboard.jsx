import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';
import { 
  FiFileText, FiUsers, FiHardDrive, FiActivity, 
  FiUploadCloud, FiFolderPlus, FiShield, FiBarChart2
} from 'react-icons/fi';
import { 
  BarChart, Bar, XAxis, YAxis, CartesianGrid, 
  Tooltip, ResponsiveContainer, PieChart, Pie, Cell, Legend 
} from 'recharts';
import { Link } from 'react-router-dom';
import { toast } from 'react-toastify';

const Dashboard = () => {
  const { user } = useAuth();
  const [stats, setStats] = useState({
    totalUsers: 0,
    totalFiles: 0,
    storageUsed: 0,
    remainingStorage: 0,
    maxStorageLimit: 100 * 1024 * 1024,
    monthlyStats: [],
    categoryStats: []
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchStats();
  }, []);

  const fetchStats = async () => {
    try {
      const res = await api.get('/reports/summary');
      setStats(res.data);
    } catch (err) {
      toast.error('Failed to load dashboard metrics');
    } finally {
      setLoading(false);
    }
  };

  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const storageUsedPercent = Math.min(
    100, 
    Math.round((stats.storageUsed / stats.maxStorageLimit) * 100) || 0
  );

  const COLORS = ['#1a73e8', '#34a853', '#fbbc05', '#ea4335', '#9c27b0', '#00bcd4', '#ff5722', '#607d8b'];

  const isAdmin = user && (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN');
  const isEmployee = user && user.role === 'EMPLOYEE';

  if (loading) {
    return (
      <div className="d-flex justify-content-center align-items-center" style={{ height: '70vh' }}>
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="container-fluid px-0">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="font-title m-0">Welcome, {user?.name}!</h2>
          <p className="text-muted small m-0">Here is a quick overview of your document storage and activities</p>
        </div>
        <div className="text-muted small">
          Last generated: {stats.generatedAt || new Date().toLocaleString()}
        </div>
      </div>

      {/* AI Workspace Insights Banner */}
      {!isEmployee && stats.aiInsights && (
        <div className="card border-0 shadow-sm mb-4" style={{ background: 'linear-gradient(135deg, #e8f0fe 0%, #c2e7ff 100%)', borderRadius: '16px' }}>
          <div className="card-body p-4">
            <div className="d-flex align-items-start gap-3">
              <div className="rounded-3 bg-primary text-white p-2.5 d-flex align-items-center justify-content-center" style={{ minWidth: '46px', height: '46px' }}>
                <span style={{ fontSize: '1.25rem' }}>✨</span>
              </div>
              <div>
                <h6 className="fw-bold text-primary mb-1" style={{ fontSize: '0.7rem', letterSpacing: '0.5px' }}>AI WORKSPACE INSIGHTS</h6>
                <p className="m-0 text-dark fw-semibold" style={{ fontSize: '0.88rem', lineHeight: '1.5' }}>
                  {stats.aiInsights}
                </p>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Metrics Row */}
      <div className="row g-3 mb-4">
        {/* Total Documents */}
        <div className={isEmployee ? "col-12 col-md-6" : "col-12 col-sm-6 col-lg-3"}>
          <div className="smart-card h-100 d-flex align-items-center gap-3">
            <div className="rounded-3 bg-primary text-white p-3 d-flex align-items-center justify-content-center" style={{ width: '56px', height: '56px' }}>
              <FiFileText size={24} />
            </div>
            <div>
              <span className="text-muted small fw-semibold">{isEmployee ? 'My Documents' : 'Total Documents'}</span>
              <h3 className="m-0 fw-bold mt-1">{stats.totalFiles}</h3>
            </div>
          </div>
        </div>

        {/* Total Users (Admin only) / User Role Card */}
        {!isEmployee && (
          <div className="col-12 col-sm-6 col-lg-3">
            <div className="smart-card h-100 d-flex align-items-center gap-3">
              <div className="rounded-3 bg-success text-white p-3 d-flex align-items-center justify-content-center" style={{ width: '56px', height: '56px' }}>
                {isAdmin ? <FiUsers size={24} /> : <FiActivity size={24} />}
              </div>
              <div>
                <span className="text-muted small fw-semibold">{isAdmin ? 'Total Users' : 'Account Role'}</span>
                <h3 className="m-0 fw-bold mt-1">
                  {isAdmin ? stats.totalUsers : user?.role?.replace('_', ' ')}
                </h3>
              </div>
            </div>
          </div>
        )}

        {/* Storage Used & Progress Index */}
        <div className={isEmployee ? "col-12 col-md-6" : "col-12 col-lg-6"}>
          <div className="smart-card h-100 d-flex flex-column justify-content-between">
            <div className="d-flex align-items-center justify-content-between mb-2">
              <div className="d-flex align-items-center gap-3">
                <div className="rounded-3 bg-warning text-white p-3 d-flex align-items-center justify-content-center" style={{ width: '48px', height: '48px' }}>
                  <FiHardDrive size={20} />
                </div>
                <div>
                  <span className="text-muted small fw-semibold">{isEmployee ? 'My Storage' : 'Workspace Storage'}</span>
                  <h4 className="m-0 fw-bold mt-0.5" style={{ fontSize: '1.2rem' }}>
                    {formatBytes(stats.storageUsed)} 
                    <span className="text-muted font-body ms-1.5" style={{ fontSize: '0.8rem', fontWeight: 'normal' }}>
                      / {formatBytes(stats.maxStorageLimit)} limit
                    </span>
                  </h4>
                </div>
              </div>
              <span className="badge bg-primary rounded-pill px-2.5 py-1">{storageUsedPercent}% Used</span>
            </div>
            <div>
              <div className="progress rounded-pill mb-1.5" style={{ height: '7px' }}>
                <div 
                  className="progress-bar progress-bar-striped progress-bar-animated bg-warning" 
                  role="progressbar" 
                  style={{ width: `${storageUsedPercent}%` }} 
                  aria-valuenow={storageUsedPercent} 
                  aria-valuemin="0" 
                  aria-valuemax="100"
                ></div>
              </div>
              <div className="d-flex justify-content-between text-muted" style={{ fontSize: '0.72rem' }}>
                <span>{formatBytes(stats.remainingStorage)} remaining</span>
                <span>Max Capacity: {formatBytes(stats.maxStorageLimit)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* AI Analytics & Health Index */}
      <div className="row g-3 mb-4">
        {/* Health Score Gauge */}
        {!isEmployee && (
          <div className="col-12 col-md-4">
            <div className="smart-card d-flex align-items-center justify-content-between">
              <div className="d-flex align-items-center gap-3">
                {stats.healthScore === -1 || stats.healthScore === null || stats.healthScore === undefined ? (
                  <div className="d-flex align-items-center gap-2">
                    <div className="rounded-circle bg-light d-flex align-items-center justify-content-center text-muted" style={{ width: '48px', height: '48px', fontSize: '1.2rem' }}>
                      ⏳
                    </div>
                    <div>
                      <span className="text-muted small fw-bold d-block">Workspace Health Score</span>
                      <span className="small text-muted" style={{ fontSize: '0.72rem' }}>Waiting for workspace activity</span>
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="position-relative d-flex align-items-center justify-content-center" style={{ width: '60px', height: '60px' }}>
                      <svg width="60" height="60" style={{ transform: 'rotate(-90deg)' }}>
                        <circle
                          cx="30"
                          cy="30"
                          r="24"
                          fill="transparent"
                          stroke="#e9ecef"
                          strokeWidth="4.5"
                        />
                        <circle
                          cx="30"
                          cy="30"
                          r="24"
                          fill="transparent"
                          stroke={stats.healthScore >= 90 ? '#20c997' : stats.healthScore >= 75 ? '#fd7e14' : '#dc3545'}
                          strokeWidth="4.5"
                          strokeDasharray={2 * Math.PI * 24}
                          strokeDashoffset={2 * Math.PI * 24 * (1 - (stats.healthScore || 0) / 100)}
                          strokeLinecap="round"
                          style={{ transition: 'stroke-dashoffset 0.5s ease-in-out' }}
                        />
                      </svg>
                      <div className="position-absolute fw-bold text-dark" style={{ fontSize: '0.8rem' }}>
                        {stats.healthScore}%
                      </div>
                    </div>
                    <div>
                      <span className="text-muted small fw-bold d-block">Workspace Health Score</span>
                      <span className="small text-muted" style={{ fontSize: '0.75rem' }}>
                        {stats.healthScore >= 90 ? 'Excellent duplicate & tag status' : 'Cleanup recommended'}
                      </span>
                    </div>
                  </>
                )}
              </div>
              {stats.healthScore !== -1 && stats.healthScore !== null && (
                <div className="fw-bold px-2 py-1 bg-light rounded text-dark" style={{ fontSize: '0.65rem' }}>
                  Index
                </div>
              )}
            </div>
          </div>
        )}

        {/* AI Search Analytics */}
        <div className={isEmployee ? "col-12" : "col-12 col-md-4"}>
          <div className="smart-card d-flex align-items-center gap-3">
            <div className="rounded-3 p-3 d-flex align-items-center justify-content-center text-white" style={{ width: '56px', height: '56px', backgroundColor: '#0dcaf0' }}>
              <span style={{ fontSize: '1.25rem' }}>🔍</span>
            </div>
            <div>
              <span className="text-muted small fw-bold d-block">Top Searched Keyword</span>
              <h5 className="m-0 fw-bold mt-1 text-info text-capitalize" style={{ fontSize: '1rem' }}>
                {stats.topSearchKeyword || 'No searches yet'}
              </h5>
            </div>
          </div>
        </div>

        {/* AI Duplicate Analytics */}
        {!isEmployee && (
          <div className="col-12 col-md-4">
            <div className="smart-card d-flex align-items-center gap-3">
              <div className="rounded-3 p-3 d-flex align-items-center justify-content-center text-white" style={{ width: '56px', height: '56px', backgroundColor: '#ea4335' }}>
                <span style={{ fontSize: '1.25rem' }}>💾</span>
              </div>
              <div>
                <span className="text-muted small fw-bold d-block">Potential Storage Savings</span>
                <h5 className="m-0 fw-bold mt-1 text-danger">
                  {stats.potentialSavings > 0 ? (
                    <>
                      {formatBytes(stats.potentialSavings)} <span className="small text-muted fw-normal" style={{ fontSize: '0.75rem' }}>({stats.duplicateCount} duplicates)</span>
                    </>
                  ) : (
                    <span className="text-muted" style={{ fontSize: '0.85rem', fontWeight: 'normal' }}>No storage optimization available.</span>
                  )}
                </h5>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Charts Row */}
      <div className="row g-4 mb-4">
        {/* Monthly Upload Statistics */}
        <div className="col-12 col-lg-8">
          <div className="smart-card" style={{ minHeight: '380px' }}>
            <h5 className="mb-4 font-title fw-semibold d-flex align-items-center gap-2">
              <FiBarChart2 className="text-primary" />
              <span>Document Upload Timeline</span>
            </h5>
            <div style={{ width: '100%', height: '280px' }}>
              {stats.monthlyStats.length === 0 ? (
                <div className="d-flex justify-content-center align-items-center h-100 text-muted small">
                  No upload data recorded for charts yet.
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={stats.monthlyStats} margin={{ top: 5, right: 10, left: -20, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                    <XAxis dataKey="month" tick={{ fontSize: 11 }} />
                    <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                    <Tooltip />
                    <Bar dataKey="count" fill="var(--primary-blue)" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>
        </div>

        {/* Category Distribution Chart */}
        <div className="col-12 col-lg-4">
          <div className="smart-card" style={{ minHeight: '380px' }}>
            <h5 className="mb-4 font-title fw-semibold d-flex align-items-center gap-2">
              <FiFileText className="text-success" />
              <span>Category Layout</span>
            </h5>
            <div style={{ width: '100%', height: '280px' }}>
              {stats.categoryStats.length === 0 ? (
                <div className="d-flex justify-content-center align-items-center h-100 text-muted small">
                  Upload files to view category chart.
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={stats.categoryStats}
                      cx="50%"
                      cy="45%"
                      innerRadius={60}
                      outerRadius={80}
                      paddingAngle={4}
                      dataKey="count"
                      nameKey="category"
                    >
                      {stats.categoryStats.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                    <Legend layout="horizontal" verticalAlign="bottom" align="center" wrapperStyle={{ fontSize: '10px' }} />
                  </PieChart>
                </ResponsiveContainer>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Quick Action Pane */}
      <div className="smart-card mb-4 bg-white">
        <h5 className="mb-3 font-title fw-semibold">{isEmployee ? 'My Dashboard Links' : 'Workspace Quick Links'}</h5>
        <div className="row g-2">
          <div className={isEmployee ? "col-6 col-md-4" : "col-6 col-md-3"}>
            <Link to="/documents" className="btn btn-outline-primary w-100 py-3 rounded-3 d-flex flex-column align-items-center gap-2">
              <FiUploadCloud size={20} />
              <span className="small fw-semibold">Upload Files</span>
            </Link>
          </div>
          <div className={isEmployee ? "col-6 col-md-4" : "col-6 col-md-3"}>
            <Link to="/folders" className="btn btn-outline-success w-100 py-3 rounded-3 d-flex flex-column align-items-center gap-2">
              <FiFolderPlus size={20} />
              <span className="small fw-semibold">Create Folder</span>
            </Link>
          </div>
          <div className={isEmployee ? "col-12 col-md-4" : "col-6 col-md-3"}>
            <Link to="/security" className="btn btn-outline-warning w-100 py-3 rounded-3 d-flex flex-column align-items-center gap-2">
              <FiShield size={20} />
              <span className="small fw-semibold">Security Health</span>
            </Link>
          </div>
          {!isEmployee && (
            <div className="col-6 col-md-3">
              <Link to="/reports" className="btn btn-outline-danger w-100 py-3 rounded-3 d-flex flex-column align-items-center gap-2">
                <FiBarChart2 size={20} />
                <span className="small fw-semibold">Download Reports</span>
              </Link>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
