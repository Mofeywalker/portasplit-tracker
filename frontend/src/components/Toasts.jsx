import { Check, Alert, Info } from '../icons.jsx';

const STYLES = {
  success: { cls: 'bg-white text-emerald-800 ring-emerald-200', Icon: Check, iconCls: 'text-emerald-500' },
  error: { cls: 'bg-white text-rose-800 ring-rose-200', Icon: Alert, iconCls: 'text-rose-500' },
  info: { cls: 'bg-white text-slate-800 ring-brand-200', Icon: Info, iconCls: 'text-brand-500' },
};

export default function Toasts({ toasts }) {
  return (
    <div className="fixed bottom-4 right-4 z-[60] space-y-2 w-80 max-w-[calc(100vw-2rem)]">
      {toasts.map((t) => {
        const s = STYLES[t.type] || STYLES.info;
        const Icon = s.Icon;
        return (
          <div key={t.id} className={`animate-fade-in rounded-xl px-4 py-3 text-sm shadow-xl shadow-slate-900/10 ring-1 flex items-start gap-2 ${s.cls}`}>
            <Icon className={`h-4 w-4 mt-0.5 shrink-0 ${s.iconCls}`} />
            <span className="font-semibold">{t.text}</span>
          </div>
        );
      })}
    </div>
  );
}
