package com.miclesworkshop.halalbot;

import com.google.common.base.Preconditions;
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

import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class HalalBot {
    private DiscordApi discordApi;
    private Logger log = Logger.getLogger(getClass().getName());
    private Commands commands;

    public HalalBot(String token) {
        discordApi = new DiscordApiBuilder().setToken(token).login().join();

        commands = new Commands(this);

        registerListeners();

        discordApi.getServers().forEach(this::initServer);

        printInvite();
    }

    private void initServer(Server server) {
        log.info("Initializing " + server.getName());

        log.info("  -> Own Roles: " + server.getRoles(discordApi.getYourself()).stream().map(Role::getName).collect(Collectors.joining(", ")));

        getApprovalModeratorRole(server);

        getApprovalCategory(server);
    }

    private Role getApprovalModeratorRole(Server server) {
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

    private ChannelCategory getApprovalCategory(Server server) {
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

        discordApi.addMessageCreateListener(event -> commands.parseMessage(event.getMessage()));
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
}
