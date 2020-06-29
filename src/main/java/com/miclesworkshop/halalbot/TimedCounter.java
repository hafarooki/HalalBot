package com.miclesworkshop.halalbot;

import java.util.concurrent.TimeUnit;

import org.javacord.api.entity.channel.ServerTextChannel;

public class TimedCounter {

    private static final long RESOLUTION = 1;

    private byte threshold;
    private byte count;
    private byte cooldownTime;
    private long countEndTime;
    private long cooldownEndTime;

    private ServerTextChannel channel;

    public TimedCounter(ServerTextChannel channel) {
        this.channel = channel;
        reset();
    }

    public void increment() {
        if (cooldownEndTime != 0
                && cooldownEndTime < System.currentTimeMillis()) {
            reset();
            channel.unsetSlowmode();
            channel.sendMessage("**Cooldown period ended!**");
        } else if (cooldownTime > TimeUnit.MINUTES.toSeconds(RESOLUTION)) {
            return;
        }

        count++;
        if (countEndTime < System.currentTimeMillis()) {
            count = 1;
            countEndTime = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(RESOLUTION);
        } else if (count >= threshold) {
            if (cooldownEndTime == 0) {
                cooldownEndTime = System.currentTimeMillis();
            }
            cooldownEndTime += TimeUnit.MINUTES.toMillis(15);
            threshold /= 2;
            cooldownTime *= 2;
            channel.updateSlowmodeDelayInSeconds(cooldownTime);
            count = 0; 
            countEndTime = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(RESOLUTION);
            channel.sendMessage(String.format("**Cooldown set to %d seconds!**",
                        cooldownTime));
        }
    }

    private void reset() {
        threshold = 32;
        cooldownTime = 2;
        cooldownEndTime = 0L;
    }

}
