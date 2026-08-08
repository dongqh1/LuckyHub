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
import com.dongqh.luckyhub.inventory.service.ActivityPrizeInventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ChannelInventoryServiceTests {

    @Autowired
    private ChannelInventoryService service;

    @Autowired
    private ActivityPrizeInventoryService legacyInventoryService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductSkuMapper skuMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM inventory_ledger");
        jdbcTemplate.update("DELETE FROM inventory_reservation");
        jdbcTemplate.update("DELETE FROM inventory_channel_stock");
        jdbcTemplate.update("DELETE FROM sku_inventory");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product");
    }

    @Test
    void initializesOnceAndValidatesIdempotencyIdentity() {
        long skuId = createSku(1);

        var first = service.initialize(new InitializeSkuStockCommand(skuId, 20, " INIT-1 "));
        var repeated = service.initialize(new InitializeSkuStockCommand(skuId, 20, "INIT-1"));

        assertThat(first.totalStock()).isEqualTo(20);
        assertThat(repeated.totalStock()).isEqualTo(20);
        assertThat(count("sku_inventory")).isEqualTo(1);
        assertThat(countLedger("INIT-1")).isEqualTo(1);
        assertThatThrownBy(() -> service.initialize(new InitializeSkuStockCommand(skuId, 21, "INIT-1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ChannelInventoryErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT));
        assertThat(legacyInventoryService).isNotSameAs(service);
    }

    @Test
    void rejectsMissingOrDisabledSku() {
        assertError(() -> service.initialize(new InitializeSkuStockCommand(Long.MAX_VALUE, 20, "INIT-MISSING")),
                ChannelInventoryErrorCode.INVENTORY_SKU_UNAVAILABLE);
        long disabledSkuId = createSku(0);
        assertError(() -> service.initialize(new InitializeSkuStockCommand(disabledSkuId, 20, "INIT-DISABLED")),
                ChannelInventoryErrorCode.INVENTORY_SKU_UNAVAILABLE);
    }

    @Test
    void allocatesReservesAndConfirmsExactlyOnce() {
        long skuId = initializedSku(20);

        var allocated = service.allocate(new AllocateChannelStockCommand(skuId, " mall ", 10, "ALLOC-1"));
        var reserved = service.reserve(new ReserveChannelStockCommand(skuId, "mall", 3, "RES-1"));
        var confirmed = service.confirm("RES-1");
        var repeated = service.confirm("RES-1");

        assertThat(allocated.channelCode()).isEqualTo("MALL");
        assertThat(allocated.allocatedStock()).isEqualTo(10);
        assertThat(reserved.availableStock()).isEqualTo(7);
        assertThat(reserved.reservedStock()).isEqualTo(3);
        assertThat(confirmed.reservedStock()).isZero();
        assertThat(confirmed.consumedStock()).isEqualTo(3);
        assertThat(confirmed.reservationStatus()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(repeated).isEqualTo(confirmed);
        assertThat(countLedger("RESERVE:RES-1")).isEqualTo(1);
        assertThat(countLedger("CONFIRM:RES-1")).isEqualTo(1);
        assertError(() -> service.release("RES-1"),
                ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
    }

    @Test
    void releasesReservationExactlyOnceAndRejectsConfirmAfterRelease() {
        long skuId = initializedSku(20);
        service.allocate(new AllocateChannelStockCommand(skuId, "POINTS", 10, "ALLOC-POINTS"));
        service.reserve(new ReserveChannelStockCommand(skuId, "POINTS", 4, "RES-RELEASE"));

        var released = service.release("RES-RELEASE");
        var repeated = service.release("RES-RELEASE");

        assertThat(released.availableStock()).isEqualTo(10);
        assertThat(released.reservedStock()).isZero();
        assertThat(released.reservationStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(repeated).isEqualTo(released);
        assertThat(countLedger("RELEASE:RES-RELEASE")).isEqualTo(1);
        assertError(() -> service.confirm("RES-RELEASE"),
                ChannelInventoryErrorCode.INVENTORY_STATE_CONFLICT);
    }

    @Test
    void returnsStableErrorsForInsufficientAndMissingInventory() {
        long skuId = initializedSku(5);

        assertError(() -> service.allocate(new AllocateChannelStockCommand(
                        skuId, "MALL", 6, "ALLOC-TOO-MUCH")),
                ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT);
        service.allocate(new AllocateChannelStockCommand(skuId, "MALL", 3, "ALLOC-3"));
        assertError(() -> service.reserve(new ReserveChannelStockCommand(
                        skuId, "MALL", 4, "RES-TOO-MUCH")),
                ChannelInventoryErrorCode.INVENTORY_INSUFFICIENT);
        assertError(() -> service.get(skuId, "UNKNOWN"),
                ChannelInventoryErrorCode.INVENTORY_NOT_FOUND);
        assertError(() -> service.confirm("NO-RESERVATION"),
                ChannelInventoryErrorCode.INVENTORY_NOT_FOUND);
    }

    private long initializedSku(int totalStock) {
        long skuId = createSku(1);
        service.initialize(new InitializeSkuStockCommand(skuId, totalStock, "INIT-" + skuId));
        return skuId;
    }

    private long createSku(int status) {
        Product product = new Product();
        product.setProductCode("PROD-" + System.nanoTime());
        product.setProductName("库存测试商品");
        product.setProductType(ProductType.PHYSICAL);
        product.setStatus(1);
        productMapper.insert(product);

        ProductSku sku = new ProductSku();
        sku.setProductId(product.getId());
        sku.setSkuCode("SKU-" + System.nanoTime());
        sku.setSkuName("库存测试SKU");
        sku.setCashPriceCent(100L);
        sku.setCashEnabled(true);
        sku.setPointsEnabled(false);
        sku.setStatus(status);
        sku.setVersion(0);
        skuMapper.insert(sku);
        return sku.getId();
    }

    private int count(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private int countLedger(String businessNo) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_ledger WHERE business_no = ?", Integer.class, businessNo);
        return count == null ? 0 : count;
    }

    private void assertError(Runnable action, ChannelInventoryErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
