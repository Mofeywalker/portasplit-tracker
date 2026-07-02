import { useMemo, useState } from 'react';
import { jobMeta, levelCls } from '../jobs.jsx';
import { clockTime } from '../format.js';
import { Terminal } from '../icons.jsx';

// Live technical logbook: the merged, newest-first stream of every job's log lines, with per-source
// filtering and an optional "hide debug" toggle. Updates as App re-polls /api/jobs/log.
export default function Logbook({ lines, onRefresh }) {
  const [typeFilter, setTypeFilter] = useState('');
  const [showDebug, setShowDebug] = useState(true);

  const presentTypes = useMemo(() => {
    const seen = [];
    for (const l of lines || []) {
      if (!seen.includes(l.type)) seen.push(l.type);
    }
    return seen;
  }, [lines]);

  const filtered = useMemo(() => (lines || []).filter((l) =>
    (!typeFilter || l.type === typeFilter) && (showDebug || l.level !== 'DEBUG')
  ), [lines, typeFilter, showDebug]);

  const chip = (active) => active
    ? 'bg-brand-500 text-white ring-brand-500 shadow-sm shadow-brand-500/25'
    : 'bg-white text-slate-600 ring-slate-200 hover:bg-slate-50 hover:text-slate-900';

  return (
    <section className="card rounded-2xl ring-1 ring-slate-200 p-4">
      <div className="flex items-center justify-between mb-3 gap-3 flex-wrap">
        <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
          <Terminal className="h-4 w-4 text-brand-500" /> Logbuch
          <span className="text-xs font-medium text-slate-400">{filtered.length} Einträge</span>
        </h2>
        <div className="flex items-center gap-1.5 flex-wrap">
          <button onClick={() => setTypeFilter('')} className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 transition ${chip(typeFilter === '')}`}>Alle</button>
          {presentTypes.map((t) => (
            <button key={t} onClick={() => setTypeFilter(typeFilter === t ? '' : t)}
              className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ring-1 transition ${chip(typeFilter === t)}`}>
              {jobMeta(t).label}
            </button>
          ))}
          <label className="ml-1 inline-flex items-center gap-1.5 text-[11px] text-slate-500 cursor-pointer select-none">
            <input type="checkbox" checked={showDebug} onChange={(e) => setShowDebug(e.target.checked)} className="peer sr-only" />
            <span className="relative h-4 w-7 rounded-full bg-slate-200 transition peer-checked:bg-brand-500 after:absolute after:top-0.5 after:left-0.5 after:h-3 after:w-3 after:rounded-full after:bg-white after:shadow after:transition peer-checked:after:translate-x-3" />
            Debug
          </label>
          {onRefresh && (
            <button onClick={onRefresh} className="text-[11px] text-slate-500 hover:text-brand-600 transition ml-1">Aktualisieren</button>
          )}
        </div>
      </div>

      <ol className="space-y-0.5 max-h-[28rem] overflow-y-auto pr-1 font-mono text-[12px] leading-relaxed">
        {filtered.map((l) => {
          const m = jobMeta(l.type);
          return (
            <li key={`${l.jobId}-${l.at}-${l.message}`} className="flex items-start gap-2 rounded px-1.5 py-0.5 hover:bg-slate-50">
              <span className="text-slate-400 tabular-nums shrink-0">{clockTime(l.at)}</span>
              <span className={`inline-flex items-center gap-1 shrink-0 ${m.text}`} title={`Job #${l.jobId}`}>
                <span className={`h-1.5 w-1.5 rounded-full ${m.dot}`} />
                <span className="hidden sm:inline">{m.label}</span>
              </span>
              <span className={`break-words ${levelCls(l.level)}`}>
                {l.level === 'WARN' || l.level === 'ERROR' ? `${l.level}: ` : ''}{l.message}
              </span>
            </li>
          );
        })}
        {filtered.length === 0 && (
          <li className="text-center text-slate-400 text-sm py-6 font-sans">Noch keine Log-Einträge.</li>
        )}
      </ol>
    </section>
  );
}
