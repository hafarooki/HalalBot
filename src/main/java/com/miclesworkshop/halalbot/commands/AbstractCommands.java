package com.miclesworkshop.halalbot.commands;

import com.miclesworkshop.halalbot.HalalBot;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.logging.Logger;

public abstract class AbstractCommands {
    private Logger log = Logger.getLogger(AbstractCommands.class.getName());

    protected HalalBot bot;

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

        String cmd = split[0].toLowerCase();
        executeCommand(server, user, channel, message, channelName, cmd, args);
    }

    protected abstract void executeCommand(Server server, User user, ServerTextChannel channel, Message message,
                                           String channelName, String cmd, String[] args);
}
