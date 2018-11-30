package com.miclesworkshop.halalbot;

import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ApprovalCommands {
    private Logger log = Logger.getLogger(getClass().getName());

    private HalalBot bot;

    public ApprovalCommands(HalalBot halalBot) {
        this.bot = halalBot;
    }

    public void parseMessage(Server server, User user, ServerTextChannel channel, Message message) {
        String content = message.getContent();
        if (!content.startsWith("*")) return;

        String[] split = content.split(" ");

        String channelName = channel.getName();


        switch (split[0]) {
            case "*approve": {
                if (!checkModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                if (split.length != 2) {
                    channel.sendMessage("Usage: *approve <role key>");
                    return;
                }

                String key = split[1].toLowerCase();

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
                    channel.delete(approvedUser.getName() + " was approved by " + approvedUser.getName() + ".");

                    // inform the person who was approved
                    approvedUser.sendMessage("You were approved in" + server.getName() + " as a " + role.getName() + "!");

                    // inform the person who approved them
                    user.sendMessage("Approved user " + approvedUser.getName() + " as a " + role.getName());

                    log.info(user.getName() + " approved " + approvedUser.getName() + " as a " + role.getName());
                });

                break;
            }
            case "*ban": {
                if (!checkModerator(server, channel, user)) {
                    return;
                }

                if (!ensureApprovalChannel(channel)) {
                    return;
                }

                if (split.length == 1) {
                    channel.sendMessage("Reason required! Usage: `*ban <reason>`");
                    return;
                }

                String reason = content.substring(5);

                bot.getDiscordApi().getUserById(channelName.replaceFirst("approval-", "")).thenAccept(bannedUser -> {
                    // remove the channel
                    channel.delete(bannedUser.getName() + " was denied & banned by " + bannedUser.getName() + ". " +
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
            case "*listroles": {
                if (!checkPermission(server, channel, user, PermissionType.MANAGE_ROLES)) {
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
                if (!checkPermission(server, channel, user, PermissionType.MANAGE_ROLES)) {
                    return;
                }

                if (split.length < 3) {
                    channel.sendMessage("Usage: *addrole <key> <actual role name>");
                    return;
                }

                String key = split[1].toLowerCase();

                String roleName = content.substring(("*addrole " + key + " ").length());
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
                if (!checkPermission(server, channel, user, PermissionType.MANAGE_ROLES)) {
                    return;
                }

                if (split.length != 2) {
                    channel.sendMessage("Usage: *removerole <key>");
                    return;
                }

                String key = split[1].toLowerCase();

                ServerData serverData = bot.getServerData(server);

                if (serverData.getRoles().remove(key) == null) {
                    channel.sendMessage("Role " + key + " not found! Use *listroles for a list.");
                    return;
                }

                bot.saveData();

                channel.sendMessage("Success! Unregistered role " + key + ".");

                log.info(user.getName() + " removed role with key " + key);
                break;
            }
        }
    }

    private boolean ensureApprovalChannel(ServerTextChannel channel) {
        return channel.getName().startsWith("approval-") && channel.getCategory().orElse(null) == bot.getApprovalCategory(channel.getServer());
    }

    private boolean checkModerator(Server server, TextChannel channel, User user) {
        if (!server.getRoles(user).contains(bot.getApprovalModeratorRole(server))) {
            channel.sendMessage(user.getMentionTag() + " you aren't an approval moderator!");
            return false;
        }

        return true;
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
