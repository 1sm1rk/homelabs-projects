package de.homelabs.projects.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichTextTest {

    @Test
    void dropsDangerousMarkup() {
        assertThat(RichText.sanitize("<p>ok</p><script>alert(1)</script>"))
                .contains("ok")
                .doesNotContain("<script");
        assertThat(RichText.sanitize("<b onclick=\"steal()\">fett</b>"))
                .isEqualTo("<b>fett</b>");
        assertThat(RichText.sanitize("<p style=\"color:red\" class=\"x\">text</p>"))
                .isEqualTo("<p>text</p>");
        assertThat(RichText.sanitize("<a href=\"javascript:alert(1)\">klick</a>"))
                .doesNotContain("javascript:");
    }

    @Test
    void keepsTheAllowedEditorMarkup() {
        assertThat(RichText.sanitize("<h2>Titel</h2><ul><li>eins</li></ul><pre><code>x()</code></pre>"))
                .contains("<h2>Titel</h2>")
                .contains("<li>eins</li>")
                .contains("<code>x()</code>");
        assertThat(RichText.sanitize("<table><tbody><tr><td>a</td></tr></tbody></table>"))
                .contains("<td>a</td>");
        assertThat(RichText.sanitize("<blockquote><p>zitat</p></blockquote>")).contains("<blockquote>");
    }

    @Test
    void restrictsImageSourcesToAttachmentUrls() {
        assertThat(RichText.sanitize("<img src=\"/api/attachments/abc-123/content\" alt=\"plan\">"))
                .contains("/api/attachments/abc-123/content")
                .contains("alt=\"plan\"");
        assertThat(RichText.sanitize("<img src=\"https://example.org/tracker.png\">"))
                .doesNotContain("example.org");
        assertThat(RichText.sanitize("<img src=\"javascript:alert(1)\">"))
                .doesNotContain("javascript");
    }

    @Test
    void wrapsPlainTextIntoParagraphsAndBreaks() {
        assertThat(RichText.plainToHtml("Hallo Welt")).isEqualTo("<p>Hallo Welt</p>");
        assertThat(RichText.plainToHtml("erste\nzweite")).isEqualTo("<p>erste<br>zweite</p>");
        assertThat(RichText.plainToHtml("absatz eins\n\nabsatz zwei"))
                .isEqualTo("<p>absatz eins</p><p>absatz zwei</p>");
        assertThat(RichText.plainToHtml("  ")).isEmpty();
        // markup typed by a plain-text writer stays text
        assertThat(RichText.plainToHtml("<b>kein html</b>")).isEqualTo("<p>&lt;b&gt;kein html&lt;/b&gt;</p>");
    }

    @Test
    void derivesPlainTextFromSanitizedHtml() {
        assertThat(RichText.toPlainText("<p>Hallo <strong>Welt</strong></p><p>zweite Zeile</p>"))
                .isEqualTo("Hallo Welt zweite Zeile");
        assertThat(RichText.toPlainText("<ul><li>a</li><li>b</li></ul>")).isEqualTo("a b");
        assertThat(RichText.toPlainText("<p>Umlaute &amp; Zeichen &lt;ok&gt;</p>"))
                .isEqualTo("Umlaute & Zeichen <ok>");
        assertThat(RichText.toPlainText("")).isEmpty();
    }

    @Test
    void canonicalizesEitherShapeIntoHtmlPlusText() {
        RichText.Content plain = RichText.canonicalize("Zeile eins\n\nZeile zwei", null);
        assertThat(plain.html()).isEqualTo("<p>Zeile eins</p><p>Zeile zwei</p>");
        assertThat(plain.text()).isEqualTo("Zeile eins Zeile zwei");

        RichText.Content rich = RichText.canonicalize(null, "<p>fett</p>");
        assertThat(rich.html()).isEqualTo("<p>fett</p>");
        assertThat(rich.text()).isEqualTo("fett");

        RichText.Content stored = RichText.canonicalize(null, "<p>ok</p><script>x()</script>");
        assertThat(stored.html()).doesNotContain("<script");
        assertThat(stored.text()).doesNotContain("<script");

        assertThat(RichText.canonicalize(null, null)).isEqualTo(new RichText.Content("", ""));
    }

    @Test
    void rejectsBothContentShapesAtOnce() {
        assertThatThrownBy(() -> RichText.canonicalize("text", "<p>text</p>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either description or description_html");
    }

    @Test
    void excerptsTheFirstTwoHundredCharacters() {
        String longText = "a".repeat(250);

        assertThat(RichText.excerpt(longText, 200)).hasSize(201).endsWith("…");
        assertThat(RichText.excerpt("kurz", 200)).isEqualTo("kurz");
        assertThat(RichText.excerpt(null, 200)).isEmpty();
    }
}
