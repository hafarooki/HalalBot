package com.miclesworkshop.halalbot.commands;

import com.miclesworkshop.halalbot.HalalBot;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class JailCommands extends AbstractCommands {
    public JailCommands(HalalBot bot) {
        super(bot);
    }

    private Logger log = Logger.getLogger(getClass().getName());

    @Override
    protected void executeCommand(Server server, User user, ServerTextChannel channel, Message message,
                                  String channelName, String cmd, String[] args) {
        if (!cmd.equals("*jail") && !cmd.equals("*unjail")) {
            return;
        }

        boolean jail = cmd.equals("*jail");

        if (!server.hasPermission(user, PermissionType.KICK_MEMBERS)) {
            channel.sendMessage(user.getMentionTag() + " You don't have the KICK_MEMBERS permission!");
            return;
        }

        if (message.getMentionedUsers().isEmpty()) {
            channel.sendMessage("Usage: " + cmd + " <user(s)>");
        }

        for (User target : message.getMentionedUsers()) {
            if (jail && server.hasPermission(target, PermissionType.KICK_MEMBERS)) {
                channel.sendMessage("Can't jail " + target.getDiscriminatedName());
                continue;
            }

            if (isJailed(target, server) == jail) {
                if (jail) {
                    channel.sendMessage(target.getDiscriminatedName() + " is already jailed!");
                } else {
                    channel.sendMessage(target.getDiscriminatedName() + " is already not jailed!");
                }
                continue;
            }

            try {
                if (jail) {
                    target.addRole(bot.getJailedRole(server), "Jailed by " + user.getDiscriminatedName()).get();
                    target.sendMessage("You have been jailed in " + server.getName() + "!");
                    channel.sendMessage("Jailed " + target.getDiscriminatedName());
                } else {
                    target.removeRole(bot.getJailedRole(server), "Unjailed by " + user.getDiscriminatedName()).get();
                    target.sendMessage("You have been unjailed in " + server.getName() + "!");
                    channel.sendMessage("Unjailed " + target.getDiscriminatedName());
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isJailed(User user, Server server) {
        return server.getRoles(user).contains(bot.getJailedRole(server));
    }
}
