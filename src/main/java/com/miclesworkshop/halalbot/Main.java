package com.miclesworkshop.halalbot;

import org.apache.commons.cli.*;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        Options options = new Options();

        addOption(options, "datafolder", "Data Folder");
        addOption(options, "token", "Discord API Token");

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

        File dataFolder = new File(line.getOptionValue("datafolder"));

        if (dataFolder.mkdirs()) {
            System.out.println("Created data folder " + dataFolder.getPath());
        }

        new HalalBot(dataFolder, token);
    }

    private static void addOption(Options options, String opt, String description) {
        Option option = new Option(opt, true, description);
        option.setRequired(true);
        options.addOption(option);
    }
}
