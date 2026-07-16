import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { FiShare2, FiDownload, FiUser, FiMail, FiCalendar, FiEye, FiFileText } from 'react-icons/fi';
import { toast } from 'react-toastify';
import OverlayPortal from '../components/OverlayPortal';

const Shared = () => {
  const [sharedWithMe, setSharedWithMe] = useState([]);
  const [sharedByMe, setSharedByMe] = useState([]);
  const [activeTab, setActiveTab] = useState('with-me'); // with-me, by-me
  const [loading, setLoading] = useState(true);
  const [filterQuery, setFilterQuery] = useState('');

  // Preview State
  const [previewUrl, setPreviewUrl] = useState(null);
  const [previewType, setPreviewType] = useState(null);
  const [previewName, setPreviewName] = useState('');
  const [previewText, setPreviewText] = useState('');
  const [previewDocId, setPreviewDocId] = useState(null);
  const [zoomScale, setZoomScale] = useState(1);
  const [isPlaying, setIsPlaying] = useState(false);
  const [mediaVolume, setMediaVolume] = useState(0.8);
  const mediaRef = React.useRef(null);

  useEffect(() => {
    fetchSharedDocs();
  }, [activeTab]);

  const fetchSharedDocs = async () => {
    setLoading(true);
    try {
      if (activeTab === 'with-me') {
        const res = await api.get('/docs/shared/with-me');
        setSharedWithMe(res.data);
      } else {
        const res = await api.get('/docs/shared/by-me');
        setSharedByMe(res.data);
      }
    } catch (err) {
      toast.error('Failed to load shared document logs');
    } finally {
      setLoading(false);
    }
  };

  const handleDownload = (id, name) => {
    api.get(`/docs/${id}/download`, { responseType: 'blob' })
      .then((res) => {
        const url = window.URL.createObjectURL(new Blob([res.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', name);
        document.body.appendChild(link);
        link.click();
        link.remove();
        toast.success('Download started');
      })
      .catch(() => toast.error('Failed to download shared file'));
  };

  const getMimeType = (filename) => {
    if (!filename) return 'application/octet-stream';
    const parts = filename.split('.');
    const ext = parts[parts.length - 1].toLowerCase();
    switch (ext) {
      case 'pdf': return 'application/pdf';
      case 'png': return 'image/png';
      case 'jpg':
      case 'jpeg': return 'image/jpeg';
      case 'gif': return 'image/gif';
      case 'webp': return 'image/webp';
      case 'bmp': return 'image/bmp';
      case 'mp3': return 'audio/mpeg';
      case 'wav': return 'audio/wav';
      case 'aac': return 'audio/aac';
      case 'm4a': return 'audio/x-m4a';
      case 'ogg': return 'audio/ogg';
      case 'mp4': return 'video/mp4';
      case 'avi': return 'video/x-msvideo';
      case 'mov': return 'video/quicktime';
      case 'mkv': return 'video/x-matroska';
      case 'webm': return 'video/webm';
      case 'txt': return 'text/plain';
      default: return 'application/octet-stream';
    }
  };

  const handleView = (id, filename) => {
    setPreviewDocId(id);
    const parts = filename.split('.');
    const ext = parts[parts.length - 1].toLowerCase();
    setZoomScale(1);
    setIsPlaying(false);
    
    if (ext === 'mp3' || ext === 'wav' || ext === 'aac' || ext === 'm4a' || ext === 'ogg' ||
        ext === 'mp4' || ext === 'avi' || ext === 'mov' || ext === 'mkv' || ext === 'webm') {
      const token = localStorage.getItem('token') || sessionStorage.getItem('token') || '';
      const streamURL = `http://${window.location.hostname}:8080/api/docs/${id}/download?token=${token}`;
      setPreviewUrl(streamURL);
      setPreviewName(filename);
      if (ext === 'mp3' || ext === 'wav' || ext === 'aac' || ext === 'm4a' || ext === 'ogg') {
        setPreviewType('audio');
      } else {
        setPreviewType('video');
      }
      return;
    }

    api.get(`/docs/${id}/download`, { responseType: 'blob' })
      .then((res) => {
        const mime = getMimeType(filename);
        const file = new Blob([res.data], { type: mime });
        const fileURL = URL.createObjectURL(file);
        
        setPreviewUrl(fileURL);
        setPreviewName(filename);
        
        if (ext === 'pdf') {
          setPreviewType('pdf');
        } else if (ext === 'png' || ext === 'jpg' || ext === 'jpeg' || ext === 'gif' || ext === 'webp' || ext === 'bmp') {
          setPreviewType('image');
        } else if (ext === 'txt' || ext === 'java' || ext === 'py' || ext === 'js' || ext === 'cpp' || ext === 'c' || ext === 'html' || ext === 'css') {
          setPreviewType('text');
          file.text().then(txt => setPreviewText(txt)).catch(() => setPreviewText('Could not load text content.'));
        } else {
          setPreviewType('other');
        }
      })
      .catch(() => toast.error('Failed to preview document'));
  };

  const togglePlay = () => {
    if (!mediaRef.current) return;
    if (isPlaying) {
      mediaRef.current.pause();
      setIsPlaying(false);
    } else {
      mediaRef.current.play().then(() => setIsPlaying(true)).catch(() => {});
    }
  };

  const restartMedia = () => {
    if (!mediaRef.current) return;
    mediaRef.current.currentTime = 0;
    mediaRef.current.play().then(() => setIsPlaying(true)).catch(() => {});
  };

  const changeVolume = (amount) => {
    if (!mediaRef.current) return;
    let newVolume = Math.min(1, Math.max(0, mediaRef.current.volume + amount));
    mediaRef.current.volume = newVolume;
    setMediaVolume(newVolume);
  };

  const zoomIn = () => {
    setZoomScale(prev => Math.min(3, prev + 0.1));
  };

  const zoomOut = () => {
    setZoomScale(prev => Math.max(0.3, prev - 0.1));
  };

  const resetZoom = () => {
    setZoomScale(1);
  };

  const closePreview = () => {
    if (mediaRef.current) {
      mediaRef.current.pause();
    }
    setPreviewUrl(null);
    setPreviewType(null);
    setPreviewText('');
    setZoomScale(1);
    setIsPlaying(false);
  };

  return (
    <div className="container-fluid px-0">
      <div className="mb-4">
        <h2 className="font-title m-0">Shared Documents</h2>
        <p className="text-muted small m-0">Collaborate and manage files shared inside the network</p>
      </div>

      {/* Tabs */}
      <div className="card shadow-sm border-0 mb-4 bg-white p-2" style={{ borderRadius: '12px' }}>
        <ul className="nav nav-pills gap-1">
          <li className="nav-item">
            <button 
              className={`nav-link rounded-pill px-4 fw-semibold border-0 ${activeTab === 'with-me' ? 'active btn-primary' : 'text-dark'}`}
              onClick={() => setActiveTab('with-me')}
            >
              Shared with Me
            </button>
          </li>
          <li className="nav-item">
            <button 
              className={`nav-link rounded-pill px-4 fw-semibold border-0 ${activeTab === 'by-me' ? 'active btn-primary' : 'text-dark'}`}
              onClick={() => setActiveTab('by-me')}
            >
              Shared by Me
            </button>
          </li>
        </ul>
      </div>

      {/* Search Filter Bar */}
      <div className="bg-white p-3 rounded mb-4 shadow-sm border d-flex gap-2 align-items-center">
        <FiShare2 className="text-primary me-2" size={18} />
        <input 
          type="text" 
          className="form-control" 
          placeholder="Filter shared documents by name, sender, or email..." 
          value={filterQuery}
          onChange={(e) => setFilterQuery(e.target.value)}
        />
        {filterQuery && (
          <button className="btn btn-sm btn-light border rounded-pill px-3" onClick={() => setFilterQuery('')}>
            Clear
          </button>
        )}
      </div>

      {/* Listing Content */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : activeTab === 'with-me' ? (
        // Shared With Me View
        sharedWithMe.length === 0 ? (
          <div className="text-center py-5 bg-white rounded shadow-sm border">
            <FiShare2 size={48} className="text-muted mb-3" />
            <h5>No shared files found</h5>
            <p className="text-muted small">Documents shared with you by other users will appear here</p>
          </div>
        ) : (
          <div className="table-responsive shadow-sm rounded">
            <table className="table custom-table m-0">
              <thead>
                <tr>
                  <th>Document Name</th>
                  <th>Shared By</th>
                  <th>Date Shared</th>
                  <th>Permission</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {sharedWithMe
                  .filter(share => {
                    const term = filterQuery.toLowerCase();
                    return (share.documentName || '').toLowerCase().includes(term) ||
                           (share.sharedByName || '').toLowerCase().includes(term) ||
                           (share.sharedByEmail || '').toLowerCase().includes(term);
                  })
                  .map(share => (
                    <tr key={share.id}>
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <FiShare2 className="text-success" />
                          <span className="fw-semibold text-dark">{share.documentName}</span>
                        </div>
                      </td>
                      <td>
                        <div>
                          <span className="fw-semibold text-dark">{share.sharedByName}</span>
                          <span className="d-block text-muted" style={{ fontSize: '0.75rem' }}>{share.sharedByEmail}</span>
                        </div>
                      </td>
                      <td>{new Date(share.createdAt).toLocaleDateString()}</td>
                      <td>
                        <span className="badge bg-light text-primary border text-uppercase" style={{ fontSize: '0.65rem' }}>
                          {share.permission}
                        </span>
                      </td>
                      <td className="text-end">
                        <div className="d-inline-flex gap-1.5 align-items-center">
                          <button className="btn btn-sm btn-light border text-success" title="View Preview" onClick={() => handleView(share.documentId, share.documentName)}>
                            <FiEye size={13} />
                          </button>
                          <button className="btn btn-sm btn-light border" title="Download" onClick={() => handleDownload(share.documentId, share.documentName)}>
                            <FiDownload size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )
      ) : (
        // Shared By Me View
        sharedByMe.length === 0 ? (
          <div className="text-center py-5 bg-white rounded shadow-sm border">
            <FiShare2 size={48} className="text-muted mb-3" />
            <h5>No outbound shares found</h5>
            <p className="text-muted small">Share files from 'My Documents' page to coordinate workspaces</p>
          </div>
        ) : (
          <div className="table-responsive shadow-sm rounded">
            <table className="table custom-table m-0">
              <thead>
                <tr>
                  <th>Document Name</th>
                  <th>Shared With</th>
                  <th>Date Shared</th>
                  <th>Permission</th>
                  <th className="text-end">Actions</th>
                </tr>
              </thead>
              <tbody>
                {sharedByMe
                  .filter(share => {
                    const term = filterQuery.toLowerCase();
                    return (share.documentName || '').toLowerCase().includes(term) ||
                           (share.sharedWithEmail || '').toLowerCase().includes(term);
                  })
                  .map(share => (
                    <tr key={share.id}>
                      <td>
                        <div className="d-flex align-items-center gap-2">
                          <FiShare2 className="text-primary" />
                          <span className="fw-semibold text-dark">{share.documentName}</span>
                        </div>
                      </td>
                      <td>
                        <span className="fw-semibold text-dark">{share.sharedWithEmail}</span>
                      </td>
                      <td>{new Date(share.createdAt).toLocaleDateString()}</td>
                      <td>
                        <span className="badge bg-light text-primary border text-uppercase" style={{ fontSize: '0.65rem' }}>
                          {share.permission}
                        </span>
                      </td>
                      <td className="text-end">
                        <div className="d-inline-flex gap-1.5 align-items-center">
                          <button className="btn btn-sm btn-light border text-success" title="View Preview" onClick={() => handleView(share.documentId, share.documentName)}>
                            <FiEye size={13} />
                          </button>
                          <button className="btn btn-sm btn-light border" title="Download" onClick={() => handleDownload(share.documentId, share.documentName)}>
                            <FiDownload size={13} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {/* Document Preview Modal */}
      <OverlayPortal
        isOpen={!!previewUrl}
        onClose={closePreview}
        dialogClassName="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable"
        dialogStyle={{ maxWidth: '80%' }}
      >
        <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom bg-light">
                <h5 className="modal-title font-title text-dark fw-bold">{previewName}</h5>
                <button type="button" className="btn-close" onClick={closePreview}></button>
              </div>
              <div className="modal-body p-0" style={{ backgroundColor: '#f8f9fa' }}>
                {previewType === 'pdf' && (
                  <div style={{ overflow: 'hidden', width: '100%', height: '75vh', position: 'relative' }}>
                    <iframe src={previewUrl} style={{ width: '100%', height: '100%', border: 'none', transform: `scale(${zoomScale})`, transformOrigin: 'top center', transition: 'transform 0.15s ease' }} title={previewName} />
                  </div>
                )}
                {previewType === 'image' && (
                  <div className="text-center p-4 bg-white" style={{ overflow: 'hidden', display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '300px' }}>
                    <img src={previewUrl} alt={previewName} style={{ maxWidth: '100%', maxHeight: '75vh', objectFit: 'contain', borderRadius: '4px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', transform: `scale(${zoomScale})`, transformOrigin: 'center center', transition: 'transform 0.15s ease' }} />
                  </div>
                )}
                {previewType === 'audio' && (
                  <div className="text-center p-5 bg-white">
                    <div className="mb-4">
                      <FiFileText size={48} className="text-primary mb-2" />
                      <h6 className="text-dark">Audio Player Preview</h6>
                      <span className="text-muted small">Format: {previewName.split('.').pop().toUpperCase()}</span>
                    </div>
                    <audio ref={mediaRef} controls src={previewUrl} style={{ width: '100%', maxWidth: '600px' }} className="w-100" onPlay={() => setIsPlaying(true)} onPause={() => setIsPlaying(false)} />
                  </div>
                )}
                {previewType === 'video' && (
                  <div className="text-center p-3 bg-white" style={{ overflow: 'hidden', display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '300px', backgroundColor: '#000' }}>
                    <video ref={mediaRef} controls src={previewUrl} style={{ maxWidth: '100%', maxHeight: '70vh', transform: `scale(${zoomScale})`, transformOrigin: 'center center', transition: 'transform 0.15s ease' }} onPlay={() => setIsPlaying(true)} onPause={() => setIsPlaying(false)} />
                  </div>
                )}
                {previewType === 'text' && (
                  <div className="p-3 bg-white">
                    <pre className="m-0 p-3 bg-light border rounded text-dark fs-6" style={{ maxHeight: '75vh', overflow: 'auto', whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
                      {previewText || 'Loading content...'}
                    </pre>
                  </div>
                )}
                {previewType === 'other' && (
                  <div className="text-center p-5 bg-white">
                    <FiFileText size={48} className="text-muted mb-3" />
                    <h5>Preview Not Supported</h5>
                    <p className="text-muted small">This file format cannot be previewed inline inside the browser.</p>
                    <button className="btn btn-primary rounded-pill px-4" onClick={() => { handleDownload(previewDocId, previewName); setPreviewUrl(null); }}>
                      <FiDownload size={14} className="me-2" /> Download to View
                    </button>
                  </div>
                )}

                {/* Media and Zoom Controller Bar */}
                <div className="d-flex align-items-center justify-content-center gap-3 p-3 bg-light border-top flex-wrap">
                  {/* Playback Controls */}
                  {(previewType === 'video' || previewType === 'audio') && (
                    <div className="d-flex align-items-center gap-2 border-end pe-3">
                      <button className="btn btn-sm btn-primary rounded-pill px-3 fw-bold" onClick={togglePlay}>
                        {isPlaying ? 'Pause' : 'Play / Resume'}
                      </button>
                      <button className="btn btn-sm btn-secondary rounded-pill px-3" onClick={restartMedia}>
                        Restart
                      </button>
                      <button className="btn btn-sm btn-outline-secondary" onClick={() => changeVolume(-0.1)} title="Volume Down">
                        Vol -
                      </button>
                      <button className="btn btn-sm btn-outline-secondary" onClick={() => changeVolume(0.1)} title="Volume Up">
                        Vol +
                      </button>
                      <span className="text-muted small ms-1">Vol: {Math.round(mediaVolume * 100)}%</span>
                    </div>
                  )}

                  {/* Zoom Controls */}
                  {(previewType === 'image' || previewType === 'video' || previewType === 'pdf') && (
                    <div className="d-flex align-items-center gap-2">
                      <button className="btn btn-sm btn-outline-dark rounded-circle" onClick={zoomOut} style={{ width: '32px', height: '32px', padding: 0, display: 'flex', justifyContent: 'center', alignItems: 'center' }} title="Zoom Out">
                        -
                      </button>
                      <span className="text-dark fw-bold small px-1">{Math.round(zoomScale * 100)}%</span>
                      <button className="btn btn-sm btn-outline-dark rounded-circle" onClick={zoomIn} style={{ width: '32px', height: '32px', padding: 0, display: 'flex', justifyContent: 'center', alignItems: 'center' }} title="Zoom In">
                        +
                      </button>
                      <button className="btn btn-sm btn-link text-muted p-0 ms-1 small" onClick={resetZoom}>
                        Reset Zoom
                      </button>
                    </div>
                  )}
                </div>
              </div>
              <div className="modal-footer bg-light border-top">
                <button type="button" className="btn btn-secondary rounded-pill px-4" onClick={closePreview}>Close</button>
              </div>
            </div>
      </OverlayPortal>
    </div>
  );
};

export default Shared;
