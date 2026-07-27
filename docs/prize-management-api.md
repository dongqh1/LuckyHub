# 奖品管理与阿里云 OSS 配置

## 你需要填写的配置

在项目根目录的 `.env` 中填写以下内容：

```properties
OSS_ENABLED=true
OSS_REGION=cn-hangzhou
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=你的公开奖品图片Bucket名称
OSS_ACCESS_KEY_ID=你的RAM用户AccessKey ID
OSS_ACCESS_KEY_SECRET=你的RAM用户AccessKey Secret
OSS_PUBLIC_BASE_URL=https://你的Bucket名称.oss-cn-hangzhou.aliyuncs.com
```

需要按实际情况修改：

- `OSS_REGION`：Bucket 所在地域，例如杭州是 `cn-hangzhou`。
- `OSS_ENDPOINT`：该地域的公网 Endpoint。
- `OSS_BUCKET`：用于奖品图片的 Bucket 名称。
- `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`：RAM 用户密钥，不要提交到 Git。
- `OSS_PUBLIC_BASE_URL`：浏览器访问图片时使用的公开根地址。可以使用 Bucket 公网域名，也可以使用已绑定的自定义域名。
- `OSS_ENABLED`：配置完成后改为 `true`；未配置时保留 `false`，其他奖品接口仍可使用。

阿里云侧还需要完成：

1. 创建专用的奖品图片 Bucket。
2. 允许公开读取该 Bucket 中的奖品图片。
3. 给 RAM 用户授予目标 Bucket 下 `prizes/*` 对象的上传权限。
4. 不要使用阿里云主账号 AccessKey。

## 推荐调用顺序

### 1. 上传图片

```http
POST /api/admin/prize-images
Content-Type: multipart/form-data
Authorization: Bearer <token>

file=<JPEG、PNG 或 WebP 文件>
```

限制：

- 最大 5 MiB；
- 只支持 JPEG、PNG、WebP；
- 后端同时验证 `Content-Type` 和文件头；
- OSS 对象路径格式为 `prizes/yyyy/MM/UUID.ext`。

成功响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "https://bucket.example.com/prizes/2026/07/uuid.png",
    "objectKey": "prizes/2026/07/uuid.png"
  }
}
```

### 2. 创建奖品并保存公开 URL

将上传接口返回的 `data.url` 放进 `imageUrl`：

```http
POST /api/admin/prizes
Content-Type: application/json
Authorization: Bearer <token>

{
  "prizeName": "一等奖咖啡券",
  "prizeType": "COUPON",
  "prizeLevel": "FIRST",
  "imageUrl": "https://bucket.example.com/prizes/2026/07/uuid.png",
  "description": "可兑换一杯咖啡",
  "stackable": false
}
```

`imageUrl` 会保存到 MySQL 的 `marketing_prize.image_url`。

## 奖品管理接口

| 方法 | 地址 | 说明 | 权限 |
|---|---|---|---|
| `POST` | `/api/admin/prize-images` | 上传奖品图片 | `prize:image:upload` |
| `POST` | `/api/admin/prizes` | 创建奖品 | `prize:create` |
| `GET` | `/api/admin/prizes/{id}` | 查询奖品详情 | `prize:read` |
| `GET` | `/api/admin/prizes` | 分页查询奖品 | `prize:read` |
| `PUT` | `/api/admin/prizes/{id}` | 修改奖品 | `prize:update` |
| `PATCH` | `/api/admin/prizes/{id}/disable` | 禁用奖品 | `prize:disable` |

分页查询参数：

- `page`：页码，默认 `1`；
- `size`：每页数量，默认 `20`，最大 `100`；
- `name`：按奖品名称模糊查询；
- `type`：按奖品类型精确查询；
- `status`：`1` 为启用，`0` 为禁用。

奖品类型：

- `COUPON`：优惠券；
- `POINTS`：积分；
- `MEMBERSHIP`：会员权益；
- `PHYSICAL`：实物奖品。

奖品等级：

- `FIRST`：一等奖；
- `SECOND`：二等奖；
- `THIRD`：三等奖；
- `CONSOLATION`：安慰奖。

禁用接口是幂等的。重复禁用已禁用奖品仍返回成功，系统不会物理删除奖品记录。

## 错误码

| 错误码 | 含义 |
|---|---|
| `41001` | 奖品不存在 |
| `41002` | 图片为空 |
| `41003` | 图片类型不支持或文件头不匹配 |
| `41004` | 图片超过 5 MiB |
| `51001` | OSS 上传失败 |
| `51002` | OSS 未启用或配置不完整 |

本阶段不会自动删除被替换的旧图片，也不会清理“上传成功但尚未创建奖品”的孤立图片。
