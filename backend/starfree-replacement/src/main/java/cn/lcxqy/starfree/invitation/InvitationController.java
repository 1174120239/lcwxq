package cn.lcxqy.starfree.invitation;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/SFreeInvitation")
public class InvitationController {
    private final InvitationService invitations;

    public InvitationController(InvitationService invitations) {
        this.invitations = invitations;
    }

    @RequestMapping(value = "/config", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse config(@RequestParam Map<String, String> request) {
        return ApiResponse.success("", invitations.publicConfig(
                RequestValues.text(request, "inviteCode")));
    }

    @RequestMapping(value = "/me", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse me(@RequestParam Map<String, String> request) {
        return ApiResponse.success("", invitations.me(RequestValues.text(request, "token")));
    }
}
