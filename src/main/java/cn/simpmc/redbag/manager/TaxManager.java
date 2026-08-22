package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Calculates and settles the sender surcharge as one atomic business step. */
public final class TaxManager {

    private final SimpMCRedBag plugin;
    private final EconomyManager economy;

    public TaxManager(SimpMCRedBag plugin, EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public BigDecimal calculateTax(BigDecimal principal) {
        return calculator().tax(principal);
    }

    public BigDecimal calculateCharge(BigDecimal principal) {
        return calculator().charge(principal);
    }

    /**
     * Withdraws x*(1+y) and sends the y part to the configured account. If
     * the tax deposit fails, the sender is compensated and the operation fails.
     */
    public boolean collect(Player sender, BigDecimal principal) {
        BigDecimal tax = calculateTax(principal);
        BigDecimal charge = MoneyMath.normalize(principal.add(tax));
        if (!economy.withdraw(sender, charge)) {
            return false;
        }
        if (tax.signum() == 0) {
            return true;
        }

        OfflinePlayer recipient = plugin.getServer().getOfflinePlayer(
                plugin.getConfigManager().getTaxRecipient());
        EconomyResponse response = economy.deposit(recipient, tax);
        if (response != null && response.transactionSuccess()) {
            return true;
        }

        // Never leave the player charged when the configured tax account is
        // unavailable. A failed compensation is logged for manual recovery.
        if (!economy.deposit(sender, charge)) {
            plugin.getLogger().severe("Tax deposit failed and charge compensation also failed for "
                    + sender.getName() + " (amount " + charge + ")");
        }
        plugin.getLogger().warning("Unable to deposit tax " + tax + " to "
                + plugin.getConfigManager().getTaxRecipient());
        return false;
    }

    public String recipientName() {
        return plugin.getConfigManager().getTaxRecipient();
    }

    private TaxCalculator calculator() {
        return new TaxCalculator(plugin.getConfigManager().getTaxRate());
    }
}
