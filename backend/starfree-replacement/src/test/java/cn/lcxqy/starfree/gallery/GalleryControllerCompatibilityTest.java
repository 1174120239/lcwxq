package cn.lcxqy.starfree.gallery;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GalleryControllerCompatibilityTest {
    @Test
    void standaloneGalleryEndpointsKeepTheirOwnBasePath() throws Exception {
        RequestMapping base = GalleryController.class.getAnnotation(RequestMapping.class);
        assertThat(base.value()).containsExactly("/SFreeGallery");
        assertRoute("galleryList", "/galleryList");
        assertRoute("galleryTags", "/galleryTags");
        assertRoute("galleryInfo", "/galleryInfo");
        assertRoute("gallerySubmit", "/gallerySubmit");
        assertRoute("galleryManage", "/galleryManage");
        assertRoute("galleryReview", "/galleryReview");
        assertRoute("galleryDelete", "/galleryDelete");
    }

    private void assertRoute(String name, String route) throws Exception {
        RequestMapping mapping = "galleryTags".equals(name)
                ? GalleryController.class.getMethod(name).getAnnotation(RequestMapping.class)
                : GalleryController.class.getMethod(name, Map.class).getAnnotation(RequestMapping.class);
        assertThat(mapping.value()).containsExactly(route);
    }
}
