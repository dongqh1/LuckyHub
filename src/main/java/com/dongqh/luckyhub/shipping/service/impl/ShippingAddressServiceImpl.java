package com.dongqh.luckyhub.shipping.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.dto.CreateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.dto.UpdateShippingAddressCommand;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import com.dongqh.luckyhub.shipping.enums.AddressStatus;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.mapper.UserShippingAddressMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.support.AddressMasker;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShippingAddressServiceImpl implements ShippingAddressService {

    private final UserShippingAddressMapper mapper;
    private final AddressCipher cipher;

    public ShippingAddressServiceImpl(UserShippingAddressMapper mapper, AddressCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public ShippingAddressView create(long userId, CreateShippingAddressCommand command) {
        lockUser(userId);
        List<UserShippingAddress> active = mapper.lockActiveByUser(userId);
        boolean makeDefault = active.isEmpty() || command.defaultAddress();
        if (makeDefault && !active.isEmpty()) {
            clearDefaults(userId);
        }
        SensitiveAddress address = normalize(command.receiverName(), command.phone(), command.province(),
                command.city(), command.district(), command.detail());
        UserShippingAddress entity = new UserShippingAddress();
        entity.setUserId(userId);
        writeSensitive(entity, address);
        entity.setIsDefault(makeDefault ? 1 : 0);
        entity.setStatus(AddressStatus.ACTIVE);
        entity.setVersion(0);
        mapper.insert(entity);
        return view(mapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShippingAddressView> list(long userId) {
        return mapper.selectList(new LambdaQueryWrapper<UserShippingAddress>()
                        .eq(UserShippingAddress::getUserId, userId)
                        .eq(UserShippingAddress::getStatus, AddressStatus.ACTIVE)
                        .orderByDesc(UserShippingAddress::getIsDefault)
                        .orderByDesc(UserShippingAddress::getId))
                .stream().map(this::view).toList();
    }

    @Override
    @Transactional
    public ShippingAddressView update(long userId, long addressId, UpdateShippingAddressCommand command) {
        lockUser(userId);
        UserShippingAddress entity = requireOwnedActive(userId, addressId);
        if (command.defaultAddress()) {
            clearDefaults(userId);
        }
        SensitiveAddress address = normalize(command.receiverName(), command.phone(), command.province(),
                command.city(), command.district(), command.detail());
        writeSensitive(entity, address);
        entity.setIsDefault(command.defaultAddress() ? 1 : 0);
        updateLocked(entity);
        return view(mapper.selectById(addressId));
    }

    @Override
    @Transactional
    public void delete(long userId, long addressId) {
        lockUser(userId);
        UserShippingAddress entity = requireOwnedActive(userId, addressId);
        entity.setStatus(AddressStatus.DELETED);
        entity.setIsDefault(0);
        entity.setDeletedAt(LocalDateTime.now());
        updateLocked(entity);
    }

    @Override
    @Transactional
    public ShippingAddressView makeDefault(long userId, long addressId) {
        lockUser(userId);
        UserShippingAddress entity = requireOwnedActive(userId, addressId);
        clearDefaults(userId);
        entity.setIsDefault(1);
        updateLocked(entity);
        return view(mapper.selectById(addressId));
    }

    @Override
    public UserShippingAddress requireOwnedActive(long userId, long addressId) {
        UserShippingAddress entity = mapper.selectById(addressId);
        if (entity == null || entity.getStatus() != AddressStatus.ACTIVE) {
            throw error(ShippingErrorCode.ADDRESS_NOT_FOUND);
        }
        if (!Long.valueOf(userId).equals(entity.getUserId())) {
            throw error(ShippingErrorCode.ADDRESS_ACCESS_DENIED);
        }
        return entity;
    }

    private void lockUser(long userId) {
        if (mapper.lockUserRow(userId) == null) {
            throw error(ShippingErrorCode.ADDRESS_INVALID);
        }
    }

    private void clearDefaults(long userId) {
        mapper.update(null, new LambdaUpdateWrapper<UserShippingAddress>()
                .eq(UserShippingAddress::getUserId, userId)
                .eq(UserShippingAddress::getStatus, AddressStatus.ACTIVE)
                .eq(UserShippingAddress::getIsDefault, 1)
                .set(UserShippingAddress::getIsDefault, 0));
    }

    private void updateLocked(UserShippingAddress entity) {
        int updated = mapper.update(entity, new LambdaUpdateWrapper<UserShippingAddress>()
                .eq(UserShippingAddress::getId, entity.getId())
                .eq(UserShippingAddress::getUserId, entity.getUserId())
                .eq(UserShippingAddress::getStatus, AddressStatus.ACTIVE));
        if (updated != 1) {
            throw error(ShippingErrorCode.ADDRESS_STATE_CONFLICT);
        }
    }

    private SensitiveAddress normalize(String receiver, String phone, String province,
                                       String city, String district, String detail) {
        return new SensitiveAddress(receiver.trim(), phone.trim(), province.trim(), city.trim(),
                district.trim(), detail.trim());
    }

    private void writeSensitive(UserShippingAddress entity, SensitiveAddress address) {
        entity.setReceiverCiphertext(cipher.encrypt(address.receiver()));
        entity.setPhoneCiphertext(cipher.encrypt(address.phone()));
        entity.setProvinceCiphertext(cipher.encrypt(address.province()));
        entity.setCityCiphertext(cipher.encrypt(address.city()));
        entity.setDistrictCiphertext(cipher.encrypt(address.district()));
        entity.setDetailCiphertext(cipher.encrypt(address.detail()));
        entity.setReceiverMasked(AddressMasker.maskReceiver(address.receiver()));
        entity.setPhoneMasked(AddressMasker.maskPhone(address.phone()));
        entity.setRegionMasked(AddressMasker.maskRegion(address.province(), address.city(), address.district()));
    }

    private ShippingAddressView view(UserShippingAddress entity) {
        return new ShippingAddressView(entity.getId(), entity.getReceiverMasked(), entity.getPhoneMasked(),
                entity.getRegionMasked(), entity.getIsDefault() == 1, entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private BusinessException error(ShippingErrorCode code) {
        return new BusinessException(code);
    }

    private record SensitiveAddress(String receiver, String phone, String province,
                                    String city, String district, String detail) {
    }
}
