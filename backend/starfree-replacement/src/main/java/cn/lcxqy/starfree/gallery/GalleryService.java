package cn.lcxqy.starfree.gallery;

import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.StaffAccess;
import cn.lcxqy.starfree.space.AiModerationRequest;
import cn.lcxqy.starfree.space.AiModerationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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

@Service
public class GalleryService {
    private static final Logger LOG = LoggerFactory.getLogger(GalleryService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};
    private final JdbcTemplate jdbc;
    private final StaffAccess access;
    private final ObjectMapper mapper;
    private AiModerationService moderation;
    private ApplicationEventPublisher events;

    public GalleryService(JdbcTemplate jdbc, StaffAccess access, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.access = access;
        this.mapper = mapper;
    }

    @Autowired(required = false)
    void setAiModeration(AiModerationService moderation) {
        this.moderation = moderation;
    }

    @Autowired(required = false)
    void setEvents(ApplicationEventPublisher events) {
        this.events = events;
    }

    public Page galleryList(Map<String, String> request) {
        int page = Math.max(1, RequestValues.integer(request, "page", 1));
        int limit = bounded(request, "limit", 20, 50);
        String tag = optional(RequestValues.text(request, "tag"), 24);
        String where = " WHERE g.status='published'";
        List<Object> args = new ArrayList<>();
        if (!tag.isEmpty()) { where += " AND FIND_IN_SET(?,g.tags)>0"; args.add(tag); }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_gallery_items g" + where, Integer.class, args.toArray());
        List<Object> rowArgs = new ArrayList<>(args); rowArgs.add((page - 1) * limit); rowArgs.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(select() + where + " ORDER BY g.sort_order DESC,g.published_at DESC,g.id DESC LIMIT ?,?", rowArgs.toArray());
        return new Page(normalize(rows), total == null ? 0 : total);
    }

