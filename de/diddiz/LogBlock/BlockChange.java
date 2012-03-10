package de.diddiz.LogBlock;

import static de.diddiz.util.MaterialName.materialName;

import java.sql.*;
import java.text.SimpleDateFormat;

import org.bukkit.Location;

public class BlockChange implements LookupCacheElement {
    private final static SimpleDateFormat formatter = new SimpleDateFormat("MM-dd HH:mm:ss");
    public final long id, date;
    public final Location loc;
    public final String playerName;
    public final int replaced, type;
    public final byte data;
    public final String signtext;
    public final ChestAccess ca;

    public BlockChange(final long date, final Location loc, final String playerName, final int replaced,
            final int type, final byte data, final String signtext, final ChestAccess ca) {
        this.id = 0;
        this.date = date;
        this.loc = loc;
        this.playerName = playerName;
        this.replaced = replaced;
        this.type = type;
        this.data = data;
        this.signtext = signtext;
        this.ca = ca;
    }

    public BlockChange(final ResultSet rs, final QueryParams p) throws SQLException {
        this.id = p.needId ? rs.getInt("id") : 0;
        this.date = p.needDate ? rs.getTimestamp("date").getTime() : 0;
        this.loc = p.needCoords ? new Location(p.world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                : null;
        this.playerName = p.needPlayer ? rs.getString("playername") : null;
        this.replaced = p.needType ? rs.getInt("replaced") : 0;
        this.type = p.needType ? rs.getInt("type") : 0;
        this.data = p.needData ? rs.getByte("data") : (byte) 0;
        this.signtext = p.needSignText ? rs.getString("signtext") : null;
        this.ca = p.needChestAccess && rs.getShort("itemtype") != 0 && rs.getShort("itemamount") != 0 ? new ChestAccess(
                rs.getShort("itemtype"), rs.getShort("itemamount"), rs.getByte("itemdata")) : null;
    }

    @Override
    public Location getLocation() {
        return this.loc;
    }

    @Override
    public String getMessage() {
        return toString();
    }

    @Override
    public String toString() {
        final StringBuilder msg = new StringBuilder();
        if (this.date > 0) msg.append(formatter.format(this.date) + " ");
        if (this.playerName != null) msg.append(this.playerName + " ");
        if (this.signtext != null) {
            final String action = this.type == 0 ? "destroyed " : "created ";
            if (!this.signtext.contains("\0"))
                msg.append(action + this.signtext);
            else msg.append(action + materialName(this.type != 0 ? this.type : this.replaced) + " ["
                    + this.signtext.replace("\0", "] [") + "]");
        } else if (this.type == this.replaced) {
            if (this.type == 0)
                msg.append("did an unspecified action");
            else if (this.ca != null) {
                if (this.ca.itemType == 0 || this.ca.itemAmount == 0)
                    msg.append("looked inside " + materialName(this.type));
                else if (this.ca.itemAmount < 0)
                    msg.append("took " + -this.ca.itemAmount + "x "
                            + materialName(this.ca.itemType, this.ca.itemData));
                else msg.append("put in " + this.ca.itemAmount + "x "
                        + materialName(this.ca.itemType, this.ca.itemData));
            } else if (this.type == 23 || this.type == 54 || this.type == 61 || this.type == 62)
                msg.append("opened " + materialName(this.type));
            else if (this.type == 64 || this.type == 71 || this.type == 96 || this.type == 107)
                msg.append((this.data == 0 ? "opened" : "closed") + " " + materialName(this.type));
            else if (this.type == 69)
                msg.append("switched " + materialName(this.type));
            else if (this.type == 77)
                msg.append("pressed " + materialName(this.type));
            else if (this.type == 92)
                msg.append("ate a piece of " + materialName(this.type));
            else if (this.type == 25 || this.type == 93 || this.type == 94)
                msg.append("changed " + materialName(this.type));
        } else if (this.type == 0)
            msg.append("destroyed " + materialName(this.replaced, this.data));
        else if (this.replaced == 0)
            msg.append("created " + materialName(this.type, this.data));
        else msg.append("replaced " + materialName(this.replaced, (byte) 0) + " with "
                + materialName(this.type, this.data));
        if (this.loc != null)
            msg.append(" at " + this.loc.getBlockX() + ":" + this.loc.getBlockY() + ":"
                    + this.loc.getBlockZ());
        return msg.toString();
    }
}
