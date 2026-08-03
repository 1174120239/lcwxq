package cn.lcxqy.starfree.user;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Legacy-compatible user discovery and management routes.
 *
 * <p>All endpoints keep form-urlencoded GET/POST compatibility because the existing uni-app and
 * admin pages use both methods. Authorization is repeated in the service. HTTP 200 with code 0 is
 * the expected business-error protocol and must not be replaced by an HTML error page.
 */
@RestController
@RequestMapping("/SFreeUsers")
public class UserAdministrationController {
    private final UserAdministrationService service;
    private final ObjectMapper mapper;

    public UserAdministrationController(UserAdministrationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * GET/POST {@code /SFreeUsers/userList}: paginated user search.
     *
     * <p>Parameters are {@code searchParams/page/limit/searchKey/order/token}. The JSON filter accepts
     * uid, invitationUser, group/groupKey, vip and bantime; limit is capped at 50 and order is an
     * allowlist. Anonymous results exclude mail, phone, balances, address, payment data, IP and push
     * identifiers. A valid administrator/editor token receives management fields. The response uses
     * {@code data/count/total}; no cache is written, so updates are immediately visible.
     */
    @RequestMapping(value = "/userList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse userList(@RequestParam Map<String, String> request) {
        UserAdministrationService.Page page = service.users(request);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUsers/phoneLogin}: consume an official SMS code and create a session.
     *
     * <p>Requires {@code phone/code}. SMS sending remains on the official endpoint, but this route
     * reads and consumes the same Java-serialized Redis code. Existing users are checked for bans;
     * when the phone is unknown, registration occurs only if invitation-only registration is off.
     * The generated token is stored in MySQL and the shared Redis session. A newly created account
     * returns {@code noPassWord=1}. A code is consumed only after the session is complete.
     */
    @RequestMapping(value = "/phoneLogin", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse phoneLogin(@RequestParam Map<String, String> request,
                                  HttpServletRequest servletRequest) {
        Map<String, Object> user = service.phoneLogin(
                RequestValues.text(request, "phone"), RequestValues.text(request, "code"),
                clientAddress(servletRequest));
        return ApiResponse.success("登录成功", user);
    }

    /**
     * GET/POST {@code /SFreeUsers/manageUserEdit}: staff edits an existing account.
     *
     * <p>Requires staff {@code token} and JSON {@code params} containing uid or name. Editable fields
     * are screenName, mail, phone, url, customize, introduce, avatar, userBg, address, pay,
     * experience, group and password. Passwords are phpass-hashed. created/logged/assets/points/vip
     * and authCode cannot be supplied. Editors cannot modify or create administrators; changing a
     * password, account identifier or group clears all known MySQL/Redis sessions.
     */
    @RequestMapping(value = "/manageUserEdit", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse manageUserEdit(@RequestParam Map<String, String> request) {
        Map<String, Object> body = RequestValues.jsonObject(mapper, request.get("params"));
        int changed = service.manageEdit(RequestValues.text(request, "token"), body);
        return changed > 0
                ? ApiResponse.success("操作成功", changed)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeUsers/userDelete}: administrator deletes one account row.
     *
     * <p>Requires {@code token/key}; key is the target uid. Self-deletion and administrator targets
     * are rejected. Social-binding rows and active sessions are removed, but historical posts,
     * comments, logs and payments are deliberately retained for audit compatibility. Use userClean
     * explicitly before deletion if those rows must be removed. The operation holds the global
     * economy advisory lock so it cannot race a balance update.
     */
    @RequestMapping(value = "/userDelete", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse userDelete(@RequestParam Map<String, String> request) {
        int changed = service.deleteUser(RequestValues.text(request, "token"),
                RequestValues.integer(request, "key", 0));
        return changed > 0
                ? ApiResponse.success("操作成功", changed)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeUsers/setScan}: authorize a pending QR-code login.
     *
     * <p>Requires a valid {@code token} and server-generated {@code codeContent}. The nonce must
     * already exist in shared Redis; arbitrary missing keys are rejected. Its value is replaced with
     * the token for 90 seconds using legacy Java serialization. Replaying an expired QR code returns
     * code 0 and does not create a new key.
     */
    @RequestMapping(value = "/setScan", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse setScan(@RequestParam Map<String, String> request) {
        service.approveScan(RequestValues.text(request, "token"),
                RequestValues.text(request, "codeContent"));
        return ApiResponse.success("操作成功！", null);
    }

    /**
     * GET/POST {@code /SFreeUsers/madeInvitation}: administrator generates invite codes.
     *
     * <p>Requires {@code token/num}; num is 1..100. Codes use an unambiguous cryptographic random
     * alphabet, are checked for collisions, and are inserted with status 0, the creator uid and
     * current epoch seconds. The endpoint never accepts a caller-supplied code or owner.
     */
    @RequestMapping(value = "/madeInvitation", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse madeInvitation(@RequestParam Map<String, String> request) {
        int created = service.generateInvitations(RequestValues.text(request, "token"),
                RequestValues.integer(request, "num", 0));
        return ApiResponse.success("生成邀请码成功", created);
    }

    /**
     * GET/POST {@code /SFreeUsers/invitationList}: administrator invitation pagination.
     *
     * <p>Requires token. {@code searchParams} may contain status 0/1; page defaults to 1 and limit to
     * 15 with a maximum of 50. Results are newest first and return id/code/created/uid/status plus
     * count and total. Invalid JSON is treated as no filter, matching other legacy list routes.
     */
    @RequestMapping(value = "/invitationList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse invitationList(@RequestParam Map<String, String> request) {
        UserAdministrationService.Page page = service.invitations(request);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUsers/invitationExcel}: download unused invitation codes.
     *
     * <p>Requires administrator token. limit is clamped to 1..10000. The response is UTF-8 tabular
     * text with an Excel MIME type and .xls filename, avoiding a heavyweight spreadsheet runtime
     * while remaining directly openable by desktop Excel. Only status=0 codes are exported. Never
     * return a JSON body after response bytes have started.
     */
    @RequestMapping(value = "/invitationExcel", method = {RequestMethod.GET, RequestMethod.POST})
    public void invitationExcel(@RequestParam Map<String, String> request,
                                HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = service.invitationExport(
                RequestValues.text(request, "token"), RequestValues.integer(request, "limit", 15));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.ms-excel;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=InvitationExcel.xls");
        try (Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write('\ufeff');
            writer.write("ID\t邀请码\t创建人\t创建时间\r\n");
            for (Map<String, Object> row : rows) {
                writer.write(cell(row, "id") + "\t" + cell(row, "code") + "\t"
                        + cell(row, "uid") + "\t" + cell(row, "created") + "\r\n");
            }
        }
    }

    /**
     * GET/POST {@code /SFreeUsers/sendUser}: administrator sends a persistent system message.
     *
     * <p>Requires {@code token/uid/text}; text is 1..4000 characters and the target must exist. One
     * starfree_inbox row is written with type=system and isread=0. Provider push is not attempted in
     * this replacement route, so failure of an optional push vendor cannot duplicate the inbox row.
     */
    @RequestMapping(value = "/sendUser", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse sendUser(@RequestParam Map<String, String> request) {
        int changed = service.sendSystemMessage(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0), RequestValues.text(request, "text"));
        return changed > 0
                ? ApiResponse.success("发送成功", changed)
                : ApiResponse.failure("发送失败");
    }

    /**
     * GET/POST {@code /SFreeUsers/banUser}: staff temporarily bans one account.
     *
     * <p>Requires token, uid, positive time in seconds, type manager/system and optional reason text.
     * A new duration extends an existing future ban. Administrators cannot be banned; editors also
     * cannot ban editors. The write clears authCode, revokes shared sessions and appends an immutable
     * starfree_violation row with handler and expiry. Maximum duration is ten years.
     */
    @RequestMapping(value = "/banUser", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse banUser(@RequestParam Map<String, String> request) {
        int changed = service.ban(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0),
                RequestValues.integer(request, "time", 0),
                RequestValues.text(request, "type"), RequestValues.text(request, "text"));
        return changed > 0
                ? ApiResponse.success("操作成功", changed)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeUsers/unblockUser}: administrator removes an active ban.
     *
     * <p>Requires token and uid. bantime is set to current epoch seconds; an already-unbanned user
     * returns code 0. Violation history is retained as an audit record and is never rewritten.
     */
    @RequestMapping(value = "/unblockUser", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse unblockUser(@RequestParam Map<String, String> request) {
        int changed = service.unblock(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0));
        return changed > 0
                ? ApiResponse.success("操作成功", changed)
                : ApiResponse.failure("操作失败");
    }

    /**
     * GET/POST {@code /SFreeUsers/violationList}: public ban-history pagination.
     *
     * <p>searchParams optionally filters uid/type; page and limit are bounded. Each row includes a
     * sanitized userJson, or an explicit deleted-user placeholder. Private contact, balance, payment,
     * IP and push fields are never exposed. Results use current database user state, so bantime may
     * show an unblocked value while the historical violation value retains the original expiry.
     */
    @RequestMapping(value = "/violationList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse violationList(@RequestParam Map<String, String> request) {
        UserAdministrationService.Page page = service.violations(request);
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUsers/userClean}: administrator deletes one selected data class.
     *
     * <p>Requires token, uid and clean selector: 1 posts, 2 comments, 3 Space, 4 shop, 5 clock logs.
     * Administrator targets are rejected. This intentionally does not cascade into other tables,
     * preserving the old endpoint's narrow behavior; MyISAM has no rollback, so callers must back up
     * and run one selector at a time. data is the number of deleted rows.
     */
    @RequestMapping(value = "/userClean", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse userClean(@RequestParam Map<String, String> request) {
        int changed = service.cleanUserData(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0), RequestValues.integer(request, "clean", 0));
        return ApiResponse.success("清理成功，缓存刷新后将自动生效", changed);
    }

    /**
     * GET/POST {@code /SFreeUsers/restrict}: administrator controls the shared silence key.
     *
     * <p>Requires token/uid/type. type=1 creates the exact Redis key consumed by content, Space and
     * advertising write guards for the configured silenceTime; type=0 removes it. Removing a missing
     * key returns code 0. Redis must be enabled because a MySQL-only substitute would not affect the
     * old API during mixed routing.
     */
    @RequestMapping(value = "/restrict", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse restrict(@RequestParam Map<String, String> request) {
        int type = RequestValues.integer(request, "type", 0);
        service.restrict(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0), type == 1);
        return ApiResponse.success("操作成功", null);
    }

    /**
     * GET/POST {@code /SFreeUsers/giftVIP}: staff grants time-limited VIP.
     *
     * <p>Requires token, uid and day 1..36500. Permanent VIP cannot be extended. Expiry starts from
     * the later of now/current expiry, the operation runs under the shared economy lock, and a zero
     * value completed paylog row records the grant. No assets or points are deducted and the official
     * recharge provider is not called.
     */
    @RequestMapping(value = "/giftVIP", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse giftVip(@RequestParam Map<String, String> request) {
        int changed = service.giftVip(RequestValues.text(request, "token"),
                RequestValues.integer(request, "uid", 0), RequestValues.integer(request, "day", 1));
        return changed > 0
                ? ApiResponse.success("开通VIP成功", changed)
                : ApiResponse.failure("操作失败");
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

    private String cell(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
            }
        }
        return "";
    }
}
