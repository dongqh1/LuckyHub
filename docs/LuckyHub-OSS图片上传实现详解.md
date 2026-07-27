# LuckyHub OSS 图片上传：从一个 POST 请求开始

> 这不是一份“类名说明书”，而是一堂按程序执行顺序展开的课。
>
> 我们只跟踪一件事：管理员使用 Postman 发送
> `POST /api/admin/prize-images` 后，图片怎样一步一步进入阿里云 OSS，最后怎样把公开 URL 保存到奖品表。

---

## 0. 先记住最终流程

一次完整操作实际上包含两个请求：

```text
第一个请求：上传图片
POST /api/admin/prize-images
        ↓
校验图片
        ↓
上传到 OSS
        ↓
返回公开 URL

第二个请求：创建奖品
POST /api/admin/prizes
请求 JSON 中携带 imageUrl
        ↓
把 URL 保存到 marketing_prize.image_url
```

为什么分成两个请求？

- 图片是二进制文件，适合使用 `multipart/form-data`。
- 奖品名称、类型、等级、图片 URL 等是结构化数据，适合使用 JSON。
- 图片上传成功后，前端可以立刻预览。
- 创建和修改奖品可以共用同一个图片 URL。

本章先完整跟踪第一个请求，最后再讲第二个请求怎样保存 URL。

---

# 第一段：请求是怎样进入后端的

## 1. 从 Postman 发出请求

假设我们要上传一个真正的 PNG 文件：

```text
D:\prize.png
```

Postman 配置如下：

```text
Method: POST
URL: http://localhost:8080/api/admin/prize-images

Headers:
Authorization: Bearer 你的登录Token

Body:
form-data

Key:  file
Type: File
Value: D:\prize.png
```

不要自己填写 `Content-Type: application/json`。

选择 `form-data` 后，Postman 会自动生成类似下面的请求：

```http
POST /api/admin/prize-images HTTP/1.1
Authorization: Bearer eyJ...
Content-Type: multipart/form-data; boundary=----PostmanBoundary

------PostmanBoundary
Content-Disposition: form-data; name="file"; filename="prize.png"
Content-Type: image/png

这里是真正的图片二进制字节
------PostmanBoundary--
```

### 现在遇到的第一个问题

普通 JSON 只能很方便地表示文字、数字、布尔值和对象，不能直接承载原始图片字节。

因此我们需要一种“一个 HTTP 请求里可以放多个部分”的格式，这就是：

```text
multipart/form-data
```

`multipart` 的意思是“多个部分”。每一部分都可以有自己的名称、文件名、类型和内容。

本项目只有一个部分：

```text
部分名称：file
文件名称：prize.png
声明类型：image/png
实际内容：图片字节
```

这里的 `file` 非常重要。它必须和后端的：

```java
@RequestPart("file")
```

完全一致，否则 Spring 找不到这一部分。

---

## 2. Spring 先把 HTTP 文件转换成 MultipartFile

请求到达 Tomcat 后，Spring 会解析 `multipart/form-data`，并把名为 `file` 的部分包装成：

```java
MultipartFile
```

我们没有自己编写 HTTP 报文解析器，因为 Spring 已经提供了这个工具。

`MultipartFile` 可以理解成“Spring 对上传文件的统一包装”。常用方法如下：

```java
file.isEmpty()          // 文件是否为空
file.getSize()          // 文件大小，单位是字节
file.getContentType()   // 请求声明的 MIME 类型，例如 image/png
file.getOriginalFilename() // 客户端传来的原文件名
file.getBytes()         // 读取真正的文件字节
```

注意：

```text
getOriginalFilename() 和 getContentType() 都来自客户端，不能完全相信。
```

用户可以把 `abc.exe` 改名为 `abc.png`，也可以伪造 `Content-Type: image/png`。所以稍后必须检查文件的真实字节。

### 为什么配置 6MB，而业务限制是 5MiB

项目中有：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 6MB
      max-request-size: 6MB
```

这是 Spring 接收请求的外层限制。如果请求超过 6MB，它还没有进入我们的 Controller，就会被 Spring 拒绝。

业务校验器中还有：

```java
static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
```

这是我们自己的业务规则：图片最多 5MiB。

为什么外层是 6MB、业务层是 5MiB？

因为 multipart 请求除了图片本身，还有边界、请求头等少量额外内容。外层稍微放宽，确保 5MiB 图片能进入业务代码，然后由业务代码返回统一的错误信息。

---

## 3. 在 Controller 之前：先验证身份和权限

请求虽然到达应用，但不能让任何人都上传图片。

现在缺少两项判断：

```text
1. 这个请求是谁发的？
2. 这个人有没有上传奖品图片的权限？
```

所以请求会先经过：

```text
AuthenticationFilter
        ↓
PermissionInterceptor
        ↓
