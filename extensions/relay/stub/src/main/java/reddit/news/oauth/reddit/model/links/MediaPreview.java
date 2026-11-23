package reddit.news.oauth.reddit.model.links;

public class MediaPreview {
    public String mediaUrl;
    public String gifUrl;
    public int type;

    public MediaPreview(MediaDetails thumb, MediaDetails largeThumb, String media,
                        String mobileMedia, String gif, String description, String title, int type) {}
}
