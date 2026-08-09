package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

abstract class Task5ShippingTestFixture {
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected SysUserMapper users;
    @Autowired protected ShippingAddressService addresses;
    @Autowired protected ShippingAddressSnapshotService snapshots;
    @Autowired protected CatalogService catalog;
    @Autowired protected ChannelInventoryService inventory;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> skuIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<String> fulfillmentNos = new ArrayList<>();

    @AfterEach
    void cleanTask5Fixtures() {
        fulfillmentNos.forEach(no -> jdbc.update("DELETE FROM fulfillment_attempt WHERE fulfillment_no=?", no));
        fulfillmentNos.forEach(no -> jdbc.update("DELETE FROM fulfillment_quarantine WHERE fulfillment_no=?", no));
        fulfillmentNos.forEach(no -> jdbc.update("DELETE FROM fulfillment_task WHERE fulfillment_no=?", no));
        fulfillmentNos.forEach(no -> jdbc.update("DELETE FROM sim_logistics_record WHERE fulfillment_no=?", no));
        userIds.forEach(id -> jdbc.update("DELETE FROM shipping_order WHERE target_user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM payment_order WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM mall_order WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM points_redemption_order WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM points_ledger WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM points_account WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM shipping_address_snapshot WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM user_shipping_address WHERE user_id=?", id));
        skuIds.forEach(id -> jdbc.update("DELETE FROM inventory_ledger WHERE sku_id=?", id));
        skuIds.forEach(id -> jdbc.update("DELETE FROM inventory_reservation WHERE sku_id=?", id));
        skuIds.forEach(id -> jdbc.update("DELETE FROM inventory_channel_stock WHERE sku_id=?", id));
        skuIds.forEach(id -> jdbc.update("DELETE FROM sku_inventory WHERE sku_id=?", id));
        skuIds.forEach(id -> jdbc.update("DELETE FROM product_sku WHERE id=?", id));
        productIds.forEach(id -> jdbc.update("DELETE FROM product WHERE id=?", id));
        userIds.forEach(users::deleteById);
        fulfillmentNos.clear();
        userIds.clear();
        skuIds.clear();
        productIds.clear();
    }

    protected long createUser() {
        SysUser user = new SysUser();
        user.setUsername("task5-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("统一发货测试用户");
        user.setStatus(1);
        users.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    protected long createAddress(long userId) {
        return addresses.create(userId, new CreateShippingAddressCommand(
                "张三", "13812345678", "浙江省", "杭州市", "余杭区", "文一西路1号", false)).id();
    }

    protected ShippingAddressSnapshot createSnapshot(
            long userId, ShippingSourceType type, String sourceId
    ) {
        return snapshots.create(userId, createAddress(userId), type, sourceId);
    }

    protected long createPhysicalSku(boolean cash, boolean points) {
        String suffix = UUID.randomUUID().toString();
        var product = catalog.create(new CreateProductCommand(
                "TASK5-P-" + suffix, "阶段6实物", ProductType.PHYSICAL,
                "https://cdn.example/task5.png", null,
                "TASK5-S-" + suffix, "默认规格", cash ? 100L : null,
                points ? 100L : null, cash, points));
        long skuId = product.skus().get(0).id();
        productIds.add(product.id());
        skuIds.add(skuId);
        inventory.initialize(new InitializeSkuStockCommand(skuId, 10, "TASK5-INIT-" + suffix));
        if (cash) {
            inventory.allocate(new AllocateChannelStockCommand(skuId, "MALL", 10, "TASK5-MALL-" + suffix));
        }
        if (points) {
            inventory.allocate(new AllocateChannelStockCommand(skuId, "POINTS", 10, "TASK5-POINTS-" + suffix));
        }
        return skuId;
    }

    protected String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    protected void trackFulfillment(String fulfillmentNo) {
        fulfillmentNos.add(fulfillmentNo);
    }
}
