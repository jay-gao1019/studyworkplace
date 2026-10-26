// P2 教材页（#/cn #/math #/en，docs/04 §2 P2）：目录打卡结算 + 进度条 + 条目自测跳转知识树 + 单元电子书（M4.7 v2.2；v2.3 待审勾；v2.4 佐证+阅读引导）。
import { h, toast, floatCoin, evidenceSheet } from '../ui.js';

const NAMES = { cn: '📖 语文', math: '🧮 数学', en: '🅰️ 英语' };

export async function render(main, ctx, meta) {
  main.innerHTML = '';
  const subject = (meta && meta.subject) || 'math';
  let groups = [];      // 当前教材目录 [{id,title,branch,leaves:[{id,title,checked}],ebookPage?}]
  let coinValue = 5;    // 每次打卡结算金额（后端 tree 接口 coinValue）
  let branch = '校内';  // 数学页双 Tab 当前档
  let reqSeq = 0;       // 防旧请求回写
  let ebookOk = false;  // 本年级/学期的人教 PDF 是否已就位（M4.7）
  let ebookDir = '';    // 电子书目录绝对路径（就绪索引返回，用于灰态提示文案）

  const wrap = h('div', {});
  main.append(wrap);

  // 顶栏年级/学期变化不会自动重跑路由；监听 app:refresh 重新拉取（页面被切走时自清理）。
  window.addEventListener('app:refresh', onAppRefresh);
  function onAppRefresh() {
    if (!document.body.contains(wrap)) { window.removeEventListener('app:refresh', onAppRefresh); return; }
    load();
  }

  const gradeOf = () => ctx.store.s.grade ?? ctx.store.s.profile?.grade ?? 1;
  const termOf = () => ctx.store.s.term ?? ctx.store.s.profile?.term ?? '上';

  async function load() {
    const seq = ++reqSeq;
    wrap.innerHTML = '';
    wrap.append(h('div', { class: 'empty' }, '课本目录加载中…'));
    let data;
    try {
      data = await ctx.api.get(`/textbook/${subject}?grade=${gradeOf()}&term=${encodeURIComponent(termOf())}`);
    } catch (e) {
      if (seq !== reqSeq) return;
      wrap.innerHTML = '';
      wrap.append(h('div', { class: 'card empty' }, `课本没加载出来：${e.message}`));
      return;
    }
    if (seq !== reqSeq) return;
    groups = data.groups || [];
    coinValue = data.coinValue ?? 5;
    draw(data.progress);
    // 电子书就绪索引（M4.7）：异步补状态，不阻塞目录渲染。
    try {
      const idx = await ctx.api.get('/ebook/index');
      if (seq !== reqSeq || !document.body.contains(wrap)) return;
      const was = ebookOk;
      ebookDir = idx.dir || '';
      ebookOk = !!idx[`${subject}-${gradeOf()}${termOf()}`];
      if (was !== ebookOk) draw(data.progress);
    } catch { /* 索引拿不到时按钮保持灰态 */ }
  }

  function draw(apiProgress) {
    // 优先用本年级/学期接口返回的进度；拿不到时退回顶栏 bootstrap 快照。
    const prog = { ...(ctx.store.s.progress?.[subject] || {}), ...(apiProgress || {}) };
    wrap.innerHTML = '';
    wrap.append(
      h('div', { class: 'section-title' }, NAMES[subject] || '课本',
        h('span', { class: 'pill' }, `${gradeOf()}年级 · ${termOf()}学期`)),
      h('div', { class: 'card', style: 'margin-bottom:14px' },
        bar('🏫 校内打卡进度', prog.schoolPercent),
        prog.hasOlympiad ? bar('🏅 奥数打卡进度', prog.olympiadPercent) : null));

    if (subject === 'math') {
      const bodyBox = h('div', {});
      const tabsEl = h('div', { class: 'tabs' },
        h('button', { class: 'btn', onclick: (e) => { branch = '校内'; markOn(e.currentTarget); renderBranch(bodyBox); } }, '🏫 校内'),
        h('button', { class: 'btn', onclick: (e) => { branch = '奥数'; markOn(e.currentTarget); renderBranch(bodyBox); } }, '🏅 奥数'));
      function markOn(active) {
        for (const b of tabsEl.children) b.classList.toggle('on', b === active);
      }
      function renderBranch(box) {
        box.innerHTML = '';
        box.append(groupsCard(groups.filter((g) => (g.branch || '校内') === branch)));
      }
      wrap.append(tabsEl, bodyBox);
      markOn(tabsEl.children[0]);
      renderBranch(bodyBox);
    } else {
      wrap.append(groupsCard(groups));
    }
  }

  function groupsCard(list) {
    const box = h('div', {});
    if (!list.length) {
      box.append(h('div', { class: 'card empty' }, '这一组还没有条目，可以请爸爸妈妈在后台导入'));
      return box;
    }
    for (const g of list) {
      const card = h('div', { class: 'card tb-group' }, groupHeader(g));
      for (const leaf of (g.leaves || [])) card.append(leafRow(leaf));
      box.append(card);
    }
    return box;
  }

  /** 单元标题行：标题 + [📖 打开电子书]（M4.7：按钮排在自测链路之前）。 */
  function groupHeader(g) {
    const btn = h('button', {
      class: 'btn ebook-btn',
      title: ebookOk ? '打开本册人教版电子书（可定位到对应单元起始页）'
        : '电子书还没就位：点开看怎么放入书架',
      onclick: () => openReader(g),
    }, '📖 打开电子书');
    if (!ebookOk) btn.classList.add('grey'); // v2.4：灰态但可点——点开是引导页而非 404
    return h('div', { class: 'tb-group-head' }, h('span', { style: 'font-weight:700' }, g.title), btn);
  }

  /** PDF 阅读器弹层：iframe #page 定位到分组的 ebookPage（默认第 1 页）；缺文件显示引导态（M14.4）。 */
  function openReader(g) {
    const page = g.ebookPage || 1;
    const url = `/api/ebook/${subject}/${gradeOf()}/${encodeURIComponent(termOf())}/file#page=${page}`;
    const body = ebookOk
      ? h('iframe', { src: url, title: '人教版电子书' })
      : h('div', { class: 'pdf-guide' }, h('div', {},
        h('div', { class: 'big' }, '📚'),
        h('b', {}, '这本书还没有放入书架'),
        h('p', {}, `请家长在 后台 → 📚 教材资源 上传「${NAMES[subject] || ''} ${gradeOf()}年级${termOf()}册」人教版 PDF。`),
        h('p', {}, '手机浏览器打开后台也能直接上传；上传后孩子端立即可读。'),
        h('button', { class: 'btn primary', style: 'margin-top:14px', onclick: () => { close(); ctx.ui.go('admin'); } }, '🔒 去找家长上传')));
    const overlay = h('div', { class: 'pdf-overlay' },
      h('div', { class: 'pdf-box' },
        h('div', { class: 'pdf-bar' },
          h('b', {}, `${NAMES[subject] || '课本'} · ${gradeOf()}年级${termOf()}册`),
          ebookOk ? h('span', { class: 'pdf-page' }, `第 ${page} 页`) : null,
          h('button', { class: 'btn ghost', style: 'margin-left:auto', onclick: close }, '✕ 关闭')),
        body));
    function close() {
      overlay.remove();
      document.removeEventListener('keydown', onKey);
    }
    function onKey(e) { if (e.key === 'Escape') close(); }
    document.addEventListener('keydown', onKey);
    overlay.addEventListener('click', (e) => { if (e.target === overlay) close(); });
    document.body.append(overlay);
  }

  function leafRow(leaf) {
    const tick = h('span', {});
    const name = h('span', { class: 't' }, leaf.title);
    const checkBtn = h('button', { class: 'btn', style: 'padding:5px 12px;font-size:.85rem' });
    const quizBtn = h('button', {
      class: 'btn ghost', style: 'padding:5px 10px;font-size:.85rem;flex:none',
      title: '跳到知识体系做这个知识点的自测',
      onclick: () => { sessionStorage.setItem('kb-leaf', leaf.id); ctx.ui.go('knowledge'); },
    }, '🎯自测');
    const row = h('div', { class: 'tb-leaf' }, tick, name, checkBtn, quizBtn);

    const sync = () => {
      const pend = !!leaf.pending && !leaf.checked;
      row.classList.toggle('checked', !!leaf.checked);
      row.classList.toggle('pend', pend);
      tick.setAttribute('style', leaf.checked
        ? 'width:22px;height:22px;border-radius:7px;flex:none;display:grid;place-items:center;font-weight:800;color:#fff;background:var(--c-income);border:2px solid var(--c-income)'
        : pend
          ? 'width:22px;height:22px;border-radius:7px;flex:none;display:grid;place-items:center;font-size:13px;border:2px dashed var(--c-primary);background:#fff'
          : 'width:22px;height:22px;border-radius:7px;flex:none;border:2px solid var(--c-line);background:#fff');
      tick.textContent = leaf.checked ? '✓' : pend ? '⏳' : '';
      checkBtn.className = `btn ${leaf.checked || pend ? 'ghost' : 'primary'}`;
      checkBtn.style.cssText = 'padding:5px 12px;font-size:.85rem';
      checkBtn.textContent = leaf.checked ? '取消打卡'
        : pend ? '⏳ 待审核（点击撤回）' : `知识点 +${coinValue}🪙`;
    };

    checkBtn.onclick = async () => {
      checkBtn.disabled = true;
      try {
        // v2.3/M11.3：待审条目再点 = 撤回申请
        if (leaf.pending && !leaf.checked) {
          const r = await ctx.api.post('/textbook/check',
            { profileId: ctx.store.s.currentProfileId, nodeId: leaf.id });
          if (r.state === 'pending' && !r.checked) {
            leaf.pending = false;
            sync();
            toast(`“${leaf.title}”的申请已撤回`, '');
            await ctx.store.refreshCoins();
          }
          return;
        }
        // v2.4/FR-32：审批启用时，打卡先弹 📸 佐证层（照片可选）
        let extra = {};
        if (!leaf.checked && ctx.store.s.approvalEnabled) {
          const ev = await evidenceSheet(ctx.api, {
            title: `打卡「${leaf.title}」`,
            subtitle: `知识点 +${coinValue}🪙 · 家长确认后到账`,
            kind: 'textbook',
          });
          if (!ev) return; // 取消
          extra = { photoIds: ev.photoIds, note: ev.note };
        }
        const r = await ctx.api.post('/textbook/check',
          { profileId: ctx.store.s.currentProfileId, nodeId: leaf.id, ...extra });
        if (r.state === 'pending') {
          leaf.pending = true;
          leaf.checked = false;
          sync();
          floatCoin(row, '⏳ 已提交');
          toast('⏳ 已提交，待家长确认', 'ok');
        } else {
          leaf.checked = !!r.checked;
          const amount = r.amount ?? coinValue; // 反选时后端不返回 amount，用目录接口的金额
          sync();
          floatCoin(row, leaf.checked ? `+${amount}🪙` : `−${amount}🪙`);
          toast(leaf.checked ? `“${leaf.title}”打卡成功，+${amount}🪙！` : '这次打卡取消啦', leaf.checked ? 'ok' : '');
        }
        await ctx.store.refreshCoins();
      } catch (e) {
        toast(e.message, 'err');
      }
      checkBtn.disabled = false;
    };

    sync();
    return row;
  }

  function bar(label, percent) {
    const p = Math.max(0, Math.min(100, Math.round(Number(percent) || 0)));
    return h('div', { style: 'margin:8px 0' },
      h('div', { style: 'display:flex;justify-content:space-between;font-size:.9rem;color:var(--c-sub);margin-bottom:4px' },
        h('span', {}, label), h('span', {}, `${p}%`)),
      h('div', { class: 'progress-line' }, h('i', { style: `width:${p}%` })));
  }

  await load();
}
