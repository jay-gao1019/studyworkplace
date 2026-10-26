// P6 后台配置（docs/04 §2 P6）：家长 PIN 门 + 五个配置 Tab。
// 原生 ESM，无第三方依赖；DOM 全部走 ui.h()，接口全部走 ctx.api.admin()。
import { h, toast, modal, confirmModal } from '../ui.js';

const GRADES = [1, 2, 3, 4, 5, 6];

// ── 入口 ───────────────────────────────────────────
export async function render(main, ctx) {
  main.innerHTML = '';
  if (!ctx.api.adminToken) {
    renderPinGate(main, ctx);
    return;
  }
  renderAdmin(main, ctx);
}

// ── 小工具：表单字段、下拉、错误处理 ────────────────
function field(label, control) {
  return h('label', { style: 'display:flex;flex-direction:column;gap:4px;font-size:.92rem' },
    h('span', { style: 'color:var(--c-sub)' }, label), control);
}

function optList(value, label, current) {
  return h('option', { value, selected: String(current) === String(value) ? 'selected' : null }, label);
}

function sel(cls, pairs, current) {
  const s = h('select', { class: cls }, ...pairs.map(([v, l]) => optList(v, l, current)));
  return s;
}

/** 统一的后台请求包装：401 回 PIN 门，其余弹错误。fn 返回 Promise。 */
async function runAdmin(ctx, main, fn, onOk) {
  try {
    const r = await fn();
    if (onOk) onOk(r);
    return r;
  } catch (e) {
    if (e && e.code === 401) {
      toast('家长验证已过期，请重新输入 PIN', 'err');
      render(main, ctx); // token 已被 api.admin 清除 → 回到 PIN 门
    } else {
      toast((e && e.message) || '操作失败', 'err');
    }
    return null;
  }
}

// ── PIN 门 ─────────────────────────────────────────
function renderPinGate(main, ctx) {
  const dots = h('div', { class: 'pin-dots' });
  let pin = '';
  let busy = false;

  const paint = () => { dots.textContent = '●'.repeat(pin.length); };

  const press = (d) => {
    if (busy) return;
    if (pin.length >= 4) return;
    pin += d;
    paint();
    if (pin.length === 4) submit();
  };
  const back = () => { if (busy) return; pin = pin.slice(0, -1); paint(); };
  const clear = () => { if (busy) return; pin = ''; paint(); };

  async function submit() {
    busy = true;
    const entered = pin;
    try {
      const r = await ctx.api.post('/admin/unlock', { pin: entered });
      ctx.api.setAdminToken(r.token);
      toast('已进入家长后台', 'ok');
      render(main, ctx);
    } catch (e) {
      pin = '';
      paint();
      busy = false;
      toast((e && e.message) || 'PIN 错误', 'err');
    }
  }

  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '清空', '0', '退格'];
  const pad = h('div', { class: 'pad-grid' }, ...keys.map((k) => h('button', {
    onclick: () => { if (k === '清空') clear(); else if (k === '退格') back(); else press(k); },
  }, k)));

  const hint = ctx.store.s.pinUnset
    ? h('p', { style: 'color:var(--c-expense);margin-top:10px' }, '尚未设置 PIN，默认 8888，进来后记得改')
    : null;

  const box = h('div', { class: 'pin-pad' },
    h('h2', {}, '🔒 家长入口'),
    h('p', { style: 'color:var(--c-sub);margin-top:6px' }, '请输入 4 位家长 PIN'),
    dots, pad, hint);

  main.append(box);
  paint();
}

// ── 配置页骨架：顶部锁定 + admin-tabs ────────────────
function renderAdmin(main, ctx) {
  const TABS = [
    ['users', '用户管理', renderUsers],
    ['plan', '每日计划', renderPlan],
    ['shop', '商店', renderShop],
    ['life', '生活设置', renderLife],
    ['data', '数据与题库', renderData],
  ];
  let current = 'users';

  main.innerHTML = '';
  const tabBar = h('div', { class: 'admin-tabs' });
  const bodyBox = h('div', { class: 'admin-body' });

  const topbar = h('div', { style: 'display:flex;justify-content:space-between;align-items:center' },
    h('div', { class: 'section-title', style: 'margin:0' }, '🔒 家长后台'),
    h('button', { class: 'btn ghost', onclick: doLock }, '🔒 锁定后台'));

  main.append(topbar, tabBar, bodyBox);

  async function doLock() {
    await runAdmin(ctx, main, () => ctx.api.admin('POST', '/lock', {}));
    ctx.api.setAdminToken(null);
    render(main, ctx);
  }

  function drawTabs() {
    tabBar.innerHTML = '';
    for (const [key, label] of TABS) {
      tabBar.append(h('button', {
        class: `btn ghost ${key === current ? 'on' : ''}`,
        onclick: () => { current = key; drawTabs(); loadTab(); },
      }, label));
    }
  }

  async function loadTab() {
    bodyBox.innerHTML = '';
    bodyBox.append(h('div', { class: 'empty' }, '加载配置…'));
    let config;
    try {
      config = await ctx.api.admin('GET', '/config');
    } catch (e) {
      if (e && e.code === 401) {
        toast('家长验证已过期，请重新输入 PIN', 'err');
        render(main, ctx);
      } else {
        toast((e && e.message) || '加载配置失败', 'err');
        bodyBox.innerHTML = '';
        bodyBox.append(h('div', { class: 'card empty' }, (e && e.message) || '加载配置失败'));
      }
      return;
    }
    const found = TABS.find(([k]) => k === current);
    bodyBox.innerHTML = '';
    found[2](bodyBox, ctx, config, { main, reload: loadTab });
  }

  drawTabs();
  loadTab();
}

