# 小程序后端接口对齐设计

## 文档说明

- 日期：2026-04-24
- 覆盖范围：`demo/` Spring Boot 后端与 `卓凯安伴小程序/miniprogram/` 已改动接口契约
- 目标：以后端兼容式改造承接小程序当前实现，保证小程序调用路径、请求字段、响应结构、状态流转全部闭环可用

## 背景

2026-04-24 小程序端已完成一轮接口与页面联动调整，改动集中在统一请求层 `miniprogram/utils/api.ts`，并向以下业务链路扩散：

1. 机构端发布新闻：支持按范围发布、带附件上传、指定目标家庭。
2. 机构端预约详情：支持机构管理员选择护理人员并指定上门日期。
3. 护工端预约详情与上门记录：支持接单后提交服务记录、支付信息和照片。
4. 员工端首页：聚合新闻、地图、家庭、设备、报警、预约、护理人员统计。
5. 机构端地图详情：要求家庭地图点位和报警状态可直接渲染。
6. 小程序统一上传：通过 `uploadPhoto()` 固定调用 `/api/mini/upload`。

当前后端已具备大部分 `auth`、`mini`、`news` 接口，但仍存在几类高风险不一致：

1. 小程序已调用 `POST /api/mini/upload`，后端尚无该接口，上传链路必然失败。
2. 机构端派单会提交 `visitDate`，但后端 `dispatch` 请求体未接收该字段，导致界面输入被静默丢弃。
3. 护工端上门记录提交 `visitTime` 为 `yyyy-MM-dd HH:mm:ss`，后端当前只稳定支持到分钟格式，存在解析失败和时间兜底错误。
4. 若后端继续按旧契约工作，页面虽然能请求成功，但会在状态按钮、详情字段、地图标注和附件展示上出现逻辑错位。

## 目标

本次改造目标如下：

1. 以小程序当前代码为唯一接口契约源，后端无条件向其收敛。
2. 补齐缺失接口，保证小程序现有请求路径全部存在。
3. 兼容小程序当前请求字段和时间格式，不要求前端回退。
4. 保持后台管理端和既有接口可用，不做破坏性变更。
5. 对关键链路补充验证，确保机构发新闻、机构派单、护工接单/记录、员工首页统计与地图都能跑通。

## 非目标

本次不包含以下内容：

1. 不重构小程序页面逻辑或统一请求签名逻辑。
2. 不大规模重写 `MiniAppController` 的结构，只做聚焦式兼容改造。
3. 不引入新的对象存储、CDN 或媒体服务；上传先采用本地可访问静态目录方案。
4. 不重做新闻、预约、家庭等后台管理端 UI，仅保证其既有 API 不被破坏。

## 当前契约摘要

### 1. 认证与公共接口

小程序当前依赖以下公开接口：

- `POST /api/auth/login`
- `POST /api/auth/nurse/login`
- `POST /api/auth/guardian/login`
- `POST /api/auth/nurse/register`
- `POST /api/auth/guardian/register`
- `GET /api/institution/list`

这些接口后端已存在，返回结构也与 `utils/request.ts` 的 `ApiResponse<T>` 解包方式兼容，本轮不调整路径。

### 2. 新闻接口

小程序当前使用：

- `GET /api/news`
- `GET /api/news/{id}`
- `POST /api/news`

其中创建新闻请求体包含：

- `title`
- `content`
- `category`
- `targetScope`，取值 `ALL` 或 `FAMILY`
- `targetFamilyId` 或 `targetFamilyName`
- `publisherId`
- `publisherName`
- `attachments`

后端 `NewsPostService` 已基本支持上述字段，但依赖上传接口返回的附件 URL，因此新闻功能的真正阻塞点在上传链路而非新闻表本身。

### 3. 预约接口

小程序当前使用：

- `GET /api/mini/appointments`
- `GET /api/mini/appointments/{id}`
- `POST /api/mini/appointments`
- `PUT /api/mini/appointments/{id}/accept`
- `PUT /api/mini/appointments/{id}/dispatch`
- `POST /api/mini/appointments/{id}/visit-record`

