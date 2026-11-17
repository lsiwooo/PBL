package com.moa.pbl.data;

public final class ParsedTransaction {
    private final String itemName;
    private final long amount;
    private final String transactionType;
    private final String category;
    private final String occurredOn; // yyyy-MM-dd. null 이면 저장하는 날(오늘)로 기록됩니다.

    /** 기존 코드(자연어 분석 결과)가 쓰는 생성자. 그대로 동작합니다. */
    public ParsedTransaction(String itemName, long amount, String transactionType, String category) {
        this(itemName, amount, transactionType, category, null);
    }

    /** 문자·알림, 거래내역 파일처럼 실제 거래 날짜를 아는 경우에 쓰는 생성자 */
    public ParsedTransaction(String itemName, long amount, String transactionType, String category, String occurredOn) {
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
