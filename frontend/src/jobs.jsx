// Shared presentation metadata for the check sources ("jobs"): per-source colour/icon and the
// state-badge styling used by both the JobsPanel cards and the Logbook.
//
// Tailwind v4 scans source files for literal class names, so every colour class below must appear
// as a complete literal string here (no runtime string building) to be included in the build.
import { Globe, Store, Search } from './icons.jsx';

export const JOB_META = {
  AMAZON: { label: 'Amazon', Icon: Globe, text: 'text-amber-600', bg: 'bg-amber-50', ring: 'ring-amber-300', badge: 'bg-amber-50 text-amber-700 ring-amber-200', dot: 'bg-amber-500' },
  LIDL: { label: 'Lidl', Icon: Store, text: 'text-blue-600', bg: 'bg-blue-50', ring: 'ring-blue-300', badge: 'bg-blue-50 text-blue-700 ring-blue-200', dot: 'bg-blue-500' },
  KLEINANZEIGEN: { label: 'Kleinanzeigen', Icon: Search, text: 'text-green-600', bg: 'bg-green-50', ring: 'ring-green-300', badge: 'bg-green-50 text-green-700 ring-green-200', dot: 'bg-green-500' },
  OBI: { label: 'OBI', Icon: Store, text: 'text-orange-600', bg: 'bg-orange-50', ring: 'ring-orange-300', badge: 'bg-orange-50 text-orange-700 ring-orange-200', dot: 'bg-orange-500' },
  TOOM: { label: 'toom', Icon: Store, text: 'text-red-600', bg: 'bg-red-50', ring: 'ring-red-300', badge: 'bg-red-50 text-red-700 ring-red-200', dot: 'bg-red-500' },
  GLOBUS: { label: 'Globus', Icon: Store, text: 'text-yellow-600', bg: 'bg-yellow-50', ring: 'ring-yellow-300', badge: 'bg-yellow-50 text-yellow-700 ring-yellow-200', dot: 'bg-yellow-500' },
  HAGEBAU: { label: 'Hagebau', Icon: Store, text: 'text-emerald-600', bg: 'bg-emerald-50', ring: 'ring-emerald-300', badge: 'bg-emerald-50 text-emerald-700 ring-emerald-200', dot: 'bg-emerald-500' },
  HORNBACH: { label: 'Hornbach', Icon: Store, text: 'text-teal-600', bg: 'bg-teal-50', ring: 'ring-teal-300', badge: 'bg-teal-50 text-teal-700 ring-teal-200', dot: 'bg-teal-500' },
  BAUHAUS: { label: 'Bauhaus', Icon: Store, text: 'text-rose-600', bg: 'bg-rose-50', ring: 'ring-rose-300', badge: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' },
};

export const jobMeta = (type) => JOB_META[type] || {
  label: type, Icon: Search, text: 'text-slate-500', bg: 'bg-slate-100',
  ring: 'ring-slate-300', badge: 'bg-slate-100 text-slate-600 ring-slate-200', dot: 'bg-slate-400',
};

// state → badge. `pulse` marks the live states (running / queued) that animate.
export const STATE_BADGE = {
  RUNNING: { label: 'Läuft…', cls: 'bg-brand-50 text-brand-700 ring-brand-200', dot: 'bg-brand-500', pulse: true },
  QUEUED: { label: 'In Warteschlange', cls: 'bg-slate-100 text-slate-600 ring-slate-200', dot: 'bg-slate-400', pulse: true },
  SUCCESS: { label: 'OK', cls: 'bg-emerald-50 text-emerald-700 ring-emerald-200', dot: 'bg-emerald-500' },
  WARN: { label: 'Warnung', cls: 'bg-amber-50 text-amber-700 ring-amber-200', dot: 'bg-amber-500' },
  FAILED: { label: 'Fehler', cls: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' },
  SKIPPED: { label: 'Übersprungen', cls: 'bg-slate-100 text-slate-500 ring-slate-200', dot: 'bg-slate-400' },
  IDLE: { label: 'Bereit', cls: 'bg-slate-100 text-slate-500 ring-slate-200', dot: 'bg-slate-400' },
  DISABLED: { label: 'Deaktiviert', cls: 'bg-slate-100 text-slate-400 ring-slate-200', dot: 'bg-slate-300' },
};

export const stateBadge = (state) => STATE_BADGE[state] || STATE_BADGE.IDLE;

export const levelCls = (level) => ({
  DEBUG: 'text-slate-400',
  INFO: 'text-slate-600',
  WARN: 'text-amber-600',
  ERROR: 'text-rose-600',
}[level] || 'text-slate-600');
