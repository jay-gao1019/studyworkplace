// UI 小工具：toast / 确认弹层 / 飘金币 / DOM 速建。

export function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') el.className = v;
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
    else if (v !== null && v !== undefined) el.setAttribute(k, v);
  }
  for (const c of children) appendChild(el, c);
  return el;
}

function appendChild(el, c) {
  if (c === null || c === undefined || c === false) return;
  if (Array.isArray(c)) { for (const x of c) appendChild(el, x); return; }
  el.append(c.nodeType ? c : document.createTextNode(String(c)));
}

export function toast(msg, kind = '') {
  const root = document.getElementById('toast-root');
  const t = h('div', { class: `toast ${kind}` }, msg);
  root.append(t);
  setTimeout(() => t.remove(), 2600);
}

/** 通用弹层；返回 Promise，确定→resolve(表单值或 true)，取消→resolve(null)。 */
export function modal({ title, body, okText = '确定', cancelText = '取消', danger = false }) {
  return new Promise((resolve) => {
    const root = document.getElementById('modal-root');
    const form = body && body.nodeType === 1 ? body : null;
    const close = (val) => { root.innerHTML = ''; resolve(val); };
    const card = h('div', { class: 'modal-card' },
      h('h3', {}, title),
      typeof body === 'string' ? h('p', { style: 'margin-top:10px;color:var(--c-sub)' }, body) : (body || null),
      h('div', { class: 'modal-actions' },
        h('button', { class: 'btn ghost', onclick: () => close(null) }, cancelText),
        h('button', { class: `btn ${danger ? 'danger' : 'primary'}`, onclick: () => close(form ? form : true) }, okText)));
    card.addEventListener('keydown', (e) => { if (e.key === 'Enter' && form) close(form); });
    root.innerHTML = '';
    root.append(card);
    card.querySelector('button:last-child').focus();
  });
}

export function confirmModal(title, text = '') {
  return modal({ title, body: text });
}

/** 在锚点元素右上角飘一枚 "+5🪙"。 */
export function floatCoin(anchor, text) {
  const f = h('span', { class: 'float-coin' }, text);
  anchor.classList.add('floaty');
  f.style.right = '12px';
  f.style.top = '6px';
  anchor.append(f);
  setTimeout(() => f.remove(), 850);
}

export const fmt = {
  coins: (n) => `${n}🪙`,
  pct: (x) => `${Math.round((x || 0) * 100)}%`,
};
