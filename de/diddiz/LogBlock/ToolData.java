package de.diddiz.LogBlock;

import org.bukkit.entity.Player;

public class ToolData {
    public boolean enabled;
    public QueryParams params;
    public ToolMode mode;

    public ToolData(final Tool tool, final LogBlock logblock, final Player player) {
        this.enabled = tool.defaultEnabled && logblock.hasPermission(player, "logblock.tools." + tool.name);
        this.params = tool.params.clone();
        this.mode = tool.mode;
    }
}
