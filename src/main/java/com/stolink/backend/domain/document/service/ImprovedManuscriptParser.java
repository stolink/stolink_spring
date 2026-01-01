package com.stolink.backend.domain.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 정규식을 최소화하고 상태 기반 휴리스틱 검증을 사용하는 개선된 원고 파서
 *
 * 이 버전은 "방어적 파싱(Defensive Parsing)" 원칙을 따릅니다.
 * 1. 명시적인 금지 조건 (쿼트 포함, 문장부호로 끝남 등)을 우선 검사
 * 2. 챕터 마커가 있더라도 문맥(공백 라인)이 맞지 않으면 기각
 * 3. 정규식은 매우 제한적으로만 사용 (숫자 패턴 등)
 */
public class ImprovedManuscriptParser {

    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MIN_EMPTY_LINES_BEFORE_CHAPTER = 2; // 더 엄격하게: 최소 2줄 공백
    private static final List<String> CHAPTER_MARKERS = List.of(
            "제", "第", "Chapter", "Part", "Book", "Volume", "Section", "편", "부", "권", "서문", "프롤로그", "에필로그");

    // 대화문이나 문장 감지를 위한 금지 문자들
    private static final List<String> FORBIDDEN_CHARS = List.of(
            "\"", "'", "“", "”", "‘", "’", // 따옴표 (대화문)
            "?", "!", "..." // 문장 끝맺음 부호 (제목에는 잘 안 쓰임)
    );

    /**
     * 챕터 헤더 검증 클래스
     */
    private static class ChapterHeaderValidator {

        /**
         * 1차 필터: 헤더 후보인지 빠르게 검증
         *
         * 조건:
         * 1. 빈 줄이 아님
         * 2. 길이가 MAX_TITLE_LENGTH 이내
         * 3. 금지된 문자(따옴표 등)가 없어야 함
         * 4. 앞에 충분한 공백 라인이 존재 (또는 파일 시작)
         */
        public static boolean isHeaderCandidate(String line, int emptyLineCount, boolean isFirstLine) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                return false;
            }

            if (trimmed.length() > MAX_TITLE_LENGTH) {
                return false;
            }

            // 금지 문자 포함 여부 확인 (대화문 등 제외)
            for (String forbidden : FORBIDDEN_CHARS) {
                if (trimmed.contains(forbidden)) {
                    return false;
                }
            }

            // 제목이 마침표로 끝나는 경우, 숫자로 시작하지 않으면 의심스러움 (문장일 가능성)
            // 예: "그는 집에 갔다." vs "1. 서론."
            if (trimmed.endsWith(".") && !Character.isDigit(trimmed.charAt(0))) {
                return false;
            }

            // 문맥 고려: 챕터 제목 앞에는 보통 빈 줄이 있음
            // 파일 시작이거나, 페이지 넘김(Form Feed) 문자 등이 있는 경우는 예외로 칠 수도 있으나 여기선 공백 라인만 봄
            // 명확한 키워드(Chapter 등)로 시작하면 공백 기준을 조금 완화(1줄)할 수도 있지만,
            // 사용자가 "엄격한" 기준을 원했으므로 기본적으로 2줄 이상을 요구하되,
            // "제N장" 처럼 매우 강력한 패턴은 1줄도 허용하도록 로직 세분화 가능.
            // 여기서는 안전하게: 첫 줄이 아니고 공백이 부족하면 일단 의심.
            if (!isFirstLine && emptyLineCount < MIN_EMPTY_LINES_BEFORE_CHAPTER) {
                // 공백이 부족해도, "강력한 마커"로 시작하고 길이가 아주 짧으면 허용 (예: "제1장")
                if (emptyLineCount >= 1 && isStrongHeaderPattern(trimmed)) {
                    return true;
                }
                return false;
            }

