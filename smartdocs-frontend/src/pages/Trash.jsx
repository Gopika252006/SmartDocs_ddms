import React, { useState, useEffect } from 'react';
import api from '../services/api';
import { FiTrash2, FiRefreshCw, FiAlertTriangle, FiFileText } from 'react-icons/fi';
import { toast } from 'react-toastify';

const Trash = () => {
  const [trashedDocs, setTrashedDocs] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchTrash();
  }, []);

  const fetchTrash = async () => {
    setLoading(true);
    try {
      const res = await api.get('/docs/trash');
      setTrashedDocs(res.data);
    } catch (err) {
      toast.error('Failed to load trash bin items');
    } finally {
      setLoading(false);
    }
  };

  const handleRestore = async (id, name) => {
    try {
      await api.post(`/docs/${id}/restore`);
      toast.success(`Successfully restored: ${name}`);
      fetchTrash();
    } catch (err) {
      toast.error('Failed to restore document');
    }
  };

  const handlePermanentDelete = async (id, name) => {
    if (!window.confirm(`WARNING: Are you sure you want to permanently delete "${name}"? This action CANNOT be undone and will delete all files on disk.`)) return;

    try {
      await api.delete(`/docs/${id}/permanent`);
      toast.success(`Permanently deleted: ${name}`);
      fetchTrash();
    } catch (err) {
      toast.error('Failed to permanently delete document');
    }
  };

  const formatBytes = (bytes) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="container-fluid px-0">
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h2 className="font-title m-0">Trash Bin</h2>
          <p className="text-muted small m-0">Manage soft-deleted items or execute permanent removals</p>
        </div>
        <div className="d-flex align-items-center text-warning gap-1 bg-warning-light p-2 rounded small" style={{ backgroundColor: '#fff3cd' }}>
          <FiAlertTriangle size={16} />
          <span>Items will remain here until permanently cleared.</span>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status"></div>
        </div>
      ) : trashedDocs.length === 0 ? (
        <div className="text-center py-5 bg-white rounded shadow-sm border">
          <FiTrash2 size={48} className="text-muted mb-3" />
          <h5>Trash is empty</h5>
          <p className="text-muted small">Documents deleted from workspace folder structures will reside here.</p>
        </div>
      ) : (
        <div className="table-responsive shadow-sm rounded">
          <table className="table custom-table m-0">
            <thead>
              <tr>
                <th>Document Name</th>
                <th>File Type</th>
                <th>Size</th>
                <th>Status</th>
                <th className="text-end">Actions</th>
              </tr>
            </thead>
            <tbody>
              {trashedDocs.map(doc => (
                <tr key={doc.id}>
                  <td>
                    <div className="d-flex align-items-center gap-2">
                      <FiFileText className="text-muted" />
                      <span className="fw-semibold text-dark">{doc.name}</span>
                    </div>
                  </td>
                  <td>{doc.fileType}</td>
                  <td>{formatBytes(doc.fileSize)}</td>
                  <td>
                    <span className="badge bg-danger-light text-danger" style={{ backgroundColor: '#f8d7da' }}>Soft Deleted</span>
                  </td>
                  <td className="text-end">
                    <div className="d-inline-flex gap-1">
                      <button 
                        className="btn btn-sm btn-light border d-flex align-items-center gap-1 text-success" 
                        title="Restore" 
                        onClick={() => handleRestore(doc.id, doc.name)}
                      >
                        <FiRefreshCw size={12} />
                        <span>Restore</span>
                      </button>
                      <button 
                        className="btn btn-sm btn-light border d-flex align-items-center gap-1 text-danger" 
                        title="Delete Permanently" 
                        onClick={() => handlePermanentDelete(doc.id, doc.name)}
                      >
                        <FiTrash2 size={12} />
                        <span>Purge</span>
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default Trash;
