package com.miclesworkshop.halalbot.commands;

import com.miclesworkshop.halalbot.HalalBot;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

public abstract class AbstractCommands {
    HalalBot bot;

    AbstractCommands(HalalBot bot) {
        this.bot = bot;
    }

    public void parseMessage(Server server, User user, ServerTextChannel channel, Message message) {
        String content = message.getContent();

        if (!content.startsWith("*")) {
            return;
        }

        String[] split = content.split(" ");

        String channelName = channel.getName();
        String[] args = new String[split.length - 1];

        if (args.length >= 0) {
            System.arraycopy(split, 1, args, 0, args.length);
        }

        executeCommand(server, user, channel, message, channelName, split[0].toLowerCase(), args);
    }

    protected abstract void executeCommand(Server server, User user, ServerTextChannel channel, Message message,
                                           String channelName, String cmd, String[] args);
}