PrizeImageController
```

### 3.1 AuthenticationFilter：确认“你是谁”

请求头中有：

```http
Authorization: Bearer 你的Token
```

`AuthenticationFilter` 会读取并验证这个 Token。验证通过后，系统才知道当前用户是谁。

没有 Token、Token 过期或 Token 无效时，请求不会进入上传 Controller。

### 3.2 PermissionInterceptor：确认“你能做什么”

上传方法上写了：

```java
@RequirePermission(PermissionCodes.PRIZE_IMAGE_UPLOAD)
```

常量的实际值是：

```java
public static final String PRIZE_IMAGE_UPLOAD = "prize:image:upload";
```

拦截器会检查当前用户是否拥有：

```text
prize:image:upload
```

数据库迁移 `V3__add_prize_management_permissions.sql` 创建了这个权限，并把它授予管理员角色。

这两个组件的职责不同：

```text
AuthenticationFilter：验证身份
PermissionInterceptor：验证权限
```

身份验证通过，不代表一定有上传权限。

---

# 第二段：Controller 接住请求

## 4. 为什么需要 PrizeImageController

身份和权限通过后，Spring 要找到一个 Java 方法处理：

```text
POST /api/admin/prize-images
```

现在缺少的是：

```text
HTTP 请求与 Java 方法之间的入口。
```

所以我们编写了 `PrizeImageController`：

```java
@RestController
@RequestMapping("/api/admin/prize-images")
public class PrizeImageController {

    private final PrizeImageService service;

