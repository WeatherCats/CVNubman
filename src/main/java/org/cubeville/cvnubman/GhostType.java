package org.cubeville.cvnubman;

import net.md_5.bungee.api.ChatColor;

public enum GhostType {
    BLINKY ("Blinky", ChatColor.RED),
    INKY ("Inky", ChatColor.AQUA),
    PINKY ("Pinky", ChatColor.LIGHT_PURPLE),
    CLYDE ("Clyde", ChatColor.GOLD);
    private final String displayName;
    private final ChatColor chatColor;
    GhostType(String displayName, ChatColor chatColor) {
        this.displayName = displayName;
        this.chatColor = chatColor;
    }
    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }
}
