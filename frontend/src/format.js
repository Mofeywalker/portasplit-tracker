const eur = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
const dtf = new Intl.DateTimeFormat('de-DE', {
  day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
});
const tf = new Intl.DateTimeFormat('de-DE', {
  hour: '2-digit', minute: '2-digit', second: '2-digit',
});

// Wall-clock time with seconds, for the logbook (e.g. "14:23:07").
export function clockTime(iso) {
  return iso ? tf.format(new Date(iso)) : '-';
}

// Human duration for a job run: "450 ms", "4,2 s" or "1:05 min".
export function duration(ms) {
  if (ms == null) return '-';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(s < 10 ? 1 : 0).replace('.', ',')} s`;
  const m = Math.floor(s / 60);
  const r = Math.round(s % 60);
  return `${m}:${String(r).padStart(2, '0')} min`;
}

export function money(v) {
  return v == null ? '-' : eur.format(Number(v));
}

export function dateTime(iso) {
  return iso ? dtf.format(new Date(iso)) : '-';
}

export function relative(iso) {
  if (!iso) return '-';
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 45) return 'gerade eben';
  const rtf = new Intl.RelativeTimeFormat('de', { numeric: 'auto' });
  const mins = Math.round(diff / 60);
  if (Math.abs(mins) < 60) return rtf.format(-mins, 'minute');
  const hrs = Math.round(diff / 3600);
  if (Math.abs(hrs) < 24) return rtf.format(-hrs, 'hour');
  return rtf.format(-Math.round(diff / 86400), 'day');
}

export function productLabel(p) {
  return p === 'PORTASPLIT_COOL' ? 'PortaSplit Cool' : 'PortaSplit';
}

export function eventLabel(e) {
  switch (e.eventType) {
    case 'AVAILABLE': return `Verfügbar (${e.stock ?? '?'})`;
    case 'UNAVAILABLE': return 'Ausverkauft';
    case 'STOCK_CHANGE': return `Bestand: ${e.stock ?? '?'}`;
    case 'INITIAL': return e.available ? `Verfügbar (${e.stock ?? '?'})` : 'Erfasst';
    default: return e.eventType;
  }
}

export function eventTextCls(e) {
  if (e.available) return 'text-emerald-600';
  return e.eventType === 'UNAVAILABLE' ? 'text-rose-600' : 'text-slate-500';
}

export function eventDot(e) {
  if (e.available) return 'bg-emerald-500';
  return e.eventType === 'UNAVAILABLE' ? 'bg-rose-500' : 'bg-slate-400';
}

export function shopUpdated(shop) {
  const times = shop.products
    .map((p) => p.lastCheckedAt)
    .filter(Boolean)
    .map((t) => new Date(t).getTime());
  if (times.length === 0) return '-';
  return relative(new Date(Math.max(...times)).toISOString());
}
