package com.miclesworkshop.halalbot.commands;

import com.miclesworkshop.halalbot.HalalBot;
import com.miclesworkshop.halalbot.ServerData;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ApprovalCommands extends AbstractCommands {
    private Logger log = Logger.getLogger(getClass().getName());

    public ApprovalCommands(HalalBot halalBot) {
        super(halalBot);
    }

    @Override
    protected void executeCommand(Server server, User user, ServerTextChannel channel, Message message,
                                  String channelName, String cmd, String[] args) {
        switch (cmd) {
            case "*approve": {
                if (isNotModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                if (args.length != 1) {
                    channel.sendMessage("Usage: *approve <role key>");
                    return;
                }

                String key = args[0].toLowerCase();

                ServerData serverData = bot.getServerData(server);

                if (!serverData.getRoles().containsKey(key)) {
                    channel.sendMessage("Role " + key + " not found! For a list of roles, see *listroles.");
                    return;
                }

                Optional<Role> optionalRole = server.getRoleById(serverData.getRoles().get(key));

                if (!optionalRole.isPresent()) {
                    channel.sendMessage("Role " + key + " is no longer available. " +
                            "To remove the role key use `*removerole " + key + "`." +
                            "To change it use `*addrole key`.");
                    return;
                }

                Role role = optionalRole.get();

                bot.getDiscordApi().getUserById(channelName.replaceFirst("approval-", "")).thenAccept(approvedUser -> {
                    // remove all old roles
                    server.getRoles(approvedUser).forEach(oldRole -> server.removeRoleFromUser(approvedUser, oldRole));

                    // add new role
                    server.addRoleToUser(approvedUser, role);

                    // deleted channel
                    bot.deleteChannel(channel, approvedUser.getName() + " was approved by " + approvedUser.getName() + ".");

                    // inform the person who was approved
                    approvedUser.sendMessage("You were approved in " + server.getName() + " as a " + role.getName() + "!");

                    // inform the person who approved them
                    user.sendMessage("Approved user " + approvedUser.getName() + " as a " + role.getName());

                    log.info(user.getName() + " approved " + approvedUser.getName() + " as a " + role.getName());
                });

                break;
            }
            case "*vc": {
                if (isNotModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                message.delete();

                String id = channelName.replaceFirst("approval-", "");

                bot.getDiscordApi().getUserById(id).thenAccept(approvedUser ->
                    channel.sendMessage(approvedUser.getMentionTag()
                            +  " please join Approval-Voice!")
                );

                break;
            }
            case "*ban": {
                if (isNotModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                if (args.length == 0) {
                    channel.sendMessage("Reason required! Usage: `*ban <reason>`");
                    return;
                }

                String reason = String.join(" ", args);

                bot.getDiscordApi().getUserById(channelName.replaceFirst("approval-", "")).thenAccept(bannedUser -> {
                    // remove the channel
                    bot.deleteChannel(channel, bannedUser.getName() + " was denied & banned by " + bannedUser.getName() + ". " +
                            "Reason: " + reason);

                    // inform the banned user
                    bannedUser.sendMessage("You were banned from " + server.getName() + ". Reason: " + reason);

                    // ban user from the server (do it after sending the private message in case it's necessary)
                    server.banUser(bannedUser);

                    // inform the one who banned them
                    user.sendMessage("Banned user " + bannedUser.getName() + " & deleted their channel.");

                    log.info(user.getName() + " banned " + bannedUser.getName() + " for " + reason);
                });

                return;
            }
            case "*apply": {
                ServerTextChannel limboChannel = bot.getOrCreateLimboChannel(server);

                if (channel.getId() != limboChannel.getId()) {
                    channel.sendMessage("This can only be done in " + limboChannel.getMentionTag());
                    return;
                }

                bot.createApprovalChannelIfAbsent(server, user);
                break;
            }
            case "*close": {
                if (isNotModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                if (args.length == 0) {
                    channel.sendMessage("Reason required! Usage: `*close <reason>`");
                    return;
                }

                String reason = String.join(" ", args);

                bot.closeApprovalChannel(channel, reason, user);
                break;
            }
            case "*addmod":
            case "*removemod": {
                if (missingRolePermission(server, user, channel)) {
                    return;
                }

                if (missingMentionedUser(channel, message)) {
                    return;
                }

                Role approvalModeratorRole = bot.getApprovalModeratorRole(server);

                for (User newMod : message.getMentionedUsers()) {
                    if (cmd.equals("*addmod")) {
                        if (server.getRoles(newMod).contains(approvalModeratorRole)) {
                            channel.sendMessage(newMod.getName() + " is already an approval moderator!");
                            continue;
                        }

                        server.addRoleToUser(newMod, approvalModeratorRole);
                        newMod.sendMessage("You've been made an approval moderator by " + user.getName() + " in " + server.getName() + "!");
                        channel.sendMessage("Made " + newMod.getName() + " an approval moderator.");

                        log.info(user.getName() + " made " + newMod.getName() + " an approval moderator in " + server.getName());
                    } else {
                        if (!server.getRoles(newMod).contains(approvalModeratorRole)) {
                            channel.sendMessage(newMod.getName() + " isn't an approval moderator!");
                            continue;
                        }

                        server.removeRoleFromUser(newMod, approvalModeratorRole);
                        newMod.sendMessage("You've been removed as an approval moderator by " + user.getName() + " in " + server.getName() + "!");
                        channel.sendMessage("Made " + newMod.getName() + " not approval moderator.");

                        log.info(user.getName() + " made " + newMod.getName() + " no longer an approval moderator in " + server.getName());
                    }
                }

                break;
            }
            case "*listroles": {
                if (missingRolePermission(server, user, channel)) {
                    return;
                }

                ServerData serverData = bot.getServerData(server);

                Map<String, Long> roles = serverData.getRoles();
                String rolesString = roles.isEmpty() ? "None" : roles.entrySet().stream()
                        .map(p -> p.getKey() + ": " + p.getValue())
                        .collect(Collectors.joining("\n"));

                channel.sendMessage("**Current Roles (Role Name to Role ID) (Use *addrole to add roles)**\n" + rolesString);

                break;
            }
            case "*addrole": {
                if (missingRolePermission(server, user, channel)) {
                    return;
                }

                if (args.length < 2) {
                    channel.sendMessage("Usage: *addrole <key> <actual role name>");
                    return;
                }

                String key = args[0].toLowerCase();

                String roleName = "";

                for (int i = 1; i < args.length; i++) {
                    roleName += args[i];
                    if (i != args.length - 1) {
                        roleName += " ";
                    }
                }

                Optional<Role> optionalRole = server.getRolesByNameIgnoreCase(roleName).stream().findFirst();

                if (!optionalRole.isPresent()) {
                    channel.sendMessage("Role " + roleName + " not found!");
                    return;
                }

                Role role = optionalRole.get();

                ServerData serverData = bot.getServerData(server);
                serverData.getRoles().put(key, role.getId());
                bot.saveData();

                channel.sendMessage("Success! Made role " + role.getName() + " mapped to " + key + ".\n" +
                        "You can now do `*approve " + key + "` to approve someone into this role.");

                log.info(user.getName() + " registered role " + role.getName() + " as " + key);
                break;
            }
            case "*removerole": {
                if (missingRolePermission(server, user, channel)) {
                    return;
                }

                if (args.length != 1) {
                    channel.sendMessage("Usage: *removerole <key>");
                    return;
                }

                String key = args[0].toLowerCase();

                ServerData serverData = bot.getServerData(server);

                if (!serverData.getRoles().containsKey(key)) {
                    channel.sendMessage("Role " + key + " not found! Use *listroles for a list.");
                    return;
                }

                serverData.getRoles().remove(key);
                bot.saveData();

                channel.sendMessage("Success! Unregistered role " + key + ".");

                log.info(user.getName() + " removed role with key " + key);
                break;
            }
        }
    }

    private boolean missingMentionedUser(ServerTextChannel channel, Message message) {
        if (message.getMentionedUsers().isEmpty()) {
            channel.sendMessage("You must mention the users you want to add as mods!");
            return true;
        }
        return false;
    }

    private boolean missingRolePermission(Server server, User user, ServerTextChannel channel) {
        return !checkPermission(server, channel, user, PermissionType.MANAGE_ROLES);
    }

    private boolean ensureApprovalChannel(ServerTextChannel channel) {
        return channel.getName().startsWith("approval-") && channel.getCategory().isPresent() && channel.getCategory().get().getName().startsWith("Approval");
    }

    private boolean isNotModerator(Server server, TextChannel channel, User user) {
        Role approvalModeratorRole = bot.getApprovalModeratorRole(server);

        List<Role> roles = user.getRoles(server);

        if (!roles.contains(approvalModeratorRole)) {
            channel.sendMessage(user.getMentionTag() + " you aren't an approval moderator!");
            return true;
        }

        return false;
    }

    private boolean checkPermission(Server server, TextChannel channel, User user, PermissionType... permissions) {
        if (!server.hasPermissions(user, permissions)) {
            String permissionString = Arrays.stream(permissions)
                    .map(PermissionType::name)
                    .collect(Collectors.joining(", "));

            channel.sendMessage(user.getMentionTag() + " you don't have the permission(s) " + permissionString + "!");
            return false;
        }

        return true;
    }
}
