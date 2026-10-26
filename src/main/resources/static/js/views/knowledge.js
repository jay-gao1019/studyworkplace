// P3 知识体系（#/knowledge，docs/04 §2 P3）：六档筛选 + 左树右答 + 整轮自测判分 + 未掌握攻关。
import { h, toast, modal, fmt } from '../ui.js';

const SUBJECTS = [['cn', '📖 语文'], ['math', '🧮 数学'], ['en', '🅰️ 英语']];
// 六档筛选胶囊：全部 / 精通 / 掌握 / 部分 / 未掌握 / 未答（needs_retest 恒显示）
const FILTERS = [
  ['全部', null], ['精通', 'proficient'], ['掌握', 'mastered'],
  ['部分掌握', 'partial'], ['未掌握', 'weak'], ['未答', 'unanswered'],
];
const CELEBRATE = ['🎉', '✨', '🌟', '🪙', '🎊'];

export async function render(main, ctx) {
  main.innerHTML = '';
  let subject = 'math';       // 当前科目（局部状态，默认数学）
  let filter = null;          // 当前筛选档 mastery 值，null=全部
  let data = null;            // 最近一次知识树数据
  let openNodes = new Set();  // 展开中的模块/单元 id
  let selectedId = null;      // 右侧面板正在自测的叶子 id
  let quizSeq = 0;            // 防旧题目请求回写

  const gradeOf = () => ctx.store.s.grade ?? ctx.store.s.profile?.grade ?? 1;
  const termOf = () => ctx.store.s.term ?? ctx.store.s.profile?.term ?? '上';

  const subTabs = h('div', { class: 'tabs' });
  const statsBox = h('div', { style: 'display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px' });
  const filterBox = h('div', { class: 'filter-bar' });
  const treeBox = h('div', { class: 'card', style: 'max-height:70vh;overflow:auto' });
  const quizBox = h('div', {});

  main.append(
    h('div', { class: 'section-title' }, '🌳 知识体系',
      h('span', { class: 'pill' }, `${gradeOf()}年级 · ${termOf()}学期`)),
    subTabs, statsBox, filterBox,
    h('div', { class: 'kb-layout' }, treeBox, quizBox));
  quizBox.append(h('div', { class: 'card empty' }, '👈 点左边的知识点，就在这里答题自测'));

  drawSubjectTabs();
  await loadTree(true);

  // ── 数据加载 ─────────────────────────────
  async function loadTree(initial) {
    treeBox.innerHTML = '';
    treeBox.append(h('div', { class: 'empty' }, '知识树加载中…'));
    try {
      data = await ctx.api.get(`/knowledge/${subject}/${gradeOf()}/${encodeURIComponent(termOf())}`);
    } catch (e) {
      toast(e.message, 'err');
      treeBox.innerHTML = '';
      treeBox.append(h('div', { class: 'empty' }, `知识树没加载出来：${e.message}`));
      return;
    }
    if (initial) {
      openNodes = new Set();
      for (const mod of data.modules || []) {
        openNodes.add(mod.id);
        for (const u of mod.units || []) openNodes.add(u.id);
      }
    }
    renderStats();
    renderFilterBar();
    renderTree();
    if (initial) {
      const pending = sessionStorage.getItem('kb-leaf'); // 教材页“🎯自测”跳转带来的叶子
      if (pending) {
        sessionStorage.removeItem('kb-leaf');
        const hit = findLeaf(pending);
        if (hit) {
          openNodes.add(hit.mod.id);
          openNodes.add(hit.unit.id);
          selectLeaf(hit.leaf);
        } else {
          toast('这个条目在知识树上还没找到对应知识点，先从左边挑一个吧');
        }
      }
    }
  }

  async function reloadTree() { // 整轮提交后重新着色，不重置展开/筛选状态
    try {
      data = await ctx.api.get(`/knowledge/${subject}/${gradeOf()}/${encodeURIComponent(termOf())}`);
      renderStats();
      renderTree();
    } catch { /* 刷新失败就保留旧树着色 */ }
  }

  function findLeaf(id) {
    for (const mod of data.modules || []) {
      for (const unit of mod.units || []) {
        for (const leaf of unit.leaves || []) {
          if (leaf.id === id) return { mod, unit, leaf };
        }
      }
    }
    return null;
  }

  // ── 顶部：科目 / 统计 / 筛选 ──────────────
  function drawSubjectTabs() {
    subTabs.innerHTML = '';
    for (const [code, label] of SUBJECTS) {
      const b = h('button', { class: `btn ${code === subject ? 'on' : ''}` }, label);
      b.onclick = () => {
        if (code === subject) return;
        subject = code;
        selectedId = null;
        quizBox.innerHTML = '';
        quizBox.append(h('div', { class: 'card empty' }, '👈 点左边的知识点，就在这里答题自测'));
        drawSubjectTabs();
        loadTree(true);
      };
      subTabs.append(b);
    }
  }

  function renderStats() {
    const st = data.stats || {};
    statsBox.innerHTML = '';
    statsBox.append(
      h('span', { class: 'badge' }, `🍃 末级知识点 ${st.leafCount ?? 0} 个`),
      h('span', { class: 'badge' }, `🌿 大纲节点 ${st.nodeCount ?? 0} 个`),
      h('span', { class: 'badge' }, `✅ 已答 ${st.answeredLeaves ?? 0} 个`),
      h('span', { class: 'badge' }, `⭐ 精通+掌握 ${fmt.pct((st.goodRatio ?? 0) / 100)}`)); // goodRatio 后端为百分数
  }

  function renderFilterBar() {
    filterBox.innerHTML = '';
    for (const [label, val] of FILTERS) {
      const chip = h('button', { class: `chip ${filter === val ? 'on' : ''}` }, label);
      chip.onclick = () => { filter = val; renderFilterBar(); renderTree(); };
      filterBox.append(chip);
    }
    if (filter === 'weak') {
      filterBox.append(h('button', {
        class: 'btn primary', style: 'padding:6px 14px;font-size:.88rem',
        onclick: startAttack,
      }, '🎯开始攻关'));
    }
  }

  function visible(leaf) {
    return filter === null || leaf.mastery === filter || leaf.mastery === 'needs_retest';
  }

  // ── 左侧知识树 ───────────────────────────
  function renderTree() {
    treeBox.innerHTML = '';
    let shown = 0;
    for (const mod of data.modules || []) {
      const units = (mod.units || [])
        .map((u) => ({ u, leaves: (u.leaves || []).filter(visible) }))
        .filter((x) => x.leaves.length);
      if (!units.length) continue;
      shown++;
      treeBox.append(moduleNode(mod, units));
    }
    if (!shown) treeBox.append(h('div', { class: 'empty' }, '这一档还没有知识点，换个筛选或科目看看'));
  }

  function moduleNode(mod, units) {
    const box = h('div', { class: 'tree-node' });
    const row = h('div', { class: 'row' },
      h('span', { style: 'flex:none;width:16px' }, openNodes.has(mod.id) ? '▼' : '▶'),
      h('b', {}, mod.title || '自定义知识点'),
      mod.branch === '奥数' ? h('span', { class: 'pill' }, '奥数') : null);
    row.onclick = () => { toggleOpen(mod.id); };
    box.append(row);
    if (openNodes.has(mod.id)) {
      const kids = h('div', { style: 'margin-left:16px' });
      for (const { u, leaves } of units) {
        if (!u.title) { // 自定义叶子直挂模块：没有单元行，直接渲染叶子
          kids.append(...leaves.map(leafRow));
          continue;
        }
        const uBox = h('div', {});
        const uRow = h('div', { class: 'row' },
          h('span', { style: 'flex:none;width:16px' }, openNodes.has(u.id) ? '▼' : '▶'),
          h('span', { style: 'font-weight:600' }, u.title));
        uRow.onclick = () => { toggleOpen(u.id); };
        uBox.append(uRow);
        if (openNodes.has(u.id)) {
          uBox.append(h('div', { style: 'margin-left:16px' }, ...leaves.map(leafRow)));
        }
        kids.append(uBox);
      }
      box.append(kids);
    }
    return box;
  }

  function toggleOpen(id) {
    if (openNodes.has(id)) openNodes.delete(id); else openNodes.add(id);
    renderTree();
  }

  function leafRow(leaf) {
    const row = h('div', { class: `row ${leaf.id === selectedId ? 'sel' : ''}` },
      h('span', { class: `dot ${leaf.mastery || 'unanswered'}`, title: leaf.masteryLabel || '' }),
      h('span', { style: 'flex:1;min-width:0' }, leaf.title),
      leaf.mastery === 'needs_retest'
        ? h('span', { class: 'pill', style: 'background:#fff1e0;color:#F97316' }, '⚠待重测') : null,
      h('span', { style: 'flex:none;color:var(--c-sub);font-size:.8rem' }, `${leaf.quizCount ?? 0}题`));
    row.onclick = () => selectLeaf(leaf);
    return row;
  }

  // ── 攻关清单 ─────────────────────────────
  async function startAttack() {
    let list;
    try {
      list = await ctx.api.get(`/knowledge/attack?subject=${subject}&grade=${gradeOf()}&term=${encodeURIComponent(termOf())}`);
    } catch (e) { toast(e.message, 'err'); return; }
    if (!list || !list.length) { toast('红点清零啦，没有要攻关的知识点！🎉', 'ok'); return; }
    let picked = null;
    const body = h('div', { style: 'display:flex;flex-direction:column;gap:8px;margin-top:10px;max-height:340px;overflow:auto' },
      ...list.map((l) => {
        const b = h('button', { class: 'btn', style: 'text-align:left' }, `${l.title} · ${l.quizCount ?? 0}题`);
        // 记下滑这条，再借“确定”按钮关闭弹层，让 await modal 继续往下走并选中它
        b.onclick = () => {
          picked = l;
          const okBtn = document.getElementById('modal-root').querySelector('.modal-actions button:last-child');
          if (okBtn) okBtn.click();
        };
        return b;
      }));
    const ok = await modal({
      title: `🎯 攻关清单（${list.length} 个未掌握）`, body,
      okText: '攻第一个', cancelText: '先不用了',
    });
    const target = picked || (ok ? list[0] : null);
    if (!target) return;
    const hit = findLeaf(target.id);
    if (hit) {
      openNodes.add(hit.mod.id);
      openNodes.add(hit.unit.id);
      selectLeaf(hit.leaf);
    } else {
      selectLeaf(target); // 攻关清单自带 quizCount，可直接开答
    }
    renderTree();
  }

  // ── 右侧自测面板：逐题暂存 → 整轮提交 ─────
  async function selectLeaf(leaf) {
    const seq = ++quizSeq;
    selectedId = leaf.id;
    renderTree();
    quizBox.innerHTML = '';
    if (!leaf.quizCount) {
      quizBox.append(h('div', { class: 'card empty' }, '📭 这个知识点还没有题目，请家长在后台导入'));
      return;
    }
    quizBox.append(h('div', { class: 'card empty' }, '题目加载中…'));
    let quizzes;
    try {
      quizzes = await ctx.api.get(`/knowledge/leaf/${leaf.id}/quiz`);
    } catch (e) {
      if (seq !== quizSeq) return;
      toast(e.message, 'err');
      quizBox.innerHTML = '';
      quizBox.append(h('div', { class: 'card empty' }, `题目没加载出来：${e.message}`));
      return;
    }
    if (seq !== quizSeq) return;
    if (!quizzes || !quizzes.length) {
      quizBox.innerHTML = '';
      quizBox.append(h('div', { class: 'card empty' }, '📭 这个知识点还没有题目，请家长在后台导入'));
      return;
    }
    startRound(leaf, quizzes);
  }

  function startRound(leaf, quizzes) {
    const answers = new Map(); // quizId → given
    let idx = 0;
    next();

    function next() { renderQuestion(quizzes[idx], idx === quizzes.length - 1); }

    function renderQuestion(q, last) {
      let chosen = answers.get(q.id) ?? null;
      let input = null;
      const card = h('div', { class: 'card quiz-panel' },
        h('div', { style: 'color:var(--c-sub);font-size:.9rem' }, `第 ${idx + 1}/${quizzes.length} 题`),
        h('div', { class: 'stem' }, q.stem));

      if (q.type === 'single') {
        const opts = (q.options || []).map((o) => {
          const el = h('div', { class: `quiz-opt ${chosen === o.id ? 'sel' : ''}` }, `${o.id}.  ${o.text}`);
          el.onclick = () => {
            chosen = o.id;
            for (const x of opts) x.classList.remove('sel');
            el.classList.add('sel');
          };
          return el;
        });
        card.append(...opts);
      } else {
        input = h('input', { type: 'text', placeholder: '把答案填在这里', style: 'width:100%' });
        if (chosen != null) input.value = chosen;
        input.onkeydown = (e) => { if (e.key === 'Enter') submit(); };
        card.append(input);
      }

      function submit() {
        if (q.type === 'single') {
          if (!chosen) { toast('先选一个答案哦', 'err'); return; }
          answers.set(q.id, chosen);
        } else {
          const text = (input.value || '').trim();
          if (!text) { toast('把答案填进框框里再提交哦', 'err'); return; }
          answers.set(q.id, text);
        }
        if (last) finishRound();
        else { idx += 1; next(); toast('这题答案已保存，继续下一题！'); }
      }

      card.append(
        h('button', { class: 'btn primary', style: 'margin-top:14px', onclick: submit },
          last ? '✅ 完成整轮提交' : '✏️ 提交本题'),
        h('div', { style: 'color:var(--c-sub);font-size:.82rem;margin-top:10px' },
          '答完全部题目后一起批改，判分在后台完成'));
      quizBox.innerHTML = '';
      quizBox.append(card);
      if (input) input.focus();
    }

    async function finishRound() {
      const body = {
        profileId: ctx.store.s.currentProfileId,
        answers: quizzes.map((q) => ({ quizId: q.id, given: answers.get(q.id) ?? '' })),
      };
      let r;
      try {
        r = await ctx.api.post(`/knowledge/leaf/${leaf.id}/attempt`, body);
      } catch (e) { toast(e.message, 'err'); return; }
      showResult(leaf, quizzes, r);
      try { await ctx.store.refreshCoins(); } catch { /* 顶栏刷新失败不影响本轮结果 */ }
      await reloadTree();
    }
  }

  function showResult(leaf, quizzes, r) {
    const card = h('div', { class: 'card quiz-panel', style: 'position:relative' });
    const rate = r.rate ?? Math.round((r.correct / (r.total || 1)) * 100);
    card.append(
      h('div', { style: 'font-size:1.05rem;font-weight:700' }, `本轮答对 ${r.correct}/${r.total} 题`),
      h('div', { style: 'display:flex;align-items:center;gap:10px;margin-top:8px;flex-wrap:wrap' },
        h('span', {}, '新掌握度：'),
        h('span', { class: `mastery-badge m-${r.mastery}` }, r.masteryLabel || r.mastery || '')),
      h('div', { style: 'color:var(--c-sub);margin-top:6px' },
        rate >= 90 ? '太棒啦，这个知识点被你拿下啦！'
          : rate >= 60 ? '就差一点点，看看错的题目再来一轮！'
            : '这次没答对没关系，我们一起看看正确答案！'));

    if (r.rewarded) {
      toast(`首次精通奖励 +${r.rewarded}🪙！`, 'ok');
      sprinkle(card);
    }

    const list = h('div', { style: 'margin-top:14px' });
    for (const res of (r.results || [])) {
      const q = quizzes.find((x) => x.id === res.quizId) || {};
      list.append(h('div', { class: `quiz-result ${res.correct ? 'ok' : 'no'}` },
        h('div', { style: 'font-weight:600' }, q.stem || ''),
        h('div', {
          style: `margin-top:6px;font-weight:700;color:${res.correct ? 'var(--c-income)' : 'var(--c-expense)'}`,
        }, res.correct ? '✓ 答对啦！' : `✗ 正确答案是 ${res.answer ?? ''}`),
        h('div', { style: 'font-size:.85rem;color:var(--c-sub);margin-top:4px' },
          `你的答案：${res.given || '（没填）'}`),
        res.explanation
          ? h('details', { style: 'margin-top:8px' },
            h('summary', { style: 'cursor:pointer;color:var(--c-primary-dark)' }, '💡 看讲解'),
            h('p', { style: 'margin-top:6px' }, res.explanation))
          : null));
    }
    card.append(list);

    card.append(h('div', { style: 'display:flex;gap:10px;margin-top:14px' },
      h('button', { class: 'btn primary', onclick: () => selectLeaf(leaf) }, '🔁 再来一轮'),
      h('button', {
        class: 'btn ghost',
        onclick: () => {
          selectedId = null;
          renderTree();
          quizBox.innerHTML = '';
          quizBox.append(h('div', { class: 'card empty' }, '👈 点左边的知识点，就在这里答题自测'));
        },
      }, '收起')));
    quizBox.innerHTML = '';
    quizBox.append(card);
  }

  // 撒花：复用 app.css 的 floatUp 关键帧，1.5s 内分批飘 emoji
  function sprinkle(anchor) {
    for (let i = 0; i < 10; i++) {
      setTimeout(() => {
        if (!document.body.contains(anchor)) return;
        const s = h('span', { class: 'float-coin' }, CELEBRATE[i % CELEBRATE.length]);
        s.style.left = `${6 + Math.random() * 84}%`;
        s.style.right = 'auto';
        s.style.top = `${20 + Math.random() * 60}px`;
        s.style.fontSize = `${14 + Math.random() * 12}px`;
        anchor.append(s);
        setTimeout(() => s.remove(), 900);
      }, i * 140);
    }
  }
}
