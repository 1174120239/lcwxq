package cn.lcxqy.starfree.space;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SpacePollService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final LegacyTokenService tokens;

    public SpacePollService(JdbcTemplate jdbc, ObjectMapper mapper, LegacyTokenService tokens) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.tokens = tokens;
    }

    public PollDraft draft(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        Map<String, Object> body = RequestValues.jsonObject(mapper, json);
        String title = bounded(body.get("title"), 80, false, "请填写投票标题");
        String description = bounded(body.get("description"), 240, true, "投票简介不能超过240字");
        boolean multiple = number(body.get("multiple")) == 1;
        if (!(body.get("options") instanceof List)) {
            throw new IllegalArgumentException("投票至少需要两个选项");
        }
        List<String> options = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (Object raw : (List<?>) body.get("options")) {
            String option = bounded(raw, 80, false, "投票选项不能为空且不能超过80字");
            if (!unique.add(option)) {
                throw new IllegalArgumentException("投票选项不能重复");
            }
            options.add(option);
        }
        if (options.size() < 2 || options.size() > 6) {
            throw new IllegalArgumentException("投票需要2至6个选项");
        }
        int maxChoices = multiple ? (int) number(body.get("maxChoices")) : 1;
        if (multiple && (maxChoices < 2 || maxChoices > options.size())) {
            throw new IllegalArgumentException("多选数量设置不正确");
        }
        return new PollDraft(title, description, multiple, maxChoices, options);
    }

    @Transactional
    public void create(long spaceId, PollDraft draft) {
        if (draft == null) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        jdbc.update("INSERT INTO starfree_space_polls"
                        + "(space_id,title,description,multiple,max_choices,total_votes,created) "
                        + "VALUES(?,?,?,?,?,0,?)",
                spaceId, draft.title, draft.description, draft.multiple ? 1 : 0,
                draft.maxChoices, now);
        Long pollId = jdbc.queryForObject(
                "SELECT id FROM starfree_space_polls WHERE space_id=? LIMIT 1", Long.class, spaceId);
        if (pollId == null) {
            throw new IllegalStateException("Poll id could not be read");
        }
        for (int index = 0; index < draft.options.size(); index++) {
            jdbc.update("INSERT INTO starfree_space_poll_options"
                            + "(poll_id,option_text,sort_order,vote_count) VALUES(?,?,?,0)",
                    pollId, draft.options.get(index), index);
        }
    }

    public Map<String, Object> forSpace(long spaceId, Long viewerUid) {
        List<Map<String, Object>> polls;
        try {
            polls = jdbc.queryForList(
                    "SELECT id,title,description,multiple,max_choices,total_votes "
                            + "FROM starfree_space_polls WHERE space_id=? LIMIT 1", spaceId);
        } catch (DataAccessException error) {
            return null;
        }
        if (polls.isEmpty()) {
            return null;
        }
        Map<String, Object> row = polls.get(0);
        long pollId = number(value(row, "id"));
        Set<Long> selected = new LinkedHashSet<>();
        if (viewerUid != null && viewerUid > 0) {
            for (Map<String, Object> vote : jdbc.queryForList(
                    "SELECT option_id FROM starfree_space_poll_votes WHERE poll_id=? AND uid=?",
                    pollId, viewerUid)) {
                selected.add(number(value(vote, "option_id")));
            }
        }
        List<Map<String, Object>> options = new ArrayList<>();
        for (Map<String, Object> option : jdbc.queryForList(
                "SELECT id,option_text,vote_count FROM starfree_space_poll_options "
                        + "WHERE poll_id=? ORDER BY sort_order,id", pollId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            long optionId = number(value(option, "id"));
            item.put("id", optionId);
            item.put("text", text(value(option, "option_text")));
            item.put("votes", number(value(option, "vote_count")));
            item.put("selected", selected.contains(optionId));
            options.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", pollId);
        result.put("title", text(value(row, "title")));
        result.put("description", text(value(row, "description")));
        result.put("multiple", number(value(row, "multiple")));
        result.put("maxChoices", number(value(row, "max_choices")));
        result.put("totalVotes", number(value(row, "total_votes")));
        result.put("voted", !selected.isEmpty());
        result.put("options", options);
        return result;
    }

    @Transactional
    public Map<String, Object> vote(Map<String, String> request) {
        Long uid = tokens.userId(RequestValues.text(request, "token"));
        if (uid == null || uid <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        long pollId = RequestValues.integer(request, "pollId", 0);
        List<Map<String, Object>> polls = jdbc.queryForList(
                "SELECT p.id,p.space_id,p.multiple,p.max_choices FROM starfree_space_polls p "
                        + "JOIN starfree_space s ON s.id=p.space_id "
                        + "WHERE p.id=? AND s.status=1 AND s.onlyMe=0 AND s.type<>3 LIMIT 1 FOR UPDATE",
                pollId);
        if (polls.isEmpty()) {
            throw new IllegalArgumentException("投票不存在或暂不可参与");
        }
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space_poll_votes WHERE poll_id=? AND uid=?",
                Integer.class, pollId, uid);
        if (existing != null && existing > 0) {
            throw new IllegalArgumentException("你已经参与过该投票");
        }
        List<Long> choices = choiceIds(RequestValues.text(request, "optionIds"));
        Map<String, Object> poll = polls.get(0);
        int maxChoices = number(value(poll, "multiple")) == 1
                ? (int) number(value(poll, "max_choices")) : 1;
        if (choices.isEmpty() || choices.size() > maxChoices) {
            throw new IllegalArgumentException(maxChoices == 1 ? "请选择一个选项"
                    : "最多选择" + maxChoices + "项");
        }
        Set<Long> valid = new LinkedHashSet<>();
        for (Map<String, Object> option : jdbc.queryForList(
                "SELECT id FROM starfree_space_poll_options WHERE poll_id=?", pollId)) {
            valid.add(number(value(option, "id")));
        }
        if (!valid.containsAll(choices)) {
            throw new IllegalArgumentException("投票选项不正确");
        }
        long now = Instant.now().getEpochSecond();
        for (Long optionId : choices) {
            jdbc.update("INSERT INTO starfree_space_poll_votes(poll_id,option_id,uid,created) "
                    + "VALUES(?,?,?,?)", pollId, optionId, uid, now);
            jdbc.update("UPDATE starfree_space_poll_options SET vote_count=vote_count+1 "
                    + "WHERE id=? AND poll_id=?", optionId, pollId);
        }
        jdbc.update("UPDATE starfree_space_polls SET total_votes=total_votes+1 WHERE id=?", pollId);
        return forSpace(number(value(poll, "space_id")), uid);
    }

    @Transactional
    public void removeForSpace(long spaceId) {
        try {
            List<Long> ids = jdbc.query("SELECT id FROM starfree_space_polls WHERE space_id=?",
                    new Object[]{spaceId}, (rs, rowNum) -> rs.getLong(1));
            for (Long pollId : ids) {
                jdbc.update("DELETE FROM starfree_space_poll_votes WHERE poll_id=?", pollId);
                jdbc.update("DELETE FROM starfree_space_poll_options WHERE poll_id=?", pollId);
            }
            jdbc.update("DELETE FROM starfree_space_polls WHERE space_id=?", spaceId);
        } catch (DataAccessException error) {
            // Migration 011 is additive. Existing dynamic deletion must keep working before it runs.
        }
    }

    private List<Long> choiceIds(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> result = new LinkedHashSet<>();
        for (String value : raw.split(",")) {
            try {
                long id = Long.parseLong(value.trim());
                if (id <= 0 || !result.add(id)) {
                    throw new IllegalArgumentException("投票选项不正确");
                }
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("投票选项不正确");
            }
        }
        return new ArrayList<>(result);
    }

    private String bounded(Object raw, int maximum, boolean empty, String message) {
        String value = text(raw).trim();
        if ((!empty && value.isEmpty()) || value.length() > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private long number(Object value) {
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static final class PollDraft {
        private final String title;
        private final String description;
        private final boolean multiple;
        private final int maxChoices;
        private final List<String> options;

        private PollDraft(String title, String description, boolean multiple,
                          int maxChoices, List<String> options) {
            this.title = title;
            this.description = description;
            this.multiple = multiple;
            this.maxChoices = maxChoices;
            this.options = options;
        }
    }
}