    public PrizeImageController(PrizeImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(PermissionCodes.PRIZE_IMAGE_UPLOAD)
    public ApiResponse<ImageUploadView> upload(
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(service.upload(file));
    }
}
```

下面按执行顺序解释。

### 4.1 `@RestController`

```java
@RestController
```

告诉 Spring：

```text
这个类负责处理 HTTP 请求；
方法返回的 Java 对象要转换成 JSON。
```

如果方法返回 `ImageUploadView`，Spring 会使用 Jackson 把它转换为 JSON，而不是去寻找一个 HTML 页面。

### 4.2 `@RequestMapping`

```java
@RequestMapping("/api/admin/prize-images")
```

定义这个类下面所有接口共同的路径。

### 4.3 `@PostMapping`

```java
@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
```

表示：

```text
只接收 POST 请求；
请求类型必须是 multipart/form-data。
```

类路径和方法路径合并后得到：

```text
POST /api/admin/prize-images
```

### 4.4 `@RequestPart("file")`

```java
@RequestPart("file") MultipartFile file
```

告诉 Spring：

```text
请从 multipart 请求中找到名字为 file 的部分，
并转换成 MultipartFile 后传给这个参数。
```

这就是为什么 Postman 的 Key 必须填写 `file`。

### 4.5 `@ResponseStatus(HttpStatus.CREATED)`

上传成功后返回：

```text
HTTP 201 Created
```

201 表示服务器成功创建了一个新资源。这里的新资源就是 OSS 中的新对象。

### 4.6 构造器注入

Controller 不自己创建 Service：

```java
private final PrizeImageService service;

public PrizeImageController(PrizeImageService service) {
    this.service = service;
}
```

Spring 启动时已经创建了 `PrizeImageService`，然后通过构造器交给 Controller。

这叫“依赖注入”：

```text
PrizeImageController 需要 PrizeImageService，
但不负责 new PrizeImageService(...)。
```

优点是 Controller 更容易测试，也不用知道 Service 还有哪些依赖。

### 4.7 Controller 为什么只有一行

```java
return ApiResponse.success(service.upload(file));
```

Controller 的职责只是：

```text
接收 HTTP 输入 → 调用业务逻辑 → 包装 HTTP 输出
```

它不应该负责识别 PNG、不应该生成 OSS 路径，也不应该直接调用阿里云。

如果把所有逻辑都塞进 Controller，以后命令行任务或其他接口想复用上传能力时，就只能复制代码。

因此，下一步把 `MultipartFile` 交给：

```text
PrizeImageService.upload(file)
```

---

# 第三段：Service 组织整个上传流程

## 5. 为什么需要 PrizeImageService

现在 Controller 已经拿到文件，但还缺少一个对象来安排整个业务步骤：

```text
1. 校验文件
2. 生成 OSS Object Key
3. 上传 OSS
4. 生成公开 URL
5. 返回结果
```

这个“流程编排者”就是 `PrizeImageService`。

它的核心方法是：

```java
public ImageUploadView upload(MultipartFile file) {
    ValidatedImage image = validator.validate(file);

    String objectKey = "prizes/%s/%s.%s".formatted(
            LocalDate.now(clock).format(PATH_DATE),
            uuidSupplier.get(),
            image.extension()
    );

    gateway.put(objectKey, image.content(), image.contentType());

    String url = properties.normalizedPublicBaseUrl()
            + "/"
            + objectKey;

    return new ImageUploadView(url, objectKey);
}
```

不要急着一次理解完。接下来跟着程序一行一行执行。

---

# 第四段：上传前先验证文件

## 6. 第一行：`validator.validate(file)`

Service 执行的第一行是：

```java
ValidatedImage image = validator.validate(file);
```

### 现在缺少什么

`MultipartFile` 只是 Spring 收到的文件，它保存的是客户端提供的信息。

我们还不知道：

- 文件是不是空的；
- 文件是否超过 5MiB；
- 文件字节到底是不是图片；
- 它是真 PNG、JPEG 还是 WebP；
- 客户端声明的类型和真实类型是否一致。

如果不校验就上传，会出现：

- 非图片文件进入 OSS；
- 攻击者上传伪装文件；
- 超大文件消耗带宽和存储；
- 扩展名和真实内容不一致；
- 浏览器拿到错误的 `Content-Type`。

所以我们单独编写：

```text
PrizeImageValidator
```

它的工作只有一个：

```text
把“不可信的 MultipartFile”
转换为“已经验证过的 ValidatedImage”。
```

---

## 7. 校验第一步：文件不能为空

```java
if (file == null || file.isEmpty()) {
    throw new BusinessException(PrizeErrorCode.IMAGE_EMPTY);
}
```

逐项解释：

```text
file == null
```

表示根本没有拿到文件对象。

```text
file.isEmpty()
```

表示对象存在，但没有实际内容。

失败后抛出：

```java
new BusinessException(PrizeErrorCode.IMAGE_EMPTY)
```

`BusinessException` 是项目统一的业务异常。全局异常处理器会把它转换成结构一致的错误 JSON。

`IMAGE_EMPTY` 对应业务错误码 `41002`。

这样 Controller 不需要在每个方法中重复编写 `try/catch`。

---

## 8. 校验第二步：文件不能超过 5MiB

```java
static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
```

这里使用 `long`，因为文件大小可能超过 `int` 的安全范围。

计算过程：

```text
5 × 1024 × 1024 = 5,242,880 字节
```

第一次判断：

```java
if (file.getSize() > MAX_IMAGE_BYTES) {
    throw new BusinessException(PrizeErrorCode.IMAGE_TOO_LARGE);
}
```

`getSize()` 可以在读取全部内容之前判断大小。这样超大文件不会先被完整放入我们的 `byte[]`。

随后读取字节：

```java
byte[] content;
try {
    content = file.getBytes();
} catch (IOException exception) {
    throw new BusinessException(PrizeErrorCode.OSS_UPLOAD_FAILED);
}
```

为什么要转为 `byte[]`？

因为后面要：

- 检查文件头；
- 把相同的已验证字节传给 OSS SDK。

读取后又检查一次：

```java
if (content.length > MAX_IMAGE_BYTES) {
    throw new BusinessException(PrizeErrorCode.IMAGE_TOO_LARGE);
}
```

这是防御性检查。最终相信的是实际读到的字节数量。

---

## 9. 校验第三步：不能只看文件名

很多初学实现会这样判断：

```java
file.getOriginalFilename().endsWith(".png")
```

这是不可靠的，因为改文件名不会改变文件内容：

```text
cat.exe  →  cat.png
```

所以我们的代码直接检查文件开头的固定字节，也就是“文件签名”或“魔数”。

调用代码：

```java
ImageFormat format = detect(content);
```

### 9.1 JPEG 的文件头

```java
if (startsWith(
        content,
        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
)) {
    return ImageFormat.JPEG;
}
```

JPEG 文件开头通常是：

```text
FF D8 FF
```

### 9.2 PNG 的文件头

```java
if (startsWith(content, new byte[]{
        (byte) 0x89, 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
})) {
    return ImageFormat.PNG;
}
```

PNG 的固定开头是：

```text
89 50 4E 47 0D 0A 1A 0A
```

### 9.3 WebP 的文件头

```java
if (content.length >= 12
        && Arrays.equals(
                Arrays.copyOfRange(content, 0, 4),
                new byte[]{'R', 'I', 'F', 'F'}
        )
        && Arrays.equals(
                Arrays.copyOfRange(content, 8, 12),
                new byte[]{'W', 'E', 'B', 'P'}
        )) {
    return ImageFormat.WEBP;
}
```

WebP 的结构是：

```text
第 0～3 字节：RIFF
第 4～7 字节：文件长度等信息
第 8～11 字节：WEBP
```

所以只看开头的 `RIFF` 还不够，还要检查第 8～11 字节。

### 9.4 `startsWith` 工具方法

```java
private boolean startsWith(byte[] content, byte[] signature) {
    return content.length >= signature.length
            && Arrays.equals(
                    Arrays.copyOf(content, signature.length),
                    signature
            );
}
```

它做两件事：

```text
1. 确认文件至少有签名那么长，避免数组越界；
2. 截取文件开头并和标准签名比较。
```

我们把这段重复逻辑提取成方法，是为了让 PNG 和 JPEG 的判断代码更容易读。

---

## 10. 为什么还要比较 Content-Type

识别真实格式后，代码继续执行：

```java
if (format == null
        || !format.contentType.equals(file.getContentType())) {
    throw new BusinessException(
            PrizeErrorCode.IMAGE_TYPE_UNSUPPORTED
    );
}
```

这里检查两个问题：

```text
format == null
```

说明真实字节不是支持的 JPEG、PNG 或 WebP。

```text
真实格式的 contentType != 请求声明的 contentType
```

说明客户端声明与文件实际内容不一致。

例如：

```text
文件真实格式：WebP
请求声明类型：image/png
```

即使文件名叫 `3.png`，也会拒绝。

### 真实案例：为什么 `D:\3.png` 被拒绝

我们检查到这个文件的前 16 个字节是：

```text
52 49 46 46 16 E9 00 00 57 45 42 50 56 50 38 20
```

把十六进制翻译成字符：

```text
52 49 46 46  → RIFF
57 45 42 50  → WEBP
```

因此它的真实格式是 WebP，不是 PNG。

如果 Postman 按 `.png` 声明：

```text
image/png
```

校验器检测到：

```text
image/webp
```

两者不一致，所以返回：

```text
41003 仅支持 JPEG、PNG 和 WebP 图片
```

这说明系统不是“认不出 PNG”，而是成功发现了“扩展名与真实内容不一致”。

正确做法是：

- 把图片真正转换为 PNG；或者
- 使用正确的 `.webp` 文件名并以 `image/webp` 上传。

只改后缀不叫格式转换。

---

## 11. 为什么要创建 ValidatedImage

校验成功后返回：

```java
return new ValidatedImage(
        content,
        format.contentType,
        format.extension
);
```

它的定义非常短：

```java
public record ValidatedImage(
        byte[] content,
        String contentType,
        String extension
) {
}
```

### 现在缺少什么

校验器已经得到了三个可信结果：

```text
真实文件字节
真实 MIME 类型
正确扩展名
```

我们需要把它们一起交回 Service。

可以返回三个零散值吗？Java 方法只能直接返回一个值。也可以使用数组或 Map，但类型含义不清晰。

所以创建 `ValidatedImage`，专门表达：

```text
这是一张已经通过检查的图片。
```

### `record` 是什么

`record` 适合保存一组不可随意修改的数据。

Java 会自动生成：

```java
image.content()
image.contentType()
image.extension()
```

还会生成构造器、`equals`、`hashCode` 和 `toString`。

相比手写普通类，它减少了大量样板代码。

此时控制权回到 Service：

```java
ValidatedImage image = validator.validate(file);
```

从这一行以后，Service 使用的是已验证数据，不再直接相信原始文件名。

---

# 第五段：生成 OSS 中的对象地址

## 12. 为什么需要 Object Key

OSS 可以理解成一个很大的对象仓库：

```text
Bucket
 ├─ prizes/2026/07/abc.png
 ├─ prizes/2026/07/def.webp
 └─ prizes/2026/08/ghi.jpg
```

Bucket 中每个对象都必须有一个唯一名称，这个名称叫：

```text
Object Key
```

现在我们有图片字节，但还没有决定它在 Bucket 中叫什么。

所以 Service 生成：

```java
String objectKey = "prizes/%s/%s.%s".formatted(
        LocalDate.now(clock).format(PATH_DATE),
        uuidSupplier.get(),
        image.extension()
);
```

日期格式定义：

```java
private static final DateTimeFormatter PATH_DATE =
        DateTimeFormatter.ofPattern("yyyy/MM");
```

假设：

```text
日期：2026-07-27
UUID：7c9e6679-7425-40de-944b-e07fc1f90ae7
真实格式：PNG
```

得到：

```text
prizes/2026/07/7c9e6679-7425-40de-944b-e07fc1f90ae7.png
```

每部分的作用：

```text
prizes/       表示奖品图片业务目录
2026/07/      方便按月份查看和管理
UUID          几乎不会重复，避免同名覆盖
.png          使用校验器识别的真实扩展名
```

为什么不使用原文件名？

```text
logo.png
```

可能被很多用户重复上传。如果直接使用原名，后上传的文件可能覆盖先上传的文件；原名还可能包含空格、中文、路径字符或恶意字符。

### 为什么注入 Clock 和 UUID Supplier

生产构造器使用：

```java
Clock.systemUTC()
UUID::randomUUID
```

日期和随机 UUID 每次都会变化。如果在测试中直接调用它们，预期结果无法固定。

所以另一个构造器允许测试传入固定的：

```text
Clock
Supplier<UUID>
```

这样测试可以明确断言生成的 Object Key。这是一种“让不可预测因素可替换”的测试设计。

---

# 第六段：业务代码怎样调用 OSS

## 13. 为什么不在 Service 中直接写阿里云 SDK

Object Key 已经生成，下一步是：

```java
gateway.put(objectKey, image.content(), image.contentType());
```

第一次看到可能会问：

```text
为什么不直接在 PrizeImageService 中 new OSSClient？
```

如果直接依赖阿里云 SDK，Service 会同时承担：

- 奖品图片业务流程；
- 阿里云客户端创建；
- AccessKey 配置；
- PutObject 请求拼装；
- 云服务异常处理。

以后改成腾讯云、MinIO 或本地存储时，还要修改业务 Service。

所以现在缺少一个稳定的“存储能力接口”：

```java
public interface ObjectStorageGateway extends AutoCloseable {

