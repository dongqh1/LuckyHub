package com.dongqh.luckyhub.points;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.common.exception.ForbiddenException;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.dto.PointsRedemptionQuery;
import com.dongqh.luckyhub.points.dto.ReversePointsRedemptionCommand;
import com.dongqh.luckyhub.points.entity.PointsRedemptionOrder;
import com.dongqh.luckyhub.points.enums.PointsBusinessType;
import com.dongqh.luckyhub.points.enums.PointsErrorCode;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.mapper.PointsRedemptionOrderMapper;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
class PointsRedemptionServiceTests {

    @Autowired private PointsRedemptionService service;
    @Autowired private PointsAccountService accountService;
    @MockitoSpyBean private ChannelInventoryService inventoryService;
    @Autowired private PointsRedemptionOrderMapper orderMapper;
    @Autowired private ProductMapper productMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanBusinessTables();
    }

    @AfterEach
    void tearDown() {
        reset(inventoryService);
        cleanBusinessTables();
        userIds.forEach(userMapper::deleteById);
        userIds.clear();
    }

    @Test
    void createsCompletedRedemptionWithSnapshotsAndRepeatsWithoutSecondMutation() {
        long userId = createUser();
        SkuFixture fixture = createSku(3_000L, true, 1, 1);
        seedPoints(userId, 5_000L, "SEED-SUCCESS");

        var first = service.create(userId,
                new CreatePointsRedemptionCommand(" REDEEM-3000 ", fixture.skuId(), 1));

        Product product = productMapper.selectById(fixture.productId());
        product.setProductName("改价后的商品名");
        product.setImageUrl("https://cdn.example/new.png");
        productMapper.updateById(product);
        ProductSku sku = skuMapper.selectById(fixture.skuId());
        sku.setSkuName("改价后的SKU名");
        sku.setPointsPrice(9_999L);
        skuMapper.updateById(sku);

        var repeated = service.create(userId,
                new CreatePointsRedemptionCommand("REDEEM-3000", fixture.skuId(), 1));

        assertThat(repeated).isEqualTo(first);
        assertThat(first.status()).isEqualTo(PointsRedemptionStatus.COMPLETED);
        assertThat(first.unitPoints()).isEqualTo(3_000L);
        assertThat(first.totalPoints()).isEqualTo(3_000L);
        assertThat(first.productName()).isEqualTo("积分兑换商品");
        assertThat(first.skuName()).isEqualTo("积分兑换SKU");
        assertThat(first.imageUrl()).isEqualTo("https://cdn.example/original.png");
        assertThat(accountService.get(userId).balance()).isEqualTo(2_000L);
        assertThat(inventoryService.get(fixture.skuId(), "POINTS").consumedStock()).isOne();
        assertThat(count("points_redemption_order", "redemption_no", "REDEEM-3000")).isOne();
        assertThat(count("points_ledger", "business_id", "REDEEM-3000")).isOne();
        assertThat(count("inventory_reservation", "reservation_no", "REDEEM-3000")).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT channel_code FROM inventory_reservation WHERE reservation_no = ?",
                String.class, "REDEEM-3000")).isEqualTo("POINTS");
    }

    @Test
    void rejectsRedemptionNumberReuseWithDifferentIdentityAndProtectsUserScope() {
        long ownerId = createUser();
        long otherUserId = createUser();
        SkuFixture firstSku = createSku(100L, true, 1, 3);
        SkuFixture secondSku = createSku(100L, true, 1, 3);
        seedPoints(ownerId, 1_000L, "SEED-OWNER");
        seedPoints(otherUserId, 1_000L, "SEED-OTHER");
        service.create(ownerId, new CreatePointsRedemptionCommand("IDENTITY-1", firstSku.skuId(), 1));

        assertError(() -> service.create(otherUserId,
                        new CreatePointsRedemptionCommand("IDENTITY-1", firstSku.skuId(), 1)),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        assertError(() -> service.create(ownerId,
                        new CreatePointsRedemptionCommand("IDENTITY-1", secondSku.skuId(), 1)),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        assertError(() -> service.create(ownerId,
                        new CreatePointsRedemptionCommand("IDENTITY-1", firstSku.skuId(), 2)),
                PointsErrorCode.POINTS_IDEMPOTENCY_CONFLICT);
        assertThatThrownBy(() -> service.get(otherUserId, "IDENTITY-1"))
                .isInstanceOf(ForbiddenException.class);
        PointsRedemptionQuery query = new PointsRedemptionQuery();
        assertThat(service.page(otherUserId, query).records()).isEmpty();
        assertThat(service.page(ownerId, query).records()).singleElement()
                .satisfies(order -> assertThat(order.redemptionNo()).isEqualTo("IDENTITY-1"));
    }

    @Test
    void rejectsDisabledNonPointsZeroPriceAndOverflowSku() {
        long userId = createUser();
        seedPoints(userId, 10_000L, "SEED-UNAVAILABLE");
        SkuFixture disabled = createSku(100L, true, 0, 1);
        SkuFixture nonPoints = createSku(100L, false, 1, 1);
        SkuFixture zeroPrice = createSku(0L, true, 1, 1);
        SkuFixture overflow = createSku(Long.MAX_VALUE, true, 1, 2);

        assertError(() -> service.create(userId,
                        new CreatePointsRedemptionCommand("DISABLED", disabled.skuId(), 1)),
                PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
        assertError(() -> service.create(userId,
                        new CreatePointsRedemptionCommand("NON-POINTS", nonPoints.skuId(), 1)),
                PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
        assertError(() -> service.create(userId,
                        new CreatePointsRedemptionCommand("ZERO-PRICE", zeroPrice.skuId(), 1)),
                PointsErrorCode.REDEMPTION_SKU_UNAVAILABLE);
        assertError(() -> service.create(userId,
                        new CreatePointsRedemptionCommand("OVERFLOW", overflow.skuId(), 2)),
                PointsErrorCode.POINTS_AMOUNT_INVALID);
    }

    @Test
    void insufficientPointsRollsBackOrderInventoryAndDebit() {
        long userId = createUser();
        SkuFixture fixture = createSku(3_000L, true, 1, 1);
        seedPoints(userId, 2_999L, "SEED-LOW-POINTS");

        assertError(() -> service.create(userId,
                        new CreatePointsRedemptionCommand("LOW-POINTS", fixture.skuId(), 1)),
                PointsErrorCode.POINTS_INSUFFICIENT);

        assertThat(accountService.get(userId).balance()).isEqualTo(2_999L);
        assertThat(inventoryService.get(fixture.skuId(), "POINTS").availableStock()).isOne();
        assertThat(count("points_redemption_order", "redemption_no", "LOW-POINTS")).isZero();
        assertThat(count("points_ledger", "business_id", "LOW-POINTS")).isZero();
        assertThat(count("inventory_reservation", "reservation_no", "LOW-POINTS")).isZero();
    }

    @Test
    void insufficientInventoryRollsBackOrderAndPointsMutation() {
        long userId = createUser();
        SkuFixture fixture = createSku(100L, true, 1, 1);
        seedPoints(userId, 1_000L, "SEED-LOW-STOCK");

        assertThatThrownBy(() -> service.create(userId,
                new CreatePointsRedemptionCommand("LOW-STOCK", fixture.skuId(), 2)))
                .isInstanceOf(BusinessException.class);

        assertThat(accountService.get(userId).balance()).isEqualTo(1_000L);
        assertThat(inventoryService.get(fixture.skuId(), "POINTS").availableStock()).isOne();
        assertThat(count("points_redemption_order", "redemption_no", "LOW-STOCK")).isZero();
        assertThat(count("points_ledger", "business_id", "LOW-STOCK")).isZero();
    }

    @Test
    void failureAfterDebitRollsBackEveryCreateAsset() {
        long userId = createUser();
        SkuFixture fixture = createSku(100L, true, 1, 1);
        seedPoints(userId, 1_000L, "SEED-ROLLBACK");
        doThrow(new IllegalStateException("confirm failed"))
                .when(inventoryService).confirm("FAIL-AFTER-DEBIT");

        assertThatThrownBy(() -> service.create(userId,
                new CreatePointsRedemptionCommand("FAIL-AFTER-DEBIT", fixture.skuId(), 1)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accountService.get(userId).balance()).isEqualTo(1_000L);
        assertThat(inventoryService.get(fixture.skuId(), "POINTS").availableStock()).isOne();
        assertThat(count("points_redemption_order", "redemption_no", "FAIL-AFTER-DEBIT")).isZero();
        assertThat(count("points_ledger", "business_id", "FAIL-AFTER-DEBIT")).isZero();
        assertThat(count("inventory_reservation", "reservation_no", "FAIL-AFTER-DEBIT")).isZero();
        assertThat(count("inventory_ledger", "business_no", "RESERVE:FAIL-AFTER-DEBIT")).isZero();
    }

    @Test
    void reversesCompletedRedemptionExactlyOnceAndRejectsOtherStates() {
        long userId = createUser();
        SkuFixture fixture = createSku(300L, true, 1, 1);
        seedPoints(userId, 1_000L, "SEED-REVERSE-ORDER");
        service.create(userId,
                new CreatePointsRedemptionCommand("ORDER-TO-REVERSE", fixture.skuId(), 1));

        var first = service.reverse("ORDER-TO-REVERSE",
                new ReversePointsRedemptionCommand("REVERSAL-ORDER-1", "履约失败"));
        var repeated = service.reverse("ORDER-TO-REVERSE",
                new ReversePointsRedemptionCommand("REVERSAL-ORDER-1", "重复请求"));

        assertThat(first.status()).isEqualTo(PointsRedemptionStatus.REVERSED);
        assertThat(first.reversalNo()).isEqualTo("REVERSAL-ORDER-1");
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(accountService.get(userId).balance()).isEqualTo(1_000L);
        assertThat(inventoryService.get(fixture.skuId(), "POINTS").availableStock()).isOne();
        assertThat(count("points_ledger", "business_id", "REVERSAL-ORDER-1")).isOne();
        assertThat(count("inventory_ledger", "business_no", "RETURN:ORDER-TO-REVERSE")).isOne();
        assertError(() -> service.reverse("ORDER-TO-REVERSE",
                        new ReversePointsRedemptionCommand("REVERSAL-ORDER-2", "错误的第二次冲正")),
                PointsErrorCode.REDEMPTION_STATE_CONFLICT);

        service.create(userId,
                new CreatePointsRedemptionCommand("SECOND-ORDER", fixture.skuId(), 1));
        assertError(() -> service.reverse("SECOND-ORDER",
                        new ReversePointsRedemptionCommand("REVERSAL-ORDER-1", "复用其他订单冲正号")),
                PointsErrorCode.REDEMPTION_STATE_CONFLICT);

        PointsRedemptionOrder processing = processingOrder(userId, fixture.skuId(), "PROCESSING-ORDER");
        orderMapper.insert(processing);
        assertError(() -> service.reverse("PROCESSING-ORDER",
                        new ReversePointsRedemptionCommand("REV-PROCESSING", "尚未完成")),
                PointsErrorCode.REDEMPTION_STATE_CONFLICT);
    }

    private SkuFixture createSku(long price, boolean pointsEnabled, int status, int allocated) {
        String suffix = UUID.randomUUID().toString();
        Product product = new Product();
        product.setProductCode("P-" + suffix);
        product.setProductName("积分兑换商品");
        product.setProductType(ProductType.PHYSICAL);
        product.setImageUrl("https://cdn.example/original.png");
        product.setStatus(1);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSkuCode("S-" + suffix);
        sku.setSkuName("积分兑换SKU");
        sku.setCashPriceCent(null);
        sku.setPointsPrice(price);
        sku.setCashEnabled(false);
        sku.setPointsEnabled(pointsEnabled);
        sku.setStatus(status);
        sku.setVersion(0);
        skuMapper.insert(sku);

        if (status == 1 && allocated > 0) {
            inventoryService.initialize(new InitializeSkuStockCommand(
                    sku.getId(), allocated, "INIT-" + suffix));
            inventoryService.allocate(new AllocateChannelStockCommand(
                    sku.getId(), "POINTS", allocated, "ALLOC-" + suffix));
        }
        return new SkuFixture(product.getId(), sku.getId());
    }

    private PointsRedemptionOrder processingOrder(long userId, long skuId, String redemptionNo) {
        PointsRedemptionOrder order = new PointsRedemptionOrder();
        order.setRedemptionNo(redemptionNo);
        order.setUserId(userId);
        order.setSkuId(skuId);
        order.setQuantity(1);
        order.setUnitPoints(1L);
        order.setTotalPoints(1L);
        order.setProductCode("PROCESSING-P");
        order.setProductName("处理中商品");
        order.setSkuCode("PROCESSING-S");
        order.setSkuName("处理中SKU");
        order.setProductType(ProductType.PHYSICAL);
        order.setStatus(PointsRedemptionStatus.PROCESSING);
        return order;
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("pr-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("兑换测试用户");
        user.setStatus(1);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private void seedPoints(long userId, long amount, String businessId) {
        accountService.adjust(new AdminPointsAdjustmentCommand(userId, amount, businessId, "测试入账"));
    }

    private int count(String table, String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }

    private void cleanBusinessTables() {
        jdbcTemplate.update("DELETE FROM points_redemption_order");
        jdbcTemplate.update("DELETE FROM points_ledger");
        jdbcTemplate.update("DELETE FROM points_account");
        jdbcTemplate.update("DELETE FROM inventory_ledger");
        jdbcTemplate.update("DELETE FROM inventory_reservation");
        jdbcTemplate.update("DELETE FROM inventory_channel_stock");
        jdbcTemplate.update("DELETE FROM sku_inventory");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product");
    }

    private void assertError(Runnable action, PointsErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private record SkuFixture(long productId, long skuId) {}
}
