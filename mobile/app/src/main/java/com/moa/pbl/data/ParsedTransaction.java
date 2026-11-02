package com.moa.pbl.data;

public final class ParsedTransaction {
    private final String itemName;
    private final long amount;
    private final String transactionType;
    private final String category;

    public ParsedTransaction(String itemName, long amount, String transactionType, String category) {
        this.itemName = itemName;
        this.amount = amount;
        this.transactionType = transactionType;
        this.category = category;
    }

    public String itemName() { return itemName; }
    public long amount() { return amount; }
    public String transactionType() { return transactionType; }
    public String category() { return category; }
}
