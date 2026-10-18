package cn.lcxqy.starfree.space;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Content submitted for asynchronous AI moderation after its transaction commits. */
public final class AiModerationRequest {
    private final String contentType;
    private final long contentId;
    private final long authorUid;
    private final String text;
    private final String attachment;
    private final List<String> images;
    private final long modified;

    public AiModerationRequest(String contentType, long contentId, long authorUid, String text,
                               String attachment, List<String> images, long modified) {
        this.contentType = contentType == null ? "" : contentType;
        this.contentId = contentId;
        this.authorUid = authorUid;
        this.text = text == null ? "" : text;
        this.attachment = attachment == null ? "" : attachment;
        this.images = images == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(images));
        this.modified = modified;
    }

    public String getContentType() { return contentType; }
    public long getContentId() { return contentId; }
    public long getAuthorUid() { return authorUid; }
    public String getText() { return text; }
    public String getAttachment() { return attachment; }
    public List<String> getImages() { return images; }
    public long getModified() { return modified; }
}
