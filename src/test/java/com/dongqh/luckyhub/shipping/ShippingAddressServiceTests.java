package com.dongqh.luckyhub.shipping;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.rbac.entity.SysUser;
import com.dongqh.luckyhub.rbac.mapper.SysUserMapper;
import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.dto.UpdateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import com.dongqh.luckyhub.shipping.enums.AddressStatus;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.mapper.UserShippingAddressMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ShippingAddressServiceTests {

    @Autowired ShippingAddressService service;
    @Autowired UserShippingAddressMapper addressMapper;
    @Autowired SysUserMapper userMapper;
    @Autowired JdbcTemplate jdbc;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void clean() {
        userIds.forEach(userId -> jdbc.update("DELETE FROM user_shipping_address WHERE user_id=?", userId));
        userIds.forEach(userMapper::deleteById);
        userIds.clear();
    }

    @Test
    void firstAddressBecomesDefaultAndLaterExplicitDefaultAtomicallyReplacesIt() {
        long userId = createUser();
        ShippingAddressView first = service.create(userId, create("张三", "13812345678", false));
        ShippingAddressView second = service.create(userId, create("李四", "13912345678", true));

        assertThat(first.defaultAddress()).isTrue();
        assertThat(second.defaultAddress()).isTrue();
        assertThat(service.list(userId)).extracting(ShippingAddressView::defaultAddress)
                .containsExactly(true, false);
        assertThat(activeDefaultCount(userId)).isEqualTo(1);
    }

    @Test
    void trimsEncryptsAndOnlyReturnsMaskedFields() {
        long userId = createUser();
        ShippingAddressView view = service.create(userId, new CreateShippingAddressCommand(
                " 张三 ", " 13812345678 ", " 浙江省 ", " 杭州市 ", " 余杭区 ", " 文一西路 1 号 ", false));
        UserShippingAddress stored = addressMapper.selectById(view.id());

        assertThat(view.receiverMasked()).isEqualTo("张*");
        assertThat(view.phoneMasked()).isEqualTo("138****5678");
        assertThat(view.regionMasked()).isEqualTo("浙江省杭州市余杭区***");
        assertThat(stored.getReceiverCiphertext()).startsWith("v1.").doesNotContain("张三");
        assertThat(stored.getPhoneCiphertext()).doesNotContain("13812345678");
        assertThat(stored.getProvinceCiphertext()).doesNotContain("浙江省");
        assertThat(stored.getCityCiphertext()).doesNotContain("杭州市");
        assertThat(stored.getDistrictCiphertext()).doesNotContain("余杭区");
        assertThat(stored.getDetailCiphertext()).doesNotContain("文一西路");
    }

    @Test
    void updateReencryptsAllSixFieldsAndCanBecomeDefault() {
        long userId = createUser();
        ShippingAddressView first = service.create(userId, create("张三", "13812345678", false));
        service.create(userId, create("李四", "13912345678", false));
        UserShippingAddress before = addressMapper.selectById(first.id());
        List<String> oldCiphertexts = ciphertexts(before);

        ShippingAddressView updated = service.update(userId, first.id(), new UpdateShippingAddressCommand(
                "张三", "13812345678", "浙江省", "杭州市", "余杭区", "文一西路1号", true));
        UserShippingAddress after = addressMapper.selectById(first.id());

        assertThat(ciphertexts(after)).zipSatisfy(oldCiphertexts,
                (current, old) -> assertThat(current).isNotEqualTo(old));
        assertThat(updated.defaultAddress()).isTrue();
        assertThat(activeDefaultCount(userId)).isEqualTo(1);
    }

    @Test
    void deleteIsSoftClearsDefaultAndRemovesAddressFromReads() {
        long userId = createUser();
        ShippingAddressView created = service.create(userId, create("张三", "13812345678", false));

        service.delete(userId, created.id());

        UserShippingAddress deleted = addressMapper.selectById(created.id());
        assertThat(deleted.getStatus()).isEqualTo(AddressStatus.DELETED);
        assertThat(deleted.getIsDefault()).isZero();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(service.list(userId)).isEmpty();
        assertError(() -> service.requireOwnedActive(userId, created.id()), ShippingErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void distinguishesAbsentIdsFromCrossUserAccessForAllMutations() {
        long owner = createUser();
        long other = createUser();
        long id = service.create(owner, create("张三", "13812345678", false)).id();
        UpdateShippingAddressCommand update = update("王五", "13712345678", false);

        assertError(() -> service.update(other, id, update), ShippingErrorCode.ADDRESS_ACCESS_DENIED);
        assertError(() -> service.delete(other, id), ShippingErrorCode.ADDRESS_ACCESS_DENIED);
        assertError(() -> service.makeDefault(other, id), ShippingErrorCode.ADDRESS_ACCESS_DENIED);
        assertError(() -> service.requireOwnedActive(other, id), ShippingErrorCode.ADDRESS_ACCESS_DENIED);
        assertError(() -> service.update(owner, Long.MAX_VALUE, update), ShippingErrorCode.ADDRESS_NOT_FOUND);
        assertError(() -> service.delete(owner, Long.MAX_VALUE), ShippingErrorCode.ADDRESS_NOT_FOUND);
        assertError(() -> service.makeDefault(owner, Long.MAX_VALUE), ShippingErrorCode.ADDRESS_NOT_FOUND);
        assertError(() -> service.requireOwnedActive(owner, Long.MAX_VALUE), ShippingErrorCode.ADDRESS_NOT_FOUND);
    }

    @Test
    void concurrentFirstAddressCreationLeavesExactlyOneDefault() throws Exception {
        long userId = createUser();
        runConcurrently(
                () -> service.create(userId, create("张三", "13812345678", false)),
                () -> service.create(userId, create("李四", "13912345678", false)));
        assertThat(service.list(userId)).hasSize(2);
        assertThat(activeDefaultCount(userId)).isEqualTo(1);
    }

    @Test
    void concurrentDefaultChangesLeaveExactlyOneDefault() throws Exception {
        long userId = createUser();
        long first = service.create(userId, create("张三", "13812345678", false)).id();
        long second = service.create(userId, create("李四", "13912345678", false)).id();
        runConcurrently(() -> service.makeDefault(userId, first), () -> service.makeDefault(userId, second));
        assertThat(activeDefaultCount(userId)).isEqualTo(1);
    }

    private void runConcurrently(Runnable first, Runnable second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try {
            Future<?> one = pool.submit(() -> { ready.countDown(); await(go); first.run(); });
            Future<?> two = pool.submit(() -> { ready.countDown(); await(go); second.run(); });
            ready.await();
            go.countDown();
            one.get();
            two.get();
        } finally {
            pool.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private long createUser() {
        SysUser user = new SysUser();
        user.setUsername("shipping-" + UUID.randomUUID());
        user.setPassword("test-password");
        user.setNickname("收货用户");
        user.setStatus(1);
        userMapper.insert(user);
        userIds.add(user.getId());
        return user.getId();
    }

    private CreateShippingAddressCommand create(String name, String phone, boolean isDefault) {
        return new CreateShippingAddressCommand(name, phone, "浙江省", "杭州市", "余杭区", "文一西路1号", isDefault);
    }

    private UpdateShippingAddressCommand update(String name, String phone, boolean isDefault) {
        return new UpdateShippingAddressCommand(name, phone, "浙江省", "杭州市", "余杭区", "文一西路2号", isDefault);
    }

    private List<String> ciphertexts(UserShippingAddress address) {
        return List.of(address.getReceiverCiphertext(), address.getPhoneCiphertext(), address.getProvinceCiphertext(),
                address.getCityCiphertext(), address.getDistrictCiphertext(), address.getDetailCiphertext());
    }

    private int activeDefaultCount(long userId) {
        return addressMapper.selectCount(new LambdaQueryWrapper<UserShippingAddress>()
                .eq(UserShippingAddress::getUserId, userId)
                .eq(UserShippingAddress::getStatus, AddressStatus.ACTIVE)
                .eq(UserShippingAddress::getIsDefault, 1)).intValue();
    }

    private void assertError(Runnable action, ShippingErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
