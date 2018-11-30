package com.miclesworkshop.halalbot;

import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.channel.TextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Commands {
    private Logger log = Logger.getLogger(getClass().getName());

    private HalalBot bot;

    public Commands(HalalBot halalBot) {
        this.bot = halalBot;
    }

    public void parseMessage(Server server, User user, ServerTextChannel channel, Message message) {
        String content = message.getContent();
        if (!content.startsWith("*")) return;

        String[] split = content.split(" ");

        switch (split[0]) {
            case "*ban": {
                if (split.length == 1) {
                    channel.sendMessage("Reason required! Usage: `*ban <reason>`");
                    return;
                }

                if (!checkPermission(server, channel, user, PermissionType.BAN_MEMBERS)) {
                    return;
                }

                String channelName = channel.getName();

                if (!channelName.startsWith("approval-")) {
                    channel.sendMessage("Not an approval channel!");
                    return;
                }

                String reason = content.substring(5);

                bot.getDiscordApi().getUserById(channelName.replaceFirst("approval-", "")).thenAccept(bannedUser -> {
                    server.banUser(bannedUser);
                    channel.delete("User was denied & banned by " + bannedUser.getName() + ". Reason: " + reason);
                    bannedUser.sendMessage("You were banned from " + server.getName() + ". Reason: " + reason);
                    user.sendMessage("Banned user " + bannedUser.getName() + " & deleted their channel.");
                });
            }
        }
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
