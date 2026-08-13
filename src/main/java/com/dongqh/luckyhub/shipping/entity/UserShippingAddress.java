package com.dongqh.luckyhub.shipping.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.dongqh.luckyhub.shipping.enums.AddressStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@TableName("user_shipping_address")
public class UserShippingAddress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String receiverCiphertext;
    private String phoneCiphertext;
    private String provinceCiphertext;
    private String cityCiphertext;
    private String districtCiphertext;
    private String detailCiphertext;
    private String receiverMasked;
    private String phoneMasked;
    private String regionMasked;
    private Integer isDefault;
    private AddressStatus status;
    @Version
    private Integer version;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
