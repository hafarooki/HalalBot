package com.miclesworkshop.halalbot;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.Message;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HalalBot {
    private DiscordApi discordApi;
    private Logger log = Logger.getLogger(getClass().getName());
    private ApprovalCommands approvalCommands;

    private Map<Long, ServerData> serverDataMap = new HashMap<>();

    private File serverDataFile;

    public HalalBot(String token) {
        discordApi = new DiscordApiBuilder().setToken(token).login().join();

        serverDataFile = new File("server_data.json");

        approvalCommands = new ApprovalCommands(this);

        registerListeners();

        discordApi.getServers().forEach(this::initServer);

        printInvite();
    }

    public void saveData() {
        try (FileWriter writer = new FileWriter(serverDataFile)) {
            new Gson().toJson(serverDataMap, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initServer(Server server) {
        log.info("Initializing " + server.getName());

        log.info("  -> Own Roles: " + server.getRoles(discordApi.getYourself()).stream().map(Role::getName).collect(Collectors.joining(", ")));

        if (!serverDataFile.exists()) {
            serverDataMap = new HashMap<>();
            saveData();
        } else {
            try (FileReader reader = new FileReader(serverDataFile)) {
                Type type = new TypeToken<Map<Long, ServerData>>() {
                }.getType();
                serverDataMap = new Gson().fromJson(reader, type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (!serverDataMap.containsKey(server.getId())) {
            log.info("  -> Data not registered, registering...");
            getServerData(server);
            saveData();
        }

        getApprovalModeratorRole(server);

        getApprovalCategory(server);
    }

    public Role getApprovalModeratorRole(Server server) {
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

    public ChannelCategory getApprovalCategory(Server server) {
        String approvalCategoryName = "Approval";
        return server.getChannelCategoriesByName(approvalCategoryName).stream().findFirst().orElseGet(() -> {
            try {
                return server.createChannelCategoryBuilder()
                        .setAuditLogReason("Approval category missing, created it.")
                        .setName(approvalCategoryName)
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

            log.info(user.getName() + " joined! Creating channel");

            ServerTextChannel channel = createApprovalChannel(event.getServer(), user);

            try {
                channel.createUpdater()
                        .addPermissionOverwrite(event.getServer().getEveryoneRole(), new PermissionsBuilder()
                                .setDenied(PermissionType.READ_MESSAGES).build())
                        .addPermissionOverwrite(getApprovalModeratorRole(event.getServer()), new PermissionsBuilder()
                                .setAllowed(PermissionType.READ_MESSAGES).build())
                        .addPermissionOverwrite(discordApi.getYourself(), new PermissionsBuilder()
                                .setAllowed(PermissionType.READ_MESSAGES).build())
                        .addPermissionOverwrite(user, new PermissionsBuilder()
                                .setAllowed(PermissionType.READ_MESSAGES).build())
                        .update().get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            channel.sendMessage(user.getMentionTag() + " welcome to the " + event.getServer().getName() + " Discord server!" +
                    "Since we get a lot of trolls and spammers, we require you to go through an approval process.\n\n" +
                    "Please answer the following questions:\n" +
                    String.join("\n",
                            "**1)** What is your faith/religion? (You don't have to be a Muslim to join!)",
                            "**2)** What is your gender (male/female) (please specify your *biological, birth gender*)",
                            "**3)** Where did you hear of this server? " +
                                    "__(please be detailed - if it's a 'friend', name them, and if it through 'Google', provide the exact link)__",
                            "**4)** What do you want to do in this server?"
                    ));
        });

        discordApi.addServerMemberLeaveListener(event -> {
            User user = event.getUser();

            log.info(user.getName() + " left! Deleting channel if exists");

            event.getServer()
                    .getChannelsByName(getApprovalChannelName(user))
                    .forEach(channel -> {
                        log.info("Deleted channel " + channel.getName());
                        channel.delete("User left the server");
                    });
        });

        discordApi.addMessageCreateListener(event -> {
            Message message = event.getMessage();
            if (message.getMentionedUsers().contains(discordApi.getYourself())) {
                message.getChannel().sendMessage("السلام عليكم!\n" +
                        "I am the Halal Bot! I am a special bot for Muslim.Chat who helps moderators manage new users who need approval.");
            }
        });

        discordApi.addMessageCreateListener(event -> {
            event.getServerTextChannel().ifPresent(channel -> event.getMessageAuthor().asUser().ifPresent(user ->
                            approvalCommands.parseMessage(channel.getServer(), user, channel, event.getMessage())
                    )
            );
        });
    }

    private String getApprovalChannelName(User user) {
        return "approval-" + user.getIdAsString();
    }

    private Optional<ServerTextChannel> getApprovalChannel(Server server, User user) {
        ChannelCategory category = getApprovalCategory(server);
        return server.getTextChannels().stream()
                .filter(channel -> {
                    Optional<ChannelCategory> channelCategory = channel.getCategory();
                    return channelCategory.isPresent() && channelCategory.get().equals(category);
                })
                .filter(channel -> channel.getName().equals(getApprovalChannelName(user)))
                .findFirst();
    }

    private ServerTextChannel createApprovalChannel(Server server, User user) {
        String channelName = getApprovalChannelName(user);

        Preconditions.checkState(server.getChannelsByName(channelName).isEmpty());

        try {
            return server.createTextChannelBuilder()
                    .setName(channelName)
                    .setCategory(getApprovalCategory(server))
                    .create().get();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private ServerTextChannel getOrCreateApprovalChannel(Server server, User user) {
        return getApprovalChannel(server, user).orElseGet(() -> this.createApprovalChannel(server, user));
    }

    public DiscordApi getDiscordApi() {
        return discordApi;
    }

    public Map<Long, ServerData> getServerDataMap() {
        return serverDataMap;
    }

    public ServerData getServerData(Server server) {
        return serverDataMap.computeIfAbsent(server.getId(), id -> new ServerData());
    }
}
