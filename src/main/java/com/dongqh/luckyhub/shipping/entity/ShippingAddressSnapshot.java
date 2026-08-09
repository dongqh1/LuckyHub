package com.dongqh.luckyhub.shipping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.dongqh.luckyhub.shipping.enums.ShippingSourceType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("shipping_address_snapshot")
public class ShippingAddressSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotNo;
    private Long userId;
    private Long addressId;
    private ShippingSourceType sourceType;
    private String sourceId;
    private String receiverCiphertext;
    private String phoneCiphertext;
    private String provinceCiphertext;
    private String cityCiphertext;
    private String districtCiphertext;
    private String detailCiphertext;
    private String receiverMasked;
    private String phoneMasked;
    private String regionMasked;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
