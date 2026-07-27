# 活动管理 API

## 1. 状态

数据库 `marketing_activity.status` 保存真实状态：

| 状态 | 含义 |
|---|---|
| `DRAFT` | 草稿，可以编辑和配置奖品 |
| `SCHEDULED` | 已发布，等待开始 |
| `RUNNING` | 正在进行 |
| `ENDED` | 已到结束时间 |
| `DISABLED` | 管理员禁用，只能查询或恢复 |

状态流转：

```text
DRAFT --发布--> SCHEDULED --到达开始时间--> RUNNING --到达结束时间--> ENDED
  |                   |                      |                    |
  +-------------------+----------------------+--------------------+--禁用--> DISABLED
                                                                            |
                                                                            +--恢复--> DRAFT
```

Spring 默认每 30 秒使用 MySQL `NOW(3)` 批量推进时间状态：

```properties
ACTIVITY_STATUS_REFRESH_INTERVAL=30000
ACTIVITY_STATUS_REFRESH_INITIAL_DELAY=0
```

## 2. 权限

| 权限 | 用途 |
|---|---|
| `activity:create` | 创建活动 |
| `activity:read` | 查询活动和活动奖品 |
| `activity:update` | 修改活动 |
| `activity:publish` | 发布活动 |
| `activity:disable` | 禁用活动 |
| `activity:restore` | 恢复为草稿 |
| `activity:prize:manage` | 添加、修改和移除活动奖品 |

Flyway V4 创建权限并授予 `ADMIN`。

## 3. 活动接口

| 方法 | 路径 | 权限 |
|---|---|---|
| `POST` | `/api/admin/activities` | `activity:create` |
| `GET` | `/api/admin/activities/{id}` | `activity:read` |
| `GET` | `/api/admin/activities` | `activity:read` |
| `PUT` | `/api/admin/activities/{id}` | `activity:update` |
| `PATCH` | `/api/admin/activities/{id}/publish` | `activity:publish` |
| `PATCH` | `/api/admin/activities/{id}/disable` | `activity:disable` |
| `PATCH` | `/api/admin/activities/{id}/restore` | `activity:restore` |

创建或修改请求：

```json
{
  "activityName": "八月幸运抽奖",
  "description": "八月会员抽奖活动",
  "startTime": "2026-08-01T10:00:00",
  "endTime": "2026-08-10T22:00:00",
  "dailyLimit": 3
}
```

分页查询示例：

```http
GET /api/admin/activities?name=八月&status=SCHEDULED&page=1&size=20
```

规则：

- `endTime` 必须晚于 `startTime`；
- 创建后固定为 `DRAFT`；
- `DRAFT`、`SCHEDULED` 和 `RUNNING` 允许修改；
- 修改已发布活动时，新的 `endTime` 必须晚于当前时间；
- `ENDED` 和 `DISABLED` 不允许普通修改；
- 只有 `DRAFT` 可以发布；
- `DISABLED` 恢复后变成 `DRAFT`；
- 重复禁用保持成功。

## 4. 活动奖品接口

| 方法 | 路径 | 权限 |
|---|---|---|
| `POST` | `/api/admin/activities/{activityId}/prizes` | `activity:prize:manage` |
| `GET` | `/api/admin/activities/{activityId}/prizes` | `activity:read` |
| `PUT` | `/api/admin/activities/{activityId}/prizes/{prizeId}` | `activity:prize:manage` |
| `DELETE` | `/api/admin/activities/{activityId}/prizes/{prizeId}` | `activity:prize:manage` |

添加：

```json
{
  "prizeId": 7,
  "weight": 20,
  "totalStock": 100,
  "sortOrder": 1
}
```

新增时：

```text
remainingStock = totalStock
```

修改：

```json
{
  "weight": 30,
  "totalStock": 150,
  "sortOrder": 2
}
```

修改总库存时保留已消耗数量：

```text
consumed = oldTotalStock - oldRemainingStock
newRemainingStock = newTotalStock - consumed
```

`DISABLED` 和 `ENDED` 活动不能配置奖品。只能关联当前启用的奖品。

## 5. 发布校验

发布前必须满足：

- 活动是 `DRAFT`；
- 结束时间晚于当前时间；
- 至少配置一个奖品；
- 所有关联奖品处于启用状态；
- 权重大于零；
- 库存满足 `0 <= remainingStock <= totalStock`；
- 至少一个奖品有剩余库存。

发布后：

```text
当前时间 < startTime  → SCHEDULED
当前时间 >= startTime → RUNNING
```

## 6. 错误码

| 错误码 | 含义 |
|---:|---|
| `42001` | 活动不存在 |
| `42002` | 活动时间范围非法 |
| `42003` | 当前状态不允许操作 |
| `42004` | 活动奖品不存在 |
| `42005` | 活动已关联该奖品 |
| `42006` | 活动没有配置奖品 |
| `42007` | 活动包含已禁用奖品 |
| `42008` | 活动没有可用奖品库存 |
| `42009` | 新总库存小于已消耗库存 |