// ── 通用：表格单元格 ────────────────────────────────
function td(content, attrs = {}) {
  return h('td', attrs, content.nodeType ? content : String(content));
}

function table(cols, rows) {
  return h('table', { class: 'cfg-table' },
    h('thead', {}, h('tr', {}, ...cols.map((c) => h('th', {}, c)))),
    h('tbody', {}, ...rows));
}

function enabledPill(on) {
  return h('span', { class: 'pill', style: on ? 'background:#d9f5e3;color:#1a7a3c' : 'background:#ececec;color:#777' },
    on ? '启用' : '停用');
}

function subBox(title, actions) {
  return h('div', { style: 'display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;flex-wrap:wrap;gap:8px' },
    h('div', { class: 'section-title', style: 'margin:0' }, title), actions);
}

// ════════════════════════════════════════════════════
// Tab 1 · 用户管理
// ════════════════════════════════════════════════════
function renderUsers(box, ctx, config, svc) {
  const { main, reload } = svc;
  const profiles = config.profiles || [];

  async function afterChange(id, msg) {
    toast(msg, 'ok');
    if (String(id) === String(ctx.store.s.currentProfileId)) {
      try { await ctx.store.refresh(); } catch { /* 忽略 */ }
      toast('已改动当前档案，界面正在刷新…', 'ok');
      window.dispatchEvent(new Event('app:refresh'));
    } else {
      reload();
    }
  }

  // 新建档案
  function newProfileForm(item = {}) {
    return h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
      field('名字', h('input', { class: 'f-name', value: item.name || '' })),
      field('头像（emoji）', h('input', { class: 'f-avatar', value: item.avatar || '🐼' })),
      field('年级', sel('f-grade', GRADES.map((g) => [String(g), `${g} 年级`]), item.grade || 1)),
      field('学期', sel('f-term', [['上', '上学期'], ['下', '下学期']], item.term || '上')));
  }

  async function createProfile() {
    const node = await modal({ title: '新建档案', body: newProfileForm(), okText: '创建' });
    if (!node) return;
    const name = node.querySelector('.f-name').value.trim();
    if (!name) { toast('名字不能为空', 'err'); return; }
    const obj = {
      name,
      avatar: node.querySelector('.f-avatar').value.trim() || undefined,
      grade: +node.querySelector('.f-grade').value,
      term: node.querySelector('.f-term').value,
    };
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/profiles', obj));
    if (r) afterChange(r.id, `已创建档案「${name}」`);
  }

  async function editProfile(p) {
    const node = await modal({ title: `编辑：${p.name}`, body: newProfileForm(p), okText: '保存' });
    if (!node) return;
    const obj = {
      name: node.querySelector('.f-name').value.trim(),
      avatar: node.querySelector('.f-avatar').value.trim(),
      grade: +node.querySelector('.f-grade').value,
      term: node.querySelector('.f-term').value,
    };
    const r = await runAdmin(ctx, main, () => ctx.api.admin('PATCH', `/profiles/${p.id}`, obj));
    if (r) afterChange(p.id, '资料已保存');
  }

  async function deleteProfile(p) {
    const first = await confirmModal('删除档案', `将永久删除档案「${p.name}」及其金币、计划、进度等数据。此操作不可恢复。`);
    if (!first) return;
    const node = await modal({
      title: '输入档案名以确认',
      danger: true,
      okText: '确认删除',
      body: h('div', { style: 'display:flex;flex-direction:column;gap:10px' },
        h('p', { style: 'color:var(--c-sub)' }, `请输入档案名「${p.name}」：`),
        h('input', { class: 'f-confirm', placeholder: p.name })),
    });
    if (!node) return;
    const word = node.querySelector('.f-confirm').value.trim();
    if (word !== p.name) { toast('输入的档案名不匹配，已取消', 'err'); return; }
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/danger/delete-profile', { profileId: p.id, confirmWord: word }));
    if (r) {
      toast(`已删除档案「${p.name}」`, 'ok');
      if (String(p.id) === String(ctx.store.s.currentProfileId)) {
        try { await ctx.store.refresh(); } catch { /* 忽略 */ }
        window.dispatchEvent(new Event('app:refresh'));
      } else reload();
    }
  }

  const list = h('div', { class: 'grid cols2' });
  if (!profiles.length) {
    list.append(h('div', { class: 'card empty' }, '还没有档案，点右上角新建第一个吧'));
  }
  for (const p of profiles) {
    const isCurrent = String(p.id) === String(ctx.store.s.currentProfileId);
    list.append(h('div', { class: 'card', style: 'display:flex;align-items:center;gap:12px' },
      h('span', { style: 'font-size:2rem' }, p.avatar || '🐼'),
      h('div', { style: 'flex:1' },
        h('b', {}, p.name, isCurrent ? h('span', { class: 'pill', style: 'margin-left:6px' }, '当前') : null),
        h('div', { style: 'color:var(--c-sub);font-size:.88rem' }, `${p.grade} 年级 · ${p.term}学期`)),
      h('button', { class: 'btn ghost', onclick: () => editProfile(p) }, '编辑'),
      h('button', { class: 'btn danger', onclick: () => deleteProfile(p) }, '删除')));
  }

  box.append(
    subBox('👨‍👩‍👧 用户管理', h('button', { class: 'btn primary', onclick: createProfile }, '＋ 新建档案')),
    list);
}

