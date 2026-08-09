package com.dongqh.luckyhub.shipping.service.impl;

import com.dongqh.luckyhub.common.exception.BusinessException;
import com.dongqh.luckyhub.shipping.entity.ShippingAddressSnapshot;
import com.dongqh.luckyhub.shipping.entity.UserShippingAddress;
import com.dongqh.luckyhub.shipping.enums.ShippingErrorCode;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import com.dongqh.luckyhub.shipping.mapper.ShippingAddressSnapshotMapper;
import com.dongqh.luckyhub.shipping.service.ShippingAddressService;
import com.dongqh.luckyhub.shipping.service.ShippingAddressSnapshotService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class ShippingAddressSnapshotServiceImpl implements ShippingAddressSnapshotService {
    private final ShippingAddressSnapshotMapper mapper;
    private final ShippingAddressService addresses;

    public ShippingAddressSnapshotServiceImpl(
            ShippingAddressSnapshotMapper mapper,
            ShippingAddressService addresses
    ) {
        this.mapper = mapper;
        this.addresses = addresses;
    }

    @Override
    public ShippingAddressSnapshot create(
            long userId,
            long addressId,
            ShippingSourceType sourceType,
            String sourceId
    ) {
        UserShippingAddress address = addresses.requireOwnedActive(userId, addressId);
        ShippingAddressSnapshot candidate = copy(userId, addressId, sourceType, sourceId, address);
        ShippingAddressSnapshot existing = mapper.selectBySource(sourceType, sourceId);
        if (existing != null) {
            return requireEqual(existing, candidate);
        }
        try {
            mapper.insert(candidate);
            return candidate;
        } catch (DuplicateKeyException exception) {
            ShippingAddressSnapshot winner = mapper.selectBySource(sourceType, sourceId);
            if (winner == null) {
                throw exception;
            }
            return requireEqual(winner, candidate);
        }
    }

    @Override
    public ShippingAddressSnapshot require(long snapshotId) {
        ShippingAddressSnapshot snapshot = mapper.selectById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_NOT_FOUND);
        }
        return snapshot;
    }

    private ShippingAddressSnapshot copy(
            long userId,
            long addressId,
            ShippingSourceType sourceType,
            String sourceId,
            UserShippingAddress address
    ) {
        ShippingAddressSnapshot snapshot = new ShippingAddressSnapshot();
        snapshot.setSnapshotNo("ADDRESS-" + UUID.randomUUID());
        snapshot.setUserId(userId);
        snapshot.setAddressId(addressId);
        snapshot.setSourceType(sourceType);
        snapshot.setSourceId(sourceId);
        snapshot.setReceiverCiphertext(address.getReceiverCiphertext());
        snapshot.setPhoneCiphertext(address.getPhoneCiphertext());
        snapshot.setProvinceCiphertext(address.getProvinceCiphertext());
        snapshot.setCityCiphertext(address.getCityCiphertext());
        snapshot.setDistrictCiphertext(address.getDistrictCiphertext());
        snapshot.setDetailCiphertext(address.getDetailCiphertext());
        snapshot.setReceiverMasked(address.getReceiverMasked());
        snapshot.setPhoneMasked(address.getPhoneMasked());
        snapshot.setRegionMasked(address.getRegionMasked());
        return snapshot;
    }

    private ShippingAddressSnapshot requireEqual(
            ShippingAddressSnapshot existing,
            ShippingAddressSnapshot candidate
    ) {
        if (!Objects.equals(existing.getUserId(), candidate.getUserId())
                || !Objects.equals(existing.getAddressId(), candidate.getAddressId())
                || existing.getSourceType() != candidate.getSourceType()
                || !Objects.equals(existing.getSourceId(), candidate.getSourceId())
                || !Objects.equals(existing.getReceiverCiphertext(), candidate.getReceiverCiphertext())
                || !Objects.equals(existing.getPhoneCiphertext(), candidate.getPhoneCiphertext())
                || !Objects.equals(existing.getProvinceCiphertext(), candidate.getProvinceCiphertext())
                || !Objects.equals(existing.getCityCiphertext(), candidate.getCityCiphertext())
                || !Objects.equals(existing.getDistrictCiphertext(), candidate.getDistrictCiphertext())
                || !Objects.equals(existing.getDetailCiphertext(), candidate.getDetailCiphertext())
                || !Objects.equals(existing.getReceiverMasked(), candidate.getReceiverMasked())
                || !Objects.equals(existing.getPhoneMasked(), candidate.getPhoneMasked())
                || !Objects.equals(existing.getRegionMasked(), candidate.getRegionMasked())) {
            throw new BusinessException(ShippingErrorCode.SHIPPING_IDEMPOTENCY_CONFLICT);
        }
        return existing;
    }
}