            return true;
        }

        // 공백이 부족해도 인정해줄 만한 강력한 패턴인지 확인
        private static boolean isStrongHeaderPattern(String text) {
            // "제"로 시작하고 "장/편/부"가 들어있는 짧은 문자열
            if (text.startsWith("제") && (text.contains("장") || text.contains("편") || text.contains("부")))
                return true;
            if (text.startsWith("Chapter") || text.startsWith("Part"))
                return true;
            return false;
        }

        /**
         * 2차 검증: 실제 챕터 헤더인지 정밀 검증
         */
        public static boolean validateHeader(String line) {
            String trimmed = line.trim();

            // Case 1: 숫자로 시작 ("1.", "1장", "IV" 등)
            if (startsWithNumber(trimmed)) {
                // 숫자로 시작하더라도 뒤에 텍스트가 따라올 때, 그 형식이 "1. 제목" 형태여야 함.
                // "10년 뒤에 만났다" 같은 문장은 걸러야 함.
                // 숫자가 나오고 점(.)이나 공백, 또는 챕터 단위(장, 편)가 바로 나와야 함.
                if (isValidNumberedHeader(trimmed)) {
                    return true;
                }
            }

            // Case 2: 챕터 마커로 시작 ("제", "Chapter", "프롤로그" 등)
            for (String marker : CHAPTER_MARKERS) {
                if (trimmed.startsWith(marker)) {
                    // "제"로 시작하는 경우 "제1장" 형태인지 추가 검증
                    if (marker.equals("제") || marker.equals("第")) {
                        return containsChapterKeyword(trimmed);
                    }
                    // 프롤로그, 에필로그, 서문 등은 바로 인정
                    if (isStandaloneTitle(marker)) {
                        return true;
                    }
                    // Chapter, Part 등은 뒤에 숫자가 와야 함
                    if (marker.equals("Chapter") || marker.equals("Part") || marker.equals("Book")) {
                        return containsNumberAfterMarker(trimmed, marker);
                    }
                    return true;
                }
            }

            return false;
        }

        private static boolean isStandaloneTitle(String marker) {
            return marker.equals("서문") || marker.equals("프롤로그") || marker.equals("에필로그");
        }

        private static boolean containsNumberAfterMarker(String text, String marker) {
            String contentAfterMarker = text.substring(marker.length()).trim();
            if (contentAfterMarker.isEmpty())
                return false;
            // 마커 뒤에 숫자가 시작되어야 함 (아라비아 or 로마)
            char first = contentAfterMarker.charAt(0);
            return Character.isDigit(first) || "IVXLCDM".indexOf(first) >= 0;
        }

        /**
         * 숫자로 시작하는지 검증
         */
        private static boolean startsWithNumber(String text) {
            if (text.isEmpty())
                return false;
            char first = text.charAt(0);
            return Character.isDigit(first) || "IVXLCDM".indexOf(first) >= 0;
        }

        /**
         * 숫자 헤더 유효성 검사 (예: "1. 서론" OK, "10년 뒤" False)
         */
        private static boolean isValidNumberedHeader(String text) {
            // 패턴: 숫자 + [. 혹은 공백 혹은 '장']
            // 정규식을 여기서 살짝 써서 검증 (state machine으로 하기엔 복잡함)
            // ^[0-9IVXLCDM]+(\.|장|부|편)?\s*.*
            // 하지만 정규식을 안 쓰기로 했으므로 char loop으로 확인

            int i = 0;
            int len = text.length();

            // 숫자 부분 스킵
            while (i < len && (Character.isDigit(text.charAt(i)) || "IVXLCDM".indexOf(text.charAt(i)) >= 0)) {
                i++;
            }

            if (i == len)
                return true; // 숫자만 있는 경우 ("1") OK

            char afterNumber = text.charAt(i);
            // 숫자 바로 뒤에 오는 문자가 점(.), 공백( ), 또는 단위(장, 편)여야 함
            return afterNumber == '.' || Character.isWhitespace(afterNumber) ||
                    "장편부권".indexOf(afterNumber) >= 0;
        }

        /**
         * 챕터 키워드 포함 여부 ("장", "편", "부", "권")
         */
        private static boolean containsChapterKeyword(String text) {
            return text.contains("장") || text.contains("편") ||
                    text.contains("부") || text.contains("권");
        }
    }

    /**
     * 파싱된 섹션 정보
     */
    public static class ParsedSection {
        private final String title;
        private final String content;

        public ParsedSection(String title, String content) {
            this.title = title;
            this.content = content;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * 원고 텍스트를 챕터 단위로 파싱
     */
    public static List<ParsedSection> parse(String manuscriptContent) {
        List<ParsedSection> sections = new ArrayList<>();
        // 줄바꿈 정규화
        String[] lines = manuscriptContent.split("\\r?\\n");

        StringBuilder contentBuilder = new StringBuilder();
        String pendingChapterTitle = null;
        int sectionNumber = 1;
        int emptyLineCount = 10; // 파일 시작 버퍼
        boolean isFirstContentLine = true;

        for (String line : lines) {
            String trimmed = line.trim();

            boolean isHeader = false;

            // 1. 헤더 후보 검증
            if (ChapterHeaderValidator.isHeaderCandidate(line, emptyLineCount, isFirstContentLine)) {
                // 2. 헤더 확정 검증
                if (ChapterHeaderValidator.validateHeader(line)) {
                    isHeader = true;
                }
            }

            if (isHeader) {
                // 이전 섹션 저장 (내용이 있을 경우)
                if (contentBuilder.length() > 0) {
                    String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber++;
                    sections.add(new ParsedSection(sectionTitle, contentBuilder.toString().trim()));
                    contentBuilder.setLength(0); // reset
                    pendingChapterTitle = null;
                }

                // 새 챕터 시작
                pendingChapterTitle = trimmed;
                emptyLineCount = 0;
                isFirstContentLine = false;
                continue; // 헤더 라인은 본문에 포함하지 않고 스킵
            }

            // 섹션 구분선 처리 (***, ---)
            if (isSectionDivider(trimmed)) {
                if (contentBuilder.length() > 0) {
                    String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber++;
                    sections.add(new ParsedSection(sectionTitle, contentBuilder.toString().trim()));
                    contentBuilder.setLength(0);
                    pendingChapterTitle = null;
                }
                emptyLineCount = 0;
                continue;
            }

            // 본문 내용 누적
            if (trimmed.isEmpty()) {
                emptyLineCount++;
            } else {
                emptyLineCount = 0;
                isFirstContentLine = false;
            }

            // 첫 빈 줄들을 스킵하고 싶다면 condition 추가 가능하지만,
            // 원본 보존을 위해 빈 줄도 포함하되, 섹션 간 경계에서의 빈 줄은 파싱 과정에서 자연스럽게 정리됨(title 앞 빈 줄은
            // emptyLineCount로 소모됨)
            // 단, contentBuilder에 넣을 때 너무 많은 빈 줄이 들어가는 것은 나중에 trim()으로 정리
            contentBuilder.append(line).append("\n");
        }

        // 마지막 섹션 저장
        if (contentBuilder.length() > 0) {
            String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber;
            sections.add(new ParsedSection(sectionTitle, contentBuilder.toString().trim()));
        }

        return sections;
    }

    /**
     * 섹션 구분선 확인
     */
    private static boolean isSectionDivider(String line) {
        if (line.length() < 3)
            return false;
        // 문자가 모두 같은지, 그리고 그 문자가 구분선 문자(*, -, =)인지
        char first = line.charAt(0);
        if (!("*".indexOf(first) >= 0 || "-".indexOf(first) >= 0 || "=".indexOf(first) >= 0)) {
            return false;
        }
        for (int i = 1; i < line.length(); i++) {
            if (line.charAt(i) != first)
                return false;
        }
        return true;
    }
}
