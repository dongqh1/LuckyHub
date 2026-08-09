package com.dongqh.luckyhub.drawchance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.drawchance.entity.DrawChanceReservation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface DrawChanceReservationMapper extends BaseMapper<DrawChanceReservation> {
    @Insert("""
            INSERT IGNORE INTO draw_chance_reservation(request_id,user_id,activity_id,draw_date,draw_count,bonus_reserved,status)
            VALUES(#{r.requestId},#{r.userId},#{r.activityId},#{r.drawDate},#{r.drawCount},0,'RESERVED')
            """)
    int insertIfAbsent(@Param("r") DrawChanceReservation reservation);

    @Select("SELECT * FROM draw_chance_reservation WHERE request_id=#{requestId} FOR UPDATE")
    DrawChanceReservation lockByRequestId(@Param("requestId") String requestId);

    @Update("UPDATE draw_chance_reservation SET bonus_reserved=#{bonus} WHERE id=#{id} AND status='RESERVED'")
    int setBonus(@Param("id") long id, @Param("bonus") long bonus);

    @Update("UPDATE draw_chance_reservation SET status=#{status},settled_at=CURRENT_TIMESTAMP(3) WHERE id=#{id} AND status='RESERVED'")
    int settle(@Param("id") long id,
               @Param("status") com.dongqh.luckyhub.drawchance.enums.DrawChanceReservationStatus status);

    @Select("""
            SELECT COALESCE(SUM(bonus_reserved),0) FROM draw_chance_reservation
            WHERE user_id=#{userId} AND activity_id=#{activityId} AND draw_date=#{drawDate}
              AND status IN ('RESERVED','CONFIRMED')
            """)
    long sumActiveBonus(@Param("userId") long userId, @Param("activityId") long activityId,
                        @Param("drawDate") LocalDate drawDate);

    @Select("""
            SELECT * FROM draw_chance_reservation
            WHERE status='RESERVED' AND created_at < #{cutoff}
            ORDER BY created_at,id LIMIT #{limit} FOR UPDATE SKIP LOCKED
            """)
    List<DrawChanceReservation> lockExpired(@Param("limit") int limit,
                                            @Param("cutoff") LocalDateTime cutoff);
}
