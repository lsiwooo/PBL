package com.moa.pbl;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import com.moa.pbl.data.SupabaseRepository;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 은행 앱 푸시나 문자 알림이 오면 입출금 내용을 읽어 가계부에 자동 저장합니다.
 * 설정 > 알림 접근 권한에서 '자연어 가계부'를 켜야 동작합니다.
 */
public class BankNotificationListener extends NotificationListenerService {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long DUPLICATE_WINDOW_MS = 60_000L;
    private static String lastKey = "";
    private static long lastKeyTime = 0L;

    /**
     * 읽을 앱 목록입니다. (개인 대화 앱의 알림은 읽지 않습니다.)
     * 사용하는 은행 앱이 없어서 저장이 안 되면, 자동 저장 기록에 '허용 목록에 없는 앱' 메시지와 함께
     * 앱 패키지 이름이 표시되니 그 이름을 여기에 추가하세요. (아래 은행 앱 이름은 확인하지 못한 값이 섞여 있을 수 있어요)
     */
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
            // 문자 앱
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            // 은행 앱
            "com.kbstar.kbbank",
            "com.kbstar.reboot",
            "viva.republica.toss",
            "com.kakaobank.channel",
            "com.shinhan.sbanking",
            "com.wooribank.smart.npib",
            "com.kbankwith.smartbank"
    ));

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String packageName = sbn.getPackageName();
        if (packageName == null || packageName.equals(getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.extras == null) return;
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle extras = notification.extras;
        String title = asText(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = asText(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = asText(extras.getCharSequence(Notification.EXTRA_TEXT));

        // 입출금 문구로 보이지 않으면 바로 버립니다. (내용은 저장하거나 보내지 않습니다)
        if (!BankMessageParser.looksLikeBankMessage(text)) return;

        if (!ALLOWED_PACKAGES.contains(packageName)) {
            BankSyncStore.appendLog(this, "허용 목록에 없는 앱의 입출금 알림을 무시했어요: " + packageName);
            return;
        }

        // 같은 알림이 짧은 시간에 여러 번 올 때(알림 갱신 등) 한 번만 처리합니다.
        long now = System.currentTimeMillis();
        String key = packageName + "|" + text;
        synchronized (BankNotificationListener.class) {
            if (key.equals(lastKey) && now - lastKeyTime < DUPLICATE_WINDOW_MS) return;
            lastKey = key;
            lastKeyTime = now;
        }

        final String finalTitle = title;
        final String finalText = text;
        final long postTime = sbn.getPostTime();
        IO.execute(() -> handle(finalTitle, finalText, postTime));
    }

    private void handle(String title, String text, long postTime) {
        BankEvent event = BankMessageParser.parse(text, title, postTime, ZoneId.systemDefault());
        if (event == null) {
            BankSyncStore.appendLog(this, "입출금 문구로 보이지만 금액·구분을 읽지 못해 건너뛰었어요.");
            return;
        }
        String label = describe(event);
        SupabaseRepository repository = new SupabaseRepository(this);
        if (!repository.isConfigured() || !repository.hasSession()) {
            BankSyncStore.addPending(this, event);
            BankSyncStore.appendLog(this, "로그인이 필요해 대기 목록에 넣었어요: " + label);
            return;
        }
        try {
            BankSyncStore.flushPending(this, repository); // 밀려 있던 거래를 먼저 저장 (실패해도 계속)
            repository.insertLedgerEntries(Collections.singletonList(event.transaction));
            BankSyncStore.updateBalance(this, event.accountName, event.balanceAfter, event.occurredAtMillis);
            BankSyncStore.appendLog(this, "자동 저장: " + label);
        } catch (Exception error) {
            BankSyncStore.addPending(this, event);
            String reason = error.getMessage() == null ? "" : error.getMessage();
            if (reason.length() > 40) reason = reason.substring(0, 40);
            BankSyncStore.appendLog(this, "저장에 실패해 대기 목록에 넣었어요: " + label + " (" + reason + ")");
        }
    }

    private static String describe(BankEvent event) {
        String sign = "income".equals(event.transaction.transactionType()) ? "+" : "-";
        String money = NumberFormat.getInstance(Locale.KOREA).format(event.transaction.amount());
        return event.transaction.itemName() + " " + sign + money + "원 (" + event.accountName + ")";
    }

    private static String asText(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
