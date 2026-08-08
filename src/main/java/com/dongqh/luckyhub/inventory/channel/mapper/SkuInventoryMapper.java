package com.dongqh.luckyhub.inventory.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.inventory.channel.entity.SkuInventory;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SkuInventoryMapper extends BaseMapper<SkuInventory> {
    @Update("""
            UPDATE sku_inventory
            SET allocated_stock = allocated_stock + #{quantity}, version = version + 1
            WHERE sku_id = #{skuId}
              AND total_stock - allocated_stock >= #{quantity}
            """)
    int allocateIfAvailable(@Param("skuId") long skuId, @Param("quantity") int quantity);
}
