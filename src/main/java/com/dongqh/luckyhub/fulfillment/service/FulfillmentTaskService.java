package com.dongqh.luckyhub.fulfillment.service;
import com.dongqh.luckyhub.common.result.PageResponse; import com.dongqh.luckyhub.fulfillment.dto.*; import com.dongqh.luckyhub.fulfillment.vo.FulfillmentTaskView;
public interface FulfillmentTaskService {FulfillmentTaskView create(CreateFulfillmentTaskCommand command);FulfillmentTaskView get(String fulfillmentNo);PageResponse<FulfillmentTaskView> page(FulfillmentTaskQuery query);}