    public List<Map<String, Object>> galleryTags() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT tags FROM starfree_gallery_items WHERE status='published' ORDER BY published_at DESC LIMIT 1000")) {
            for (String tag : splitTags(row.get("tags"))) counts.put(tag, counts.getOrDefault(tag, 0) + 1);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        counts.entrySet().stream().sorted((a, b) -> Integer.compare(b.getValue(), a.getValue())).limit(30)
                .forEach(entry -> { Map<String, Object> item = new LinkedHashMap<>(); item.put("name", entry.getKey()); item.put("count", entry.getValue()); result.add(item); });
        return result;
    }

    public Map<String, Object> galleryInfo(long id, String token) {
        StaffAccess.Actor actor = optionalActor(token);
        List<Map<String, Object>> rows = jdbc.queryForList(select() + " WHERE g.id=? LIMIT 1", id);
        if (id <= 0 || rows.isEmpty()) throw new IllegalArgumentException("图集不存在");
        Map<String, Object> row = rows.get(0);
        String status = text(row.get("status")); long author = number(row.get("author_uid"));
        if (!"published".equals(status) && (actor == null || (!actor.isStaff() && actor.getUid() != author))) throw new IllegalArgumentException("图集不存在或尚未发布");
        if ("published".equals(status)) jdbc.update("UPDATE starfree_gallery_items SET views=views+1 WHERE id=?", id);
        Map<String, Object> result = normalizeOne(row);
        if ("published".equals(status)) result.put("views", number(row.get("views")) + 1);
        return result;
    }

    @Transactional
    public Map<String, Object> gallerySubmit(String token, Map<String, Object> body) {
        StaffAccess.Actor actor = access.requireUser(token);
        String title = required(body.get("title"), 2, 120, "标题");
        String description = optional(body.get("description"), 2000);
        List<String> images = cleanImages(body.get("imageUrls"));
        List<String> tags = cleanTags(body.get("tags"));
        if (images.isEmpty() || images.size() > 9) throw new IllegalArgumentException("请选择1至9张图片");
        if (tags.isEmpty()) throw new IllegalArgumentException("请至少添加一个标签");
        double ratio = decimal(body.get("imageRatio"), 0.8);
        if (ratio < 0.35 || ratio > 2.5) ratio = 0.8;
        long now = Instant.now().getEpochSecond();
        Integer duplicate = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_gallery_items WHERE author_uid=? AND title=? AND created>=? AND status<>'deleted'", Integer.class, actor.getUid(), title, now - 30);
        if (duplicate != null && duplicate > 0) throw new IllegalArgumentException("请勿重复提交相同图集");
        String imagesJson;
        try { imagesJson = mapper.writeValueAsString(images); }
        catch (Exception e) { throw new IllegalStateException("图片列表保存失败"); }
        long id = insertKey("INSERT INTO starfree_gallery_items(author_uid,title,description,image_urls,image_ratio,tags,status,created,modified) VALUES(?,?,?,?,?,?,'submitted',?,?)",
                actor.getUid(), title, description, imagesJson, ratio, String.join(",", tags), now, now);
        publishAiReview(id, actor.getUid(), title, description, tags, images, now);
        return managed(id);
    }

    public Page galleryManage(Map<String, String> request) {
        access.requireStaff(RequestValues.text(request, "token"));
        int page = Math.max(1, RequestValues.integer(request, "page", 1)); int limit = bounded(request, "limit", 30, 60);
        String status = RequestValues.text(request, "status"); String tag = optional(RequestValues.text(request, "tag"), 24);
        String where = " WHERE 1=1"; List<Object> args = new ArrayList<>();
        if (status.matches("submitted|published|rejected|hidden|deleted")) { where += " AND g.status=?"; args.add(status); }
        if (!tag.isEmpty()) { where += " AND FIND_IN_SET(?,g.tags)>0"; args.add(tag); }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM starfree_gallery_items g" + where, Integer.class, args.toArray());
        args.add((page - 1) * limit); args.add(limit);
        return new Page(normalize(jdbc.queryForList(select() + where + " ORDER BY FIELD(g.status,'submitted','published','rejected','hidden','deleted'),g.modified DESC,g.id DESC LIMIT ?,?", args.toArray())), total == null ? 0 : total);
    }

    @Transactional
    public Map<String, Object> galleryReview(String token, long id, String status, String reason) {
        StaffAccess.Actor actor = access.requireStaff(token);
        if (!status.matches("published|rejected|hidden|submitted")) throw new IllegalArgumentException("图集状态不正确");
        Map<String, Object> old = current(id); String previous = text(old.get("status")); long now = Instant.now().getEpochSecond();
        int changed = jdbc.update("UPDATE starfree_gallery_items SET status=?,review_reason=?,modified=?,published_at=? WHERE id=? AND status<>'deleted'",
                status, optional(reason, 500), now, "published".equals(status) ? (number(old.get("published_at")) > 0 ? number(old.get("published_at")) : now) : 0, id);
        if (changed != 1) throw new IllegalArgumentException("图集不存在或已删除");
        audit(id, actor.getUid(), actor.getName(), previous, status, "review", reason, now);
        recordAiHumanAction(id, previous, status, actor.getUid(), reason);
        return managed(id);
    }

    @Transactional
    public Map<String, Object> galleryDelete(String token, long id, String reason) {
        StaffAccess.Actor actor = access.requireStaff(token); Map<String, Object> old = current(id); String previous = text(old.get("status"));
        if ("deleted".equals(previous)) return normalizeOne(old);
        long now = Instant.now().getEpochSecond();
        jdbc.update("UPDATE starfree_gallery_items SET status='deleted',review_reason=?,modified=?,published_at=0 WHERE id=?", optional(reason, 500), now, id);
        audit(id, actor.getUid(), actor.getName(), previous, "deleted", "delete", reason, now);
        recordAiHumanAction(id, previous, "deleted", actor.getUid(), reason);
        return managed(id);
    }

    private void publishAiReview(long id, long uid, String title, String description,
                                 List<String> tags, List<String> images, long modified) {
        if (events == null || moderation == null || !moderation.enabledForGallery()) {
            return;
        }
        String text = "图集标题：" + title + "\n图集描述：" + description
                + "\n图集标签：" + String.join("、", tags);
        events.publishEvent(new AiModerationRequest(AiModerationService.TYPE_GALLERY, id, uid,
                text, "", images, modified));
    }

    private void recordAiHumanAction(long id, String from, String to, long uid, String reason) {
        if (moderation == null) {
            return;
        }
        try {
            moderation.recordGalleryAction(id, from, to, uid, reason);
        } catch (RuntimeException error) {
            LOG.warn("Could not record AI human action for gallery {}", id);
        }
    }

    private void audit(long id, long uid, String operatorName, String from, String to, String action, String reason, long now) {
        jdbc.update("INSERT INTO starfree_gallery_actions(gallery_id,operator_uid,operator_name,from_status,to_status,action,reason,created) VALUES(?,?,?,?,?,?,?,?)", id, uid, optional(operatorName, 64), from, to, action, optional(reason, 500), now);
    }
    private Map<String, Object> current(long id) { if (id <= 0) throw new IllegalArgumentException("图集不存在"); List<Map<String,Object>> rows=jdbc.queryForList(select()+" WHERE g.id=? LIMIT 1",id); if(rows.isEmpty())throw new IllegalArgumentException("图集不存在");return rows.get(0); }
    private Map<String, Object> managed(long id) { return normalizeOne(current(id)); }
    private String select() { return "SELECT g.id,g.author_uid,g.title,g.description,g.image_urls,g.image_ratio,g.tags,g.status,g.review_reason,g.views,g.sort_order,g.created,g.modified,g.published_at,u.name AS author_name,u.screenName AS author_screen_name,u.avatar AS author_avatar FROM starfree_gallery_items g LEFT JOIN starfree_users u ON u.uid=g.author_uid"; }
    private List<Map<String,Object>> normalize(List<Map<String,Object>> rows){List<Map<String,Object>> out=new ArrayList<>();for(Map<String,Object> row:rows)out.add(normalizeOne(row));return out;}
    private Map<String,Object> normalizeOne(Map<String,Object> r){Map<String,Object>x=new LinkedHashMap<>();x.put("id",number(r.get("id")));x.put("authorUid",number(r.get("author_uid")));x.put("authorName",text(r.get("author_screen_name")).isEmpty()?r.get("author_name"):r.get("author_screen_name"));x.put("authorAvatar",r.get("author_avatar"));x.put("title",r.get("title"));x.put("description",r.get("description"));x.put("imageUrls",parseImages(r.get("image_urls")));x.put("imageRatio",decimal(r.get("image_ratio"),0.8));x.put("tags",splitTags(r.get("tags")));x.put("status",r.get("status"));x.put("reviewReason",r.get("review_reason"));x.put("views",number(r.get("views")));x.put("sortOrder",number(r.get("sort_order")));x.put("created",number(r.get("created")));x.put("modified",number(r.get("modified")));x.put("publishedAt",number(r.get("published_at")));return x;}
    private List<String> parseImages(Object value){try{return mapper.readValue(text(value),STRING_LIST);}catch(Exception e){return new ArrayList<>();}}
    private List<String> cleanImages(Object value){List<String> input=value instanceof List?mapper.convertValue(value,STRING_LIST):new ArrayList<>();List<String> out=new ArrayList<>();for(String item:input){String url=optional(item,500);if(url.matches("(?i)^https?://[^\\s]+$")&&!out.contains(url))out.add(url);}return out;}
    private List<String> cleanTags(Object value){List<String> input=value instanceof List?mapper.convertValue(value,STRING_LIST):java.util.Arrays.asList(optional(value,500).split("[,，#\\s]+"));List<String> out=new ArrayList<>();for(String item:input){String tag=optional(item,24).replaceAll("[,，#\\s]+","");if(!tag.isEmpty()&&!out.contains(tag)){out.add(tag);if(out.size()==10)break;}}return out;}
    private List<String> splitTags(Object value){List<String> out=new ArrayList<>();for(String s:text(value).split(","))if(!s.trim().isEmpty())out.add(s.trim());return out;}
    private StaffAccess.Actor optionalActor(String token){try{return token==null||token.trim().isEmpty()?null:access.requireUser(token);}catch(RuntimeException e){return null;}}
    private int bounded(Map<String,String> r,String key,int def,int max){return Math.max(1,Math.min(max,RequestValues.integer(r,key,def)));}
    private String optional(Object v,int max){String s=v==null?"":String.valueOf(v).trim();return s.length()>max?s.substring(0,max):s;}
    private String required(Object v,int min,int max,String label){String s=optional(v,max);if(s.length()<min)throw new IllegalArgumentException(label+"至少需要"+min+"个字");return s;}
    private String text(Object v){return v==null?"":String.valueOf(v);}
    private long number(Object v){try{return v==null?0:Long.parseLong(String.valueOf(v));}catch(Exception e){return 0;}}
    private double decimal(Object v,double fallback){try{return v==null?fallback:Double.parseDouble(String.valueOf(v));}catch(Exception e){return fallback;}}
    private long insertKey(String sql,Object... values){KeyHolder holder=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement p=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);return p;},holder);if(holder.getKey()==null)throw new IllegalStateException("图集创建失败");return holder.getKey().longValue();}
    public static final class Page { final List<Map<String,Object>> data; final int total; Page(List<Map<String,Object>> data,int total){this.data=data;this.total=total;} }
}
