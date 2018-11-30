package com.miclesworkshop.halalbot;

import org.javacord.api.entity.message.Message;

import java.util.logging.Logger;

public class Commands {
    private Logger log = Logger.getLogger(getClass().getName());

    private HalalBot bot;

    public Commands(HalalBot halalBot) {
        this.bot = halalBot;
    }

    public void parseMessage(Message message) {
        String content = message.getContent();
        if (!content.startsWith("*")) return;

        String[] split = content.split(" ");
    }
}
