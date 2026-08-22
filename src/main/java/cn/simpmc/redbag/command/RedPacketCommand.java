package cn.simpmc.redbag.command;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.manager.RedPacketService;
import cn.simpmc.redbag.model.RedPacket;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class RedPacketCommand implements CommandExecutor, TabCompleter {

    private final SimpMCRedBag plugin;

    public RedPacketCommand(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("messages.errors.player-only"));
            return true;
        }
        if (!player.hasPermission("simpmc.redbag.use")) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.no-permission"));
            return true;
        }
        if (!plugin.getEconomyManager().isEconomyEnabled()) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.economy-disabled"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "open" -> {
                plugin.getRedPacketGUI().openMainGUI(player);
                yield true;
            }
            case "send", "create" -> {
                handleSend(player, args);
                yield true;
            }
            case "claim" -> {
                if (args.length < 2) {
                    sendHelp(player);
                } else {
                    plugin.getChatManager().handleClaimCommand(player, args[1]);
                }
                yield true;
            }
            case "reload" -> {
                handleReload(player);
                yield true;
            }
            case "info" -> {
                handleInfo(player);
                yield true;
            }
            case "help" -> {
                sendHelp(player);
                yield true;
            }
            default -> {
                sendHelp(player);
                yield true;
            }
        };
    }

    private void handleSend(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.help.send"));
            return;
        }
        BigDecimal amount = plugin.getEconomyManager().parseAmount(args[1]);
        Integer count = plugin.getEconomyManager().parseCount(args[2]);
        RedPacket.RedPacketType type = RedPacket.RedPacketType.NORMAL;
        if (args.length >= 4) {
            if ("lucky".equalsIgnoreCase(args[3])) {
                type = RedPacket.RedPacketType.LUCKY;
            } else if (!"normal".equalsIgnoreCase(args[3])) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.operation-failed"));
                return;
            }
        }
        if (amount == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.invalid-amount"));
            return;
        }
        if (count == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.invalid-count"));
            return;
        }
        RedPacketService.Result result = plugin.getRedPacketService().send(player, type, amount, count);
        if (!result.isSuccess()) {
            plugin.getChatManager().sendResultError(player, result);
            return;
        }
        plugin.getChatManager().sendCreatedMessage(player, result);
        plugin.getChatManager().broadcastRedPacket(result.packet());
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("simpmc.redbag.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.no-permission"));
            return;
        }
        plugin.getConfigManager().reloadConfig();
        if (plugin.getConfigManager().validateConfig()) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.success.config-reloaded"));
        } else {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.config-invalid"));
        }
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("simpmc.redbag.admin")) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.no-permission"));
            return;
        }
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§e=== SimpMC-RedBag 状态 ===");
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§7活动红包: §a"
                + plugin.getRedPacketManager().getActivePacketCount());
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§7税率: §a"
                + plugin.getConfigManager().getTaxRate().toPlainString());
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§7税收账户: §a"
                + plugin.getConfigManager().getTaxRecipient());
        player.sendMessage(plugin.getConfigManager().getPrefix() + "§7版本: §a"
                + plugin.getDescription().getVersion());
    }

    private void sendHelp(Player player) {
        String prefix = plugin.getConfigManager().getPrefix();
        player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.header"));
        player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.send"));
        player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.open"));
        player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.claim"));
        player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.help"));
        if (player.hasPermission("simpmc.redbag.admin")) {
            player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.reload"));
            player.sendMessage(prefix + plugin.getConfigManager().getRawMessage("messages.help.info"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>(Arrays.asList("send", "open", "claim", "help"));
            if (sender.hasPermission("simpmc.redbag.admin")) {
                values.addAll(Arrays.asList("reload", "info"));
            }
            return values.stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 4 && ("send".equalsIgnoreCase(args[0]) || "create".equalsIgnoreCase(args[0]))) {
            return Arrays.stream(new String[] {"normal", "lucky"})
                    .filter(value -> value.startsWith(args[3].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
