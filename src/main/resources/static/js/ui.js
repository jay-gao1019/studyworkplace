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

// ── v2.4/M13.6：佐证与图片查看（desk/textbook/admin 共用）──────

/**
 * 📸 提交佐证半屏弹层（docs/04 §P1 v2.4/FR-32）。
 * 拍照（capture 唤起相机）或相册选图 ≤3 张，选后即传（服务端盖时间水印）；备注 ≤60 字。
 * resolve(null)=取消；resolve({photoIds,note})=提交（照片可为空＝跳过直接提交）。
 * 注：水印备注取"选图那一刻"的输入值；最终备注随动作请求体提交并并入审批理由。
 */
export function evidenceSheet(api, { title, subtitle = '', kind = 'task', okText = '提交申请', profileId = null } = {}) {
  return new Promise((resolve) => {
    const shots = []; // {photoId,url,file,name}
    let uploading = 0;
    const MAX = 3;

    const thumbs = h('div', { class: 'evid-thumbs' });
    const noteIn = h('input', { class: 'evid-note', maxlength: '60', placeholder: '备注（可选，≤60 字，会写进照片水印和申请理由）' });
    const submitBtn = h('button', { class: 'btn primary' }, okText);

    const camIn = h('input', { type: 'file', accept: 'image/jpeg,image/png', capture: 'environment', style: 'display:none' });
    const libIn = h('input', { type: 'file', accept: 'image/*', multiple: true, style: 'display:none' });

    function paint() {
      thumbs.innerHTML = '';
      for (const s of shots) {
        const img = s.uploading
          ? h('span', { class: 'evid-spin' }, '⏳')
          : h('img', { src: s.url, alt: s.name });
        const del = h('button', { class: 'evid-del', onclick: () => { shots.splice(shots.indexOf(s), 1); paint(); } }, '✕');
        thumbs.append(h('div', { class: 'evid-thumb' }, img, del));
      }
      if (shots.length < MAX) {
        thumbs.append(
          h('button', { class: 'evid-add', onclick: () => camIn.click() }, '📷 拍照'),
          h('button', { class: 'evid-add', onclick: () => libIn.click() }, '🖼 相册'));
      }
      submitBtn.disabled = uploading > 0;
    }

    async function addFiles(list) {
      const noteNow = noteIn.value.trim().slice(0, 60) || undefined;
      for (const file of [...list]) {
        if (shots.length >= MAX) { toast(`最多 ${MAX} 张佐证照片哦`, 'err'); break; }
        const slot = { uploading: true, file, name: file.name };
        shots.push(slot);
        uploading += 1;
        paint();
        try {
          const fd = new FormData();
          fd.append('file', file);
          fd.append('kind', kind);
          if (profileId) fd.append('profileId', profileId); // 家长代记时归属到目标档案（需有效 admin token）
          if (noteNow) fd.append('note', noteNow);
          const r = await api.upload('/photo/evidence', fd);
          Object.assign(slot, { uploading: false, photoId: r.photoId, url: r.url });
        } catch (e) {
          toast(e.message || '照片上传失败', 'err');
          shots.splice(shots.indexOf(slot), 1);
        } finally {
          uploading -= 1;
          paint();
        }
      }
    }

    camIn.onchange = () => { addFiles([camIn.files[0]]); camIn.value = ''; };
    libIn.onchange = () => { addFiles(libIn.files); libIn.value = ''; };

    const close = (val) => { overlay.remove(); document.removeEventListener('keydown', onKey); resolve(val); };
    const onKey = (e) => { if (e.key === 'Escape') close(null); };

    const sheet = h('div', { class: 'evid-card' },
      h('div', { class: 'evid-head' }, h('b', {}, '📸 ' + title),
        h('button', { class: 'btn ghost', onclick: () => close(null) }, '✕')),
      subtitle ? h('p', { class: 'evid-sub' }, subtitle) : null,
      h('div', { style: 'display:flex;flex-direction:column;gap:8px' }, noteIn),
      h('p', { class: 'evid-hint' }, '照片会由服务器自动盖上时间水印；不拍也能提交，家长在审批中心看图核验'),
      thumbs, camIn, libIn,
      h('div', { class: 'modal-actions' },
        h('button', {
          class: 'btn ghost',
          onclick: () => close({ photoIds: [], note: noteIn.value.trim().slice(0, 60) || undefined }),
        }, '跳过直接提交'),
        submitBtn));
    submitBtn.onclick = () => close({ photoIds: shots.filter((s) => s.photoId).map((s) => s.photoId), note: noteIn.value.trim().slice(0, 60) || undefined });
    const overlay = h('div', { class: 'evid-overlay' }, sheet);
    overlay.addEventListener('click', (e) => { if (e.target === overlay && !uploading) close(null); });
    document.addEventListener('keydown', onKey);
    document.body.append(overlay);
    paint();
  });
}

/** 大图轮播灯箱（M11.10）：photos=[{url,takenAt?,note?}]，从 index 打开。 */
export function lightbox(photos, index = 0) {
  if (!photos || !photos.length) return;
  let cur = index;
  const img = h('img', { alt: '佐证照片' });
  const cap = h('div', { class: 'lb-cap' });
  const prev = h('button', { class: 'btn ghost lb-nav' }, '‹');
  const next = h('button', { class: 'btn ghost lb-nav' }, '›');
  function paint() {
    const p = photos[cur];
    img.src = p.url;
    cap.textContent = `${cur + 1}/${photos.length}　🕐 ${(p.takenAt || '').replace('T', ' ').slice(0, 19)}`
      + (p.note ? `　备注：${p.note}` : '');
    prev.style.visibility = photos.length > 1 ? 'visible' : 'hidden';
    next.style.visibility = photos.length > 1 ? 'visible' : 'hidden';
  }
  const close = () => { overlay.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => {
    if (e.key === 'Escape') close();
    if (e.key === 'ArrowLeft') { cur = (cur - 1 + photos.length) % photos.length; paint(); }
    if (e.key === 'ArrowRight') { cur = (cur + 1) % photos.length; paint(); }
  };
  prev.onclick = () => { cur = (cur - 1 + photos.length) % photos.length; paint(); };
  next.onclick = () => { cur = (cur + 1) % photos.length; paint(); };
  const overlay = h('div', { class: 'lb-overlay' },
    h('div', { class: 'lb-box' },
      h('div', { class: 'pdf-bar' }, h('b', {}, '📷 佐证照片'), cap,
        h('button', { class: 'btn ghost', style: 'margin-left:auto', onclick: close }, '✕ 关闭')),
      h('div', { class: 'lb-stage' }, prev, img, next)));
  overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });
  document.addEventListener('keydown', onKey);
  document.body.append(overlay);
  paint();
}
