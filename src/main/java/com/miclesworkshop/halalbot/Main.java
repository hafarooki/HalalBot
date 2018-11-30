package com.miclesworkshop.halalbot;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

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


        File lockFile = new File(dataFolder, "instance.lock");
        try {
            if (lockFile.createNewFile()) {
                System.out.println("Created lock file " + lockFile.getPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (FileChannel channel = FileChannel.open(lockFile.toPath(), StandardOpenOption.WRITE)) {
            try (FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    System.out.println("Lock file is in use already!");
                    System.exit(3);
                }

                new HalalBot(dataFolder, token);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    private static void addOption(Options options, String opt, String description) {
        Option option = new Option(opt, true, description);
        option.setRequired(true);
        options.addOption(option);
    }
}
