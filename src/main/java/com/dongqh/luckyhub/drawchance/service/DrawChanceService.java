package com.dongqh.luckyhub.drawchance.service;

import com.dongqh.luckyhub.drawchance.dto.DrawChanceReservationCommand;
import com.dongqh.luckyhub.drawchance.model.DrawChanceReservationResult;
import com.dongqh.luckyhub.drawchance.vo.DrawChanceAccountView;
import java.time.LocalDateTime;

public interface DrawChanceService {
    DrawChanceAccountView credit(long userId, String businessId, long chances);
    DrawChanceReservationResult reserve(DrawChanceReservationCommand command);
    void confirm(String requestId);
    void release(String requestId);
    int reconcileExpired(int limit, LocalDateTime cutoff);
    DrawChanceAccountView get(long userId);
}
