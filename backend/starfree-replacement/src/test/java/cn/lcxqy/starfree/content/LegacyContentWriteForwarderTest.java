package cn.lcxqy.starfree.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LegacyContentWriteForwarderTest {
    @Test
    void forwardsExcludedFormDataAndMarksTheResponse() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(once(), requestTo("http://127.0.0.1:8081/SFreeContents/contentsUpdate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Real-IP", "203.0.113.9"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains("token=token");
                    assertThat(body).contains("isDraft=1");
                    assertThat(body).contains("params=");
                })
                .andRespond(withSuccess("{\"code\":1,\"data\":1}", MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", "token");
        form.add("isDraft", "1");
        form.add("params", "{\"cid\":7,\"title\":\"draft\"}");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", "203.0.113.9");

        ResponseEntity<byte[]> response = new LegacyContentWriteForwarder(template,
                "http://127.0.0.1:8081/").forwardUpdate(form, request);

        assertThat(response.getHeaders().getFirst(LegacyContentWriteForwarder.DELEGATE_HEADER))
                .isEqualTo("legacy-contents-update");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"code\":1,\"data\":1}");
        server.verify();
    }
}
