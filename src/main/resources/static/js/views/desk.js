// P1 学习台（docs/04 §2 P1）：金币柱状图 + 今日计划 + 连续/完成率悬浮卡 + 常显四进度 + 统计报表（M10 v2.2）。
import { h, toast, floatCoin, evidenceSheet } from '../ui.js';

const M = { day: '日', month: '月', year: '年' };

export async function render(main, ctx) {
  main.innerHTML = '';
  const prog = progressSection(ctx);
  main.append(heroSection(ctx), prog.box, chartSection(ctx), statsSection(ctx, prog.refresh), planSection(ctx));
}

// ── 常显四进度卡（FR-25 / 需求3：奥数即使 0% 也常显）──────

function progressSection(ctx) {
  const box = h('div', { class: 'card prog-card' });
  const bars = [
    ['📖 语文', 'cn', 'schoolPercent'],
    ['🧮 数学', 'math', 'schoolPercent'],
    ['🏅 奥数', 'math', 'olympiadPercent'],
    ['🅰️ 英语', 'en', 'schoolPercent'],
  ];
  function refresh(progress) {
    box.innerHTML = '';
    box.append(h('b', {}, '📈 课本进度'),
      h('div', { class: 'prog-grid' }, ...bars.map(([label, key, field]) => {
        const p = Math.max(0, Math.min(100, Math.round(Number(progress?.[key]?.[field]) || 0)));
        return h('div', { class: 'prog-item' },
          h('div', { class: 'prog-label' }, label, h('span', {}, `${p}%`)),
          h('div', { class: 'progress-line' }, h('i', { style: `width:${p}%` })));
      })));
  }
  refresh(ctx.store.s.progress);
  return { box, refresh };
}

// ── 右上悬浮卡：火焰 + 双线完成率环 ────────────────

function heroSection(ctx) {
  const box = h('div', { class: 'card hero-card', id: 'hero' }, h('span', { class: 'empty' }, '…'));
  loadHero(box, ctx);
  return box;
}

async function loadHero(box, ctx) {
  const { store } = ctx;
  const coins = store.s.coins || {};
  let weekly = { study: { done: 0, total: 0 }, life: { done: 0, total: 0 } };
  try { weekly = await ctx.api.get('/plans/weekly'); } catch { /* 后端未就绪时降级 */ }
  box.innerHTML = '';
  box.append(
    h('div', {}, h('div', { style: 'font-size:1.5rem' }, `🔥 连续 ${coins.streak ?? 0} 天`),
      h('div', { style: 'color:var(--c-sub)' }, `${coins.levelName ?? ''} · 距下一级还差 ${coins.toNextLevel ?? 0}🪙`)),
    ring('学习线', weekly.study, '#FF8A3D'),
    ring('生活线', weekly.life, '#34C759'));
}

function ring(label, v, color) {
  const rate = v.total ? Math.min(1, v.done / v.total) : 0;
  const r = 26, c = 2 * Math.PI * r;
  const svg = h('svg', { width: 68, height: 68, viewBox: '0 0 68 68' },
    h('circle', { cx: 34, cy: 34, r, fill: 'none', stroke: '#f1e7d6', 'stroke-width': 8 }),
    h('circle', {
      cx: 34, cy: 34, r, fill: 'none', stroke: color, 'stroke-width': 8,
      'stroke-dasharray': `${(rate * c).toFixed(1)} ${c.toFixed(1)}`, 'stroke-linecap': 'round',
      transform: 'rotate(-90 34 34)',
    }),
    h('text', { x: 34, y: 39, 'text-anchor': 'middle', style: 'font-size:13px;font-weight:700' }, `${Math.round(rate * 100)}%`));
  return h('div', { style: 'text-align:center' }, svg, h('div', { style: 'font-size:.85rem;color:var(--c-sub)' }, label));
}

// ── 金币柱状图（手写 SVG，ADR-6）──────────────────

function chartSection(ctx) {
  const svgBox = h('div', { id: 'chart-svg' });
  let period = 'day';
  const btns = Object.keys(M).map((p) => h('button', {
    class: `btn ghost ${p === period ? 'on' : ''}`,
    onclick: (e) => { period = p; btns.forEach((b) => b.classList.remove('primary')); e.target.classList.add('primary'); draw(); },
  }, M[p]));
  btns[0].classList.add('primary');
  const wrap = h('div', { class: 'card chart-wrap' },
    h('div', { style: 'display:flex;justify-content:space-between;align-items:center' },
      h('b', {}, '金币收支'), h('div', { class: 'chart-toggle' }, ...btns)),
    svgBox);
  async function draw() {
    svgBox.innerHTML = '';
    const buckets = await ctx.api.get(`/coins/stats?period=${period}`);
    svgBox.append(bars(buckets, period));
  }
  draw();
  return wrap;
}

