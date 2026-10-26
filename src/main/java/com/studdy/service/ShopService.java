package com.studdy.service;

import com.studdy.domain.coin.CoinTx;
import com.studdy.domain.coin.TxType;
import com.studdy.entity.RedemptionEntity;
import com.studdy.entity.ShopItemEntity;
import com.studdy.repo.RedemptionRepository;
import com.studdy.repo.ShopItemRepository;
import com.studdy.util.Ids;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 兑换商店（docs/03 M3）：兑换即扣币记 pending，兑现永远线下由家长确认（M3.5）。 */
@Service
public class ShopService {

    private final ShopItemRepository items;
    private final RedemptionRepository redemptions;
    private final LedgerService ledger;

    public ShopService(ShopItemRepository items, RedemptionRepository redemptions, LedgerService ledger) {
        this.items = items;
        this.redemptions = redemptions;
        this.ledger = ledger;
    }

    public List<Map<String, Object>> catalog(String profileId) {
        int balance = ledger.balance(profileId);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (ShopItemEntity it : items.findAll()) {
            if (!it.isEnabled()) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", it.getId());
            m.put("name", it.getName());
            m.put("icon", it.getIcon());
            m.put("price", it.getPrice());
            m.put("affordable", balance >= it.getPrice());
            m.put("shortfall", Math.max(0, it.getPrice() - balance));
            out.add(m);
        }
        return out;
    }

    /** M3.2/3.3：事务内重校验余额→ redeem 流水 + pending 兑换记录。 */
    @Transactional
    public Map<String, Object> redeem(String profileId, String itemId) {
        ShopItemEntity it = items.findById(itemId).orElseThrow(() -> new ApiException(404, "商品不存在"));
        if (!it.isEnabled()) {
            throw new ApiException(400, "该商品已下架");
        }
        int balance = ledger.balance(profileId);
        if (balance < it.getPrice()) {
            throw new ApiException(400, "金币不够，还差 " + (it.getPrice() - balance) + " 枚");
        }
        CoinTx tx = ledger.appendTx(profileId, TxType.REDEEM, -it.getPrice(), null, null, null);
        String rid = Ids.next("rd");
        // refTxId 语义借用于流水引用：redeem 流水不写 redemptionId，兑换记录持有 txId 即闭环
        redemptions.save(new RedemptionEntity(rid, profileId, it.getName(), it.getPrice(),
                it.getIcon(), "pending", Instant.now(), tx.id()));
        return Map.of("redemptionId", rid, "status", "pending",
                "balance", ledger.balance(profileId), "amount", -it.getPrice(),
                "tip", "兑换成功！请与爸爸妈妈确认兑现哦");
    }

    /** M3.6 孩子端"我的兑换"。 */
    public List<Map<String, Object>> myRedemptions(String profileId) {
        return redemptions.findByProfileIdOrderByIdDesc(profileId).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("itemName", r.getItemName());
            m.put("icon", r.getIcon());
            m.put("price", r.getPrice());
            m.put("status", r.getStatus());
            m.put("statusLabel", switch (r.getStatus()) {
                case "pending" -> "待兑现";
                case "redeemed" -> "已兑现";
                default -> "已撤销";
            });
            m.put("createdAt", r.getCreatedAt().toString());
            m.put("redeemedAt", r.getRedeemedAt() == null ? null : r.getRedeemedAt().toString());
            return m;
        }).toList();
    }

    /** M3.4 家长确认兑现。 */
    @Transactional
    public Map<String, Object> confirm(String redemptionId) {
        RedemptionEntity r = mustBe(redemptionId, "pending");
        r.setStatus("redeemed");
        r.setRedeemedAt(Instant.now());
        redemptions.save(r);
        return Map.of("status", "redeemed");
    }

    /** M3.4 家长撤销兑换 → 退款流水 + cancelled。 */
    @Transactional
    public Map<String, Object> cancel(String redemptionId) {
        RedemptionEntity r = mustBe(redemptionId, "pending");
        ledger.appendTx(r.getProfileId(), TxType.REDEEM_REFUND, r.getPrice(),
                "refund:" + r.getId(), null, "撤销兑换：" + r.getItemName());
        r.setStatus("cancelled");
        redemptions.save(r);
        return Map.of("status", "cancelled", "balance", ledger.balance(r.getProfileId()));
    }

    private RedemptionEntity mustBe(String id, String expectStatus) {
        RedemptionEntity r = redemptions.findById(id).orElseThrow(() -> new ApiException(404, "兑换记录不存在"));
        if (!expectStatus.equals(r.getStatus())) {
            throw new ApiException(409, "该兑换记录已是「" + r.getStatus() + "」状态，不能重复操作");
        }
        return r;
    }
}
