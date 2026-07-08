// Small inline SVG icon components. All accept a `className` prop.
const stroke = {
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
};

// App logo: the PortaSplit itself - indoor tower with vents, hose, outdoor pod.
export const LogoMark = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}>
    <rect x="3" y="3" width="10" height="18" rx="3" />
    <path d="M6.5 7.5h3M6.5 10.5h3" />
    <path d="M13 13.5h2.5a3.5 3.5 0 0 1 3.5 3.5" />
    <rect x="16" y="17" width="6" height="4" rx="1.6" />
  </svg>
);
export const Snowflake = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}>
    <path d="M2 12h20M12 2v20" />
    <path d="m16 4-4 4-4-4M8 20l4-4 4 4M4 8l4 4-4 4M20 16l-4-4 4-4" />
  </svg>
);
// Half sun / half snowflake - the PortaSplit heats and cools.
export const SunSnow = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}>
    <path d="M10 9a3 3 0 0 0 0 6" />
    <path d="M10 4V3M10 21v-1M2 12h1M4.34 6.34l-.7-.7M3.64 18.36l.7-.7" />
    <path d="m17 3-3 6 1.5 3M14 4l1.25 2.5L18 6M17 21l-3-6 1.5-3H22M14 20l1.25-2.5L18 18M20 10l-1.5 2 1.5 2" />
  </svg>
);
export const Refresh = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M21 12a9 9 0 1 1-3-6.7" /><path d="M21 4v5h-5" /></svg>
);
export const Telegram = (p) => (
  <svg viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M21.9 4.3 18.7 19c-.2 1-.9 1.3-1.8.8l-4.9-3.6-2.4 2.3c-.3.3-.5.5-1 .5l.3-4.9 8.9-8c.4-.3-.1-.5-.6-.2L6.3 13 1.6 11.5c-1-.3-1-1 .2-1.5L20.6 2.8c.8-.3 1.6.2 1.3 1.5Z" /></svg>
);
export const Plus = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M12 5v14M5 12h14" /></svg>
);
export const Clock = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
);
export const Search = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="11" cy="11" r="7" /><path d="m21 21-4.3-4.3" /></svg>
);
export const ExternalLink = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" /></svg>
);
export const Edit = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M12 20h9" /><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" /></svg>
);
export const Trash = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m2 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" /></svg>
);
export const Activity = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M3 12h4l3 8 4-16 3 8h4" /></svg>
);
export const Store = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M3 9 4 4h16l1 5M4 9v11h16V9M9 20v-6h6v6" /></svg>
);
export const Branch = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M3 21h18M5 21V7l7-4 7 4v14M9 9h.01M9 13h.01M9 17h.01M15 9h.01M15 13h.01M15 17h.01" /></svg>
);
export const Globe = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3a15 15 0 0 1 0 18 15 15 0 0 1 0-18" /></svg>
);
export const MapPin = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z" /><circle cx="12" cy="10" r="3" /></svg>
);
export const Eye = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></svg>
);
export const Check = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="m5 13 4 4L19 7" /></svg>
);
export const Info = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="12" cy="12" r="9" /><path d="M12 16v-4M12 8h.01" /></svg>
);
export const Alert = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" /></svg>
);
export const Settings = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z" /></svg>
);
export const Shield = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" /></svg>
);
export const Pause = (p) => (
  <svg viewBox="0 0 24 24" fill="currentColor" {...p}><rect x="6" y="5" width="4" height="14" rx="1" /><rect x="14" y="5" width="4" height="14" rx="1" /></svg>
);
export const Terminal = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="m4 17 6-6-6-6M12 19h8" /></svg>
);
export const Layers = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="m12 2 9 5-9 5-9-5 9-5ZM3 12l9 5 9-5M3 17l9 5 9-5" /></svg>
);
export const Play = (p) => (
  <svg viewBox="0 0 24 24" fill="currentColor" {...p}><path d="M8 5.5v13a1 1 0 0 0 1.5.86l11-6.5a1 1 0 0 0 0-1.72l-11-6.5A1 1 0 0 0 8 5.5Z" /></svg>
);
export const ChevronDown = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><path d="m6 9 6 6 6-6" /></svg>
);
export const Copy = (p) => (
  <svg viewBox="0 0 24 24" {...stroke} {...p}><rect x="9" y="9" width="12" height="12" rx="2" /><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" /></svg>
);
