import { useState } from 'react';
import { jobMeta, stateBadge } from '../jobs.jsx';
import { relative, duration } from '../format.js';
import { Layers, Clock, Alert, Refresh, ChevronDown, Copy, Check } from '../icons.jsx';
import { BrandLogo } from '../logos.jsx';

// Sources hidden from the "Prüfungen" panel in the frontend (still checked on the backend).
const HIDDEN_SOURCES = new Set(['HAGEBAU']);

// Counts down to the next scheduled run, anchored to when the /api/jobs payload was fetched so the
// label ticks every second (App provides `now` and `jobsAt`).
function nextText(ms, now, jobsAt) {
  if (ms == null) return null;
  const rem = ms - (now - jobsAt);
  if (rem <= 0) return 'gleich';
  const t = Math.floor(rem / 1000);
  return `${Math.floor(t / 60)}:${String(t % 60).padStart(2, '0')}`;
}

// Bounded, scrollable box for (often huge, e.g. raw HTML error page dumps) job error text, so a
// single failing source can't blow up the card's height. Offers a copy button since the text is
// usually too long to read in place but useful to paste elsewhere for debugging.
function ErrorBox({ text }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // clipboard API unavailable/denied - nothing sensible to do, leave the button as-is.
    }
  };

  return (
    <div className="relative rounded-lg ring-1 ring-rose-100 bg-rose-50/60">
      <div className="flex items-start gap-1.5 px-2 py-1.5 pr-7 max-h-24 overflow-y-auto">
        <Alert className="h-3.5 w-3.5 shrink-0 mt-px text-rose-600" />
        <span className="break-words whitespace-pre-wrap text-[11px] text-rose-600 leading-snug">{text}</span>
      </div>
      <button onClick={copy} title="In Zwischenablage kopieren"
        className="absolute top-1 right-1 inline-flex items-center justify-center h-5 w-5 rounded-md text-rose-400 hover:text-rose-700 hover:bg-rose-100 transition">
        {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
      </button>
    </div>
  );
}

