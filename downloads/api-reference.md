# 121 智能化采购同步接口文档

版本 **1.1.0** · 更新 **2026-11-17** · 服务提供方 **121系统** · 调用方 **智能化办公平台后台服务**

本文是服务器对服务器的对接规范。网页用于公开查阅；业务接口代码已实现、默认关闭，尚需真实环境部署联调。环境表中的121域名及现有接口入口由121提供方根据前端抓包确认；其余示例账号、部门和订单为虚构数据，回调域名为占位。

## 一、环境与接口总览

### 1.1 环境地址

| 环境 | 已有后端接口入口（抓包确认） | 新开放接口拟用基础地址 | 当前状态 |
| --- | --- | --- | --- |
| 测试环境 | `http://testfcst.chinaptac.com/api` | `https://testfcst.chinaptac.com/open-api/v1` | 复用原测试域名；HTTPS及新增转发待配置验收，暂不能作为已开放接口调用 |
| 生产环境 | `https://fcst.chinaptac.com/prod-api` | `https://fcst.chinaptac.com/open-api/v1` | 复用原生产域名；新接口部署及转发待验收，尚未开放 |
| 删除回调 | 由智能化提供 | `https://<智能化回调域名>/<约定路径>` | 智能化提供HTTPS接口，121运维配置固定地址 |

业务接口继续使用原121各环境域名，不需要新申请API域名。`/api`、`/prod-api`是已有业务接口的入口前缀；上表不代表新开放路径已经部署。新接口按下述独立转发方案验收后，由121交付最终调用地址。GitHub Pages仅托管本文档，不接收业务请求；测试与生产分别配置调用凭据。

在原121域名下新增 `/open-api/` 转发到原121后端；已有页面和API转发规则保留。所有业务请求使用HTTPS、TLS 1.2或更高版本；调用方校验证书与域名。网关必须保留完整 `/open-api/v1` 路径及请求体原始字节；不要把原页面使用的 `/api` 或 `/prod-api` 前缀直接拼入签名路径。本协议外部与应用收到的开放接口路径一致，最终以部署验收地址为准。

### 1.2 接口目录与调用权限

以下路径相对于基础地址 `/open-api/v1`。C01是121主动调用智能化的反向接口。

| 编号 | 接口名称 | 方法 | 路径 | 必需scope |
| --- | --- | --- | --- | --- |
| U01 | 查询121用户信息 | POST | `/users/verify` | `user:verify` |
| D01 | 查询基础字典 | GET | `/dictionaries/{type}` | `dictionary:read` |
| P01 | 创建采购草稿 | POST | `/purchase-drafts` | `purchase-draft:create` |
| P02 | 按来源和批次查询草稿 | GET | `/purchase-drafts/by-source` | `purchase-draft:read` |
| P03 | 按121采购单ID查询草稿 | GET | `/purchase-drafts/{draftId}` | `purchase-draft:read` |
| P04 | 取消同步并删除草稿 | POST | `/purchase-drafts/{draftId}/cancel` | `purchase-draft:cancel` |
| C01 | 采购草稿删除通知 | POST | 智能化提供的固定HTTPS地址 | 独立反向凭据 |

`scope` 是调用方系统的接口权限，由121配置；用户的部门权限同时按客户端部门白名单校验。查询用户成功不等于证明账号所有权；智能化应完成账号绑定授权。本版本操作人必须与发起人相同，不开放代发。

### 1.3 数据与业务约定

- ID使用JSON字符串，避免大整数丢精度；必填ID为正整数形式。字典中的 `code` 是业务编码，不能替代 `id`。
- 所有金额以人民币元计，使用十进制数，非负、最多两位小数；单价、金额及采购合计最大 `9999999999999.99`。
- 数量使用整数，最大 `999999999`。税率传比例：`0.13` 表示13%，不能传 `13`。
- `cashCycle` 单位是天；`budgetMonth` 为 `YYYY-MM`；`purchaseDate` 为 `yyyy-MM-dd`。回调 `eventTime` 为UTC ISO 8601，例如 `2026-11-17T02:00:00Z`。
- 智能化必须提供预算月份、销售数量、销售单价和当日库存。121保存明细传入价格；预算、利润和导出继续沿用现有取价规则，同步接口不更新物料主数据价格。
- 智能化维护供应商与默认项目、项目与默认变现周期的映射和页面默认值。121提供字典并在创建时再次校验。
- 来源草稿创建后供应商不可修改；部门归属沿用121现有规则，不支持跨部门修改，其余业务字段按121现有草稿规则编辑。取消同步只允许删除草稿，已锁单及退单状态不能取消。

## 二、公共请求参数与鉴权

公共鉴权参数放在HTTP请求头，**不放在JSON正文**。智能化后台签名后调用121，不使用员工登录密码、员工JWT或网页登录会话。HMAC用于验证系统身份及请求完整性，传输加密由HTTPS提供。

### 2.1 请求头参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `X-Client-Id` | 调用方系统编号 | string | 是 | 121分配，1—64字符；ASCII字母、数字及 `._:-` |
| `X-Key-Id` | 密钥编号 | string | 是 | 1—64字符，字符集同上；用于密钥轮换 |
| `X-Timestamp` | 请求时间戳 | string | 是 | 10位Unix秒，不是毫秒；与121相差不超过300秒 |
| `X-Nonce` | 本次随机数 | string | 是 | 16—128字符，字符集同上；建议UUID v4，每次HTTP请求更新 |
| `X-Request-Id` | 本次请求编号 | string | 是 | 16—128字符，字符集同上；建议UUID，每次HTTP请求更新 |
| `X-Content-SHA256` | 正文摘要 | string | 是 | 实际发送原始字节的SHA-256，64位小写十六进制；GET按空字节计算 |
| `X-Signature` | 请求签名 | string | 是 | 32字节HMAC-SHA256结果的标准Base64，44字符，保留结尾 `=` |
| `Idempotency-Key` | 业务幂等键 | string | 仅P01必填 | 16—160字符，字符集同上；其他接口不能传 |
| `Content-Type` | 正文媒体类型 | string | POST必填 | `application/json` 或 `application/json; charset=utf-8` |
| `Accept` | 期望响应类型 | string | 建议 | `application/json` |

Header名称不区分大小写。鉴权头不能重复，值不得带额外空格或换行。签名密钥为32字节随机数的标准Base64，双方通过安全渠道交换，计算前先Base64解码；不会出现在网页、浏览器脚本或业务正文里。

### 2.2 签名原文与算法

按以下10行拼接，以单个LF（`\n`）连接，最后一行后没有LF；无查询参数或幂等键时，仍保留对应空行。

```text
HTTP_METHOD
REQUEST_PATH
CANONICAL_QUERY_STRING
X-Client-Id
X-Key-Id
X-Timestamp
X-Nonce
X-Request-Id
Idempotency-Key
X-Content-SHA256
```

