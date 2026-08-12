package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.security.LegacyTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Date;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {
    @Test void publicViewerOnlyReceivesEnabledFields(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class); LegacyTokenService tokens=mock(LegacyTokenService.class);
        Map<String,Object> profile=new LinkedHashMap<>(); profile.put("gender","女"); profile.put("birthday",Date.valueOf("2005-02-03")); profile.put("showGender",1); profile.put("showBirthday",0);
        when(jdbc.queryForList(anyString(),eq(9L))).thenReturn(Collections.singletonList(profile));
        Map<String,Object> user=new LinkedHashMap<>(); user.put("uid",9L);
        new UserProfileService(jdbc,tokens).attach(user,false);
        assertThat(user).containsEntry("gender","女").containsEntry("birthday","").containsEntry("showGender",1).containsEntry("showBirthday",0);
    }
    @Test void cannotEditAnotherUsersProfile(){
        JdbcTemplate jdbc=mock(JdbcTemplate.class); LegacyTokenService tokens=mock(LegacyTokenService.class); when(tokens.userId("token")).thenReturn(7L);
        Map<String,Object> body=new LinkedHashMap<>();body.put("uid",9);body.put("gender","男");
        assertThatThrownBy(()->new UserProfileService(jdbc,tokens).save("token",body)).isInstanceOf(IllegalArgumentException.class).hasMessage("无权修改其他用户资料");
    }
}
