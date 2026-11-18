package com.moa.pbl;

import com.moa.pbl.data.ParsedTransaction;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 은행 문자·푸시 알림 문구에서 거래를 읽어냅니다. (인터넷 없이 폰 안에서 처리)
 * 예) [Web발신] [KB]11/16 14:30 123-**-****89 홍길동 입금 50,000원 잔액 1,250,000원
 */
public final class BankMessageParser {
    private BankMessageParser() {}

    private static final String[] BLOCK_WORDS = {"인증번호", "(광고)", "[광고]", "수신거부"};
    private static final String[] KEYWORDS = {"입금", "출금", "승인", "결제", "이체"};

    private static final Pattern AMOUNT = Pattern.compile("([0-9][0-9,]*)\\s*원");
    private static final Pattern BALANCE = Pattern.compile("(?:잔액|잔고)\\s*:?\\s*([0-9][0-9,]*)\\s*원?");
    private static final Pattern YMD = Pattern.compile("(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern MD_SLASH = Pattern.compile("(?<!\\d)(\\d{1,2})/(\\d{1,2})(?!\\d)");
    private static final Pattern MD_KOR = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern TIME = Pattern.compile("(?<!\\d)(\\d{1,2}):(\\d{2})(?!\\d)");
    private static final Pattern TAG = Pattern.compile("\\[([^\\]]{1,14})\\]");
    private static final Pattern ACCOUNT_NO = Pattern.compile("[0-9*xX]{2,}(?:-[0-9*xX]+)+");
    private static final Pattern MONTHS = Pattern.compile("\\d+개월");
    private static final Pattern SENTENCE_END = Pattern.compile(".*(입금|출금|결제|승인|이체)(했|하셨|되었|됐|완료).*");

    private static final Set<String> IGNORED_TAGS = new HashSet<>(Arrays.asList("web발신", "국제발신", "광고", "fw"));
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "입금", "출금", "승인", "결제", "이체", "취소", "승인취소", "체크카드", "신용카드", "일시불", "누적", "할부", "잔액", "잔고", "님", "원"));
    private static final Map<String, String> ACCOUNT_LABELS = new HashMap<>();

    static {
        ACCOUNT_LABELS.put("KB", "KB국민은행");
        ACCOUNT_LABELS.put("국민", "KB국민은행");
        ACCOUNT_LABELS.put("KB국민", "KB국민은행");
        ACCOUNT_LABELS.put("신한", "신한은행");
        ACCOUNT_LABELS.put("우리", "우리은행");
        ACCOUNT_LABELS.put("하나", "하나은행");
        ACCOUNT_LABELS.put("NH", "NH농협은행");
        ACCOUNT_LABELS.put("농협", "NH농협은행");
        ACCOUNT_LABELS.put("IBK", "IBK기업은행");
        ACCOUNT_LABELS.put("기업", "IBK기업은행");
        ACCOUNT_LABELS.put("카카오뱅크", "카카오뱅크");
        ACCOUNT_LABELS.put("토스뱅크", "토스뱅크");
        ACCOUNT_LABELS.put("케이뱅크", "케이뱅크");
        ACCOUNT_LABELS.put("SC제일", "SC제일은행");
    }

    /** 은행 입출금 문구처럼 보이는지 (패키지 허용 여부를 판단하기 전에 가볍게 확인) */
    public static boolean looksLikeBankMessage(String text) {
        if (text == null || !text.contains("원")) return false;
        for (String blocked : BLOCK_WORDS) {
            if (text.contains(blocked)) return false;
        }
        for (String keyword : KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    /** 거래로 읽을 수 없으면 null */
    public static BankEvent parse(String body, String title, long postTimeMillis, ZoneId zone) {
        if (body == null) return null;
        String text = body.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (!looksLikeBankMessage(text)) return null;

        // 금액: '잔액' 앞부분의 첫 번째 금액
        int balanceWord = text.indexOf("잔액");
        if (balanceWord < 0) balanceWord = text.indexOf("잔고");
        String head = balanceWord > 0 ? text.substring(0, balanceWord) : text;
        Matcher amountMatcher = AMOUNT.matcher(head);
        if (!amountMatcher.find()) return null;
        Long amount = parseMoney(amountMatcher.group(1));
        if (amount == null || amount <= 0) return null;

        // 입금/출금 구분
        int in = text.indexOf("입금");
        int out = text.indexOf("출금");
        boolean cancelled = text.contains("취소") && (text.contains("승인") || text.contains("결제"));
        boolean income;
        if (cancelled) income = true; // 카드 승인 취소는 환불로 봅니다.
        else if (in >= 0 && (out < 0 || in < out)) income = true;
        else if (out >= 0) income = false;
        else income = false; // 승인, 결제, 이체

        Matcher balanceMatcher = BALANCE.matcher(text);
        Long balance = balanceMatcher.find() ? parseMoney(balanceMatcher.group(1)) : null;

        ZonedDateTime post = Instant.ofEpochMilli(postTimeMillis).atZone(zone);
        LocalDateTime when = resolveDateTime(text, post);
        long whenMillis = when.atZone(zone).toInstant().toEpochMilli();

        String account = accountLabel(text, title);
        String name = itemName(text);
        if (name.isEmpty()) name = account + (income ? " 입금" : " 출금");
        // 승인 취소(환불)는 원래 지출했던 카테고리로 분류합니다.
        String category = CategoryRules.categorize(name, income && !cancelled);

        ParsedTransaction transaction = new ParsedTransaction(
                name, amount, income ? "income" : "expense", category, when.toLocalDate().toString());
        return new BankEvent(transaction, account, balance, whenMillis);
    }

    private static Long parseMoney(String digits) {
        String clean = digits.replace(",", "");
        if (clean.isEmpty() || clean.length() > 15) return null;
        try {
            return Long.parseLong(clean);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime resolveDateTime(String text, ZonedDateTime post) {
        LocalDate date = post.toLocalDate();
        LocalTime time = post.toLocalTime().withNano(0);

        boolean dated = false;
        Matcher ymd = YMD.matcher(text);
        while (ymd.find()) {
            int year = Integer.parseInt(ymd.group(1));
            if (year < 2000 || year > 2100) continue;
            try {
                date = LocalDate.of(year, Integer.parseInt(ymd.group(2)), Integer.parseInt(ymd.group(3)));
                dated = true;
                break;
            } catch (DateTimeException ignored) {
                // 날짜가 아니면(계좌번호 등) 다음 후보를 봅니다.
            }
        }
        if (!dated) {
            Matcher md = MD_SLASH.matcher(text);
            if (md.find()) {
                date = inferDate(Integer.parseInt(md.group(1)), Integer.parseInt(md.group(2)), date);
            } else {
                Matcher kor = MD_KOR.matcher(text);
                if (kor.find()) date = inferDate(Integer.parseInt(kor.group(1)), Integer.parseInt(kor.group(2)), date);
            }
        }
        Matcher clock = TIME.matcher(text);
        if (clock.find()) {
            try {
                time = LocalTime.of(Integer.parseInt(clock.group(1)), Integer.parseInt(clock.group(2)));
            } catch (DateTimeException ignored) {
                // 시각이 이상하면 알림이 온 시각을 씁니다.
            }
        }
        return LocalDateTime.of(date, time);
    }

    /** 연도가 없는 날짜: 올해로 보되, 도착한 날보다 미래가 되면 작년으로 봅니다. */
    private static LocalDate inferDate(int month, int day, LocalDate base) {
        try {
            LocalDate candidate = LocalDate.of(base.getYear(), month, day);
            if (candidate.isAfter(base.plusDays(1))) candidate = candidate.minusYears(1);
            return candidate;
        } catch (DateTimeException e) {
            return base;
        }
    }

    private static String accountLabel(String text, String title) {
        Matcher tags = TAG.matcher(text);
        while (tags.find()) {
            String tag = tags.group(1).trim();
            if (tag.isEmpty() || IGNORED_TAGS.contains(tag.toLowerCase(Locale.ROOT))) continue;
            String mapped = ACCOUNT_LABELS.get(tag.toUpperCase(Locale.ROOT));
            return mapped != null ? mapped : tag;
        }
        if (title != null) {
            String t = title.trim();
            if (!t.isEmpty() && (t.contains("은행") || t.contains("뱅크") || t.toUpperCase(Locale.ROOT).startsWith("KB"))) {
                return t.length() > 20 ? t.substring(0, 20) : t;
            }
        }
        return "알림 계좌";
    }

    /** 문구에서 날짜·금액·계좌번호·상용구를 지우고 남은 말(보낸 사람, 가게 이름)을 항목명으로 씁니다. */
    private static String itemName(String text) {
        String s = text.replaceAll("\\[[^\\]]*\\]", " ");
        s = YMD.matcher(s).replaceAll(" ");
        s = MD_SLASH.matcher(s).replaceAll(" ");
        s = MD_KOR.matcher(s).replaceAll(" ");
        s = TIME.matcher(s).replaceAll(" ");
        s = BALANCE.matcher(s).replaceAll(" ");
        s = AMOUNT.matcher(s).replaceAll(" ");
        s = ACCOUNT_NO.matcher(s).replaceAll(" ");

        List<String> kept = new ArrayList<>();
        for (String rawToken : s.trim().split("[\\s,:;|]+")) {
            String token = rawToken.replaceAll("(님이|님께서|님)$", ""); // 홍길동님이 -> 홍길동
            if (token.length() < 2 || STOP_WORDS.contains(token)) continue; // 한 글자(을, 를 등)는 버림
            if (token.contains("*") || MONTHS.matcher(token).matches()) continue; // 가려진 이름(홍*동), 할부 개월
            if (token.matches("[0-9\\p{Punct}]+")) continue;
            if (SENTENCE_END.matcher(token).matches()) continue; // '입금했어요.' 같은 문장 끝말
            kept.add(token);
            if (kept.size() == 3) break;
        }
        String name = String.join(" ", kept);
        return name.length() > 30 ? name.substring(0, 30) : name;
    }
}
