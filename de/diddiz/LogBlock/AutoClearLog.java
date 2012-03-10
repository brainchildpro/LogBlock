package de.diddiz.LogBlock;

import static de.diddiz.LogBlock.config.Config.autoClearLog;
import static org.bukkit.Bukkit.*;

import java.util.Arrays;
import java.util.logging.Level;

public class AutoClearLog implements Runnable {
    private final LogBlock logblock;

    AutoClearLog(final LogBlock logblock) {
        this.logblock = logblock;
    }

    public void run() {
        final CommandsHandler handler = this.logblock.getCommandsHandler();
        for (final String paramStr : autoClearLog)
            try {
                final QueryParams params = new QueryParams(this.logblock, getConsoleSender(),
                        Arrays.asList(paramStr.split(" ")));
                handler.new CommandClearLog(getServer().getConsoleSender(), params, false);
            } catch (final Exception ex) {
                getLogger().log(Level.SEVERE, "Failed to schedule auto ClearLog: ", ex);
            }
    }
}
