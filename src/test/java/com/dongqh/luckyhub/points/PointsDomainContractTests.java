package com.dongqh.luckyhub.points;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqh.luckyhub.catalog.enums.ProductType;
import com.dongqh.luckyhub.shipping.vo.ShippingAddressSnapshotView;
import com.dongqh.luckyhub.common.enums.ErrorCode;
import com.dongqh.luckyhub.config.MybatisPlusConfig;
import com.dongqh.luckyhub.rbac.constant.PermissionCodes;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class PointsDomainContractTests {

    private static final String POINTS_PACKAGE = "com.dongqh.luckyhub.points.";

    @Test
    void exposesStablePointsEnumsAndErrors() {
        assertThat(enumNames("enums.PointsBusinessType")).containsExactly(
                "LOTTERY_REWARD", "ORDER_REWARD", "MEMBERSHIP_BONUS",
                "REDEMPTION", "REVERSAL", "MANUAL_ADJUSTMENT");
        assertThat(enumNames("enums.PointsDirection")).containsExactly("CREDIT", "DEBIT");
        assertThat(enumNames("enums.PointsRedemptionStatus"))
                .containsExactly("PROCESSING", "COMPLETED", "REVERSED");

        Map<String, ExpectedError> expected = new LinkedHashMap<>();
        expected.put("POINTS_INSUFFICIENT", new ExpectedError(47001, "积分余额不足", HttpStatus.CONFLICT));
        expected.put("POINTS_IDEMPOTENCY_CONFLICT", new ExpectedError(47002, "积分幂等参数冲突", HttpStatus.CONFLICT));
        expected.put("POINTS_LEDGER_NOT_FOUND", new ExpectedError(47003, "积分流水不存在", HttpStatus.NOT_FOUND));
        expected.put("POINTS_REVERSAL_CONFLICT", new ExpectedError(47004, "积分冲正状态冲突", HttpStatus.CONFLICT));
        expected.put("POINTS_AMOUNT_INVALID", new ExpectedError(47005, "积分数量不合法", HttpStatus.BAD_REQUEST));
        expected.put("REDEMPTION_NOT_FOUND", new ExpectedError(47006, "积分兑换单不存在", HttpStatus.NOT_FOUND));
        expected.put("REDEMPTION_SKU_UNAVAILABLE", new ExpectedError(47007, "SKU不可用于积分兑换", HttpStatus.BAD_REQUEST));
        expected.put("REDEMPTION_STATE_CONFLICT", new ExpectedError(47008, "积分兑换单状态冲突", HttpStatus.CONFLICT));
        expected.put("POINTS_USER_UNAVAILABLE", new ExpectedError(47009, "积分账户用户不存在或已禁用", HttpStatus.BAD_REQUEST));

        Class<?> errorClass = requiredClass("enums.PointsErrorCode");
        assertThat(errorClass.isEnum()).isTrue();
        assertThat(Arrays.stream(errorClass.getEnumConstants()).map(value -> ((Enum<?>) value).name()))
                .containsExactlyElementsOf(expected.keySet());
        for (Object value : errorClass.getEnumConstants()) {
            ErrorCode error = (ErrorCode) value;
            ExpectedError expectedError = expected.get(((Enum<?>) value).name());
            assertThat(error.code()).isEqualTo(expectedError.code());
            assertThat(error.message()).isEqualTo(expectedError.message());
            assertThat(error.httpStatus()).isEqualTo(expectedError.httpStatus());
        }
    }

    @Test
    void mapsV10EntitiesAndMappersExactly() throws Exception {
        assertEntity("entity.PointsAccount", "points_account", Map.ofEntries(
                Map.entry("id", Long.class),
                Map.entry("userId", Long.class),
                Map.entry("balance", Long.class),
                Map.entry("version", Integer.class),
                Map.entry("createdAt", LocalDateTime.class),
                Map.entry("updatedAt", LocalDateTime.class)));
        assertEntity("entity.PointsLedger", "points_ledger", Map.ofEntries(
                Map.entry("id", Long.class),
                Map.entry("userId", Long.class),
                Map.entry("businessType", requiredClass("enums.PointsBusinessType")),
                Map.entry("businessId", String.class),
                Map.entry("direction", requiredClass("enums.PointsDirection")),
                Map.entry("amount", Long.class),
                Map.entry("balanceAfter", Long.class),
                Map.entry("reversalOfLedgerId", Long.class),
                Map.entry("remark", String.class),
                Map.entry("createdAt", LocalDateTime.class)));
        assertEntity("entity.PointsRedemptionOrder", "points_redemption_order", Map.ofEntries(
                Map.entry("id", Long.class),
                Map.entry("redemptionNo", String.class),
                Map.entry("userId", Long.class),
                Map.entry("skuId", Long.class),
                Map.entry("quantity", Integer.class),
                Map.entry("unitPoints", Long.class),
                Map.entry("totalPoints", Long.class),
                Map.entry("productCode", String.class),
                Map.entry("productName", String.class),
                Map.entry("skuCode", String.class),
                Map.entry("skuName", String.class),
                Map.entry("productType", ProductType.class),
                Map.entry("imageUrl", String.class),
                Map.entry("status", requiredClass("enums.PointsRedemptionStatus")),
                Map.entry("reversalNo", String.class),
                Map.entry("failureReason", String.class),
                Map.entry("addressSnapshotId", Long.class),
                Map.entry("shippingOrderId", Long.class),
                Map.entry("createdAt", LocalDateTime.class),
                Map.entry("updatedAt", LocalDateTime.class)));

        assertMapper("mapper.PointsAccountMapper", "entity.PointsAccount");
        assertMapper("mapper.PointsLedgerMapper", "entity.PointsLedger");
        assertMapper("mapper.PointsRedemptionOrderMapper", "entity.PointsRedemptionOrder");

        MapperScan mapperScan = MybatisPlusConfig.class.getAnnotation(MapperScan.class);
        assertThat(mapperScan.basePackages()).contains("com.dongqh.luckyhub.points.mapper");
    }

    @Test
    void definesPermissionsCommandsAndValidatedQueries() throws Exception {
        assertThat(permission("POINTS_READ")).isEqualTo("points:read");
        assertThat(permission("POINTS_REDEEM")).isEqualTo("points:redeem");
        assertThat(permission("POINTS_ADJUST")).isEqualTo("points:adjust");

        assertRecord("dto.PointsMutationCommand", new String[]{
                "userId", "businessType", "businessId", "amount", "remark"
        }, new Class<?>[]{
                Long.class, requiredClass("enums.PointsBusinessType"), String.class, Long.class, String.class
        });
        assertRecord("dto.PointsReversalCommand", new String[]{
                "userId", "originalBusinessType", "originalBusinessId", "reversalBusinessId", "remark"
        }, new Class<?>[]{
                Long.class, requiredClass("enums.PointsBusinessType"), String.class, String.class, String.class
        });
        assertRecord("dto.AdminPointsAdjustmentCommand", new String[]{
                "userId", "delta", "businessId", "reason"
        }, new Class<?>[]{Long.class, Long.class, String.class, String.class});
        assertAdminAdjustmentValidation();

        assertRecord("dto.CreatePointsRedemptionCommand", new String[]{
                "redemptionNo", "skuId", "quantity", "addressId"
        }, new Class<?>[]{String.class, Long.class, Integer.class, Long.class});
        assertRecord("dto.ReversePointsRedemptionCommand", new String[]{
                "reversalNo", "reason"
        }, new Class<?>[]{String.class, String.class});

        assertQuery("dto.PointsLedgerQuery", Map.ofEntries(
                Map.entry("page", long.class),
                Map.entry("size", long.class),
                Map.entry("businessId", String.class),
                Map.entry("businessType", requiredClass("enums.PointsBusinessType")),
                Map.entry("direction", requiredClass("enums.PointsDirection"))));
        assertQuery("dto.PointsRedemptionQuery", Map.ofEntries(
                Map.entry("page", long.class),
                Map.entry("size", long.class),
                Map.entry("status", requiredClass("enums.PointsRedemptionStatus"))));
    }

    @Test
    void exposesImmutablePointsViews() {
        assertRecord("vo.PointsAccountView",
                new String[]{"userId", "balance", "updatedAt"},
                new Class<?>[]{Long.class, Long.class, LocalDateTime.class});
        assertRecord("vo.PointsLedgerView", new String[]{
                "id", "userId", "businessType", "businessId", "direction", "amount",
                "balanceAfter", "reversalOfLedgerId", "remark", "createdAt"
        }, new Class<?>[]{
                Long.class, Long.class, requiredClass("enums.PointsBusinessType"), String.class,
                requiredClass("enums.PointsDirection"), Long.class, Long.class, Long.class,
                String.class, LocalDateTime.class
        });
        assertRecord("vo.PointsRedemptionView", new String[]{
                "id", "redemptionNo", "userId", "skuId", "quantity", "unitPoints", "totalPoints",
                "productCode", "productName", "skuCode", "skuName", "productType", "imageUrl",
                "status", "reversalNo", "failureReason", "addressSnapshot", "shippingOrderId",
                "createdAt", "updatedAt"
        }, new Class<?>[]{
                Long.class, String.class, Long.class, Long.class, Integer.class, Long.class, Long.class,
                String.class, String.class, String.class, String.class, ProductType.class, String.class,
                requiredClass("enums.PointsRedemptionStatus"), String.class, String.class,
                ShippingAddressSnapshotView.class, Long.class,
                LocalDateTime.class, LocalDateTime.class
        });
    }

    private List<String> enumNames(String relativeName) {
        Class<?> enumClass = requiredClass(relativeName);
        assertThat(enumClass.isEnum()).as(relativeName + " must be an enum").isTrue();
        return Arrays.stream(enumClass.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .toList();
    }

    private void assertEntity(String relativeName, String tableName, Map<String, Class<?>> fields)
            throws Exception {
        Class<?> entityClass = requiredClass(relativeName);
        TableName tableNameAnnotation = entityClass.getAnnotation(TableName.class);
        assertThat(tableNameAnnotation).as(relativeName + " must declare @TableName").isNotNull();
        assertThat(tableNameAnnotation.value()).isEqualTo(tableName);
        TableId tableId = entityClass.getDeclaredField("id").getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.AUTO);
        for (Map.Entry<String, Class<?>> field : fields.entrySet()) {
            assertThat(entityClass.getDeclaredField(field.getKey()).getType())
                    .as(relativeName + "." + field.getKey())
                    .isEqualTo(field.getValue());
        }
    }

    private void assertMapper(String mapperName, String entityName) {
        Class<?> mapperClass = requiredClass(mapperName);
        Class<?> entityClass = requiredClass(entityName);
        assertThat(mapperClass.isInterface()).isTrue();
        assertThat(Arrays.stream(mapperClass.getGenericInterfaces()).anyMatch(type ->
                type instanceof ParameterizedType parameterized
                        && parameterized.getRawType().equals(BaseMapper.class)
                        && parameterized.getActualTypeArguments()[0].equals(entityClass)))
                .as(mapperName + " must extend BaseMapper<" + entityName + ">")
                .isTrue();
    }

    private String permission(String fieldName) throws Exception {
        try {
            return (String) PermissionCodes.class.getField(fieldName).get(null);
        } catch (NoSuchFieldException exception) {
            return fail("缺少积分权限常量: PermissionCodes." + fieldName, exception);
        }
    }

    private void assertRecord(String relativeName, String[] names, Class<?>[] types) {
        Class<?> recordClass = requiredClass(relativeName);
        assertThat(recordClass.isRecord()).as(relativeName + " must be a record").isTrue();
        RecordComponent[] components = recordClass.getRecordComponents();
        assertThat(Arrays.stream(components).map(RecordComponent::getName)).containsExactly(names);
        assertThat(Arrays.stream(components).map(RecordComponent::getType)).containsExactly(types);
    }

    private void assertAdminAdjustmentValidation() throws Exception {
        Class<?> commandClass = requiredClass("dto.AdminPointsAdjustmentCommand");
        var userIdAccessor = commandClass.getDeclaredMethod("userId");
        var deltaAccessor = commandClass.getDeclaredMethod("delta");
        var businessIdAccessor = commandClass.getDeclaredMethod("businessId");
        var reasonAccessor = commandClass.getDeclaredMethod("reason");
        assertThat(userIdAccessor.getAnnotation(NotNull.class)).isNotNull();
        assertThat(userIdAccessor.getAnnotation(Positive.class)).isNotNull();
        assertThat(deltaAccessor.getAnnotation(NotNull.class)).isNotNull();
        assertThat(businessIdAccessor.getAnnotation(NotBlank.class)).isNotNull();
        assertThat(businessIdAccessor.getAnnotation(Size.class).max()).isEqualTo(100);
        assertThat(reasonAccessor.getAnnotation(NotBlank.class)).isNotNull();
        assertThat(reasonAccessor.getAnnotation(Size.class).max()).isEqualTo(500);

        Constructor<?> constructor = commandClass.getDeclaredConstructor(
                Long.class, Long.class, String.class, String.class);
        assertThatThrownBy(() -> constructor.newInstance(1L, 0L, "ADJUST-1", "测试"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> constructor.newInstance(1L, Long.MIN_VALUE, "ADJUST-2", "测试"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    private void assertQuery(String relativeName, Map<String, Class<?>> fields) throws Exception {
        Class<?> queryClass = requiredClass(relativeName);
        Object query = queryClass.getDeclaredConstructor().newInstance();
        for (Map.Entry<String, Class<?>> expected : fields.entrySet()) {
            Field field = queryClass.getDeclaredField(expected.getKey());
            assertThat(field.getType()).isEqualTo(expected.getValue());
        }
        Field page = queryClass.getDeclaredField("page");
        Field size = queryClass.getDeclaredField("size");
        page.setAccessible(true);
        size.setAccessible(true);
        assertThat(page.getLong(query)).isEqualTo(1L);
        assertThat(size.getLong(query)).isEqualTo(20L);
        assertThat(page.getAnnotation(Min.class).value()).isEqualTo(1L);
        assertThat(size.getAnnotation(Min.class).value()).isEqualTo(1L);
        assertThat(size.getAnnotation(Max.class).value()).isEqualTo(100L);
    }

    private Class<?> requiredClass(String relativeName) {
        try {
            return Class.forName(POINTS_PACKAGE + relativeName);
        } catch (ClassNotFoundException exception) {
            return fail("缺少积分领域类型: " + POINTS_PACKAGE + relativeName, exception);
        }
    }

    private record ExpectedError(int code, String message, HttpStatus httpStatus) {
    }
}
