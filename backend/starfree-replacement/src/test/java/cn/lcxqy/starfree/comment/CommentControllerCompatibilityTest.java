package cn.lcxqy.starfree.comment;

import cn.lcxqy.starfree.api.ApiResponse;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentControllerCompatibilityTest {
    @Test
    void listUsesLegacyPagedEnvelope() {
        CommentService service = mock(CommentService.class);
        Map<String, Object> item = new HashMap<>();
        item.put("coid", 1);
        when(service.page("{}", 5, 2, "hello", "created", ""))
                .thenReturn(new CommentService.CommentPage(Collections.singletonList(item), 9));
        Map<String, String> params = new HashMap<>();
        params.put("searchParams", "{}");
        params.put("limit", "5");
        params.put("page", "2");
        params.put("order", "created");
        params.put("searchKey", "hello");

        ApiResponse response = new CommentController(service).list(params);

        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEmpty();
        assertThat(response.getCount()).isEqualTo(1);
        assertThat(response.getTotal()).isEqualTo(9);
    }
}