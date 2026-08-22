package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyManager {

    private final SimpMCRedBag plugin;
    private Economy economy;

    public EconomyManager(SimpMCRedBag plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault is not installed");
            return;
        }
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            plugin.getLogger().severe("No Vault economy provider is registered");
            return;
        }
        economy = registration.getProvider();
        plugin.getLogger().info("Economy initialized with " + economy.getName());
    }

    public boolean isEconomyEnabled() {
        return economy != null;
    }

    public BigDecimal getBalance(Player player) {
        return getBalance((OfflinePlayer) player);
    }

    public BigDecimal getBalance(OfflinePlayer player) {
        if (!isEconomyEnabled()) {
            return BigDecimal.ZERO.setScale(MoneyMath.SCALE);
        }
        try {
            return MoneyMath.normalize(BigDecimal.valueOf(economy.getBalance(player)));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to read balance: " + exception.getMessage());
            return BigDecimal.ZERO.setScale(MoneyMath.SCALE);
        }
    }

    public boolean hasEnough(Player player, BigDecimal amount) {
        if (!isEconomyEnabled() || !MoneyMath.isPositive(amount)) {
            return false;
        }
        try {
            return economy.has(player, MoneyMath.normalize(amount).doubleValue());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to check balance for " + player.getName()
                    + ": " + exception.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, BigDecimal amount) {
        EconomyResponse response = withdrawResponse(player, amount);
        return response != null && response.transactionSuccess();
    }

    public EconomyResponse withdrawResponse(OfflinePlayer player, BigDecimal amount) {
        if (!isEconomyEnabled() || !MoneyMath.isPositive(amount)) {
            return null;
        }
        try {
            return economy.withdrawPlayer(player, MoneyMath.normalize(amount).doubleValue());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Economy withdrawal failed: " + exception.getMessage());
            return null;
        }
    }

    public boolean deposit(Player player, BigDecimal amount) {
        EconomyResponse response = deposit(player == null ? null : (OfflinePlayer) player, amount);
        return response != null && response.transactionSuccess();
    }

    public EconomyResponse deposit(OfflinePlayer player, BigDecimal amount) {
        if (!isEconomyEnabled() || player == null || !MoneyMath.isPositive(amount)) {
            return null;
        }
        try {
            return economy.depositPlayer(player, MoneyMath.normalize(amount).doubleValue());
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Economy deposit failed: " + exception.getMessage());
            return null;
        }
    }

    public String formatAmount(BigDecimal amount) {
        BigDecimal normalized = MoneyMath.normalize(amount);
        if (!isEconomyEnabled()) {
            return normalized.toPlainString();
        }
        try {
            return economy.format(normalized.doubleValue());
        } catch (RuntimeException exception) {
            return normalized.toPlainString();
        }
    }

    public String getCurrencyNameSingular() {
        if (!isEconomyEnabled()) {
            return "金币";
        }
        String name = economy.currencyNameSingular();
        return name == null || name.isBlank() ? "金币" : name;
    }

    public String getCurrencyNamePlural() {
        if (!isEconomyEnabled()) {
            return "金币";
        }
        String name = economy.currencyNamePlural();
        return name == null || name.isBlank() ? "金币" : name;
    }

    public BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank() || !value.matches("\\+?(?:\\d+(?:\\.\\d{1,2})?|\\.\\d{1,2})")) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value).setScale(MoneyMath.SCALE, RoundingMode.UNNECESSARY);
            return amount.signum() > 0 ? amount : null;
        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }

    public Integer parseCount(String value) {
        if (value == null || !value.matches("[1-9]\\d*")) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
