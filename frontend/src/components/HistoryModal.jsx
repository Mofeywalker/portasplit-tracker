import { useEffect, useMemo, useRef } from 'react';
import ApexCharts from 'apexcharts';
import { dateTime, money, eventLabel, eventTextCls } from '../format.js';
import { BrandLogo } from '../logos.jsx';

const TABS = [
  { key: 'PORTASPLIT', label: 'PortaSplit' },
  { key: 'PORTASPLIT_COOL', label: 'PortaSplit Cool' },
];

// Chart value for one event: the real stock count when known; otherwise 1 if it was available (many
// sources only report available/not without a count) and 0 if it was not available.
function chartStock(e) {
  if (e.stock != null) return e.stock;
  return e.available ? 1 : 0;
}

function buildOptions(events) {
  const data = events.map((e) => [new Date(e.createdAt).getTime(), chartStock(e)]);
  // extend the last value to "now" so the step line reaches the present
  const last = events[events.length - 1];
  data.push([Date.now(), chartStock(last)]);
  return {
    chart: { type: 'area', height: 260, background: 'transparent', toolbar: { show: false }, fontFamily: 'Inter', animations: { enabled: true } },
    theme: { mode: 'light' },
    colors: ['#10b981'],
    stroke: { curve: 'stepline', width: 2.5 },
    fill: { type: 'gradient', gradient: { shadeIntensity: 1, opacityFrom: 0.30, opacityTo: 0.02 } },
    dataLabels: { enabled: false },
    grid: { borderColor: '#e2e8f0', strokeDashArray: 4 },
    xaxis: { type: 'datetime', labels: { style: { colors: '#64748b' }, datetimeUTC: false } },
    yaxis: { min: 0, forceNiceScale: true, labels: { style: { colors: '#64748b' }, formatter: (v) => Math.round(v) }, title: { text: 'Bestand', style: { color: '#94a3b8', fontWeight: 600 } } },
    tooltip: { theme: 'light', x: { format: 'dd.MM.yyyy HH:mm' } },
    series: [{ name: 'Bestand', data }],
  };
}

export default function HistoryModal({ shop, product, events, loading, onClose, onSwitchProduct }) {
  const chartEl = useRef(null);
  const chartRef = useRef(null);
  const eventsDesc = useMemo(() => [...events].reverse(), [events]);

  useEffect(() => {
    if (chartRef.current) { chartRef.current.destroy(); chartRef.current = null; }
    if (chartEl.current && events.length > 0) {
      chartRef.current = new ApexCharts(chartEl.current, buildOptions(events));
      chartRef.current.render();
    }
    return () => {
      if (chartRef.current) { chartRef.current.destroy(); chartRef.current = null; }
    };
  }, [events]);

  return (
    <div className="fixed inset-0 z-50 grid place-items-center p-4">
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-3xl card rounded-2xl ring-1 ring-slate-200 shadow-2xl animate-fade-in">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <div className="flex items-center gap-3">
            <BrandLogo name={shop?.chain || shop?.name} label={shop?.chain} className="h-9 w-9" fallback={null} />
            <div>
              <h3 className="font-extrabold text-slate-900">{shop?.name}</h3>
              <p className="text-xs text-slate-500">Verlauf</p>
            </div>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-900">✕</button>
        </div>
        <div className="px-5 py-4">
          <div className="inline-flex rounded-lg bg-slate-100 ring-1 ring-slate-200 p-1 mb-4">
            {TABS.map((t) => (
              <button key={t.key} onClick={() => onSwitchProduct(t.key)}
                className={`rounded-md px-3 py-1.5 text-xs font-bold transition ${product === t.key ? 'bg-brand-500 text-white shadow-sm' : 'text-slate-500 hover:text-slate-900'}`}>
                {t.label}
              </button>
            ))}
          </div>
          <div className="rounded-xl bg-slate-50 ring-1 ring-slate-200 p-2">
            <div ref={chartEl} className="min-h-[260px]" />
            {!loading && events.length === 0 && (
              <p className="text-center text-slate-400 text-sm py-16">Noch kein Verlauf vorhanden.</p>
            )}
          </div>
          <div className="mt-4 max-h-52 overflow-y-auto">
            <table className="w-full text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-slate-500">
                <tr><th className="py-1.5 pr-3">Zeitpunkt</th><th className="py-1.5 pr-3">Ereignis</th><th className="py-1.5 pr-3">Bestand</th><th className="py-1.5">Preis</th></tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {eventsDesc.map((e) => (
                  <tr key={e.id}>
                    <td className="py-1.5 pr-3 text-slate-400 tabular-nums whitespace-nowrap">{dateTime(e.createdAt)}</td>
                    <td className="py-1.5 pr-3"><span className={`font-bold ${eventTextCls(e)}`}>{eventLabel(e)}</span></td>
                    <td className="py-1.5 pr-3 text-slate-600 tabular-nums">{e.stock ?? '-'}</td>
                    <td className="py-1.5 text-slate-600 tabular-nums">{e.price != null ? money(e.price) : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
