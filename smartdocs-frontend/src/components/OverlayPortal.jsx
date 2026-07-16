import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';

const OverlayPortal = ({
  isOpen,
  onClose,
  children,
  type = 'modal', // 'modal' | 'drawer' | 'dropdown'
  triggerEl = null, // required for dropdown positioning
  lockScroll = null, // defaults to true for modal/drawer, false for dropdown
  closeOnEsc = true,
  closeOnOutsideClick = true,
  dialogClassName = 'modal-dialog modal-dialog-centered',
  dialogStyle = {},
  dropdownWidth = 220,
  align = 'right' // 'left' | 'right' | 'stretch'
}) => {
  const overlayRef = useRef(null);
  const [dropdownCoords, setDropdownCoords] = useState({ top: 0, left: 0, width: dropdownWidth });

  const shouldLockScroll = lockScroll !== null ? lockScroll : (type !== 'dropdown');

  // Lock background body scrolling when modal or drawer is open
  useEffect(() => {
    if (!isOpen || !shouldLockScroll) return;

    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen, shouldLockScroll]);

  // Position calculation for dropdowns with viewport boundary checks
  useEffect(() => {
    if (!isOpen || type !== 'dropdown' || !triggerEl) return;

    const updatePosition = () => {
      if (!overlayRef.current) return;
      const rect = triggerEl.getBoundingClientRect();
      let width = align === 'stretch' ? rect.width : (overlayRef.current.offsetWidth || dropdownWidth);
      const height = overlayRef.current.offsetHeight || 200;

      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;

      let top = 0;
      let left = 0;

      if (align === 'left' || align === 'stretch') {
        left = rect.left;
      } else {
        left = rect.right - width;
      }

      // Prevent menu going off screen to the right
      if (left + width > window.innerWidth - 10) {
        left = window.innerWidth - width - 10;
      }

      // Boundary check to prevent menu going off screen to the left
      if (left < 10) {
        left = 10;
      }

      // Automatically open above if space below is tight and above is spacious
      if (spaceBelow < height && spaceAbove > height) {
        top = rect.top + window.scrollY - height - 4;
      } else {
        top = rect.bottom + window.scrollY + 4;
      }

      setDropdownCoords({ top, left, width });
    };

    const timer = setTimeout(updatePosition, 0);

    window.addEventListener('scroll', updatePosition, true);
    window.addEventListener('resize', updatePosition);

    return () => {
      clearTimeout(timer);
      window.removeEventListener('scroll', updatePosition, true);
      window.removeEventListener('resize', updatePosition);
    };
  }, [isOpen, type, triggerEl, dropdownWidth, align]);

  // Event handlers to capture clicks outside or key presses
  useEffect(() => {
    if (!isOpen) return;

    const handleOutsideClick = (e) => {
      if (!closeOnOutsideClick) return;

      // Click inside dropdown/overlay menu itself -> ignore
      if (overlayRef.current && overlayRef.current.contains(e.target)) {
        return;
      }
      // Click on trigger element -> ignore
      if (triggerEl && triggerEl.contains(e.target)) {
        return;
      }
      onClose();
    };

    const handleKeyDown = (e) => {
      if (closeOnEsc && e.key === 'Escape') {
        onClose();
      }
    };

    document.addEventListener('click', handleOutsideClick, true);
    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('click', handleOutsideClick, true);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen, closeOnOutsideClick, closeOnEsc, triggerEl, onClose]);

  if (!isOpen) return null;

  // Render Portal for dropdowns
  if (type === 'dropdown') {
    return ReactDOM.createPortal(
      <div
        ref={overlayRef}
        className="animate-fade-in"
        style={{
          position: 'absolute',
          top: `${dropdownCoords.top}px`,
          left: `${dropdownCoords.left}px`,
          width: align === 'stretch' ? `${dropdownCoords.width}px` : 'auto',
          zIndex: 9999,
          pointerEvents: 'auto'
        }}
      >
        {children}
      </div>,
      document.body
    );
  }

  // Render Portal for side drawers (e.g. AI Insights tab)
  if (type === 'drawer') {
    return ReactDOM.createPortal(
      <div
        className="animate-fade-in"
        style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100vw',
          height: '100vh',
          backgroundColor: 'rgba(0,0,0,0.15)', // light translucent backdrop to close on outside click
          zIndex: 1080,
          display: 'flex',
          justifyContent: 'flex-end',
          pointerEvents: 'auto'
        }}
        onClick={(e) => {
          if (closeOnOutsideClick && e.target === e.currentTarget) {
            onClose();
          }
        }}
      >
        <div
          ref={overlayRef}
          onClick={(e) => e.stopPropagation()}
          style={{ height: '100%', pointerEvents: 'auto' }}
        >
          {children}
        </div>
      </div>,
      document.body
    );
  }

  // Render Portal for regular modals
  return ReactDOM.createPortal(
    <div
      className="modal show d-block animate-fade-in"
      style={{
        backgroundColor: 'rgba(0,0,0,0.5)', // standard modal backdrop shadow overlay
        zIndex: 1090,
        position: 'fixed',
        top: 0,
        left: 0,
        width: '100vw',
        height: '100vh',
        overflowY: 'auto'
      }}
      onClick={(e) => {
        if (closeOnOutsideClick && e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={overlayRef}
        className={dialogClassName}
        style={dialogStyle}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>,
    document.body
  );
};

export default OverlayPortal;