// ════════════════════════════════════════════════════
// Tab 2 · 每日计划（任务项 CRUD + 手动奖惩 + 数据修正 M7.6）
// ════════════════════════════════════════════════════
const ICON_PRESETS = ['📚', '🧮', '⚽', '✍️', '🎨', '🎵', '🔬', '🧹', '🍽️', '🪑', '🛏️', '🌸', '🗑️', '⏰', '📱', '🌙', '🅰️', '🏅'];

function taskForm(item = {}) {
  const iconInput = h('input', { class: 'f-icon', value: item.icon || '📌' });
  const iconGrid = h('div', { class: 'icon-grid' },
    ICON_PRESETS.map((e) => h('button', { type: 'button', class: 'icon-pick', onclick: () => { iconInput.value = e; } }, e)));
  const scope = !item.gradeScope || item.gradeScope === 'all';
  const picked = new Set(scope ? [] : String(item.gradeScope).split(',').map((s) => s.trim()));
  const allCb = h('input', { type: 'checkbox', class: 'g-all', checked: scope ? 'checked' : null });
  const gradeCbs = [1, 2, 3, 4, 5, 6].map((g) => h('label', { class: 'g-item' },
    h('input', { type: 'checkbox', class: 'g-cb', 'data-g': String(g), checked: picked.has(String(g)) ? 'checked' : null }), `${g}年级`));
  return h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
    field('名称', h('input', { class: 'f-name', value: item.name || '' })),
    field('图标（点选或手输）', h('div', {}, iconInput, iconGrid)),
    field('金额（负数＝家长代记的扣分项）', h('input', { class: 'f-amount', type: 'number', value: item.amount ?? 5 })),
    field('类别', sel('f-category', [['study', '学习'], ['life', '生活']], item.category || 'study')),
    field('年级范围（M7.7：复选，全不勾＝全部）', h('div', { class: 'grade-checks' },
      h('label', { class: 'g-item' }, allCb, '全部'), ...gradeCbs)),
    field('学期', sel('f-term', [['all', '全部'], ['上', '上学期'], ['下', '下学期']], item.term || 'all')),
    field('排序（小在前）', h('input', { class: 'f-sort', type: 'number', value: item.sort ?? 0 })),
    field('启用', h('input', { class: 'f-enabled', type: 'checkbox', checked: item.enabled !== false ? 'checked' : null })));
}

function readTaskForm(node, id) {
  const all = node.querySelector('.g-all').checked;
  const chosen = [...node.querySelectorAll('.g-cb')].filter((c) => c.checked).map((c) => c.getAttribute('data-g'));
  const gradeScope = all || chosen.length === 0 || chosen.length === 6
    ? 'all' : chosen.sort((a, b) => a - b).join(',');
  const obj = {
    name: node.querySelector('.f-name').value.trim(),
    icon: node.querySelector('.f-icon').value.trim(),
    amount: Number(node.querySelector('.f-amount').value) || 0,
    category: node.querySelector('.f-category').value,
    gradeScope,
    term: node.querySelector('.f-term').value,
    sort: Number(node.querySelector('.f-sort').value) || 0,
    enabled: node.querySelector('.f-enabled').checked,
  };
  if (id !== undefined) obj.id = id;
  return obj;
}

