package cn.lcxqy.starfree.qa;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.push.UniPushService;
import cn.lcxqy.starfree.security.LegacyTokenService;
import cn.lcxqy.starfree.security.StaffAccess;
import cn.lcxqy.starfree.space.AiModerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QaService {
    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final LegacyTokenService tokens;
    private final UniPushService push;
    private AiModerationService aiModeration;

    public QaService(JdbcTemplate jdbc, StaffAccess access, LegacyTokenService tokens,
                     UniPushService push) {
        this.jdbc = jdbc;
        this.access = access;
        this.tokens = tokens;
        this.push = push;
    }

    @Autowired
    void setAiModeration(AiModerationService aiModeration) {
        this.aiModeration = aiModeration;
    }

    public Page questionList(Map<String, String> request) {
        int limit = bounded(RequestValues.integer(request, "limit", 6), 30);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        String keyword = RequestValues.text(request, "keyword");
        boolean recommendedOnly = RequestValues.integer(request, "recommended", 0) == 1;
        List<Object> args = new ArrayList<Object>();
        String where = " WHERE q.status=1";
        if (recommendedOnly) {
            where += " AND q.recommended=1";
        }
        if (!keyword.isEmpty()) {
            where += " AND (q.title LIKE ? OR q.description LIKE ? OR q.topic LIKE ?)";
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_questions q" + where,
                Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<Object>(args);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(questionSelect() + where
                        + " ORDER BY q.recommended DESC,q.sort_order DESC,q.modified DESC,q.id DESC LIMIT ?,?",
                rowArgs.toArray());
        return new Page(normalizeQuestions(rows), total == null ? 0 : total);
    }

    public Map<String, Object> questionInfo(Map<String, String> request) {
        long id = positiveId(request.get("id"), "问题不存在");
        boolean staff = isStaff(RequestValues.text(request, "token"));
        List<Map<String, Object>> rows = jdbc.queryForList(questionSelect()
                + " WHERE q.id=?" + (staff ? "" : " AND q.status=1") + " LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("问题不存在或已停用");
        }
        return normalizeQuestion(rows.get(0));
    }

    public Map<String, Object> questionAdd(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token);
        String title = validateText(body.get("title"), 4, 160,
                "问题标题至少需要4个字", "问题标题不能超过160个字");
        String description = optionalText(body.get("description"), 5000, "问题说明不能超过5000个字");
        String topic = optionalText(body.get("topic"), 80, "话题不能超过80个字");
        long now = Instant.now().getEpochSecond();
        rejectBanned(actor, now);
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_questions "
                        + "WHERE created_by=? AND title=? AND description=? AND created>=?",
                Integer.class, actor.getUid(), title, description, now - 20);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("问题已提交，请勿重复发送");
        }
        boolean auditEnabled = aiModeration != null && aiModeration.globalAuditEnabled();
        int initialStatus = auditEnabled ? 0 : 1;
        long id = insertKey("INSERT INTO starfree_qa_questions"
                        + "(title,description,topic,cover_url,status,recommended,sort_order,created_by,created,modified) "
                        + "VALUES(?,?,?,'',?,0,0,?,?,?)",
                title, description, topic, initialStatus, actor.getUid(), now, now);
        int status = initialStatus;
        AiModerationService.Decision decision = null;
        if (auditEnabled && aiModeration.enabledForQuestion()) {
            decision = aiModeration.reviewQuestion(id, actor.getUid(), title, description, "");
            if (decision.isSafe()) {
                int changed = jdbc.update(
                        "UPDATE starfree_qa_questions SET status=1,modified=? WHERE id=? AND status=0",
                        Instant.now().getEpochSecond(), id);
                status = changed == 1 ? 1 : 0;
                aiModeration.markContentStatus(decision.getReviewId(), status);
            } else {
                aiModeration.markContentStatus(decision.getReviewId(), 0);
            }
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", id);
        result.put("status", status);
        result.put("createdBy", actor.getUid());
        if (decision != null) {
            result.put("aiDecision", decision.getStatus());
            result.put("aiCategory", decision.getCategory());
            result.put("aiReason", decision.getReason());
        }
        return result;
    }

    public Page answerList(Map<String, String> request) {
        long questionId = positiveId(request.get("questionId"), "问题不存在");
        requirePublicQuestion(questionId);
        int limit = bounded(RequestValues.integer(request, "limit", 10), 30);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        String sort = RequestValues.text(request, "sort");
        Long viewerUid = tokens.userId(RequestValues.text(request, "token"));
        long viewer = viewerUid == null ? 0 : viewerUid;
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_answers "
                        + "WHERE question_id=? AND status=1", Integer.class, questionId);
        String order = "latest".equals(sort)
                ? "a.created DESC,a.id DESC"
                : "a.likes DESC,a.created DESC,a.id DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id,a.question_id,a.uid,a.text,a.likes,a.status,a.created,a.modified,"
                        + "CASE WHEN l.uid IS NULL THEN 0 ELSE 1 END AS is_liked,"
                        + "(SELECT COUNT(*) FROM starfree_qa_comments c "
                        + "WHERE c.answer_id=a.id AND c.status=1) AS comment_count "
                        + "FROM starfree_qa_answers a LEFT JOIN starfree_qa_answer_likes l "
                        + "ON l.answer_id=a.id AND l.uid=? "
                        + "WHERE a.question_id=? AND a.status=1 ORDER BY " + order + " LIMIT ?,?",
                viewer, questionId, (page - 1) * limit, limit);
        List<Map<String, Object>> data = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            data.add(normalizeAnswer(row));
        }
        return new Page(data, total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> answerAdd(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token);
        long questionId = positiveId(body.get("questionId"), "问题不存在");
        String text = validateText(body.get("text"), 4, 5000, "回答至少需要4个字", "回答不能超过5000个字");
        Map<String, Object> question = requirePublicQuestion(questionId);
        long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_answers "
                        + "WHERE question_id=? AND uid=? AND text=? AND status=1 AND created>=?",
                Integer.class, questionId, actor.getUid(), text, now - 20);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("回答已提交，请勿重复发送");
        }
        long id = insertKey("INSERT INTO starfree_qa_answers"
                        + "(question_id,uid,text,likes,status,created,modified) VALUES(?,?,?,0,1,?,?)",
                questionId, actor.getUid(), text, now, now);
        writeNotice("qaAnswer", actor.getUid(), number(question.get("createdBy")), questionId, id,
                "回答了问题：" + preview(text), "问答收到新回答");
        return answer(id, actor.getUid());
    }

    public Map<String, Object> answerEdit(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token);
        long id = positiveId(body.get("id"), "回答不存在");
        String text = validateText(body.get("text"), 4, 5000, "回答至少需要4个字", "回答不能超过5000个字");
        Map<String, Object> answer = requireAnswer(id);
        requireOwnerOrStaff(actor, number(answer.get("uid")));
        jdbc.update("UPDATE starfree_qa_answers SET text=?,modified=? WHERE id=? AND status=1",
                text, Instant.now().getEpochSecond(), id);
        return answer(id, actor.getUid());
    }

    @Transactional
    public int answerDelete(String token, long id) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> answer = requireAnswer(id);
        requireOwnerOrStaff(actor, number(answer.get("uid")));
        int changed = jdbc.update("UPDATE starfree_qa_answers SET status=0,modified=? WHERE id=? AND status=1",
                Instant.now().getEpochSecond(), id);
        if (changed == 1) {
            jdbc.update("DELETE FROM starfree_qa_answer_likes WHERE answer_id=?", id);
        }
        return changed;
    }

    @Transactional
    public Map<String, Object> answerLike(String token, long answerId) {
        StaffAccess.Actor actor = access.requireUser(token);
        requireAnswer(answerId);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_answer_likes "
                + "WHERE answer_id=? AND uid=?", Integer.class, answerId, actor.getUid());
        int liked;
        if (exists != null && exists > 0) {
            jdbc.update("DELETE FROM starfree_qa_answer_likes WHERE answer_id=? AND uid=?",
                    answerId, actor.getUid());
            liked = 0;
        } else {
            jdbc.update("INSERT INTO starfree_qa_answer_likes(answer_id,uid,created) VALUES(?,?,?)",
                    answerId, actor.getUid(), Instant.now().getEpochSecond());
            liked = 1;
        }
        jdbc.update("UPDATE starfree_qa_answers SET likes=(SELECT COUNT(*) FROM "
                + "starfree_qa_answer_likes WHERE answer_id=?) WHERE id=?", answerId, answerId);
        Integer likes = jdbc.queryForObject("SELECT likes FROM starfree_qa_answers WHERE id=?",
                Integer.class, answerId);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", answerId);
        result.put("isLiked", liked);
        result.put("likes", likes == null ? 0 : likes);
        return result;
    }

    public Page commentList(Map<String, String> request) {
        long answerId = positiveId(request.get("answerId"), "回答不存在");
        requireAnswer(answerId);
        int limit = bounded(RequestValues.integer(request, "limit", 10), 30);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_comments "
                        + "WHERE answer_id=? AND parent_id=0 AND status=1", Integer.class, answerId);
        List<Map<String, Object>> roots = jdbc.queryForList(
                "SELECT id,answer_id,uid,parent_id,root_id,text,status,created,modified "
                        + "FROM starfree_qa_comments WHERE answer_id=? AND parent_id=0 AND status=1 "
                        + "ORDER BY created DESC,id DESC LIMIT ?,?",
                answerId, (page - 1) * limit, limit);
        if (roots.isEmpty()) {
            return new Page(Collections.<Map<String, Object>>emptyList(), total == null ? 0 : total);
        }
        List<Long> rootIds = new ArrayList<Long>();
        for (Map<String, Object> root : roots) {
            rootIds.add(number(value(root, "id")));
        }
        String placeholders = placeholders(rootIds.size());
        List<Object> childArgs = new ArrayList<Object>();
        childArgs.add(answerId);
        childArgs.addAll(rootIds);
        List<Map<String, Object>> children = jdbc.queryForList(
                "SELECT id,answer_id,uid,parent_id,root_id,text,status,created,modified "
                        + "FROM starfree_qa_comments WHERE answer_id=? AND status=1 AND root_id IN ("
                        + placeholders + ") ORDER BY created ASC,id ASC", childArgs.toArray());
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<Long, Map<String, Object>>();
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> root : roots) {
            Map<String, Object> item = normalizeComment(root);
            nodes.put(number(item.get("id")), item);
            result.add(item);
        }
        for (Map<String, Object> child : children) {
            Map<String, Object> item = normalizeComment(child);
            nodes.put(number(item.get("id")), item);
        }
        for (Map<String, Object> item : nodes.values()) {
            long parentId = number(item.get("parentId"));
            if (parentId == 0) {
                continue;
            }
            Map<String, Object> parent = nodes.get(parentId);
            if (parent == null) {
                parent = nodes.get(number(item.get("rootId")));
            }
            if (parent != null) {
                item.put("replyToUser", parent.get("userJson"));
                commentChildren(parent).add(item);
            }
        }
        return new Page(result, total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> commentAdd(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token);
        long answerId = positiveId(body.get("answerId"), "回答不存在");
        long parentId = number(body.get("parentId"));
        String text = validateText(body.get("text"), 1, 1000, "评论内容不能为空", "评论不能超过1000个字");
        Map<String, Object> answer = requireAnswer(answerId);
        long rootId = 0;
        long noticeUid = number(answer.get("uid"));
        if (parentId > 0) {
            Map<String, Object> parent = requireComment(parentId);
            if (number(parent.get("answerId")) != answerId) {
                throw new IllegalArgumentException("回复目标不属于当前回答");
            }
            rootId = number(parent.get("rootId"));
            if (rootId == 0) {
                rootId = parentId;
            }
            noticeUid = number(parent.get("uid"));
        }
        long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_comments "
                        + "WHERE answer_id=? AND uid=? AND parent_id=? AND text=? AND status=1 AND created>=?",
                Integer.class, answerId, actor.getUid(), parentId, text, now - 20);
        if (duplicate != null && duplicate > 0) {
            throw new IllegalArgumentException("评论已提交，请勿重复发送");
        }
        long id = insertKey("INSERT INTO starfree_qa_comments"
                        + "(answer_id,uid,parent_id,root_id,text,status,created,modified) "
                        + "VALUES(?,?,?,?,?,1,?,?)",
                answerId, actor.getUid(), parentId, rootId, text, now, now);
        long questionId = number(answer.get("questionId"));
        // Keep the answer relationship in the response while using the notification cid
        // for the exact comment that was created. Older rows are normalized by the inbox
        // service so existing notifications remain usable.
        writeNotice("qaComment", actor.getUid(), noticeUid, questionId, id,
                parentId > 0 ? "回复了你的评论：" + preview(text) : "评论了你的回答：" + preview(text),
                "问答收到新评论");
        return comment(id);
    }

    public int commentDelete(String token, long id) {
        StaffAccess.Actor actor = access.requireUser(token);
        Map<String, Object> comment = requireComment(id);
        requireOwnerOrStaff(actor, number(comment.get("uid")));
        return jdbc.update("UPDATE starfree_qa_comments SET status=0,modified=? "
                + "WHERE (id=? OR root_id=?) AND status=1", Instant.now().getEpochSecond(), id, id);
    }

    public Page questionManage(Map<String, String> request) {
        access.requireStaff(RequestValues.text(request, "token"));
        int limit = bounded(RequestValues.integer(request, "limit", 20), 60);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        String keyword = RequestValues.text(request, "keyword");
        String statusText = RequestValues.text(request, "status");
        List<Object> args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (!keyword.isEmpty()) {
            where += " AND (q.title LIKE ? OR q.topic LIKE ?)";
            String like = "%" + keyword + "%";
            args.add(like);
            args.add(like);
        }
        if ("0".equals(statusText) || "1".equals(statusText)) {
            where += " AND q.status=?";
            args.add(Integer.parseInt(statusText));
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_qa_questions q" + where,
                Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<Object>(args);
        rowArgs.add((page - 1) * limit);
        rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(questionManageSelect() + where
                        + " ORDER BY q.sort_order DESC,q.modified DESC,q.id DESC LIMIT ?,?",
                rowArgs.toArray());
        return new Page(normalizeQuestions(rows), total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> questionSave(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireStaff(token);
        long id = number(body.get("id"));
        String title = validateText(body.get("title"), 4, 160, "问题标题至少需要4个字", "问题标题不能超过160个字");
        String description = optionalText(body.get("description"), 5000, "问题说明不能超过5000个字");
        String topic = optionalText(body.get("topic"), 80, "话题不能超过80个字");
        String coverUrl = optionalText(body.get("coverUrl"), 500, "封面地址不能超过500个字");
        int recommended = flag(body.get("recommended"));
        int status = flagDefault(body.get("status"), 1);
        int sortOrder = integer(body.get("sortOrder"), 0);
        long now = Instant.now().getEpochSecond();
        int oldStatus = -1;
        if (id > 0) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT status FROM starfree_qa_questions WHERE id=? LIMIT 1", id);
            if (existing.isEmpty()) {
                throw new IllegalArgumentException("问题不存在");
            }
            oldStatus = (int) number(value(existing.get(0), "status"));
            int changed = jdbc.update("UPDATE starfree_qa_questions SET title=?,description=?,topic=?,"
                            + "cover_url=?,recommended=?,status=?,sort_order=?,modified=? WHERE id=?",
                    title, description, topic, coverUrl, recommended, status, sortOrder, now, id);
            if (changed != 1) {
                throw new IllegalArgumentException("问题不存在");
            }
            if (aiModeration != null && oldStatus != status) {
                aiModeration.recordHumanAction(AiModerationService.TYPE_QUESTION, id,
                        oldStatus, status, actor.getUid(), "后台编辑提问并调整状态");
            }
        } else {
            id = insertKey("INSERT INTO starfree_qa_questions"
                            + "(title,description,topic,cover_url,status,recommended,sort_order,created_by,created,modified) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?)",
                    title, description, topic, coverUrl, status, recommended, sortOrder,
                    actor.getUid(), now, now);
        }
        return managedQuestion(id);
    }

    @Transactional
    public Map<String, Object> questionStatus(String token, long id, int status) {
        StaffAccess.Actor actor = access.requireStaff(token);
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("问题状态不正确");
        }
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT status FROM starfree_qa_questions WHERE id=? LIMIT 1", id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("问题不存在");
        }
        int oldStatus = (int) number(value(existing.get(0), "status"));
        if (oldStatus == status) {
            throw new IllegalArgumentException("问题已经是当前状态");
        }
        int changed = jdbc.update("UPDATE starfree_qa_questions SET status=?,modified=? WHERE id=?",
                status, Instant.now().getEpochSecond(), id);
        if (changed != 1) {
            throw new IllegalArgumentException("问题不存在");
        }
        if (aiModeration != null) {
            aiModeration.recordHumanAction(AiModerationService.TYPE_QUESTION, id,
                    oldStatus, status, actor.getUid(), "APP管理端改判提问状态");
        }
        return managedQuestion(id);
    }

    private String questionSelect() {
        return "SELECT q.id,q.title,q.description,q.topic,q.cover_url,q.status,q.recommended,"
                + "q.sort_order,q.created_by,q.created,q.modified,"
                + "(SELECT COUNT(*) FROM starfree_qa_answers a WHERE a.question_id=q.id AND a.status=1) AS answer_count "
                + "FROM starfree_qa_questions q";
    }

    private String questionManageSelect() {
        return "SELECT q.id,q.title,q.description,q.topic,q.cover_url,q.status,q.recommended,"
                + "q.sort_order,q.created_by,q.created,q.modified,"
                + "(SELECT COUNT(*) FROM starfree_qa_answers a WHERE a.question_id=q.id AND a.status=1) AS answer_count,"
                + "(SELECT r.ai_decision FROM starfree_ai_moderation_reviews r "
                + "WHERE r.content_type='question' AND r.content_id=q.id ORDER BY r.created DESC,r.id DESC LIMIT 1) AS ai_decision,"
                + "(SELECT r.risk_category FROM starfree_ai_moderation_reviews r "
                + "WHERE r.content_type='question' AND r.content_id=q.id ORDER BY r.created DESC,r.id DESC LIMIT 1) AS ai_category,"
                + "(SELECT r.reason FROM starfree_ai_moderation_reviews r "
                + "WHERE r.content_type='question' AND r.content_id=q.id ORDER BY r.created DESC,r.id DESC LIMIT 1) AS ai_reason,"
                + "(SELECT r.human_decision FROM starfree_ai_moderation_reviews r "
                + "WHERE r.content_type='question' AND r.content_id=q.id ORDER BY r.created DESC,r.id DESC LIMIT 1) AS human_decision,"
                + "(SELECT r.review_note FROM starfree_ai_moderation_reviews r "
                + "WHERE r.content_type='question' AND r.content_id=q.id ORDER BY r.created DESC,r.id DESC LIMIT 1) AS review_note "
                + "FROM starfree_qa_questions q";
    }

    private List<Map<String, Object>> normalizeQuestions(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            result.add(normalizeQuestion(row));
        }
        return result;
    }

    private Map<String, Object> normalizeQuestion(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("title", text(value(row, "title")));
        result.put("description", text(value(row, "description")));
        result.put("topic", text(value(row, "topic")));
        result.put("coverUrl", text(value(row, "cover_url")));
        result.put("status", number(value(row, "status")));
        result.put("recommended", number(value(row, "recommended")));
        result.put("sortOrder", number(value(row, "sort_order")));
        result.put("createdBy", number(value(row, "created_by")));
        result.put("created", number(value(row, "created")));
        result.put("modified", number(value(row, "modified")));
        result.put("answerCount", number(value(row, "answer_count")));
        result.put("aiDecision", text(value(row, "ai_decision")));
        result.put("aiCategory", text(value(row, "ai_category")));
        result.put("aiReason", text(value(row, "ai_reason")));
        result.put("humanDecision", text(value(row, "human_decision")));
        result.put("reviewNote", text(value(row, "review_note")));
        return result;
    }

    private Map<String, Object> normalizeAnswer(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("questionId", number(value(row, "question_id")));
        result.put("uid", number(value(row, "uid")));
        result.put("text", text(value(row, "text")));
        result.put("likes", number(value(row, "likes")));
        result.put("status", number(value(row, "status")));
        result.put("created", number(value(row, "created")));
        result.put("modified", number(value(row, "modified")));
        result.put("isLiked", number(value(row, "is_liked")));
        result.put("commentCount", number(value(row, "comment_count")));
        result.put("userJson", publicUser(number(value(row, "uid"))));
        return result;
    }

    private Map<String, Object> normalizeComment(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("answerId", number(value(row, "answer_id")));
        result.put("uid", number(value(row, "uid")));
        result.put("parentId", number(value(row, "parent_id")));
        result.put("rootId", number(value(row, "root_id")));
        result.put("text", text(value(row, "text")));
        result.put("created", number(value(row, "created")));
        result.put("modified", number(value(row, "modified")));
        result.put("userJson", publicUser(number(value(row, "uid"))));
        result.put("children", new ArrayList<Map<String, Object>>());
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> commentChildren(Map<String, Object> comment) {
        return (List<Map<String, Object>>) comment.get("children");
    }

    private Map<String, Object> answer(long id, long viewerUid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id,a.question_id,a.uid,a.text,a.likes,a.status,a.created,a.modified,"
                        + "CASE WHEN l.uid IS NULL THEN 0 ELSE 1 END AS is_liked,"
                        + "(SELECT COUNT(*) FROM starfree_qa_comments c WHERE c.answer_id=a.id AND c.status=1) AS comment_count "
                        + "FROM starfree_qa_answers a LEFT JOIN starfree_qa_answer_likes l "
                        + "ON l.answer_id=a.id AND l.uid=? WHERE a.id=? AND a.status=1 LIMIT 1",
                viewerUid, id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("回答不存在");
        }
        return normalizeAnswer(rows.get(0));
    }

    private Map<String, Object> comment(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,answer_id,uid,parent_id,root_id,text,status,created,modified "
                        + "FROM starfree_qa_comments WHERE id=? AND status=1 LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评论不存在");
        }
        return normalizeComment(rows.get(0));
    }

    private Map<String, Object> managedQuestion(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(questionManageSelect()
                + " WHERE q.id=? LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("问题不存在");
        }
        return normalizeQuestion(rows.get(0));
    }

    private Map<String, Object> requirePublicQuestion(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(questionSelect()
                + " WHERE q.id=? AND q.status=1 LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("问题不存在或已停用");
        }
        return normalizeQuestion(rows.get(0));
    }

    private Map<String, Object> requireAnswer(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,question_id,uid,text,likes,status,created,modified "
                        + "FROM starfree_qa_answers WHERE id=? AND status=1 LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("回答不存在或已删除");
        }
        Map<String, Object> row = rows.get(0);
        requirePublicQuestion(number(value(row, "question_id")));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("questionId", number(value(row, "question_id")));
        result.put("uid", number(value(row, "uid")));
        result.put("text", text(value(row, "text")));
        return result;
    }

    private Map<String, Object> requireComment(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,answer_id,uid,parent_id,root_id,text,status,created,modified "
                        + "FROM starfree_qa_comments WHERE id=? AND status=1 LIMIT 1", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("评论不存在或已删除");
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", number(value(row, "id")));
        result.put("answerId", number(value(row, "answer_id")));
        result.put("uid", number(value(row, "uid")));
        result.put("parentId", number(value(row, "parent_id")));
        result.put("rootId", number(value(row, "root_id")));
        return result;
    }

    private Map<String, Object> publicUser(long uid) {
        Map<String, Object> source = tokens.publicUserById(uid);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("uid", uid);
        if (source == null) {
            result.put("name", "已注销用户");
            result.put("avatar", "");
            result.put("vip", 0);
            result.put("campus", "");
            result.put("grade", "");
            return result;
        }
        String screenName = text(source.get("screenName"));
        String accountName = text(source.get("name"));
        result.put("name", screenName.isEmpty() ? accountName : screenName);
        result.put("avatar", publicAvatar(source));
        result.put("vip", number(source.get("vip")));
        result.put("campus", text(source.get("campus")));
        result.put("grade", text(source.get("grade")));
        return result;
    }

    private String publicAvatar(Map<String, Object> user) {
        String avatar = text(user.get("avatar"));
        if (!avatar.isEmpty()) {
            return avatar;
        }
        String mail = text(user.get("mail")).toLowerCase();
        if (mail.endsWith("@qq.com") && mail.length() > 7) {
            return "https://q1.qlogo.cn/g?b=qq&nk="
                    + mail.substring(0, mail.length() - 7) + "&s=640";
        }
        return "";
    }

    private void writeNotice(String type, long fromUid, long toUid, long questionId, long referenceId,
                             String message, String title) {
        if (toUid <= 0 || fromUid == toUid) {
            return;
        }
        try {
                    jdbc.update("INSERT INTO starfree_inbox(type,uid,text,touid,isread,value,created,cid) "
                            + "VALUES(?,?,?,?,0,?,?,?)",
                    type, fromUid, message, toUid, questionId, Instant.now().getEpochSecond(), referenceId);
            if (push != null) {
                push.sendComment(toUid, title, message, "qa:" + questionId);
            }
        } catch (DataAccessException ignored) {
            // Publishing succeeds even when an optional notification dependency is unavailable.
        }
    }

    private long insertKey(final String sql, final Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null || key.longValue() <= 0) {
            throw new IllegalStateException("未能创建问答记录");
        }
        return key.longValue();
    }

    private void requireOwnerOrStaff(StaffAccess.Actor actor, long ownerUid) {
        if (actor.getUid() != ownerUid && !actor.isStaff()) {
            throw new IllegalArgumentException("你没有操作权限");
        }
    }

    private void rejectBanned(StaffAccess.Actor actor, long now) {
        long bannedUntil = number(actor.getUser().get("bantime"));
        if (bannedUntil == 1 || bannedUntil > now) {
            throw new IllegalArgumentException("账号当前不可发布内容");
        }
    }

    private boolean isStaff(String token) {
        Map<String, Object> user = tokens.user(token);
        if (user == null) {
            return false;
        }
        String group = text(user.get("group"));
        return "administrator".equals(group) || "editor".equals(group);
    }

    private long positiveId(Object value, String message) {
        long id = number(value);
        if (id <= 0) {
            throw new IllegalArgumentException(message);
        }
        return id;
    }

    private String validateText(Object raw, int minimum, int maximum, String minimumMessage,
                                String maximumMessage) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.length() < minimum) {
            throw new IllegalArgumentException(minimumMessage);
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(maximumMessage);
        }
        return value;
    }

    private String optionalText(Object raw, int maximum, String message) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.length() > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private int flag(Object value) {
        return number(value) == 1 ? 1 : 0;
    }

    private int flagDefault(Object value, int fallback) {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : flag(value);
    }

    private int integer(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int bounded(int requested, int maximum) {
        return Math.max(1, Math.min(requested, maximum));
    }

    private String placeholders(int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append('?');
        }
        return result.toString();
    }

    private String preview(String input) {
        String value = input == null ? "" : input.replace('\r', ' ').replace('\n', ' ').trim();
        return value.length() > 80 ? value.substring(0, 80) + "..." : value;
    }

    private Object value(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long number(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return value == null ? 0 : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static final class Page {
        private final List<Map<String, Object>> data;
        private final int total;

        private Page(List<Map<String, Object>> data, int total) {
            this.data = data;
            this.total = total;
        }

        public List<Map<String, Object>> getData() {
            return data;
        }

        public int getTotal() {
            return total;
        }
    }
}
