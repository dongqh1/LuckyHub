package com.dongqh.luckyhub.shipping.service;

import com.dongqh.luckyhub.common.result.PageResponse;
import com.dongqh.luckyhub.shipping.dto.ShippingOrderQuery;
import com.dongqh.luckyhub.shipping.vo.ShippingOrderView;

public interface ShippingAdminService {
    PageResponse<ShippingOrderView> page(ShippingOrderQuery query);
    ShippingOrderView get(String shippingNo);
    ShippingOrderView retry(String shippingNo, long operatorId, String note);
    ShippingOrderView terminate(String shippingNo, long operatorId, String note);
    int projectPending();
}
