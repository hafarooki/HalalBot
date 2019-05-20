package com.miclesworkshop.halalbot.commands;

import com.miclesworkshop.halalbot.HalalBot;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

public class QuranCommands extends AbstractCommands {
    private String[][][] data; // first dimension: surah index. second dimension: ayah index. third dimension: arabic/english

    public QuranCommands(HalalBot bot) {
        super(bot);
        try (ObjectInputStream ois = new ObjectInputStream(QuranCommands.class.getResourceAsStream("/halalbot_quran_data.bin"))) {
            data = (String[][][]) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void executeCommand(Server server, User user, ServerTextChannel channel, Message message, String channelName, String cmd, String[] args) {
        if (!cmd.equals("*quran") && !cmd.equals("*equran") && !cmd.equals("*aquran")) {
            return;
        }

        if (args.length < 2) {
            sendUsage(channel, cmd);
            return;
        }

        int surahNum = 0;
        int ayah1Num = 0;
        int ayah2Num = 0;

        try {
            surahNum = Integer.parseInt(args[0]);
            ayah1Num = Integer.parseInt(args[1]);
            ayah2Num = args.length > 2 ? Integer.parseInt(args[2]) : ayah1Num;
        } catch (NumberFormatException e) {
            sendUsage(channel, cmd);
            return;
        }

        if (surahNum < 1 || surahNum > data.length) {
            channel.sendMessage("Surah #" + surahNum + " not found");
            return;
        }

        String[][] surahData = data[surahNum - 1];

        if (ayah2Num < ayah1Num) {
            channel.sendMessage("Second ayah can't be less than first ayah.");
            return;
        }

        if (ayah1Num < 1) {
            channel.sendMessage("Ayah can't be less than 1");
            return;
        }

        int ayahCount = surahData.length;
        if (ayah2Num > ayahCount) {
            channel.sendMessage("Surah only has " + ayahCount + " ayahs");
            return;
        }

        String[][] range = Arrays.copyOfRange(surahData, ayah1Num - 1, ayah2Num - 1);

        EmbedBuilder embedBuilder = new EmbedBuilder();

        for (int i = 0; i < range.length; i++) {
            String index = surahNum + ":" + (i + 1);
            StringBuilder contentBuilder = new StringBuilder();

            String[] ayahData = range[i];
            switch (cmd) {
                case "*quran":
                    contentBuilder.append(ayahData[0] + "\n");
                    contentBuilder.append(ayahData[1]);
                    break;
                case "*aquran":
                    contentBuilder.append(ayahData[0]);
                    break;
                case "*equran":
                    contentBuilder.append(ayahData[1]);
                    break;
            }
            contentBuilder.append("\n\n");
            String content = contentBuilder.toString();
            embedBuilder.addInlineField(index, content);
            channel.sendMessage(content);
        }

        embedBuilder.addInlineField("Translation", "Mufti Taqi Usmani");

        channel.sendMessage(embedBuilder);
    }

    private void sendUsage(ServerTextChannel channel, String cmd) {
        channel.sendMessage("__**Usage:**__ " + cmd + " surah ayah OR " + " surah ayah1 ayah2");
    }
}
