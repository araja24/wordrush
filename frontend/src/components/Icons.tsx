// Icons from the design, as inline SVG. Decorative: the button around each carries the label.
const stroke = { fill: 'none', stroke: 'currentColor', strokeLinecap: 'round', strokeLinejoin: 'round' } as const;

export function HelpIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" strokeWidth="2.2" {...stroke} aria-hidden="true">
      <circle cx="12" cy="12" r="9.5" />
      <path d="M9.5 9.5a2.5 2.5 0 0 1 4.9.7c0 1.7-2.4 2.3-2.4 3.8" />
      <path d="M12 17.3v.01" />
    </svg>
  );
}

export function MenuIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" strokeWidth="2.4" {...stroke} aria-hidden="true">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

export function CloseIcon({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" strokeWidth="2.4" {...stroke} aria-hidden="true">
      <path d="M6 6l12 12M18 6L6 18" />
    </svg>
  );
}

export function LinkIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" strokeWidth="2.2" {...stroke} aria-hidden="true">
      <path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.7 1.7" />
      <path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.7-1.7" />
    </svg>
  );
}

export function ShareIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" strokeWidth="2.2" {...stroke} aria-hidden="true">
      <path d="M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7" />
      <path d="M16 6l-4-4-4 4M12 2v13" />
    </svg>
  );
}

export function CrownIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M3 8l4.5 4L12 5l4.5 7L21 8l-2 11H5z" />
    </svg>
  );
}

export function BackspaceIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" strokeWidth="2.2" {...stroke} aria-hidden="true">
      <path d="M21 5H8l-6 7 6 7h13z" />
      <path d="M12 9l6 6M18 9l-6 6" />
    </svg>
  );
}

export function SpinnerIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" strokeWidth="2.6" {...stroke} aria-hidden="true">
      <path d="M21 12a9 9 0 1 1-6.2-8.56" />
    </svg>
  );
}

export function ChatIcon({ size = 22 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" strokeWidth="2.2" {...stroke} aria-hidden="true">
      <path d="M4 5h16v11H10l-6 4z" />
    </svg>
  );
}

export function ChevronDownIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" strokeWidth="2.4" {...stroke} aria-hidden="true">
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

export function SendIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" strokeWidth="2.4" {...stroke} aria-hidden="true">
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}
