package cn.lcxqy.starfree.log;

import cn.lcxqy.starfree.api.ApiResponse;
import cn.lcxqy.starfree.api.RequestValues;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Legacy-compatible order history and administrator data-maintenance routes. */
@RestController
@RequestMapping("/SFreeUserlog")
public class UserlogAdministrationController {
    private final UserlogAdministrationService service;

    public UserlogAdministrationController(UserlogAdministrationService service) {
        this.service = service;
    }

    /**
     * GET/POST {@code /SFreeUserlog/orderList}: current buyer's sixty newest orders.
     *
     * <p>Requires token; uid from the request is ignored. Each log may include shopInfo and the
     * merchant's email. Deleted goods remain as the purchase log without shopInfo. count is this
     * response size and total is the buyer's complete buy-log count, which may exceed sixty.
     */
    @RequestMapping(value = "/orderList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse buyerOrders(@RequestParam Map<String, String> request) {
        UserlogAdministrationService.Page page = service.buyerOrders(
                RequestValues.text(request, "token"));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUserlog/orderSellList}: current merchant's paged sales.
     *
     * <p>Requires token; page defaults to one, limit to fifteen and is capped at fifty. Seller
     * ownership is l.toid=token uid. Buyer address/email are returned only through this authenticated
     * route for fulfilment and must not be copied into public shop or user APIs.
     */
    @RequestMapping(value = "/orderSellList", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse sellerOrders(@RequestParam Map<String, String> request) {
        UserlogAdministrationService.Page page = service.sellerOrders(
                RequestValues.text(request, "token"),
                RequestValues.integer(request, "page", 1),
                RequestValues.integer(request, "limit", 15));
        return ApiResponse.paged(page.getData(), page.getData().size(), page.getTotal());
    }

    /**
     * GET/POST {@code /SFreeUserlog/dataClean}: run one administrator cleanup selector.
     *
     * <p>Requires administrator token and clean=1..8. All deletes are permanent MyISAM writes and
     * execute under the global economy lock. The response data is the affected row/account count.
     * Selector 6 removes at most 500 rigorously dormant empty accounts per call; always back up the
     * production database and inspect the count before repeating a cleanup.
     */
    @RequestMapping(value = "/dataClean", method = {RequestMethod.GET, RequestMethod.POST})
    public ApiResponse clean(@RequestParam Map<String, String> request) {
        int changed = service.clean(RequestValues.text(request, "token"),
                RequestValues.integer(request, "clean", 0));
        return ApiResponse.success("清理成功", changed);
    }
}
