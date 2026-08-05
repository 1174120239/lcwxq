package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.StaffAccess;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CampusIdentityServiceTest {
    @Test
    void registrationOptionsOnlyQueryEnabledValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffAccess access = mock(StaffAccess.class);
        when(jdbc.queryForList(contains("enabled=1"), eq("campus")))
                .thenReturn(Collections.singletonList(row("id", 2, "type", "campus",
                        "name", "东校区", "sort_order", 20, "enabled", 1)));
        when(jdbc.queryForList(contains("enabled=1"), eq("grade")))
                .thenReturn(Collections.singletonList(row("id", 3, "type", "grade",
                        "name", "2024级", "sort_order", 20, "enabled", 1)));

        Map<String, Object> result = new CampusIdentityService(jdbc, access).registrationOptions();

        assertThat((java.util.List<?>) result.get("campuses")).hasSize(1);
        assertThat((java.util.List<?>) result.get("grades")).hasSize(1);
    }

    @Test
    void managementReadRequiresStaff() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffAccess access = mock(StaffAccess.class);
        when(jdbc.queryForList(contains("starfree_identity_options"), eq("campus")))
                .thenReturn(Collections.emptyList());
        when(jdbc.queryForList(contains("starfree_identity_options"), eq("grade")))
                .thenReturn(Collections.emptyList());

        new CampusIdentityService(jdbc, access).manageOptions("staff-token");

        verify(access).requireStaff("staff-token");
    }

    @Test
    void gradeNameMustUseAdmissionYearFormat() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        StaffAccess access = mock(StaffAccess.class);
        Map<String, Object> body = row(
                "type", "grade", "name", "大一", "sortOrder", 10, "enabled", 1);

        assertThatThrownBy(() -> new CampusIdentityService(jdbc, access).save("staff-token", body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("年级名称请使用“2024级”格式");

        verify(access).requireStaff("staff-token");
    }

    private static Map<String, Object> row(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }
}