function renderPlan(box, ctx, config, svc) {
  const { main, reload } = svc;
  const items = config.taskItems || [];

  async function addTask() {
    const node = await modal({ title: '添加任务', body: taskForm(), okText: '保存' });
    if (!node) return;
    const obj = readTaskForm(node);
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/task-items', obj))) {
      toast('任务已添加', 'ok'); reload();
    }
  }

  async function editTask(t) {
    const node = await modal({ title: `编辑任务：${t.name}`, body: taskForm(t), okText: '保存' });
    if (!node) return;
    const obj = readTaskForm(node, t.id);
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/task-items', obj))) {
      toast('任务已保存', 'ok'); reload();
    }
  }

  async function delTask(t) {
    if (!await confirmModal('删除任务', `确认删除任务「${t.name}」？`)) return;
    if (await runAdmin(ctx, main, () => ctx.api.admin('DELETE', `/task-items/${t.id}`))) {
      toast('任务已删除', 'ok'); reload();
    }
  }

  const rows = items.map((t) => h('tr', {},
    td(t.name), td(t.icon || '📌'),
    td(String(t.amount), { style: t.amount < 0 ? 'color:var(--c-expense);font-weight:700' : '' }),
    td(t.category === 'life' ? '生活' : '学习'),
    td(t.gradeScope || 'all'),
    td(t.term === 'all' ? '全部' : `${t.term}学期`),
    td(enabledPill(t.enabled)),
    td(h('div', { style: 'display:flex;gap:6px' },
      h('button', { class: 'btn ghost', onclick: () => editTask(t) }, '编辑'),
      h('button', { class: 'btn danger', onclick: () => delTask(t) }, '删除')))));

  const tbl = items.length
    ? h('div', { class: 'card' }, table(
      ['名称', '图标', '金额', '类别', '年级范围', '学期', '启用', '操作'], rows))
    : h('div', { class: 'card empty' }, '还没有任务，点右上「＋ 添加任务」');

  // 手动奖惩快捷区
  const manualNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px;max-width:520px' },
    field('档案', sel('m-profile', (config.profiles || []).map((p) => [p.id, `${p.name}（${p.grade}${p.term}）`]),
      ctx.store.s.currentProfileId)),
    field('金额（正=奖励，负=扣除）', h('input', { class: 'm-amount', type: 'number', value: 5 })),
    field('原因（≥2 字）', h('input', { class: 'm-reason', placeholder: '例如：主动整理书桌' })),
    h('button', { class: 'btn primary', onclick: doManual }, '记录这一笔'));

  async function doManual() {
    const profileId = manualNode.querySelector('.m-profile').value;
    const amount = Number(manualNode.querySelector('.m-amount').value) || 0;
    const reason = manualNode.querySelector('.m-reason').value.trim();
    if (!reason || reason.length < 2) { toast('原因至少 2 个字', 'err'); return; }
    if (amount === 0) { toast('金额不能为 0', 'err'); return; }
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/manual-tx', { profileId, amount, reason }));
    if (r) {
      toast(`已记录，余额 ${r.balance}🪙`, 'ok');
      manualNode.querySelector('.m-reason').value = '';
      if (String(profileId) === String(ctx.store.s.currentProfileId)) ctx.store.refreshCoins().catch(() => {});
    }
  }

  // ── 数据修正（M7.6/FR-26）：近 50 条流水，逐条"冲正+补录"改数 ──
  const TYPE_CN = {
    daily_checkin: '签到', task: '任务', textbook: '教材', redeem: '兑换', redeem_refund: '兑换退款',
    reverse: '冲正', screen_over: '屏幕超时', habit_milestone: '习惯奖励', manual: '手动',
  };
  const txBox = h('div', { class: 'card' }, h('div', { class: 'empty' }, '流水加载中…'));

  async function loadTxs() {
    const pid = ctx.store.s.currentProfileId;
    const txs = await runAdmin(ctx, main, () => ctx.api.admin('GET', `/profiles/${pid}/txs`));
    if (!txs) return;
    txBox.innerHTML = '';
    if (!txs.length) {
      txBox.append(h('div', { class: 'empty' }, '这个档案还没有金币流水'));
      return;
    }
    const rows = txs.map((t) => {
      const canAdjust = !t.revoked && !['reverse', 'redeem', 'redeem_refund'].includes(t.type);
      return h('tr', {},
        td(t.dayKey),
        td(TYPE_CN[t.type] || t.type),
        td(`${t.amount > 0 ? '+' : '−'}${Math.abs(t.amount)}🪙`,
          { style: t.amount < 0 ? 'color:var(--c-expense);font-weight:700' : 'color:var(--c-income);font-weight:700' }),
        td(t.reason || '—'),
        td(t.revoked ? '已冲正' : '有效'),
        td(canAdjust
          ? h('button', { class: 'btn ghost', onclick: () => adjustTx(t) }, '✏️ 修正')
          : h('span', { style: 'color:var(--c-sub);font-size:.85rem' },
            ['redeem', 'redeem_refund'].includes(t.type) && !t.revoked ? '请用商店撤销' : '—')));
    });
    txBox.append(h('p', { style: 'color:var(--c-sub);margin-bottom:8px' },
      '修正＝冲正原流水 + 补录新手动流水（成对记账，原流水永不改写）'),
    table(['日期', '类型', '金额', '原因', '状态', '操作'], rows));
  }

  async function adjustTx(t) {
    const node = h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
      h('p', { style: 'color:var(--c-sub)' }, `这笔「${TYPE_CN[t.type] || t.type}」是 ${t.amount}🪙（${t.dayKey}）`),
      field('新金额（≠0，±1000 内）', h('input', { class: 'a-amount', type: 'number', value: t.amount })),
      field('修正原因（≥2 字）', h('input', { class: 'a-reason', placeholder: '例如：口算实际全对，应该给 5 分' })));
    const go = await modal({ title: '✏️ 修正这笔流水', body: node, okText: '冲正并补录', danger: true });
    if (!go) return;
    const newAmount = Number(node.querySelector('.a-amount').value) || 0;
    const reason = node.querySelector('.a-reason').value.trim();
    if (newAmount === 0) { toast('新金额不能为 0', 'err'); return; }
    if (reason.length < 2) { toast('修正原因至少 2 个字', 'err'); return; }
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/txs/adjust', { txId: t.id, newAmount, reason }));
    if (r) {
      toast(`已修正：冲正 ${-t.amount} + 补录 ${newAmount}，余额 ${r.balance}🪙`, 'ok');
      loadTxs();
      ctx.store.refreshCoins().catch(() => {});
    }
  }
  loadTxs();

  box.append(
    subBox('📋 每日计划任务', h('button', { class: 'btn primary', onclick: addTask }, '＋ 添加任务')),
    tbl,
    h('div', { class: 'section-title' }, '⚡ 家长代记（手动奖惩）'),
    h('div', { class: 'card' }, manualNode),
    h('div', { class: 'section-title' }, '🧾 数据修正（调整已积分的流水）'),
    txBox);
}

