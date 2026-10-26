// 壳层入口：状态栏/工具栏/导航 + hash 路由（docs/04 §1，§2.5 移动端 v2.4）。
import { api } from './api.js';
import { store } from './store.js';
import { h, toast, modal } from './ui.js';

const LEVEL_EMOJI = ['🌱', '🌿', '🌳', '🌸', '🏆'];
const ROUTES = {
  desk: { label: '🏠 学习台', module: './views/desk.js' },
  cn: { label: '📖 语文', module: './views/textbook.js', subject: 'cn' },
  math: { label: '🧮 数学', module: './views/textbook.js', subject: 'math' },
  en: { label: '🅰️ 英语', module: './views/textbook.js', subject: 'en' },
  knowledge: { label: '🌳 知识体系', module: './views/knowledge.js' },
  life: { label: '🌱 生活营', module: './views/life.js' },
  shop: { label: '🛒 兑换商店', module: './views/shop.js' },
  admin: { label: '🔒 后台配置', module: './views/admin.js' },
};
const cache = {};

const ctx = {
  api, store, ui: { h, toast, modal, go: (r) => { location.hash = `#/${r}`; } },
  get scope() { return { grade: store.s.grade, term: store.s.term }; },
};

// ── 状态栏 / 工具栏 / 导航 ─────────────────────────

function renderStatusbar() {
  const el = document.getElementById('statusbar');
  const { profile, coins, progress, profiles } = store.s;
  el.innerHTML = '';
  if (!profile) return;
  const gradeSel = h('select', { onchange: (e) => { store.setScope(+e.target.value, store.s.term); } },
    [1, 2, 3, 4, 5, 6].map((g) => h('option', { value: g, selected: g === profile.grade ? 'selected' : null }, `${g}年级`)));
  const termSel = h('select', { onchange: (e) => { store.setScope(profile.grade, e.target.value); } },
    ['上', '下'].map((t) => h('option', { value: t, selected: t === profile.term ? 'selected' : null }, `${t}学期`)));
  const profSel = h('select', { onchange: async (e) => { await store.switchProfile(e.target.value); renderAll(); } },
    profiles.map((p) => h('option', { value: p.id, selected: p.id === profile.id ? 'selected' : null }, p.name)));
  const prog = progress ? Object.entries(progress).map(([k, v]) =>
    h('span', { class: 'badge subject-dot' }, `${{ cn: '语', math: '数', en: '英' }[k]} ${v.schoolPercent}%${v.hasOlympiad ? ` · 奥${v.olympiadPercent}%` : ''}`)) : [];
  // v2.3/FR-29：待审核角标（N>0 显示），点开只读浮层列本人申报项。
  const pend = (store.s.pendingCount || 0) > 0
    ? h('button', { class: 'badge pend-badge', onclick: showMine }, `📋 待审核 ${store.s.pendingCount}`) : null;
  el.append(
    h('button', { class: 'btn drawer-btn', onclick: () => document.body.classList.toggle('nav-open') }, '☰'),
    h('span', { class: 'who' }, h('span', { class: 'avatar' }, profile.avatar || '🐼'), profile.name, profSel),
    gradeSel, termSel,
    h('span', { class: 'badge' }, `${LEVEL_EMOJI[coins?.levelIndex ?? 0] ?? '🌱'} ${coins?.levelName ?? ''}`),
    h('span', { class: 'badge streak' }, `🔥 连续${coins?.streak ?? 0}天`),
    h('span', { class: 'badge coins' }, `🪙 ${coins?.balance ?? 0}`),
    h('span', { class: 'badge' }, `累计${coins?.totalEarned ?? 0}`),
    pend,
    ...prog);
}

/** 孩子端只读浮层：本人待审核清单（docs/04 §P1 v2.3）。 */
async function showMine() {
  let items = [];
  try { items = await api.get('/pending/mine'); } catch (e) { toast(e.message, 'err'); return; }
  modal({
    title: `📋 我等家长确认的（${items.length}）`,
    cancelText: '关闭', okText: '知道啦',
    body: h('div', { style: 'display:flex;flex-direction:column;gap:8px;max-height:50vh;overflow:auto' },
      ...items.map((p) => {
        const neg = p.kind === 'PENALTY' || p.amount < 0; // PENALTY 存绝对值，按扣分展示
        return h('div', { class: 'mine-row' },
          h('span', { class: 'mine-label' }, p.label),
          h('span', { class: `mine-amount ${neg ? 'neg' : ''}` }, `${neg ? '−' : '+'}${Math.abs(p.amount)}🪙`),
          p.evidenceCount ? h('span', { class: 'pill', title: '已附佐证照片' }, `📷${p.evidenceCount}`) : null);
      }),
      h('p', { style: 'color:var(--c-sub);margin-top:10px' }, '等爸爸妈妈在后台确认哦～通过后金币才会到账')),
  });
}

function renderToolbar() {
  const el = document.getElementById('toolbar');
  el.innerHTML = '';
  el.append(
    h('button', { class: 'btn primary', onclick: doCheckin }, '✅ 每日签到 +20'),
    h('button', { class: 'btn', onclick: () => ctx.ui.go('admin') }, '🔒 后台配置'),
    h('button', { class: 'btn ghost', onclick: doExport }, '⬇️ 导出备份'),
    h('button', { class: 'btn ghost', onclick: doImport }, '⬆️ 导入备份'));
}

