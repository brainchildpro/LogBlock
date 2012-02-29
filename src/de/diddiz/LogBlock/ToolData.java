package de.diddiz.LogBlock;

import org.bukkit.entity.Player;

public class ToolData {
    public boolean enabled;
    public QueryParams params;
    public ToolMode mode;

    public ToolData(Tool tool, LogBlock logblock, Player player) {
        this.enabled = tool.defaultEnabled && logblock.hasPermission(player, "logblock.tools." + tool.name);
        this.params = tool.params.clone();
        this.mode = tool.mode;
    }
}