新增或已固定的契约要求：

1. `dispatch` 请求除 `nurseId`、`nurseName` 外，还会带 `visitDate`。
2. `visit-record` 请求中的 `visitTime` 采用 `yyyy-MM-dd HH:mm:ss`。
3. 提交记录前，如果页面还处于 `pending`，小程序会先自动调用一次 `accept`。
4. 详情接口返回的 `status`、`doctor`、`acceptNurse`、`family`、`member`、`guardian` 等字段必须足够让小程序完成详情展示和按钮状态计算。

### 4. 地图、首页和资料接口

员工端首页与机构端地图当前依赖：

- `GET /api/mini/family/map-list`
- `GET /api/mini/device/list`
- `GET /api/mini/nurse-list`
- `GET /api/mini/alarms`
- `GET /api/mini/service/center`
- `GET /api/mini/staff/profile`
- `GET /api/mini/institution/nurses`

这些接口已存在，但后端需要保证以下语义持续成立：

1. 家庭点位返回 `latitude`、`longitude`、`status`、`hasAlarm`、`members`。
2. 设备列表返回 `id`、`deviceCode`、`location`、`address`、`status`、`members`。
3. 员工资料返回 `id`、`name`、`phone`、`orgName`、`orgId`、`role`，并允许小程序缓存后继续合并。
4. 机构护理人员列表必须足够支持派单弹窗展示。

### 5. 上传接口

小程序上传函数固定请求：

- `POST /api/mini/upload`
- 表单字段名：`file`
- Header：带 JWT `Authorization`
- 期望返回：`ApiResponse<String>`，其中 `data` 为可直接写入新闻附件或服务记录图片数组的访问 URL

这是本轮唯一完全缺失的接口。

## 方案比较

### 方案 A：契约优先兼容改造（推荐）

做法：以 `miniprogram/utils/api.ts` 和最新页面代码为准，对后端做兼容式补齐与扩展。

优点：

1. 风险最小，直接服务于当前已经改完的小程序。
2. 改动范围可控，主要集中在 `MiniAppController`、新闻服务、静态资源配置和少量 DTO。
3. 不需要同步修改小程序页面。

缺点：

1. `MiniAppController` 会继续承担较多接口职责。
2. 某些请求体需要做“向前兼容”字段扩展，接口定义会略显宽松。

### 方案 B：前后端同时回退到旧契约

做法：将小程序新增字段和上传链路撤回，回到后端已有接口范围。

优点：

1. 后端改动最少。

缺点：

1. 需要重新修改小程序页面，与当前用户要求相违背。
2. 已经新增的新闻附件、派单日期等体验会消失。

### 方案 C：全面重构 miniapp API 层

做法：拆分 `MiniAppController`、新增 miniapp 专用 service 和 DTO 体系，顺便清理所有返回对象。

优点：

1. 长期结构最清晰。

缺点：

1. 本轮时间成本过高。
2. 容易引入与当前页面联调无关的新风险。

结论：采用方案 A。

## 设计

### 一、接口对齐原则

1. 小程序当前请求路径不变。
2. 小程序当前请求字段优先，后端做兼容解析。
3. 小程序当前解包逻辑不变，后端统一继续返回 `ApiResponse<T>`。
4. 若同一接口已被后台管理端复用，则只做向后兼容扩展，不改语义。

### 二、上传链路设计

新增 `POST /api/mini/upload`，由认证通过的小程序用户调用。

请求约束：

1. Content-Type 使用 `multipart/form-data`。
2. 文件字段固定为 `file`。
3. 仅允许图片类型：`jpg`、`jpeg`、`png`、`webp`。
4. 单文件大小设置明确上限，避免异常大文件拖垮服务。

服务端行为：

1. 校验是否有文件、文件是否为空、类型是否合法。
2. 为文件生成不可预测的新文件名，避免重名覆盖和路径注入。
3. 将文件保存到后端可公开访问的静态目录，例如 `data/uploads/miniapp/`。
4. 返回基于当前服务可访问前缀的 URL，供新闻附件和上门记录照片直接引用。

