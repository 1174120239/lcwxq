package cn.lcxqy.starfree.gallery;

import cn.lcxqy.starfree.security.StaffAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GalleryServiceTest {
    @Test
    void unauthenticatedSubmissionIsRejectedBeforePersistence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffAccess access = mock(StaffAccess.class);
        when(access.requireUser("bad-token")).thenThrow(new IllegalArgumentException("未登录"));
        GalleryService service = new GalleryService(jdbc, access, new ObjectMapper());

        assertThatThrownBy(() -> service.gallerySubmit("bad-token", Collections.emptyMap()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未登录");
    }
}