| 项目 | 计算规则 |
| --- | --- |
| `HTTP_METHOD` | 大写，例如 `POST`、`GET` |
| `REQUEST_PATH` | 完整路径，例如 `/open-api/v1/users/verify`；不含域名、查询参数 |
| `CANONICAL_QUERY_STRING` | 参数名和值各按UTF-8 RFC 3986编码，编码后按参数名升序，使用 `name=value` 和 `&` 连接；空格编码 `%20`，不能用 `+`；重复参数拒绝 |
| `X-Content-SHA256` | `lowercase(hex(SHA256(rawBodyBytes)))` |
| `X-Signature` | `Base64(HMAC_SHA256(Base64Decode(secret), UTF8(canonicalString)))` |

例如 `?pageSize=100&deptId=80001&pageNum=1` 的规范查询串为 `deptId=80001&pageNum=1&pageSize=100`。签名后必须发送相同正文原始字节；再次序列化、重排属性或改空格都可能导致验签失败。

### 2.3 正文、限流与重试

| 规则 | 当前约定 |
| --- | --- |
| 正文 | UTF-8、不带BOM、不压缩；GET正文为空，POST正文是JSON对象 |
| 大小 | U01最大8192字节（8KiB），其他接口最大2097152字节（2MiB） |
| 严格JSON | 拒绝未知属性、重复属性、错误标量类型、非整数数量、尾随JSON及非法UTF-8 |
| 防重放 | 验签后按客户端分别占用Nonce和Request-Id，各600秒；任一重复返回401 `AUTH_REPLAY_REQUEST` |
| 调用频率 | 默认每客户端每分钟120次，由121配置；超限429，`Retry-After: 60` |
| 服务暂不可用 | 防重放存储故障或事务竞争返回503 `SYSTEM_BUSY`；不能跳过验签 |
| 每次HTTP重试 | 生成新时间戳、Nonce、Request-Id及签名 |
| P01业务重试 | 同时保留原 `syncBatchId`、`Idempotency-Key` 及原始正文；先按P02查询 |

## 三、公共响应与返回值

### 3.1 统一响应字段

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {}
}
```

| 返回字段 | 类型 | 含义 | 取值说明 |
| --- | --- | --- | --- |
| `code` | string | 业务处理结果码 | 成功为 `SUCCESS`；失败为第十一节列出的字符串错误码，不能按数字 `0` 判断 |
| `message` | string | 结果说明 | 供展示、排查使用；文字可变化，程序不能通过解析message判断结果 |
| `requestId` | string | 本次请求追踪编号 | 合法时回显请求头；缺失或非法时由121生成，反馈问题时一并提供 |
| `data` | object / null | 业务返回内容 | 成功为对应接口对象；当前应用失败响应固定为 `null` |

成功HTTP为200；失败使用实际HTTP状态码。所有应用响应带 `Cache-Control: no-store`；401额外带 `WWW-Authenticate: HMAC realm="121-open-api"`；429额外带 `Retry-After: 60`。网关502/504可能没有JSON正文，应按HTTP失败处理。

### 3.2 采购单状态值

下面是P01、P02、P03、P04的 `data.status`，与响应外层 `code` 含义不同。

| 返回值 | 中文含义 | 业务说明 | 可通过P04取消 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | 已在121建立有效采购草稿，尚未锁单 | 是 |
| `LOCKED` | 已锁单 | 已进入121锁单流程，来源关联继续有效 | 否 |
| `RETURN_PENDING` | 退单待审核 | 已申请退单，等待审核；不能视为删除 | 否 |
| `RETURNED` | 退单通过 | 现有退单流程已通过；来源关联仍保留 | 否 |
| `RETURN_REJECTED` | 退单驳回 | 退单申请被驳回；来源关联仍有效 | 否 |
| `DELETED` | 已删除 | 草稿已软删除，本轮批次可查询历史ID；不能恢复为已同步 | 重复调用返回DELETED |

已删除标记优先于其他状态。只有确认旧批次为 `DELETED` 后，智能化才能使用新批次和新幂等键重新同步。同来源有效关联包括锁单及退单，不仅包括草稿。

## 四、U01 查询121用户信息

### 4.1 接口说明

| 项目 | 内容 |
| --- | --- |
| 请求方法 | `POST` |
| 请求路径 | `/open-api/v1/users/verify` |
| 权限 | `user:verify` |
| 用途 | 校验绑定账号有效性，查询基本信息及客户端授权范围内的用户部门 |
| 传参方式 | Request Body JSON；不接受查询参数、幂等键 |
| 是否分页 | 否，部门按ID升序一次返回 |

### 4.2 请求参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `loginAccount` | 121登录账号 | string | 是 | 原始输入最多128个Unicode码点；验签后仅去首尾ASCII空格，结果1—30码点；内部禁止空白、控制、格式字符 |

按现有121账号查询规则匹配，不强制转换大小写。传姓名、手机号或用户ID不能替代登录账号。返回成功只表示该账号有效，不证明智能化当前用户拥有此账号。

### 4.3 返回字段

以下字段均位于 `data` 对象中。

| 返回字段 | 类型 | 含义与取值 |
| --- | --- | --- |
| `userId` | string | 121用户唯一ID |
| `loginAccount` | string | 121记录中的规范登录账号 |
| `userName` | string | 姓名或昵称，未维护时为空字符串 |
| `status` | string | 成功时固定 `ACTIVE`，表示启用；停用账号返回错误，不返回DISABLED对象 |
| `departments` | array | 有效用户成员部门与客户端授权部门的交集，成功至少1项 |
| `departments[].id` | string | 部门唯一ID，创建草稿时作为departmentId |
| `departments[].code` | string | 业务部门编码，缺失时为空字符串 |
| `departments[].name` | string | 部门名称 |

不返回密码、Token、电话、邮箱或完整内部用户信息。用户可能有多个部门，不能假定其上次登录所选部门就是本次采购部门。

### 4.4 请求与响应示例

请求正文：

```json
{"loginAccount":"zhangsan"}
```

成功响应，HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "userId": "101",
    "loginAccount": "zhangsan",
    "userName": "张三",
    "status": "ACTIVE",
    "departments": [{"id":"80001","code":"","name":"示例业务部"}]
  }
}
```

账号不存在或已删除，HTTP 404：

