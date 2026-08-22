package cn.simpmc.redbag;

import cn.simpmc.redbag.command.RedPacketCommand;
import cn.simpmc.redbag.gui.RedPacketGUI;
import cn.simpmc.redbag.listener.PlayerListener;
import cn.simpmc.redbag.manager.ChatManager;
import cn.simpmc.redbag.manager.ConfigManager;
import cn.simpmc.redbag.manager.EconomyManager;
import cn.simpmc.redbag.manager.RedPacketManager;
import cn.simpmc.redbag.manager.RedPacketService;
import cn.simpmc.redbag.manager.TaxManager;
import cn.simpmc.redbag.task.RedPacketTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpMCRedBag extends JavaPlugin {

    private ConfigManager configManager;
    private EconomyManager economyManager;
    private TaxManager taxManager;
    private RedPacketManager redPacketManager;
    private RedPacketService redPacketService;
    private ChatManager chatManager;
    private RedPacketGUI redPacketGUI;
    private RedPacketTask redPacketTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        if (!configManager.validateConfig()) {
            getLogger().severe("Configuration validation failed; disabling SimpMC-RedBag");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyManager = new EconomyManager(this);
        if (!economyManager.isEconomyEnabled()) {
            getLogger().severe("Vault economy is unavailable; disabling SimpMC-RedBag");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        taxManager = new TaxManager(this, economyManager);
        redPacketManager = new RedPacketManager(this);
        redPacketService = new RedPacketService(this);
        chatManager = new ChatManager(this);
        redPacketGUI = new RedPacketGUI(this);

        RedPacketCommand command = new RedPacketCommand(this);
        PluginCommand pluginCommand = getCommand("redbag");
        if (pluginCommand == null) {
            getLogger().severe("Command redbag is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        redPacketTask = new RedPacketTask(this);
        redPacketTask.start();
        getLogger().info("SimpMC-RedBag enabled; tax rate=" + configManager.getTaxRate()
                + ", tax account=" + configManager.getTaxRecipient());
    }

    @Override
    public void onDisable() {
        if (redPacketTask != null) {
            redPacketTask.stop();
        }
        if (redPacketManager != null) {
            redPacketManager.refundAllActivePackets();
        }
        if (redPacketGUI != null) {
            redPacketGUI.clearAllSessions();
        }
        getLogger().info("SimpMC-RedBag disabled");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public TaxManager getTaxManager() {
        return taxManager;
    }

    public RedPacketManager getRedPacketManager() {
        return redPacketManager;
    }

    public RedPacketService getRedPacketService() {
        return redPacketService;
    }

    public ChatManager getChatManager() {
        return chatManager;
    }

    public RedPacketGUI getRedPacketGUI() {
        return redPacketGUI;
    }
}