// ════════════════════════════════════════════════════
// Tab 3 · 商店（商品 CRUD + 待办兑现）
// ════════════════════════════════════════════════════
function shopForm(item = {}) {
  return h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
    field('名称', h('input', { class: 'f-name', value: item.name || '' })),
    field('图标（emoji）', h('input', { class: 'f-icon', value: item.icon || '🎁' })),
    field('价格（金币）', h('input', { class: 'f-price', type: 'number', value: item.price ?? 20 })),
    field('库存（unlimited 或数字）', h('input', { class: 'f-stock', value: item.stock || 'unlimited' })),
    field('上架', h('input', { class: 'f-enabled', type: 'checkbox', checked: item.enabled !== false ? 'checked' : null })));
}

function readShopForm(node, id) {
  const obj = {
    name: node.querySelector('.f-name').value.trim(),
    icon: node.querySelector('.f-icon').value.trim(),
    price: Number(node.querySelector('.f-price').value) || 0,
    stock: node.querySelector('.f-stock').value.trim() || 'unlimited',
    enabled: node.querySelector('.f-enabled').checked,
  };
  if (id !== undefined) obj.id = id;
  return obj;
}

function statusPill(r) {
  const map = { pending: ['#fff3cd', '#856404'], redeemed: ['#d9f5e3', '#1a7a3c'], cancelled: ['#ececec', '#777'] };
  const [bg, fg] = map[r.status] || ['#f4ede2', '#8A7A66'];
  return h('span', { class: 'pill', style: `background:${bg};color:${fg}` }, r.statusLabel || r.status);
}

function renderShop(box, ctx, config, svc) {
  const { main, reload } = svc;
  const shopItems = config.shopItems || [];
  const pending = (config.redemptionsPending || []).filter((r) => r.status === 'pending');
  const history = (config.redemptionsPending || []).filter((r) => r.status !== 'pending');

  async function addItem() {
    const node = await modal({ title: '添加商品', body: shopForm(), okText: '保存' });
    if (!node) return;
    const obj = readShopForm(node);
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/shop-items', obj))) { toast('商品已添加', 'ok'); reload(); }
  }
  async function editItem(it) {
    const node = await modal({ title: `编辑商品：${it.name}`, body: shopForm(it), okText: '保存' });
    if (!node) return;
    const obj = readShopForm(node, it.id);
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/shop-items', obj))) { toast('商品已保存', 'ok'); reload(); }
  }
  async function delItem(it) {
    if (!await confirmModal('删除商品', `确认删除商品「${it.name}」？`)) return;
    if (await runAdmin(ctx, main, () => ctx.api.admin('DELETE', `/shop-items/${it.id}`))) { toast('商品已删除', 'ok'); reload(); }
  }

  const rows = shopItems.map((it) => h('tr', {},
    td(it.name), td(it.icon || '🎁'), td(`${it.price}🪙`),
    td(it.stock === 'unlimited' ? '不限量' : String(it.stock)),
    td(enabledPill(it.enabled)),
    td(h('div', { style: 'display:flex;gap:6px' },
      h('button', { class: 'btn ghost', onclick: () => editItem(it) }, '编辑'),
      h('button', { class: 'btn danger', onclick: () => delItem(it) }, '删除')))));

  const tbl = shopItems.length
    ? h('div', { class: 'card' }, table(['名称', '图标', '价格', '库存', '上架', '操作'], rows))
    : h('div', { class: 'card empty' }, '还没有商品');

  // 待办兑现
  const todoCard = h('div', { class: 'card' });
  if (!pending.length) {
    todoCard.append(h('div', { class: 'empty' }, '没有等待兑现的兑换单 🎉'));
  } else {
    for (const r of pending) {
      todoCard.append(h('div', { class: 'tb-leaf' },
        h('span', { style: 'font-size:1.4rem' }, r.icon || '🎁'),
        h('span', { class: 't' }, `${r.profileName || ''}·${r.itemName}`,
          h('span', { style: 'color:var(--c-sub);margin-left:8px' }, `${r.price}🪙`)),
        statusPill(r),
        h('button', { class: 'btn primary', style: 'margin-left:8px', onclick: () => confirmRedeem(r) }, '标已兑现'),
        h('button', { class: 'btn danger', onclick: () => cancelRedeem(r) }, '撤销退款')));
    }
  }

  async function confirmRedeem(r) {
    if (!await confirmModal('标记已兑现', `确认已把「${r.itemName}」交给 ${r.profileName || '孩子'}？`)) return;
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', `/redemptions/${r.id}/confirm`, {}))) { toast('已标记兑现', 'ok'); reload(); }
  }
  async function cancelRedeem(r) {
    if (!await confirmModal('撤销并退款', `撤销后金币将退回 ${r.profileName || '孩子'} 的余额，确认？`)) return;
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', `/redemptions/${r.id}/cancel`, {}))) { toast('已撤销并退款', 'ok'); reload(); }
  }

  const histCard = h('div', { class: 'card' });
  if (!history.length) {
    histCard.append(h('div', { style: 'color:var(--c-sub)' }, '暂无历史记录'));
  } else {
    for (const r of history) {
      histCard.append(h('div', { class: 'tb-leaf' },
        h('span', { class: 't' }, `${r.profileName || ''}·${r.itemName}`),
        statusPill(r)));
    }
  }

  box.append(
    subBox('🛒 商店商品', h('button', { class: 'btn primary', onclick: addItem }, '＋ 添加商品')),
    tbl,
    h('div', { class: 'section-title' }, '⏳ 待办兑现'), todoCard,
    h('div', { class: 'section-title' }, '📜 兑换历史'), histCard);
}