    void put(
            String objectKey,
            byte[] content,
            String contentType
    );

    @Override
    default void close() throws Exception {
    }
}
```

这个接口只表达业务真正需要的能力：

```text
请把这些字节，以这个类型，保存到这个 Object Key。
```

它不暴露阿里云 SDK 的类型。

### interface 在这里解决了什么

`PrizeImageService` 依赖：

```text
ObjectStorageGateway
```

而不是依赖：

```text
AliyunOssObjectStorageGateway
```

因此可以替换实现：

```text
生产环境 → AliyunOssObjectStorageGateway
配置缺失 → UnavailableObjectStorageGateway
单元测试 → FakeObjectStorageGateway
未来切换 → MinioObjectStorageGateway
```

Service 的业务代码不需要改变。

这就是“面向接口编程”最实际的用途，不是为了多写一个文件。

---

## 14. 应用启动时，Gateway 从哪里来

Service 的构造器需要：

```java
ObjectStorageGateway gateway
```

但是接口不能直接 `new`，因为接口没有具体实现。

现在缺少的是：

```text
启动时根据 OSS 配置，选择并创建一个具体实现。
```

这由 `OssConfiguration` 完成：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
public class OssConfiguration {

    @Bean
    public ObjectStorageGateway objectStorageGateway(
            OssProperties properties
    ) {
        if (!properties.enabled() || !properties.isComplete()) {
            return new UnavailableObjectStorageGateway();
        }

        OSSClient client = OSSClient.newBuilder()
                .region(properties.region())
                .endpoint(properties.endpoint())
                .credentialsProvider(
                        new StaticCredentialsProvider(
                                properties.accessKeyId(),
                                properties.accessKeySecret()
                        )
                )
                .build();

        return new AliyunOssObjectStorageGateway(
                client,
                properties.bucket()
        );
    }
}
```

### `@Configuration`

告诉 Spring：这个类负责创建和配置其他对象。

### `@Bean`

告诉 Spring：把方法返回的对象放入 Spring 容器。

以后某个构造器需要 `ObjectStorageGateway` 时，Spring 就把这里创建的对象注入进去。

### 两个分支

配置完整且启用：

```text
AliyunOssObjectStorageGateway
```

未启用或配置不完整：

```text
UnavailableObjectStorageGateway
```

后者的代码是：

```java
public void put(
        String objectKey,
        byte[] content,
        String contentType
) {
    throw new BusinessException(
            PrizeErrorCode.OSS_CONFIG_UNAVAILABLE
    );
}
```

这样应用可以正常启动，但有人真正上传时，会得到明确的 `51002 OSS 配置不可用`，而不是含糊的空指针异常。

---

# 第七段：配置怎样从 .env 进入 Java

## 15. `.env → application.yaml → OssProperties`

创建 OSS 客户端需要：

```text
是否启用
Region
Endpoint
Bucket
AccessKey ID
AccessKey Secret
公开访问域名
```

敏感信息不能硬编码到 Java，也不能提交到 Git。

本地 `.env` 示例：

```properties
OSS_ENABLED=true
OSS_REGION=cn-hangzhou
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET=你的Bucket名称
OSS_ACCESS_KEY_ID=你的AccessKeyId
OSS_ACCESS_KEY_SECRET=你的AccessKeySecret
OSS_PUBLIC_BASE_URL=https://你的Bucket名称.oss-cn-hangzhou.aliyuncs.com
```

`application.yaml` 首先加载 `.env`：

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

`optional` 表示没有 `.env` 时应用也可以尝试启动。

然后把环境变量映射到项目配置：

```yaml
luckyhub:
  oss:
    enabled: ${OSS_ENABLED:false}
    region: ${OSS_REGION:}
    endpoint: ${OSS_ENDPOINT:}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    public-base-url: ${OSS_PUBLIC_BASE_URL:}
```

例如：

```text
OSS_REGION=cn-hangzhou
        ↓
luckyhub.oss.region=cn-hangzhou
```

最后由 Spring 绑定为：

```java
@ConfigurationProperties(prefix = "luckyhub.oss")
public record OssProperties(
        boolean enabled,
        String region,
        String endpoint,
        String bucket,
        String accessKeyId,
        String accessKeySecret,
        String publicBaseUrl
) {
}
```

配置名称会自动转换：

```text
access-key-id     → accessKeyId
public-base-url   → publicBaseUrl
```

所以完整数据流是：

```text
.env
  OSS_ACCESS_KEY_ID
        ↓
application.yaml
  luckyhub.oss.access-key-id
        ↓
OssProperties
  accessKeyId()
        ↓
OssConfiguration
        ↓
StaticCredentialsProvider
        ↓
OSSClient
```

### `isComplete()` 是干什么的

```java
public boolean isComplete() {
    return StringUtils.hasText(region)
            && StringUtils.hasText(endpoint)
            && StringUtils.hasText(bucket)
            && StringUtils.hasText(accessKeyId)
            && StringUtils.hasText(accessKeySecret)
            && StringUtils.hasText(publicBaseUrl);
}
```

它集中检查所有必填配置是否有内容。

如果把这些判断散落在不同类中，很容易漏掉某项，而且错误行为不一致。

### `normalizedPublicBaseUrl()` 是干什么的

```java
public String normalizedPublicBaseUrl() {
    if (!StringUtils.hasText(publicBaseUrl)) {
        return "";
    }
    return publicBaseUrl.trim().replaceAll("/+$", "");
}
```

它会：

```text
去掉首尾空格；
去掉末尾一个或多个 /。
```

例如：

```text
输入：https://example.com///
输出：https://example.com
```

这样稍后拼接 `"/" + objectKey` 时不会出现双斜杠。

---

## 16. AccessKey 和 Bucket 是怎样联系起来的

这里容易产生一个误解：

```text
是不是创建 AccessKey 时，要把它和某个 Bucket 绑定？
```

不是由 LuckyHub 在本地“绑定”的。

调用 OSS 时，请求同时携带：

```text
1. 目标 Bucket：properties.bucket()
2. 身份凭证：AccessKey ID + AccessKey Secret
3. 要执行的操作：PutObject
4. Object Key：prizes/2026/07/xxx.png
```

阿里云收到请求后会做类似判断：

```text
这个 AccessKey 代表哪个 RAM 用户？
        ↓
这个 RAM 用户拥有哪些权限策略？
        ↓
策略是否允许对这个 Bucket 执行 oss:PutObject？
        ↓
允许：上传
拒绝：返回 AccessDenied
```

所以真正建立联系的是阿里云 RAM 权限策略。

一个 AccessKey 可以有多个 Bucket 的权限；一个 Bucket 也可以允许多个 RAM 用户访问。关键不是“创建了第几个 AccessKey”，而是该 AccessKey 所属身份的权限策略。

最小权限思想是只允许它操作需要的 Bucket 和动作，不要为了省事长期使用拥有全部权限的主账号 AccessKey。

---

# 第八段：真正调用阿里云 OSS

## 17. AliyunOssObjectStorageGateway 做了什么

Service 调用：

```java
gateway.put(objectKey, image.content(), image.contentType());
```

在配置完整时，`gateway` 的实际对象是：

```text
AliyunOssObjectStorageGateway
```

核心代码：

```java
@Override
public void put(
        String objectKey,
        byte[] content,
        String contentType
) {
    PutObjectRequest request = PutObjectRequest.newBuilder()
            .bucket(bucket)
            .key(objectKey)
            .contentType(contentType)
            .body(BinaryData.fromBytes(content))
            .build();

    try {
        client.putObject(request);
    } catch (RuntimeException exception) {
        throw new BusinessException(
                PrizeErrorCode.OSS_UPLOAD_FAILED
        );
    }
}
```

逐行解释。

### `.bucket(bucket)`

告诉阿里云把对象放进哪个 Bucket。

值来自：

```text
OSS_BUCKET
```

### `.key(objectKey)`

指定对象在 Bucket 中的唯一名称，例如：

```text
prizes/2026/07/7c9e....png
```

OSS 没有传统磁盘的真实文件夹。这里的 `/` 是 Object Key 的一部分，控制台只是把它显示成目录。

### `.contentType(contentType)`

把真实类型写入 OSS 对象元数据，例如：

```text
image/png
```

浏览器访问公开 URL 时，OSS 会返回这个响应类型。设置正确后，浏览器通常会直接显示图片。

### `.body(BinaryData.fromBytes(content))`

把 Java 的 `byte[]` 转换成 OSS SDK 接受的请求体。

### `.build()`

前面使用 Builder 一项一项填写参数，`build()` 最终生成不可继续修改的 `PutObjectRequest`。

Builder 模式适合参数较多、部分参数可选的对象。

### `client.putObject(request)`

这一行才真正发起网络请求：

```text
LuckyHub → 阿里云 OSS
```

在它之前都只是准备数据和构造请求。

如果网络失败、签名错误、Bucket 不存在或权限不足，SDK 会抛异常。

我们把 SDK 异常转换为统一业务异常：

```java
throw new BusinessException(
        PrizeErrorCode.OSS_UPLOAD_FAILED
);
```

对应错误码 `51001` 和 HTTP 502。

为什么不直接把阿里云异常返回给前端？

- SDK 异常结构可能改变；
- 原始消息可能包含内部实现信息；
- 前端需要稳定的业务错误码；
- 上层业务不应该依赖某一家云厂商的异常类型。

---

# 第九段：上传成功后生成公开 URL

## 18. OSS 上传成功为什么没有直接得到我们要保存的 URL

`putObject` 的主要结果是“对象已经保存成功”，业务仍然需要一个浏览器能访问的地址。

现在我们已知：

```text
公开基础地址：
https://example-bucket.oss-cn-hangzhou.aliyuncs.com

Object Key：
prizes/2026/07/7c9e....png
```

Service 拼接：

```java
String url = properties.normalizedPublicBaseUrl()
        + "/"
        + objectKey;
```

得到：

```text
https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/2026/07/7c9e....png
```

如果使用自定义 CDN 域名：

```properties
OSS_PUBLIC_BASE_URL=https://img.example.com
```

代码无需修改，生成的 URL 会变为：

```text
https://img.example.com/prizes/2026/07/7c9e....png
```

需要注意：

```text
拼出了 URL，不等于匿名用户一定能访问。
```

Bucket 必须允许相应的公开读取，或通过 CDN/签名 URL 提供访问。当前项目保存的是公开 URL，因此部署时要保证这个地址确实可读。

---

## 19. 为什么返回 ImageUploadView

上传完成后，前端需要两个结果：

```text
url：给浏览器展示，并保存到奖品表
objectKey：标识 OSS 中的对象，便于以后管理
```

所以创建：

```java
public record ImageUploadView(
        String url,
        String objectKey
) {
}
```

Service 返回：

```java
return new ImageUploadView(url, objectKey);
```

Controller 再包装：

```java
return ApiResponse.success(service.upload(file));
```

最终 Spring 转换成类似 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/2026/07/7c9e6679-7425-40de-944b-e07fc1f90ae7.png",
    "objectKey": "prizes/2026/07/7c9e6679-7425-40de-944b-e07fc1f90ae7.png"
  }
}
```

到这里，第一个 POST 请求结束。

此时：

```text
OSS 中：已经有图片
marketing_prize 表中：还没有保存图片 URL
```

---

# 第十段：把 URL 保存到奖品表

## 20. 为什么上传接口不直接写数据库

上传接口只知道“一张图片上传成功了”，它还不知道：

- 奖品名称是什么；
- 奖品类型是什么；
- 奖品等级是什么；
- 是否允许叠加；
- 这张图属于新奖品还是已有奖品。

所以不能在图片上传接口中凭空创建奖品。

前端拿到 `data.url` 后，再发送创建奖品请求：

```http
POST /api/admin/prizes
Authorization: Bearer 你的Token
Content-Type: application/json
```

示例 JSON：

```json
{
  "prizeName": "一等奖机械键盘",
  "prizeType": "PHYSICAL",
  "prizeLevel": "FIRST",
  "imageUrl": "https://example-bucket.oss-cn-hangzhou.aliyuncs.com/prizes/2026/07/7c9e6679-7425-40de-944b-e07fc1f90ae7.png",
  "description": "抽中后由工作人员发放",
  "stackable": false
}
```

执行链路：

```text
PrizeController
    ↓