```json
{
  "code": "USER_NOT_FOUND",
  "message": "121登录账号不存在",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

### 4.5 业务返回码

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 账号启用且至少一个可用授权部门 | 保存必要关联信息 |
| 400 | `PARAM_INVALID` | 账号、JSON、查询参数或幂等键不符合要求 | 修正参数 |
| 403 | `OPERATOR_FORBIDDEN` | 没有可用授权部门 | 核对用户部门与客户端白名单 |
| 404 | `USER_NOT_FOUND` | 账号不存在或已删除 | 提示修改绑定账号 |
| 422 | `USER_DISABLED` | 账号存在但已停用 | 使用启用账号 |

鉴权、限流、正文大小和系统错误还可能返回第十一节公共错误码。网络/系统故障不能当作账号不存在。

## 五、D01 查询基础字典

### 5.1 接口说明与参数

`GET /open-api/v1/dictionaries/{type}`，权限 `dictionary:read`，无正文、不传幂等键。

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `type` | 字典类型 | Path | string | 是 | 取值见下表 |
| `deptId` | 部门ID | Query | string | 条件 | 除departments外均必填，且是客户授权有效部门 |
| `projectId` | 项目筛选 | Query | string | 否 | 传入须为正整数ID；仅stores按库位关联项目精确筛选，其他类型忽略；省略返回该部门全部库位 |
| `parentId` | 父部门筛选 | Query | string | 否 | 正整数ID，按返回项parentId精确过滤；只建议departments使用，其他字典通常得到空列表 |
| `roleCode` | 人员角色 | Query | string | 条件 | approval-users必填且须符合角色表；其他字典类型忽略 |
| `keyword` | 名称关键词 | Query | string | 否 | 最长100；名称包含匹配 |
| `pageNum` | 页码 | Query | integer | 否 | 默认1，范围1—1000000 |
| `pageSize` | 每页条数 | Query | integer | 否 | 默认100，范围1—200 |

不得重复或传未知参数。`projectId`和`parentId`只要传入就校验正整数ID；各筛选参数的实际生效范围见上表。字典数据可能变更，P01创建时会重新校验；同名记录以ID区分，调用方应遍历全部分页。

### 5.2 字典类型及联动

| type | 名称 | 返回/联动规则 | 创建时使用 |
| --- | --- | --- | --- |
| `departments` | 部门 | 客户授权范围内启用未删部门；可按parentId筛选 | `departmentId = id` |
| `suppliers` | 供应商 | 现有全局供应商/双角色合作商；额外返回requiresProject | `supplierId = id` |
| `projects` | 项目 | 现有全局项目字典 | `projectId = id` |
| `stores` | 库位 | 按deptId，可选按projectId筛选；返回relation | `storeId = id` |
| `provinces` | 省份 | 部门省份字典；没有外部行政区划编码 | `provinceId = id` |
| `sale-channels` | 销售渠道 | 部门销售渠道 | `saleChannelId = id` |
| `payment-methods` | 付款方式 | 部门付款方式 | `paymentMethodId = id` |
| `fund-plans` | 资金计划 | 部门资金计划 | `fundPlanId = id` |
| `transport-costs` | 运费成本 | 部门字典中可解析的非负金额；额外value、unit | `transportCost = value` |
| `tax-rates` | 税率 | 固定0、0.06、0.13；额外value、unit | `taxRate = value` |
| `cash-cycles` | 变现周期 | 部门字典；额外value、unit，单位天 | `cashCycle = value` |
| `approval-users` | 审批人员 | 具有指定有效角色的启用用户；仅PRODUCT_MANAGER限定本部门，其他角色允许跨部门 | 对应人员字段 `= id` |

省份必须用121字典ID，例如 `"6"`；不能使用 `"110000"` 等行政区划编码代替。供应商 `requiresProject=true` 时P01必须提交项目；选定库位的非零项目关联应与提交项目一致。按projectId筛选时，只返回精确匹配的库位。

### 5.3 审批角色枚举

| roleCode | 含义 | P01对应参数 |
| --- | --- | --- |
| `PRODUCT_MANAGER` | 产品经理 | `productManagerUserId` |
| `DEPARTMENT_MANAGER` | 部门经理 | `departmentManagerUserId` |
| `DEPARTMENT_LEADER` | 分管领导 | `departmentLeaderUserId` |
| `BUSINESS_REVIEWER` | 业务审核 | `businessReviewerUserId` |
| `BUDGET_REVIEWER` | 预算审核 | `budgetReviewerUserId` |
| `CHIEF_ACCOUNTANT` | 总会计师 | `chiefAccountantUserId` |
| `GENERAL_MANAGER` | 总经理 | `generalManagerUserId` |

### 5.4 返回字段与枚举含义

| 返回字段 | 类型 | 出现条件 | 含义与取值 |
| --- | --- | --- | --- |
| `data.pageNum` | integer | 始终 | 本次页码 |
| `data.pageSize` | integer | 始终 | 本次每页条数 |
| `data.total` | integer | 始终 | 全部筛选结果总条数，不是总页数 |
| `data.items` | array | 始终 | 当前页数据；无匹配或超出末页时为 `[]` |
| `items[].id` | string | 所有字典 | 字典项标识；税率ID是比例字符串，不应按普通正整数ID解析 |
| `items[].code` | string | 所有字典 | 业务编码；未维护时为空字符串 |
| `items[].name` | string | 所有字典 | 显示名称 |
| `items[].status` | string | 所有字典 | 固定 `ACTIVE`，表示本次返回的有效项 |
| `items[].parentId` | string | 部门 | 父部门ID；根层级可能为 `"0"` |
| `items[].requiresProject` | boolean | 供应商 | true：创建必须带projectId；false：项目可选 |
| `items[].relation.departmentId` | string | 库位 | 库位所属部门ID |
| `items[].relation.projectId` | string | 库位且存在值 | 关联项目ID；`"0"` 表示未绑定具体项目 |
| `items[].value` | number / string | 运费、税率、变现周期 | 实际业务值，创建时传value；不能把字典项ID当金额/天数 |
| `items[].unit` | string | 同上 | `CNY`=人民币元；`RATIO`=比例；`DAY`=天 |
| `items[].loginAccount` | string | 审批人员 | 人员的121登录账号 |
| `items[].departmentIds` | array of string | 审批人员 | 人员成员部门与客户白名单交集中的ID；跨部门审批人可为空数组，不代表不可选 |

### 5.5 请求与响应示例

```http
GET /open-api/v1/dictionaries/provinces?deptId=80001&pageNum=1&pageSize=100
```

HTTP 200，省份示例：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "pageNum": 1,
    "pageSize": 100,
    "total": 1,
    "items": [{"id":"6","code":"","name":"北京","status":"ACTIVE"}]
  }
}
```

以下仅展示不同字典的 `items[]` 结构；仍使用同样的分页响应外壳。

```json
{
  "supplierItem": {"id":"1001","code":"","name":"示例供应商","status":"ACTIVE","requiresProject":true},
  "storeItem": {"id":"3001","code":"","name":"示例直运库","status":"ACTIVE","relation":{"departmentId":"80001","projectId":"2001"}},
  "taxRateItem": {"id":"0.13","code":"","name":"13%","status":"ACTIVE","value":0.13,"unit":"RATIO"},
  "cashCycleItem": {"id":"7","code":"","name":"30","status":"ACTIVE","value":30,"unit":"DAY"},
  "approvalUserItem": {"id":"101","code":"","name":"张三","status":"ACTIVE","loginAccount":"zhangsan","departmentIds":["80001"]}
}
```

