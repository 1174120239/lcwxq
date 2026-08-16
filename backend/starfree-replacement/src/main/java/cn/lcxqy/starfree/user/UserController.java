package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import cn.lcxqy.starfree.security.LegacyTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户账号、登录态、个人资料、通知和关注关系接口。
 *
 * <p>这里描述的是替代后端直接接到请求时的行为；生产公网是否进入这些方法，还要看
 * Nginx 精确 location。邮箱验证码由同包的独立控制器处理；短信验证码、手机登录、
 * 第三方登录和后台用户管理仍由 {@code LegacyProxyController} 或生产旧 API 处理。
 */
@RestController
@RequestMapping("/SFreeUsers")
public class UserController {
    private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

    private final LegacyTokenService tokens;
    private final JdbcTemplate jdbc;
    private final UserAuthenticationService authentication;
    private final UserRegistrationService registration;
    private final AccountMaintenanceService accountMaintenance;
    private final UserInteractionService interactions;
    private final ObjectMapper mapper;
    private UserProfileService profiles;

    public UserController(
            LegacyTokenService tokens,
            JdbcTemplate jdbc,
            UserAuthenticationService authentication,
            UserRegistrationService registration,
            AccountMaintenanceService accountMaintenance,
            UserInteractionService interactions,
            ObjectMapper mapper) {
        this.tokens = tokens;
        this.jdbc = jdbc;
        this.authentication = authentication;
        this.registration = registration;
        this.accountMaintenance = accountMaintenance;
        this.interactions = interactions;
        this.mapper = mapper;
    }

    @Autowired
    void setProfiles(UserProfileService profiles) {
        this.profiles = profiles;
    }