响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": "https://host/uploads/miniapp/20260424/uuid-image.jpg"
}
```

错误处理：

1. 缺文件返回 400。
2. 文件类型非法返回 400。
3. 写盘失败返回 500，并给出明确 message。

### 三、预约派单链路设计

保留现有 `PUT /api/mini/appointments/{id}/dispatch`，扩展请求体：

- `nurseId`
- `nurseName`
- `visitDate`，格式为 `yyyy-MM-dd`

服务端行为：

1. 解析并保存派单护理人员信息。
2. 若传入 `visitDate`，则将其按 `Asia/Shanghai` 时区解析为当天 `00:00:00` 并写入 `appointmentTime`。
3. 保留 `dispatchedBy` 机构名称回写逻辑。
4. 若护理人员不存在或不在当前机构，返回业务错误，避免跨机构误派单。

状态语义：

1. 机构派单后订单仍对小程序表现为待接单语义，因此后端状态维持与现有页面兼容的流转方式。
2. 护工端收到派单后仍可调用 `accept` 进入已接单状态。

### 四、上门记录链路设计

保留现有 `POST /api/mini/appointments/{id}/visit-record`，增强时间兼容与记录保存。

请求体兼容字段：

- `visitTime`，支持 `yyyy-MM-dd HH:mm:ss` 与 `yyyy-MM-dd HH:mm`
- `payAmount`
- `payStatus`
- `remark`
- `photos`

服务端行为：

1. 优先按秒级格式解析，失败后降级到分钟格式。
2. 写入 `visitTime`、`payAmount`、`payStatus`、`visitRemark`。
3. 将订单状态设置为 `COMPLETED`。
4. 为 `ServiceOrder` 明确新增 `visitPhotos` 文本字段，按 JSON 数组持久化 `photos`，禁止把照片 URL 混入 `visitRemark`。

本轮实现要求：即使照片暂不单独展示，也必须保证接口接收后不报错、不丢主流程。

### 五、新闻发布链路设计

保留 `POST /api/news`，不修改路径。

服务端要求：

1. 继续支持 `targetScope=ALL|FAMILY`。
2. `targetScope=FAMILY` 时，允许通过 `targetFamilyName` 解析家庭。
3. `attachments` 直接保存为 JSON 字符串。
4. `publisherId`、`publisherName` 若前端未传，则后端按登录用户兜底。

列表与详情：

1. 机构端、护工端可查看全部新闻。
2. 家属端仅查看 `ALL` 或命中自身家庭的新闻。
3. 返回实体字段保持兼容，确保小程序详情页可直接渲染标题、内容、发布时间、附件等。

### 六、地图与首页聚合链路设计

对现有接口不改路径，只补稳定性约束：

1. `family/map-list` 必须始终返回统一字段，不因家庭无设备或无报警而缺失基础属性。
2. `device/list` 中的 `location` 继续按 `lat,lng` 输出，满足小程序现有解析逻辑。
3. `service/center` 在无当前机构上下文时，仍回退到首个机构，避免地图首页空白。
4. `staff/profile` 和 `institution/nurses` 返回字段保持轻量，不额外引入前端未消费字段。

### 七、鉴权与静态资源设计

上传接口属于 `/api/mini/**` 范围内，继续受 miniapp 签名和 JWT 保护。

新增的文件访问 URL 设计为静态公开读取路径，例如 `/uploads/**`。

原因：

1. 小程序新闻详情和照片预览需要直接加载图片 URL。
2. 若继续走带签名下载接口，会额外引入图片请求签名复杂度，不适合本轮。

约束：

1. 公开静态目录只用于小程序上传图片，不暴露其它服务器目录。
2. 路径必须由后端生成，不允许客户端指定子路径。

## 组件与文件边界

### 需要修改的主要文件

1. `demo/src/main/java/com/ncf/demo/web/MiniAppController.java`
   - 新增上传接口。
   - 扩展派单请求体。
   - 增强上门记录时间解析。
   - 接入预约照片保存逻辑。

2. `demo/src/main/java/com/ncf/demo/entity/ServiceOrder.java`
   - 新增 `visitPhotos` 文本字段及 getter/setter。

3. `demo/src/main/java/com/ncf/demo/web/dto/...` 或 `MiniAppController` 内部 record
   - 为 `dispatch` 增加 `visitDate` 字段。

4. `demo/src/main/java/com/ncf/demo/config/...`
   - 增加静态上传目录映射或相关属性配置。

5. `demo/src/main/resources/application*.properties`
   - 增加上传目录、URL 前缀、文件大小限制等配置。

6. `demo/src/main/java/com/ncf/demo/service/NewsPostService.java`
   - 保持附件、目标家庭解析与发布人兜底逻辑稳定，补齐空值兜底并保持 JSON 序列化行为一致。

7. `demo/src/test/...`
   - 增加上传、派单日期、上门记录时间格式兼容的回归测试。

### 不建议本轮改动的文件

1. 小程序页面代码。
2. 后台管理端前端代码。
3. 认证主链路与 JWT 机制。
4. `MiniAppSignatureFilter` 签名规则。

## 数据流

### 1. 机构发布新闻

1. 小程序选择图片。
2. 小程序逐张调用 `/api/mini/upload` 获取 URL。
3. 小程序将 URL 数组作为 `attachments` 提交给 `/api/news`。
4. 后端保存新闻和附件 JSON。
5. 家属或员工端通过 `/api/news`、`/api/news/{id}` 查看。

### 2. 机构派单

1. 机构详情页读取预约详情。
2. 打开派单弹窗，调用 `/api/mini/institution/nurses` 获取护理人员。
3. 选择护理人员与上门日期后提交 `/api/mini/appointments/{id}/dispatch`。
4. 后端保存护理人员信息和预约日期。
5. 护工端列表与详情重新拉取后看到更新后的信息。

### 3. 护工接单并提交记录

1. 护工详情页调用 `/api/mini/appointments/{id}`。
2. 若状态为 `pending`，点击接单调用 `/accept`。
3. 记录页填写服务结果、老人状态、支付信息并上传照片。
4. 小程序先走 `/api/mini/upload`，再提交 `/visit-record`。
5. 后端完成记录落库并置订单为 `COMPLETED`。

## 错误处理

1. 对前端显式依赖的字段统一做非空兜底，避免页面直接渲染失败。
2. 对上传接口返回明确 message，便于小程序 toast 透传。
3. 对派单跨机构、预约不存在、重复接单等错误保持业务异常语义。
4. 对日期解析失败不再静默吞掉并写入错误值，应明确采用受控兜底或报错。

## 测试策略

### 1. 单元与集成测试

至少覆盖以下场景：

1. `POST /api/mini/upload` 上传成功。
2. `POST /api/mini/upload` 文件为空或类型非法失败。
3. `PUT /api/mini/appointments/{id}/dispatch` 接收 `visitDate` 并正确回写预约时间。
4. `POST /api/mini/appointments/{id}/visit-record` 能解析秒级时间格式。
5. 新闻创建时 `attachments` 能正确保存。

### 2. 编译与回归验证

1. 运行后端测试或至少完成 `gradlew test` / 针对性测试。
2. 运行 `gradlew build -x test` 或最小可行编译验证，确保控制器和配置修改可编译。
3. 使用与小程序一致的真实路径和请求体做手动回归：
   - 上传图片
   - 机构发布新闻
   - 机构派单
   - 护工提交上门记录
   - 员工首页读取统计与地图数据

## 风险与权衡

1. `ServiceOrder` 当前没有照片字段，因此本轮直接新增 `visitPhotos`，避免把结构化照片数据污染到备注文本。
2. 静态公开图片目录实现快，但生产环境需要结合部署路径和反向代理校准访问前缀。
3. `MiniAppController` 继续变大是已知技术债，但本轮应优先保证契约正确，重构延后。

## 实施建议

按以下顺序实施最稳妥：

1. 先补上传接口与静态资源映射，打通新闻和记录图片主链路。
2. 再修正预约派单与上门记录的请求体兼容。
3. 然后补测试和编译验证。
4. 最后用小程序实际接口顺序回归关键页面。