### 5.6 业务返回码

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 查询成功，无数据也成功 | 遍历分页或展示空列表 |
| 400 | `PARAM_INVALID` | 缺deptId、无效分页/roleCode、未知或不合法参数 | 修正参数 |
| 403 | `OPERATOR_FORBIDDEN` | 部门超出客户端授权范围 | 核对授权 |
| 404 | `NOT_FOUND` | 不支持的字典type | 使用5.2节枚举 |
| 422 | `REFERENCE_INVALID` | 部门不存在、停用或已删除 | 重新查询可用部门 |

另见公共错误码。

## 六、P01 创建采购草稿

### 6.1 接口说明

`POST /open-api/v1/purchase-drafts`，权限 `purchase-draft:create`。JSON正文，不接受查询参数，必须带 `Idempotency-Key`。成功创建草稿，原子保存主单、全部明细及来源关联；校验失败整体回滚，不新增供应商/物料。

### 6.2 主单请求参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `sourceSystem` | 来源系统 | string | 是 | 固定 `INTELLIGENT_OFFICE`，表示智能化办公 |
| `sourceOrderId` | 来源采购单号 | string | 是 | 1—64位ASCII字母、数字、下划线、短横线；同来源稳定不变 |
| `syncBatchId` | 本轮同步批次 | string | 是 | UUID；同轮重试不变，确认删除后重新同步用新UUID |
| `operatorLoginAccount` | 实际操作人账号 | string | 是 | 最长30，启用未删用户，属于departmentId |
| `initiatorLoginAccount` | 采购发起人账号 | string | 是 | 必须与operatorLoginAccount相同，本版本不支持代发 |
| `departmentId` | 业务部门 | string | 是 | 用户所属、客户端授权且启用的121部门ID |
| `supplierId` | 供应商 | string | 是 | D01 suppliers返回的ID；同步后不可改供应商 |
| `supplierName` | 供应商展示名称 | string | 否 | 最长100，不用于查找、不更新主数据；ID不存在时用于提示，未提供名称则提示ID；有效时以ID对应名称为准 |
| `authorized` | 是否授权 | boolean | 是 | true=授权；false=未授权，false是合法值，不等同未填 |
| `projectId` | 项目 | string | 条件 | 供应商requiresProject=true时必填；提供时必须有效 |
| `storeId` | 库位 | string | 是 | 属departmentId；存在非零项目关联时与提交项目一致 |
| `provinceId` | 省份 | string | 是 | 当前部门省份字典ID，不是行政区划编码 |
| `saleChannelId` | 销售渠道 | string | 是 | 当前部门渠道字典ID |
| `paymentMethodId` | 付款方式 | string | 否 | 提供时为当前部门有效付款方式ID |
| `fundPlanId` | 资金计划 | string | 是 | 当前部门资金计划字典ID |
| `transportCost` | 运费成本 | number | 是 | 非负金额，单位元 |
| `taxRate` | 主单税率 | number | 是 | 0=0%；0.06=6%；0.13=13% |
| `cashCycle` | 变现周期 | integer | 是 | 1—36500，单位天 |
| `budgetMonth` | 预算月份 | string | 是 | 2000—2099年有效 `YYYY-MM`，由智能化传入 |
| `purchaseDate` | 采购日期 | string | 是 | 有效 `yyyy-MM-dd`，显式传入，不由121自动取日期 |
| `contractNo` | 合同号 | string | 否 | 最长64 |
| `externalPurchaseOrderNo` | 外部采购单号 | string | 否 | 最长64，业务备注关联号；不等于121内部ID |
| `remark` | 主单备注 | string | 否 | 最长500 |
| `details` | 采购明细 | array | 是 | 1—500条，字段见6.4 |

不要提交状态、创建人、121主单ID等额外字段。字符串拒绝控制字符和尖括号。未填写的可选ID应省略，不传空字符串。

### 6.3 可选审批人员

以下都是string类型、可选、正整数用户ID；提供时用户须启用未删且具有相应有效角色。仅产品经理须属于当前部门，其他审批角色按现有页面允许跨部门。通过D01的approval-users取得ID，不能直接传姓名；departmentIds可能为空数组，不代表该跨部门审批人不可选。121按现有业务字段保存人员姓名。

智能化按需求将产品经理默认设为制单人（采购发起人），其他人员按双方维护的默认配置选择，提交前可调整。默认人员仍须符合上述角色与有效性条件；没有匹配人员时应先补齐121人员配置，121不按姓名猜测或自动新增人员。

| 参数名 | 名称 | D01 roleCode |
| --- | --- | --- |
| `productManagerUserId` | 产品经理 | `PRODUCT_MANAGER` |
| `departmentManagerUserId` | 部门经理 | `DEPARTMENT_MANAGER` |
| `departmentLeaderUserId` | 分管领导 | `DEPARTMENT_LEADER` |
| `businessReviewerUserId` | 业务审核 | `BUSINESS_REVIEWER` |
| `budgetReviewerUserId` | 预算审核 | `BUDGET_REVIEWER` |
| `chiefAccountantUserId` | 总会计师 | `CHIEF_ACCOUNTANT` |
| `generalManagerUserId` | 总经理 | `GENERAL_MANAGER` |

### 6.4 明细请求参数

以下字段位于 `details[]` 中。

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `sourceDetailId` | 来源明细号 | string | 是 | 最长64，当前单据内唯一，用于映射返回detailId |
| `materialCode` | 物料编码 | string | 是 | 最长64，必须唯一匹配121现有物料 |
| `quantity` | 采购数量 | integer | 是 | 1—999999999 |
| `purchasePrice` | 采购单价 | number | 是 | 非负金额，单位元 |
| `purchaseAmount` | 采购金额 | number | 否 | 提供时必须精确等于quantity×purchasePrice；省略由121计算 |
| `salesQuantity` | 销售数量 | integer | 是 | 0—999999999 |
| `salesPrice` | 销售单价 | number | 是 | 非负金额，销售金额由121计算 |
| `inventoryQuantity` | 当日库存 | integer | 是 | 0—999999999，由智能化提供 |
| `detailTaxRate` | 明细税率 | number | 否 | 0、0.06或0.13；省略继承主单taxRate |
| `materialName` | 物料展示名称 | string | 否 | 最长100，仅接收校验，不更新物料描述 |
| `productCode` | 产品编码 | string | 否 | 最长64，不更新物料主数据 |
| `brandName` | 品牌名称 | string | 否 | 最长100，不更新品牌主数据 |
| `remark` | 明细备注 | string | 否 | 最长500 |

