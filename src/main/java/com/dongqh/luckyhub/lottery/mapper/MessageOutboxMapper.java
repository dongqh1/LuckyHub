package com.dongqh.luckyhub.lottery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.lottery.entity.MessageOutbox;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {

    @Select("""
            SELECT *
            FROM message_outbox
            WHERE status = 'PENDING'
               OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<MessageOutbox> lockRelayBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE message_outbox
            SET status = 'SENT', sent_at = #{sentAt}, last_error = NULL, next_retry_at = NULL
            WHERE id = #{id} AND status IN ('PENDING', 'FAILED')
            """)
    int markSent(@Param("id") long id, @Param("sentAt") LocalDateTime sentAt);

    @Update("""
            UPDATE message_outbox
            SET status = 'FAILED', retry_count = retry_count + 1,
                last_error = #{error}, next_retry_at = #{nextRetryAt}
            WHERE id = #{id} AND status IN ('PENDING', 'FAILED')
            """)
    int markFailed(@Param("id") long id,
                   @Param("error") String error,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);
}
