package com.studdy.service;

import com.studdy.entity.*;
import com.studdy.repo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 首次启动写入默认 AdminConfig（幂等；docs/06 T3.2）。默认值即需求 S2/S3/S6 的"同款预置"。 */
@Component
public class DefaultConfigInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultConfigInitializer.class);

    private final SettingsRepository settingsRepo;
    private final TaskItemRepository taskRepo;
    private final ShopItemRepository shopRepo;
    private final HabitRepository habitRepo;
    private final PenaltyPresetRepository presetRepo;

    public DefaultConfigInitializer(SettingsRepository settingsRepo, TaskItemRepository taskRepo,
                                    ShopItemRepository shopRepo, HabitRepository habitRepo,
                                    PenaltyPresetRepository presetRepo) {
        this.settingsRepo = settingsRepo;
        this.taskRepo = taskRepo;
        this.shopRepo = shopRepo;
        this.habitRepo = habitRepo;
        this.presetRepo = presetRepo;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // v2.3/M12.4：默认扣分预设（表为空时装载；家长清空后不复活）
        if (presetRepo.count() == 0) {
            presetRepo.saveAll(List.of(
                    new PenaltyPresetEntity("pp_homework_slow", "写作业拖拉", "🐌", 3, true, 1),
                    new PenaltyPresetEntity("pp_temper", "发脾气", "😤", 5, true, 2),
                    new PenaltyPresetEntity("pp_liar", "说谎", "🎭", 8, true, 3),
                    new PenaltyPresetEntity("pp_careless", "丢三落四", "🧦", 2, true, 4)));
        }
        if (settingsRepo.existsById("singleton")) {
            return;
        }
        log.info("首次启动：写入默认配置");

        SettingsEntity s = new SettingsEntity("singleton");
        s.setLevelThresholds("[0,200,600,1500,3000]");
        s.setLevelNames("[\"小嫩芽\",\"绿幼苗\",\"向阳花\",\"知识树\",\"小学霸\"]");
        s.setCheckinAmount(20);
        s.setTextbookCoinValue(5);
        s.setLifeSettings("""
            {"sleepTarget":"21:30","sleepGraceMin":15,"wakeTarget":"07:00","wakeGraceMin":15,
             "sleepCoin":5,"wakeCoin":5,"screenDailyMinutes":40,"screenWeeklyBankCap":60,"screenOverPenalty":-10}""");
        settingsRepo.save(s);

        // 学习侧（需求 S2.1 原文同款）
        taskRepo.saveAll(List.of(
                new TaskItemEntity("task_read", "阅读课外书30分钟", "📚", 5, "study", "all", "all", true, 1),
                new TaskItemEntity("task_math_drill", "口算/计算练习", "🧮", 5, "study", "all", "all", true, 2),
                new TaskItemEntity("task_outdoor", "户外运动20分钟", "⚽", 5, "study", "all", "all", true, 3),
                new TaskItemEntity("task_handwriting", "练字10分钟", "✍️", 5, "study", "all", "all", true, 4),
                new TaskItemEntity("task_homework_late", "未按时完成作业", "⏰", -10, "study", "all", "all", true, 101),
                new TaskItemEntity("task_screen_over", "超时玩电子设备", "📱", -10, "study", "all", "all", true, 102)));

        // 生活侧·家务（M6.5 默认池）
        taskRepo.saveAll(List.of(
                new TaskItemEntity("chore_trash", "倒垃圾", "🗑️", 3, "life", "all", "all", true, 11),
                new TaskItemEntity("chore_dishes", "洗碗", "🍽️", 5, "life", "all", "all", true, 12),
                new TaskItemEntity("chore_desk", "整理书桌", "🪑", 4, "life", "all", "all", true, 13),
                new TaskItemEntity("chore_bed", "叠被子", "🛏️", 2, "life", "all", "all", true, 14),
                new TaskItemEntity("chore_water", "浇花", "🌸", 2, "life", "all", "all", true, 15)));

        // 商店（需求 S3.3 同款价）
        shopRepo.saveAll(List.of(
                new ShopItemEntity("shop_cartoon", "看一集动画片", "📺", 60, true, "unlimited"),
                new ShopItemEntity("shop_game", "玩30分钟游戏", "🎮", 120, true, "unlimited"),
                new ShopItemEntity("shop_book", "买一本喜欢的书", "📖", 200, true, "unlimited"),
                new ShopItemEntity("shop_outing", "周末外出游玩", "🎡", 300, true, "unlimited"),
                new ShopItemEntity("shop_menu", "选择今晚家庭菜单", "🍲", 80, true, "unlimited"),
                new ShopItemEntity("shop_late_sleep", "换10分钟晚睡", "🌙", 100, true, "unlimited")));

        // 习惯（M6.11 默认）
        habitRepo.saveAll(List.of(
                new HabitEntity("habit_schoolbag", "自己收拾书包", "🎒", 21, "{\"7\":10,\"14\":20,\"21\":50}", true)));

        log.info("默认配置完成：任务 {} 项、商品 {} 项、习惯 {} 项",
                taskRepo.count(), shopRepo.count(), habitRepo.count());
    }
}
