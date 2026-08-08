package com.dongqh.luckyhub.inventory.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.inventory.channel.entity.InventoryLedger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface InventoryLedgerMapper extends BaseMapper<InventoryLedger> {
    @Insert("""
            INSERT IGNORE INTO inventory_ledger (
                business_no, sku_id, channel_code, operation, quantity, created_at
            ) VALUES (
                #{ledger.businessNo}, #{ledger.skuId}, #{ledger.channelCode},
                #{ledger.operation}, #{ledger.quantity}, CURRENT_TIMESTAMP(3)
            )
            """)
    int claim(@Param("ledger") InventoryLedger ledger);
}