PrizeServiceImpl
    ↓
Prize.setImageUrl(...)
    ↓
PrizeMapper / MyBatis-Plus
    ↓
marketing_prize.image_url
```

修改奖品时也是同样原理：

```http
PUT /api/admin/prizes/{id}
```

JSON 中带上新的 `imageUrl`，数据库字段就会更新。

### 一个完整的前端操作顺序

```text
1. 用户选择图片
2. 前端调用图片上传接口
3. 后端返回 URL
4. 前端把 URL 显示在预览框
5. 用户填写奖品名称、等级等
6. 用户点击“创建奖品”
7. 前端把 URL 和其他字段一起作为 JSON 提交
8. 后端把 URL 保存到 image_url
```

如果第 3 步成功，但用户关闭页面，没有执行第 6 步，OSS 中会留下未关联奖品的图片。当前版本接受这种情况；将来可以增加定时清理或“确认使用”机制。

---

# 第十一段：把一次请求完整跑一遍

## 21. 正常 PNG 的逐步执行示例

假设输入：

```text
文件名：prize.png
请求声明类型：image/png
大小：120,000 字节
文件头：89 50 4E 47 0D 0A 1A 0A
```

执行过程如下：

| 顺序 | 当前对象 | 做什么 | 产生什么 |
|---:|---|---|---|
| 1 | Postman | 发送 multipart POST | HTTP 请求 |
| 2 | Spring | 解析名为 `file` 的部分 | `MultipartFile` |
| 3 | AuthenticationFilter | 验证 Token | 当前登录用户 |
| 4 | PermissionInterceptor | 检查上传权限 | 允许进入 Controller |
| 5 | PrizeImageController | 接收文件并调用 Service | `service.upload(file)` |
| 6 | PrizeImageValidator | 检查非空和大小 | 继续 |
| 7 | PrizeImageValidator | 读取 `byte[]` | 图片真实字节 |
| 8 | PrizeImageValidator | 用文件头识别 PNG | `image/png`、`png` |
| 9 | PrizeImageValidator | 对比声明类型 | `ValidatedImage` |
| 10 | PrizeImageService | 日期 + UUID + 扩展名 | Object Key |
| 11 | ObjectStorageGateway | 抽象存储调用 | 进入阿里云实现 |
| 12 | AliyunOssObjectStorageGateway | 创建 PutObjectRequest | OSS 请求对象 |
| 13 | OSSClient | 发送网络请求 | OSS 保存图片 |
| 14 | PrizeImageService | 基础域名 + Object Key | 公开 URL |
| 15 | ImageUploadView | 保存返回字段 | Java 返回对象 |
| 16 | Spring/Jackson | 把对象序列化 | JSON 响应 |

用一句话概括数据的变化：

```text
HTTP 二进制
→ MultipartFile
→ byte[]
→ ValidatedImage
→ PutObjectRequest
→ OSS Object
→ URL
→ ImageUploadView
→ JSON
```

---

# 第十二段：下次自己从零实现时怎么写

## 22. 推荐实现顺序

以后为其他业务实现文件上传，可以按照下面顺序。

### 第 1 步：先定义接口契约

先明确：

```text
路径是什么？
请求方法是什么？
form-data 的字段名是什么？
允许哪些格式？
最大多大？
成功返回什么？
```

本项目答案：

```text
POST /api/admin/prize-images
字段名：file
格式：JPEG、PNG、WebP
业务大小：5MiB
返回：url、objectKey
```

### 第 2 步：定义输出 VO

```java
public record ImageUploadView(
        String url,
        String objectKey
) {
}
```

先定义输出，可以让 Controller 和 Service 的目标更清晰。

### 第 3 步：编写校验器

把不可信输入变成可信数据：

```text
MultipartFile → ValidatedImage
```

至少检查：

- 空文件；
- 文件大小；
- 文件签名；
- MIME 类型；
- 支持的扩展名。

### 第 4 步：定义存储接口

```java
void put(
        String objectKey,
        byte[] content,
        String contentType
);
```

先表达业务需要的最小能力，再实现具体云厂商。

### 第 5 步：实现阿里云适配器

负责把通用参数转换为：

```text
PutObjectRequest
```

并调用：

```java
client.putObject(request);
```

### 第 6 步：建立配置对象

配置流保持清晰：

```text
.env → application.yaml → OssProperties → OSSClient
```

不要在代码里写死 AccessKey。

### 第 7 步：编写 Service

Service 只负责按顺序组织：

```text
校验 → 生成 Key → 上传 → 生成 URL → 返回
```

### 第 8 步：最后编写 Controller

Controller 只做 HTTP 适配：

```text
接收 MultipartFile → 调 Service → 返回 JSON
```

### 第 9 步：补充权限和错误码

至少区分：

```text
文件为空
文件太大
类型不支持
OSS 未配置
OSS 上传失败
没有上传权限
```

### 第 10 步：编写测试

不要只测试成功上传，还要测试每一个拒绝分支。

---

# 第十三段：代码为什么容易测试

## 23. 测试时不应该真的上传阿里云

单元测试如果每次都访问真实 OSS，会有这些问题：

- 需要真实 AccessKey；
- 依赖网络；
- 运行慢；
- 可能产生费用；
- 会留下测试垃圾文件；
- 阿里云暂时异常会导致本地测试失败。

因为 Service 依赖接口：

```java
ObjectStorageGateway
```

测试中可以传入假的实现，只记录收到的参数：

```java
class FakeObjectStorageGateway
        implements ObjectStorageGateway {

    String objectKey;
    byte[] content;
    String contentType;

    @Override
    public void put(
            String objectKey,
            byte[] content,
            String contentType
    ) {
        this.objectKey = objectKey;
        this.content = content;
        this.contentType = contentType;
    }
}
```

测试真正关心的是：

```text
Service 有没有生成正确 Key？
有没有把正确字节交给 Gateway？
有没有生成正确 URL？
```

这再次说明 `ObjectStorageGateway` 不是多余的抽象，它让业务测试不依赖云服务。

校验器测试则分别准备：

```text
空文件
超过 5MiB 的文件
真正的 PNG
真正的 JPEG
真正的 WebP
伪装成 PNG 的 WebP
完全不支持的字节
```

Controller 测试主要验证：

```text
路由是否正确
字段名是否为 file
成功是否返回 201
是否声明了 prize:image:upload 权限
返回 JSON 是否正确
```

---

# 第十四段：常见失败怎样定位

## 24. 根据“执行到哪一步”排查

### 24.1 Controller 都没进入

可能原因：

- URL 写错；
- 不是 POST；
- 请求不是 `multipart/form-data`；
- form-data Key 不是 `file`；
- 文件超过 Spring 的 6MB 外层限制；
- Token 无效；
- 没有权限。

### 24.2 返回 41002

```text
图片为空
```

检查 Postman 中 `file` 的 Type 是否选为 `File`，是否确实选择了文件。

### 24.3 返回 41003

```text
图片类型不支持或声明类型与真实类型不一致
```

重点不要只看后缀。检查真实文件头和请求 MIME。

### 24.4 返回 41004

```text
图片超过 5MiB
```

压缩或缩小图片后再上传。

### 24.5 返回 51002

```text
OSS 配置不可用
```

检查：

```text
OSS_ENABLED
OSS_REGION
OSS_ENDPOINT
OSS_BUCKET
OSS_ACCESS_KEY_ID
OSS_ACCESS_KEY_SECRET
OSS_PUBLIC_BASE_URL
```

修改 `.env` 后要重启应用，因为 OSS Client 是应用启动时创建的。

### 24.6 返回 51001

```text
LuckyHub 已尝试调用 OSS，但上传失败
```

重点检查：

- Endpoint 和 Region 是否属于这个 Bucket；
- Bucket 名称是否正确；
- AccessKey 是否有效；
- RAM 用户是否有 `oss:PutObject` 权限；
- 网络能否访问 OSS；
- 系统时间是否明显错误。

### 24.7 上传成功，但 URL 不能打开

这说明“写入 OSS”成功，但“公开读取”没有配置好。

检查：

- `OSS_PUBLIC_BASE_URL` 是否正确；
- Bucket 或对象是否允许公开读取；
- 自定义域名/CDN 是否已经绑定并生效；
- URL 中的 Object Key 是否完整。

---

# 第十五段：最后真正理解这套设计

## 25. 每个类只回答一个问题

| 类或配置 | 它回答的问题 |
|---|---|
| `PrizeImageController` | 哪个 HTTP 请求进入哪个 Java 方法？ |
| `MultipartFile` | Spring 怎样表示收到的上传文件？ |
| `PrizeImageValidator` | 这个文件是否真的可接受？ |
| `ValidatedImage` | 怎样携带已经验证过的字节、类型和扩展名？ |
| `PrizeImageService` | 上传业务步骤按什么顺序执行？ |
| `ObjectStorageGateway` | 业务层需要怎样的对象存储能力？ |
| `AliyunOssObjectStorageGateway` | 怎样把通用上传转换成阿里云 PutObject？ |
| `UnavailableObjectStorageGateway` | 配置缺失时怎样返回明确错误？ |
| `OssProperties` | Java 怎样拿到集中管理的 OSS 配置？ |
| `OssConfiguration` | 启动时怎样创建 OSS Client 和 Gateway？ |
| `ImageUploadView` | 上传成功要返回哪些字段？ |
| `PrizeServiceImpl` | 怎样把公开 URL 保存进奖品数据？ |

最核心的原理不是记住类名，而是学会这样思考：

```text
请求来到这里以后，现在缺少什么能力？
        ↓
