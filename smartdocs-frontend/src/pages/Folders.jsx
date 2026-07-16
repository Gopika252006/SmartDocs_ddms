import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import api from '../services/api';
import { 
  FiFolder, FiPlus, FiChevronRight, FiFileText, 
  FiFolderPlus, FiEdit, FiTrash2, FiUploadCloud, FiDownload, FiEye, FiMoreVertical
} from 'react-icons/fi';
import { toast } from 'react-toastify';
import DocumentActionsDropdown from '../components/DocumentActionsDropdown';
import OverlayPortal from '../components/OverlayPortal';

const Folders = () => {
  const location = useLocation();
  const [folders, setFolders] = useState([]);
  const [currentFolder, setCurrentFolder] = useState(null); // null means root
  const [breadcrumbs, setBreadcrumbs] = useState([]); // Array of { id, name }
  const [filesInFolder, setFilesInFolder] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [dropdownTriggerEl, setDropdownTriggerEl] = useState(null);
  const [dropdownDoc, setDropdownDoc] = useState(null);

  const getDropdownActions = (doc) => {
    if (!doc) return [];
    return [
      {
        label: 'Rename File',
        icon: <FiEdit size={14} className="text-muted" />,
        onClick: () => handleRenameFile(doc.id, doc.name)
      },
      { type: 'divider' },
      {
        label: 'Move to Trash',
        icon: <FiTrash2 size={14} className="text-danger" />,
        onClick: () => handleSoftDeleteFile(doc.id),
        className: 'text-danger'
      }
    ];
  };

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const rowsPerPage = 10;

  useEffect(() => {
    setBreadcrumbs([]);
    setCurrentFolder(null);
    setCurrentPage(1);
  }, [location]);

  // Folder creation state
  const [showFolderModal, setShowFolderModal] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [creatingFolder, setCreatingFolder] = useState(false);

  // File upload inside folder state
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [customFileName, setCustomFileName] = useState('');
  const [uploading, setUploading] = useState(false);

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
    fetchDirectory();
    setCurrentPage(1);
  }, [currentFolder]);

  const fetchDirectory = async () => {
    setLoading(true);
    try {
      if (currentFolder === null) {
        // Load Root Folders
        const resFolders = await api.get('/folders');
        setFolders(resFolders.data);
        setFilesInFolder([]);
      } else {
        // Load Subfolders
        const resFolders = await api.get(`/folders/${currentFolder.id}/subfolders`);
        setFolders(resFolders.data);

        // Load Documents inside Folder
        const resDocs = await api.get(`/docs/folder/${currentFolder.id}`);
        setFilesInFolder(resDocs.data);
      }
    } catch (err) {
      toast.error('Failed to load directory files');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateFolder = async (e) => {
    e.preventDefault();
    if (!newFolderName.trim()) return;

    setCreatingFolder(true);
    try {
      await api.post('/folders', {
        name: newFolderName.trim(),
        parentFolderId: currentFolder ? currentFolder.id : null
      });
      toast.success('Folder created successfully!');
      setShowFolderModal(false);
      setNewFolderName('');
      fetchDirectory();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to create folder');
    } finally {
      setCreatingFolder(false);
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!selectedFile || !currentFolder) return toast.warn('Please select a file');

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('name', customFileName);
    formData.append('folderId', currentFolder.id);

    setUploading(true);
    try {
      await api.post('/docs/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      toast.success('Document uploaded inside folder!');
      setShowUploadModal(false);
      setSelectedFile(null);
      setCustomFileName('');
      fetchDirectory();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const handleRenameFolder = async (folderId, currentName) => {
    const newName = window.prompt('Enter new name for the folder:', currentName);
    if (!newName || newName.trim() === '' || newName === currentName) return;

    try {
      await api.put(`/folders/${folderId}/rename?name=${encodeURIComponent(newName)}`);
      toast.success('Folder renamed');
      fetchDirectory();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Rename failed');
    }
  };

  const handleDeleteFolder = async (folderId) => {
    if (!window.confirm('WARNING: Deleting this folder will soft-delete all nested folders and documents inside it recursively. Proceed?')) return;

    try {
      await api.delete(`/folders/${folderId}`);
      toast.info('Folder deleted');
      fetchDirectory();
    } catch (err) {
      toast.error('Failed to delete folder');
    }
  };

  const handleSoftDeleteFile = async (id) => {
    if (!window.confirm("Are you sure you want to move this file to Trash?")) return;
    try {
      await api.delete(`/docs/${id}/soft`);
      toast.success("Document moved to Trash successfully!");
      fetchDirectory();
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to delete document");
    }
  };

  const handleRenameFile = async (id, currentName) => {
    const newName = window.prompt("Rename Document:", currentName);
    if (!newName || newName.trim() === "" || newName === currentName) return;
    try {
      await api.put(`/docs/${id}/rename?name=${encodeURIComponent(newName)}`);
      toast.success("Document renamed successfully!");
      fetchDirectory();
    } catch (err) {
      toast.error(err.response?.data?.message || "Rename failed");
    }
  };

  const handleDownloadFile = (id, name) => {
    api.get(`/docs/${id}/download`, { responseType: 'blob' })
      .then((res) => {
        const url = window.URL.createObjectURL(new Blob([res.data]));
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', name);
        document.body.appendChild(link);
        link.click();
        link.remove();
      })
      .catch(() => toast.error('Failed to download file'));
  };

  const getMimeType = (fileType) => {
    if (!fileType) return 'application/octet-stream';
    switch (fileType.toUpperCase()) {
      case 'PDF': return 'application/pdf';
      case 'PNG': return 'image/png';
      case 'JPG':
      case 'JPEG': return 'image/jpeg';
      case 'GIF': return 'image/gif';
      case 'WEBP': return 'image/webp';
      case 'BMP': return 'image/bmp';
      case 'MP3': return 'audio/mpeg';
      case 'WAV': return 'audio/wav';
      case 'AAC': return 'audio/aac';
      case 'M4A': return 'audio/x-m4a';
      case 'OGG': return 'audio/ogg';
      case 'MP4': return 'video/mp4';
      case 'AVI': return 'video/x-msvideo';
      case 'MOV': return 'video/quicktime';
      case 'MKV': return 'video/x-matroska';
      case 'WEBM': return 'video/webm';
      case 'TXT': return 'text/plain';
      default: return 'application/octet-stream';
    }
  };

  const handleView = (id, fileType, name) => {
    setPreviewDocId(id);
    const typeLower = fileType.toLowerCase();
    setZoomScale(1);
    setIsPlaying(false);
    
    if (typeLower === 'mp3' || typeLower === 'wav' || typeLower === 'aac' || typeLower === 'm4a' || typeLower === 'ogg' ||
        typeLower === 'mp4' || typeLower === 'avi' || typeLower === 'mov' || typeLower === 'mkv' || typeLower === 'webm') {
      const token = localStorage.getItem('token') || sessionStorage.getItem('token') || '';
      const streamURL = `http://${window.location.hostname}:8080/api/docs/${id}/download?token=${token}`;
      setPreviewUrl(streamURL);
      setPreviewName(name);
      if (typeLower === 'mp3' || typeLower === 'wav' || typeLower === 'aac' || typeLower === 'm4a' || typeLower === 'ogg') {
        setPreviewType('audio');
      } else {
        setPreviewType('video');
      }
      return;
    }

    api.get(`/docs/${id}/download`, { responseType: 'blob' })
      .then((res) => {
        const mime = getMimeType(fileType);
        const file = new Blob([res.data], { type: mime });
        const fileURL = URL.createObjectURL(file);
        
        setPreviewUrl(fileURL);
        setPreviewName(name);
        
        if (typeLower === 'pdf') {
          setPreviewType('pdf');
        } else if (typeLower === 'png' || typeLower === 'jpg' || typeLower === 'jpeg' || typeLower === 'gif' || typeLower === 'webp' || typeLower === 'bmp') {
          setPreviewType('image');
        } else if (typeLower === 'txt' || typeLower === 'java' || typeLower === 'py' || typeLower === 'js' || typeLower === 'cpp' || typeLower === 'c' || typeLower === 'html' || typeLower === 'css') {
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

  const navigateToFolder = (folder) => {
    setBreadcrumbs([...breadcrumbs, { id: folder.id, name: folder.name }]);
    setCurrentFolder(folder);
  };

  const navigateToBreadcrumb = (index) => {
    if (index === -1) {
      // Navigate to Root
      setBreadcrumbs([]);
      setCurrentFolder(null);
    } else {
      const target = breadcrumbs[index];
      setBreadcrumbs(breadcrumbs.slice(0, index + 1));
      setCurrentFolder({ id: target.id, name: target.name });
    }
  };

  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  // Files Pagination Math inside folders
  const totalPages = Math.max(1, Math.ceil(filesInFolder.length / rowsPerPage));
  const indexOfLastRow = currentPage * rowsPerPage;
  const indexOfFirstRow = indexOfLastRow - rowsPerPage;
  const currentFiles = filesInFolder.slice(indexOfFirstRow, indexOfLastRow);

  return (
    <div className="container-fluid px-0">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="font-title m-0">Directory Folders</h2>
          <p className="text-muted small m-0">Structure files in nested folders</p>
        </div>
        <div className="d-flex gap-2">
          {currentFolder && (
            <button className="btn btn-outline-primary" onClick={() => setShowUploadModal(true)}>
              <FiUploadCloud size={16} className="me-1" />
              <span>Upload inside</span>
            </button>
          )}
          <button className="btn btn-primary-custom" onClick={() => setShowFolderModal(true)}>
            <FiFolderPlus size={16} />
            <span>Create Folder</span>
          </button>
        </div>
      </div>

      <div className="d-flex align-items-center flex-wrap gap-2 mb-4 bg-white p-2.5 rounded-pill shadow-sm border" style={{ maxWidth: 'fit-content' }}>
        <button 
          className={`btn btn-sm rounded-pill px-3 py-1 d-inline-flex align-items-center ${breadcrumbs.length === 0 ? 'btn-primary' : 'btn-light border text-primary'}`}
          onClick={() => navigateToBreadcrumb(-1)}
          style={{ fontSize: '0.8rem', fontWeight: 500 }}
        >
          <FiFolder className="me-1.5" size={13} /> Root
        </button>
        {breadcrumbs.map((crumb, idx) => (
          <React.Fragment key={crumb.id}>
            <FiChevronRight className="text-muted" size={12} />
            <button 
              className={`btn btn-sm rounded-pill px-3 py-1 ${idx === breadcrumbs.length - 1 ? 'btn-outline-primary active border-0 fw-bold' : 'btn-light border text-primary'}`}
              onClick={() => navigateToBreadcrumb(idx)}
              style={{ fontSize: '0.8rem', fontWeight: 500 }}
              disabled={idx === breadcrumbs.length - 1}
            >
              {crumb.name}
            </button>
          </React.Fragment>
        ))}
      </div>

      {/* Directory Content List */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : (
        <div>
          {/* Folders Section */}
          <h5 className="mb-3 font-title fw-semibold">Folders ({folders.length})</h5>
          {folders.length === 0 ? (
            <div className="text-muted small mb-4 py-2 px-3 bg-white rounded border">No subfolders created here</div>
          ) : (
            <div className="row g-3 mb-5">
              {folders.map(folder => (
                <div key={folder.id} className="col-12 col-sm-6 col-md-4 col-lg-3">
                  <div className="smart-card d-flex align-items-center justify-content-between p-3">
                    <div 
                      className="d-flex align-items-center gap-3 cursor-pointer flex-grow-1" 
                      onClick={() => navigateToFolder(folder)}
                      style={{ cursor: 'pointer' }}
                    >
                      <div className="bg-warning-light text-warning p-2 rounded" style={{ backgroundColor: '#fff3cd' }}>
                        <FiFolder size={24} />
                      </div>
                      <div style={{ minWidth: 0 }}>
                        <h6 className="m-0 text-truncate text-dark fw-semibold" style={{ fontSize: '0.9rem' }}>{folder.name}</h6>
                        <small className="text-muted" style={{ fontSize: '0.7rem' }}>Directory Folder</small>
                      </div>
                    </div>
                    
                    {/* Folder actions dropdown/buttons */}
                    <div className="d-flex gap-1 ms-2">
                      <button className="btn btn-sm btn-link p-0 text-dark" title="Rename" onClick={() => handleRenameFolder(folder.id, folder.name)}>
                        <FiEdit size={14} />
                      </button>
                      <button className="btn btn-sm btn-link p-0 text-danger" title="Delete" onClick={() => handleDeleteFolder(folder.id)}>
                        <FiTrash2 size={14} />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Files Section (Only visible inside a folder) */}
          {currentFolder && (
            <div>
              <h5 className="mb-3 font-title fw-semibold">Documents ({filesInFolder.length})</h5>
              {filesInFolder.length === 0 ? (
                <div className="text-center py-5 bg-white rounded shadow-sm border">
                  <FiFileText size={32} className="text-muted mb-2" />
                  <p className="text-muted small m-0">No documents uploaded inside this folder</p>
                </div>
              ) : (
                <>
                  <div className="table-responsive shadow-sm rounded">
                    <table className="table custom-table m-0">
                      <thead>
                        <tr>
                          <th>Name</th>
                          <th>Type</th>
                          <th>Size</th>
                          <th>Uploaded</th>
                          <th className="text-end">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {currentFiles.map((doc, idx) => {
                          const isNearBottom = currentFiles.length > 3 && idx >= currentFiles.length - 3;
                          return (
                            <tr key={doc.id}>
                              <td>
                                <div className="d-flex align-items-center gap-2">
                                  <FiFileText className="text-primary" />
                                  <span className="fw-semibold text-dark">{doc.name}</span>
                                </div>
                              </td>
                              <td>{doc.fileType}</td>
                              <td>{formatBytes(doc.fileSize)}</td>
                              <td>{new Date(doc.createdAt).toLocaleDateString()}</td>
                              <td className="text-end">
                                <div className="d-inline-flex gap-1.5 align-items-center">
                                  <button className="btn btn-sm btn-light border text-success" title="View in Browser" onClick={() => handleView(doc.id, doc.fileType, doc.name)}>
                                    <FiEye size={14} />
                                  </button>
                                  <button className="btn btn-sm btn-light border" onClick={() => handleDownloadFile(doc.id, doc.name)}>
                                    <FiDownload size={14} />
                                  </button>
                                  
                                  {/* Action Dropdown Menu */}
                                  <button 
                                    className="btn btn-sm btn-light border dropdown-toggle no-caret px-2 d-inline-flex align-items-center" 
                                    type="button" 
                                    onClick={(e) => {
                                      e.stopPropagation();
                                      setDropdownTriggerEl(e.currentTarget);
                                      setDropdownDoc(doc);
                                      setDropdownOpen(true);
                                    }}
                                  >
                                    <FiMoreVertical size={13} />
                                  </button>
                                </div>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                  <div className="d-flex justify-content-between align-items-center mt-3 pt-3 border-top flex-wrap gap-2 px-3 pb-3 bg-white shadow-sm rounded-bottom">
                    <span className="text-muted small">
                      Showing {indexOfFirstRow + 1} to {Math.min(indexOfLastRow, filesInFolder.length)} of {filesInFolder.length} documents
                    </span>
                    {totalPages > 1 && (
                      <nav>
                        <ul className="pagination pagination-sm m-0">
                          <li className={`page-item ${currentPage === 1 ? 'disabled' : ''}`}>
                            <button className="page-link" onClick={() => setCurrentPage(prev => Math.max(1, prev - 1))}>
                              Previous
                            </button>
                          </li>
                          {Array.from({ length: totalPages }, (_, i) => i + 1)
                            .filter(p => p === 1 || p === totalPages || Math.abs(p - currentPage) <= 1)
                            .map((p, idx, arr) => {
                              const showEllipsis = idx > 0 && p - arr[idx - 1] > 1;
                              return (
                                <React.Fragment key={p}>
                                  {showEllipsis && <li className="page-item disabled"><span className="page-link">...</span></li>}
                                  <li className={`page-item ${currentPage === p ? 'active' : ''}`}>
                                    <button className="page-link" onClick={() => setCurrentPage(p)}>
                                      {p}
                                    </button>
                                  </li>
                                </React.Fragment>
                              );
                            })}
                          <li className={`page-item ${currentPage === totalPages ? 'disabled' : ''}`}>
                            <button className="page-link" onClick={() => setCurrentPage(prev => Math.min(totalPages, prev + 1))}>
                              Next
                            </button>
                          </li>
                        </ul>
                      </nav>
                    )}
                  </div>
                </>
              )}
            </div>
          )}
        </div>
      )}

      {/* Create Folder Modal */}
      <OverlayPortal
        isOpen={showFolderModal}
        onClose={() => setShowFolderModal(false)}
      >
        <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <h5 className="modal-title font-title">Create New Folder</h5>
                <button type="button" className="btn-close" onClick={() => setShowFolderModal(false)}></button>
              </div>
              <form onSubmit={handleCreateFolder}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Folder Name</label>
                    <input 
                      type="text" 
                      className="form-control" 
                      placeholder="e.g. Projects, Invoices" 
                      value={newFolderName}
                      onChange={(e) => setNewFolderName(e.target.value)}
                      required 
                    />
                  </div>
                </div>
                <div className="modal-footer border-top">
                  <button type="button" className="btn btn-light" onClick={() => setShowFolderModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={creatingFolder || !newFolderName.trim()}>
                    {creatingFolder ? 'Creating...' : 'Create'}
                  </button>
                </div>
              </form>
            </div>
      </OverlayPortal>

      {/* Upload Modal (Inside Folder) */}
      <OverlayPortal
        isOpen={showUploadModal && !!currentFolder}
        onClose={() => setShowUploadModal(false)}
      >
        {currentFolder && (
          <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <div>
                  <h5 className="modal-title font-title m-0">Upload into Folder</h5>
                  <p className="text-muted small m-0">Current: {currentFolder.name}</p>
                </div>
                <button type="button" className="btn-close" onClick={() => setShowUploadModal(false)}></button>
              </div>
              <form onSubmit={handleUploadSubmit}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Select File (Max 150MB)</label>
                    <input 
                      type="file" 
                      className="form-control" 
                      onChange={(e) => {
                        const file = e.target.files[0];
                        if (file) {
                          setSelectedFile(file);
                          setCustomFileName(file.name);
                        }
                      }}
                      required 
                    />
                  </div>
                  {selectedFile && (
                    <div className="mb-3">
                      <label className="form-label small fw-semibold text-muted">File Display Name</label>
                      <input 
                        type="text" 
                        className="form-control" 
                        value={customFileName}
                        onChange={(e) => setCustomFileName(e.target.value)}
                        required 
                      />
                    </div>
                  )}
                </div>
                <div className="modal-footer border-top">
                  <button type="button" className="btn btn-light" onClick={() => setShowUploadModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={uploading || !selectedFile}>
                    {uploading ? 'Uploading...' : 'Upload'}
                  </button>
                </div>
              </form>
            </div>
        )}
      </OverlayPortal>
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
                    <button className="btn btn-primary rounded-pill px-4" onClick={() => { handleDownloadFile(previewDocId, previewName); setPreviewUrl(null); }}>
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
      <DocumentActionsDropdown 
        isOpen={dropdownOpen} 
        onClose={() => {
          setDropdownOpen(false);
          setDropdownTriggerEl(null);
          setDropdownDoc(null);
        }}
        triggerEl={dropdownTriggerEl}
        actions={getDropdownActions(dropdownDoc)}
      />
    </div>
  );
};

export default Folders;
