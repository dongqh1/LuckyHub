package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.catalog.dto.CreateProductCommand;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.service.CatalogService;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.order.dto.CreateCashOrderCommand;
import com.dongqh.luckyhub.order.service.CashOrderService;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PhysicalOrderAddressIntegrationTests {
    @Autowired CashOrderService cashOrders;
    @Autowired PointsRedemptionService redemptions;
    @Autowired PointsAccountService points;
    @Autowired CatalogService catalog;
    @Autowired ChannelInventoryService inventory;
    @Autowired ShippingAddressService addresses;
    @Autowired SysUserMapper users;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> skuIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();

    @AfterEach
    void clean() {
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
        userIds.clear();
        skuIds.clear();
        productIds.clear();
    }

    @Test
    void physicalCashAndPointsOrdersRequireOwnedAddressAndExposeOnlyMaskedSnapshot() {
        String suffix = UUID.randomUUID().toString();
        long owner = createUser();
        long other = createUser();
        long ownerAddress = createAddress(owner);
        long otherAddress = createAddress(other);
        long cashSku = createSku(ProductType.PHYSICAL, true, false);
        long pointsSku = createSku(ProductType.PHYSICAL, false, true);
        points.adjust(new AdminPointsAdjustmentCommand(owner, 1_000L, "SEED-PHYSICAL-" + suffix, "测试入账"));

        assertRequestInvalid(() -> cashOrders.create(owner,
                new CreateCashOrderCommand("CASH-NO-ADDRESS-" + suffix, cashSku, 1, null, null)));
        assertAccessDenied(() -> cashOrders.create(owner,
                new CreateCashOrderCommand("CASH-OTHER-ADDRESS-" + suffix, cashSku, 1, null, otherAddress)));
        assertRequestInvalid(() -> redemptions.create(owner,
                new CreatePointsRedemptionCommand("POINTS-NO-ADDRESS-" + suffix, pointsSku, 1, null)));

        var cash = cashOrders.create(owner,
                new CreateCashOrderCommand("CASH-PHYSICAL-" + suffix, cashSku, 1, null, ownerAddress));
        var redemption = redemptions.create(owner,
                new CreatePointsRedemptionCommand("POINTS-PHYSICAL-" + suffix, pointsSku, 1, ownerAddress));

        assertThat(cash.addressSnapshot()).isNotNull();
        assertThat(cash.addressSnapshot().receiverMasked()).isEqualTo("张*");
        assertThat(redemption.addressSnapshot()).isNotNull();
        assertThat(redemption.addressSnapshot().phoneMasked()).isEqualTo("138****5678");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM shipping_address_snapshot WHERE user_id=?", Integer.class, owner)).isEqualTo(2);
    }

    @Test
    void virtualOrdersRejectAddressAndKeepLegacyNullSnapshot() {
        String suffix = UUID.randomUUID().toString();
        long userId = createUser();
        long addressId = createAddress(userId);
        long cashSku = createSku(ProductType.VIRTUAL, true, false);
        long pointsSku = createSku(ProductType.VIRTUAL, false, true);
        points.adjust(new AdminPointsAdjustmentCommand(userId, 1_000L, "SEED-VIRTUAL-" + suffix, "测试入账"));

        assertRequestInvalid(() -> cashOrders.create(userId,
                new CreateCashOrderCommand("CASH-VIRTUAL-BAD-" + suffix, cashSku, 1, null, addressId)));
        assertRequestInvalid(() -> redemptions.create(userId,
                new CreatePointsRedemptionCommand("POINTS-VIRTUAL-BAD-" + suffix, pointsSku, 1, addressId)));

        assertThat(cashOrders.create(userId,
                new CreateCashOrderCommand("CASH-VIRTUAL-" + suffix, cashSku, 1, null, null)).addressSnapshot()).isNull();
        assertThat(redemptions.create(userId,
                new CreatePointsRedemptionCommand("POINTS-VIRTUAL-" + suffix, pointsSku, 1, null)).addressSnapshot()).isNull();
    }

    @Test
    void retriesWithDifferentAddressConflictAndRollbackLeavesNoSnapshot() {
        String suffix = UUID.randomUUID().toString();
        String retryRedemptionNo = "RETRY-SNAPSHOT-" + suffix;
        long userId = createUser();
        long first = createAddress(userId);
        long second = createAddress(userId);
        long sku = createSku(ProductType.PHYSICAL, false, true);
        points.adjust(new AdminPointsAdjustmentCommand(
                userId, 50L, "SEED-ROLLBACK-SNAPSHOT-" + suffix, "测试入账"));

        assertThatThrownBy(() -> redemptions.create(userId,
                new CreatePointsRedemptionCommand("ROLLBACK-SNAPSHOT-" + suffix, sku, 1, first)))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM shipping_address_snapshot
                WHERE user_id=? AND source_type=?
                """, Integer.class, userId, "POINTS_REDEMPTION")).isZero();

        points.adjust(new AdminPointsAdjustmentCommand(
                userId, 1_000L, "SEED-RETRY-SNAPSHOT-" + suffix, "测试入账"));
        redemptions.create(userId, new CreatePointsRedemptionCommand(retryRedemptionNo, sku, 1, first));
        assertThatThrownBy(() -> redemptions.create(userId,
                new CreatePointsRedemptionCommand(retryRedemptionNo, sku, 1, second)))
                .isInstanceOf(BusinessException.class);
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("po-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("实物下单用户");
        user.setStatus(1);
        users.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private long createAddress(long userId) {
        return addresses.create(userId, new CreateShippingAddressCommand(
                "张三", "13812345678", "浙江省", "杭州市", "余杭区", "文一西路1号", false)).id();
    }

    private long createSku(ProductType type, boolean cash, boolean point) {
        String suffix = UUID.randomUUID().toString();
        var product = catalog.create(new CreateProductCommand("P-" + suffix, "测试商品", type,
                null, null, "S-" + suffix, "默认", cash ? 100L : null,
                point ? 100L : null, cash, point));
        long skuId = product.skus().get(0).id();
        productIds.add(product.id());
        skuIds.add(skuId);
        inventory.initialize(new InitializeSkuStockCommand(skuId, 10, "INIT-" + suffix));
        if (cash) inventory.allocate(new AllocateChannelStockCommand(skuId, "MALL", 10, "MALL-" + suffix));
        if (point) inventory.allocate(new AllocateChannelStockCommand(skuId, "POINTS", 10, "POINTS-" + suffix));
        return skuId;
    }

    private void assertRequestInvalid(Runnable action) {
        assertShippingError(action, ShippingErrorCode.SHIPPING_REQUEST_INVALID);
    }

    private void assertAccessDenied(Runnable action) {
        assertShippingError(action, ShippingErrorCode.ADDRESS_ACCESS_DENIED);
    }

    private void assertShippingError(Runnable action, ShippingErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