function bars(buckets, period) {
  const W = 900, H = 180, pad = 6;
  const max = Math.max(10, ...buckets.map((b) => Math.max(b.income, b.expense)));
  const bw = (W - pad * 2) / buckets.length;
  const svg = h('svg', { class: 'bars', viewBox: `0 0 ${W} ${H}` });
  buckets.forEach((b, i) => {
    const x = pad + i * bw;
    const hi = (b.income / max) * (H - 34);
    const ho = (b.expense / max) * (H - 34);
    const g = h('g', {},
      b.income ? h('rect', { class: 'bar-in', x: x + bw * .14, y: H - 22 - hi, width: bw * .3, height: hi, rx: 2 }) : null,
      b.expense ? h('rect', { class: 'bar-out', x: x + bw * .5, y: H - 22 - ho, width: bw * .3, height: ho, rx: 2 }) : null,
      (period !== 'year' && i % Math.ceil(buckets.length / 8) === 0) || period === 'year'
        ? h('text', { x: x + bw / 2, y: H - 6, 'text-anchor': 'middle' }, b.label.slice(period === 'day' ? 5 : 0, period === 'year' ? 4 : 7)) : null);
    const tip = h('title', {}, `${b.label}｜收 ${b.income} · 支 ${b.expense}`);
    g.prepend(tip);
    svg.append(g);
  });
  return svg;
}

// ── 数据统计报表（M10 v2.2 / 需求5）──────────────────

const MASTERY_CN = { proficient: '熟练', mastered: '掌握', partial: '部分', weak: '薄弱', unanswered: '未测', needs_retest: '待重测' };
const MASTERY_COLOR = { proficient: '#1a7a3c', mastered: '#34C759', partial: '#b8860b', weak: '#d9534f', unanswered: '#999', needs_retest: '#e08a2e' };

function statsSection(ctx, refreshProgress) {
  let period = 'week';
  const body = h('div', { class: 'empty' }, '报表加载中…');
  const btns = Object.entries({ week: '近 7 天', month: '近 30 天' }).map(([p, label]) => h('button', {
    class: `btn ghost ${p === period ? 'primary' : ''}`,
    onclick: (e) => { period = p; btns.forEach((b) => b.classList.remove('primary')); e.target.classList.add('primary'); draw(); },
  }, label));
  const wrap = h('div', { class: 'card stats-card' },
    h('div', { style: 'display:flex;justify-content:space-between;align-items:center;margin-bottom:8px' },
      h('b', {}, '📊 数据统计'), h('div', { class: 'chart-toggle' }, ...btns)),
    body);
  async function draw() {
    body.innerHTML = '';
    body.append(h('div', { class: 'empty' }, '报表加载中…'));
    let r;
    try {
      r = await ctx.api.get(`/stats/report?period=${period}`);
    } catch (e) {
      body.innerHTML = '';
      body.append(h('div', { class: 'empty' }, `报表暂不可用：${e.message}`));
      return;
    }
    body.innerHTML = '';
    if (refreshProgress) refreshProgress(r.textbook);
    body.append(coinBlock(r.coins), planBlock(r.plan), quizBlock(r.quiz), lifeBlock(r.life));
  }
  draw();
  return wrap;
}

function coinBlock(c) {
  const src = Object.entries(c.bySource || {});
  const maxAbs = Math.max(1, ...src.map(([, v]) => Math.abs(v)));
  return h('div', { class: 'stats-block' },
    h('div', { class: 'stats-nums' },
      statNum('收入', `+${c.income}🪙`), statNum('支出', `−${c.expense}🪙`),
      statNum('净收支', `${c.net >= 0 ? '+' : '−'}${Math.abs(c.net)}🪙`), statNum('余额', `${c.balance}🪙`)),
    src.length ? h('div', { class: 'stats-bars' }, ...src.map(([k, v]) =>
      h('div', { class: 'stats-bar' },
        h('span', { class: 'sb-label' }, k),
        h('span', { class: `sb-track ${v >= 0 ? 'in' : 'out'}` },
          h('i', { style: `width:${Math.round(Math.abs(v) / maxAbs * 100)}%` })),
        h('span', { class: 'sb-val' }, `${v > 0 ? '+' : ''}${v}`)))) : null);
}

function statNum(label, val) {
  return h('div', { class: 'stat-num' }, h('b', {}, val), h('span', {}, label));
}

function planBlock(p) {
  const rate = p.total ? Math.round(p.done / p.total * 100) : 0;
  return h('div', { class: 'stats-block' },
    h('div', { class: 'stats-head' }, h('b', {}, '✅ 计划完成'), h('span', { class: 'pill' }, `${p.done}/${p.total} · ${rate}%`)),
    h('div', { class: 'progress-line' }, h('i', { style: `width:${rate}%` })));
}

function quizBlock(q) {
  const mastery = Object.entries(q.mastery || {}).filter(([, n]) => n > 0);
  return h('div', { class: 'stats-block' },
    h('div', { class: 'stats-head' },
      h('b', {}, '🎯 自测'),
      h('span', { class: 'pill' }, `${q.rounds} 轮 · 平均 ${q.avgRate}% · 共 ${q.leafTotal} 知识点`)),
    mastery.length ? h('div', { class: 'mastery-chips' }, ...mastery.map(([k, n]) =>
      h('span', { class: 'm-chip', style: `background:${MASTERY_COLOR[k] || '#999'}` }, `${MASTERY_CN[k] || k} ${n}`))) : null);
}

