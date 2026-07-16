import React, { useState, useEffect, useRef } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';
import { 
  FiGrid, FiFileText, FiFolder, FiShare2, FiShield, FiTrash2, 
  FiFile, FiUsers, FiBarChart2, FiLogOut, FiBell, FiSearch, 
  FiMenu, FiX, FiCheckCircle
} from 'react-icons/fi';
import { toast } from 'react-toastify';
import OverlayPortal from '../components/OverlayPortal';

const DashboardLayout = ({ children, onSearch }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [showNotifications, setShowNotifications] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [historyData, setHistoryData] = useState({ recentSearches: [], frequentSearches: [] });
  const [showSuggestions, setShowSuggestions] = useState(false);

  const searchInputRef = useRef(null);
  const notificationButtonRef = useRef(null);

  const fetchSearchHistory = async () => {
    try {
      const res = await api.get('/ai/search-history');
      setHistoryData(res.data);
    } catch (err) {
      console.error('Failed to load search history');
    }
  };

  useEffect(() => {
    fetchUnreadCount();
    fetchNotifications();
    
    // Poll notifications every 30 seconds for live updates
    const interval = setInterval(() => {
      fetchUnreadCount();
    }, 30000);
    
    return () => clearInterval(interval);
  }, []);

  const fetchUnreadCount = async () => {
    try {
      const res = await api.get('/users/notifications/unread-count');
      setUnreadCount(res.data.unreadCount);
    } catch (err) {
      console.error('Failed to load notification count');
    }
  };

  const fetchNotifications = async () => {
    try {
      const res = await api.get('/users/notifications');
      setNotifications(res.data.slice(0, 8)); // Show recent 8 notifications
    } catch (err) {
      console.error('Failed to load notifications');
    }
  };

  const handleMarkAsRead = async (id) => {
    try {
      await api.put(`/users/notifications/${id}/read`);
      setNotifications(notifications.map(n => n.id === id ? { ...n, isRead: true } : n));
      fetchUnreadCount();
    } catch (err) {
      console.error('Failed to mark read');
    }
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (onSearch) {
      onSearch(searchQuery);
    } else {
      // If page is not documents page, redirect to documents with query
      navigate(`/documents?search=${encodeURIComponent(searchQuery)}`);
    }
  };

  const handleLogout = () => {
    logout();
    toast.success('Logged out successfully');
    navigate('/login');
  };

  const hasAdminPrivileges = user && (user.role === 'ADMIN' || user.role === 'SUPER_ADMIN');

  const menuItems = [
    { name: 'Dashboard', path: '/dashboard', icon: <FiGrid size={18} /> },
    { name: 'My Documents', path: '/documents', icon: <FiFileText size={18} /> },
    { name: 'Folders', path: '/folders', icon: <FiFolder size={18} /> },
    { name: 'Shared Files', path: '/shared', icon: <FiShare2 size={18} /> },
    { name: 'Security Center', path: '/security', icon: <FiShield size={18} /> },
    { name: 'Trash Bin', path: '/trash', icon: <FiTrash2 size={18} /> },
  ];

  const adminItems = [
    { name: 'User Manager', path: '/users', icon: <FiUsers size={18} /> },
    { name: 'System Reports', path: '/reports', icon: <FiBarChart2 size={18} /> },
  ];

  if (user?.role === 'SUPER_ADMIN') {
    adminItems.unshift({ name: 'Platform Portal', path: '/platform', icon: <FiShield size={18} /> });
    adminItems.push({ name: 'Global Audit Logs', path: '/audit-logs', icon: <FiFile size={18} /> });
  }

  return (
    <div className="d-flex">
      {/* Sidebar */}
      <div 
        className={`sidebar bg-white d-flex flex-column justify-content-between`}
        style={{ 
          transform: sidebarOpen ? 'translateX(0)' : 'translateX(-260px)', 
          width: '260px', 
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          zIndex: 1070
        }}
      >
        <div>
          {/* Logo */}
          <div className="d-flex align-items-center gap-2 mb-4 px-2">
            <div className="bg-primary text-white rounded p-2 d-flex align-items-center justify-content-center" style={{ width: '40px', height: '40px' }}>
              <FiFileText size={22} />
            </div>
            <div>
              <h4 className="m-0 font-title text-primary" style={{ letterSpacing: '0.5px' }}>SmartDocs</h4>
              {user?.workspaceName ? (
                <div className="d-flex flex-column">
                  <span className="text-truncate fw-bold text-dark" style={{ fontSize: '0.72rem', maxWidth: '160px' }}>{user.workspaceName}</span>
                  <span className="text-muted text-uppercase" style={{ fontSize: '0.55rem', fontWeight: 700 }}>{user.workspaceType}</span>
                </div>
              ) : (
                <small className="text-muted text-uppercase" style={{ fontSize: '0.65rem', fontWeight: 600 }}>Enterprise DMS</small>
              )}
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="nav flex-column gap-1">
            <span className="text-muted text-uppercase px-2 mb-2" style={{ fontSize: '0.65rem', fontWeight: 600, letterSpacing: '1px' }}>Workspace</span>
            {menuItems.map((item) => (
              <Link
                key={item.name}
                to={item.path}
                className={`nav-link d-flex align-items-center gap-3 px-3 py-2 rounded text-decoration-none ${
                  location.pathname === item.path ? 'bg-primary text-white' : 'text-dark hover-bg-light'
                }`}
                style={{ transition: 'all 0.2s', fontSize: '0.9rem', fontWeight: 500 }}
              >
                {item.icon}
                <span>{item.name}</span>
              </Link>
            ))}

            {hasAdminPrivileges && (
              <>
                <hr className="my-3 mx-2" style={{ borderColor: 'var(--border-light)' }} />
                <span className="text-muted text-uppercase px-2 mb-2" style={{ fontSize: '0.65rem', fontWeight: 600, letterSpacing: '1px' }}>Administration</span>
                {adminItems.map((item) => (
                  <Link
                    key={item.name}
                    to={item.path}
                    className={`nav-link d-flex align-items-center gap-3 px-3 py-2 rounded text-decoration-none ${
                      location.pathname === item.path ? 'bg-primary text-white' : 'text-dark hover-bg-light'
                    }`}
                    style={{ transition: 'all 0.2s', fontSize: '0.9rem', fontWeight: 500 }}
                  >
                    {item.icon}
                    <span>{item.name}</span>
                  </Link>
                ))}
              </>
            )}
          </nav>
        </div>

        {/* User Card & Logout */}
        <div className="border-top pt-3 px-2">
          <div className="d-flex align-items-center gap-3 mb-3">
            <div className="rounded-circle bg-secondary text-white d-flex align-items-center justify-content-center" style={{ width: '40px', height: '40px', fontWeight: 'bold' }}>
              {user?.name?.charAt(0).toUpperCase() || 'U'}
            </div>
            <div style={{ minWidth: 0 }}>
              <h6 className="m-0 text-truncate" style={{ fontSize: '0.9rem' }}>{user?.name || 'User Name'}</h6>
              <span className="badge bg-light text-dark border text-uppercase" style={{ fontSize: '0.6rem' }}>{user?.role?.replace('_', ' ')}</span>
            </div>
          </div>
          <button 
            onClick={handleLogout} 
            className="btn btn-outline-danger w-100 rounded-pill d-flex align-items-center justify-content-center gap-2 py-2"
            style={{ fontSize: '0.85rem', fontWeight: 500 }}
          >
            <FiLogOut size={16} />
            <span>Sign Out</span>
          </button>
        </div>
      </div>

      {/* Main Body container */}
      <div 
        className="main-content flex-grow-1"
        style={{ 
          marginLeft: sidebarOpen ? '260px' : '0', 
          transition: 'all 0.3s ease',
          backgroundColor: '#f8f9fa',
          minHeight: '100vh',
          width: sidebarOpen ? 'calc(100vw - 260px)' : '100vw'
        }}
      >
        {/* Top Navbar */}
        <header className="d-flex justify-content-between align-items-center mb-4 glass-header p-3 rounded-3 shadow-sm">
          <div className="d-flex align-items-center gap-3 flex-grow-1 me-4">
            <button className="btn btn-light border-0 d-md-none" onClick={() => setSidebarOpen(!sidebarOpen)}>
              <FiMenu size={20} />
            </button>
            
            {/* AI Search Bar */}
            <form onSubmit={handleSearchSubmit} className="position-relative flex-grow-1" style={{ maxWidth: '600px' }}>
              <FiSearch className="position-absolute top-50 translate-middle-y text-muted ms-3" size={18} />
              <input 
                ref={searchInputRef}
                type="text" 
                className="form-control rounded-pill py-2 ps-5 pe-4 bg-light border-0" 
                placeholder="AI Smart Search (e.g. 'show certificates uploaded yesterday' or 'finance reports')"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onFocus={() => {
                  fetchSearchHistory();
                  setShowSuggestions(true);
                }}
                onBlur={() => setTimeout(() => setShowSuggestions(false), 200)}
              />
              
              {/* Search Suggestions Dropdown */}
              <OverlayPortal
                isOpen={showSuggestions}
                onClose={() => setShowSuggestions(false)}
                type="dropdown"
                triggerEl={searchInputRef.current}
                align="stretch"
                closeOnOutsideClick={false}
              >
                <div 
                  className="bg-white rounded-3 shadow-lg border p-3 mt-1"
                  style={{
                    maxHeight: '350px',
                    overflowY: 'auto'
                  }}
                >
                  <div className="row">
                    {/* Recent & Frequent Searches */}
                    <div className="col-12 col-md-6 mb-3 mb-md-0">
                      <h6 className="text-muted small fw-bold mb-2 text-uppercase" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>RECENT SEARCHES</h6>
                      {historyData.recentSearches && historyData.recentSearches.length > 0 ? (
                        <div className="d-flex flex-column gap-1">
                          {historyData.recentSearches.map((q, idx) => (
                            <button
                              key={idx}
                              type="button"
                              className="btn btn-sm text-start hover-bg-light py-1 px-2 rounded text-truncate border-0 bg-transparent text-dark"
                              onClick={() => {
                                setSearchQuery(q);
                                if (onSearch) onSearch(q);
                                else navigate(`/documents?search=${encodeURIComponent(q)}`);
                              }}
                              style={{ fontSize: '0.8rem' }}
                            >
                              🕒 {q}
                            </button>
                          ))}
                        </div>
                      ) : (
                        <span className="text-muted small d-block px-2">No recent searches</span>
                      )}
                    </div>
                    
                    {/* Sample AI Prompts */}
                    <div className="col-12 col-md-6">
                      <h6 className="text-muted small fw-bold mb-2 text-uppercase" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>AI SUGGESTIONS</h6>
                      <div className="d-flex flex-wrap gap-2">
                        {['yesterday files', 'show certificates', 'java projects', 'finance invoices', 'PII files'].map((prompt, idx) => (
                          <button
                            key={idx}
                            type="button"
                            className="btn btn-sm btn-light border py-1 px-2.5 rounded-pill text-start font-monospace text-primary text-lowercase"
                            onClick={() => {
                              setSearchQuery(prompt);
                              if (onSearch) onSearch(prompt);
                              else navigate(`/documents?search=${encodeURIComponent(prompt)}`);
                            }}
                            style={{ fontSize: '0.72rem' }}
                          >
                            ✨ {prompt}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              </OverlayPortal>
            </form>
          </div>
 
          <div className="d-flex align-items-center gap-3">
            {/* Notification Bell */}
            <div className="position-relative">
              <button 
                ref={notificationButtonRef}
                className="btn btn-light rounded-circle p-2 position-relative" 
                onClick={() => {
                  setShowNotifications(!showNotifications);
                  if(!showNotifications) {
                    fetchNotifications();
                  }
                }}
              >
                <FiBell size={20} />
                {unreadCount > 0 && (
                  <span className="position-absolute top-0 start-100 translate-middle badge-unread">
                    {unreadCount}
                  </span>
                )}
              </button>
 
              {/* Notification Dropdown */}
              <OverlayPortal
                isOpen={showNotifications}
                onClose={() => setShowNotifications(false)}
                type="dropdown"
                triggerEl={notificationButtonRef.current}
                dropdownWidth={320}
                align="right"
              >
                <div 
                  className="bg-white rounded-3 shadow-lg border p-2 mt-1"
                  style={{ width: '320px' }}
                >
                  <div className="d-flex justify-content-between align-items-center px-2 py-1 border-bottom">
                    <h6 className="m-0 font-title">Notifications</h6>
                    <button className="btn btn-sm btn-link text-decoration-none" onClick={() => setShowNotifications(false)}>Close</button>
                  </div>
                  <div className="overflow-auto mt-1" style={{ maxHeight: '300px' }}>
                    {notifications.length === 0 ? (
                      <div className="text-center py-4 text-muted">
                        <small>No recent notifications</small>
                      </div>
                    ) : (
                      notifications.map(n => (
                        <div 
                          key={n.id} 
                          className={`p-2 border-bottom rounded ${n.isRead ? '' : 'bg-light'}`}
                          style={{ fontSize: '0.8rem' }}
                        >
                          <div className="d-flex justify-content-between align-items-start gap-1">
                            <p className="m-0 flex-grow-1 text-dark">{n.message}</p>
                            {!n.isRead && (
                              <button 
                                className="btn btn-link p-0 text-success" 
                                title="Mark as read"
                                onClick={() => handleMarkAsRead(n.id)}
                              >
                                <FiCheckCircle size={14} />
                              </button>
                            )}
                          </div>
                          <small className="text-muted" style={{ fontSize: '0.65rem' }}>
                            {new Date(n.createdAt).toLocaleDateString()} {new Date(n.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </small>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </OverlayPortal>
            </div>

            {/* Quick Profile Summary Indicator */}
            <div className="d-none d-lg-flex flex-column align-items-end" style={{ fontSize: '0.8rem' }}>
              <span className="fw-semibold text-dark">{user?.name}</span>
              <span className="text-muted text-uppercase" style={{ fontSize: '0.65rem' }}>{user?.role?.replace('_', ' ')}</span>
            </div>
          </div>
        </header>

        {/* Content Render Outlet */}
        <main className="animate-fade-in">
          {children}
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
