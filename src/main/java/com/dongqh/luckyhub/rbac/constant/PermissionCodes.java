package com.dongqh.luckyhub.rbac.constant;

public final class PermissionCodes {

    public static final String USER_CREATE =
            "user:create";

    public static final String USER_READ =
            "user:read";

    public static final String ROLE_CREATE =
            "role:create";

    public static final String ROLE_READ =
            "role:read";

    public static final String PERMISSION_CREATE =
            "permission:create";

    public static final String PERMISSION_READ =
            "permission:read";

    public static final String USER_ROLE_ASSIGN =
            "user-role:assign";

    public static final String USER_ROLE_READ =
            "user-role:read";

    public static final String ROLE_PERMISSION_ASSIGN =
            "role-permission:assign";

    public static final String ROLE_PERMISSION_READ =
            "role-permission:read";

    public static final String USER_PERMISSION_READ =
            "user-permission:read";

    public static final String PRIZE_CREATE =
            "prize:create";

    public static final String PRIZE_READ =
            "prize:read";

    public static final String PRIZE_UPDATE =
            "prize:update";

    public static final String PRIZE_DISABLE =
            "prize:disable";

    public static final String PRIZE_IMAGE_UPLOAD =
            "prize:image:upload";

    public static final String ACTIVITY_CREATE =
            "activity:create";

    public static final String ACTIVITY_READ =
            "activity:read";

    public static final String ACTIVITY_UPDATE =
            "activity:update";

    public static final String ACTIVITY_PUBLISH =
            "activity:publish";

    public static final String ACTIVITY_DISABLE =
            "activity:disable";

    public static final String ACTIVITY_RESTORE =
            "activity:restore";

    public static final String ACTIVITY_PRIZE_MANAGE =
            "activity:prize:manage";

    public static final String LOTTERY_ACTIVITY_READ =
            "lottery:activity:read";

    public static final String LOTTERY_DRAW =
            "lottery:draw";

    public static final String LOTTERY_DRAW_READ =
            "lottery:draw:read";

    public static final String LOTTERY_RECORD_READ =
            "lottery:record:read";

    public static final String BENEFIT_READ =
            "benefit:read";

    public static final String LOTTERY_ORDER_READ_ALL =
            "lottery:order:read:all";

    public static final String LOTTERY_DRAW_READ_ALL =
            "lottery:draw:read:all";

    public static final String LOTTERY_RECORD_READ_ALL =
            "lottery:record:read:all";

    public static final String BENEFIT_READ_ALL =
            "benefit:read:all";

    public static final String CATALOG_READ =
            "catalog:read";

    public static final String CATALOG_MANAGE =
            "catalog:manage";

    public static final String REWARD_MANAGE =
            "reward:manage";

    public static final String INVENTORY_MANAGE =
            "inventory:manage";

    public static final String POINTS_READ =
            "points:read";

    public static final String POINTS_REDEEM =
            "points:redeem";

    public static final String POINTS_ADJUST =
            "points:adjust";

    public static final String COUPON_READ = "coupon:read";
    public static final String COUPON_MANAGE = "coupon:manage";
    public static final String MEMBERSHIP_READ = "membership:read";
    public static final String MEMBERSHIP_MANAGE = "membership:manage";
    public static final String ORDER_CREATE = "order:create";
    public static final String ORDER_READ = "order:read";
    public static final String ORDER_CANCEL = "order:cancel";
    public static final String PAYMENT_CREATE = "payment:create";
    public static final String PAYMENT_SIMULATE = "payment:simulate";
    public static final String FULFILLMENT_CREATE = "fulfillment:create";
    public static final String FULFILLMENT_READ = "fulfillment:read";
    public static final String FULFILLMENT_OPERATE = "fulfillment:operate";
    public static final String SIMULATOR_CONTROL = "simulator:control";

    private PermissionCodes() {
    }
}
