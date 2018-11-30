package com.miclesworkshop.halalbot;

import com.google.common.base.Preconditions;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ChannelCategory;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.permission.Permissions;
import org.javacord.api.entity.permission.PermissionsBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class HalalBot {
    private DiscordApi discordApi;

    public HalalBot(String token) {
        discordApi = new DiscordApiBuilder().setToken(token).login().join();

        registerListeners();

        discordApi.getServers().forEach(this::initServer);

        printInvite();
    }

    private void initServer(Server server) {
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
        discordApi.addServerJoinListener(event -> initServer(event.getServer()));
        discordApi.addServerBecomesAvailableListener(event -> initServer(event.getServer()));

        discordApi.addServerMemberJoinListener(event -> {
            ServerTextChannel channel = createApprovalChannel(event.getServer(), event.getUser());

            try {
                channel.createUpdater()
                        .addPermissionOverwrite(event.getServer().getEveryoneRole(), new PermissionsBuilder()
                                .setDenied(PermissionType.READ_MESSAGES).build())
                        .addPermissionOverwrite(getApprovalModeratorRole(event.getServer()), new PermissionsBuilder()
                                .setAllowed(PermissionType.READ_MESSAGES).build())
                        .addPermissionOverwrite(event.getUser(), new PermissionsBuilder()
                                .setAllowed(PermissionType.READ_MESSAGES).build())
                        .update().get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }

            channel.sendMessage(event.getUser().getMentionTag() + " welcome to the " + event.getServer().getName() + " Discord server!\n\n" +
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
            event.getServer()
                    .getChannelsByName(getApprovalChannelName(event.getUser()))
                    .forEach(category -> category.delete("User left the server"));
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
}
