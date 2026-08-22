package cn.simpmc.redbag.gui;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.manager.RedPacketService;
import cn.simpmc.redbag.model.RedPacket;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RedPacketGUI {

    private final SimpMCRedBag plugin;
    private final Map<UUID, RedPacketCreationSession> creationSessions = new ConcurrentHashMap<>();

    public RedPacketGUI(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    public void openMainGUI(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, plugin.getConfigManager().getGuiTitle());
        fillDecorationItems(inventory);
        inventory.setItem(plugin.getConfigManager().getItemSlot("items.normal-packet.slot"),
                createItem("items.normal-packet"));
        inventory.setItem(plugin.getConfigManager().getItemSlot("items.lucky-packet.slot"),
                createItem("items.lucky-packet"));
        player.openInventory(inventory);
    }

    private void fillDecorationItems(Inventory inventory) {
        Material material = material("items.decoration.material", Material.BLACK_STAINED_GLASS_PANE);
        ItemStack decoration = new ItemStack(material);
        ItemMeta meta = decoration.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getItemName("items.decoration.name"));
            decoration.setItemMeta(meta);
        }
        int normalSlot = plugin.getConfigManager().getItemSlot("items.normal-packet.slot");
        int luckySlot = plugin.getConfigManager().getItemSlot("items.lucky-packet.slot");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != normalSlot && slot != luckySlot) {
                inventory.setItem(slot, decoration);
            }
        }
    }

    private ItemStack createItem(String path) {
        Material fallback = path.contains("lucky") ? Material.GOLD_INGOT : Material.RED_WOOL;
        ItemStack item = new ItemStack(material(path + ".material", fallback));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getConfigManager().getItemName(path + ".name"));
            meta.setLore(Arrays.asList(plugin.getConfigManager().getItemLore(path + ".lore")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material material(String path, Material fallback) {
        try {
            return Material.valueOf(plugin.getConfigManager().getItemMaterial(path).toUpperCase());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }
        if (slot == plugin.getConfigManager().getItemSlot("items.normal-packet.slot")) {
            startRedPacketCreation(player, RedPacket.RedPacketType.NORMAL);
        } else if (slot == plugin.getConfigManager().getItemSlot("items.lucky-packet.slot")) {
            startRedPacketCreation(player, RedPacket.RedPacketType.LUCKY);
        }
        player.closeInventory();
    }

    private void startRedPacketCreation(Player player, RedPacket.RedPacketType type) {
        creationSessions.put(player.getUniqueId(), new RedPacketCreationSession(
                type, plugin.getConfigManager().getCreationTimeoutSeconds()));
        player.sendMessage(plugin.getConfigManager().getMessage("messages.prompts.enter-amount"));
        player.sendMessage(plugin.getConfigManager().getRawMessage("messages.prompts.cancel-hint"));
    }

    public boolean handleChatInput(Player player, String message) {
        UUID id = player.getUniqueId();
        RedPacketCreationSession session = creationSessions.get(id);
        if (session == null) {
            return false;
        }
        if ("cancel".equalsIgnoreCase(message.trim())) {
            creationSessions.remove(id);
            player.sendMessage(plugin.getConfigManager().getMessage("messages.success.cancelled"));
            return true;
        }
        if (session.isExpired()) {
            creationSessions.remove(id);
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.packet-not-found"));
            return true;
        }
        if (session.getStep() == RedPacketCreationSession.Step.WAITING_FOR_AMOUNT) {
            return handleAmountInput(player, session, message);
        }
        if (session.getStep() == RedPacketCreationSession.Step.WAITING_FOR_COUNT) {
            return handleCountInput(player, session, message);
        }
        return false;
    }

    private boolean handleAmountInput(Player player, RedPacketCreationSession session, String input) {
        BigDecimal amount = plugin.getEconomyManager().parseAmount(input.trim());
        if (amount == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.invalid-amount"));
            return true;
        }
        if (amount.compareTo(plugin.getConfigManager().getMinTotalAmount()) < 0) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.amount-too-low",
                    "{min}", plugin.getEconomyManager().formatAmount(
                            plugin.getConfigManager().getMinTotalAmount())));
            return true;
        }
        session.setAmount(amount);
        session.setStep(RedPacketCreationSession.Step.WAITING_FOR_COUNT);
        player.sendMessage(plugin.getConfigManager().getMessage("messages.prompts.enter-count"));
        player.sendMessage(plugin.getConfigManager().getRawMessage("messages.prompts.cancel-hint"));
        return true;
    }

    private boolean handleCountInput(Player player, RedPacketCreationSession session, String input) {
        Integer count = plugin.getEconomyManager().parseCount(input.trim());
        if (count == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.invalid-count"));
            return true;
        }
        RedPacketService.Result result = plugin.getRedPacketService().send(
                player, session.getType(), session.getAmount(), count);
        if (!result.isSuccess()) {
            plugin.getChatManager().sendResultError(player, result);
            return true;
        }
        creationSessions.remove(player.getUniqueId());
        plugin.getChatManager().sendCreatedMessage(player, result);
        plugin.getChatManager().broadcastRedPacket(result.packet());
        return true;
    }

    public RedPacketCreationSession getCreationSession(UUID playerId) {
        return creationSessions.get(playerId);
    }

    public void removeCreationSession(UUID playerId) {
        creationSessions.remove(playerId);
    }

    public void clearAllSessions() {
        creationSessions.clear();
    }

    public void cleanup() {
        clearAllSessions();
    }
}