这个能力应该由谁负责？
        ↓
它需要什么输入？
        ↓
它应该产生什么输出？
        ↓
输出交给执行链的下一步
```

本项目的答案最终形成：

```text
Controller 负责 HTTP
Validator 负责信任边界
Service 负责流程
Gateway 负责隔离外部存储
Configuration 负责组装对象
VO 负责表达输出
Prize Service 负责持久化 URL
```

当你下次实现“用户头像上传”“活动封面上传”或“商品图片上传”时，可以直接套用同一套思考方式，而不是复制粘贴阿里云代码。

---

## 26. 用三句话复述整个原理

第一句：

```text
Spring 把 multipart 请求中的 file 转成 MultipartFile，
Controller 再把它交给 Service。
```

第二句：

```text
Service 先通过 Validator 得到可信的 ValidatedImage，
再生成唯一 Object Key，通过 Gateway 调用 OSS。
```

第三句：

```text
上传成功后用公开基础域名和 Object Key 拼出 URL，
前端再把这个 URL 放进创建或修改奖品的 JSON，最终保存到数据库。
```

如果你能够不看文档，用自己的话解释这三句，并能画出下面这条链路，就已经真正理解了本次实现：

```text
POST multipart
→ MultipartFile
→ Controller
→ Service
→ Validator
→ ValidatedImage
→ Object Key
→ Gateway
→ OSS SDK
→ OSS
→ public URL
→ ImageUploadView
→ 创建奖品 JSON
→ image_url
```
