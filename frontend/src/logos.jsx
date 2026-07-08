// Brand badges for the tracked stores. We intentionally don't bundle third-party store logos in
// this repo (trademark/copyright - see README), so each chain gets a generic initials badge in a
// distinguishing color instead. `badgeFor` accepts either a shop `chain` ("Globus Baumarkt", "OBI")
// or a job `type` ("GLOBUS", "OBI") - it matches loosely so shop names like "OBI Online" resolve too.
const BADGES = {
  BAUHAUS: { text: 'BH', bg: '#c8102e' },
  HORNBACH: { text: 'HB', bg: '#ec6602' },
  TOOM: { text: 'TO', bg: '#00843d' },
  OBI: { text: 'OB', bg: '#eb690b' },
  HAGEBAU: { text: 'HG', bg: '#e2001a' },
  GLOBUS: { text: 'GL', bg: '#004f9f' },
  AMAZON: { text: 'AZ', bg: '#131921' },
  LIDL: { text: 'LI', bg: '#0050aa' },
  KLEINANZEIGEN: { text: 'KA', bg: '#4b5563' },
};

const ORDER = ['BAUHAUS', 'HORNBACH', 'TOOM', 'OBI', 'HAGEBAU', 'GLOBUS', 'AMAZON', 'LIDL', 'KLEINANZEIGEN'];

export function badgeFor(key) {
  if (!key) return null;
  const k = String(key).toUpperCase();
  for (const id of ORDER) if (k.includes(id)) return BADGES[id];
  return null;
}

// A small, friendly "app-icon" tile with a generic initials badge for a store. When no chain
// matches it renders `fallback` instead, so callers can pass their glyph.
export function BrandLogo({ name, label, className = 'h-10 w-10', pad = 'p-1.5', fallback = null }) {
  const badge = badgeFor(name);
  if (!badge) return fallback;
  return (
    <span title={label || name || ''}
      className={`grid place-items-center shrink-0 overflow-hidden rounded-xl ring-1 ring-slate-200 shadow-sm font-bold text-white text-[10px] tracking-tight leading-none ${pad} ${className}`}
      style={{ backgroundColor: badge.bg }}>
      {badge.text}
    </span>
  );
}
