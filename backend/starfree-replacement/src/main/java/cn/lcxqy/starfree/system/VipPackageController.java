package cn.lcxqy.starfree.system;

import cn.lcxqy.starfree.economy.ShopCatalogService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public, read-only VIP package compatibility endpoint used by the existing purchase page. */
@RestController
@RequestMapping("/StarFreeSystem")
public class VipPackageController {
    private final ShopCatalogService shops;

    public VipPackageController(ShopCatalogService shops) {
        this.shops = shops;
    }

    /**
     * GET/POST {@code /StarFreeSystem/vipTypeList}: lists configured VIP packages.
     *
     * <p>No token or parameters are required. Rows expose only id, orderKey, name, price, day,
     * giftDay, and intro from starfree_vips, ordered by orderKey then id descending. The response
     * deliberately uses the historical top-level {@code vip} array and {@code count}; wrapping the
     * list in standard {@code data} would break pages/user/buyvip.vue. Prices and days returned here
     * are display data only: buyVIPpackage reloads the selected row under the economy lock and never
     * trusts a client-submitted price.
     */
    @RequestMapping(value = "/vipTypeList", method = {RequestMethod.GET, RequestMethod.POST})
    public Map<String, Object> list() {
        List<Map<String, Object>> packages = shops.vipPackages();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", 1);
        response.put("msg", "");
        response.put("vip", packages);
        response.put("count", packages.size());
        return response;
    }
}