function JobCard({ s, now, jobsAt, onToggle, onTrigger, busy }) {
  const m = jobMeta(s.type);
  const badge = stateBadge(s.state);
  const last = s.lastRun;
  const Icon = m.Icon;
  const live = s.running || s.queued;
  const next = (!live && s.enabled) ? nextText(s.nextRunInMs, now, jobsAt) : null;
  const showError = last && last.error && (last.state === 'FAILED' || last.state === 'WARN');

  return (
    <div className={`card rounded-2xl ring-1 p-4 animate-fade-in ${live ? m.ring : 'ring-slate-200'}`}>
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2.5 min-w-0">
          <BrandLogo name={s.type} label={m.label} className="h-9 w-9"
            fallback={<span className={`h-9 w-9 grid place-items-center rounded-xl shrink-0 ${m.bg}`}><Icon className={`h-4 w-4 ${m.text}`} /></span>} />
          <div className="min-w-0">
            <div className="text-sm font-extrabold text-slate-900 leading-tight truncate">{m.label}</div>
            <div className="text-[11px] text-slate-400 truncate">{s.subtitle}</div>
          </div>
        </div>
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold ring-1 shrink-0 ${badge.cls}`}>
          <span className={`h-2 w-2 rounded-full ${badge.dot} ${badge.pulse ? 'animate-pulse' : ''}`} />
          {badge.label}
        </span>
      </div>

      <div className="mt-3 space-y-1.5">
        {!s.enabled ? (
          <p className="text-xs text-slate-400">Quelle ist deaktiviert.</p>
        ) : last ? (
          <>
            <p className="text-xs text-slate-600 leading-snug">{last.summary || '-'}</p>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-slate-400">
              <span>{relative(last.finishedAt || last.startedAt)}</span>
              <span className="tabular-nums">⏱ {duration(last.durationMs)}</span>
              <span>{last.triggerLabel}</span>
              {last.logCount > 0 && <span>{last.logCount} Log-Einträge</span>}
            </div>
            {showError && <ErrorBox text={last.error} />}
          </>
        ) : (
          <p className="text-xs text-slate-400">{live ? 'Läuft…' : 'Noch nicht geprüft.'}</p>
        )}

        <div className="min-h-[16px]">
          {s.queued ? (
            <span className="inline-flex items-center gap-1.5 text-[11px] text-slate-500">
              <Clock className="h-3 w-3" /> wartet auf den Worker…
            </span>
          ) : next ? (
            <span className="inline-flex items-center gap-1.5 text-[11px] text-slate-500">
              <Clock className="h-3 w-3" /> nächste Prüfung in <b className="text-slate-700 tabular-nums">{next}</b>
            </span>
          ) : null}
        </div>

        <div className="pt-2 mt-1 border-t border-slate-100 flex items-center justify-between gap-2">
          {/* Per-source manual trigger: enqueues just this source on its own worker. */}
          <button onClick={() => onTrigger?.(s.type)} disabled={live || !s.enabled}
            title={s.enabled ? 'Diese Quelle jetzt prüfen' : 'Quelle ist deaktiviert'}
            className="inline-flex items-center gap-1.5 rounded-lg px-2 py-1 text-[11px] font-bold ring-1 ring-slate-200 text-slate-600 hover:bg-slate-50 hover:text-brand-600 disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-slate-600 transition">
            <Refresh className={`h-3 w-3 ${live ? 'animate-spin' : ''}`} />
            {s.running ? 'läuft…' : s.queued ? 'wartet…' : 'Prüfen'}
          </button>

          {/* Per-source on/off switch. Disabled while its own toggle request is in flight. */}
          <label className={`inline-flex items-center gap-2 text-[11px] select-none shrink-0 ${busy ? 'opacity-50 cursor-wait' : 'cursor-pointer'}`}
            title={s.enabled ? 'Quelle deaktivieren' : 'Quelle aktivieren'}>
            <span className={s.enabled ? 'text-emerald-600 font-bold' : 'text-slate-400'}>{s.enabled ? 'Aktiv' : 'Aus'}</span>
            <input type="checkbox" checked={s.enabled} disabled={busy} onChange={(e) => onToggle?.(s.type, e.target.checked)} className="peer sr-only" />
            <span className="relative h-5 w-9 rounded-full bg-slate-200 transition peer-checked:bg-emerald-500 after:absolute after:top-0.5 after:left-0.5 after:h-4 after:w-4 after:rounded-full after:bg-white after:shadow after:transition peer-checked:after:translate-x-4" />
          </label>
        </div>
      </div>
    </div>
  );
}

export default function JobsPanel({ jobs, now, jobsAt, onToggle, onTrigger, pending }) {
  // Collapsed by default: the checks live behind an accordion so they don't dominate the dashboard.
  const [open, setOpen] = useState(false);
  const sources = (jobs?.sources || []).filter((s) => !HIDDEN_SOURCES.has(s.type));
  const queueLen = jobs?.queued?.length || 0;
  const running = jobs?.running || [];
  const activeCount = sources.filter((s) => s.enabled).length;

  return (
    <section>
      <button type="button" onClick={() => setOpen((o) => !o)} aria-expanded={open}
        className="w-full flex items-center justify-between gap-3 rounded-xl px-1 py-1.5 text-left hover:bg-slate-50/60 transition">
        <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2 min-w-0">
          <Layers className="h-4 w-4 text-brand-500 shrink-0" /> Prüfungen
          <span className="text-xs font-medium text-slate-400 truncate">
            {running.length > 0 ? `läuft: ${running.map((r) => jobMeta(r.type).label).join(', ')}` : `${activeCount} aktiv`}
            {queueLen > 0 ? ` · ${queueLen} in Warteschlange` : ''}
          </span>
        </h2>
        <ChevronDown className={`h-4 w-4 text-slate-400 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-3 animate-fade-in">
          {sources.map((s) => <JobCard key={s.type} s={s} now={now} jobsAt={jobsAt} onToggle={onToggle} onTrigger={onTrigger} busy={pending?.[s.type] !== undefined} />)}
          {sources.length === 0 && (
            <div className="col-span-full text-center text-slate-400 text-sm py-6">Lade Prüfstatus…</div>
          )}
        </div>
      )}
    </section>
  );
}
