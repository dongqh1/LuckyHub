package com.dongqh.luckyhub.inventory;

import com.dongqh.luckyhub.catalog.entity.Product;
import com.dongqh.luckyhub.catalog.entity.ProductSku;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.catalog.mapper.ProductMapper;
import com.dongqh.luckyhub.catalog.mapper.ProductSkuMapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.inventory.channel.dto.AllocateChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.InitializeSkuStockCommand;
import com.dongqh.luckyhub.inventory.channel.dto.ReserveChannelStockCommand;
import com.dongqh.luckyhub.inventory.channel.enums.ChannelInventoryErrorCode;
import com.dongqh.luckyhub.inventory.channel.enums.InventoryReservationStatus;
import com.dongqh.luckyhub.inventory.channel.service.ChannelInventoryService;
import com.dongqh.luckyhub.inventory.channel.vo.ChannelInventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@SpringBootTest
class ChannelInventoryConcurrencyTests {

    @Autowired
    private ChannelInventoryService service;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductSkuMapper skuMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM inventory_ledger");
        jdbcTemplate.update("DELETE FROM inventory_reservation");
        jdbcTemplate.update("DELETE FROM inventory_channel_stock");
        jdbcTemplate.update("DELETE FROM sku_inventory");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product");
        executor = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void oneHundredConcurrentReservationsConsumeOnlyTenAvailableUnits() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            long skuId = initializedAndAllocatedSku(20, 10);
            CountDownLatch start = new CountDownLatch(1);
            ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                String reservationNo = "CONCURRENT-" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.reserve(new ReserveChannelStockCommand(skuId, "MALL", 1, reservationNo));
                        return true;
                    } catch (BusinessException exception) {
                        if (exception.getErrorCode() != ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT) {
                            unexpected.add(exception);
                        }
                        return false;
                    }
                }));
            }

            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }

            var inventory = service.get(skuId, "MALL");
            assertThat(unexpected).isEmpty();
            assertThat(successes).isEqualTo(10);
            assertThat(inventory.availableStock()).isZero();
            assertThat(inventory.reservedStock()).isEqualTo(10);
            assertThat(inventory.consumedStock()).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT MIN(available_stock) FROM inventory_channel_stock", Integer.class)).isZero();
        });
    }

    @Test
    void concurrentDuplicateReservationMutatesStockOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            long skuId = initializedAndAllocatedSku(20, 10);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    service.reserve(new ReserveChannelStockCommand(skuId, "mall", 1, "SAME-RES"));
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            var inventory = service.get(skuId, "MALL");
            assertThat(inventory.availableStock()).isEqualTo(9);
            assertThat(inventory.reservedStock()).isEqualTo(1);
            assertThat(count("inventory_reservation", "reservation_no", "SAME-RES")).isEqualTo(1);
            assertThat(count("inventory_ledger", "business_no", "RESERVE:SAME-RES")).isEqualTo(1);
        });
    }

    @Test
    void twentyConcurrentConfirmedReversalsRestoreConsumedStockOnce() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            long skuId = initializedAndAllocatedSku(20, 10);
            service.reserve(new ReserveChannelStockCommand(
                    skuId, "MALL", 1, "SAME-RETURN"));
            service.confirm("SAME-RETURN");
            CountDownLatch ready = new CountDownLatch(20);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ChannelInventoryView>> futures = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.reverseConfirmed("SAME-RETURN");
                }));
            }

            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<ChannelInventoryView> future : futures) {
                assertThat(future.get().reservationStatus())
                        .isEqualTo(InventoryReservationStatus.REVERSED);
            }

            var inventory = service.get(skuId, "MALL");
            assertThat(inventory.availableStock()).isEqualTo(10);
            assertThat(inventory.reservedStock()).isZero();
            assertThat(inventory.consumedStock()).isZero();
            assertThat(count("inventory_ledger", "business_no", "RETURN:SAME-RETURN")).isEqualTo(1);
        });
    }

    private long initializedAndAllocatedSku(int total, int allocated) {
        long skuId = createSku();
        service.initialize(new InitializeSkuStockCommand(skuId, total, "INIT-CONCURRENT-" + skuId));
        service.allocate(new AllocateChannelStockCommand(
                skuId, "MALL", allocated, "ALLOC-CONCURRENT-" + skuId));
        return skuId;
    }

    private long createSku() {
        Product product = new Product();
        product.setProductCode("CONCURRENT-PROD-" + System.nanoTime());
        product.setProductName("并发库存商品");
        product.setProductType(ProductType.PHYSICAL);
        product.setStatus(1);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSkuCode("CONCURRENT-SKU-" + System.nanoTime());
        sku.setSkuName("并发库存SKU");
        sku.setCashPriceCent(100L);
        sku.setCashEnabled(true);
        sku.setPointsEnabled(false);
        sku.setStatus(1);
        sku.setVersion(0);
        skuMapper.insert(sku);
        return sku.getId();
    }

    private int count(String table, String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
        return count == null ? 0 : count;
    }
}
