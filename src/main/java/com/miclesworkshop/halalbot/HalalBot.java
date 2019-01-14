package com.miclesworkshop.halalbot;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Permissions;
import org.javacord.api.entity.permission.PermissionsBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class HalalBot {
    private DiscordApi discordApi;
    private Logger log = Logger.getLogger(getClass().getName());
    private ApprovalCommands approvalCommands;

    private Map<Long, ServerData> serverDataMap;

    private File serverDataFile;

    HalalBot(File dataFolder, String token) {
        discordApi = new DiscordApiBuilder().setToken(token).login().join();

        serverDataFile = new File(dataFolder, "server_data.json");

        if (!serverDataFile.exists()) {
            log.info("Creating new server data file...");
            serverDataMap = new HashMap<>();
            saveData();
        } else {
            log.info("Reading server data...");
            try (FileReader reader = new FileReader(serverDataFile)) {
                Type type = new TypeToken<Map<Long, ServerData>>() {
                }.getType();
                serverDataMap = new Gson().fromJson(reader, type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.info("  -> Read " + serverDataMap.size() + " servers.");
        }

        approvalCommands = new ApprovalCommands(this);

        registerListeners();

        discordApi.getServers().forEach(this::initServer);

        printInvite();
    }

    synchronized void saveData() {
        try (FileWriter writer = new FileWriter(serverDataFile)) {
            new Gson().toJson(serverDataMap, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initServer(Server server) {
        log.info("Initializing " + server.getName());

        log.info("  -> Own Roles: " + server.getRoles(discordApi.getYourself()).stream().map(Role::getName).collect(Collectors.joining(", ")));

        getOrCreateLimboChannel(server);

        if (!serverDataMap.containsKey(server.getId())) {
            log.info("  -> Data not registered, registering...");
            getServerData(server);
            saveData();
        }

        getApprovalModeratorRole(server);
    }

    Role getApprovalModeratorRole(Server server) {
        return server.getRolesByName("Approval Moderator").stream().findFirst().orElseGet(() -> {
            try {
                return server.createRoleBuilder()
                        .setName("Approval Moderator")
                        .setAuditLogReason("Approval role was missing, created")
                        .setDisplaySeparately(false)
                        .setMentionable(true)
                        .create().get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private void printInvite() {
        Permissions permissions = new PermissionsBuilder().setAllowed(PermissionType.ADMINISTRATOR).build();
        System.out.println("Invite URL: " + discordApi.createBotInvite(permissions));
    }

    private void registerListeners() {
        discordApi.addServerJoinListener(event -> {
            Server server = event.getServer();
            log.info("Joined " + server.getName());
            initServer(server);
        });
        discordApi.addServerBecomesAvailableListener(event -> initServer(event.getServer()));

        discordApi.addServerMemberJoinListener(event -> {
            User user = event.getUser();

            if (user.isBot()) {
                return;
            }

            Server server = event.getServer();

            log.info(user.getName() + " joined " + server.getName() + " ! Creating channel");

            // add the approval role to the user if they just joined
            server.getRolesByNameIgnoreCase("Approval").stream()
                    .findFirst()
                    .ifPresent(role -> {
                        try {
                            server.addRoleToUser(user, role).get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

            getOrCreateLimboChannel(server).sendMessage(user.getMentionTag() + " welcome to " + server.getName() + "!\n" +
                    "To be able to join in on the conversation, please begin the application process by typing `*apply`.");
        });

        discordApi.addServerMemberLeaveListener(event -> {
            User user = event.getUser();

            Server server = event.getServer();

            log.info(user.getName() + " left " + server.getName() + "! Deleting channel if exists");

            for (ServerTextChannel channel : server.getTextChannelsByName(getApprovalChannelName(user))) {
                log.info("Deleted channel " + channel.getName());
                deleteChannel(channel, "User left the server");
            }
        });

        discordApi.addMessageCreateListener(event -> {
            Message message = event.getMessage();
            if (message.getMentionedUsers().contains(discordApi.getYourself())) {
                message.getChannel().sendMessage("السلام عليكم!\n" +
                        "I am the Halal Bot! I am a special bot for Muslim.Chat who helps moderators manage new users who need approval.");
            }
        });

        discordApi.addMessageCreateListener(event -> event
                .getServerTextChannel().ifPresent(channel -> event
                        .getMessageAuthor().asUser()
                        .ifPresent(user -> approvalCommands.parseMessage(channel.getServer(), user, channel, event.getMessage()))
                )
        );
    }

    void createApprovalChannelIfAbsent(Server server, User user) {
        Optional<ServerTextChannel> channel = getApprovalChannel(server, user);

        if (channel.isPresent()) {
            channel.get().sendMessage(user.getMentionTag() + " this channel already exists!");
            return;
        }

        channel = createApprovalChannel(server, user);

        if (!channel.isPresent()) {
            return;
        }


        EmbedBuilder embedBuilder = new EmbedBuilder();

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .withZone(ZoneId.systemDefault());

        embedBuilder.addInlineField("Joined Discord", timeFormatter.format(user.getCreationTimestamp()));

        server.getJoinedAtTimestamp(user).ifPresent(instant ->
                embedBuilder.addInlineField("Joined Server", timeFormatter.format(instant))
        );

        channel.get().sendMessage(user.getMentionTag() + " welcome to the " + server.getName() + " Discord server! " +
                "Since we get a lot of trolls and spammers, we require you to go through an approval process.\n\n" +
                "Please answer the following questions:\n" +
                String.join("\n",
                        "**1)** What is your faith/religion? (You don't have to be a Muslim to join!)",
                        "**2)** What is your gender (male/female) (please specify your *biological, birth gender*)",
                        "**3)** Where did you hear of this server? " +
                                "__(please be detailed - " +
                                "if you heard it from a friend, give their name, " +
                                "and if it is through searching on the Internet, give the link to where you found it)__",
                        "**4)** What do you want to do in this server?"
                ), embedBuilder);
    }

    void closeApprovalChannel(ServerTextChannel channel, String reason, @Nullable User closer) {
        String channelName = channel.getName();
        Preconditions.checkArgument(channelName.startsWith("approval-"));

        String whoDoneIt = closer == null ? " automatically" : " by " + closer.getName();

        discordApi.getUserById(channelName.replaceFirst("approval-", ""))
                .thenAccept(user -> getOrCreateLimboChannel(channel.getServer())
                        .sendMessage(user.getMentionTag() + " your approval ticket has been closed "
                                + whoDoneIt + ". Reason: " + reason + "\n\n" +
                                "Please say `*apply` in this limbo channel to apply again.")
                );

        deleteChannel(channel, "Approval channel closed " + whoDoneIt + " for " + reason);
    }

    private Optional<ServerTextChannel> getLimboChannel(Server server) {
        long limboChannelId = getServerData(server).getLimboChannel();
        return limboChannelId == 0 ? Optional.empty() : server.getTextChannelById(limboChannelId);
    }

    ServerTextChannel getOrCreateLimboChannel(Server server) {
        return getLimboChannel(server).orElseGet(() -> {
                    ServerTextChannel channel = server.getTextChannelsByNameIgnoreCase("approval").stream().findFirst().orElseGet(() -> {
                        try {
                            log.info("Creating limbo #approval channel!");
                            return server.createTextChannelBuilder()
                                    .setName("approval")
                                    .create().get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    getServerData(server).setLimboChannel(channel.getId());
                    saveData();

                    return channel;
                }
        );
    }

    private Optional<ServerTextChannel> getLogsChannel(Server server) {
        long logsChannelId = getServerData(server).getLimboChannel();
        return logsChannelId == 0 ? Optional.empty() : server.getTextChannelById(logsChannelId);
    }

    ServerTextChannel getOrCreateLogsChannel(Server server) {
        return getLimboChannel(server).orElseGet(() -> {
                    ServerTextChannel channel = server.getTextChannelsByNameIgnoreCase("approval-logs").stream().findFirst().orElseGet(() -> {
                        try {
                            log.info("Creating logs #approval-logs channel!");
                            return server.createTextChannelBuilder()
                                    .setName("approval-logs")
                                    .addPermissionOverwrite(server.getEveryoneRole(), new PermissionsBuilder()
                                            .setDenied(PermissionType.READ_MESSAGES).build())
                                    .addPermissionOverwrite(getApprovalModeratorRole(server), new PermissionsBuilder()
                                            .setAllowed(PermissionType.READ_MESSAGES).build())
                                    .addPermissionOverwrite(discordApi.getYourself(), new PermissionsBuilder()
                                            .setAllowed(PermissionType.READ_MESSAGES).build())
                                    .create().get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    getServerData(server).setLogsChannel(channel.getId());
                    saveData();

                    return channel;
                }
        );
    }

    private String getApprovalChannelName(User user) {
        return "approval-" + user.getIdAsString();
    }

    private Optional<ServerTextChannel> getApprovalChannel(Server server, User user) {
        return server.getTextChannels().stream()
                .filter(channel -> {
                    Optional<ChannelCategory> channelCategory = channel.getCategory();
                    return channelCategory.isPresent() && channelCategory.get().getName().startsWith("Approval");
                })
                .filter(channel -> channel.getName().equals(getApprovalChannelName(user)))
                .findFirst();
    }

    private Optional<ServerTextChannel> createApprovalChannel(Server server, User user) {
        String channelName = getApprovalChannelName(user);

        Preconditions.checkState(server.getChannelsByName(channelName).isEmpty());

        String approvalCategoryName = "Approval";
        ChannelCategory category = server.getChannelCategoriesByName(approvalCategoryName).stream().findFirst().orElseGet(() -> {
            try {
                return server.createChannelCategoryBuilder()
                        .setAuditLogReason("Approval category missing, created it.")
                        .setName(approvalCategoryName)
                        .create().get();
            } catch (Exception exception1) {
                throw new RuntimeException(exception1);
            }
        });

        if (category.getChannels().size() > 45) {
            getOrCreateLimboChannel(server).sendMessage(user.getMentionTag() + " there were too many approval " +
                    "tickets to process your request! Please ask an approval moderator to clear some old ones.");
            return Optional.empty();
        }

        try {
            return Optional.of(server.createTextChannelBuilder()
                    .setName(channelName)
                    .setCategory(category)
                    .addPermissionOverwrite(server.getEveryoneRole(), new PermissionsBuilder()
                            .setDenied(PermissionType.READ_MESSAGES).build())
                    .addPermissionOverwrite(getApprovalModeratorRole(server), new PermissionsBuilder()
                            .setAllowed(PermissionType.READ_MESSAGES).build())
                    .addPermissionOverwrite(discordApi.getYourself(), new PermissionsBuilder()
                            .setAllowed(PermissionType.READ_MESSAGES).build())
                    .addPermissionOverwrite(user, new PermissionsBuilder()
                            .setAllowed(PermissionType.READ_MESSAGES).build())
                    .create().get());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    DiscordApi getDiscordApi() {
        return discordApi;
    }

    ServerData getServerData(Server server) {
        return serverDataMap.computeIfAbsent(server.getId(), id -> {
            ServerData serverData = new ServerData();
            serverData.setLimboChannel(0);
            serverData.setRoles(new HashMap<>());
            return serverData;
        });
    }

    void deleteChannel(ServerTextChannel channel, String reason) {
        try {
            String log = channel.getMessagesAsStream()
                    .map(m -> "[" + m.getCreationTimestamp().atZone(ZoneId.systemDefault()).toString() + "] " +
                            m.getAuthor().getDisplayName() +
                            " (" + m.getAuthor().getDiscriminatedName() + ")" + ": " +
                            m.getReadableContent() +
                            (m.getLastEditTimestamp().isPresent() ? "(edited)" : ""))
                    .collect(Collectors.joining("\n"));

            getOrCreateLogsChannel(channel.getServer()).sendMessage(
                    "Logs from approval channel " + channel.getName() + " (Deletion reason: `" + reason + "`)\n" +
                            "```Log\n" +
                            log + "" +
                            "\n```");
        } catch (Exception e) {
            e.printStackTrace();
        }

        channel.delete(reason);
    }
}
