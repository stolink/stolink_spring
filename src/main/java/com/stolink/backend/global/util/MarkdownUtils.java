package com.stolink.backend.global.util;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import lombok.extern.slf4j.Slf4j;

/**
 * 마크다운 변환 유틸리티 클래스
 * flexmark-java를 사용하여 마크다운을 HTML로 변환
 */
@Slf4j
public class MarkdownUtils {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    // 마크다운 패턴 감지를 위한 정규식
    private static final Pattern MARKDOWN_PATTERNS = Pattern.compile(
            "(^#{1,6}\\s+.+$)" + // 헤딩 (#, ##, ###...)
                    "|(^[*_]{1,3}.+[*_]{1,3}$)" + // 볼드/이탤릭 (*text*, **text**, _text_, __text__)
                    "|(^\\s*[-*+]\\s+.+$)" + // 리스트 (- item, * item, + item)
                    "|(^\\s*\\d+\\.\\s+.+$)" + // 순서 리스트 (1. item)
                    "|(^>.+$)" + // 인용구 (> text)
                    "|(```[\\s\\S]*?```)" + // 코드 블록 (```code```)
                    "|(^\\[.+\\]\\(.+\\)$)" + // 링크 ([text](url))
                    "|(^!\\[.+\\]\\(.+\\)$)" + // 이미지 (![alt](url))
                    "|(\\[\\s*[xX]?\\s*\\])", // 체크박스 ([ ] or [x])
            Pattern.MULTILINE);

    // HTML 태그 패턴
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    static {
        MutableDataSet options = new MutableDataSet();

        // GFM (GitHub Flavored Markdown) 확장 기능 활성화
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()));

        // 소프트 줄바꿈을 하드 줄바꿈으로 처리
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

        PARSER = Parser.builder(options).build();
        RENDERER = HtmlRenderer.builder(options).build();
    }

    private MarkdownUtils() {
        // 유틸리티 클래스 - 인스턴스 생성 방지
    }

    /**
     * 마크다운 텍스트를 HTML로 변환
     *
     * @param markdown 마크다운 텍스트
     * @return HTML 문자열
     */
    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }

        try {
            Node document = PARSER.parse(markdown);
            return RENDERER.render(document).trim();
        } catch (Exception e) {
            log.error("마크다운 변환 실패: {}", e.getMessage());
            // 변환 실패 시 원본 텍스트를 <p> 태그로 감싸서 반환
            return wrapInParagraphs(markdown);
        }
    }

    /**
     * 텍스트가 마크다운 형식인지 감지
     * 마크다운 특유의 패턴이 있고, HTML 태그가 거의 없으면 마크다운으로 판단
     *
     * @param text 검사할 텍스트
     * @return 마크다운 형식이면 true
     */
    public static boolean isMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // HTML 태그가 많으면 이미 HTML임
        long htmlTagCount = HTML_TAG_PATTERN.matcher(text).results().count();
        if (htmlTagCount > 5) {
            return false;
        }

        // 마크다운 패턴이 있는지 확인
        return MARKDOWN_PATTERNS.matcher(text).find();
    }

    /**
     * 마크다운 텍스트인 경우 HTML로 변환, 아니면 원본 반환
     *
     * @param content 변환할 텍스트
     * @return 변환된 HTML 또는 원본 텍스트
     */
    public static String convertIfMarkdown(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        if (isMarkdown(content)) {
            log.debug("마크다운 감지됨, HTML로 변환 중...");
            return toHtml(content);
        }

        return content;
    }

    /**
     * 일반 텍스트를 <p> 태그로 감싸기
     *
     * @param text 일반 텍스트
     * @return HTML 문자열
     */
    private static String wrapInParagraphs(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n\n+");

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                sb.append("<p>").append(trimmed.replace("\n", "<br />")).append("</p>\n");
            }
        }

        return sb.toString().trim();
    }
}