// ════════════════════════════════════════════════════
// Tab 4 · 生活设置（签到/作息/屏幕/等级阈值 + 习惯）
// ════════════════════════════════════════════════════
function parseObj(v, fallback = {}) {
  if (v && typeof v === 'object') return v;
  if (typeof v === 'string') { try { return JSON.parse(v) || fallback; } catch { return fallback; } }
  return fallback;
}

const DEFAULT_LIFE = {
  sleepTarget: '21:30', sleepGraceMin: 15, wakeTarget: '07:00', wakeGraceMin: 15,
  sleepCoin: 5, wakeCoin: 5, screenDailyMinutes: 40, screenWeeklyBankCap: 120, screenOverPenalty: 10,
};

function numInput(cls, val, extra = {}) {
  return h('input', { class: cls, type: 'number', value: val ?? '', ...extra });
}

function habitForm(item = {}) {
  const mr = parseObj(item.milestoneRewards, { 7: 10, 14: 20, 21: 50 });
  return h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
    field('名称', h('input', { class: 'f-name', value: item.name || '' })),
    field('图标（emoji）', h('input', { class: 'f-icon', value: item.icon || '🌟' })),
    field('周期（天）', numInput('f-cycle', item.cycleDays ?? 7)),
    field('里程碑奖励 JSON（如 {"7":10,"14":20,"21":50}）',
      h('textarea', { class: 'f-mile', rows: 3, style: 'font-family:monospace' }, JSON.stringify(mr))),
    field('启用', h('input', { class: 'f-enabled', type: 'checkbox', checked: item.enabled !== false ? 'checked' : null })));
}

function readHabitForm(node, id) {
  const mileText = node.querySelector('.f-mile').value.trim();
  let mile;
  if (mileText) {
    try { mile = JSON.parse(mileText); } catch { return { error: '里程碑奖励不是合法 JSON' }; }
  }
  const obj = {
    name: node.querySelector('.f-name').value.trim(),
    icon: node.querySelector('.f-icon').value.trim(),
    cycleDays: Number(node.querySelector('.f-cycle').value) || 7,
    enabled: node.querySelector('.f-enabled').checked,
  };
  if (mile !== undefined) obj.milestoneRewards = mile;
  if (id !== undefined) obj.id = id;
  return obj;
}