多个物料编码不存在时一次返回全部缺失编码；编码对应多个物料时报 `MATERIAL_AMBIGUOUS`。未命中物料不能自行创建或按名称匹配。

供应商ID不存在时返回422 `REFERENCE_INVALID`，message为“供应商xxx，在121系统未找到，请先核实供应商再推送至121”；xxx优先使用supplierName，未提供名称时使用supplierId，名称不参与匹配。物料缺失返回422 `MATERIAL_NOT_FOUND`，message为“物料编码xxx、xxx在121系统未找到，请先核实物料编码再推送至121”，一次列出全部缺失编码。

### 6.5 返回字段

P01、P02、P03和P04使用相同业务对象。

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `data.draftId` | string | 121采购主单ID，即现有apply_id |
| `data.purchaseOrderNo` | string | 本版本与draftId相同的采购单序号，未另建编号规则 |
| `data.sourceSystem` | string | 来源系统，当前为INTELLIGENT_OFFICE |
| `data.sourceOrderId` | string | 智能化来源订单号 |
| `data.syncBatchId` | string | 本轮同步UUID，用于查询、重试和防止旧事件覆盖新批次 |
| `data.status` | string | 当前采购状态；全部取值和含义见3.2节 |
| `data.details` | array | 来源明细与121明细ID的映射 |
| `data.details[].sourceDetailId` | string | 创建时传入的来源明细号 |
| `data.details[].detailId` | string | 121采购明细ID |

### 6.6 请求与响应示例

除公共鉴权头外再带 `Idempotency-Key: intelligent-office:PC202611170001:550e8400-e29b-41d4-a716-446655440001`。

```json
{
  "sourceSystem": "INTELLIGENT_OFFICE",
  "sourceOrderId": "PC202611170001",
  "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
  "operatorLoginAccount": "zhangsan",
  "initiatorLoginAccount": "zhangsan",
  "departmentId": "80001",
  "supplierId": "1001",
  "authorized": true,
  "projectId": "2001",
  "storeId": "3001",
  "provinceId": "6",
  "saleChannelId": "4001",
  "fundPlanId": "6001",
  "transportCost": 0,
  "taxRate": 0.13,
  "cashCycle": 30,
  "budgetMonth": "2026-11",
  "purchaseDate": "2026-11-17",
  "details": [
    {
      "sourceDetailId": "line-1",
      "materialCode": "MAT-001",
      "quantity": 10,
      "purchasePrice": 100,
      "purchaseAmount": 1000,
      "salesQuantity": 10,
      "salesPrice": 120,
      "inventoryQuantity": 50
    }
  ]
}
```