function lifeBlock(l) {
  return h('div', { class: 'stats-block' },
    h('div', { class: 'stats-head' }, h('b', {}, '🌱 生活')),
    h('div', { class: 'stats-nums' },
      statNum('按时作息', `${l.sleepOnTime}/${l.sleepRecords} 次`),
      statNum('屏幕', `${l.screenMinutes} 分钟`),
      statNum('习惯打卡', `${l.habitDone} 次`)));
}

// ── 今日计划清单 ─────────────────────────

function planSection(ctx) {
  const box = h('div', { id: 'plan-box' }, h('div', { class: 'empty' }, '加载今日计划…'));
  loadPlan(box, ctx);
  return box;
}

async function loadPlan(box, ctx) {
  const { store, api } = ctx;
  const pid = store.s.currentProfileId;
  let view;
  try {
    view = await api.get('/plans/today');
  } catch (e) {
    box.innerHTML = '';
    box.append(h('div', { class: 'card empty' }, e.message));
    return;
  }
  const items = (view.items || []).filter((t) => t.amount >= 0);
  box.innerHTML = '';
  const list = h('div', { class: 'grid cols2' });
  if (!items.length) {
    list.append(h('div', { class: 'card empty' },
      '还没有赚金币的任务哦', h('div', {},
        h('button', { class: 'btn primary', style: 'margin-top:12px', onclick: () => ctx.ui.go('admin') }, '请爸爸妈妈去后台配置'))));
  }
  for (const t of items) {
    const card = h('div', { class: `card task-card ${t.done ? 'done' : ''} ${t.pending && !t.done ? 'pend' : ''}` },
      h('span', { class: 'icon' }, t.icon || '📌'),
      h('span', { class: 'name' }, t.name, t.pending && !t.done ? h('span', { class: 'pill', style: 'margin-left:6px' }, '⏳ 待审核') : null),
      h('span', { class: 'amount' }, `+${t.amount}🪙`),
      h('span', { class: 'tick' }));
    card.style.cursor = 'pointer';
    card.onclick = async () => {
      card.style.pointerEvents = 'none';
      try {
        // v2.3/M11.3：半透明待审勾再点 = 撤回申请
        if (card.classList.contains('pend')) {
          const r = await api.post('/plans/check', { profileId: pid, taskItemId: t.taskId });
          if (r.state === 'pending' && !r.done) {
            card.classList.remove('pend');
            const pill = card.querySelector('.pill'); if (pill) pill.remove();
            toast(`「${t.name}」的申请已撤回`, '');
            await store.refreshCoins();
          }
          return;
        }
        // v2.4/FR-32：审批启用时，加分勾选先弹 📸 佐证层（照片可选）
        let extra = {};
        if (!card.classList.contains('done') && store.s.approvalEnabled && t.amount > 0) {
          const ev = await evidenceSheet(api, {
            title: `提交「${t.name}」`,
            subtitle: `+${t.amount}🪙 · 家长确认后到账`,
            kind: 'task',
          });
          if (!ev) return; // 取消
          extra = { photoIds: ev.photoIds, note: ev.note };
        }
        const r = await api.post('/plans/check', { profileId: pid, taskItemId: t.taskId, ...extra });
        if (r.state === 'pending') {
          card.classList.add('pend');
          card.querySelector('.name').append(h('span', { class: 'pill', style: 'margin-left:6px' }, '⏳ 待审核'));
          floatCoin(card, '⏳ 已提交');
          toast('⏳ 已提交，待家长确认', 'ok');
        } else {
          // 家长修正过的任务取消时后端不再二次冲正，tx 为 null（M7.6）。
          if (r.tx) floatCoin(card, r.done ? `+${r.tx.amount}🪙` : `-${Math.abs(r.tx.amount)}🪙`);
          card.classList.toggle('done', r.done);
          toast(r.done ? `${t.name} 完成！` : `${t.name} 已取消`, r.done ? 'ok' : '');
        }
        await store.refreshCoins();
      } catch (e) { toast(e.message, 'err'); } finally { card.style.pointerEvents = ''; }
    };
    list.append(card);
  }
  box.append(h('div', { class: 'section-title' }, '📋 今日计划'), list);
  const ded = (view.deductedToday || []);
  if (ded.length) {
    box.append(h('div', { class: 'section-title' }, '今天被扣了'),
      ...ded.map((d) => h('div', { class: 'deduct-line' }, `−${Math.abs(d.amount)}🪙 ${reasonText(d)}`)));
  }
}

function reasonText(d) {
  if (d.reason) return d.reason;
  if (d.type === 'REDEEM') return '商店兑换';
  if (d.type === 'SCREEN_OVER') return '屏幕超时';
  return '家长记录';
}