function renderLife(box, ctx, config, svc) {
  const { main, reload } = svc;
  const st = config.settings || {};
  const life = parseObj(st.lifeSettings, DEFAULT_LIFE);
  const thresholds = Array.isArray(st.levelThresholds)
    ? st.levelThresholds.join(',')
    : parseObj(st.levelThresholds, []);
  const thresholdText = Array.isArray(thresholds) ? thresholds.join(',') : String(st.levelThresholds || '');

  // 设置表单
  const sNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
    h('div', { class: 'grid cols2' },
      field('每日签到奖励', numInput('s-checkin', st.checkinAmount ?? 20)),
      field('教材打卡奖励', numInput('s-textbook', st.textbookCoinValue ?? 5))),
    h('div', { class: 'section-title', style: 'margin:8px 0 0' }, '🛏️ 作息窗口'),
    h('div', { class: 'grid cols2' },
      field('睡觉目标', h('input', { class: 's-sleept', type: 'time', value: life.sleepTarget || '21:30' })),
      field('起床目标', h('input', { class: 's-waket', type: 'time', value: life.wakeTarget || '07:00' })),
      field('睡觉宽限（分钟）', numInput('s-sleepg', life.sleepGraceMin ?? 15)),
      field('起床宽限（分钟）', numInput('s-wakeg', life.wakeGraceMin ?? 15)),
      field('睡觉达成奖励', numInput('s-sleepc', life.sleepCoin ?? 5)),
      field('起床达成奖励', numInput('s-wakec', life.wakeCoin ?? 5))),
    h('div', { class: 'section-title', style: 'margin:8px 0 0' }, '📱 屏幕额度'),
    h('div', { class: 'grid cols2' },
      field('每日上限（分钟）', numInput('s-daily', life.screenDailyMinutes ?? 40)),
      field('每周额度池上限（分钟）', numInput('s-bankcap', life.screenWeeklyBankCap ?? 120)),
      field('超时每分钟扣币', numInput('s-over', life.screenOverPenalty ?? 10))),
    h('div', { class: 'section-title', style: 'margin:8px 0 0' }, '🌳 等级阈值（逗号分隔数字）'),
    field('阈值序列', h('input', { class: 's-thr', placeholder: '0,100,300,600,1000', value: thresholdText })),
    h('button', { class: 'btn primary', style: 'align-self:flex-start', onclick: saveSettings }, '保存设置'));

  async function saveSettings() {
    const thrRaw = sNode.querySelector('.s-thr').value.trim();
    const levelThresholds = thrRaw
      ? thrRaw.split(',').map((x) => Number(x.trim())).filter((x) => !Number.isNaN(x))
      : [];
    const payload = {
      checkinAmount: Number(sNode.querySelector('.s-checkin').value) || 0,
      textbookCoinValue: Number(sNode.querySelector('.s-textbook').value) || 0,
      levelThresholds,
      lifeSettings: {
        sleepTarget: sNode.querySelector('.s-sleept').value,
        wakeTarget: sNode.querySelector('.s-waket').value,
        sleepGraceMin: Number(sNode.querySelector('.s-sleepg').value) || 0,
        wakeGraceMin: Number(sNode.querySelector('.s-wakeg').value) || 0,
        sleepCoin: Number(sNode.querySelector('.s-sleepc').value) || 0,
        wakeCoin: Number(sNode.querySelector('.s-wakec').value) || 0,
        screenDailyMinutes: Number(sNode.querySelector('.s-daily').value) || 0,
        screenWeeklyBankCap: Number(sNode.querySelector('.s-bankcap').value) || 0,
        screenOverPenalty: Number(sNode.querySelector('.s-over').value) || 0,
      },
    };
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/settings', payload))) {
      toast('生活设置已保存', 'ok');
    }
  }

  // 习惯管理
  const habits = config.habits || [];
  async function addHabit() {
    const node = await modal({ title: '添加习惯', body: habitForm(), okText: '保存' });
    if (!node) return;
    const obj = readHabitForm(node);
    if (obj.error) { toast(obj.error, 'err'); return; }
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/habits', obj))) { toast('习惯已添加', 'ok'); reload(); }
  }
  async function editHabit(hb) {
    const node = await modal({ title: `编辑习惯：${hb.name}`, body: habitForm(hb), okText: '保存' });
    if (!node) return;
    const obj = readHabitForm(node, hb.id);
    if (obj.error) { toast(obj.error, 'err'); return; }
    if (!obj.name) { toast('名称不能为空', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/habits', obj))) { toast('习惯已保存', 'ok'); reload(); }
  }
  async function delHabit(hb) {
    if (!await confirmModal('删除习惯', `确认删除习惯「${hb.name}」？`)) return;
    if (await runAdmin(ctx, main, () => ctx.api.admin('DELETE', `/habits/${hb.id}`))) { toast('习惯已删除', 'ok'); reload(); }
  }

  const hRows = habits.map((hb) => {
    const mr = parseObj(hb.milestoneRewards, {});
    return h('tr', {},
      td(hb.name), td(hb.icon || '🌟'),
      td(`${hb.cycleDays ?? ''} 天`),
      td(h('code', { style: 'font-size:.82rem' }, JSON.stringify(mr))),
      td(enabledPill(hb.enabled)),
      td(h('div', { style: 'display:flex;gap:6px' },
        h('button', { class: 'btn ghost', onclick: () => editHabit(hb) }, '编辑'),
        h('button', { class: 'btn danger', onclick: () => delHabit(hb) }, '删除'))));
  });

  const habitTbl = habits.length
    ? h('div', { class: 'card' }, table(['名称', '图标', '周期', '里程碑奖励', '启用', '操作'], hRows))
    : h('div', { class: 'card empty' }, '还没有习惯');

  // 屏幕额度发放
  const grantNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px;max-width:520px' },
    field('档案', sel('g-profile', (config.profiles || []).map((p) => [p.id, `${p.name}（${p.grade}${p.term}）`]),
      ctx.store.s.currentProfileId)),
    field('额外发放分钟（1–120）', numInput('g-min', 15, { min: 1, max: 120 })),
    field('原因', h('input', { class: 'g-reason', placeholder: '例如：本周阅读表现好' })),
    h('button', { class: 'btn primary', onclick: doGrant }, '发放屏幕额度'));

  async function doGrant() {
    const profileId = grantNode.querySelector('.g-profile').value;
    const minutes = Number(grantNode.querySelector('.g-min').value) || 0;
    const reason = grantNode.querySelector('.g-reason').value.trim();
    if (minutes < 1 || minutes > 120) { toast('发放分钟需在 1–120 之间', 'err'); return; }
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/screen/bank-grant', { profileId, minutes, reason }));
    if (r) toast(`已发放 ${r.grantedMinutes} 分钟，当前可用 ${r.available} 分钟`, 'ok');
  }

  box.append(
    h('div', { class: 'section-title' }, '⚙️ 奖励与阈值'), h('div', { class: 'card' }, sNode),
    subBox('🌟 习惯管理', h('button', { class: 'btn primary', onclick: addHabit }, '＋ 添加习惯')), habitTbl,
    h('div', { class: 'section-title' }, '🎁 手动发放屏幕额度'), h('div', { class: 'card' }, grantNode));
}

// ════════════════════════════════════════════════════
// Tab 5 · 数据与题库（改 PIN / 自定义叶 / 题库导入 / 危险区）
// ════════════════════════════════════════════════════
const SUBJECTS = [['cn', '语文'], ['math', '数学'], ['en', '英语']];

const IMPORT_EXAMPLE = `{
  "leafId": "cust_xxx",
  "subject": "math",
  "quizzes": [
    { "type": "single", "stem": "3 + 4 = ?", "options": ["6", "7", "8"], "answer": "7", "explanation": "3 加 4 等于 7" },
    { "type": "blank", "stem": "5 × 6 = ___", "answer": "30", "explanation": "五乘法口诀" }
  ]
}`;

