import React, { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import api from '../services/api';
import { 
  FiFileText, FiUploadCloud, FiTrash2, FiDownload, 
  FiEdit, FiShare2, FiEye, FiTag, FiCpu, FiPlus, FiClock, FiMoreVertical
} from 'react-icons/fi';
import { toast } from 'react-toastify';
import DocumentActionsDropdown from '../components/DocumentActionsDropdown';
import OverlayPortal from '../components/OverlayPortal';

const Files = () => {
  const [searchParams] = useSearchParams();
  const searchQueryParam = searchParams.get('search') || '';

  const [files, setFiles] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Upload State
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [selectedFile, setSelectedFile] = useState(null);
  const [customName, setCustomName] = useState('');
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [tagsInput, setTagsInput] = useState('');
  const [aiSuggestedCat, setAiSuggestedCat] = useState('');
  const [uploading, setUploading] = useState(false);

  // Version State
  const [showVersionModal, setShowVersionModal] = useState(false);
  const [activeDoc, setActiveDoc] = useState(null);
  const [versions, setVersions] = useState([]);

  // Share State
  const [showShareModal, setShowShareModal] = useState(false);
  const [shareEmail, setShareEmail] = useState('');
  const [sharing, setSharing] = useState(false);

  // AI Insights and Chat State
  const [selectedAIDoc, setSelectedAIDoc] = useState(null);
  const [chatOpen, setChatOpen] = useState(false);
  const [chatQuestion, setChatQuestion] = useState('');
  const [chatHistory, setChatHistory] = useState([]);
  const [chatLoading, setChatLoading] = useState(false);

  // Advanced AI Productivity & Enterprise States
  const [allFolders, setAllFolders] = useState([]);
  const [selectedMoveFolderId, setSelectedMoveFolderId] = useState('');
  const [suggestedFolder, setSuggestedFolder] = useState(null);
  const [relatedDocs, setRelatedDocs] = useState([]);
  const [targetLang, setTargetLang] = useState('Tamil');
  const [translatedSummary, setTranslatedSummary] = useState('');
  const [translating, setTranslating] = useState(false);
  const [enterpriseData, setEnterpriseData] = useState(null);
  const [enterpriseLoading, setEnterpriseLoading] = useState(false);
  const [enterpriseMode, setEnterpriseMode] = useState('CONTRACT');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [dropdownTriggerEl, setDropdownTriggerEl] = useState(null);
  const [dropdownDoc, setDropdownDoc] = useState(null);

  const getDropdownActions = (doc) => {
    if (!doc) return [];
    return [
      {
        label: 'Share File',
        icon: <FiShare2 size={14} className="text-muted" />,
        onClick: () => openShare(doc)
      },
      {
        label: 'Version History',
        icon: <FiClock size={14} className="text-muted" />,
        onClick: () => openVersions(doc)
      },
      {
        label: 'Smart Rename',
        icon: <FiEdit size={14} className="text-muted" />,
        onClick: () => handleRename(doc.id, doc.name)
      },
      { type: 'divider' },
      {
        label: 'Move to Trash',
        icon: <FiTrash2 size={14} className="text-danger" />,
        onClick: () => handleSoftDelete(doc.id),
        className: 'text-danger'
      }
    ];
  };

  useEffect(() => {
    if (selectedAIDoc) {
      setTranslatedSummary('');
      setEnterpriseData(null);
      fetchAISuggestedFolder(selectedAIDoc.id);
      
      if (selectedAIDoc.relatedDocumentIds) {
        const ids = selectedAIDoc.relatedDocumentIds.split(',').map(id => id.trim());
        const matched = files.filter(f => ids.includes(f.id.toString()));
        setRelatedDocs(matched);
      } else {
        setRelatedDocs([]);
      }
    }
  }, [selectedAIDoc, files]);

  const fetchAISuggestedFolder = async (docId) => {
    try {
      const res = await api.get(`/ai/suggest-folder/${docId}`);
      if (res.data && res.data.folderId) {
        setSuggestedFolder(res.data);
        setSelectedMoveFolderId(res.data.folderId.toString());
      } else {
        setSuggestedFolder(null);
        setSelectedMoveFolderId('');
      }
    } catch (err) {
      setSuggestedFolder(null);
      setSelectedMoveFolderId('');
    }
  };

  const fetchAllFolders = async () => {
    try {
      const res = await api.get('/folders/all');
      setAllFolders(res.data);
    } catch (err) {
      console.error('Failed to load folders list');
    }
  };

  const handleOpenInsights = async (doc) => {
    const hasMetadata = doc.aiSummary && !doc.aiSummary.includes("processing") && !doc.aiSummary.includes("unavailable");
    if (!hasMetadata) {
      toast.info("Generating AI insights for this document...");
    }
    try {
      const res = await api.get(`/ai/insights/${doc.id}`);
      setSelectedAIDoc(res.data);
      setFiles(prev => prev.map(f => f.id === doc.id ? res.data : f));
      setChatHistory([]);
      setChatOpen(false);
    } catch (err) {
      toast.error(err.response?.data?.message || "Failed to load AI Insights");
      setSelectedAIDoc(doc);
      setChatHistory([]);
      setChatOpen(false);
    }
  };

  const handleMoveToSuggestedFolder = async () => {
    if (!selectedAIDoc || !suggestedFolder) return;
    try {
      await api.put(`/docs/${selectedAIDoc.id}/move?folderId=${suggestedFolder.folderId}`);
      toast.success(`Document moved to folder: ${suggestedFolder.folderName}`);
      fetchFiles();
      setSuggestedFolder(null);
      setSelectedAIDoc(null);
    } catch (err) {
      toast.error('Failed to move document');
    }
  };

  const handleTranslate = async () => {
    if (!selectedAIDoc || !selectedAIDoc.aiSummary) return;
    setTranslating(true);
    try {
      const res = await api.post('/ai/translate', {
        text: selectedAIDoc.aiSummary,
        targetLanguage: targetLang
      });
      setTranslatedSummary(res.data.translatedText);
      toast.success('Translation completed');
    } catch (err) {
      toast.error('Translation failed');
    } finally {
      setTranslating(false);
    }
  };

  const handleEnterpriseAnalyze = async (mode) => {
    if (!selectedAIDoc) return;
    setEnterpriseMode(mode);
    setEnterpriseLoading(true);
    setEnterpriseData(null);
    try {
      const res = await api.post(`/ai/analyze-enterprise/${selectedAIDoc.id}?mode=${mode}`);
      setEnterpriseData(res.data);
      toast.success(`${mode} analysis completed`);
    } catch (err) {
      toast.error(`${mode} analysis failed`);
    } finally {
      setEnterpriseLoading(false);
    }
  };

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

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const rowsPerPage = 10;

  // General Filter States
  const [searchQuery, setSearchQuery] = useState(searchQueryParam);
  const [filterCategory, setFilterCategory] = useState('');
  const [filterType, setFilterType] = useState('');

  useEffect(() => {
    fetchCategories();
    fetchAllFolders();
    if (searchQueryParam) {
      handleAISearch(searchQueryParam);
    } else {
      fetchFiles();
    }
  }, [searchQueryParam]);
  useEffect(() => {
    setCurrentPage(1);
  }, [searchQuery, filterCategory, filterType]);
  const fetchFiles = async () => {
    setLoading(true);
    try {
      const res = await api.get('/docs/root');
      setFiles(res.data);
    } catch (err) {
      toast.error('Failed to load documents');
    } finally {
      setLoading(false);
    }
  };

  const fetchCategories = async () => {
    try {
      const res = await api.get('/categories');
      setCategories(res.data);
    } catch (err) {
      console.error('Failed to load categories');
    }
  };

  // Triggers AI Natural Language Search
  const handleAISearch = async (queryText) => {
    if (!queryText.trim()) return fetchFiles();
    setLoading(true);
    try {
      const res = await api.get(`/ai/search?query=${encodeURIComponent(queryText)}`);
      setFiles(res.data);
      toast.info(`AI Search returned ${res.data.length} matches`);
    } catch (err) {
      toast.error('AI Search failed');
    } finally {
      setLoading(false);
    }
  };

  // Triggers AI category suggestion when choosing a file
  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setSelectedFile(file);
    setCustomName(file.name);

    // Call AI Category suggestion
    try {
      const res = await api.get(`/ai/suggest-category?filename=${encodeURIComponent(file.name)}`);
      const suggestedName = res.data.suggestedCategory;
      setAiSuggestedCat(suggestedName);

      // Auto-select in category list if matched
      const matched = categories.find(c => c.name.toLowerCase() === suggestedName.toLowerCase());
      if (matched) {
        setSelectedCategoryId(matched.id);
      }
    } catch (err) {
      console.error('AI suggestion failed');
    }
  };

  const handleUploadSubmit = async (e) => {
    e.preventDefault();
    if (!selectedFile) return toast.warn('Please select a file');

    const formData = new FormData();
    formData.append('file', selectedFile);
    formData.append('name', customName);
    if (selectedCategoryId) {
      formData.append('folderId', ''); // Root folder upload
      formData.append('categoryId', selectedCategoryId);
    }
    
    // Format Tags
    if (tagsInput.trim()) {
      const tagsArray = tagsInput.split(',').map(t => t.trim()).filter(Boolean);
      tagsArray.forEach(tag => formData.append('tags', tag));
    }

    setUploading(true);
    try {
      await api.post('/docs/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      toast.success('Document uploaded successfully!');
      setShowUploadModal(false);
      resetUploadState();
      fetchFiles();
    } catch (err) {
      toast.error(err.response?.data?.message || 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  const resetUploadState = () => {
    setSelectedFile(null);
    setCustomName('');
    setSelectedCategoryId('');
    setTagsInput('');
    setAiSuggestedCat('');
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

  const handleSoftDelete = async (id) => {
    if (!window.confirm('Are you sure you want to move this document to trash?')) return;
    try {
      await api.delete(`/docs/${id}/soft`);
      toast.info('Document moved to trash');
      fetchFiles();
    } catch (err) {
      toast.error('Failed to delete document');
    }
  };

  const handleRename = async (id, currentName) => {
    let suggestedName = currentName;
    try {
      toast.info('AI is generating smart name suggestion...', { autoClose: 1000 });
      const suggestRes = await api.get(`/ai/suggest-name/${id}`);
      if (suggestRes.data && suggestRes.data.suggestedName) {
        suggestedName = suggestRes.data.suggestedName;
      }
    } catch (err) {
      console.warn('Could not load AI name suggestion, falling back', err);
    }

    const promptMessage = suggestedName !== currentName
      ? `Rename Document\nAI Suggested Name:\n${suggestedName}\n\nEnter new name:`
      : 'Enter new name for the document:';

    const newName = window.prompt(promptMessage, suggestedName);
    if (!newName || newName.trim() === '' || newName === currentName) return;

    try {
      await api.put(`/docs/${id}/rename?name=${encodeURIComponent(newName)}`);
      toast.success('Document renamed');
      fetchFiles();
      if (selectedAIDoc && selectedAIDoc.id === id) {
        setSelectedAIDoc(prev => ({ ...prev, name: newName }));
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Rename failed');
    }
  };

  // Version history actions
  const openVersions = async (doc) => {
    setActiveDoc(doc);
    setShowVersionModal(true);
    try {
      const res = await api.get(`/docs/${doc.id}/versions`);
      setVersions(res.data);
    } catch (err) {
      toast.error('Failed to load version history');
    }
  };

  const handleNewVersionUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    if (!window.confirm(`Upload ${file.name} as a new version?`)) return;

    const formData = new FormData();
    formData.append('file', file);

    try {
      await api.post(`/docs/${activeDoc.id}/new-version`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      toast.success('New version uploaded successfully!');
      openVersions(activeDoc); // Refresh versions list
      fetchFiles(); // Refresh main list
    } catch (err) {
      toast.error(err.response?.data?.message || 'Version upload failed');
    }
  };

  const handleDeleteVersion = async (versionNumber) => {
    if (!window.confirm(`Are you sure you want to delete Version v${versionNumber}?`)) return;

    try {
      await api.delete(`/docs/${activeDoc.id}/versions/${versionNumber}`);
      toast.success(`Version v${versionNumber} deleted successfully!`);
      
      fetchFiles();
      
      try {
        const res = await api.get(`/docs/${activeDoc.id}/versions`);
        if (res.data.length === 0) {
          setShowVersionModal(false);
        } else {
          setVersions(res.data);
        }
      } catch (e) {
        // Document deleted entirely (no versions left)
        setShowVersionModal(false);
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to delete version');
    }
  };

  // Sharing actions
  const openShare = (doc) => {
    setActiveDoc(doc);
    setShowShareModal(true);
    setShareEmail('');
  };

  const handleShareSubmit = async (e) => {
    e.preventDefault();
    if (!shareEmail.trim()) return;

    setSharing(true);
    try {
      await api.post(`/docs/${activeDoc.id}/share`, { email: shareEmail, permission: 'VIEW_ONLY' });
      toast.success(`Shared successfully with ${shareEmail}`);
      setShowShareModal(false);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to share document');
    } finally {
      setSharing(false);
    }
  };

  const handleChatSubmit = async (e) => {
    e.preventDefault();
    if (!chatQuestion.trim()) return;

    const question = chatQuestion.trim();
    setChatQuestion('');
    setChatHistory(prev => [...prev, { sender: 'user', text: question }]);
    setChatLoading(true);

    try {
      const res = await api.post(`/ai/chat/${selectedAIDoc.id}`, { question });
      setChatHistory(prev => [...prev, { sender: 'ai', text: res.data.answer }]);
    } catch (err) {
      const errorMsg = err.response?.data?.message || 'Sorry, I failed to get a response.';
      toast.error(errorMsg);
      setChatHistory(prev => [...prev, { sender: 'ai', text: errorMsg }]);
    } finally {
      setChatLoading(false);
    }
  };

  // Helper for formatting sizes
  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  const filteredFiles = files.filter(f => {
    if (filterCategory && f.categoryName !== filterCategory) return false;
    if (filterType && f.fileType !== filterType) return false;
    return true;
  });

  // Pagination Math
  const totalPages = Math.max(1, Math.ceil(filteredFiles.length / rowsPerPage));
  const indexOfLastRow = currentPage * rowsPerPage;
  const indexOfFirstRow = indexOfLastRow - rowsPerPage;
  const currentFiles = filteredFiles.slice(indexOfFirstRow, indexOfLastRow);

  return (
    <div className="container-fluid px-0">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="font-title m-0">My Documents</h2>
          <p className="text-muted small m-0">Upload and manage secure digital resources</p>
        </div>
        <button className="btn btn-primary-custom" onClick={() => setShowUploadModal(true)}>
          <FiUploadCloud size={16} />
          <span>Upload Document</span>
        </button>
      </div>

      {/* Filter and Search Row */}
      <div className="row g-3 mb-4 bg-white p-3 rounded shadow-sm">
        <div className="col-12 col-md-5">
          <div className="input-group">
            <input 
              type="text" 
              className="form-control" 
              placeholder="Search by keywords (or press enter for AI search)..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleAISearch(searchQuery)}
            />
            <button className="btn btn-primary d-flex align-items-center gap-1" onClick={() => handleAISearch(searchQuery)}>
              <FiCpu size={16} />
              <span>AI Search</span>
            </button>
          </div>
        </div>
        
        {/* Category filter */}
        <div className="col-6 col-md-3">
          <select className="form-select" value={filterCategory} onChange={(e) => setFilterCategory(e.target.value)}>
            <option value="">All Categories</option>
            {categories.map(c => <option key={c.id} value={c.name}>{c.name}</option>)}
          </select>
        </div>

        {/* File Type filter */}
        <div className="col-6 col-md-3">
          <select className="form-select" value={filterType} onChange={(e) => setFilterType(e.target.value)}>
            <option value="">All Extensions</option>
            <option value="PDF">PDF</option>
            <option value="DOCX">DOCX</option>
            <option value="PPTX">PPT/PPTX</option>
            <option value="PNG">PNG</option>
            <option value="JPG">JPG</option>
            <option value="ZIP">ZIP</option>
          </select>
        </div>

        <div className="col-12 col-md-1">
          <button className="btn btn-outline-secondary w-100" onClick={() => {
            setSearchQuery('');
            setFilterCategory('');
            setFilterType('');
            fetchFiles();
          }}>Reset</button>
        </div>
      </div>

      {/* Documents Grid / Table */}
      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : filteredFiles.length === 0 ? (
        <div className="text-center py-5 bg-white rounded shadow-sm">
          <FiFileText size={48} className="text-muted mb-3" />
          <h5>No documents found</h5>
          <p className="text-muted small">Upload your first document to start organizing files securely</p>
        </div>
      ) : (
        <>
          <div className="table-responsive shadow-sm rounded">
            <table className="table custom-table m-0">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Category</th>
                  <th>Classification</th>
                  <th>Size</th>
                  <th>Tags</th>
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
                        <div className="p-2 bg-light text-primary rounded">
                          <FiFileText size={18} />
                        </div>
                        <div>
                          <span className="fw-semibold text-dark">{doc.name}</span>
                          <span className="badge bg-light text-muted border ms-2" style={{ fontSize: '0.65rem' }}>{doc.fileType}</span>
                          {doc.sensitive && (
                            <span className="badge bg-danger bg-opacity-10 text-danger border border-danger border-opacity-25 ms-2" style={{ fontSize: '0.65rem' }}>
                              🔒 Sensitive
                            </span>
                          )}
                          {doc.piiDetected && (
                            <span className="badge bg-warning bg-opacity-10 text-warning border border-warning border-opacity-25 ms-2" style={{ fontSize: '0.65rem', color: '#856404' }}>
                              ⚠️ PII Found
                            </span>
                          )}
                        </div>
                      </div>
                    </td>
                    <td>
                      {doc.categoryName ? (
                        <span className="badge bg-light text-dark border">{doc.categoryName}</span>
                      ) : (
                        <span className="text-muted small">Uncategorized</span>
                      )}
                    </td>
                    <td>
                      <span className={`badge ${
                        doc.confidentialityClass === 'Highly Confidential' ? 'bg-danger text-white' :
                        doc.confidentialityClass === 'Confidential' ? 'bg-warning text-dark' :
                        doc.confidentialityClass === 'Internal' ? 'bg-info text-white' :
                        'bg-light text-dark border'
                      }`} style={{ fontSize: '0.7rem' }}>
                        {doc.confidentialityClass || 'Public'}
                      </span>
                    </td>
                    <td>{formatBytes(doc.fileSize)}</td>
                    <td>
                      <div className="d-flex flex-wrap gap-1" style={{ maxWidth: '200px' }}>
                        {doc.tags.map(t => (
                          <span key={t} className="badge bg-primary-light text-primary" style={{ fontSize: '0.7rem', backgroundColor: '#e8f0fe' }}>
                            <FiTag size={8} className="me-1" />
                            {t}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <small className="text-muted">
                        {new Date(doc.createdAt).toLocaleDateString()}
                      </small>
                    </td>
                    <td className="text-end">
                      <div className="d-inline-flex gap-1.5 align-items-center">
                        <button className="btn btn-sm btn-light border text-success d-inline-flex align-items-center" title="View in Browser" onClick={() => handleView(doc.id, doc.fileType, doc.name)}>
                          <FiEye size={13} className="me-1" />
                          <span className="d-none d-md-inline">Preview</span>
                        </button>
                        <button className="btn btn-sm btn-light border d-inline-flex align-items-center" title="Download" onClick={() => handleDownload(doc.id, doc.name)}>
                          <FiDownload size={13} className="me-1" />
                          <span className="d-none d-md-inline">Download</span>
                        </button>
                        <button className="btn btn-sm btn-outline-primary d-inline-flex align-items-center" title="AI Insights" onClick={() => handleOpenInsights(doc)}>
                          <FiCpu size={13} className="me-1" />
                          <span className="d-none d-md-inline">AI Insights</span>
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
              Showing {indexOfFirstRow + 1} to {Math.min(indexOfLastRow, filteredFiles.length)} of {filteredFiles.length} documents
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

      {/* Upload Document Modal */}
      <OverlayPortal
        isOpen={showUploadModal}
        onClose={() => { setShowUploadModal(false); resetUploadState(); }}
      >
        <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <h5 className="modal-title font-title">Upload New Document</h5>
                <button type="button" className="btn-close" onClick={() => { setShowUploadModal(false); resetUploadState(); }}></button>
              </div>
              <form onSubmit={handleUploadSubmit}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Select File (Max 150MB)</label>
                    <input type="file" className="form-control" onChange={handleFileChange} required />
                  </div>

                  {selectedFile && (
                    <>
                      <div className="mb-3">
                        <label className="form-label small fw-semibold text-muted">Document Name</label>
                        <input type="text" className="form-control" value={customName} onChange={(e) => setCustomName(e.target.value)} required />
                      </div>

                      <div className="mb-3">
                        <label className="form-label small fw-semibold text-muted">Category</label>
                        <select className="form-select" value={selectedCategoryId} onChange={(e) => setSelectedCategoryId(e.target.value)}>
                          <option value="">Choose category...</option>
                          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                        </select>
                        {aiSuggestedCat && (
                          <div className="mt-1 d-flex align-items-center text-primary gap-1" style={{ fontSize: '0.75rem' }}>
                            <FiCpu size={12} />
                            <span>AI Suggested Category: <strong>{aiSuggestedCat}</strong></span>
                          </div>
                        )}
                      </div>

                      <div className="mb-3">
                        <label className="form-label small fw-semibold text-muted">Tags (comma-separated)</label>
                        <input type="text" className="form-control" placeholder="e.g. Java, Invoice, 2026" value={tagsInput} onChange={(e) => setTagsInput(e.target.value)} />
                      </div>
                    </>
                  )}
                </div>
                <div className="modal-footer border-top">
                  <button type="button" className="btn btn-light" onClick={() => { setShowUploadModal(false); resetUploadState(); }}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={uploading || !selectedFile}>
                    {uploading ? 'Uploading...' : 'Upload'}
                  </button>
                </div>
              </form>
            </div>
      </OverlayPortal>

      {/* Version History Modal */}
      <OverlayPortal
        isOpen={showVersionModal && !!activeDoc}
        onClose={() => setShowVersionModal(false)}
        dialogClassName="modal-dialog modal-dialog-centered modal-lg"
      >
        {activeDoc && (
          <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <div>
                  <h5 className="modal-title font-title m-0">Version History</h5>
                  <p className="text-muted small m-0">{activeDoc.name}</p>
                </div>
                <button type="button" className="btn-close" onClick={() => setShowVersionModal(false)}></button>
              </div>
              <div className="modal-body">
                {/* Upload New Version section */}
                <div className="bg-light p-3 rounded mb-4 d-flex justify-content-between align-items-center">
                  <div>
                    <h6 className="m-0 fw-semibold">Upload New Version</h6>
                    <small className="text-muted">Save changes or replace current file with a newer copy</small>
                  </div>
                  <label className="btn btn-sm btn-primary m-0 d-inline-flex align-items-center gap-1">
                    <FiPlus size={14} />
                    <span>Upload File</span>
                    <input type="file" className="d-none" onChange={handleNewVersionUpload} />
                  </label>
                </div>

                <div className="table-responsive">
                  <table className="table custom-table m-0">
                    <thead>
                      <tr>
                        <th>Version</th>
                        <th>Size</th>
                        <th>Uploaded By</th>
                        <th>Uploaded Date</th>
                        <th className="text-end">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {versions.map((ver) => (
                        <tr key={ver.id}>
                          <td>
                            <span className="badge bg-primary rounded-pill">v{ver.versionNumber}</span>
                            {ver.versionNumber === versions.length && (
                              <span className="badge bg-success ms-2">Current</span>
                            )}
                          </td>
                          <td>{formatBytes(ver.fileSize)}</td>
                          <td>{ver.uploadedByName} ({ver.uploadedByEmail})</td>
                          <td>{new Date(ver.createdAt).toLocaleString()}</td>
                          <td className="text-end">
                            <div className="d-inline-flex gap-1">
                              <button className="btn btn-sm btn-light border" title="Download version" onClick={() => {
                                api.get(`/docs/${activeDoc.id}/download?version=${ver.versionNumber}`, { responseType: 'blob' })
                                  .then(res => {
                                    const url = window.URL.createObjectURL(new Blob([res.data]));
                                    const link = document.createElement('a');
                                    link.href = url;
                                    link.setAttribute('download', `v${ver.versionNumber}_${activeDoc.name}`);
                                    document.body.appendChild(link);
                                    link.click();
                                    link.remove();
                                  })
                                  .catch(() => toast.error('Failed to download version'));
                              }}>
                                <FiDownload size={14} />
                              </button>
                              <button className="btn btn-sm btn-light border text-danger" title="Delete version" onClick={() => handleDeleteVersion(ver.versionNumber)}>
                                <FiTrash2 size={14} />
                              </button>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
        )}
      </OverlayPortal>

      {/* Share Document Modal */}
      <OverlayPortal
        isOpen={showShareModal && !!activeDoc}
        onClose={() => setShowShareModal(false)}
      >
        {activeDoc && (
          <div className="modal-content border-0 shadow-lg rounded-3">
              <div className="modal-header border-bottom">
                <div>
                  <h5 className="modal-title font-title m-0">Share Document</h5>
                  <p className="text-muted small m-0">{activeDoc.name}</p>
                </div>
                <button type="button" className="btn-close" onClick={() => setShowShareModal(false)}></button>
              </div>
              <form onSubmit={handleShareSubmit}>
                <div className="modal-body">
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Recipient Email Address</label>
                    <input 
                      type="email" 
                      className="form-control" 
                      placeholder="user@company.com" 
                      value={shareEmail}
                      onChange={(e) => setShareEmail(e.target.value)}
                      required 
                    />
                  </div>
                  <div className="mb-3">
                    <label className="form-label small fw-semibold text-muted">Permissions</label>
                    <select className="form-select" disabled>
                      <option value="VIEW_ONLY">View Only (VIEW_ONLY)</option>
                    </select>
                    <small className="text-muted mt-1 d-block" style={{ fontSize: '0.7rem' }}>
                      Security policy defaults to read-only document access.
                    </small>
                  </div>
                </div>
                <div className="modal-footer border-top">
                  <button type="button" className="btn btn-light" onClick={() => setShowShareModal(false)}>Cancel</button>
                  <button type="submit" className="btn btn-primary" disabled={sharing || !shareEmail.trim()}>
                    {sharing ? 'Sharing...' : 'Share'}
                  </button>
                </div>
              </form>
            </div>
        )}
      </OverlayPortal>

      {/* AI Insights Side Panel */}
      <OverlayPortal
        isOpen={!!selectedAIDoc && !chatOpen}
        onClose={() => setSelectedAIDoc(null)}
        type="drawer"
      >
        {selectedAIDoc && (
          <div 
            className="position-fixed top-0 end-0 glass-drawer shadow-lg h-100 border-start animate-fade-in" 
            style={{ width: '420px', zIndex: 1060, transition: 'all 0.3s ease', display: 'flex', flexDirection: 'column' }}
          >
          {/* Header */}
          <div className="p-3 border-bottom d-flex justify-content-between align-items-center bg-light">
            <div className="d-flex align-items-center gap-2">
              <FiCpu className="text-primary" size={20} />
              <h5 className="m-0 font-title">AI Workspace Hub</h5>
            </div>
            <button className="btn-close" onClick={() => setSelectedAIDoc(null)}></button>
          </div>
          
          {/* Body */}
          <div className="p-3 flex-grow-1 overflow-y-auto" style={{ maxHeight: 'calc(100vh - 80px)' }}>
            <h6 className="fw-bold text-truncate mb-3 text-primary">{selectedAIDoc.name}</h6>

            <>
              {/* Duplicate Check Alert */}
              {selectedAIDoc.duplicateStatus === 'POTENTIAL_DUPLICATE' && (
                <div className="alert alert-warning p-2.5 mb-3" style={{ fontSize: '0.8rem' }}>
                  <strong>⚠️ Potential Duplicate!</strong>
                  <div className="mt-1">
                    This file matches another workspace document by {Math.round(selectedAIDoc.duplicateScore * 100)}%.
                  </div>
                </div>
              )}

              {/* Security AI Analysis */}
              {(selectedAIDoc.sensitive || selectedAIDoc.piiDetected) && (
                <div className="alert alert-danger p-3 mb-3 border-danger bg-danger bg-opacity-10 text-danger" style={{ fontSize: '0.8rem', borderRadius: '8px' }}>
                  <h6 className="alert-heading fw-bold mb-1 d-flex align-items-center gap-1">
                    🔒 Sensitive Data Found
                  </h6>
                  <div className="mt-2" style={{ lineHeight: '1.4' }}>
                    {selectedAIDoc.sensitive && <div>• Classified as <strong>🔒 SENSITIVE</strong> document. Share access is blocked and strict access controls are enforced.</div>}
                    {selectedAIDoc.piiDetected && <div>• <strong>Personally Identifiable Information (PII)</strong> detected.</div>}
                    {selectedAIDoc.sensitiveDetails && <div className="mt-1.5 small font-monospace bg-white bg-opacity-70 p-1.5 rounded text-dark border">Detected: {selectedAIDoc.sensitiveDetails}</div>}
                  </div>
                </div>
              )}

              {/* Confidentiality Classification */}
              <div className="mb-4">
                <label className="small fw-semibold text-muted text-uppercase mb-1" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>Confidentiality Classification</label>
                <div>
                  <span className={`badge ${
                    selectedAIDoc.confidentialityClass === 'Highly Confidential' ? 'bg-danger text-white' :
                    selectedAIDoc.confidentialityClass === 'Confidential' ? 'bg-warning text-dark' :
                    selectedAIDoc.confidentialityClass === 'Internal' ? 'bg-info text-white' :
                    'bg-light text-dark border'
                  } px-3 py-2 fw-bold`} style={{ fontSize: '0.75rem', borderRadius: '20px' }}>
                    {selectedAIDoc.confidentialityClass || 'Public'}
                  </span>
                </div>
              </div>

              {/* AI Summary */}
              <div className="mb-4">
                <label className="small fw-semibold text-muted text-uppercase mb-1" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>Document Summary</label>
                <div className="p-3 rounded bg-light border" style={{ fontSize: '0.85rem', lineHeight: '1.5' }}>
                  {selectedAIDoc.aiSummary || 'AI Summary is processing... Click "Re-run AI" if it takes too long.'}
                </div>
              </div>

              {/* Highlights */}
              {selectedAIDoc.aiHighlights && (
                <div className="mb-4">
                  <label className="small fw-semibold text-muted text-uppercase mb-1" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>Key Highlights</label>
                  <ul className="ps-3 mb-0" style={{ fontSize: '0.85rem' }}>
                    {selectedAIDoc.aiHighlights.split(',').map((h, i) => (
                      <li key={i} className="mb-1">{h.trim()}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* Keywords */}
              {selectedAIDoc.aiKeywords && (
                <div className="mb-4">
                  <label className="small fw-semibold text-muted text-uppercase mb-1" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>AI Keywords</label>
                  <div className="d-flex flex-wrap gap-1">
                    {selectedAIDoc.aiKeywords.split(',').map((kw, i) => (
                      <span key={i} className="badge bg-secondary-light text-secondary" style={{ fontSize: '0.7rem', backgroundColor: '#f1f3f5', border: '1px solid #dee2e6', color: '#495057' }}>
                        {kw.trim()}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Metadata properties */}
              {selectedAIDoc.aiMetadata && selectedAIDoc.aiMetadata !== '{}' && (
                <div className="mb-4">
                  <label className="small fw-semibold text-muted text-uppercase mb-1" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>Extracted Metadata</label>
                  <div className="p-2 bg-light rounded font-monospace" style={{ fontSize: '0.75rem' }}>
                    {Object.entries(JSON.parse(selectedAIDoc.aiMetadata)).map(([k, v]) => (
                      <div key={k} className="d-flex justify-content-between border-bottom py-1">
                        <span className="text-muted">{k}:</span>
                        <span className="fw-semibold text-dark text-truncate" style={{ maxWidth: '240px' }}>{String(v)}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Auto Folder Suggestion Alert */}
              {suggestedFolder && (
                <div className="alert alert-info p-3 mb-4 border-info bg-info bg-opacity-10 text-dark" style={{ fontSize: '0.8rem', borderRadius: '8px' }}>
                  <h6 className="alert-heading fw-bold mb-2 d-flex align-items-center gap-1 text-primary">
                    📂 AI Recommended Folder
                  </h6>
                  <div className="mb-2 text-muted fw-semibold">
                    AI recommends moving this document to: <strong>{suggestedFolder.folderName}</strong>
                  </div>
                  <div className="d-flex gap-2 align-items-center mt-2.5">
                    <select 
                      className="form-select form-select-sm" 
                      value={selectedMoveFolderId} 
                      onChange={(e) => setSelectedMoveFolderId(e.target.value)}
                      style={{ fontSize: '0.8rem' }}
                    >
                      <option value="">Choose folder...</option>
                      {allFolders.map(f => (
                        <option key={f.id} value={f.id}>{f.name}</option>
                      ))}
                    </select>
                    <button 
                      className="btn btn-sm btn-primary py-1.5 px-3 fw-bold flex-shrink-0" 
                      onClick={async () => {
                        if (!selectedMoveFolderId) return toast.warn("Please select a folder");
                        try {
                          await api.put(`/docs/${selectedAIDoc.id}/move?folderId=${selectedMoveFolderId}`);
                          const destFolder = allFolders.find(f => f.id.toString() === selectedMoveFolderId.toString());
                          toast.success(`Document moved to folder: ${destFolder ? destFolder.name : 'Selected Folder'}`);
                          fetchFiles();
                          setSuggestedFolder(null);
                          setSelectedAIDoc(null);
                        } catch (err) {
                          toast.error('Failed to move document');
                        }
                      }}
                    >
                      Move Document
                    </button>
                  </div>
                </div>
              )}

              {/* Related Workspace Suggestions */}
              <div className="mb-4">
                <label className="small fw-bold text-muted text-uppercase mb-2 d-block" style={{ fontSize: '0.65rem', letterSpacing: '0.5px' }}>🔗 Related Documents</label>
                {relatedDocs.length === 0 ? (
                  <div className="text-muted small p-2 bg-light rounded text-center border">No related files found in this workspace.</div>
                ) : (
                  <div className="list-group shadow-sm border rounded">
                    {relatedDocs.map(rDoc => (
                      <button 
                        key={rDoc.id} 
                        className="list-group-item list-group-item-action py-2 text-start text-truncate fw-semibold text-dark" 
                        style={{ fontSize: '0.8rem' }}
                        onClick={() => handleOpenInsights(rDoc)}
                      >
                        📄 {rDoc.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* AI Translation */}
              <div className="mb-4 p-3 bg-light rounded border">
                <label className="small fw-bold text-muted text-uppercase mb-2 d-block" style={{ fontSize: '0.65rem' }}>🌐 AI Translation</label>
                <div className="d-flex gap-2 mb-3">
                  <select 
                    className="form-select form-select-sm" 
                    value={targetLang} 
                    onChange={(e) => setTargetLang(e.target.value)}
                    style={{ fontSize: '0.8rem' }}
                  >
                    <option value="Tamil">Tamil (தமிழ்)</option>
                    <option value="English">English</option>
                    <option value="Spanish">Spanish</option>
                    <option value="French">French</option>
                  </select>
                  <button className="btn btn-sm btn-primary py-1 px-3 fw-bold" onClick={handleTranslate} disabled={translating}>
                    {translating ? 'Translating...' : 'Translate'}
                  </button>
                </div>
                {translatedSummary && (
                  <div className="bg-white p-2.5 rounded border text-dark" style={{ fontSize: '0.82rem', lineHeight: '1.4' }}>
                    <strong>Translation output:</strong>
                    <p className="m-0 mt-1">{translatedSummary}</p>
                  </div>
                )}
              </div>
            </>
          </div>

          {/* Actions list */}
          <div className="d-flex gap-2 mt-4 border-top pt-3">
            <button 
              className="btn btn-primary flex-grow-1 d-flex align-items-center justify-content-center gap-2 py-2"
              onClick={() => setChatOpen(true)}
            >
              <FiCpu size={16} />
              <span>Chat with Doc</span>
            </button>
            <button 
              className="btn btn-outline-secondary d-flex align-items-center justify-content-center gap-2 py-2"
              onClick={async () => {
                toast.info('Triggering AI Analysis...');
                try {
                  await api.post(`/ai/process/${selectedAIDoc.id}`);
                  toast.success('AI Analysis finished! Reloading...');
                  const res = await api.get('/docs/root');
                  setFiles(res.data);
                  const updatedDoc = res.data.find(f => f.id === selectedAIDoc.id);
                  if (updatedDoc) {
                    setSelectedAIDoc(updatedDoc);
                  }
                } catch (e) {
                  toast.error('AI Re-run failed');
                }
              }}
            >
              Re-run AI
            </button>
          </div>
        </div>
      )}
    </OverlayPortal>

      {/* Chat Q&A Drawer */}
      <OverlayPortal
        isOpen={chatOpen && !!selectedAIDoc}
        onClose={() => setChatOpen(false)}
        type="drawer"
      >
        {selectedAIDoc && (
          <div 
            className="position-fixed top-0 end-0 bg-white shadow-lg h-100 border-start animate-fade-in" 
            style={{ width: '480px', zIndex: 1070, transition: 'all 0.3s ease', display: 'flex', flexDirection: 'column' }}
          >
          {/* Header */}
          <div className="p-3 border-bottom d-flex justify-content-between align-items-center bg-primary text-white">
            <div>
              <h6 className="m-0 fw-bold">Document Q&A Chat</h6>
              <small className="text-white-50 text-truncate d-block" style={{ maxWidth: '380px' }}>{selectedAIDoc.name}</small>
            </div>
            <button className="btn-close btn-close-white" onClick={() => setChatOpen(false)}></button>
          </div>

          {/* Messages */}
          <div className="p-3 flex-grow-1 overflow-y-auto bg-light" style={{ maxHeight: 'calc(100vh - 140px)' }}>
            <div className="alert alert-info py-2 px-3 small mb-3">
              Ask any question about this document. The AI will answer referencing only this document context.
            </div>

            {chatHistory.length === 0 && (
              <div className="text-center text-muted py-5">
                <FiCpu size={32} className="mb-2" />
                <p className="small">No messages yet. Ask: "What are the key points in this doc?"</p>
              </div>
            )}

            {chatHistory.map((msg, idx) => (
              <div key={idx} className={`mb-3 d-flex ${msg.sender === 'user' ? 'justify-content-end' : 'justify-content-start'}`}>
                <div 
                  className={`p-3 rounded-3 shadow-sm ${msg.sender === 'user' ? 'bg-primary text-white' : 'bg-light border text-dark'}`}
                  style={{ fontSize: '0.88rem', maxWidth: '80%', borderRadius: '12px' }}
                >
                  {msg.text}
                </div>
              </div>
            ))}

            {chatLoading && (
              <div className="d-flex justify-content-start mb-3">
                <div className="p-3 rounded-3 bg-white border shadow-sm d-flex align-items-center gap-2">
                  <div className="spinner-border spinner-border-sm text-primary" role="status"></div>
                  <span className="small text-muted">Reading document...</span>
                </div>
              </div>
            )}
          </div>

          {/* Input Footer */}
          <form onSubmit={handleChatSubmit} className="p-3 border-top bg-white">
            <div className="input-group">
              <input 
                type="text" 
                className="form-control" 
                placeholder="Ask something..." 
                value={chatQuestion}
                onChange={(e) => setChatQuestion(e.target.value)}
                disabled={chatLoading}
                required
              />
              <button type="submit" className="btn btn-primary" disabled={chatLoading}>Send</button>
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

export default Files;