成功响应，HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "draftId": "12345",
    "purchaseOrderNo": "12345",
    "sourceSystem": "INTELLIGENT_OFFICE",
    "sourceOrderId": "PC202611170001",
    "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "DRAFT",
    "details": [{"sourceDetailId":"line-1","detailId":"67890"}]
  }
}
```

缺失多个物料的失败示例，HTTP 422：

```json
{
  "code": "MATERIAL_NOT_FOUND",
  "message": "物料编码MAT-001、MAT-002在121系统未找到，请先核实物料编码再推送至121",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

### 6.7 幂等、超时与重新同步

| 场景 | 处理结果/要求 |
| --- | --- |
| 同客户、同批次、同幂等键、同原始正文摘要 | 返回已有单当前状态及原ID，不重复创建；可能返回DELETED |
| 批次或幂等键与原请求组合/内容不一致 | 409 IDEMPOTENCY_CONFLICT |
| 同sourceSystem+sourceOrderId仍有有效121关联 | 409 SOURCE_ALREADY_SYNCED，先查询已有单 |
| 创建超时、网关502/504、网络中断 | 先P02查本批次；查不到再用原批次、原键、原始正文重试 |
| 每次HTTP重试 | 更新鉴权头，但业务批次、幂等键、正文都不变 |
| 原草稿已确认DELETED | 新批次UUID、新幂等键再创建；旧批次保留历史可查询 |

正文空格或属性顺序变化也会改变摘要。不要用“超时后直接更换批次”规避幂等。建议幂等键格式为 `intelligent-office:<sourceOrderId>:<syncBatchId>`。

### 6.8 业务返回码

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 新建成功或命中原请求 | 保存主单及明细映射，并判断当前status |
| 400 | `PARAM_INVALID` | 缺字段、格式/类型/金额精度/范围或幂等键不合法 | 修正完整正文 |
| 403 | `OPERATOR_FORBIDDEN` | 部门越权、用户不属部门、操作人与发起人不同 | 核对授权，不自动重试 |
| 404 | `USER_NOT_FOUND` | 操作人不存在或已删除 | 重新绑定有效账号 |
| 409 | `IDEMPOTENCY_CONFLICT` | 批次/键已用于不一致请求 | 查询原请求，不能覆盖原结果 |
| 409 | `SOURCE_ALREADY_SYNCED` | 来源订单存在有效121单据 | 查询已有批次，勿重复创建 |
| 422 | `USER_DISABLED` | 操作人已停用 | 更换可用账号 |
| 422 | `REFERENCE_INVALID` | 供应商不存在、字典/审批人或联动关系不可用 | 核实供应商提示，或重新拉取字典并修正 |
| 422 | `MATERIAL_NOT_FOUND` | 一个或多个物料编码不存在 | 在121补齐/核实后重新推送 |
| 422 | `MATERIAL_AMBIGUOUS` | 物料编码匹配多条记录 | 121消除重复数据后重试 |
| 422 | `AMOUNT_MISMATCH` | 采购金额与数量×单价不同 | 修正金额或省略purchaseAmount |
| 503 | `SYSTEM_BUSY` | 事务竞争、集成未启用或公共设施暂不可用 | 按原业务批次退避重试；持续发生联系121 |

还可能返回公共鉴权/限流/系统错误。遇到500先查询结果，不应直接认为未创建。

## 七、P02 按来源和批次查询草稿

### 7.1 接口与请求参数

`GET /open-api/v1/purchase-drafts/by-source`，权限 `purchase-draft:read`，无正文、不传幂等键。

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `sourceSystem` | 来源系统 | Query | string | 是 | 使用创建时的INTELLIGENT_OFFICE；查询入口只校验非空及长度≤128码点 |
| `sourceOrderId` | 来源订单号 | Query | string | 是 | 使用创建时的来源订单号；非空且≤128码点 |
| `syncBatchId` | 同步批次 | Query | string | 是 | 使用创建时的UUID；查询入口只校验非空及长度≤128码点，不自动返回最近批次 |

### 7.2 返回字段与示例

`data`字段逐项见6.5节；`data.status`的所有值见3.2节。仅查询本客户端及授权部门范围内的批次。删除后仍返回原ID和明细映射，状态为DELETED。

```http
GET /open-api/v1/purchase-drafts/by-source?sourceSystem=INTELLIGENT_OFFICE&sourceOrderId=PC202611170001&syncBatchId=550e8400-e29b-41d4-a716-446655440001
```

HTTP 200：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "draftId": "12345", "purchaseOrderNo": "12345",
    "sourceSystem": "INTELLIGENT_OFFICE", "sourceOrderId": "PC202611170001",
    "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "DRAFT",
    "details": [{"sourceDetailId":"line-1","detailId":"67890"}]
  }
}
```

### 7.3 业务返回码

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 找到本轮结果 | 依据status恢复本地同步结果 |
| 400 | `PARAM_INVALID` | 缺参数或参数不合法 | 使用原创建批次参数 |
| 404 | `DRAFT_NOT_FOUND` | 批次不存在、主单不存在或不在本客户/授权部门范围 | 超时恢复时核对原参数，再按原业务请求重试；不得据此取消其他批次 |

公共错误码仍适用。P02不是按来源查询“最新一单”，syncBatchId必须准确。三个查询字段均禁止控制字符及尖括号；查询入口不另校验来源常量、订单号格式或UUID格式，格式错误且无匹配时返回404 DRAFT_NOT_FOUND。

## 八、P03 按121采购单ID查询草稿

### 8.1 接口与参数

`GET /open-api/v1/purchase-drafts/{draftId}`，权限 `purchase-draft:read`。不接受查询参数，正文为空，不传幂等键。

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `draftId` | 121采购单ID | Path | string | 是 | P01返回的正整数ID，只支持本接口同步来源单 |

### 8.2 返回与示例

响应外壳见3.1节，完整data字段见6.5节，全部status含义见3.2节。

```http
GET /open-api/v1/purchase-drafts/12345
```

HTTP 200，锁单示例：

```json
{
  "code": "SUCCESS", "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "draftId": "12345", "purchaseOrderNo": "12345",
    "sourceSystem": "INTELLIGENT_OFFICE", "sourceOrderId": "PC202611170001",
    "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "LOCKED", "details": [{"sourceDetailId":"line-1","detailId":"67890"}]
  }
}
```

### 8.3 业务返回码

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 查询成功 | 以status判断当前状态 |
| 400 | `PARAM_INVALID` | draftId或附加参数不合法 | 修正请求 |
| 404 | `DRAFT_NOT_FOUND` | 无同步来源记录、不存在、其他客户或超授权部门 | 核对ID/授权，不当作可重新创建的充分依据 |

公共错误码仍适用。

## 九、P04 取消同步并删除草稿

### 9.1 接口说明与参数

`POST /open-api/v1/purchase-drafts/{draftId}/cancel`，权限 `purchase-draft:cancel`。不传查询参数和幂等键。取消等同软删除121对应采购草稿，并产生C01删除事件。

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `draftId` | 121采购单ID | Path | string | 是 | 本客户授权范围内的来源采购单ID |
| `operatorLoginAccount` | 操作人账号 | Body | string | 是 | 最长30，启用未删且属于该单部门 |
| `reason` | 取消原因 | Body | string | 否 | 最长500；省略或仅空格使用CANCEL_SYNC，非空原因原样写入事件 |

### 9.2 请求与响应示例

```json
{"operatorLoginAccount":"zhangsan","reason":"智能化取消同步"}
```

HTTP 200；data完整字段见6.5，成功取消返回DELETED：

```json
{
  "code": "SUCCESS", "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "draftId": "12345", "purchaseOrderNo": "12345",
    "sourceSystem": "INTELLIGENT_OFFICE", "sourceOrderId": "PC202611170001",
    "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "DELETED", "details": [{"sourceDetailId":"line-1","detailId":"67890"}]
  }
}
```

锁单后取消，HTTP 409：

```json
{
  "code": "DRAFT_NOT_EDITABLE",
  "message": "Only an active draft can be deleted",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

### 9.3 取消规则及返回码

仅DRAFT可删除；已DELETED重复调用返回同样结果，不再生成重复事件。主单、所属明细、当前来源关联和删除事件在同一事务内处理；锁单与删除使用主单行锁协调。内部退单不等同本接口取消。

| HTTP | code | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `SUCCESS` | 删除成功或该批次已删除 | 按批次清空本地关联，不影响新批次 |
| 400 | `PARAM_INVALID` | 参数无效 | 修正请求 |
| 403 | `OPERATOR_FORBIDDEN` | 操作人不属该单部门或越权 | 核对部门和操作人 |
| 404 | `USER_NOT_FOUND` | 操作人不存在/已删 | 更换有效账号 |
| 404 | `DRAFT_NOT_FOUND` | 来源单不存在或客户/部门不可见 | 核对ID及授权 |
| 409 | `DRAFT_NOT_EDITABLE` | 非草稿或并发状态已变化 | 查询当前状态，不强制删除 |
| 422 | `USER_DISABLED` | 操作人已停用 | 更换启用账号 |

另见公共错误码。HTTP重试仍需更新Nonce和Request-Id。

## 十、C01 采购草稿删除通知

### 10.1 智能化需要提供的接口

| 项目 | 内容 |
| --- | --- |
| 提供方 | 智能化后台 |
| 调用方 | 121后台 |
| 方法 | POST，JSON正文 |
| 地址 | 智能化提供固定HTTPS完整URL；不可带用户名密码、query或fragment |
| 触发 | 121页面删除来源草稿，或P04成功取消 |
| 鉴权 | 沿用第二节签名算法；使用单独反向clientId/keyId/secret，不与入口密钥混用 |
| 幂等 | eventId业务幂等；不带Idempotency-Key，每次HTTP重试换新鉴权头 |

### 10.2 通知正文参数

| 参数名 | 名称 | 类型 | 是否必填 | 含义与取值 |
| --- | --- | --- | --- | --- |
| `eventId` | 事件ID | string | 是 | UUID，同事件所有重试不变；接收方去重键 |
| `eventType` | 事件类型 | string | 是 | 固定PURCHASE_DRAFT_DELETED，表示采购草稿已删除 |
| `eventTime` | 事件发生时间 | string | 是 | UTC ISO 8601，不是本次重试时间 |
| `sourceSystem` | 来源系统 | string | 是 | 当前固定INTELLIGENT_OFFICE |
| `sourceOrderId` | 来源订单号 | string | 是 | 智能化原订单号 |
| `syncBatchId` | 被删同步批次 | string | 是 | 仅清除此UUID对应的关联 |
| `draftId` | 被删121主单ID | string | 是 | 与本地当前关联一起核对 |
| `purchaseOrderNo` | 121采购单序号 | string | 是 | 本版本与draftId相同 |
| `status` | 删除结果 | string | 是 | 固定DELETED |
| `reason` | 删除原因 | string | 是 | USER_DELETE=121页面删除；CANCEL_SYNC=未提供原因的接口取消；其他非空文本=取消方自定义原因 |

### 10.3 请求示例

```json
{
  "eventId": "31bd27f7-debf-45b2-aaf8-a22a730f0113",
  "eventType": "PURCHASE_DRAFT_DELETED",
  "eventTime": "2026-11-17T02:00:00Z",
  "sourceSystem": "INTELLIGENT_OFFICE",
  "sourceOrderId": "PC202611170001",
  "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
  "draftId": "12345",
  "purchaseOrderNo": "12345",
  "status": "DELETED",
  "reason": "USER_DELETE"
}
```

### 10.4 接收方响应与每个返回值含义

121以HTTP状态判断投递结果，**不读取回调响应的JSON业务码**。本节响应是智能化需要实现的约定，不是另一个已部署的121入口。

| HTTP返回值 | 含义 | 121行为 |
| --- | --- | --- |
| 任意2xx，例如200、204 | 事件已验签、持久处理成功，或eventId已成功处理过 | 标记DELIVERED，不再正常重试 |
| 3xx | 重定向，未视为成功 | 不跟随重定向，进入失败重试 |
| 4xx | 接收方拒绝、鉴权失败或请求错误 | 仍按失败重试，接收方应排查原因 |
| 5xx | 接收方内部错误或暂不可用 | 失败重试 |
| 超时/网络失败 | 无法确认接收结果 | 失败重试，可能重复投递 |

推荐成功返回 `204 No Content`。若返回JSON，可自定内容，但不能用HTTP200加失败业务码表达失败，否则121会视为成功。必须先完成可靠落库/处理再回2xx；重复eventId也回2xx。

### 10.5 重试、乱序与本地清理

- 至少一次投递。默认每10秒轮询，连接超时5秒、读取超时10秒；首次发送失败后按30秒、2分钟、10分钟、30分钟间隔重试，最多5次发送，随后转人工补偿队列。
- 同一事件重试保持eventId和正文；时间戳、Nonce、Request-Id和签名每次更新。进程崩溃也可能导致重投。
- 先验签和防重放，再按eventId幂等处理。必须同时核对sourceOrderId、syncBatchId、draftId，仅清除完全匹配的当前主单/明细映射。
- 旧批次通知不能清除重新同步的新单；即使不匹配当前关联，也应将该旧事件记为已处理并回2xx。
- 通知可能早于P01响应到达。智能化需保留批次已删记录，迟到的创建响应不得把已删批次恢复成“已同步”。

## 十一、完整返回码与处理建议

### 11.1 业务返回码总表

下表列出当前全部公开响应码。鉴权、限流和系统码适用于121入口；其余业务码按各接口业务校验产生。客户端按HTTP及code判断，不解析message。

| HTTP | code返回值 | 中文含义 | 产生原因 | 建议处理 |
| --- | --- | --- | --- | --- |
| 200 | `SUCCESS` | 成功 | 查询、创建或取消已处理；P01可能是幂等返回 | 读取data；按status确认业务状态 |
| 400 | `PARAM_INVALID` | 请求参数错误 | 缺少必填项，格式、类型、长度、精度/范围错误，未知/重复JSON字段，错误query/幂等键 | 修正请求，不原样自动重试 |
| 401 | `AUTH_CLIENT_NOT_FOUND` | 客户端或密钥不可用 | clientId/keyId不存在、客户端停用、入口关闭或密钥配置不可用 | 联系121核对环境和凭据 |
| 401 | `AUTH_SIGNATURE_INVALID` | 签名或鉴权头无效 | 缺头、头格式/重复、摘要不符或HMAC不符 | 核对原始字节和规范签名串 |
| 401 | `AUTH_TIMESTAMP_EXPIRED` | 请求时间过期 | 与121时间偏差大于300秒 | 校准时间并重新签名 |
| 401 | `AUTH_REPLAY_REQUEST` | 请求被判重放 | 当前客户端Nonce或Request-Id在600秒窗口内重复 | 换新鉴权头，P01保留原业务批次 |
| 403 | `AUTH_FORBIDDEN` | 缺接口权限 | 客户端未配置当前接口scope | 联系121开通最小权限 |
| 403 | `OPERATOR_FORBIDDEN` | 业务操作越权 | 用户/部门超范围、无可用部门、用户不属部门、禁止代发或变更来源单部门 | 修正用户/部门并确认授权 |
| 404 | `USER_NOT_FOUND` | 用户不存在 | 账号不存在或已删除 | 核对账号、重新绑定 |
| 404 | `DRAFT_NOT_FOUND` | 同步采购单不可见 | 没有来源记录、主单不存在、其他客户或非授权部门 | 核对ID、来源、批次与授权 |
| 404 | `NOT_FOUND` | 字典类型不存在 | D01 type不在支持列表 | 使用5.2节枚举 |
| 405 | `METHOD_NOT_ALLOWED` | 请求方法不支持 | 对已匹配路径使用不支持的HTTP方法 | 按接口约定使用GET/POST |
| 409 | `IDEMPOTENCY_CONFLICT` | 幂等冲突 | 批次/键已绑定不一致请求，或并发复用唯一键 | 查询原请求，不覆盖、不随意换批次 |
| 409 | `SOURCE_ALREADY_SYNCED` | 来源已同步 | 同来源订单已有有效采购单，含锁单及退单 | 查原单；确认DELETED后再用新批次 |
| 409 | `DRAFT_NOT_EDITABLE` | 当前状态不能删除/修改 | 已锁单、退单、删除保护或并发状态变化 | 查询状态，不强制取消 |
| 409 | `SUPPLIER_IMMUTABLE` | 来源单供应商不可更改 | 121编辑同步单时尝试变更供应商 | 保留原供应商；该码用于来源单编辑保护 |
| 413 | `PAYLOAD_TOO_LARGE` | 请求体过大 | U01超过8KiB，其他超过2MiB | 缩小请求体；P01同时最多500条 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 媒体类型或编码不支持 | 非JSON、非UTF-8声明或携带Content-Encoding压缩头 | 使用未压缩UTF-8 JSON |
| 422 | `USER_DISABLED` | 用户已停用 | 账号存在但状态非启用 | 使用启用账号 |
| 422 | `REFERENCE_INVALID` | 引用数据无效 | 供应商不存在、字典/审批人失效、部门停用或库位项目联动不符 | 供应商缺失时message提示名称（未传名称则提示ID）；核实并补齐后重新查询字典 |
| 422 | `MATERIAL_NOT_FOUND` | 物料不存在 | 一个或多个materialCode无匹配 | 核对message中的全部编码，121补齐后再推 |
| 422 | `MATERIAL_AMBIGUOUS` | 物料编码不唯一 | materialCode匹配多条物料 | 121修复重复数据 |
| 422 | `AMOUNT_MISMATCH` | 采购金额不一致 | purchaseAmount不等于quantity×purchasePrice | 修正金额或省略该字段 |
| 429 | `RATE_LIMITED` | 请求过于频繁 | 超过客户端每分钟调用配额 | 等待Retry-After指定秒数 |
| 500 | `SYSTEM_ERROR` | 内部处理失败 | 未预期异常或状态数据异常 | 留存requestId；P01先P02查询，联系121 |
| 503 | `SYSTEM_BUSY` | 暂不可用 | 防重放存储故障、事务竞争或采购集成尚未启用 | 退避重试，P01保留原批次/键/正文 |

`SUPPLIER_IMMUTABLE` 是来源单的121编辑业务保护码；当前开放接口不提供修改供应商接口。未知URL还可能由网关/框架返回普通404，不能保证包含此JSON外壳。

### 11.2 失败响应与重试分组

```json
{
  "code": "AUTH_TIMESTAMP_EXPIRED",
  "message": "请求时间戳已过期",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

| 类别 | 情况 | 操作 |
| --- | --- | --- |
| 修正后再发 | 400、401、403、404、409、415、422 | 根据具体code修正，不盲目循环重试 |
| 等待后重发 | 429、503、网关502/504、网络错误 | 建议退避2秒、5秒、15秒；429以Retry-After为准 |
| 结果不确定 | P01读取超时、500、连接在发送后中断 | 先查本批次，再按原业务请求重试 |

不要把所有非200都解释为“参数错误”，也不要把所有200都解释为“仍然有效同步”：成功查询可能返回DELETED。

## 十二、签名测试向量与联调

### 12.1 公开离线测试向量

本节密钥是**公开测试常量**，只用于离线核对，禁止用于真实环境。固定时间戳已非当前时间，直接发到在线接口会超时；真实请求需使用当前时间和正式凭据。

| 项目 | 固定值 |
| --- | --- |
| Method | POST |
| Path | /open-api/v1/users/verify |
| Query | 空 |
| 原始正文 | `{"loginAccount":"zhangsan"}`，UTF-8，无结尾换行 |
| ClientId | intelligent-office-test |
| KeyId | test-key-01 |
| Timestamp | 1794787200 |
| Nonce | 550e8400-e29b-41d4-a716-446655440000 |
| RequestId | 7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41 |
| Idempotency-Key | 空 |
| 测试密钥Base64 | AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8= |
| 正文SHA-256 | aa6b77fa18f04ad64611d521f86e77117d861ad907d8ff85560a19c426ad3a65 |
| 预期签名 | lbIOMwqPoysaiu2XwGpXrZgXN0pDRAC0pUd31Rn176g= |

Python 3标准库示例（仅离线计算，不发送请求）：

```python
import base64
import hashlib
import hmac

body = b'{"loginAccount":"zhangsan"}'
secret = base64.b64decode(
    "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", validate=True
)
body_hash = hashlib.sha256(body).hexdigest()
parts = [
    "POST", "/open-api/v1/users/verify", "",
    "intelligent-office-test", "test-key-01", "1794787200",
    "550e8400-e29b-41d4-a716-446655440000",
    "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
    "", body_hash,
]
canonical = "\n".join(parts).encode("utf-8")
signature = base64.b64encode(
    hmac.new(secret, canonical, hashlib.sha256).digest()
).decode("ascii")
assert body_hash == "aa6b77fa18f04ad64611d521f86e77117d861ad907d8ff85560a19c426ad3a65"
assert signature == "lbIOMwqPoysaiu2XwGpXrZgXN0pDRAC0pUd31Rn176g="
print("签名向量校验通过")
```

### 12.2 接入前交换信息

| 121提供 | 智能化提供 |
| --- | --- |
| 测试/生产API基础地址、开放时间 | 后台固定出口IP |
| 入口clientId/keyId及安全交换的签名密钥 | C01固定HTTPS回调地址及反向凭据 |
| scope、部门白名单、限流配置 | 账号绑定流程、采购发起人对应规则 |
| 实际可用字典、物料维护方式 | 默认项目/周期映射、必填销售及库存字段 |

### 12.3 建议联调顺序

1. 离线验证签名向量；再使用真实凭据和当前时间验证U01。
2. 验证不存在/停用/无部门用户，以及篡改、过期、重放和429错误。
3. 查询字典，遍历分页，确认供应商项目和库位联动。
4. P01创建草稿，保存主单与明细映射；同批次原文重试验证不重复创建。
5. 模拟超时，按P02恢复结果；按P03验证锁单状态，P04应拒绝删除锁单单据。
6. 分别测试121页面删除、P04取消、重复取消、C01超时及重复通知。
7. 用新批次重建后投递旧通知，确认不会清除新关联；验证通知早于创建响应时不错误恢复。

正式上线需真实MySQL、Redis、HTTPS网关及双方后台验证；网页发布不代表已完成业务联调。

## 十三、下载、版本与规范参考

### 13.1 下载

- [完整OpenAPI 3.1 YAML](./downloads/openapi.yaml)
- [完整OpenAPI 3.1 JSON](./downloads/openapi.json)
- [U01独立OpenAPI YAML](./downloads/u01.openapi.yaml)
- [本页Markdown](./downloads/api-reference.md)

可导入支持OpenAPI 3.1的Apifox、Swagger等工具。导入或点击普通Authorize按钮不会自动完成HMAC签名，应由调用方脚本或请求钩子实现。

### 13.2 版本记录

| 日期 | 文档/契约版本 | 内容 |
| --- | --- | --- |
| 2026-11-17 | 1.1.0 | 审查修订：统一部门归属限制、供应商/物料缺失提示、U01失败响应和机器契约、字典与来源查询实际边界 |
| 2026-11-17 | 1.1.0 | 整理公开网页，补齐逐字段响应含义、枚举、分接口返回码、回调确认规则与折叠示例；不改变接口行为 |
| 2026-11-16 | 1.1.0 | 完整采购同步接口与代码实现对齐，确认现有取价、库存字段及省份ID规则 |

URL主版本为v1。调用方忽略新增的未知响应字段；删除字段、改变类型或错误码含义需双方评审或升级版本。地址、凭据和授权范围以双方实际交付为准，不从示例推断。

### 13.3 规范参考

- [OpenAPI Specification 3.1](https://spec.openapis.org/oas/v3.1.0.html)
- [RFC 9110：HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [RFC 9111：HTTP Caching](https://www.rfc-editor.org/rfc/rfc9111)
- [AWS请求签名规范化参考](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_sigv-create-signed-request.html)

本接口采用121自定义HMAC协议，不兼容AWS SigV4、OAuth或员工登录协议。
