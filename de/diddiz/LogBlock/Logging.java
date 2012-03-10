package de.diddiz.LogBlock;

public enum Logging {
    BLOCKPLACE(true), BLOCKBREAK(true), SIGNTEXT, TNTEXPLOSION(true), CREEPEREXPLOSION(true), GHASTFIREBALLEXPLOSION(
            true), ENDERDRAGON(true), MISCEXPLOSION, FIRE(true), LEAVESDECAY, LAVAFLOW, WATERFLOW, CHESTACCESS, KILL, CHAT, SNOWFORM, SNOWFADE, DOORINTERACT, SWITCHINTERACT, CAKEEAT, ENDERMEN, NOTEBLOCKINTERACT, DIODEINTERACT, NATURALSTRUCTUREGROW, BONEMEALSTRUCTUREGROW;

    public static final int length = Logging.values().length;

    private final boolean defaultEnabled;

    private Logging() {
        this(false);
    }

    private Logging(final boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isDefaultEnabled() {
        return this.defaultEnabled;
    }
}
