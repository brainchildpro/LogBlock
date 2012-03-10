package de.diddiz.LogBlock;

public class ChestAccess {
    final short itemType, itemAmount;
    final byte itemData;

    public ChestAccess(final short itemType, final short itemAmount, final byte itemData) {
        this.itemType = itemType;
        this.itemAmount = itemAmount;
        this.itemData = itemData >= 0 ? itemData : 0;
    }
}
