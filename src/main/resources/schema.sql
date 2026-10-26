-- StudyWorkplace schema（唯一结构事实来源，对应 docs/05；ddl-auto=validate）
CREATE TABLE IF NOT EXISTS profiles (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(24) NOT NULL,
  avatar VARCHAR(24) NOT NULL,
  grade INT NOT NULL,
  term VARCHAR(2) NOT NULL,
  created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS settings (
  code VARCHAR(20) PRIMARY KEY,
  pin_hash VARCHAR(120),
  level_thresholds JSON,
  level_names JSON,
  checkin_amount INT DEFAULT 20,
  textbook_coin_value INT DEFAULT 5,
  life_settings JSON
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS task_items (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  icon VARCHAR(24),
  amount INT NOT NULL,
  category VARCHAR(8) NOT NULL,
  grade_scope VARCHAR(32) DEFAULT 'all',
  term VARCHAR(4) DEFAULT 'all',
  enabled TINYINT(1) DEFAULT 1,
  sort INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shop_items (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  icon VARCHAR(24),
  price INT NOT NULL,
  enabled TINYINT(1) DEFAULT 1,
  stock VARCHAR(12) DEFAULT 'unlimited'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS habits (
  id VARCHAR(40) PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  icon VARCHAR(24),
  cycle_days INT DEFAULT 21,
  milestone_rewards JSON,
  enabled TINYINT(1) DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS coin_txs (
  id VARCHAR(40) PRIMARY KEY,
  profile_id VARCHAR(40) NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount INT NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  ref JSON,
  revoked TINYINT(1) NOT NULL DEFAULT 0,
  dedup_key VARCHAR(190) AS (CASE WHEN revoked = 1 OR type IN ('reverse','redeem','redeem_refund','manual') THEN NULL
        ELSE CONCAT_WS('|', profile_id, type, JSON_UNQUOTE(JSON_EXTRACT(ref,'$.srcId')), day_key)
        END) STORED,
  KEY idx_tx_profile_day (profile_id, day_key),
  UNIQUE KEY uq_dedup (dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS redemptions (
  id VARCHAR(40) PRIMARY KEY,
  profile_id VARCHAR(40) NOT NULL,
  item_name VARCHAR(60) NOT NULL,
  price INT NOT NULL,
  icon VARCHAR(24),
  status VARCHAR(12) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  redeemed_at DATETIME(6),
  tx_id VARCHAR(40) NOT NULL,
  KEY idx_redemption_profile (profile_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS plan_checks (
  profile_id VARCHAR(40) NOT NULL,
  task_item_id VARCHAR(40) NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  status VARCHAR(8) NOT NULL,
  checked_at DATETIME(6) NOT NULL,
  tx_id VARCHAR(40),
  reverse_tx_id VARCHAR(40),
  PRIMARY KEY (profile_id, task_item_id, day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS textbook_checks (
  profile_id VARCHAR(40) NOT NULL,
  node_id VARCHAR(40) NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  checked_at DATETIME(6) NOT NULL,
  tx_id VARCHAR(40),
  PRIMARY KEY (profile_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tree_nodes (
  id VARCHAR(40) PRIMARY KEY,
  subject VARCHAR(8) NOT NULL,
  grade INT NOT NULL,
  term VARCHAR(2) NOT NULL,
  parent_ref VARCHAR(40),
  kind VARCHAR(8) NOT NULL,
  title VARCHAR(80) NOT NULL,
  sort INT DEFAULT 0,
  branch VARCHAR(8),
  origin VARCHAR(9) NOT NULL,
  KEY idx_node_scope (subject, grade, term)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quizzes (
  id VARCHAR(40) PRIMARY KEY,
  leaf_id VARCHAR(40) NOT NULL,
  subject VARCHAR(8) NOT NULL,
  type VARCHAR(8) NOT NULL,
  stem VARCHAR(500) NOT NULL,
  options JSON,
  answer VARCHAR(120) NOT NULL,
  answer_accepts JSON,
  explanation VARCHAR(800),
  origin VARCHAR(9) NOT NULL,
  KEY idx_quiz_leaf (leaf_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attempt_rounds (
  id VARCHAR(40) PRIMARY KEY,
  profile_id VARCHAR(40) NOT NULL,
  leaf_id VARCHAR(40) NOT NULL,
  quiz_set_hash VARCHAR(64) NOT NULL,
  answers JSON NOT NULL,
  rate DECIMAL(4,3) NOT NULL,
  mastery VARCHAR(12) NOT NULL,
  finished_at DATETIME(6) NOT NULL,
  KEY idx_attempt (profile_id, leaf_id, finished_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sleep_logs (
  profile_id VARCHAR(40) NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  kind VARCHAR(8) NOT NULL,
  actual_at DATETIME(6) NOT NULL,
  on_time TINYINT(1) NOT NULL,
  tx_id VARCHAR(40),
  PRIMARY KEY (profile_id, day_key, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screen_sessions (
  id VARCHAR(40) PRIMARY KEY,
  profile_id VARCHAR(40) NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  start_at DATETIME(6) NOT NULL,
  end_at DATETIME(6),
  minutes_used DECIMAL(6,2),
  KEY idx_screen (profile_id, day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS screen_banks (
  profile_id VARCHAR(40) PRIMARY KEY,
  granted_minutes INT DEFAULT 0,
  consumed_minutes INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS habit_logs (
  profile_id VARCHAR(40) NOT NULL,
  habit_id VARCHAR(40) NOT NULL,
  chain_id VARCHAR(40) NOT NULL,
  day_key VARCHAR(10) NOT NULL,
  done TINYINT(1) NOT NULL,
  PRIMARY KEY (profile_id, habit_id, day_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS habit_chains (
  profile_id VARCHAR(40) NOT NULL,
  habit_id VARCHAR(40) NOT NULL,
  chain_id VARCHAR(40) NOT NULL,
  consecutive INT NOT NULL,
  started_at VARCHAR(10) NOT NULL,
  awarded_milestones JSON,
  PRIMARY KEY (profile_id, habit_id, chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 导入前快照（docs/05 §8：整库 JSON 快照，保留最近 3 份由 BackupService 清理）
CREATE TABLE IF NOT EXISTS pre_import_snapshots (
  id VARCHAR(40) PRIMARY KEY,
  created_at DATETIME(6) NOT NULL,
  payload LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
