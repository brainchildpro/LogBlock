package de.diddiz.LogBlock;

import java.sql.*;

import de.diddiz.LogBlock.QueryParams.*;



public class LookupCacheElementFactory {
    private final QueryParams params;
    private final float spaceFactor;

    public LookupCacheElementFactory(final QueryParams params, final float spaceFactor) {
        this.params = params;
        this.spaceFactor = spaceFactor;
    }

    public LookupCacheElement getLookupCacheElement(final ResultSet rs) throws SQLException {
        if (this.params.bct == BlockChangeType.CHAT) return new ChatMessage(rs, this.params);
        if (this.params.sum == SummarizationMode.NONE) return new BlockChange(rs, this.params);
        return new SummedBlockChanges(rs, this.params, this.spaceFactor);
    }
}
