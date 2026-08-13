package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpacePollServiceTest {
    @Test void draftAcceptsLimitedMultipleChoiceAndRejectsDuplicates() {
        SpacePollService service = service(mock(JdbcTemplate.class));
        assertThat(service.draft("{\"title\":\"午餐\",\"options\":[\"食堂\",\"校外\"],\"multiple\":1,\"maxChoices\":2}")).isNotNull();
        assertThatThrownBy(() -> service.draft("{\"title\":\"午餐\",\"options\":[\"食堂\",\"食堂\"]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("投票选项不能重复");
    }

    @Test void resultContainsAnonymousCountsButNoVoterIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(8L))).thenReturn(Collections.singletonList(row("id",3L,"title","午餐","description","","multiple",0,"max_choices",1,"total_votes",4L)));
        when(jdbc.queryForList(anyString(), eq(3L), eq(7L))).thenReturn(Collections.singletonList(row("option_id",11L)));
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(Collections.singletonList(row("id",11L,"option_text","食堂","vote_count",3L)));
        Map<String,Object> poll = service(jdbc).forSpace(8L,7L);
        assertThat(poll).containsEntry("totalVotes",4L).containsEntry("voted",true).doesNotContainKeys("users","voters","uid");
        Map<?,?> option = (Map<?,?>)((java.util.List<?>)poll.get("options")).get(0);
        assertThat(option.get("selected")).isEqualTo(true);
        assertThat(option.containsKey("users")).isFalse();
        assertThat(option.containsKey("voters")).isFalse();
        assertThat(option.containsKey("uid")).isFalse();
    }

    @Test void removalKeepsLegacyDynamicDeletionWorkingBeforeMigration() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<Object[]>any(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Long>>any()))
                .thenThrow(new org.springframework.jdbc.BadSqlGrammarException(
                        "query", "SELECT", new java.sql.SQLException("table missing")));

        service(jdbc).removeForSpace(8L);

        verify(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<Object[]>any(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Long>>any());
    }

    private SpacePollService service(JdbcTemplate jdbc){return new SpacePollService(jdbc,new ObjectMapper(),mock(LegacyTokenService.class));}
    private static Map<String,Object> row(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i+1<values.length;i+=2)result.put(String.valueOf(values[i]),values[i+1]);return result;}
}
