package com.miclesworkshop.halalbot;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.miclesworkshop.halalbot.commands.*;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HalalBot {
    private DiscordApi discordApi;
    private Logger log = Logger.getLogger(getClass().getName());
    private List<AbstractCommands> commandGroups;

    private Map<Long, ServerData> serverDataMap;

    private File serverDataFile;

    public HalalBot(File dataFolder, String token) {
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

        commandGroups = Arrays.asList(
                new HelpCommand(this),
                new ApprovalCommands(this),
                new JailCommands(this),
                new QuranCommands((this)));

        registerListeners();

        discordApi.getServers().forEach(this::initServer);

        printInvite();
    }

    private static <T> T getOrRuntimeException(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public synchronized void saveData() {
        try (FileWriter writer = new FileWriter(serverDataFile)) {
            new Gson().toJson(serverDataMap, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initServer(Server server) {
        log.info("Initializing " + server.getName() + " (" + server.getOwner().getName() + ")");

        if (!server.isMember(getOrRuntimeException(discordApi.getOwner()))) {
            server.leave();
            return;
        }

        log.info("  -> Own Roles: " + server.getRoles(discordApi.getYourself()).stream().map(Role::getName).collect(Collectors.joining(", ")));

        getOrCreateLimboChannel(server);
        getOrCreateJailChannel(server);
        getOrCreateLogsChannel(server);

        if (!serverDataMap.containsKey(server.getId())) {
            log.info("  -> Data not registered, registering...");
            getServerData(server);
            saveData();
        }

        getApprovalModeratorRole(server);
        getJailedRole(server);
    }

    public Role getApprovalModeratorRole(Server server) {
        return server.getRolesByName("Approval Moderator").stream().findFirst().orElseGet(() -> getOrRuntimeException(server
                .createRoleBuilder()
                .setName("Approval Moderator")
                .setAuditLogReason("Approval role was missing, created")
                .setDisplaySeparately(false)
                .setMentionable(true)
                .create()));
    }

    public Role getJailedRole(Server server) {
        return server.getRoleById(getServerData(server).getJailedRoleId()).orElseGet(() ->
                server.getRolesByName("Jailed").stream().findFirst().orElseGet(() -> getOrRuntimeException(server
                        .createRoleBuilder()
                        .setName("Jailed")
                        .setAuditLogReason("Jailed role was missing, created")
                        .setDisplaySeparately(false)
                        .setMentionable(true)
                        .setPermissions(new PermissionsBuilder().setDenied(PermissionType.READ_MESSAGES).build())
                        .create())));
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
                    .ifPresent(role -> getOrRuntimeException(server.addRoleToUser(user, role)));

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
                .getServerTextChannel().ifPresent(channel ->
                        event.getMessageAuthor().asUser().ifPresent(user ->
                                commandGroups.forEach(p -> p.parseMessage(channel.getServer(), user, channel, event.getMessage())))));
    }

    public void createApprovalChannelIfAbsent(Server server, User user) {
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
                        "**2)** What is your gender (Male/Female) (Please specify your *biological, birth gender*)",
                        "**3)** Where did you hear of this server? " +
                                "__(Please be detailed - if you heard it from a friend, give their name)__",
                        "**4)** What do you want to do in this server?",
                        "Please type @Male Verification if you are male or @Female Verification if you are female " +
                                "to ping a moderator who will be voice chatting with you for verification purposes"
                ), embedBuilder);
    }

    public void closeApprovalChannel(ServerTextChannel channel, String reason, @Nullable User closer) {
        String channelName = channel.getName();
        Preconditions.checkArgument(channelName.startsWith("approval-"));

        String whoDoneIt = closer == null ? "automatically" : "by " + closer.getName();

        discordApi.getUserById(channelName.replaceFirst("approval-", ""))
                .thenAccept(user -> getOrCreateLimboChannel(channel.getServer())
                        .sendMessage(user.getMentionTag() + " your approval ticket has been closed "
                                + whoDoneIt + ". Reason: " + reason + "\n\n" +
                                "Please say `*apply` in this limbo channel to apply again.")
                );

        deleteChannel(channel, "Approval channel closed " + whoDoneIt + ". Reason: '" + reason + "'");
    }

    public ServerTextChannel getOrCreateLimboChannel(Server server) {
        return server.getTextChannelById(getServerData(server).getLimboChannel()).orElseGet(() -> {
                    ServerTextChannel channel = server.getTextChannelsByNameIgnoreCase("approval").stream().findFirst().orElseGet(() -> {
                        log.info("Creating limbo #approval channel!");
                        return getOrRuntimeException(server.createTextChannelBuilder().setName("approval").create());
                    });

                    getServerData(server).setLimboChannel(channel.getId());
                    saveData();

                    return channel;
                }
        );
    }

    public ServerTextChannel getOrCreateJailChannel(Server server) {
        return server.getTextChannelById(getServerData(server).getJailChannel()).orElseGet(() -> {
                    ServerTextChannel channel = server.getTextChannelsByNameIgnoreCase("jail").stream().findFirst().orElseGet(() -> {
                        log.info("Creating #jail channel!");
                        return getOrRuntimeException(server.createTextChannelBuilder().setName("jail")
                                .addPermissionOverwrite(server.getEveryoneRole(), new PermissionsBuilder()
                                        .setDenied(PermissionType.READ_MESSAGES).build())
                                .addPermissionOverwrite(getApprovalModeratorRole(server), new PermissionsBuilder()
                                        .setAllowed(PermissionType.READ_MESSAGES).build())
                                .addPermissionOverwrite(getJailedRole(server), new PermissionsBuilder()
                                        .setAllowed(PermissionType.READ_MESSAGES).build())
                                .addPermissionOverwrite(discordApi.getYourself(), new PermissionsBuilder()
                                        .setAllowed(PermissionType.READ_MESSAGES).build())
                                .create());
                    });

                    getServerData(server).setJailChannel(channel.getId());
                    saveData();

                    return channel;
                }
        );
    }

    public ServerTextChannel getOrCreateLogsChannel(Server server) {
        return server.getTextChannelById(getServerData(server).getLogsChannel()).orElseGet(() -> {
                    ServerTextChannel channel = server.getTextChannelsByNameIgnoreCase("approval-logs").stream()
                            .findFirst().orElseGet(() -> {
                                log.info("Creating logs #approval-logs channel!");
                                return getOrRuntimeException(server.createTextChannelBuilder()
                                        .setName("approval-logs")
                                        .addPermissionOverwrite(server.getEveryoneRole(), new PermissionsBuilder()
                                                .setDenied(PermissionType.READ_MESSAGES).build())
                                        .addPermissionOverwrite(getApprovalModeratorRole(server), new PermissionsBuilder()
                                                .setAllowed(PermissionType.READ_MESSAGES).build())
                                        .addPermissionOverwrite(discordApi.getYourself(), new PermissionsBuilder()
                                                .setAllowed(PermissionType.READ_MESSAGES).build())
                                        .create());
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
        ChannelCategory category = server.getChannelCategoriesByName(approvalCategoryName).stream()
                .findFirst().orElseGet(() -> getOrRuntimeException(server
                        .createChannelCategoryBuilder()
                        .setAuditLogReason("Approval category missing, created it.")
                        .setName(approvalCategoryName)
                        .create()));

        if (category.getChannels().size() > 45) {
            getOrCreateLimboChannel(server).sendMessage(user.getMentionTag() + " there were too many approval " +
                    "tickets to process your request! Please ask an approval moderator to clear some old ones.");
            return Optional.empty();
        }

        return Optional.of(getOrRuntimeException(server.createTextChannelBuilder()
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
                .create()));
    }

    public DiscordApi getDiscordApi() {
        return discordApi;
    }

    public ServerData getServerData(Server server) {
        return serverDataMap.computeIfAbsent(server.getId(), id -> {
            ServerData serverData = new ServerData();
            serverData.setLimboChannel(0);
            serverData.setRoles(new HashMap<>());
            return serverData;
        });
    }

    public void deleteChannel(ServerTextChannel channel, String reason) {
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
