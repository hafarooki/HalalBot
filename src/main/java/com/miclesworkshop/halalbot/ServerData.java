package com.miclesworkshop.halalbot;

import java.util.Map;

public class ServerData {
    private Map<String, Long> roles;
    private long limboChannel;
    private long jailChannel;
    private long logsChannel;
    private long jailedRoleId;

    public Map<String, Long> getRoles() {
        return roles;
    }

    public void setRoles(Map<String, Long> roles) {
        this.roles = roles;
    }

    public long getLimboChannel() {
        return limboChannel;
    }

    public long getLogsChannel() {
        return logsChannel;
    }

    public void setLimboChannel(long limboChannel) {
        this.limboChannel = limboChannel;
    }

    public void setLogsChannel(long logsChannel) {
        this.logsChannel = logsChannel;
    }

    public long getJailChannel() {
        return jailChannel;
    }

    public void setJailChannel(long jailChannel) {
        this.jailChannel = jailChannel;
    }

    public long getJailedRoleId() {
        return jailedRoleId;
    }

    public void setJailedRoleId(long jailedRoleId) {
        this.jailedRoleId = jailedRoleId;
    }
}
