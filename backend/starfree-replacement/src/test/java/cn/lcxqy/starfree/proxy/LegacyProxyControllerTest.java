package cn.lcxqy.starfree.proxy;

import cn.lcxqy.starfree.economy.EconomyLockExecutor;
import cn.lcxqy.starfree.security.BearerTokenFilter;
import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class LegacyProxyControllerTest {
    @Test
    void allChatRequiresStaffBeforeForwardingToLegacyApi() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        doThrow(new IllegalArgumentException("你没有操作权限"))
                .when(staffAccess).requireStaff("user-token");
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/SFreeChat/allChat");
        request.setParameter("token", "user-token");

        assertThatThrownBy(() -> controller.proxy(request, new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你没有操作权限");
        verify(staffAccess).requireStaff("user-token");
        verifyNoInteractions(restTemplate, economyLock);
    }

    @Test
    void legacyTokenManagementRequiresAdministratorBeforeForwarding() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        doThrow(new IllegalArgumentException("你没有操作权限"))
                .when(staffAccess).requireAdministrator("editor-token");
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay/madetoken");
        request.setParameter("token", "editor-token");

        assertThatThrownBy(() -> controller.proxy(request, new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("你没有操作权限");
        verify(staffAccess).requireAdministrator("editor-token");
        verifyNoInteractions(restTemplate, economyLock);
    }

    @Test
    void everyOtherLegacyTokenRequestRequiresANewValidSession() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        doThrow(new IllegalArgumentException("用户未登录或Token验证失败"))
                .when(staffAccess).requireUser("forged-legacy-token");
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload/full");
        request.setParameter("token", "forged-legacy-token");

        assertThatThrownBy(() -> controller.proxy(request, new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户未登录或Token验证失败");
        verify(staffAccess).requireUser("forged-legacy-token");
        verifyNoInteractions(restTemplate, economyLock);
    }

    @Test
    void protectedLegacyRouteRejectsAMissingTokenBeforeForwarding() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        doThrow(new IllegalArgumentException("用户未登录或Token验证失败"))
                .when(staffAccess).requireUser(null);
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload/full");

        assertThatThrownBy(() -> controller.proxy(request, new MockHttpServletResponse()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户未登录或Token验证失败");
        verify(staffAccess).requireUser(null);
        verifyNoInteractions(restTemplate, economyLock);
    }

    @Test
    void officialPaymentCreationAndCallbacksUseTheEconomyLock() {
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/notify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/wxPayNotify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/EPayNotify")).isTrue();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/tokenPayStar")).isTrue();
    }

    @Test
    void unrelatedLegacyRoutesRemainOrdinaryPassThroughRequests() {
        assertThat(LegacyProxyController.requiresEconomyLock("/SFreeChat/sendMsg")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock("/pay/payorderList")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock("/SFreeAds/adsList")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsGift")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsGiftNotify")).isFalse();
        assertThat(LegacyProxyController.requiresEconomyLock(
                "/SFreeUserlog/adsServerNotify")).isFalse();
    }

    @Test
    void bearerOnlySessionIsPassedToTheLoopbackLegacyRequest() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok(new byte[0]));
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/upload/full");
        request.setParameter("token", "sf2_header");
        request.setAttribute(BearerTokenFilter.BEARER_ONLY_ATTRIBUTE, Boolean.TRUE);

        controller.proxy(request, new MockHttpServletResponse());

        verify(staffAccess).requireUser("sf2_header");
        verify(restTemplate).exchange(
                eq(URI.create("http://127.0.0.1:8081/upload/full?token=sf2_header")),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(byte[].class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void parsedMultipartUploadIsRebuiltBeforeForwarding() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        EconomyLockExecutor economyLock = mock(EconomyLockExecutor.class);
        StaffAccess staffAccess = mock(StaffAccess.class);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(byte[].class)))
                .thenReturn(ResponseEntity.ok("{\"code\":1}".getBytes(StandardCharsets.UTF_8)));
        LegacyProxyController controller = new LegacyProxyController(
                restTemplate, "http://127.0.0.1:8081", economyLock, staffAccess);
        MockMultipartHttpServletRequest request = new MockMultipartHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI("/upload/full");
        request.setParameter("token", "sf2_token");
        request.addFile(new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "image-data".getBytes(StandardCharsets.UTF_8)));

        controller.proxy(request, new MockHttpServletResponse());

        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(staffAccess).requireUser("sf2_token");
        verify(restTemplate).exchange(eq(URI.create("http://127.0.0.1:8081/upload/full")),
                eq(HttpMethod.POST), entity.capture(), eq(byte[].class));
        assertThat(entity.getValue().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form =
                (MultiValueMap<String, Object>) entity.getValue().getBody();
        assertThat(form.getFirst("token")).isEqualTo("sf2_token");
        HttpEntity<ByteArrayResource> file =
                (HttpEntity<ByteArrayResource>) form.getFirst("file");
        assertThat(file.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(file.getBody().getFilename()).isEqualTo("avatar.jpg");
        assertThat(file.getBody().getByteArray())
                .isEqualTo("image-data".getBytes(StandardCharsets.UTF_8));
    }
}
