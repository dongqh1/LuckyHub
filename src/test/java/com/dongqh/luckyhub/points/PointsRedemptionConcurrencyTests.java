package com.dongqh.luckyhub.points;

import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.points.dto.AdminPointsAdjustmentCommand;
import com.dongqh.luckyhub.points.dto.CreatePointsRedemptionCommand;
import com.dongqh.luckyhub.points.enums.PointsRedemptionStatus;
import com.dongqh.luckyhub.points.service.PointsAccountService;
import com.dongqh.luckyhub.points.service.PointsRedemptionService;
import com.dongqh.luckyhub.points.vo.PointsRedemptionView;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest
class PointsRedemptionConcurrencyTests {

    @Autowired private PointsRedemptionService service;
    @Autowired private PointsAccountService accountService;
    @Autowired private ChannelInventoryService inventoryService;
    @Autowired private ProductMapper productMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private SysUserMapper userMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;
    private Long userId;

    @BeforeEach
    void setUp() {
        cleanBusinessTables();
        executor = Executors.newFixedThreadPool(20);
        userId = createUser();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        cleanBusinessTables();
        if (userId != null) {
            userMapper.deleteById(userId);
        }
    }

    @Test
    void twentyDuplicateRedemptionsConvergeToOneCompletedOrder() {
        assertTimeoutPreemptively(Duration.ofSeconds(45), () -> {
            long skuId = createSku();
            accountService.adjust(new AdminPointsAdjustmentCommand(
                    userId, 1_000L, "SEED-CONCURRENT-REDEMPTION", "并发兑换入账"));
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<PointsRedemptionView>> futures = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.create(userId,
                            new CreatePointsRedemptionCommand("SAME-REDEMPTION", skuId, 1));
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Set<Long> orderIds = new HashSet<>();
            for (Future<PointsRedemptionView> future : futures) {
                PointsRedemptionView view = future.get(30, TimeUnit.SECONDS);
                assertThat(view.status()).isEqualTo(PointsRedemptionStatus.COMPLETED);
                orderIds.add(view.id());
            }

            assertThat(orderIds).hasSize(1);
            assertThat(accountService.get(userId).balance()).isEqualTo(900L);
            assertThat(inventoryService.get(skuId, "POINTS").consumedStock()).isOne();
            assertThat(count("points_redemption_order", "redemption_no", "SAME-REDEMPTION")).isOne();
            assertThat(count("points_ledger", "business_id", "SAME-REDEMPTION")).isOne();
            assertThat(count("inventory_reservation", "reservation_no", "SAME-REDEMPTION")).isOne();
            assertThat(count("inventory_ledger", "business_no", "RESERVE:SAME-REDEMPTION")).isOne();
            assertThat(count("inventory_ledger", "business_no", "CONFIRM:SAME-REDEMPTION")).isOne();
        });
    }

    private long createSku() {
        String suffix = UUID.randomUUID().toString();
        Product product = new Product();
        product.setProductCode("PCR-P-" + suffix);
        product.setProductName("并发兑换商品");
        product.setProductType(ProductType.VIRTUAL);
        product.setStatus(1);
        productMapper.insert(product);
        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSkuCode("PCR-S-" + suffix);
        sku.setSkuName("并发兑换SKU");
        sku.setPointsPrice(100L);
        sku.setCashEnabled(false);
        sku.setPointsEnabled(true);
        sku.setStatus(1);
        sku.setVersion(0);
        skuMapper.insert(sku);
        inventoryService.initialize(new InitializeSkuStockCommand(
                sku.getId(), 1, "INIT-PCR-" + suffix));
        inventoryService.allocate(new AllocateChannelStockCommand(
                sku.getId(), "POINTS", 1, "ALLOC-PCR-" + suffix));
        return sku.getId();
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("pcr-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("并发兑换用户");
        user.setStatus(1);
        userMapper.insert(user);
        return user.getId();
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
}
