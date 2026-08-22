package cn.simpmc.redbag.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RedPacketTest {

    @Test
    void normalDistributionUsesEqualFloorAmountsAndTracksRemainder() {
        RedPacket packet = new RedPacket(
                "sender", "sender", RedPacket.RedPacketType.NORMAL,
                new BigDecimal("10.01"), 2, 5, new Random(1));

        BigDecimal first = packet.claim("one");
        BigDecimal second = packet.claim("two");

        assertEquals(new BigDecimal("5.00"), first);
        assertEquals(new BigDecimal("5.00"), second);
        assertEquals(new BigDecimal("10.00"), first.add(second));
        assertEquals(new BigDecimal("0.01"), packet.getRemainderAmount());
        assertEquals(new BigDecimal("10.00"), packet.getDistributedAmount());
        assertEquals(new BigDecimal("0.01"), packet.getRemainingAmount());
        assertEquals(new BigDecimal("0.01"), packet.takeRemainderForRefund());
        assertEquals(BigDecimal.ZERO.setScale(2), packet.takeRemainderForRefund());
        assertEquals(BigDecimal.ZERO.setScale(2), packet.getRemainingAmount());
    }

    @Test
    void luckyDistributionConservesEveryCentAndMinimum() {
        RedPacket packet = new RedPacket(
                "sender", "sender", RedPacket.RedPacketType.LUCKY,
                new BigDecimal("10.01"), 7, 5, new Random(7));
        List<BigDecimal> payouts = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            payouts.add(packet.claim("player-" + index));
        }

        assertEquals(new BigDecimal("10.01"), payouts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        payouts.forEach(amount -> assertEquals(2, amount.scale()));
        payouts.forEach(amount -> org.junit.jupiter.api.Assertions.assertTrue(
                amount.compareTo(new BigDecimal("0.01")) >= 0));
    }

    @Test
    void rollbackRestoresTheExactReservedCentSlot() {
        RedPacket packet = new RedPacket(
                "sender", "sender", RedPacket.RedPacketType.NORMAL,
                new BigDecimal("1.01"), 2, 5, new Random(1));
        BigDecimal first = packet.claim("one");
        assertEquals(new BigDecimal("0.50"), first);
        assertEquals(new BigDecimal("0.01"), packet.getRemainderAmount());
        org.junit.jupiter.api.Assertions.assertTrue(packet.rollbackClaim("one"));
        assertEquals(first, packet.claim("two"));
    }

    @Test
    void rejectsMorePacketsThanAvailableCents() {
        assertThrows(IllegalArgumentException.class, () -> new RedPacket(
                "sender", "sender", RedPacket.RedPacketType.NORMAL,
                new BigDecimal("0.01"), 2, 5));
    }
}
