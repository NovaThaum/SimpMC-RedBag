package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public final class ConfigManager {

    private final SimpMCRedBag plugin;
    private FileConfiguration config;

    public ConfigManager(SimpMCRedBag plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        plugin.getLogger().info("SimpMC-RedBag configuration loaded");
    }

    public void reloadConfig() {
        loadConfig();
    }

    public BigDecimal getMinTotalAmount() {
        return readAmount("redpacket.min-total-amount", "1.00");
    }

    public int getMaxPacketCount() {
        return config.getInt("redpacket.max-packet-count", 50);
    }

    public long getExpirationMinutes() {
        return config.getLong("redpacket.expiration-minutes", 5L);
    }

    public BigDecimal getMinSingleAmount() {
        return readAmount("redpacket.min-single-amount", "0.01");
    }

    public long getCreationTimeoutSeconds() {
        return config.getLong("redpacket.creation-timeout-seconds", 300L);
    }

    public BigDecimal getTaxRate() {
        String value = config.getString("tax.rate", "0.10");
        try {
            return new BigDecimal(value).stripTrailingZeros();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid decimal at tax.rate: " + value);
            return new BigDecimal("0.10");
        }
    }

    public String getTaxRecipient() {
        return config.getString("tax.recipient", "Minecraft0122").trim();
    }

    public long getSendCooldownSeconds() {
        return config.getLong("anti-abuse.send-cooldown-seconds", 3L);
    }

    public int getMaxActivePerSender() {
        return config.getInt("anti-abuse.max-active-per-sender", 5);
    }

    public String getPrefix() {
        return color(config.getString("messages.prefix", "&6[SimpMC-RedBag] &r"));
    }

    public String getGuiTitle() {
        return color(config.getString("messages.gui.main-title", "&c&lSimpMC 红包"));
    }

    public String getMessage(String path) {
        return getMessage(path, new String[0]);
    }

    public String getMessage(String path, String... replacements) {
        String message = getPrefix() + getRawMessage(path);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        return message;
    }

    public String getRawMessage(String path) {
        return color(config.getString(path, "Message not found: " + path));
    }

    public String getItemMaterial(String path) {
        return config.getString(path, "STONE");
    }

    public String getItemName(String path) {
        return color(config.getString(path, "Unknown Item"));
    }

    public String[] getItemLore(String path) {
        List<String> lore = config.getStringList(path);
        return lore.stream().map(ConfigManager::color).toArray(String[]::new);
    }

    public int getItemSlot(String path) {
        return config.getInt(path, 0);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }

    public boolean validateConfig() {
        boolean valid = true;
        if (!MoneyMath.isPositive(getMinTotalAmount())) {
            plugin.getLogger().warning("redpacket.min-total-amount must be positive");
            valid = false;
        }
        if (!MoneyMath.isPositive(getMinSingleAmount())
                || getMinSingleAmount().compareTo(MoneyMath.CENT) < 0) {
            plugin.getLogger().warning("redpacket.min-single-amount must be at least 0.01");
            valid = false;
        }
        if (getMaxPacketCount() <= 0 || getExpirationMinutes() <= 0
                || getCreationTimeoutSeconds() <= 0) {
            plugin.getLogger().warning("redpacket limits and expiration must be positive");
            valid = false;
        }
        int normalSlot = getItemSlot("items.normal-packet.slot");
        int luckySlot = getItemSlot("items.lucky-packet.slot");
        if (normalSlot < 0 || normalSlot >= 27 || luckySlot < 0 || luckySlot >= 27
                || normalSlot == luckySlot) {
            plugin.getLogger().warning("GUI packet slots must be distinct values from 0 to 26");
            valid = false;
        }
        BigDecimal taxRate = getTaxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            plugin.getLogger().warning("tax.rate must be between 0 and 1");
            valid = false;
        }
        if (getTaxRecipient().isBlank()) {
            plugin.getLogger().warning("tax.recipient must not be blank");
            valid = false;
        }
        if (getSendCooldownSeconds() < 0 || getMaxActivePerSender() <= 0) {
            plugin.getLogger().warning("anti-abuse settings are invalid");
            valid = false;
        }
        return valid;
    }

    private BigDecimal readAmount(String path, String fallback) {
        String value = config.getString(path, fallback);
        try {
            return MoneyMath.normalize(new BigDecimal(value));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid decimal at " + path + ": " + value);
            return MoneyMath.normalize(new BigDecimal(fallback));
        }
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
