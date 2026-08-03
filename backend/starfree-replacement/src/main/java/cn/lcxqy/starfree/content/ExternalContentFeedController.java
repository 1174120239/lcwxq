package cn.lcxqy.starfree.content;

import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/** Provider-native public image and blog feed routes used by the unchanged uni-app pages. */
@RestController
@RequestMapping("/SFreeContents")
public class ExternalContentFeedController {
    private final ExternalContentFeedService feeds;

    public ExternalContentFeedController(ExternalContentFeedService feeds) {
        this.feeds = feeds;
    }

    /**
     * GET/POST {@code /SFreeContents/ImagePexels}: Pexels curated or search results.
     *
     * <p>Optional page defaults to one and searchKey selects search mode. Success returns Pexels JSON
     * directly, not the StarFree code/msg envelope, because the frontend reads top-level photos.
     * The API key never enters the response. Production requires shared Redis for three-second
     * client throttling and six-hour provider-response caching.
     */
    @RequestMapping(value = "/ImagePexels", method = {RequestMethod.GET, RequestMethod.POST})
    public Object pexels(@RequestParam Map<String, String> request,
                         HttpServletRequest servletRequest) {
        return feeds.pexels(RequestValues.integer(request, "page", 1),
                RequestValues.text(request, "searchKey"), clientAddress(servletRequest),
                servletRequest.getHeader("User-Agent"));
    }

    /**
     * GET/POST {@code /SFreeContents/foreverblog}: Forever Blog feed page.
     *
     * <p>Optional page defaults to one. The fixed provider response is returned directly and cached
     * for two minutes. No caller-controlled URL, host, scheme, path, or authorization header is used.
     */
    @RequestMapping(value = "/foreverblog", method = {RequestMethod.GET, RequestMethod.POST})
    public Object foreverBlog(@RequestParam Map<String, String> request) {
        return feeds.foreverBlog(RequestValues.integer(request, "page", 1));
    }

    private String clientAddress(HttpServletRequest request) {
        String value = request.getHeader("X-Real-IP");
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader("X-Forwarded-For");
            if (value != null && value.contains(",")) {
                value = value.substring(0, value.indexOf(','));
            }
        }
        if (value == null || value.trim().isEmpty()) {
            value = request.getRemoteAddr();
        }
        return value == null ? "" : value.trim();
    }
}