    /**
     * GET/POST {@code /SFreeUsers/regConfig}：返回公开注册开关。
     *
     * <p>参数和鉴权均无。data 仅包含 {@code isEmail/isInvite/isPhone} 三个数值字段，
     * 来源是数据库当前配置；不返回验证码密钥或其他管理配置。数据库异常按旧协议返回
     * HTTP 200、{@code code=0,msg=请求异常}，因此调用方必须检查业务 code。
     */
    @RequestMapping(value = "/regConfig", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse registrationConfig() {
        try {
            return ApiResponse.success("", accountMaintenance.registrationConfig());
        } catch (RuntimeException error) {
            LOG.error("Registration config read failed", error);
            return ApiResponse.failure("\u8bf7\u6c42\u5f02\u5e38");
        }
    }

    /**
     * GET/POST {@code /SFreeUsers/userFoget}：通过邮箱验证码重置密码。
     *
     * <p>顶层参数 {@code params} 必须是 JSON，包含 {@code name/code/password}；name 可为
     * 用户名或邮箱。无需现有 token，但必须启用邮箱验证并使用旧后端写入的共享 Redis
     * 验证码。成功后验证码被消费，新密码按 Typecho phpass 格式保存，并按用户名、邮箱、
     * 手机号撤销 Redis-only 会话及 MySQL authCode；用户必须重新登录。不要重放验证码。
     */
    @RequestMapping(value = "/userFoget", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse forgotPassword(@RequestParam Map<String, String> params) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        try {
            int rows = accountMaintenance.forgotPassword(body);
            return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", rows);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            LOG.error("Password reset failed", error);
            return ApiResponse.failure("\u64cd\u4f5c\u5931\u8d25");
        }
    }

    /**
     * GET/POST {@code /SFreeUsers/userEdit}：当前用户修改个人资料。
     *
     * <p>必填顶层 {@code token}；{@code params} JSON 必须带与 token 对应的 {@code uid}。
     * 允许字段限于昵称、简介、背景、主页、头像、地址、收款信息、邮箱、手机和密码等前端
     * 资料字段；服务明确忽略/拒绝 assets、points、experience、VIP、group、用户名等越权
     * 字段。邮箱/手机变更要使用旧验证码 Redis；改密码或邮箱会撤销会话，其他修改会刷新
     * 共享 Redis 用户快照。简介命中违禁词时可能只跳过简介并返回提示，客户端应显示 msg。
     */
    @RequestMapping(value = "/userEdit", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse edit(@RequestParam Map<String, String> params) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        try {
            String token = RequestValues.text(params, "token");
            boolean accountFields = profiles == null || accountMaintenance.containsAccountFields(body);
            boolean profileFields = profiles != null && profiles.containsProfileFields(body);
            if (!accountFields && !profileFields) throw new IllegalArgumentException("\u53c2\u6570\u4e0d\u6b63\u786e");
            String message = "\u64cd\u4f5c\u6210\u529f";
            int rows = 0;
            if (profileFields) profiles.validate(body);
            if (accountFields) {
                AccountMaintenanceService.EditResult result = accountMaintenance.edit(token, body);
                message = result.getMessage();
                rows += result.getRows();
            }
            if (profileFields) rows += profiles.save(token, body);
            return ApiResponse.success(message, rows);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            LOG.error("User profile update failed", error);
            return ApiResponse.failure(
                    "\u63a5\u53e3\u8bf7\u6c42\u5f02\u5e38\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458");
        }
    }

    /**
     * GET/POST {@code /SFreeUsers/setClientId}：保存推送客户端标识。
     *
     * <p>必填 {@code token}；{@code clientId} 可为空（表示清除），最大 255 字符且不能含控制
     * 字符。仅更新 token 所属用户，不接受 uid。MySQL 更新成功后会刷新共享 Redis 会话，
     * 使旧 API 立即看到新 clientId；返回 data 为受影响行数 1。
     */
    @RequestMapping(value = "/setClientId", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse setClientId(@RequestParam Map<String, String> params) {
        try {
            int rows = accountMaintenance.setClientId(
                    RequestValues.text(params, "token"),
                    RequestValues.text(params, "clientId"));
            return ApiResponse.success("\u64cd\u4f5c\u6210\u529f", rows);
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            LOG.error("Client ID update failed", error);
            return ApiResponse.failure("\u64cd\u4f5c\u5931\u8d25");
        }
    }

    /**
     * GET/POST {@code /SFreeUsers/userRegister}：创建普通用户。
     *
     * <p>{@code params} JSON 至少包含 {@code name/password}，并按当前配置接收
     * {@code mail/phone/code/inviteCode}。IP 从可信代理头提取，用于防刷与用户记录。
     * 客户端传入的钱包、积分、经验、VIP、角色和时间字段不会被采用。旧的一次性邀请码继续
     * 按原配置写 assets；用户分享邀请码按独立配置给邀请人增加 points 和 experience，并保存
     * 唯一奖励记录。MyISAM 用户/流水写入由 InnoDB 操作 journal 和反向补偿保护。该接口只
     * 返回插入行数，不自动登录；邮箱验证码由独立发送接口写入共享 Redis。
     */
    @RequestMapping(value = "/userRegister", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse register(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        try {
            Map<String, Object> result = registration.register(body, clientAddress(request));
            return ApiResponse.success("\u6ce8\u518c\u6210\u529f", result.get("rows"));
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException error) {
            LOG.error("User registration failed", error);
            return ApiResponse.failure(
                    "\u63a5\u53e3\u8bf7\u6c42\u5f02\u5e38\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458");
        }
    }

    /**
     * POST {@code /SFreeUsers/userLogin}：用户名或邮箱密码登录。
     *
     * <p>{@code params} JSON 必须含 {@code name/password}。校验 Typecho phpass 密码和封禁
     * 时间，生成新 token，覆盖 MySQL {@code authCode}，更新登录时间/IP，并在启用桥接时写入
     * 旧 Java 序列化 Redis 会话。Redis 同步失败会撤销刚写入的 authCode，避免产生只能被一边
     * 识别的登录态。成功 data 是脱敏用户对象并含 token/time/isvip；生产公网当前是否使用本
     * 方法由 Nginx 路由决定，不能只看到源码就认为线上登录已切换。
     */
    @RequestMapping(value = "/userLogin", method = RequestMethod.POST)
    public ApiResponse login(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, params.get("params"));
        Map<String, Object> user = authentication.login(
                RequestValues.objectText(body, "name"),
                RequestValues.objectText(body, "password"),
                request.getRemoteAddr());
        return ApiResponse.success("登录成功", user);
    }

    /** Closed third-party login cannot be trusted to issue safe sessions and is disabled. */
    @RequestMapping(value = "/apiLogin", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse disabledThirdPartyLogin() {
        return ApiResponse.failure("第三方登录暂时停用，请使用账号密码登录");
    }

    /**
     * GET/POST {@code /SFreeUsers/signOut}：退出当前会话。
     *
     * <p>必填 {@code token}。会删除共享 Redis session，并仅清空匹配该 token 的 MySQL
     * authCode；无效或已退出 token 返回业务失败。接口不是“退出所有设备”，密码重置和部分
     * 敏感资料修改才会按账号别名清理更多 Redis 会话。
     */
    @RequestMapping(value = "/signOut", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse signOut(@RequestParam Map<String, String> params) {
        authentication.signOut(RequestValues.text(params, "token"));
        return ApiResponse.success("退出成功", null);
    }

    /**
     * GET/POST {@code /SFreeUsers/userStatus}：校验 token 并返回当前用户快照。
     *
     * <p>必填 {@code token}。解析顺序由 LegacyTokenService 决定，可兼容 MySQL authCode 和
     * 启用后的旧 Redis session；成功时把原 token 放回 data。无效 token 返回
     * {@code code=0}，不会返回 HTTP 401。该接口适合前端启动校验，不应当作后台权限检查的
     * 唯一依据，具体写接口仍会再次校验角色和资源所有权。
     */
    @RequestMapping(value = "/userStatus", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse status(@RequestParam Map<String, String> params) {
        String token = RequestValues.text(params, "token");
        Map<String, Object> user = tokens.user(token);
        if (user == null) {
            return ApiResponse.failure("用户未登录或Token验证失败");
        }
        if (profiles != null) profiles.attach(user, true);
        user.put("token", token);
        return ApiResponse.success(user);
    }

    /**
     * GET/POST {@code /SFreeUsers/userInfo}：读取公开用户资料。
     *
     * <p>传正数 {@code uid} 时按用户 id 查询，不要求 token；未传 uid 时使用 {@code token}
     * 查询当前用户。返回由 LegacyTokenService 统一脱敏，不含密码、authCode 等字段。
     * 指定 uid 时 token 不会改变目标用户，也不会扩大私密字段集；用户不存在返回业务失败。
     */
    @RequestMapping(value = "/userInfo", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse info(@RequestParam Map<String, String> params) {
        long uid = RequestValues.integer(params, "uid", 0);
        Long viewerUid = tokens.userId(RequestValues.text(params, "token"));
        Map<String, Object> user;
        if (uid > 0) {
            user = viewerUid != null && viewerUid.longValue() == uid
                    ? tokens.userById(uid) : tokens.publicUserById(uid);
        } else {
            user = tokens.user(RequestValues.text(params, "token"));
        }
        if (user != null && profiles != null) {
            long targetUid = ((Number) user.get("uid")).longValue();
            profiles.attach(user, viewerUid != null && viewerUid.longValue() == targetUid);
        }
        return user == null ? ApiResponse.failure("用户不存在") : ApiResponse.success(user);
    }

    /**
     * GET/POST {@code /SFreeUsers/userData}：用户内容/评论/关注统计。
     *
     * <p>优先使用公开的 {@code uid}；未传时用 {@code token} 推导当前 uid。成功 data 包含
     * {@code uid/contents/comments/fans/follow}，统计直接查询数据库且没有缓存。comments
     * 是动态评论（space type=3）数量；本人不传 uid 时包含待审核评论，查看他人只统计已发布评论。
     * 不再统计文章评论。用户不存在返回
     * {@code code=0}。
     */
    @RequestMapping(value = "/userData", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse data(@RequestParam Map<String, String> params) {
        long requestedUid = RequestValues.integer(params, "uid", 0);
        long uid = requestedUid;
        if (uid <= 0) {
            Long current = tokens.userId(RequestValues.text(params, "token"));
            uid = current == null ? 0 : current;
        }
        if (uid <= 0 || tokens.userById(uid) == null) {
            return ApiResponse.failure("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uid", uid);
        Integer contents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_contents WHERE authorId = ?", Integer.class, uid);
        Integer comments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_space WHERE uid = ? AND type = 3"
                        + (requestedUid > 0 ? " AND status = 1" : ""),
                Integer.class, uid);
        Integer fans = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_fan WHERE touid = ?", Integer.class, uid);
        Integer follow = jdbc.queryForObject(
                "SELECT COUNT(*) FROM starfree_fan WHERE uid = ?", Integer.class, uid);

        // Keep both names during the migration. The replacement uses the concise names,
        // while the existing uni-app pages and legacy API use the *Num names.
        data.put("contents", contents);
        data.put("contentsNum", contents);
        data.put("comments", comments);
        data.put("commentsNum", comments);
        data.put("fans", fans);
        data.put("fanNum", fans);
        data.put("follow", follow);
        data.put("followNum", follow);
        return ApiResponse.success(data);
    }

    /**
     * GET/POST {@code /SFreeUsers/inbox}：当前用户站内通知分页。
     *
     * <p>必填 {@code token}；{@code type} 可为空/all，或按 comment、finance、system、fan 等
     * 类型筛选；{@code page=1}、{@code limit=10}，limit 最大 40。comment 通知会附关联文章
     * 信息，来源用户已删除时返回占位用户。读取不会自动标记已读，需另调 setRead。
     */
    @RequestMapping(value = "/inbox", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse inbox(@RequestParam Map<String, String> params) {
        UserInteractionService.InboxPage page = interactions.inbox(params);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUsers/unreadNum}：统计当前用户未读通知总数。
     *
     * <p>只需 {@code token}，不接受 uid，也不按通知类型拆分。返回统一包络，data 是整数。
     * 该值来自 {@code starfree_inbox.isread=0} 实时计数，不包含仍由旧聊天系统单独维护的
     * 聊天未读状态。
     */
    @RequestMapping(value = "/unreadNum", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse unreadNum(@RequestParam Map<String, String> params) {
        return ApiResponse.success(interactions.unread(params));
    }

    /**
     * GET/POST {@code /SFreeUsers/setRead}：标记单条或批量通知已读。
     *
     * <p>必填 {@code token}；传 {@code id} 时只更新当前用户的对应通知。未传 id 时，
     * {@code type} 为空/all 表示全部，comment 同时覆盖 comment 和
     * postComment，finance/system/fan 只更新对应类型。chat 当前返回 0，因为聊天仍属旧后端；
     * 未支持的 type 返回业务失败。data 是实际更新行数，重复调用自然返回 0，属于幂等操作。
     */
    @RequestMapping(value = "/setRead", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse setRead(@RequestParam Map<String, String> params) {
        return ApiResponse.success(interactions.markRead(params));
    }

    /**
     * GET/POST {@code /SFreeUsers/follow}：关注或取消关注用户。
     *
     * <p>必填 {@code token/touid/type}；type=1 关注、type=0 取消。不能关注自己或不存在用户。
     * 首次关注写 {@code starfree_fan} 并向目标用户写 fan 通知；重复关注或重复取消不会改数据，
     * 当前控制器把“变更行数为 0”包装为 code=0，因此客户端不要把重复操作当作服务故障重试。
     */
    @RequestMapping(value = "/follow", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse follow(@RequestParam Map<String, String> params) {
        int changed = interactions.follow(params);
        return changed > 0 ? ApiResponse.success(changed) : ApiResponse.failure("No follow state changed");
    }

    /**
     * GET/POST {@code /SFreeUsers/isFollow}：判断当前用户是否已关注目标。
     *
     * <p>参数：{@code token/touid}。已关注返回 {@code code=1,data=null}，未关注、无效 token、
     * 自己或非法目标均返回 {@code code=0}；这是旧前端依赖的状态式包络，不要改为 data 布尔值，
     * 否则现有页面判断会失效。
     */
    @RequestMapping(value = "/isFollow", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse isFollow(@RequestParam Map<String, String> params) {
        return interactions.isFollowing(RequestValues.text(params, "token"),
                RequestValues.integer(params, "touid", 0))
                ? ApiResponse.success(null) : ApiResponse.failure("Not followed");
    }

    /**
     * GET/POST {@code /SFreeUsers/followList}：查询某用户关注的人。
     *
     * <p>参数：公开 {@code uid}、{@code page=1}、{@code limit=10}，limit 最大 50；无需 token。
     * 每条关系附目标用户的脱敏 {@code userJson}，已删除用户使用占位资料。返回标准分页包络，
     * total 是关系总数，不保证所有目标账号仍存在。
     */
    @RequestMapping(value = "/followList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse followList(@RequestParam Map<String, String> params) {
        UserInteractionService.FollowPage page = interactions.followList(
                RequestValues.integer(params, "uid", 0),
                RequestValues.integer(params, "limit", 10),
                RequestValues.integer(params, "page", 1));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUsers/fanList}：查询某用户的粉丝。
     *
     * <p>参数使用历史命名 {@code touid}，另有 {@code page=1/limit=10}，limit 最大 50；无需
     * token。每条记录附粉丝的脱敏 userJson，分页结构与 followList 相同。不要擅自把 touid
     * 政名为 uid，现有前端和旧接口都依赖该参数名。
     */
    @RequestMapping(value = "/fanList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse fanList(@RequestParam Map<String, String> params) {
        UserInteractionService.FollowPage page = interactions.fanList(
                RequestValues.integer(params, "touid", 0),
                RequestValues.integer(params, "limit", 10),
                RequestValues.integer(params, "page", 1));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    private String clientAddress(HttpServletRequest request) {
        String value = request.getHeader("X-Real-IP");
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader("X-Forwarded-For");
            if (value != null && value.contains(",")) {
                value = value.substring(0, value.indexOf(','));
            }
        }
        if (value == null || value.trim().isEmpty()) {
            value = request.getRemoteAddr();
        }
        return value == null ? "" : value.trim();
    }
}
