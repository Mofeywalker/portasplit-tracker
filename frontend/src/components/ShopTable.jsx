import { ExternalLink, Alert } from '../icons.jsx';
import { money, shopUpdated } from '../format.js';
import { BrandLogo } from '../logos.jsx';

// Names of the products whose last check errored, for the row's stale-warning tooltip.
function staleTitle(shop) {
  const names = (shop.products || []).filter((p) => p.stale).map((p) => p.displayName);
  if (names.length === 0) return '';
  return `Konnte zuletzt nicht aktualisiert werden (Fehler): ${names.join(', ')}`;
}

function ProductCell({ shop, p, onHistory }) {
  if (!p.tracked) {
    return (
      <div className="text-xs text-slate-400">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 ring-1 ring-slate-200 text-slate-500">— keine Daten</span>
        <div className="mt-1 text-[11px] text-slate-400">noch nicht geprüft</div>
      </div>
    );
  }
  // Stock is present but the article cannot be reserved (e.g. Bauhaus freight items: the store shows
  // "N Stück verfügbar" but "Dieses Produkt kann derzeit nicht reserviert werden"). Distinct from the
  // toom case (available && reserveIssueNote), where it shows as reservable but the cart-add is refused.
  const notReservable = !p.available && !!p.reserveIssueNote && (p.currentStock ?? 0) > 0;
  return (
    <div className="flex items-start gap-2">
      <button onClick={() => onHistory(shop, p.product)} className="group flex flex-col items-start gap-1 text-left">
        <span title={notReservable ? p.reserveIssueNote : undefined}
          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold ring-1 ${
          p.available ? 'bg-emerald-50 text-emerald-700 ring-emerald-200'
          : notReservable ? 'bg-amber-50 text-amber-700 ring-amber-200'
          : 'bg-slate-100 text-slate-500 ring-slate-200'}`}>
          <span className={`h-1.5 w-1.5 rounded-full ${
            p.available ? 'bg-emerald-500 animate-pulse-ring' : notReservable ? 'bg-amber-500' : 'bg-slate-400'}`} />
          {p.available ? 'Verfügbar' : notReservable ? 'Verfügbar, nicht reservierbar' : 'Nicht verfügbar'}
          {(p.available || notReservable) && p.currentStock != null && (
            <span className={`rounded px-1 ${p.available ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>{p.currentStock}</span>
          )}
        </span>
        <span className="text-xs text-slate-400">
          {p.price != null ? <span className="text-slate-700 font-semibold">{money(p.price)}</span> : <span>-</span>}
          <span className="text-slate-400 group-hover:text-brand-600 transition"> · Verlauf</span>
        </span>
        {p.note && !notReservable && (
          <span className={`text-[11px] ${p.available ? 'text-emerald-600' : 'text-slate-400'}`}>{p.note}</span>
        )}
        {p.reserveIssueNote && p.available && (
          <span title={p.reserveIssueNote}
            className="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-600">
            <Alert className="h-3 w-3 shrink-0" /> Reservierung fehlgeschlagen
          </span>
        )}
      </button>
      {p.url && (
        <a href={p.url} target="_blank" rel="noopener" title="Zum Artikel"
          className="mt-0.5 grid place-items-center h-7 w-7 shrink-0 rounded-lg text-slate-400 hover:text-brand-600 hover:bg-slate-100 transition">
          <ExternalLink className="h-3.5 w-3.5" />
        </a>
      )}
    </div>
  );
}

export default function ShopTable({ shops, loading, hasOverview, onHistory }) {
  return (
    <section className="card rounded-2xl ring-1 ring-slate-200 overflow-hidden">
      <div className="overflow-auto max-h-[70vh]">
        <table className="w-full text-sm">
          <thead className="sticky top-0 z-10">
            <tr className="text-left text-xs uppercase tracking-wide text-slate-500 border-b border-slate-200 bg-slate-50 shadow-[inset_0_-1px_0_rgb(226_232_240)]">
              <th className="px-4 py-3 font-bold">Shop</th>
              <th className="px-4 py-3 font-bold">Midea PortaSplit</th>
              <th className="px-4 py-3 font-bold">PortaSplit Cool</th>
              <th className="px-4 py-3 font-bold whitespace-nowrap">Aktualisiert</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {shops.map((shop) => (
              <tr key={shop.id} className="hover:bg-slate-50 transition">
                <td className="px-4 py-3 align-top">
                  <div className="flex items-start gap-3">
                    <BrandLogo name={shop.chain} label={shop.chain} className="h-10 w-10 mt-0.5"
                      fallback={<span className="grid place-items-center h-10 w-10 mt-0.5 shrink-0 rounded-xl bg-slate-100 ring-1 ring-slate-200 text-xs font-bold text-slate-400">{(shop.chain || '?').slice(0, 2)}</span>} />
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-bold text-slate-900">{shop.name}</span>
                        {shop.source === 'AMAZON'
                          ? <span className="rounded-md bg-amber-50 text-amber-700 ring-1 ring-amber-200 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">Amazon</span>
                          : shop.source === 'LIDL'
                          ? <span className="rounded-md bg-blue-50 text-blue-700 ring-1 ring-blue-200 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">Lidl</span>
                          : shop.onlineOnly && <span className="rounded-md bg-sky-50 text-sky-700 ring-1 ring-sky-200 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">Online</span>}
                        {!shop.enabled && <span className="rounded-md bg-slate-100 text-slate-500 ring-1 ring-slate-200 px-1.5 py-0.5 text-[10px] font-bold uppercase">Inaktiv</span>}
                      </div>
                      <div className="mt-0.5 flex items-center gap-2 text-xs text-slate-500 flex-wrap">
                        <span className="rounded bg-slate-100 px-1.5 py-0.5 text-slate-600 font-medium">{shop.chain}</span>
                        {shop.city && <span>{[shop.plz, shop.city].filter(Boolean).join(' ')}</span>}
                        {shop.distanceKm != null && (
                          <span className="rounded bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200 px-1.5 py-0.5 font-medium tabular-nums">
                            {shop.distanceKm.toFixed(1).replace('.', ',')} km
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                </td>
                {shop.products.map((p) => (
                  <td key={p.product} className="px-4 py-3 align-top">
                    <ProductCell shop={shop} p={p} onHistory={onHistory} />
                  </td>
                ))}
                <td className="px-4 py-3 align-top whitespace-nowrap text-xs text-slate-400">
                  <div className="flex items-center gap-1.5">
                    {shopUpdated(shop)}
                    {shop.products?.some((p) => p.stale) && (
                      <span title={staleTitle(shop)} className="text-amber-500" aria-label="Aktualisierung fehlgeschlagen">
                        <Alert className="h-3.5 w-3.5" />
                      </span>
                    )}
                  </div>
                </td>
              </tr>
            ))}
            {!loading && hasOverview && shops.length === 0 && (
              <tr><td colSpan={4} className="px-4 py-16 text-center text-slate-400">
                <div className="text-3xl mb-2">🔍</div>
                Keine Shops gefunden. Passe die Suche/Filter an.
              </td></tr>
            )}
            {loading && !hasOverview && (
              <tr><td colSpan={4} className="px-4 py-16 text-center text-slate-400">Lade Daten…</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}
