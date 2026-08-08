package com.dongqh.luckyhub.inventory.channel.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("inventory_channel_stock")
public class InventoryChannelStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String channelCode;
    private Integer allocatedStock;
    private Integer availableStock;
    private Integer reservedStock;
    private Integer consumedStock;
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
