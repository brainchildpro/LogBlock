package de.diddiz.LogBlock;

import static de.diddiz.util.MaterialName.materialName;
import static de.diddiz.util.Utils.spaces;

import java.sql.*;

import org.bukkit.Location;

import de.diddiz.LogBlock.QueryParams.SummarizationMode;

public class SummedBlockChanges implements LookupCacheElement {
    private final String group;
    private final int created, destroyed;
    private final float spaceFactor;

    public SummedBlockChanges(final ResultSet rs, final QueryParams p, final float spaceFactor) throws SQLException {
        this.group = p.sum == SummarizationMode.PLAYERS ? rs.getString(1) : materialName(rs.getInt(1));
        this.created = rs.getInt(2);
        this.destroyed = rs.getInt(3);
        this.spaceFactor = spaceFactor;
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public String getMessage() {
        return this.created + spaces((int) ((10 - String.valueOf(this.created).length()) / this.spaceFactor)) + this.destroyed + spaces((int) ((10 - String.valueOf(this.destroyed).length()) / this.spaceFactor)) + this.group;
    }
}
