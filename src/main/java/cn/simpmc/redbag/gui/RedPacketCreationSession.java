package cn.simpmc.redbag.gui;

import cn.simpmc.redbag.model.RedPacket;
import java.math.BigDecimal;

public final class RedPacketCreationSession {

    private final RedPacket.RedPacketType type;
    private final long createTime;
    private final long timeoutMillis;
    private Step step = Step.WAITING_FOR_AMOUNT;
    private BigDecimal amount;

    public RedPacketCreationSession(RedPacket.RedPacketType type, long timeoutSeconds) {
        this.type = type;
        this.createTime = System.currentTimeMillis();
        this.timeoutMillis = Math.max(1L, timeoutSeconds) * 1000L;
    }

    public RedPacket.RedPacketType getType() {
        return type;
    }

    public long getCreateTime() {
        return createTime;
    }

    public Step getStep() {
        return step;
    }

    public void setStep(Step step) {
        this.step = step;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - createTime >= timeoutMillis;
    }

    public enum Step {
        WAITING_FOR_AMOUNT,
        WAITING_FOR_COUNT,
        COMPLETED
    }
}