function renderData(box, ctx, config, svc) {
  const { main, reload } = svc;

  // 改 PIN
  const pinIn = h('input', { class: 'p-pin', type: 'text', inputmode: 'numeric', maxlength: 4, placeholder: '4 位数字' });
  const pinNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px;max-width:360px' },
    field('新的家长 PIN（4 位数字）', pinIn),
    h('button', { class: 'btn primary', onclick: updatePin }, '更新 PIN'));

  async function updatePin() {
    const pin = pinIn.value.trim();
    if (!/^\d{4}$/.test(pin)) { toast('请输入恰好 4 位数字', 'err'); return; }
    if (await runAdmin(ctx, main, () => ctx.api.admin('POST', '/pin', { pin }))) {
      toast('PIN 已更新', 'ok');
      pinIn.value = '';
    }
  }

  // 自定义知识点叶
  const leafNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px;max-width:520px' },
    field('学科', sel('l-subject', SUBJECTS, 'math')),
    field('年级', sel('l-grade', GRADES.map((g) => [String(g), `${g} 年级`]), ctx.store.s.grade || 1)),
    field('学期', sel('l-term', [['上', '上学期'], ['下', '下学期']], ctx.store.s.term || '上')),
    field('标题', h('input', { class: 'l-title', placeholder: '例如：认识负数' })),
    h('button', { class: 'btn primary', onclick: addLeaf }, '创建自定义叶'));

  async function addLeaf() {
    const title = leafNode.querySelector('.l-title').value.trim();
    if (!title) { toast('标题不能为空', 'err'); return; }
    const payload = {
      profileId: ctx.store.s.currentProfileId,
      subject: leafNode.querySelector('.l-subject').value,
      grade: +leafNode.querySelector('.l-grade').value,
      term: leafNode.querySelector('.l-term').value,
      kind: 'leaf',
      title,
    };
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/nodes', payload));
    if (r) { toast(`已创建自定义叶：${r.id}`, 'ok'); leafNode.querySelector('.l-title').value = ''; }
  }

  // 题库导入
  const ta = h('textarea', { class: 'q-json', rows: 12, placeholder: IMPORT_EXAMPLE, style: 'font-family:monospace;width:100%' });
  const importNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px' }, ta,
    h('button', { class: 'btn primary', style: 'align-self:flex-start', onclick: doImport }, '导入题库'));

  async function doImport() {
    let obj;
    try {
      obj = JSON.parse(ta.value);
    } catch {
      toast('JSON 语法错误', 'err');
      return;
    }
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', '/quiz/import', obj));
    if (r) toast(`已导入 ${r.imported} 题`, 'ok');
  }

  // 危险区
  const dPairs = [['', '请选择档案…'], ...(config.profiles || []).map((p) => [p.id, `${p.name}（${p.grade}${p.term}）`])];
  const dSel = sel('d-profile', dPairs, '');
  const dWord = h('input', { class: 'd-word', placeholder: '输入档案名以确认' });
  const dangerNode = h('div', { style: 'display:flex;flex-direction:column;gap:12px' },
    h('p', { style: 'color:var(--c-expense);font-weight:700' }, '⚠️ 以下操作不可恢复，请先选择档案并输入其准确名字'),
    field('选择档案', dSel),
    field('确认词', dWord),
    h('div', { style: 'display:flex;gap:10px;flex-wrap:wrap' },
      h('button', { class: 'btn danger', onclick: () => dangerAction('reset') }, '重置该档案数据'),
      h('button', { class: 'btn danger', onclick: () => dangerAction('delete') }, '删除整个档案')));

  function currentProfile() {
    const id = dSel.value;
    return (config.profiles || []).find((p) => String(p.id) === String(id));
  }

  async function dangerAction(kind) {
    const p = currentProfile();
    if (!p) { toast('请先选择档案', 'err'); return; }
    const word = dWord.value.trim();
    if (word !== p.name) { toast('确认词与档案名不一致', 'err'); return; }
    const isDelete = kind === 'delete';
    const label = isDelete ? `彻底删除「${p.name}」及其全部数据` : `清空「${p.name}」的金币/计划/进度（保留档案本身）`;
    if (!await confirmModal(isDelete ? '危险：删除档案' : '危险：重置档案', `${label}。确定吗？此操作不可撤销。`)) return;
    const path = isDelete ? '/danger/delete-profile' : '/danger/reset-profile';
    const r = await runAdmin(ctx, main, () => ctx.api.admin('POST', path, { profileId: p.id, confirmWord: word }));
    if (r) {
      toast(isDelete ? '档案已删除' : '档案数据已重置', 'ok');
      dWord.value = '';
      try { await ctx.store.refresh(); } catch { /* 忽略 */ }
      if (String(p.id) === String(ctx.store.s.currentProfileId) && isDelete) {
        window.dispatchEvent(new Event('app:refresh'));
      } else {
        reload();
      }
    }
  }

  box.append(
    h('div', { class: 'section-title' }, '🔑 修改家长 PIN'), h('div', { class: 'card' }, pinNode),
    h('div', { class: 'section-title' }, '🌿 自定义知识点叶'), h('div', { class: 'card' }, leafNode),
    h('div', { class: 'section-title' }, '📚 题库 JSON 导入'),
    h('div', { class: 'card' }, importNode),
    h('div', { class: 'section-title' }, '🧨 危险区'),
    h('div', { class: 'card danger-zone' }, dangerNode));
}
