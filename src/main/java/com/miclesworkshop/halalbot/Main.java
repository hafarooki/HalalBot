package com.miclesworkshop.halalbot;

import com.google.gson.Gson;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

public class Main {
    public static void main(String[] args) throws IOException {
        String token;
        String dataFolderPath;

        File configFile = new File("halalbot.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                Config config = new Gson().fromJson(reader, Config.class);
                token = config.token;
                dataFolderPath = config.dataFolder;
            }
        } else {
            Options options = new Options();

            addOption(options, "datafolder", "Data Folder");
            addOption(options, "token", "Discord API Token");

            CommandLineParser parser = new DefaultParser();

            CommandLine line;

            try {
                line = parser.parse(options, args);
            } catch (ParseException exp) {
                exp.printStackTrace();
                System.exit(1);
                return;
            }

            token = line.getOptionValue("token");
            dataFolderPath = line.getOptionValue("datafolder");
        }

        File dataFolder = new File(dataFolderPath);

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
        Option option = new Option(opt, opt, true, description);
        option.setRequired(true);
        options.addOption(option);
    }
}
