package com.moa.pbl.data;

public final class LedgerEntry {
    private final String itemName;
    private final long amount;
    private final String transactionType;
    private final String category;
    private final String occurredOn;

    public LedgerEntry(String itemName, long amount, String transactionType, String category, String occurredOn) {
        this.itemName = itemName;
        this.amount = amount;
        this.transactionType = transactionType;
        this.category = category;
        this.occurredOn = occurredOn;
    }

    public String itemName() { return itemName; }
    public long amount() { return amount; }
    public String transactionType() { return transactionType; }
    public String category() { return category; }
    public String occurredOn() { return occurredOn; }
}
