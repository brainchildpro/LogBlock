package de.diddiz.LogBlock;

import java.sql.*;

import org.bukkit.Location;

public class ChatMessage implements LookupCacheElement {
    final long id, date;
    final String playerName, message;

    public ChatMessage(ResultSet rs, QueryParams p) throws SQLException {
        this.id = p.needId ? rs.getInt("id") : 0;
        this.date = p.needDate ? rs.getTimestamp("date").getTime() : 0;
        this.playerName = p.needPlayer ? rs.getString("playername") : null;
        this.message = p.needMessage ? rs.getString("message") : null;
    }

    public ChatMessage(String playerName, String message) {
        this.id = 0;
        this.date = System.currentTimeMillis() / 1000;
        this.playerName = playerName;
        this.message = message;
    }

    public Location getLocation() {
        return null;
    }

    public String getMessage() {
        return (this.playerName != null ? "<" + this.playerName + "> " : "")
                + (this.message != null ? this.message : "");
    }
}
