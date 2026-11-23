package app.morphe.extension.boost.utils;

import org.junit.Test;

import java.net.URLDecoder;

import static org.junit.Assert.*;

public class ImageSearchMenuTest {
    @Test public void googleLensEncodesTheCompleteImageUrl() {
        String image = "https://example.com/a b+\u00e9.png?token=a%2Fb&width=100#frag";
        String target = ImageSearchMenu.ENGINES[0].url(image);
        assertEquals("https://lens.google.com/uploadbyurl?url=https%3A%2F%2Fexample.com%2Fa+b%2B%C3%A9.png"
                + "%3Ftoken%3Da%252Fb%26width%3D100%23frag", target);
    }

    @Test public void everyEnginePreservesSignedAndAlreadyEncodedSourceUrls() throws Exception {
        String image = "https://preview.redd.it/image.jpeg?format=pjpg&s=abc%2Fdef%3D";
        for (ImageSearchMenu.Engine engine : ImageSearchMenu.ENGINES) {
            String target = engine.url(image);
            int placeholder = engine.template.indexOf("[url]");
            String prefix = engine.template.substring(0, placeholder);
            String suffix = engine.template.substring(placeholder + "[url]".length());
            assertTrue(engine.title + ": " + target, target.startsWith(prefix) && target.endsWith(suffix));
            String encodedImage = target.substring(prefix.length(), target.length() - suffix.length());
            assertEquals(engine.title, image, URLDecoder.decode(encodedImage, "UTF-8"));
        }
    }

    @Test public void newEngineTemplatesCanPlaceTheImageBetweenOtherParameters() {
        ImageSearchMenu.Engine engine = new ImageSearchMenu.Engine("Example",
                "https://example.com/search?mode=image&source=[url]&locale=en");
        assertEquals("https://example.com/search?mode=image&source=https%3A%2F%2Fi.redd.it%2Fa.png&locale=en",
                engine.url("https://i.redd.it/a.png"));
    }
}
