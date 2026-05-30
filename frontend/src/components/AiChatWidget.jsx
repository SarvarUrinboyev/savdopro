import { useEffect, useRef, useState } from 'react';
import { AiApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';

/**
 * Floating AI chat button + panel. Sits on top of every page so the
 * shop-owner can ask "O'tgan haftada qancha sotdik?" anywhere.
 *
 * State lives in this component (not global) so the conversation
 * resets on page change. Within a session we send the recent Q/A
 * history to the backend so follow-up questions keep context, and the
 * assistant pulls the exact data it needs via its tool calls.
 */
const SUGGESTIONS = [
  "Bugun qaysi soatda eng ko'p sotildi?",
  'Eng foydali mahsulotlar?',
  'Qaysi mahsulot tugayapti?',
];

export function AiChatWidget() {
  const t = useT();
  const [open, setOpen] = useState(false);
  const [msgs, setMsgs] = useState([
    { role: 'ai', text: "Salom! Men SavdoPRO AI yordamchisiman. Misol uchun \"O'tgan oyda qancha sotdik?\" deb so'rang." },
  ]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const scrollRef = useRef(null);

  useEffect(() => {
    if (open) scrollRef.current?.scrollTo({ top: 999999, behavior: 'smooth' });
  }, [open, msgs]);

  // Pair the visible transcript into prior Q/A turns so follow-up
  // questions keep context. Capped to the last few exchanges so the
  // prompt stays small.
  const recentTurns = () => {
    const turns = [];
    for (let i = 0; i < msgs.length - 1; i += 1) {
      if (msgs[i].role === 'user' && msgs[i + 1]?.role === 'ai') {
        turns.push({ question: msgs[i].text, answer: msgs[i + 1].text });
      }
    }
    return turns.slice(-5);
  };

  const send = async (preset) => {
    const q = (preset ?? input).trim();
    if (!q || busy) return;
    const history = recentTurns();
    setMsgs((m) => [...m, { role: 'user', text: q }]);
    setInput('');
    setBusy(true);
    try {
      const resp = await AiApi.ask(q, history);
      setMsgs((m) => [...m, { role: 'ai', text: resp.answer }]);
    } catch (err) {
      setMsgs((m) => [...m, { role: 'ai', text: '⚠️ ' + (err.message || 'Xatolik') }]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <button
        className="ai-chat-fab"
        onClick={() => setOpen((v) => !v)}
        title={t('AI yordamchi')}
      >
        🤖
      </button>
      {open && (
        <div className="ai-chat-panel">
          <div className="ai-chat-head">
            <strong>🤖 {t('AI yordamchi')}</strong>
            <button
              style={{ background: 'transparent', border: 0, color: '#fff', cursor: 'pointer', fontSize: 20 }}
              onClick={() => setOpen(false)}
            >×</button>
          </div>
          <div className="ai-chat-body" ref={scrollRef}>
            {msgs.map((m, i) => (
              <div key={i} className={`ai-msg ${m.role}`}>{m.text}</div>
            ))}
            {msgs.length === 1 && !busy && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => send(s)}
                    style={{
                      fontSize: 12,
                      padding: '4px 10px',
                      borderRadius: 14,
                      border: '1px solid var(--brand-primary, #3b82f6)',
                      background: 'transparent',
                      color: 'var(--brand-primary, #3b82f6)',
                      cursor: 'pointer',
                    }}
                  >
                    {t(s)}
                  </button>
                ))}
              </div>
            )}
            {busy && <div className="ai-msg ai">⋯</div>}
          </div>
          <div className="ai-chat-foot">
            <input
              type="text"
              placeholder={t('Savolingizni yozing...')}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
              disabled={busy}
            />
            <button className="btn btn-primary" onClick={() => send()} disabled={busy || !input.trim()}>
              {busy ? '...' : '➤'}
            </button>
          </div>
        </div>
      )}
    </>
  );
}
