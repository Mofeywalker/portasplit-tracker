import { useEffect, useState } from 'react';

const input = 'mt-1 w-full rounded-lg bg-white ring-1 ring-slate-200 focus:ring-2 focus:ring-brand-400 outline-none px-3 py-2 text-sm text-slate-700';

export default function ShopModal({ mode, initial, chains, saving, error, onClose, onSubmit }) {
  const [form, setForm] = useState(initial);
  useEffect(() => setForm(initial), [initial]);

  const set = (key) => (e) => {
    const v = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [key]: v }));
  };

  const submit = (e) => {
    e.preventDefault();
    onSubmit(form);
  };

  return (
    <div className="fixed inset-0 z-50 grid place-items-center p-4">
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" onClick={onClose} />
      <div className="relative w-full max-w-lg card rounded-2xl ring-1 ring-slate-200 shadow-2xl animate-fade-in">
        <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
          <h3 className="font-extrabold text-slate-900">{mode === 'edit' ? 'Shop bearbeiten' : 'Shop hinzufügen'}</h3>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-900">✕</button>
        </div>
        <form onSubmit={submit} className="px-5 py-4 space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">Kette *</span>
              <input value={form.chain} onChange={set('chain')} required list="chains" className={input} />
              <datalist id="chains">{chains.map((c) => <option key={c} value={c} />)}</datalist>
            </label>
            <label className="block">
              <span className="text-xs font-semibold text-slate-500">Name *</span>
              <input value={form.name} onChange={set('name')} required className={input} />
            </label>
          </div>
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">Match-Name <span className="text-slate-400 font-normal">(API-Name, optional - Standard = Name)</span></span>
            <input value={form.matchName} onChange={set('matchName')} placeholder={form.name} className={input} />
          </label>
          <div className="grid grid-cols-6 gap-3">
            <label className="block col-span-2">
              <span className="text-xs font-semibold text-slate-500">PLZ</span>
              <input value={form.plz} onChange={set('plz')} className={input} />
            </label>
            <label className="block col-span-4">
              <span className="text-xs font-semibold text-slate-500">Stadt</span>
              <input value={form.city} onChange={set('city')} className={input} />
            </label>
          </div>
          <label className="block">
            <span className="text-xs font-semibold text-slate-500">Straße</span>
            <input value={form.street} onChange={set('street')} className={input} />
          </label>
          <div className="flex items-center gap-6 pt-1">
            <label className="inline-flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
              <input type="checkbox" checked={form.onlineOnly} onChange={set('onlineOnly')} className="h-4 w-4 rounded border-slate-300 accent-brand-500" />
              Nur Online
            </label>
            <label className="inline-flex items-center gap-2 text-sm text-slate-600 cursor-pointer">
              <input type="checkbox" checked={form.enabled} onChange={set('enabled')} className="h-4 w-4 rounded border-slate-300 accent-emerald-500" />
              Aktiv überwachen
            </label>
          </div>
          {error && <p className="text-sm text-rose-600">{error}</p>}
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" onClick={onClose} className="rounded-lg px-4 py-2 text-sm text-slate-600 hover:bg-slate-100 transition">Abbrechen</button>
            <button type="submit" disabled={saving} className="rounded-lg bg-brand-500 hover:bg-brand-600 disabled:opacity-50 px-4 py-2 text-sm font-semibold text-white shadow-lg shadow-brand-500/25 transition">
              {saving ? 'Speichern…' : 'Speichern'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
