package com.dongqh.luckyhub.inventory.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.inventory.channel.entity.InventoryChannelStock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ChannelInventoryMapper extends BaseMapper<InventoryChannelStock> {
    @Insert("""
            INSERT INTO inventory_channel_stock (
                sku_id, channel_code, allocated_stock, available_stock,
                reserved_stock, consumed_stock, version
            ) VALUES (#{skuId}, #{channelCode}, #{quantity}, #{quantity}, 0, 0, 0)
            ON DUPLICATE KEY UPDATE
                allocated_stock = allocated_stock + #{quantity},
                available_stock = available_stock + #{quantity},
                version = version + 1
            """)
    int addAllocation(@Param("skuId") long skuId, @Param("channelCode") String channelCode,
                      @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_channel_stock
            SET available_stock = available_stock - #{quantity},
                reserved_stock = reserved_stock + #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
              AND available_stock >= #{quantity}
            """)
    int reserveIfAvailable(@Param("skuId") long skuId, @Param("channelCode") String channelCode,
                           @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_channel_stock
            SET reserved_stock = reserved_stock - #{quantity},
                consumed_stock = consumed_stock + #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
              AND reserved_stock >= #{quantity}
            """)
    int confirmReserved(@Param("skuId") long skuId, @Param("channelCode") String channelCode,
                        @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_channel_stock
            SET reserved_stock = reserved_stock - #{quantity},
                available_stock = available_stock + #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
              AND reserved_stock >= #{quantity}
            """)
    int releaseReserved(@Param("skuId") long skuId, @Param("channelCode") String channelCode,
                        @Param("quantity") int quantity);

    @Update("""
            UPDATE inventory_channel_stock
            SET consumed_stock = consumed_stock - #{quantity},
                available_stock = available_stock + #{quantity},
                version = version + 1
            WHERE sku_id = #{skuId} AND channel_code = #{channelCode}
              AND consumed_stock >= #{quantity}
            """)
    int reverseConsumed(@Param("skuId") long skuId, @Param("channelCode") String channelCode,
                        @Param("quantity") int quantity);
}
