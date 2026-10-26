package com.studdy.service;

import com.studdy.entity.*;
import com.studdy.repo.*;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 档案 CRUD（docs/03 M8.1：姓名 1–8 字、头像内置 8 个、年级 1–6、学期 上/下；M7.4/M8.8 危险区）。 */
@Service
public class ProfileService {

    public record ProfileView(String id, String name, String avatar, int grade, String term) {}

    private final ProfileRepository repo;
    private final CoinTxRepository txRepo;
    private final PlanCheckRepository planRepo;
    private final TextbookCheckRepository textbookRepo;
    private final AttemptRoundRepository attemptRepo;
    private final RedemptionRepository redemptionRepo;
    private final SleepLogRepository sleepRepo;
    private final ScreenSessionRepository screenRepo;
    private final ScreenBankRepository bankRepo;
    private final HabitLogRepository habitLogRepo;
    private final HabitChainRepository chainRepo;
    private final SessionService session;

    public ProfileService(ProfileRepository repo, CoinTxRepository txRepo, PlanCheckRepository planRepo,
                          TextbookCheckRepository textbookRepo, AttemptRoundRepository attemptRepo,
                          RedemptionRepository redemptionRepo, SleepLogRepository sleepRepo,
                          ScreenSessionRepository screenRepo, ScreenBankRepository bankRepo,
                          HabitLogRepository habitLogRepo, HabitChainRepository chainRepo,
                          SessionService session) {
        this.repo = repo;
        this.txRepo = txRepo;
        this.planRepo = planRepo;
        this.textbookRepo = textbookRepo;
        this.attemptRepo = attemptRepo;
        this.redemptionRepo = redemptionRepo;
        this.sleepRepo = sleepRepo;
        this.screenRepo = screenRepo;
        this.bankRepo = bankRepo;
        this.habitLogRepo = habitLogRepo;
        this.chainRepo = chainRepo;
        this.session = session;
    }

    public List<ProfileView> list() {
        return repo.findAll().stream().map(ProfileService::view).toList();
    }

    public ProfileEntity getEntity(String id) {
        return repo.findById(id).orElseThrow(() -> new ApiException(404, "档案不存在"));
    }

    public ProfileView get(String id) {
        return view(getEntity(id));
    }

    @Transactional
    public ProfileView create(String name, String avatar, int grade, String term) {
        if (name == null || name.isBlank() || name.trim().length() > 8) {
            throw new ApiException(400, "姓名需为 1–8 个字");
        }
        if (grade < 1 || grade > 6) {
            throw new ApiException(400, "年级限 1–6");
        }
        if (!List.of("上", "下").contains(term)) {
            throw new ApiException(400, "学期限 上/下");
        }
        ProfileEntity p = new ProfileEntity(Ids.next("pf"), name.trim(),
                avatar == null || avatar.isBlank() ? "🐼" : avatar, grade, term, Instant.now());
        repo.save(p);
        return view(p);
    }

    @Transactional
    public ProfileView update(String id, String name, String avatar, Integer grade, String term) {
        ProfileEntity p = getEntity(id);
        if (name != null && !name.isBlank() && name.trim().length() <= 8) {
            p.setName(name.trim());
        }
        if (avatar != null && !avatar.isBlank()) {
            p.setAvatar(avatar);
        }
        if (grade != null && grade >= 1 && grade <= 6) {
            p.setGrade(grade);
        }
        if (term != null && List.of("上", "下").contains(term)) {
            p.setTerm(term);
        }
        repo.save(p);
        return view(p);
    }

    static ProfileView view(ProfileEntity p) {
        return new ProfileView(p.getId(), p.getName(), p.getAvatar(), p.getGrade(), p.getTerm());
    }

    // ── 危险区（M7.4：确认词=档案名 + 家长 token 已由控制器校验） ──

    /** 重置某档案全部流水与勾选/答题记录（保留档案与配置）。 */
    @Transactional
    public Map<String, Object> resetProfileData(String profileId, String confirmWord) {
        ProfileEntity p = confirm(profileId, confirmWord);
        String pid = p.getId();
        attemptRepo.deleteAll(attemptRepo.findByProfileIdOrderByFinishedAtDesc(pid));
        List<PlanCheckEntity> plans = planRepo.findAll().stream().filter(x -> x.getProfileId().equals(pid)).toList();
        planRepo.deleteAll(plans);
        textbookRepo.deleteAll(textbookRepo.findByProfileId(pid));
        redemptionRepo.deleteAll(redemptionRepo.findByProfileIdOrderByIdDesc(pid));
        List<SleepLogEntity> sleeps = sleepRepo.findAll().stream().filter(x -> x.getProfileId().equals(pid)).toList();
        sleepRepo.deleteAll(sleeps);
        screenRepo.deleteAll(screenRepo.findAll().stream().filter(x -> x.getProfileId().equals(pid)).toList());
        bankRepo.findById(pid).ifPresent(bankRepo::delete);
        habitLogRepo.deleteAll(habitLogRepo.findAll().stream().filter(x -> x.getProfileId().equals(pid)).toList());
        chainRepo.deleteAll(chainRepo.findAll().stream().filter(x -> x.getProfileId().equals(pid)).toList());
        txRepo.deleteAll(txRepo.findByProfileIdOrderByCreatedAtAsc(pid));
        return Map.of("reset", pid);
    }

    /** M8.8 删除档案：连带物理删除其分区数据（唯一物理删除场景）。 */
    @Transactional
    public void deleteProfile(String profileId, String confirmWord) {
        resetProfileData(profileId, confirmWord);
        repo.deleteById(profileId);
    }

    private ProfileEntity confirm(String profileId, String confirmWord) {
        ProfileEntity p = getEntity(profileId);
        if (confirmWord == null || !confirmWord.equals(p.getName())) {
            throw new ApiException(400, "确认词与档案名不一致，操作已取消");
        }
        return p;
    }
}
