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
               OR (status = 'PROCESSING' AND next_retry_at <= #{now})
            ORDER BY id
            LIMIT #{limit}
            """)
    List<MessageOutbox> selectRelayCandidates(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE message_outbox
            SET status = 'PROCESSING', claim_token = #{claimToken}, next_retry_at = #{leaseUntil}
            WHERE id = #{id}
              AND (status = 'PENDING'
                OR (status = 'FAILED' AND (next_retry_at IS NULL OR next_retry_at <= #{now}))
                OR (status = 'PROCESSING' AND next_retry_at <= #{now}))
            """)
    int claimForRelay(@Param("id") long id,
                      @Param("claimToken") String claimToken,
                      @Param("now") LocalDateTime now,
                      @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE message_outbox
            SET status = 'SENT', sent_at = #{sentAt}, last_error = NULL,
                claim_token = NULL, next_retry_at = NULL
            WHERE id = #{id} AND status = 'PROCESSING' AND claim_token = #{claimToken}
            """)
    int markSent(@Param("id") long id,
                 @Param("claimToken") String claimToken,
                 @Param("sentAt") LocalDateTime sentAt);

    @Update("""
            UPDATE message_outbox
            SET status = 'FAILED', retry_count = retry_count + 1,
                last_error = #{error}, claim_token = NULL, next_retry_at = #{nextRetryAt}
            WHERE id = #{id} AND status = 'PROCESSING' AND claim_token = #{claimToken}
            """)
    int markFailed(@Param("id") long id,
                   @Param("claimToken") String claimToken,
                   @Param("error") String error,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt);
}
