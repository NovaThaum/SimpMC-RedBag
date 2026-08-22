package cn.simpmc.redbag.task;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.model.RedPacket;
import cn.simpmc.redbag.util.SchedulerCompat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RedPacketTask {

    private final SimpMCRedBag plugin;
    private SchedulerCompat.CancellableTask repeatingTask;

    public RedPacketTask(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    public void run() {
        try {
            for (RedPacket packet : plugin.getRedPacketManager().getExpiredPackets()) {
                plugin.getRedPacketManager().processExpiredPacket(packet.getId());
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getRedPacketManager().processRefund(player);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Red packet maintenance failed: " + exception.getMessage());
            if (plugin.getConfigManager().isDebugEnabled()) {
                exception.printStackTrace();
            }
        }
    }

    public void start() {
        repeatingTask = SchedulerCompat.runAtFixedRateGlobal(plugin, this::run, 20L, 600L);
    }

    public void stop() {
        if (repeatingTask != null) {
            repeatingTask.cancel();
            repeatingTask = null;
        }
    }
}
