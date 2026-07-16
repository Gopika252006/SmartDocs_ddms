import React from 'react';
import OverlayPortal from './OverlayPortal';

const DocumentActionsDropdown = ({ isOpen, onClose, triggerEl, actions }) => {
  return (
    <OverlayPortal
      isOpen={isOpen}
      onClose={onClose}
      type="dropdown"
      triggerEl={triggerEl}
      dropdownWidth={200}
    >
      <div
        className="dropdown-menu shadow border-0 show"
        style={{
          fontSize: '0.85rem',
          display: 'block',
          minWidth: '200px',
          margin: 0,
          backgroundColor: '#fff',
          borderRadius: '8px',
          boxShadow: '0 10px 30px rgba(0, 0, 0, 0.15)',
          padding: '0.5rem 0'
        }}
      >
        {actions.map((act, index) => {
          if (act.type === 'divider') {
            return <hr key={`div-${index}`} className="dropdown-divider" />;
          }
          return (
            <li key={`act-${index}`}>
              <button
                type="button"
                className={`dropdown-item d-flex align-items-center gap-2 py-1.5 ${act.className || ''}`}
                onClick={(e) => {
                  e.stopPropagation();
                  act.onClick();
                  onClose();
                }}
              >
                {act.icon}
                <span>{act.label}</span>
              </button>
            </li>
          );
        })}
      </div>
    </OverlayPortal>
  );
};

export default DocumentActionsDropdown;
