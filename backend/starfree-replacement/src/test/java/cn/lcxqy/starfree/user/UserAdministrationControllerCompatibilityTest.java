package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAdministrationControllerCompatibilityTest {
    @Test
    void allUserAdministrationRoutesKeepLegacyGetAndPostCompatibility() throws Exception {
        assertGetAndPost("userList", Map.class);
        assertGetAndPost("phoneLogin", Map.class, HttpServletRequest.class);
        assertGetAndPost("manageUserEdit", Map.class);
        assertGetAndPost("userDelete", Map.class);
        assertGetAndPost("setScan", Map.class);
        assertGetAndPost("madeInvitation", Map.class);
        assertGetAndPost("invitationList", Map.class);
        assertGetAndPost("invitationExcel", Map.class, HttpServletResponse.class);
        assertGetAndPost("sendUser", Map.class);
        assertGetAndPost("banUser", Map.class);
        assertGetAndPost("unblockUser", Map.class);
        assertGetAndPost("violationList", Map.class);
        assertGetAndPost("userClean", Map.class);
        assertGetAndPost("restrict", Map.class);
        assertGetAndPost("giftVip", Map.class);
    }

    @Test
    void listRoutesKeepCountAndTotalWhileWritesKeepAffectedRowData() {
        UserAdministrationService service = mock(UserAdministrationService.class);
        UserAdministrationController controller =
                new UserAdministrationController(service, new ObjectMapper());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("uid", 7);
        when(service.users(Collections.<String, String>emptyMap())).thenReturn(
                new UserAdministrationService.Page(Collections.singletonList(item), 19));
        when(service.manageEdit("token", Collections.<String, Object>singletonMap("uid", 7)))
                .thenReturn(1);

        ApiResponse list = controller.userList(Collections.<String, String>emptyMap());
        ApiResponse edit = controller.manageUserEdit(stringRow(
                "token", "token", "params", "{\"uid\":7}"));

        assertThat(list.getCode()).isEqualTo(1);
        assertThat(list.getData()).isEqualTo(Collections.singletonList(item));
        assertThat(list.getCount()).isEqualTo(1);
        assertThat(list.getTotal()).isEqualTo(19);
        assertThat(edit.getCode()).isEqualTo(1);
        assertThat(edit.getData()).isEqualTo(1);
    }

    private void assertGetAndPost(String method, Class<?>... parameterTypes) throws Exception {
        RequestMapping mapping = UserAdministrationController.class
                .getMethod(method, parameterTypes).getAnnotation(RequestMapping.class);
        assertThat(mapping.method()).containsExactlyInAnyOrder(
                RequestMethod.GET, RequestMethod.POST);
    }

    private Map<String, String> stringRow(String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            row.put(values[index], values[index + 1]);
        }
        return row;
    }
}
