package cn.lcxqy.starfree.journal;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.StaffAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Business rules for the independent Campus Editions domain. */
@Service
public class JournalService {
    private final JdbcTemplate jdbc;
    private final StaffAccess access;

    public JournalService(JdbcTemplate jdbc, StaffAccess access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Page journalList(Map<String, String> request) {
        int limit = bounded(request, "limit", 12, 50);
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        boolean includeHidden = RequestValues.integer(request, "includeHidden", 0) == 1
                && isStaff(RequestValues.text(request, "token"));
        String where = includeHidden ? " WHERE 1=1" : " WHERE j.status=1";
        List<Object> args = new ArrayList<>();
        if (RequestValues.integer(request, "featured", -1) >= 0) { where += " AND j.featured=?"; args.add(RequestValues.integer(request, "featured", 0)); }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_journals j" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args); rowArgs.add((page - 1) * limit); rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(journalSelect() + where + " ORDER BY j.featured DESC,j.sort_order DESC,j.modified DESC,j.id DESC LIMIT ?,?", rowArgs.toArray());
        return new Page(normalizeJournals(rows), total == null ? 0 : total);
    }

    public Map<String, Object> journalInfo(long id, String token) {
        if (id <= 0) throw new IllegalArgumentException("刊物不存在");
        boolean staff = isStaff(token);
        List<Map<String, Object>> rows = jdbc.queryForList(journalSelect() + " WHERE j.id=?" + (staff ? "" : " AND j.status=1") + " LIMIT 1", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("刊物不存在或已隐藏");
        return normalizeJournal(rows.get(0));
    }

    public Page articleList(Map<String, String> request) {
        int journalId = RequestValues.integer(request, "journalId", 0);
        if (journalId > 0) journalInfo(journalId, RequestValues.text(request, "token"));
        int limit = bounded(request, "limit", 12, 50); int page = Math.max(1, RequestValues.integer(request, "page", 1));
        String sort = RequestValues.text(request, "sort");
        String order = "recommended DESC,sort_order DESC,published_at DESC,id DESC";
        if ("hot".equals(sort)) order = "(likes*3 + comments*4 + views/5 - dislikes*2) DESC,published_at DESC,id DESC";
        if ("latest".equals(sort)) order = "published_at DESC,id DESC";
        List<Object> args = new ArrayList<>();
        String where = " WHERE a.status='published'";
        if (journalId > 0) { where += " AND a.journal_id=?"; args.add(journalId); }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_journal_articles a" + where, Integer.class, args.toArray());
        args.add((page - 1) * limit); args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(articleSelect() + where + " ORDER BY " + order + " LIMIT ?,?", args.toArray());
        return new Page(normalizeArticles(rows), total == null ? 0 : total);
    }

    public Map<String, Object> articleInfo(long id, String token) {
        if (id <= 0) throw new IllegalArgumentException("文章不存在");
        StaffAccess.Actor actor = optionalActor(token);
        List<Map<String, Object>> rows = jdbc.queryForList(articleSelect() + " WHERE a.id=? LIMIT 1", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("文章不存在");
        Map<String, Object> row = rows.get(0); String status = text(row.get("status")); long author = number(row.get("author_uid"));
        if (!"published".equals(status) && (actor == null || (!actor.isStaff() && actor.getUid() != author))) throw new IllegalArgumentException("文章不存在或尚未发布");
        if ("published".equals(status)) jdbc.update("UPDATE starfree_journal_articles SET views=views+1 WHERE id=?", id);
        Map<String, Object> result = normalizeArticle(row); result.put("isLiked", actor != null && vote(id, actor.getUid()) == 1); result.put("isDisliked", actor != null && vote(id, actor.getUid()) == -1); return result;
    }

    @Transactional
    public Map<String, Object> articleSubmit(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token); long journalId = number(body.get("journalId"));
        journalInfo(journalId, token); String title = required(body.get("title"), 5, 200, "文章标题"); String bodyText = required(body.get("bodyMarkdown"), 20, 60000, "文章正文");
        // Editorial review is mandatory for every normal submission, including staff submissions.
        // Staff can publish through the explicit review endpoint so the audit trail is preserved.
        String status = flag(body.get("draft")) == 1 ? "draft" : "submitted"; long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_journal_articles WHERE author_uid=? AND title=? AND created>=?", Integer.class, actor.getUid(), title, now - 30);
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("相同文章请勿重复提交");
        long id = insertKey("INSERT INTO starfree_journal_articles(journal_id,author_uid,title,subtitle,summary,body_markdown,cover_url,layout_preset,theme_preset,status,created,modified,published_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", journalId, actor.getUid(), title, optional(body.get("subtitle"), 500), optional(body.get("summary"), 1000), bodyText, optional(body.get("coverUrl"), 500), preset(body.get("layoutPreset"), "classic"), preset(body.get("themePreset"), "paper"), status, now, now, "published".equals(status) ? now : 0);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", id); result.put("status", status); return result;
    }

    @Transactional
    public Map<String, Object> articleVote(String token, long articleId, int vote) {
        StaffAccess.Actor actor = access.requireUser(token); if (vote != -1 && vote != 0 && vote != 1) throw new IllegalArgumentException("互动参数不正确");
        List<Map<String, Object>> article = jdbc.queryForList("SELECT id,status FROM starfree_journal_articles WHERE id=? LIMIT 1", articleId); if (article.isEmpty() || !"published".equals(text(article.get(0).get("status")))) throw new IllegalArgumentException("文章不存在");
        int old = vote(articleId, actor.getUid()); long now = Instant.now().getEpochSecond();
        if (vote == 0) jdbc.update("DELETE FROM starfree_journal_votes WHERE article_id=? AND uid=?", articleId, actor.getUid());
        else jdbc.update("INSERT INTO starfree_journal_votes(article_id,uid,vote,created,modified) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE vote=VALUES(vote),modified=VALUES(modified)", articleId, actor.getUid(), vote, now, now);
        if (old != vote) { if (old == 1) jdbc.update("UPDATE starfree_journal_articles SET likes=GREATEST(likes-1,0) WHERE id=?", articleId); if (old == -1) jdbc.update("UPDATE starfree_journal_articles SET dislikes=GREATEST(dislikes-1,0) WHERE id=?", articleId); if (vote == 1) jdbc.update("UPDATE starfree_journal_articles SET likes=likes+1 WHERE id=?", articleId); if (vote == -1) jdbc.update("UPDATE starfree_journal_articles SET dislikes=dislikes+1 WHERE id=?", articleId); }
        Map<String, Object> result = jdbc.queryForMap("SELECT likes,dislikes FROM starfree_journal_articles WHERE id=?", articleId); result.put("vote", vote); return result;
    }

    @Transactional
    public Map<String, Object> journalSave(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireAdministrator(token); long id = number(body.get("id")); String name = required(body.get("name"), 2, 80, "刊物名称"); long now = Instant.now().getEpochSecond(); String slug = optional(body.get("slug"), 100); if (slug.isEmpty()) slug = "journal-" + now;
        if (id > 0) jdbc.update("UPDATE starfree_journals SET name=?,slug=?,description=?,tags=?,cover_url=?,banner_url=?,theme=?,sort_order=?,featured=?,hot_weight=?,modified=? WHERE id=?", name, slug, optional(body.get("description"), 500), optional(body.get("tags"), 500), optional(body.get("coverUrl"), 500), optional(body.get("bannerUrl"), 500), preset(body.get("theme"), "editorial"), integer(body.get("sortOrder"), 0), flag(body.get("featured")), integer(body.get("hotWeight"), 0), now, id);
        else id = insertKey("INSERT INTO starfree_journals(name,slug,description,tags,cover_url,banner_url,theme,status,sort_order,featured,hot_weight,created_by,created,modified) VALUES(?,?,?,?,?,?,?,1,?,?,?,?,?,?)", name, slug, optional(body.get("description"), 500), optional(body.get("tags"), 500), optional(body.get("coverUrl"), 500), optional(body.get("bannerUrl"), 500), preset(body.get("theme"), "editorial"), integer(body.get("sortOrder"), 0), flag(body.get("featured")), integer(body.get("hotWeight"), 0), actor.getUid(), now, now);
        return journalInfo(id, token);
    }

    public Map<String, Object> journalStatus(String token, long id, int status) { access.requireAdministrator(token); if (status != 0 && status != 1) throw new IllegalArgumentException("状态不正确"); if (jdbc.update("UPDATE starfree_journals SET status=?,modified=? WHERE id=?", status, Instant.now().getEpochSecond(), id) != 1) throw new IllegalArgumentException("刊物不存在"); return journalInfo(id, token); }

    public Page articleManage(Map<String, String> request) { access.requireStaff(RequestValues.text(request, "token")); int limit = bounded(request, "limit", 20, 60); int page = Math.max(1, RequestValues.integer(request, "page", 1)); String status = RequestValues.text(request, "status"); String where = " WHERE 1=1"; List<Object> args = new ArrayList<>(); if (!status.isEmpty()) { where += " AND a.status=?"; args.add(status); } Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_journal_articles a" + where, Integer.class, args.toArray()); List<Object> rowArgs = new ArrayList<>(args); rowArgs.add((page - 1) * limit); rowArgs.add(limit); return new Page(normalizeArticles(jdbc.queryForList(articleSelect() + where + " ORDER BY a.modified DESC,a.id DESC LIMIT ?,?", rowArgs.toArray())), total == null ? 0 : total); }

    public Page myArticles(Map<String, String> request) { StaffAccess.Actor actor = access.requireUser(RequestValues.text(request, "token")); int limit = bounded(request, "limit", 20, 60); int page = Math.max(1, RequestValues.integer(request, "page", 1)); String status = RequestValues.text(request, "status"); String where = " WHERE a.author_uid=?"; List<Object> args = new ArrayList<>(); args.add(actor.getUid()); if (!status.isEmpty()) { where += " AND a.status=?"; args.add(status); } Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_journal_articles a" + where, Integer.class, args.toArray()); List<Object> rowArgs = new ArrayList<>(args); rowArgs.add((page - 1) * limit); rowArgs.add(limit); return new Page(normalizeArticles(jdbc.queryForList(articleSelect() + where + " ORDER BY a.modified DESC,a.id DESC LIMIT ?,?", rowArgs.toArray())), total == null ? 0 : total); }

    @Transactional public Map<String, Object> articleReview(String token, long id, String status, String reason) { StaffAccess.Actor actor = access.requireStaff(token); if (!status.matches("published|rejected|hidden")) throw new IllegalArgumentException("审核状态不正确"); List<Map<String,Object>> rows=jdbc.queryForList("SELECT status FROM starfree_journal_articles WHERE id=?",id); if(rows.isEmpty()) throw new IllegalArgumentException("文章不存在"); String old=text(rows.get(0).get("status")); long now=Instant.now().getEpochSecond(); int changed=jdbc.update("UPDATE starfree_journal_articles SET status=?,review_reason=?,modified=?,published_at=? WHERE id=?",status,optional(reason,500),now,"published".equals(status)?now:0,id); if(changed!=1) throw new IllegalArgumentException("文章不存在"); jdbc.update("INSERT INTO starfree_journal_actions(article_id,operator_uid,from_status,to_status,action,reason,created) VALUES(?,?,?,?,?,?,?)",id,actor.getUid(),old,status,"review",optional(reason,500),now); return managedArticle(id); }

    @Transactional
    public Map<String, Object> articleDelete(String token, long id, String reason) {
        StaffAccess.Actor actor = access.requireStaff(token);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status FROM starfree_journal_articles WHERE id=?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("文章不存在");
        String old = text(rows.get(0).get("status"));
        if ("deleted".equals(old)) return managedArticle(id);
        long now = Instant.now().getEpochSecond();
        int changed = jdbc.update("UPDATE starfree_journal_articles SET status='deleted',review_reason=?,modified=?,published_at=0 WHERE id=?", optional(reason, 500), now, id);
        if (changed != 1) throw new IllegalArgumentException("文章不存在");
        jdbc.update("INSERT INTO starfree_journal_actions(article_id,operator_uid,from_status,to_status,action,reason,created) VALUES(?,?,?,?,?,?,?)", id, actor.getUid(), old, "deleted", "delete", optional(reason, 500), now);
        return managedArticle(id);
    }

    private Map<String, Object> managedArticle(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(articleSelect() + " WHERE a.id=? LIMIT 1", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("文章不存在");
        return normalizeArticle(rows.get(0));
    }

    private String journalSelect() { return "SELECT j.id,j.name,j.slug,j.description,j.tags,j.cover_url,j.banner_url,j.theme,j.status,j.sort_order,j.featured,j.hot_weight,j.created,j.modified,(SELECT COUNT(*) FROM starfree_journal_articles a WHERE a.journal_id=j.id AND a.status='published') AS article_count FROM starfree_journals j"; }
    private String articleSelect() { return "SELECT a.id,a.journal_id,a.author_uid,a.title,a.subtitle,a.summary,a.body_markdown,a.cover_url,a.layout_preset,a.theme_preset,a.status,a.review_reason,a.recommended,a.sort_order,a.views,a.likes,a.dislikes,a.comments,a.created,a.modified,a.published_at,j.name AS journal_name,u.name AS author_name,u.screenName AS author_screenName,u.avatar AS author_avatar FROM starfree_journal_articles a JOIN starfree_journals j ON j.id=a.journal_id LEFT JOIN starfree_users u ON u.uid=a.author_uid"; }
    private List<Map<String,Object>> normalizeJournals(List<Map<String,Object>> rows){List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> r:rows)out.add(normalizeJournal(r));return out;}
    private Map<String,Object> normalizeJournal(Map<String,Object> r){Map<String,Object> x=new LinkedHashMap<>();x.put("id",number(r.get("id")));x.put("name",r.get("name"));x.put("slug",r.get("slug"));x.put("description",r.get("description"));x.put("tags",r.get("tags"));x.put("coverUrl",r.get("cover_url"));x.put("bannerUrl",r.get("banner_url"));x.put("theme",r.get("theme"));x.put("status",number(r.get("status")));x.put("sortOrder",number(r.get("sort_order")));x.put("featured",number(r.get("featured")));x.put("hotWeight",number(r.get("hot_weight")));x.put("articleCount",number(r.get("article_count")));return x;}
    private List<Map<String,Object>> normalizeArticles(List<Map<String,Object>> rows){List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> r:rows)out.add(normalizeArticle(r));return out;}
    private Map<String,Object> normalizeArticle(Map<String,Object> r){Map<String,Object> x=new LinkedHashMap<>();x.put("id",number(r.get("id")));x.put("journalId",number(r.get("journal_id")));x.put("journalName",r.get("journal_name"));x.put("authorUid",number(r.get("author_uid")));x.put("authorName",r.get("author_screenName")!=null&& !text(r.get("author_screenName")).isEmpty()?r.get("author_screenName"):r.get("author_name"));x.put("authorAvatar",r.get("author_avatar"));x.put("title",r.get("title"));x.put("subtitle",r.get("subtitle"));x.put("summary",r.get("summary"));x.put("bodyMarkdown",r.get("body_markdown"));x.put("coverUrl",r.get("cover_url"));x.put("layoutPreset",r.get("layout_preset"));x.put("themePreset",r.get("theme_preset"));x.put("status",r.get("status"));x.put("reviewReason",r.get("review_reason"));x.put("recommended",number(r.get("recommended")));x.put("sortOrder",number(r.get("sort_order")));x.put("views",number(r.get("views")));x.put("likes",number(r.get("likes")));x.put("dislikes",number(r.get("dislikes")));x.put("comments",number(r.get("comments")));x.put("created",number(r.get("created")));x.put("modified",number(r.get("modified")));x.put("publishedAt",number(r.get("published_at")));return x;}
    private int vote(long article,long uid){Integer v=jdbc.queryForObject("SELECT vote FROM starfree_journal_votes WHERE article_id=? AND uid=?",Integer.class,article,uid);return v==null?0:v;}
    private StaffAccess.Actor optionalActor(String token){try{return token==null||token.trim().isEmpty()?null:access.requireUser(token);}catch(RuntimeException e){return null;}}
    private boolean isStaff(String token){StaffAccess.Actor a=optionalActor(token);return a!=null&&a.isStaff();}
    private int bounded(Map<String,String> r,String key,int def,int max){return Math.max(1,Math.min(max,RequestValues.integer(r,key,def)));}
    private int flag(Object v){return "1".equals(String.valueOf(v))||Boolean.TRUE.equals(v)?1:0;}
    private int integer(Object v,int def){try{return v==null?def:Integer.parseInt(String.valueOf(v));}catch(Exception e){return def;}}
    private long number(Object v){try{return v==null?0:Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
    private String text(Object v){return v==null?"":String.valueOf(v);}
    private String optional(Object v,int max){String s=v==null?"":String.valueOf(v).trim();return s.length()>max?s.substring(0,max):s;}
    private String required(Object v,int min,int max,String label){String s=optional(v,max);if(s.length()<min)throw new IllegalArgumentException(label+"至少需要"+min+"个字");return s;}
    private String preset(Object v,String fallback){String s=optional(v,32);return s.matches("[a-z-]+")?s:fallback;}
    private long insertKey(String sql,Object... values){KeyHolder holder=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},holder);if(holder.getKey()==null)throw new IllegalStateException("记录创建失败");return holder.getKey().longValue();}
    public static final class Page{final List<Map<String,Object>> data;final int total;Page(List<Map<String,Object>> data,int total){this.data=data;this.total=total;}}
}
