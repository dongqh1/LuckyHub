package com.dongqh.luckyhub.shipping;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
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
class ShippingAddressSnapshotTests {
    @Autowired ShippingAddressService addresses;
    @Autowired ShippingAddressSnapshotService snapshots;
    @Autowired SysUserMapper userMapper;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void clean() {
        userIds.forEach(id -> jdbc.update("DELETE FROM shipping_address_snapshot WHERE user_id=?", id));
        userIds.forEach(id -> jdbc.update("DELETE FROM user_shipping_address WHERE user_id=?", id));
        userIds.forEach(userMapper::deleteById);
        userIds.clear();
    }

    @Test
    void copiesCiphertextWithoutDecryptingAndRemainsImmutableAfterAddressChanges() {
        long userId = createUser();
        long addressId = addresses.create(userId, address("张三", "杭州市", "文一西路1号")).id();
        String sourceId = businessId("cash");

        ShippingAddressSnapshot created = snapshots.create(
                userId, addressId, ShippingSourceType.CASH_ORDER, sourceId);
        var originalCiphertexts = ciphertexts(created);
        var originalMasked = List.of(created.getReceiverMasked(), created.getPhoneMasked(), created.getRegionMasked());

        addresses.update(userId, addressId, new com.dongqh.luckyhub.shipping.dto.UpdateShippingAddressCommand(
                "李四", "13912345678", "上海市", "上海市", "浦东新区", "世纪大道2号", true));
        addresses.delete(userId, addressId);
        ShippingAddressSnapshot unchanged = snapshots.require(created.getId());

        assertThat(ciphertexts(unchanged)).isEqualTo(originalCiphertexts);
        assertThat(List.of(unchanged.getReceiverMasked(), unchanged.getPhoneMasked(), unchanged.getRegionMasked()))
                .isEqualTo(originalMasked).containsExactly("张*", "138****5678", "浙江省杭州市余杭区***");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM shipping_address_snapshot
                WHERE user_id=? AND source_type=? AND source_id=?
                """, Integer.class, userId, ShippingSourceType.CASH_ORDER.name(), sourceId)).isOne();
    }

    @Test
    void duplicateSourceReturnsEqualSnapshotButRejectsChangedOriginOrContents() {
        long userId = createUser();
        long first = addresses.create(userId, address("张三", "杭州市", "文一西路1号")).id();
        long second = addresses.create(userId, address("李四", "杭州市", "文一西路2号")).id();
        String sourceId = businessId("points");
        ShippingAddressSnapshot created = snapshots.create(
                userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId);

        assertThat(snapshots.create(userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId).getId())
                .isEqualTo(created.getId());
        assertError(() -> snapshots.create(userId, second, ShippingSourceType.POINTS_REDEMPTION, sourceId));
        addresses.update(userId, first, new com.dongqh.luckyhub.shipping.dto.UpdateShippingAddressCommand(
                "王五", "13712345678", "浙江省", "杭州市", "余杭区", "未来科技城3号", false));
        assertError(() -> snapshots.create(userId, first, ShippingSourceType.POINTS_REDEMPTION, sourceId));
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("snapshot-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("快照用户");
        user.setStatus(1);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private CreateShippingAddressCommand address(String receiver, String city, String detail) {
        return new CreateShippingAddressCommand(receiver, "13812345678", "浙江省", city,
                "余杭区", detail, false);
    }

    private String businessId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private List<String> ciphertexts(ShippingAddressSnapshot snapshot) {
        return List.of(snapshot.getReceiverCiphertext(), snapshot.getPhoneCiphertext(),
                snapshot.getProvinceCiphertext(), snapshot.getCityCiphertext(),
                snapshot.getDistrictCiphertext(), snapshot.getDetailCiphertext());
    }

    private void assertError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT));
    }
}