function renderNav(route) {
  const el = document.getElementById('navrail');
  el.innerHTML = '';
  for (const [key, meta] of Object.entries(ROUTES)) {
    // v2.4/§2.5：图标与文字分开，≤900px 只留图标，≤480px 进抽屉
    const space = meta.label.indexOf(' ');
    el.append(h('a', { href: `#/${key}`, class: key === route ? 'active' : '' },
      h('span', { class: 'nav-icon' }, meta.label.slice(0, space < 0 ? 1 : space)),
      h('span', { class: 'nav-text' }, meta.label.slice(space < 0 ? 1 : space + 1))));
  }
}

function renderAll() {
  renderStatusbar();
  renderToolbar();
  route();
}

// ── 工具栏动作 ─────────────────────────

async function doCheckin() {
  try {
    const r = await api.post('/checkin', { profileId: store.s.currentProfileId });
    // v2.3/FR-29：审批启用时签到转待审（state=pending，不即时到账）。
    if (r.state === 'pending') toast('⏳ 签到已提交，等家长确认哦', 'ok');
    else toast(`签到成功 +${r.tx.amount}🪙！`, 'ok');
    await store.refreshCoins(); // refreshCoins 会广播 statusbar:refresh，角标同步
  } catch (e) { toast(e.message, 'err'); }
}

async function ensureToken() {
  if (api.adminToken) return true;
  return false;
}

async function doExport() {
  if (!await ensureToken()) { toast('请先在后台输入家长 PIN', 'err'); ctx.ui.go('admin'); return; }
  try {
    const res = await fetch('/api/backup/export', { headers: { 'X-Admin-Token': api.adminToken } });
    if (!res.ok) throw new Error('导出失败');
    const blob = await res.blob();
    const a = h('a', { href: URL.createObjectURL(blob), download: `studyworkplace-backup-${new Date().toISOString().slice(0, 10)}.json` });
    a.click();
    toast('备份文件已下载', 'ok');
  } catch (e) { toast(e.message, 'err'); }
}

async function doImport() {
  if (!await ensureToken()) { toast('请先在后台输入家长 PIN', 'err'); ctx.ui.go('admin'); return; }
  const input = h('input', { type: 'file', accept: '.json' });
  input.onchange = async () => {
    const f = input.files[0];
    if (!f) return;
    try {
      const raw = await f.text();
      toast('校验中…');
      const preview = await api.raw('POST', raw, '/backup/import');
      const go = await modal({
        title: '确认覆盖导入？', danger: true, okText: '确认覆盖',
        body: h('div', {},
          h('p', {}, `档案 ${preview.profileCount} 个 · 流水 ${preview.txCount} 条`),
          h('p', { style: 'color:var(--c-expense);margin-top:8px' }, preview.warning),
          h('p', { style: 'margin-top:8px;color:var(--c-sub)' }, '导入前系统已自动保存当前数据快照')),
      });
      if (!go) return;
      const r = await api.post('/backup/import/confirm', { confirmId: preview.confirmId });
      toast(`导入完成（${r.rows} 行）`, 'ok');
      await store.refresh();
      renderAll();
    } catch (e) { toast(e.message, 'err'); }
  };
  input.click();
}

// ── 路由 ─────────────────────────

async function route() {
  const key = (location.hash.replace('#/', '') || 'desk').split('?')[0];
  const meta = ROUTES[key] || ROUTES.desk;
  document.body.classList.remove('nav-open'); // v2.4：抽屉导航点完自动收起
  const main = document.getElementById('main');
  main.innerHTML = '';
  main.append(h('div', { class: 'empty' }, '加载中…'));
  try {
    if (!cache[key]) cache[key] = await import(meta.module);
    await cache[key].render(main, ctx, meta);
  } catch (e) {
    console.error(e);
    main.innerHTML = '';
    main.append(h('div', { class: 'empty' }, `页面加载出错：${e.message}`));
  }
  renderNav(key);
  renderStatusbar();
}

// ── 启动 & 档案门 ─────────────────────────

async function boot() {
  await store.refresh();
  if (!store.s.profiles.length) { showGate(); return; }
  document.getElementById('gate').hidden = true;
  document.getElementById('shell').hidden = false;
  renderAll();
}

function showGate() {
  const form = document.getElementById('gate-form');
  form.onsubmit = async (e) => {
    e.preventDefault();
    const err = document.getElementById('gate-err');
    err.textContent = '';
    try {
      await api.post('/profiles', {
        name: document.getElementById('gate-name').value.trim(),
        grade: +document.getElementById('gate-grade').value,
        term: document.getElementById('gate-term').value,
      });
      await boot();
    } catch (ex) { err.textContent = ex.message; }
  };
}

window.addEventListener('hashchange', route);
window.addEventListener('app:refresh', renderAll);
// v2.3：子页面动作改变 pendingCount 后轻量刷新顶栏（不重跑路由）。
window.addEventListener('statusbar:refresh', renderStatusbar);
boot().catch((e) => {
  console.error(e);
  document.getElementById('gate-err').textContent = `无法连接服务：${e.message}`;
});
