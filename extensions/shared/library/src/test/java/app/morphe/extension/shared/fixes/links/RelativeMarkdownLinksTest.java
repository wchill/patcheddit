/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

package app.morphe.extension.shared.fixes.links;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class RelativeMarkdownLinksTest {
    private static String broken(String label, String target) {
        return "[" + label + "](<a href=\"/" + target + "\">" + target + "</a>)";
    }

    @Test
    public void repairsMultipleLinksPreservingLabelMarkupAndEverythingOutside() {
        String input = "<p><i>Before</i> " + broken("<b>Member</b>", "u/Example_Member")
                + " " + broken("Community", "r/example-sub")
                + " <a href=\"https://example.com/a_b\">working</a></p>";
        String expected = "<p><i>Before</i> <a href=\"https://www.reddit.com/u/Example_Member\"><b>Member</b></a>"
                + " <a href=\"https://www.reddit.com/r/example-sub\">Community</a>"
                + " <a href=\"https://example.com/a_b\">working</a></p>";
        assertEquals(expected, RelativeMarkdownLinks.repairHtml(input));
        assertSame(expected, RelativeMarkdownLinks.repairHtml(expected));
    }

    @Test
    public void acceptsOptionalSlashesCaseAndNativeAbsoluteAutolinksWithoutDuplicateLabelAnchors() {
        String input = "[<a href='/u/other'>u/other</a> label](/<A HREF='https://old.reddit.com/U/Example-Name/'>"
                + "U/Example-Name</A>/)";
        assertEquals("<a href=\"https://www.reddit.com/u/Example-Name\">u/other label</a>",
                RelativeMarkdownLinks.repairHtml(input));
        assertEquals("<a href=\"https://www.reddit.com/u/Example-Name\">Member</a>",
                RelativeMarkdownLinks.repairHtml("[Member](<a href='/u/example-name'>u/Example-Name</a>)"));
    }

    @Test
    public void preservesCodeAndMonospaceIncludingNestedFormattingAndPartialOverlap() {
        String link = broken("Member", "u/member");
        for (String tag : new String[]{"code", "pre", "tt", "span style='font-family: monospace'",
                "font face='monospace'"}) {
            String close = tag.split(" ", 2)[0];
            String input = "<" + tag + "><span>" + link + "</span></" + close + "> " + link;
            assertEquals("<" + tag + "><span>" + link + "</span></" + close + "> "
                            + "<a href=\"https://www.reddit.com/u/member\">Member</a>",
                    RelativeMarkdownLinks.repairHtml(input));
        }
        String overlap = broken("<code>Member</code> label", "u/member");
        assertSame(overlap, RelativeMarkdownLinks.repairHtml(overlap));
    }

    @Test
    public void leavesExamplesNestedLabelsAndUnrelatedOrMismatchedAutolinksIntact() {
        for (String input : new String[]{
                "[Member](u/member)", "[Member](<a>u/member</a>)",
                broken("", "u/member"), broken("line\nbreak", "u/member"),
                broken("line<br>break", "u/member"), broken("&#91;member&#93;", "u/member"),
                "[outer " + broken("inner", "u/member") + "]",
                broken("<b>unclosed", "u/member"), broken("wrong", "example.com"),
                "[Member](<a href='https://example.com/u/member'>u/member</a>)",
                "[Member](<a href='/u/other'>u/member</a>)",
                "<a href='/u/outside'>" + broken("Member", "u/member") + "</a>",
                "<span title='" + broken("Member", "u/member") + "'>example</span>",
                "<!-- " + broken("Member", "u/member") + " -->",
                "<!-- " + broken("Member", "u/member"),
                "[Member]( /<a href='/u/member'>/u/member</a>)",
                "[Member](/<a href='/u/member'>/u/member</a>)",
                "[Member](<a href='/u/member'>u/member/</a>/)",
                broken("<b unclosed", "u/member"),
                "\\" + broken("Member", "u/member")}) {
            assertSame(input, RelativeMarkdownLinks.repairHtml(input));
        }
    }

    @Test
    public void rejectsTruncatedAndOversizedInputWithoutMutatingTheOriginal() {
        for (String input : new String[]{"[Member](<a href='/u/member'>u/member",
                "x".repeat(1024 * 1024) + broken("Member", "u/member")}) {
            assertSame(input, RelativeMarkdownLinks.repairHtml(input));
        }
        assertSame(null, RelativeMarkdownLinks.repairHtml(null));
    }

    @Test
    public void composesWithEscapedUrlResponseRepairBeforeTheNativeRendererRuns() throws Exception {
        String html = "<p>" + broken("Member", "u/member")
                + " <a href=\"https://example.com/a\\_b\">external</a></p>";
        String response = new org.json.JSONObject().put("kind", "t1").put("data",
                new org.json.JSONObject().put("body_html", html)).toString();
        String repaired = new org.json.JSONObject(EscapedUrls.repairRedditResponse(response))
                .getJSONObject("data").getString("body_html");
        assertEquals("<p><a href=\"https://www.reddit.com/u/member\">Member</a>"
                        + " <a href=\"https://example.com/a_b\">external</a></p>",
                RelativeMarkdownLinks.repairHtml(repaired));
    }
}
