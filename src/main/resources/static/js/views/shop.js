// P5 兑换商店（docs/04 §2 P5）：余额横幅 + 商品网格 + 二次确认兑换 + 我的兑换。
import { h, toast, modal, floatCoin } from '../ui.js';

export async function render(main, ctx) {
  main.innerHTML = '';
  const pid = ctx.store.s.currentProfileId;

  const banner = h('div', { class: 'card', style: 'text-align:center' }, h('span', { class: 'empty' }, '…'));
  const grid = h('div', { class: 'grid cols3' }, h('div', { class: 'card empty' }, '加载中…'));
  const redBox = h('div', { class: 'card' }, h('span', { class: 'empty' }, '…'));

  main.append(
    h('div', { class: 'section-title' }, '🛒 兑换商店'),
    banner,
    h('div', { class: 'section-title' }, '✨ 挑一个喜欢的'),
    grid,
    h('div', { class: 'section-title' }, '📦 我的兑换'),
    redBox);

  // ── 余额横幅 ─────────────────────────
  function renderBanner() {
    const bal = (ctx.store.s.coins && ctx.store.s.coins.balance) || 0;
    banner.innerHTML = '';
    banner.append(
      h('div', { style: 'color:var(--c-sub)' }, '我的金币'),
      h('div', { style: 'font-size:2.4rem;font-weight:800;color:var(--c-primary-dark);margin-top:4px' }, `${bal} 🪙`));
  }

  // ── 商品网格 ─────────────────────────
  async function loadShop() {
    let items = [];
    try {
      items = await ctx.api.get('/shop') || [];
    } catch (e) {
      grid.innerHTML = '';
      grid.append(h('div', { class: 'card empty' }, e.message));
      return;
    }
    grid.innerHTML = '';
    if (!items.length) {
      grid.append(h('div', { class: 'card empty' }, '商店暂时是空的，请爸爸妈妈先上货哦'));
      return;
    }
    for (const it of items) {
      const affordable = !!it.affordable;
      const card = h('div', { class: `card shop-card${affordable ? '' : ' no'}` },
        h('div', { class: 'icon' }, it.icon || '🎁'),
        h('div', { style: 'font-weight:700;margin-top:4px' }, it.name),
        h('div', { class: 'price' }, `${it.price}🪙`));
      if (affordable) {
        const btn = h('button', { class: 'btn primary', style: 'margin-top:8px' }, '兑换');
        btn.onclick = () => redeem(it, btn);
        card.append(btn);
      } else {
        card.append(h('div', { class: 'shortfall', style: 'margin-top:8px' }, `还差 ${it.shortfall} 金币哦`));
      }
      grid.append(card);
    }
  }

  async function redeem(it, anchor) {
    const ok = await modal({
      title: '和爸爸妈妈约好了吗？确认兑换？',
      body: `「${it.name}」将花费 ${it.price}🪙`,
      okText: '确认兑换',
    });
    if (!ok) return;
    try {
      const r = await ctx.api.post('/shop/redeem', { profileId: pid, itemId: it.id });
      floatCoin(anchor || banner, `-${it.price}🪙`);
      toast(r.tip || '兑换成功！请与爸爸妈妈确认兑现哦', 'ok');
      await ctx.store.refreshCoins();
      renderBanner();
      await Promise.all([loadShop(), loadRedemptions()]);
    } catch (e) {
      toast(e.message, 'err');
    }
  }

  // ── 我的兑换 ─────────────────────────
  async function loadRedemptions() {
    let list = [];
    try {
      list = await ctx.api.get('/shop/redemptions') || [];
    } catch (e) {
      redBox.innerHTML = '';
      redBox.append(h('b', {}, '📦 我的兑换'), h('div', { class: 'empty' }, e.message));
      return;
    }
    list = list.slice().sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')));
    redBox.innerHTML = '';
    redBox.append(h('b', {}, '📦 我的兑换'));
    if (!list.length) {
      redBox.append(h('div', { class: 'empty' }, '还没有兑换记录，去挑一个吧'));
      return;
    }
    const rows = h('div', { style: 'display:flex;flex-direction:column;gap:10px;margin-top:12px' });
    for (const rec of list) {
      rows.append(h('div', { style: 'display:flex;align-items:center;gap:12px' },
        h('span', { style: 'font-size:1.6rem' }, rec.icon || '🎁'),
        h('span', { style: 'flex:1;font-weight:600' }, `${rec.itemName} · ${rec.price}🪙`),
        h('span', { style: 'color:var(--c-sub);font-size:.85rem' }, dayOf(rec.createdAt)),
        h('span', { class: `rede-status ${statusClass(rec.status)}` }, rec.statusLabel || statusText(rec.status))));
      if (rec.status === 'pending') {
        rows.append(h('div', { style: 'color:var(--c-sub);font-size:.82rem;margin:2px 0 0 44px' }, '等家长确认哦'));
      }
    }
    redBox.append(rows);
  }

  renderBanner();
  await Promise.all([loadShop(), loadRedemptions()]);
}

// ── 纯渲染小工具 ─────────────────────────

function statusClass(status) {
  if (status === 'redeemed') return 'rs-redeemed';
  if (status === 'cancelled') return 'rs-cancelled';
  return 'rs-pending';
}

function statusText(status) {
  if (status === 'redeemed') return '已兑现';
  if (status === 'cancelled') return '已撤销';
  return '待兑现';
}

function dayOf(s) {
  return s ? String(s).slice(0, 10) : '';
}
