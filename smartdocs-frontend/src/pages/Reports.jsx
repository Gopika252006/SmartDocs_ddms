import React, { useState } from 'react';
import { createPortal } from 'react-dom';
import api from '../services/api';
import { FiBarChart2, FiUsers, FiFileText, FiActivity, FiHardDrive, FiEye, FiDownload, FiSearch } from 'react-icons/fi';
import { toast } from 'react-toastify';
import OverlayPortal from '../components/OverlayPortal';

const Reports = () => {
  // Modal states
  const [showModal, setShowModal] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [currentReportTitle, setCurrentReportTitle] = useState('');
  const [headers, setHeaders] = useState([]);
  const [allRows, setAllRows] = useState([]);
  const [filteredRows, setFilteredRows] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const rowsPerPage = 10;

  const triggerExport = async (endpoint, filename) => {
    toast.info('Generating report... Please wait');
    try {
      const res = await api.get(endpoint, { responseType: 'blob' });
      const url = window.URL.createObjectURL(new Blob([res.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      link.remove();
      toast.success('Report downloaded successfully!');
    } catch (err) {
      console.error('Export error details:', err);
      if (err.response?.data instanceof Blob) {
        const reader = new FileReader();
        reader.onload = () => {
          try {
            const errorObj = JSON.parse(reader.result);
            toast.error(`Export failed: ${errorObj.message || 'Failed to export CSV'}`);
          } catch (e) {
            toast.error('Failed to export report CSV. Verify admin permissions.');
          }
        };
        reader.readAsText(err.response.data);
      } else {
        const errMsg = err.response?.data?.message || err.message || 'Failed to export report CSV.';
        toast.error(`Export failed: ${errMsg}`);
      }
    }
  };

  const parseCSV = (text) => {
    const lines = [];
    let row = [""];
    let inQuotes = false;

    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      const next = text[i + 1];

      if (c === '"') {
        if (inQuotes && next === '"') {
          row[row.length - 1] += '"';
          i++;
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c === ',' && !inQuotes) {
        row.push('');
      } else if ((c === '\r' || c === '\n') && !inQuotes) {
        if (c === '\r' && next === '\n') {
          i++;
        }
        lines.push(row);
        row = [''];
      } else {
        row[row.length - 1] += c;
      }
    }
    if (row.length > 1 || row[0] !== '') {
      lines.push(row);
    }
    return lines;
  };

  const triggerView = async (endpoint, title) => {
    setModalLoading(true);
    setShowModal(true);
    setCurrentReportTitle(title);
    setSearchQuery('');
    setCurrentPage(1);
    setHeaders([]);
    setAllRows([]);
    setFilteredRows([]);

    try {
      const res = await api.get(endpoint, { responseType: 'blob' });
      const text = typeof res.data.text === 'function' 
        ? await res.data.text() 
        : await new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result);
            reader.onerror = () => reject(reader.error);
            reader.readAsText(res.data);
          });
      
      const parsed = parseCSV(text);
      if (parsed.length > 0) {
        const parsedHeaders = parsed[0];
        const parsedRows = parsed.slice(1).filter(row => row.some(cell => cell.trim() !== ''));
        setHeaders(parsedHeaders);
        setAllRows(parsedRows);
        setFilteredRows(parsedRows);
      } else {
        toast.warning('No data found in this report.');
      }
    } catch (err) {
      console.error('Error fetching report:', err);
      toast.error('Failed to retrieve report data.');
      setShowModal(false);
    } finally {
      setModalLoading(false);
    }
  };

  const handleSearchChange = (query) => {
    setSearchQuery(query);
    setCurrentPage(1);
    if (!query.trim()) {
      setFilteredRows(allRows);
    } else {
      const lower = query.toLowerCase();
      const filtered = allRows.filter(row => 
        row.some(cell => String(cell).toLowerCase().includes(lower))
      );
      setFilteredRows(filtered);
    }
  };

  const renderCell = (cellValue) => {
    const val = cellValue.trim();
    if (val === '') return <span className="text-muted">-</span>;
    if (val.toLowerCase() === 'true') {
      return <span className="badge bg-success-subtle text-success border border-success-subtle px-2 py-1 rounded-pill">Yes</span>;
    }
    if (val.toLowerCase() === 'false') {
      return <span className="badge bg-danger-subtle text-danger border border-danger-subtle px-2 py-1 rounded-pill">No</span>;
    }
    if (val === 'ADMIN' || val === 'SUPER_ADMIN') {
      return <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-2 py-1 rounded">{val}</span>;
    }
    if (val === 'EMPLOYEE') {
      return <span className="badge bg-secondary-subtle text-secondary border border-secondary-subtle px-2 py-1 rounded">{val}</span>;
    }
    return <span>{cellValue}</span>;
  };

  const reportsConfig = [
    {
      title: 'User Access Directory',
      desc: 'Export detailed list of all system users, registration dates, roles, and validation statuses.',
      icon: <FiUsers size={28} />,
      color: 'bg-primary text-white',
      endpoint: '/reports/export/users',
      filename: 'users_report.csv'
    },
    {
      title: 'Document Catalog Index',
      desc: 'Export index of all files, including size breakdown, classification categories, folders, and owner emails.',
      icon: <FiFileText size={28} />,
      color: 'bg-success text-white',
      endpoint: '/reports/export/documents',
      filename: 'documents_report.csv'
    },
    {
      title: 'System Activity logs',
      desc: 'Export audit logs history containing device details, browser type, action name, and client IP records.',
      icon: <FiActivity size={28} />,
      color: 'bg-warning text-dark',
      endpoint: '/reports/export/activity',
      filename: 'activity_report.csv'
    },
    {
      title: 'Storage Allocation Analysis',
      desc: 'Export breakdown of storage usage parameters grouped by individual user emails.',
      icon: <FiHardDrive size={28} />,
      color: 'bg-danger text-white',
      endpoint: '/reports/export/storage',
      filename: 'storage_report.csv'
    }
  ];

  // Pagination Math
  const totalPages = Math.ceil(filteredRows.length / rowsPerPage);
  const indexOfLastRow = currentPage * rowsPerPage;
  const indexOfFirstRow = indexOfLastRow - rowsPerPage;
  const currentRows = filteredRows.slice(indexOfFirstRow, indexOfLastRow);

  return (
    <div className="container-fluid px-0">
      <div className="mb-4">
        <h2 className="font-title m-0">Compliance Reports</h2>
        <p className="text-muted small m-0">Generate and export system-wide document tracking CSV sheets for corporate audits</p>
      </div>

      <div className="row g-4">
        {reportsConfig.map((rep) => (
          <div key={rep.title} className="col-12 col-md-6">
            <div className="smart-card d-flex gap-3 h-100 align-items-start p-4">
              <div className={`rounded-3 p-3 d-flex align-items-center justify-content-center ${rep.color}`} style={{ width: '60px', height: '60px' }}>
                {rep.icon}
              </div>
              <div className="flex-grow-1">
                <h5 className="font-title mb-2 fw-semibold">{rep.title}</h5>
                <p className="text-muted small mb-3">{rep.desc}</p>
                <div className="d-flex gap-2">
                  <button 
                    onClick={() => triggerView(rep.endpoint, rep.title)}
                    className="btn btn-sm btn-primary rounded-pill px-3 d-flex align-items-center gap-1 shadow-sm"
                  >
                    <FiEye size={14} /> View
                  </button>
                  <button 
                    onClick={() => triggerExport(rep.endpoint, rep.filename)}
                    className="btn btn-sm btn-outline-secondary rounded-pill px-3 d-flex align-items-center gap-1"
                  >
                    <FiDownload size={14} /> Export CSV
                  </button>
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Report Viewer Modal */}
      <OverlayPortal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        dialogClassName="modal-dialog modal-xl modal-dialog-centered"
        dialogStyle={{ maxWidth: '90%' }}
      >
        <div className="modal-content border-0 shadow-lg" style={{ borderRadius: '16px', height: '80vh', display: 'flex', flexDirection: 'column' }}>
                <div className="modal-header bg-white border-bottom-0 pb-0 pt-4 px-4 d-flex justify-content-between align-items-center">
                  <div>
                    <h4 className="modal-title font-title fw-semibold text-dark mb-1">{currentReportTitle}</h4>
                    <p className="text-muted small mb-0">Real-time view of system audit logs and report records</p>
                  </div>
                  <button 
                    type="button" 
                    className="btn-close bg-light p-2 rounded-circle" 
                    onClick={() => setShowModal(false)} 
                    aria-label="Close"
                    style={{ fontSize: '0.8rem' }}
                  ></button>
                </div>
                
                <div className="modal-body p-4 d-flex flex-column" style={{ minHeight: 0, overflow: 'hidden' }}>
                  {modalLoading ? (
                    <div className="d-flex flex-column align-items-center justify-content-center flex-grow-1 py-5">
                      <div className="spinner-border text-primary mb-3" role="status"></div>
                      <span className="text-muted">Fetching report data...</span>
                    </div>
                  ) : (
                    <>
                      <div className="d-flex justify-content-between align-items-center gap-3 mb-3">
                        <div className="input-group input-group-sm" style={{ maxWidth: '320px' }}>
                          <span className="input-group-text bg-light border-end-0 text-muted"><FiSearch /></span>
                          <input 
                            type="text" 
                            className="form-control bg-light border-start-0 ps-0 text-muted" 
                            placeholder="Search report records..."
                            value={searchQuery}
                            onChange={(e) => handleSearchChange(e.target.value)}
                          />
                        </div>
                        <div className="text-muted small">
                          Total: <strong className="text-dark">{filteredRows.length}</strong> records
                        </div>
                      </div>

                      {filteredRows.length === 0 ? (
                        <div className="d-flex flex-column align-items-center justify-content-center flex-grow-1 py-5 border rounded bg-light-subtle">
                          <span className="text-muted fs-5 mb-2">No matching records found</span>
                          <span className="text-muted small">Try refining your search query</span>
                        </div>
                      ) : (
                        <>
                          <div className="table-responsive border rounded flex-grow-1" style={{ overflowY: 'auto', background: '#fff' }}>
                            <table className="table custom-table table-hover align-middle mb-0" style={{ minWidth: '800px' }}>
                              <thead className="sticky-top bg-light" style={{ zIndex: 1 }}>
                                <tr>
                                  {headers.map((h, i) => (
                                    <th key={i} className="text-nowrap" style={{ top: 0, position: 'sticky', backgroundColor: '#f8f9fa' }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {currentRows.map((row, rIdx) => (
                                  <tr key={rIdx}>
                                    {row.map((cell, cIdx) => (
                                      <td key={cIdx} className="text-nowrap">{renderCell(cell)}</td>
                                    ))}
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </div>

                          <div className="d-flex justify-content-between align-items-center mt-3 pt-3 border-top flex-wrap gap-2">
                            <span className="text-muted small">
                              Showing {indexOfFirstRow + 1} to {Math.min(indexOfLastRow, filteredRows.length)} of {filteredRows.length} records
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
                    </>
                  )}
                </div>
              </div>
      </OverlayPortal>
    </div>
  );
};

export default Reports;
