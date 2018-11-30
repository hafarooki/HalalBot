package com.miclesworkshop.halalbot;

import org.apache.commons.cli.*;

public class Main {
    public static void main(String[] args) {
        Options options = new Options();

        Option option = new Option("t", "token", true, "Discord API Token");
        option.setRequired(true);
        options.addOption(option);

        CommandLineParser parser = new DefaultParser();

        CommandLine line;

        try {
            line = parser.parse(options, args);
        } catch (ParseException exp) {
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            System.exit(1);
            return;
        }

        String token = line.getOptionValue("token");

        new HalalBot(token);
    }
}
