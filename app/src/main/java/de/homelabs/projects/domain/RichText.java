package de.homelabs.projects.domain;

import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.web.util.HtmlUtils;

/**
 * The content pipeline: canonical HTML plus its derived plain text.
 *
 * <p>Canonical content is ALWAYS the sanitized HTML — nothing untrusted is ever
 * stored raw. Writers submit either plain text (agents rarely send markup) or HTML;
 * plain text becomes simple paragraph markup first, then both paths pass the same
 * allowlist. {@code description_text} is derived from the sanitized HTML (tags
 * stripped, whitespace collapsed, entities decoded) and feeds lexical search and the
 * lean list excerpts.
 *
 * <p>The allowlist is deliberately narrow: headings, paragraphs and breaks, lists,
 * blockquote, code/pre, tables, basic inline marks, links (http/https/mailto) and
 * images RESTRICTED to this API's attachment URLs. No styles, no classes, no event
 * handlers, no embedded content — an editor that emits more will have it dropped
 * instead of stored.
 */
public final class RichText {

    /** Canonical HTML plus the plain text derived from it. */
    public record Content(String html, String text) {
    }

    /** Image sources are limited to attachment downloads served by this API. */
    private static final Pattern ATTACHMENT_SRC = Pattern.compile("^/api/attachments/[A-Za-z0-9._/-]+$");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "hr", "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li", "blockquote", "pre", "code",
                    "strong", "em", "b", "i", "u", "s", "del", "ins", "sub", "sup",
                    "table", "thead", "tbody", "tfoot", "tr", "th", "td", "a", "img")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href", "title").onElements("a")
            .requireRelNofollowOnLinks()
            .allowAttributes("src").matching(ATTACHMENT_SRC).onElements("img")
            .allowAttributes("alt", "title").onElements("img")
            .toFactory();

    private static final Pattern BLOCK_BOUNDARY = Pattern.compile(
            "(?i)</(p|div|li|ul|ol|h[1-6]|blockquote|pre|tr|table|thead|tbody|tfoot)>|<br\\s*/?>");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\r?\\n\\s*\\r?\\n");
    private static final Pattern LINE_BREAK = Pattern.compile("\\r?\\n");

    /** Sanitizes untrusted HTML against the allowlist; {@code null} becomes empty content. */
    public static String sanitize(String html) {
        return html == null ? "" : POLICY.sanitize(html);
    }

    /** Wraps plain text into simple paragraph markup (blank line = paragraph, newline = break). */
    public static String plainToHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder html = new StringBuilder();
        for (String paragraph : PARAGRAPH_BREAK.split(text.strip())) {
            if (paragraph.isBlank()) {
                continue;
            }
            String escaped = HtmlUtils.htmlEscape(paragraph.strip());
            html.append("<p>").append(LINE_BREAK.matcher(escaped).replaceAll("<br>")).append("</p>");
        }
        return html.toString();
    }

    /** Derives plain text from SANITIZED HTML: block boundaries become spaces, tags are dropped. */
    public static String toPlainText(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return "";
        }
        String spaced = BLOCK_BOUNDARY.matcher(sanitizedHtml).replaceAll(" ");
        String stripped = TAG.matcher(spaced).replaceAll(" ");
        String decoded = HtmlUtils.htmlUnescape(stripped);
        return WHITESPACE.matcher(decoded).replaceAll(" ").strip();
    }

    /**
     * Canonicalizes a write: exactly one of the two shapes may be supplied. Both
     * {@code null} means "no content" (empty).
     */
    public static Content canonicalize(String description, String descriptionHtml) {
        if (description != null && descriptionHtml != null) {
            throw new IllegalArgumentException(
                    "pass either description or description_html, not both");
        }
        String html;
        if (descriptionHtml != null) {
            html = sanitize(descriptionHtml);
        } else if (description != null) {
            html = sanitize(plainToHtml(description));
        } else {
            return new Content("", "");
        }
        return new Content(html, toPlainText(html));
    }

    /** Lean list excerpt of already-derived plain text (~200 characters by default). */
    public static String excerpt(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String flat = text.strip();
        if (flat.length() <= maxLength) {
            return flat;
        }
        int cut = Character.isHighSurrogate(flat.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return flat.substring(0, cut).stripTrailing() + "…";
    }

    private RichText() {
    }
}
