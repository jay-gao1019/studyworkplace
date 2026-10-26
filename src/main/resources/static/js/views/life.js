// P4 生活营（docs/04 §2 P4）：作息 / 家务 / 屏幕 / 习惯 四宫格 + 本周双线。
import { h, toast, floatCoin, evidenceSheet } from '../ui.js';

// 屏幕计时器 interval id（模块级；进入/重绘时清理，避免离开视图后仍在跑）。
let screenTimer = null;

export async function render(main, ctx) {
  main.innerHTML = '';
  if (screenTimer) { clearInterval(screenTimer); screenTimer = null; }

  const pid = ctx.store.s.currentProfileId;
  const sleep = makeCard('😴 作息打卡');
  const chore = makeCard('🧹 家务小能手');
  const screen = makeCard('📱 屏幕时间');
  const habit = makeCard('🌟 成长习惯');
  const grid = h('div', { class: 'life-grid' }, sleep.root, chore.root, screen.root, habit.root);
  const weekly = h('div', { style: 'margin-top:16px' });

  main.append(h('div', { class: 'section-title' }, '🌱 生活营'), grid, weekly);

  // ── 作息打卡 ─────────────────────────
  async function doSleep(kind) {
    try {
      const r = await ctx.api.post('/life/sleep', { profileId: pid, kind });
      const word = kind === 'sleep' ? '按时睡觉' : '按时起床';
      if (r.pending) toast(`${word}已记录，金币 ⏳ 待家长确认`, 'ok');
      else toast(r.rewarded > 0 ? `${word} +${r.rewarded}🪙` : '已记录！', r.rewarded > 0 ? 'ok' : '');
      await ctx.store.refreshCoins();
      await reloadLife();
    } catch (e) { toast(e.message, 'err'); }
  }

  function renderSleep(body, data) {
    const w = data.windows || {};
    const sl = data.sleep || { sleep: { done: false }, wake: { done: false } };
    body.innerHTML = '';
    body.append(
      sleepBtn('🌙', '晚安睡', w.sleepTarget || '21:30', num(w.sleepGraceMin, 15), sl.sleep, () => doSleep('sleep')),
      sleepBtn('☀️', '早安起', w.wakeTarget || '07:00', num(w.wakeGraceMin, 15), sl.wake, () => doSleep('wake')));
  }

  // ── 屏幕计时 ─────────────────────────
  async function stopScreen(sessionId) {
    try {
      const r = await ctx.api.post('/life/screen/stop', { profileId: pid, sessionId });
      if (screenTimer) { clearInterval(screenTimer); screenTimer = null; }
      toast(`本次 ${r.minutes} 分钟`, 'ok');
      await ctx.store.refreshCoins();
      await reloadLife();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function startScreen() {
    try {
      await ctx.api.post('/life/screen/start', { profileId: pid });
      await reloadLife();
    } catch (e) { toast(e.message, 'err'); }
  }

  function renderScreen(body, s) {
    if (screenTimer) { clearInterval(screenTimer); screenTimer = null; }
    body.innerHTML = '';
    const total = num(s.dailyMinutes, 0) + num(s.bankAvailable, 0);
    const used = num(s.usedMinutes, 0);
    const locked = !!(s.over || s.locked);
    body.append(ring(used, total, locked ? 'var(--c-expense)' : 'var(--c-income)'));
    body.append(h('div', { style: 'text-align:center;color:var(--c-sub);margin-top:6px' }, `今日额度 ${used}/${total} 分钟`));
    if (num(s.bankAvailable, 0) > 0) {
      body.append(h('div', { style: 'text-align:center;margin-top:8px' },
        h('span', { class: 'pill' }, `还有 ${s.bankAvailable} 分钟奖励额度`)));
    }
    if (locked) {
      body.append(h('div', { style: 'text-align:center;color:var(--c-expense);margin-top:8px' }, '今天的时间用完啦，明天再来'));
    }

    if (s.activeSession) {
      const sessionId = s.activeSession.sessionId ?? s.activeSession.id;
      const startAt = parseTime(s.activeSession.startAt);
      const elapsedEl = h('div', { style: 'text-align:center;font-weight:700;margin-top:10px' });
      const tick = () => {
        const mins = Math.max(0, Math.floor((Date.now() - startAt) / 60000));
        elapsedEl.textContent = `已计时 ${mins} 分钟`;
      };
      tick();
      screenTimer = setInterval(() => {
        if (!document.contains(elapsedEl)) { clearInterval(screenTimer); screenTimer = null; return; }
        if (document.hidden) return;
        tick();
      }, 15000);
      body.append(elapsedEl);
      const stopBtn = h('button', { class: 'btn danger', style: 'width:100%;margin-top:10px' }, '⏹ 结束计时');
      stopBtn.onclick = () => stopScreen(sessionId);
      body.append(stopBtn);
    } else {
      const startBtn = h('button', { class: 'btn primary', style: 'width:100%;margin-top:10px' }, '▶ 开始计时');
      if (locked) startBtn.disabled = true;
      startBtn.onclick = startScreen;
      body.append(startBtn);
    }
  }

  // ── 习惯链 ─────────────────────────
  async function toggleHabit(hb) {
    try {
      const r = await ctx.api.post('/life/habit/toggle', { profileId: pid, habitId: hb.id });
      if (r.done) {
        toast(`已记录！连续 ${num(r.consecutive, 0)} 天`, 'ok');
        if (num(r.milestoneRewarded, 0) > 0) toast(`里程碑达成 +${r.milestoneRewarded}🪙！`, 'ok');
        if (num(r.milestonePending, 0) > 0) toast(`里程碑达成！奖励 ⏳ 待家长确认（${r.milestonePending} 笔）`, 'ok');
      } else {
        toast('今天先休息，明天继续', '');
      }
      await ctx.store.refreshCoins();
      await reloadLife();
    } catch (e) { toast(e.message, 'err'); }
  }

  function renderHabits(body, data) {
    const habits = data.habits || [];
    body.innerHTML = '';
    if (!habits.length) {
      body.append(h('div', { class: 'empty' }, '还没有习惯，请爸爸妈妈先添加'));
      return;
    }
    for (const hb of habits) {
      const cycle = num(hb.cycleDays, 21);
      const chain = h('div', { class: 'habit-chain' });
      for (let i = 0; i < cycle; i++) {
        const on = i < num(hb.consecutive, 0);
        const mile = (i + 1) === 7 || (i + 1) === 14 || (i + 1) === 21;
        chain.append(h('div', { class: `cell${on ? ' on' : ''}${mile ? ' mile' : ''}` }));
      }
      const head = h('div', { style: 'display:flex;align-items:center;gap:10px' },
        h('span', { style: 'font-size:1.5rem' }, hb.icon || '🌟'),
        h('span', { style: 'flex:1;font-weight:600' }, hb.name),
        h('span', { class: 'pill' }, `连续 ${num(hb.consecutive, 0)} 天`),
        hb.doneToday ? h('span', { class: 'pill', style: 'background:#d9f5e3;color:#1a7a3c' }, '✓ 今天完成') : null);
      const block = h('div', { style: 'padding:10px 0;border-bottom:1px dashed var(--c-line);cursor:pointer' }, head, chain);
      block.onclick = () => toggleHabit(hb);
      body.append(block);
    }
  }

  // ── 家务（复用计划接口）─────────────
  async function loadChores() {
    chore.body.innerHTML = '';
    chore.body.append(h('span', { class: 'empty' }, '加载中…'));
    let view;
    try {
      view = await ctx.api.get('/plans/today');
    } catch (e) {
      chore.body.innerHTML = '';
      chore.body.append(h('div', { class: 'empty' }, e.message));
      return;
    }
    const items = (view.items || []).filter((t) => t.category === 'life' && t.amount > 0);
    chore.body.innerHTML = '';
    if (!items.length) {
      chore.body.append(h('div', { class: 'empty' }, '请爸爸妈妈先在后台添加生活任务'));
      return;
    }
    const list = h('div', { style: 'display:flex;flex-direction:column;gap:10px' });
    for (const t of items) {
      const card = h('div', { class: `card task-card${t.done ? ' done' : ''}${t.pending && !t.done ? ' pend' : ''}` },
        h('span', { class: 'icon' }, t.icon || '🧹'),
        h('span', { class: 'name' }, t.name, t.pending && !t.done ? h('span', { class: 'pill', style: 'margin-left:6px' }, '⏳ 待审核') : null),
        h('span', { class: 'amount' }, `+${t.amount}🪙`),
        h('span', { class: 'tick' }));
      card.style.cursor = 'pointer';
      card.onclick = async () => { // v2.3/v2.4 待审半透明勾 + 📸 佐证（同 P1 任务卡）
        card.style.pointerEvents = 'none';
        try {
          if (card.classList.contains('pend')) {
            const r = await ctx.api.post('/plans/check', { profileId: pid, taskItemId: t.taskId });
            if (r.state === 'pending' && !r.done) {
              card.classList.remove('pend');
              const pill = card.querySelector('.pill'); if (pill) pill.remove();
              toast(`「${t.name}」的申请已撤回`, '');
              await ctx.store.refreshCoins();
            }
            return;
          }
          let extra = {};
          if (!card.classList.contains('done') && ctx.store.s.approvalEnabled && t.amount > 0) {
            const ev = await evidenceSheet(ctx.api, {
              title: `提交「${t.name}」`,
              subtitle: `+${t.amount}🪙 · 家长确认后到账`,
              kind: 'task',
            });
            if (!ev) return;
            extra = { photoIds: ev.photoIds, note: ev.note };
          }
          const r = await ctx.api.post('/plans/check', { profileId: pid, taskItemId: t.taskId, ...extra });
          if (r.state === 'pending') {
            card.classList.add('pend');
            card.querySelector('.name').append(h('span', { class: 'pill', style: 'margin-left:6px' }, '⏳ 待审核'));
            floatCoin(card, '⏳ 已提交');
            toast('⏳ 已提交，待家长确认', 'ok');
          } else {
            if (r.tx) floatCoin(card, r.done ? `+${r.tx.amount}🪙` : `-${Math.abs(r.tx.amount)}🪙`);
            card.classList.toggle('done', r.done);
            toast(r.done ? `${t.name} 完成！` : `${t.name} 已取消`, r.done ? 'ok' : '');
          }
          await ctx.store.refreshCoins();
        } catch (e) { toast(e.message, 'err'); } finally { card.style.pointerEvents = ''; }
      };
      list.append(card);
    }
    chore.body.append(list);
  }

  // ── 本周双线 ─────────────────────────
  async function loadWeekly() {
    let wk = { study: { done: 0, total: 0 }, life: { done: 0, total: 0 } };
    try {
      wk = await ctx.api.get('/plans/weekly');
    } catch {
      weekly.innerHTML = '';
      weekly.append(h('div', { class: 'card empty' }, '本周进度暂时加载不出来'));
      return;
    }
    weekly.innerHTML = '';
    weekly.append(h('div', { class: 'card' },
      h('b', {}, '📈 本周打卡进度'),
      progressLine('📚 学习线', wk.study),
      progressLine('🌱 生活线', wk.life)));
  }

  // 统一重绘作息 / 屏幕 / 习惯三块（同源 /api/life）。
  async function reloadLife() {
    let data;
    try {
      data = await ctx.api.get('/life');
    } catch (e) {
      toast(e.message, 'err');
      return;
    }
    renderSleep(sleep.body, data);
    renderScreen(screen.body, data.screen || {});
    renderHabits(habit.body, data);
  }

  await reloadLife();
  loadChores();
  loadWeekly();
}

// ── 纯渲染小工具 ─────────────────────────

function makeCard(titleEmoji) {
  const body = h('div', { style: 'margin-top:12px' }, h('span', { class: 'empty' }, '加载中…'));
  const root = h('div', { class: 'card' },
    h('b', { style: 'display:flex;align-items:center;gap:8px;font-size:1.05rem' }, titleEmoji), body);
  return { root, body };
}

function sleepBtn(emoji, label, target, grace, rec, onMark) {
  const st = rec || { done: false };
  const btn = h('button', { class: 'sleep-btn btn', style: 'margin-bottom:12px;padding:14px 16px' });
  const status = st.done
    ? (st.onTime
      ? h('span', { style: 'color:var(--c-income);font-weight:700' }, '✓ 已记录 · 按时!')
      : h('span', { style: 'color:var(--c-sub)' }, '✓ 已记录 · 有点晚啦，明天早点'))
    : h('span', { style: 'color:var(--c-sub)' }, '来啦，点一下打卡吧');
  btn.append(
    h('div', { style: 'display:flex;align-items:center;gap:10px' },
      h('span', { style: 'font-size:1.5rem' }, emoji),
      h('span', { class: 'time' }, target),
      h('span', { style: 'flex:1;font-weight:600' }, label)),
    h('div', { style: 'display:flex;justify-content:space-between;align-items:center;margin-top:6px' },
      h('span', { style: 'font-size:.85rem;color:var(--c-sub)' }, `宽限 ${grace} 分钟`),
      status));
  if (st.done) btn.disabled = true;
  else btn.onclick = onMark;
  return btn;
}

function ring(used, total, color) {
  const rate = total ? Math.min(1, used / total) : 0;
  const size = 120, cx = size / 2, r = 46, c = 2 * Math.PI * r;
  const svg = h('svg', { width: size, height: size, viewBox: `0 0 ${size} ${size}` },
    h('circle', { cx, cy: cx, r, fill: 'none', stroke: '#f1e7d6', 'stroke-width': 12 }),
    h('circle', {
      cx, cy: cx, r, fill: 'none', stroke: color, 'stroke-width': 12,
      'stroke-dasharray': `${(rate * c).toFixed(1)} ${c.toFixed(1)}`, 'stroke-linecap': 'round',
      transform: `rotate(-90 ${cx} ${cx})`,
    }),
    h('text', { x: cx, y: cx - 2, 'text-anchor': 'middle', style: 'font-size:22px;font-weight:800' }, `${used}`),
    h('text', { x: cx, y: cx + 18, 'text-anchor': 'middle', style: 'font-size:12px;fill:var(--c-sub)' }, `分钟`));
  return h('div', { class: 'ring-wrap screen-ring' }, svg);
}

function progressLine(label, v) {
  const total = (v && v.total) || 0;
  const done = (v && v.done) || 0;
  const rate = total ? Math.min(1, done / total) : 0;
  return h('div', { style: 'margin-top:12px' },
    h('div', { style: 'display:flex;justify-content:space-between;font-size:.9rem;margin-bottom:6px' },
      h('span', { style: 'font-weight:600' }, label),
      h('span', { style: 'color:var(--c-sub)' }, `本周打卡 ${done}/${total}`)),
    h('div', { class: 'progress-line' }, h('i', { style: `width:${Math.round(rate * 100)}%` })));
}

function num(x, dft) {
  return (x === null || x === undefined || Number.isNaN(x)) ? dft : x;
}

function parseTime(t) {
  if (t === null || t === undefined) return Date.now();
  if (typeof t === 'number') return t;
  if (/^\d+$/.test(String(t))) return Number(t);
  const d = Date.parse(t);
  return Number.isNaN(d) ? Date.now() : d;
}
