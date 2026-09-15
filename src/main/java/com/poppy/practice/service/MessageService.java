package com.poppy.practice.service;

import com.poppy.practice.config.MessageConfig;
import org.bukkit.entity.Player;

public final class MessageService {
    private final MessageConfig messages;

    public MessageService(MessageConfig messages) {
        this.messages = messages;
    }

    public void send(Player player, String key) {
        messages.send(player, key);
    }

    public void send(Player player, String key, String placeholder, String value) {
        messages.send(player, key, placeholder, value);
    }
}
