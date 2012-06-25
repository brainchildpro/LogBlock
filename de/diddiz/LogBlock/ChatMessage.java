package de.diddiz.LogBlock;

import java.sql.*;

import org.bukkit.Location;

public class ChatMessage implements LookupCacheElement {
    final long id, date;
    final String playerName, message;

    public ChatMessage(final ResultSet rs, final QueryParams p) throws SQLException {
        this.id = p.needId ? rs.getInt("id") : 0;
        this.date = p.needDate ? rs.getTimestamp("date").getTime() : 0;
        this.playerName = p.needPlayer ? rs.getString("playername") : null;
        this.message = p.needMessage ? rs.getString("message") : null;
    }

    public ChatMessage(final String playerName, final String message) {
        this.id = 0;
        this.date = System.currentTimeMillis() / 1000;
        this.playerName = playerName;
        this.message = message;
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public String getMessage() {
        return (this.playerName != null ? "<" + this.playerName + "> " : "") + (this.message != null ? this.message : "");
    }
}
