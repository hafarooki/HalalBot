package com.miclesworkshop.halalbot;

import java.util.HashMap;
import java.util.Map;

public class ServerData {
    private Map<String, Long> roles;
    private long limboChannel;

    public ServerData() {
        roles = new HashMap<>();
    }

    public Map<String, Long> getRoles() {
        return roles;
    }

    public void setRoles(Map<String, Long> roles) {
        this.roles = roles;
    }

    public long getLimboChannel() {
        return limboChannel;
    }

    public void setLimboChannel(long limboChannel) {
        this.limboChannel = limboChannel;
    }
}
