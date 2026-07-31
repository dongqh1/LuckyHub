package com.dongqh.luckyhub.benefit.service;
import com.dongqh.luckyhub.benefit.dto.BenefitQuery;
import com.dongqh.luckyhub.benefit.vo.BenefitView;
import com.dongqh.luckyhub.common.result.PageResponse;
public interface BenefitQueryService { PageResponse<BenefitView> page(BenefitQuery query); BenefitView getById(long id); }
