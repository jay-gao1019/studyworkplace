// 全局状态仓库：bootstrap 快照 + 订阅（docs/04 壳层 / docs/02 前端约定）。
import { api } from './api.js';

const state = {
  ready: false,
  profiles: [],
  currentProfileId: null,
  profile: null,
  coins: null,        // {balance,totalEarned,levelIndex,levelName,toNextLevel,streak}
  progress: null,     // {cn,math,en}
  pinUnset: false,
  grade: null,        // 页面级年级/学期覆盖（默认取 profile）
  term: null,
};
const subs = new Set();

export const store = {
  get s() { return state; },
  on(fn) { subs.add(fn); return () => subs.delete(fn); },
  emit() { subs.forEach((fn) => { try { fn(state); } catch (e) { console.error(e); } }); },

  async refresh() {
    const b = await api.get('/bootstrap');
    Object.assign(state, {
      profiles: b.profiles, currentProfileId: b.currentProfileId, profile: b.profile,
      coins: b.coins, progress: b.progress, pinUnset: b.pinUnset, ready: true,
    });
    if (state.grade === null) { state.grade = b.profile?.grade; state.term = b.profile?.term; }
    this.emit();
    return b;
  },

  /** 局部刷新余额/等级（金币跳动）。 */
  async refreshCoins() {
    const b = await api.get('/bootstrap');
    state.coins = b.coins;
    this.emit();
  },

  async switchProfile(id) {
    await api.post('/session/switch', { profileId: id });
    state.grade = null;
    await this.refresh();
  },

  setScope(grade, term) { state.grade = grade; state.term = term; this.emit(); },
};
