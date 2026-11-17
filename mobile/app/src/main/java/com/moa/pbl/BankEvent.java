package com.moa.pbl;

import com.moa.pbl.data.ParsedTransaction;

/** 문자·알림 한 건에서 읽어낸 거래와, 잔고 계산에 필요한 부가 정보 */
public final class BankEvent {
    public final ParsedTransaction transaction;
    public final String accountName;      // 예: KB국민은행
    public final Long balanceAfter;       // 거래 직후 잔액 (문구에 없으면 null)
    public final long occurredAtMillis;   // 거래 시각

    public BankEvent(ParsedTransaction transaction, String accountName, Long balanceAfter, long occurredAtMillis) {
        this.transaction = transaction;
        this.accountName = accountName;
        this.balanceAfter = balanceAfter;
        this.occurredAtMillis = occurredAtMillis;
    }
}
