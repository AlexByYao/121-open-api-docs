# 121 接口交接文档

版本 **2.5.0** · 文档更新 **2026-11-21** · 服务提供方 **121系统**

本文定义121开放接口的请求方式、鉴权规则、参数、响应、状态及错误码。示例数据仅用于说明报文格式；接入凭据和测试数据由121服务提供方提供。

## 接入说明

### 联调顺序

1. 获取测试环境App-Id、App-Secret、有效账号及业务测试数据。
2. 在请求头填写App-Id、App-Secret和loginAccount，调用U01查询账号及可用部门。
3. 从U01返回的departments确定deptId，再调用D01获取供应商、项目、库位及审批人等字典；提交121的ID，不以名称代替。
4. 准备P01所需字段，固定本轮syncBatchId、Idempotency-Key和请求正文，再创建采购草稿；保存返回的draftId及明细ID映射。
5. 调用P02或P03查询结果与状态；创建超时先用P02确认，再决定是否按原批次、原幂等键、原正文重试。
6. 仅对未锁单草稿调用P04取消；以DELETED确认删除。C01为121主动发送的删除通知，接收地址及反向凭据配置完成后单独验收。

### 编号、幂等与重试

| 字段 | 产生方与用途 | 何时变化 |
| --- | --- | --- |
| sourceOrderId | 来源采购单稳定标识 | 同一来源采购单不变，删除后再同步也不改 |
| syncBatchId | 调用方生成UUID，标识一轮同步 | 本轮查询、重试不变；确认旧轮DELETED后再同步才新建 |
| Idempotency-Key | 调用方生成的P01业务幂等键 | 同轮同正文不变；新轮换新键，只有P01提交该Header |
| requestId | 121自动生成的本次请求追踪编号 | 每次响应生成，排查问题时提供 |
| draftId、purchaseOrderNo | 121返回的采购单ID，两字段值相同 | 创建后保存，不能自行生成；重新同步可能得到新ID |
| sourceDetailId、detailId | 来源明细ID与121初始明细ID | 发送前固定来源行ID，成功后保存两者映射 |
| eventId | 121生成的C01事件去重键 | 同一事件重试不变；接收方按eventId幂等处理 |

同一批次的重复请求须使用相同的幂等键和原始正文。结果未知时先调用P02；返回404时，应核对查询参数和访问范围，再使用原批次、原幂等键及原始正文重试。

### 调用地址

| 环境 | API基础地址 | 说明 |
| --- | --- | --- |
| 测试环境 | `http://testfcst.chinaptac.com/open-api/v1` | 允许HTTP联调，使用测试凭据 |
| 正式环境 | `https://fcst.chinaptac.com/open-api/v1` | 使用HTTPS和正式凭据 |

调用地址 = API基础地址 + 接口路径。例如用户查询为`POST http://testfcst.chinaptac.com/open-api/v1/users/verify`。测试、正式凭据分别交付；文档网站仅供查阅，不接收业务请求。C01由接收方提供固定回调地址，正式使用HTTPS，测试可按双方约定使用HTTP。

### 接口目录

以下路径相对于基础地址 `/open-api/v1`。C01是121主动发送的删除通知。

| 编号 | 接口名称 | 方法 | 路径 |
| --- | --- | --- | --- |
| U01 | 查询121用户信息 | POST | `/users/verify` |
| D01 | 查询基础字典 | GET | `/dictionaries/{type}` |
| P01 | 创建采购草稿 | POST | `/purchase-drafts` |
| P02 | 按来源和批次查询草稿 | GET | `/purchase-drafts/by-source` |
| P03 | 按121采购单ID查询草稿 | GET | `/purchase-drafts/{draftId}` |
| P04 | 取消同步并删除草稿 | POST | `/purchase-drafts/{draftId}/cancel` |
| C01 | 采购草稿删除通知 | POST | 接收方提供的固定回调地址 |

请求须携带有效的应用凭据和121操作人账号。可访问的部门范围为该账号所属有效部门与应用授权部门的交集。采购单操作人和发起人均为请求头中的`loginAccount`。

### 部门参数与权限规则

部门参数使用`deptId`，部门ID列表使用`deptIds`。各接口的传参位置及要求如下。

| 接口或字段 | 部门参数规则 |
| --- | --- |
| U01用户查询 | 不传deptId；返回departments数组，部门对象自身仍使用id |
| D01部门字典 | 不要求deptId，返回当前账号可用部门 |
| D01其他字典 | Query必填deptId；库位返回relation.deptId，审批人员返回deptIds |
| P01创建草稿 | Body必填deptId，确定本单所属部门 |
| P02/P03查询、P04取消 | 不额外传deptId；121根据来源记录确定部门并校验权限 |
| 错误详情 | 返回部门上下文时使用data.deptId |

业务部门须处于“账号有效所属部门 ∩ 应用授权部门”范围；多部门账号创建时须明确提交一个deptId。查询、取消均校验原单所属部门，不能通过传入其他部门改变单据归属。

### 数据约定

- ID使用JSON字符串，避免大整数丢精度；必填ID为正整数形式。字典中的 `code` 是业务编码，不能替代 `id`。
- 所有金额以人民币元计，使用十进制数，非负、最多两位小数；单价、金额及采购合计最大 `9999999999999.99`。
- 数量使用整数，最大 `999999999`。税率传比例：`0.13` 表示13%，不能传 `13`。
- `cashCycle`为采购变现周期值，按字典返回值提交；`budgetMonth` 为 `YYYY-MM`；`purchaseDate` 为 `yyyy-MM-dd`。回调 `eventTime` 为UTC ISO 8601，例如 `2026-11-17T02:00:00Z`。
- P01必填预算月份、销售数量、销售单价和当日库存。明细提交价格保存于采购草稿，不更新物料基础价格。
- 供应商与项目、库位的关联按D01字典及P01字段约束校验；一张采购单只提交一个saleChannelId。
- 采购草稿的供应商和所属部门不可修改。P04仅允许取消草稿，已锁单及退单状态不可取消。

六个121入口均使用App-Id、App-Secret和公共loginAccount。每次请求携带121交付的两项固定凭据及当前操作人账号，无需员工密码或Token。应用密钥仅在服务器端保存。正式环境使用HTTPS，测试允许HTTP；HTTP会明文传输凭据和正文，仅使用测试凭据与测试数据。

### 公共请求头参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `App-Id` | 调用方应用编号 | string | 是 | 121分配的固定值，与本环境配置完全一致 |
| `App-Secret` | 应用密钥 | string | 是 | 121交付的固定密钥，原样传入，无需编码或计算 |
| `loginAccount` | 当前121操作人账号 | string | 六个121入口必填 | 用户须存在、启用且有可用授权部门；返回账号为规范loginAccount，不是姓名/手机号/ID。C01系统通知不携带此项 |
| `Idempotency-Key` | 业务幂等键 | string | 仅P01必填 | 16—160字符；ASCII字母、数字及 `._:-`；其他接口不能传 |
| `Content-Type` | 正文媒体类型 | string | POST必填 | `application/json` 或 `application/json; charset=utf-8` |
| `Accept` | 期望响应类型 | string | 建议 | `application/json` |

Header名称不区分大小写，两项凭据的值区分大小写。凭据为1—256个可打印ASCII字符（不含空格），不得重复传头或附加空格、换行；不得放入URL、正文、客户端代码或日志。

loginAccount按账号原值匹配，清理首尾ASCII空格后1—30个Unicode码点，禁止内部空白、控制及格式字符；解码前输入最长1536字符，解码后原输入最多128码点。普通ASCII账号直接传；含中文、百分号等字符时先做UTF-8百分号编码（JavaScript用`encodeURIComponent(account)`）。例如张三传`%E5%BC%A0%E4%B8%89`，`a%b`传`a%25b`，`a+b`可传`a%2Bb`。121严格解码一次，加号不当空格，不接受非法UTF-8或重复loginAccount请求头。

每次先校验应用凭据，再查验账号与有效部门交集，最后执行接口业务。loginAccount用于声明本次操作人，不能单独作为登录凭据；账号使用权限由调用方验证。公共校验失败时不会进入业务处理。

### 正文与重试

| 规则 | 当前约定 |
| --- | --- |
| 正文 | UTF-8、不带BOM、不压缩；GET正文为空，POST正文是JSON对象；U01另允许空正文，仍须Content-Type |
| 大小 | U01最大8192字节（8KiB），其他接口最大2097152字节（2MiB） |
| 严格JSON | 拒绝未知属性、重复属性、错误标量类型、非整数数量、尾随JSON及非法UTF-8 |
| 服务暂不可用 | 服务暂不可用时返回503 `SYSTEM_BUSY` |
| 每次HTTP重试 | 沿用本环境App-Id、App-Secret及本轮loginAccount |
| P01业务重试 | 同时保留原loginAccount、`syncBatchId`、`Idempotency-Key`及原始正文；先按P02查询 |

### 统一响应字段

```json
{
  "code": 200, "errorCode": null,
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {}
}
```

| 返回字段 | 类型 | 含义 | 取值说明 |
| --- | --- | --- | --- |
| `code` | integer | 数字结果码 | 成功为数字 `200`；失败与HTTP状态相同，如400、401、403、404、405、409、413、415、422、500、503 |
| `errorCode` | string / null | 具体业务错误原因 | 成功为null；失败为“返回码说明”列出的稳定业务码，如MATERIAL_NOT_FOUND |
| `message` | string | 结果说明 | 供排查使用；文字可变化，程序不能通过解析message判断结果 |
| `requestId` | string | 本次请求追踪编号 | 由121自动生成，不需要调用方传入，反馈问题时一并提供 |
| `data` | object / null | 业务返回内容 | 成功为对应接口对象；失败可为结构化错误详情，无详情时为 `null`；SOURCE_ALREADY_SYNCED返回原单对象 |

成功HTTP为200；失败使用实际HTTP状态码。所有应用响应带 `Cache-Control: no-store`；401额外带 `WWW-Authenticate: AppKey realm="121-open-api"`。网关502/504可能没有JSON正文，应按HTTP失败处理。

响应体`code`为数字，表示本次处理结果；字典项中的`code`为字符串，表示业务编码。调用方应结合HTTP状态和`errorCode`判断结果，使用`message`及`data`定位原因。

### 错误详情字段与取值

以下字段按错误场景选择返回，不保证每次失败都有详情。明细下标从0开始，`sourceDetailId`便于调用方定位自己的明细。未知异常、鉴权错误等仍可能`data=null`，请保留requestId用于问题查询。

| data字段 | 类型 | 含义 |
| --- | --- | --- |
| `field` | string | 字段路径，如`details[0].purchasePrice`；`details.materialCode`表示汇总物料错误 |
| `sourceDetailId` | string | 对应来源行ID；能可靠识别时提供 |
| `reason` | string | 具体原因，全部取值见下表 |
| `referenceType` | string | 引用类型，例如`sale-channels`、`supplier`或`approver` |
| `referenceId` | string / null | 请求中提交的引用ID；缺失时可为null |
| `deptId` | string | 引用校验使用的部门ID |
| `roleCode` | string | 审批角色，取值见D01“审批角色枚举” |
| `missingMaterialCodes` | string[] | 本次扫描的全部缺失物料编码，去重并保留顺序 |
| `materialCode` | string | 存在重复匹配或未匹配的物料编码 |
| `matchCount` | integer | 对应物料编码匹配到的记录数 |
| `provided` | number | 调用方提交的采购金额 |
| `calculated` | number | 采购数量乘单价后的计算金额 |
| `stage` | string | 失败阶段：ORDER_CREATE或DETAIL_CREATE |
| `rolledBack` | boolean | true表示本次同步已整体回滚 |
| `errors` | object[] | 缺失/重复物料的行详情，每项使用上述field、sourceDetailId、materialCode等字段 |

| reason取值 | 含义 | 调用方处理 |
| --- | --- | --- |
| `REQUIRED` | 缺少必填项 | 补齐对应字段 |
| `INVALID_VALUE` | 格式、范围、精度、重复来源行等值不符合约束 | 按field及message修正 |
| `TYPE_INVALID` | JSON字段类型不符合接口定义 | 改为约定类型，数量不能传小数 |
| `UNKNOWN_FIELD` | 出现未定义的JSON字段 | 删除字段或核实接口版本 |
| `UNAVAILABLE` | 字典引用不在当前可用选项中 | 刷新对应部门字典并重新选择 |
| `NOT_FOUND` | 审批用户不存在 | 更换有效审批用户 |
| `DISABLED` | 审批用户已停用或删除 | 更换启用且未删除的用户 |
| `DEPARTMENT_MISMATCH` | 产品经理不属于所选部门 | 选择本部门产品经理 |
| `ROLE_MISSING` | 审批用户缺少对应有效角色 | 在121补齐角色或重新选择 |

`SOURCE_ALREADY_SYNCED`的data使用P01“返回字段”中的原单对象，含原`draftId`、`purchaseOrderNo`、`syncBatchId`和当前状态。只能在原单属于当前客户端且在授权部门内时返回；不可访问时返回404 `DRAFT_NOT_FOUND`。这仍是409冲突，不表示本次创建成功。

## U01 查询121用户信息（POST）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **POST** |
| 请求路径 | `/open-api/v1/users/verify` |
| 用途 | 查询公共loginAccount对应的基本信息及有效授权成员部门 |
| 传参位置 | loginAccount位于公共Header；Body可不传或传`{}`，不接受查询参数、幂等键 |
| Content-Type | `application/json` |
| 是否分页 | 否，部门按ID升序一次返回 |

### 请求参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `loginAccount`（Header） | 当前121账号 | string | 是 | 公共参数，规则见“公共请求头参数”；本接口返回此账号信息 |
| `loginAccount`（Body） | 可选账号 | string / null | 否 | 建议省略；提供非null值时必须与请求头loginAccount规范化后相同，否则403 OPERATOR_FORBIDDEN |

账号匹配区分原始写法，不执行大小写转换。传姓名、手机号或用户ID不能替代登录账号。返回成功只表示该账号有效，不提供账号归属认证。

### 返回字段

以下字段均位于 `data` 对象中。

| 返回字段 | 类型 | 含义与取值 |
| --- | --- | --- |
| `userId` | string | 121用户唯一ID |
| `loginAccount` | string | 121记录中的规范登录账号 |
| `userName` | string | 姓名或昵称；未维护时为空字符串 |
| `phoneNumber` | string | 手机号；未维护时为空字符串，不影响账号校验 |
| `status` | string | 成功时固定 `ACTIVE`，表示启用；停用账号返回错误，不返回DISABLED对象 |
| `departments` | array | 账号所属有效部门与应用授权部门的交集；成功响应至少包含1项 |
| `departments[].id` | string | 部门唯一ID，创建草稿时作为deptId |
| `departments[].code` | string | 业务部门编码，缺失时为空字符串 |
| `departments[].name` | string | 部门名称 |

上述字段每次成功均返回，手机号未维护时保留字段并返回`""`。用户可能有多个部门，部门对象含ID、编码和名称；不能假定其上次登录所选部门就是本次采购部门。

### 请求与响应示例

请求公共头：`loginAccount: zhangsan`；请求正文可省略，或：

```json
{}
```

成功响应，HTTP 200：

```json
{
  "code": 200, "errorCode": null,
  "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "userId": "101",
    "loginAccount": "zhangsan",
    "userName": "张三",
    "phoneNumber": "13800000000",
    "status": "ACTIVE",
    "departments": [{"id":"80001","code":"","name":"示例业务部"}]
  }
}
```

账号不存在或已删除，HTTP 404：

```json
{
  "code": 404, "errorCode": "USER_NOT_FOUND",
  "message": "121登录账号不存在",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

### 业务返回码

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 账号启用且至少一个可用授权部门 | 保存必要关联信息 |
| 400 | `PARAM_INVALID` | 账号、JSON、查询参数或幂等键不符合要求 | 修正参数 |
| 403 | `OPERATOR_FORBIDDEN` | 没有可用授权部门 | 核对用户有效成员部门及客户端授权模式/白名单 |
| 404 | `USER_NOT_FOUND` | 账号不存在或已删除 | 核对loginAccount并使用有效账号 |
| 422 | `USER_DISABLED` | 账号存在但已停用 | 使用启用账号 |

鉴权、正文大小和系统错误还可能返回“返回码说明”公共错误码。网络/系统故障不能当作账号不存在。

## D01 查询基础字典（GET）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **GET** |
| 请求路径 | `/open-api/v1/dictionaries/{type}` |
| 传参位置 | Path路径参数 + Query查询参数；无正文，不传幂等键 |
| Content-Type | 无请求正文，无需设置 |

### 请求参数

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `type` | 字典类型 | Path | string | 是 | 取值见下表 |
| `deptId` | 部门ID | Query | string | 条件 | 除departments外均必填，且在公共用户有效成员部门与应用授权的交集中 |
| `projectId` | 项目筛选 | Query | string | 否 | 传入须为正整数ID；仅stores按库位关联项目精确筛选，其他类型忽略；省略返回该部门全部库位 |
| `parentId` | 父部门筛选 | Query | string | 否 | 正整数ID，按返回项parentId精确过滤；只建议departments使用，其他字典通常得到空列表 |
| `roleCode` | 人员角色 | Query | string | 条件 | approval-users必填且须符合角色表；其他字典类型忽略 |
| `keyword` | 名称关键词 | Query | string | 否 | 最长100；名称包含匹配 |
| `pageNum` | 页码 | Query | integer | 否 | 默认1，范围1—1000000 |
| `pageSize` | 每页条数 | Query | integer | 否 | 默认100，范围1—200 |

不得重复或传未知参数。`projectId`和`parentId`只要传入就校验正整数ID；各筛选参数的实际生效范围见上表。字典数据可能变更，P01创建时会重新校验；同名记录以ID区分，调用方应遍历全部分页。

### 字典类型及联动

| type | 名称 | 返回/联动规则 | 创建时使用 |
| --- | --- | --- | --- |
| `departments` | 部门 | 本次公共用户可用交集内的有效部门；可按parentId筛选 | `deptId = id` |
| `suppliers` | 供应商 | 全局供应商及具有供应商角色的合作商；额外返回requiresProject | `supplierId = id` |
| `projects` | 项目 | 全局项目字典 | `projectId = id` |
| `stores` | 库位 | 按deptId，可选按projectId筛选；返回relation | `storeId = id` |
| `provinces` | 省份 | 部门省份字典；没有外部行政区划编码 | `provinceId = id` |
| `sale-channels` | 销售渠道 | 部门销售渠道 | `saleChannelId = id` |
| `payment-methods` | 付款方式 | 部门付款方式 | `paymentMethodId = id` |
| `fund-plans` | 资金计划 | 部门资金计划 | `fundPlanId = id` |
| `transport-costs` | 运费成本 | 部门字典中可解析的非负金额；额外value、unit | `transportCost = value` |
| `tax-rates` | 税率 | 主单下拉固定0、0.06、0.13；明细不受该枚举限制；额外value、unit | `taxRate = value` |
| `cash-cycles` | 变现周期 | 部门变现周期字典；提交value，不根据unit自行换算 | `cashCycle = value` |
| `approval-users` | 审批人员 | 具有指定有效角色的启用用户；仅PRODUCT_MANAGER限定本部门，其他角色允许跨部门 | 对应人员字段 `= id` |

省份必须用121字典ID，例如 `"6"`；不能使用 `"110000"` 等行政区划编码代替。供应商 `requiresProject=true` 时P01必须提交项目；选定库位的非零项目关联应与提交项目一致。按projectId筛选时，只返回精确匹配的库位。

### 审批角色枚举

| roleCode | 含义 | P01对应参数 |
| --- | --- | --- |
| `PRODUCT_MANAGER` | 产品经理 | `productManagerUserId` |
| `DEPARTMENT_MANAGER` | 部门经理 | `departmentManagerUserId` |
| `DEPARTMENT_LEADER` | 分管领导 | `departmentLeaderUserId` |
| `BUSINESS_REVIEWER` | 业务审核 | `businessReviewerUserId` |
| `BUDGET_REVIEWER` | 预算审核 | `budgetReviewerUserId` |
| `CHIEF_ACCOUNTANT` | 总会计师 | `chiefAccountantUserId` |
| `GENERAL_MANAGER` | 总经理 | `generalManagerUserId` |

### 返回字段与枚举含义

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
| `items[].relation.deptId` | string | 库位 | 库位所属部门ID |
| `items[].relation.projectId` | string | 库位且存在值 | 关联项目ID；`"0"` 表示未绑定具体项目 |
| `items[].value` | number / string | 运费、税率、变现周期 | 实际业务值，创建时传value；不能把字典项ID当金额/天数 |
| `items[].unit` | string | 同上 | `CNY`=人民币元；`RATIO`=比例；`DAY`=天 |
| `items[].loginAccount` | string | 审批人员 | 人员的121登录账号 |
| `items[].deptIds` | array of string | 审批人员 | 人员成员部门与客户本次部门范围交集中的ID；跨部门审批人可为空数组，不代表不可选 |

### 请求与响应示例

```http
GET /open-api/v1/dictionaries/provinces?deptId=80001&pageNum=1&pageSize=100
```

HTTP 200，省份示例：

```json
{
  "code": 200, "errorCode": null,
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
  "storeItem": {"id":"3001","code":"","name":"示例直运库","status":"ACTIVE","relation":{"deptId":"80001","projectId":"2001"}},
  "taxRateItem": {"id":"0.13","code":"","name":"13%","status":"ACTIVE","value":0.13,"unit":"RATIO"},
  "cashCycleItem": {"id":"7","code":"","name":"30","status":"ACTIVE","value":30,"unit":"DAY"},
  "approvalUserItem": {"id":"101","code":"","name":"张三","status":"ACTIVE","loginAccount":"zhangsan","deptIds":["80001"]}
}
```

### 业务返回码

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 查询成功，无数据也成功 | 按分页字段读取结果；items可为空数组 |
| 400 | `PARAM_INVALID` | 缺deptId、无效分页/roleCode、未知或不合法参数 | 修正参数 |
| 403 | `OPERATOR_FORBIDDEN` | 部门超出公共用户可用交集 | 核对授权 |
| 404 | `NOT_FOUND` | 不支持的字典type | 使用D01“字典类型及联动”枚举 |
| 422 | `REFERENCE_INVALID` | 部门不存在、停用或已删除 | 重新查询可用部门 |

另见公共错误码。

## P01 创建采购草稿（POST）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **POST** |
| 请求路径 | `/open-api/v1/purchase-drafts` |
| 传参位置 | Body JSON；不接受查询参数；Header必须带Idempotency-Key |
| Content-Type | `application/json` |

本接口创建采购草稿及全部明细，并返回采购单ID和明细ID映射。首次创建成功的状态为`DRAFT`。所有数据在同一事务中保存；任一步骤失败均整体回滚。供应商和物料须预先存在。

### 主单请求参数

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `sourceSystem` | 来源系统 | string | 是 | 固定 `INTELLIGENT_OFFICE`，为当前接口约定的来源系统编码 |
| `sourceOrderId` | 来源采购单号 | string | 是 | 1—64位ASCII字母、数字、下划线、短横线；同来源稳定不变 |
| `syncBatchId` | 本轮同步批次 | string | 是 | UUID；同轮重试不变，确认删除后重新同步用新UUID |
| `operatorLoginAccount` | 可选操作人 | string / null | 否 | 建议省略，由公共loginAccount取得；非null时须与公共账号规范化后相同，否则403 OPERATOR_FORBIDDEN |
| `initiatorLoginAccount` | 可选发起人 | string / null | 否 | 建议省略，由公共loginAccount取得；非null时须与公共账号相同，不支持代发 |
| `deptId` | 业务部门 | string | 是 | 用户所属、客户端授权且启用的121部门ID |
| `supplierId` | 供应商 | string | 是 | D01 suppliers返回的ID；同步后不可改供应商 |
| `supplierName` | 供应商名称 | string | 否 | 最长100，不用于查找、不更新主数据；ID不存在时用于错误信息，未提供名称则使用ID；有效时以ID对应名称为准 |
| `authorized` | 是否授权 | boolean | 是 | true=授权；false=未授权，false是合法值，不等同未填 |
| `projectId` | 项目 | string | 条件 | 供应商requiresProject=true时必填；提供时必须有效 |
| `storeId` | 库位 | string | 是 | 属deptId；存在非零项目关联时与提交项目一致 |
| `provinceId` | 省份 | string | 是 | 当前部门省份字典ID，不是行政区划编码 |
| `saleChannelId` | 销售渠道 | string | 是 | 当前部门渠道字典ID |
| `paymentMethodId` | 付款方式 | string | 否 | 提供时为当前部门有效付款方式ID |
| `fundPlanId` | 资金计划 | string | 是 | 当前部门资金计划字典ID |
| `transportCost` | 运费成本 | number | 是 | 非负金额，单位元 |
| `taxRate` | 主单税率 | number | 是 | 0=0%；0.06=6%；0.13=13% |
| `cashCycle` | 变现周期 | integer | 是 | 正整数，范围1—9223372036854775807；按D01变现周期字典的value提交，不自动换算单位 |
| `budgetMonth` | 预算月份 | string | 是 | 2000—2099年有效 `YYYY-MM`，由调用方传入 |
| `purchaseDate` | 采购日期 | string | 是 | 有效 `yyyy-MM-dd`，显式传入，不由121自动取日期 |
| `contractNo` | 合同号 | string | 否 | 最长64 |
| `externalPurchaseOrderNo` | 外部采购单号 | string | 否 | 最长64，业务备注关联号；不等于121内部ID |
| `remark` | 主单备注 | string | 否 | 最长500 |
| `budgetExplanation` | 超预算说明 | string | 否 | 最长500；可选说明文本，保存于采购草稿 |
| `discount` | 使用折让 | number | 否 | 非负金额，单位元；省略为0 |
| `advance` | 使用预付款 | number | 否 | 非负金额，单位元；省略为0 |
| `details` | 采购明细 | array | 是 | 1—500条，字段见“明细请求参数” |

不要提交状态、创建人、121主单ID等额外字段。字符串拒绝控制字符和尖括号。未填写的可选ID应省略，不传空字符串。

### 草稿保存及失败处理

同步成功表示采购草稿及全部明细已保存。预算是否超额、超预算说明是否填写不影响保存结果。生成采购单、锁单及导出不属于本接口的处理范围。

| 场景 | 返回结果 |
| --- | --- |
| 主单未保存成功或未返回主单ID | 422 ORDER_CREATE_FAILED，message说明具体原因，data.stage=ORDER_CREATE |
| 明细未添加成功或未返回明细ID | 422 DETAIL_CREATE_FAILED，message说明来源明细，data包含sourceDetailId、field及stage=DETAIL_CREATE |
| 字段、物料、供应商或金额校验失败 | 按对应PARAM_INVALID、MATERIAL_NOT_FOUND、REFERENCE_INVALID、AMOUNT_MISMATCH等错误码返回具体原因 |
| 主单及全部明细保存成功 | 200，返回DRAFT及主单、明细ID；预算生成条件不影响本次同步成功 |

保存失败时主单、全部明细、来源、批次和明细映射一起回滚。data.rolledBack=true表示本次已整体回滚，不存在可使用的本次单据ID；明细错误通过sourceDetailId和field定位。系统异常返回HTTP 500，调用方可通过requestId联系121查询原因。

明确收到整体回滚的保存失败后，可修正数据再提交；失败请求未占用已提交的批次和幂等键。超时、断网或结果未知时仍须先P02查询，不能直接改正文或换批次。已成功批次按原批次、原键、原正文重试，返回原单及当前状态，不重复创建。

明细保存失败示例：

```json
{
  "code": 422,
  "errorCode": "DETAIL_CREATE_FAILED",
  "message": "来源明细line-2添加失败",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "stage": "DETAIL_CREATE",
    "rolledBack": true,
    "sourceDetailId": "line-2",
    "field": "details[1].materialCode"
  }
}
```

### 可选审批人员

以下都是string类型、可选、正整数用户ID；提供时用户须启用未删且具有相应有效角色。仅产品经理须属于当前部门，其他审批角色允许跨部门。通过D01的approval-users取得ID，不能直接传姓名；deptIds可能为空数组，不代表该跨部门审批人无效。

审批人须通过D01查询获得，并满足对应角色及部门条件。

| 参数名 | 名称 | D01 roleCode |
| --- | --- | --- |
| `productManagerUserId` | 产品经理 | `PRODUCT_MANAGER` |
| `departmentManagerUserId` | 部门经理 | `DEPARTMENT_MANAGER` |
| `departmentLeaderUserId` | 分管领导 | `DEPARTMENT_LEADER` |
| `businessReviewerUserId` | 业务审核 | `BUSINESS_REVIEWER` |
| `budgetReviewerUserId` | 预算审核 | `BUDGET_REVIEWER` |
| `chiefAccountantUserId` | 总会计师 | `CHIEF_ACCOUNTANT` |
| `generalManagerUserId` | 总经理 | `GENERAL_MANAGER` |

### 明细请求参数

以下字段位于 `details[]` 中。

| 参数名 | 名称 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- |
| `sourceDetailId` | 来源明细号 | string | 是 | 最长64，当前单据内唯一，用于映射返回detailId |
| `materialCode` | 物料编码 | string | 是 | 最长64，必须唯一匹配121现有物料 |
| `quantity` | 采购数量 | integer | 是 | 1—999999999 |
| `purchasePrice` | 采购单价 | number | 是 | 非负金额，单位元 |
| `salesQuantity` | 销售数量 | integer | 是 | 0—999999999 |
| `salesPrice` | 销售单价 | number | 是 | 非负金额，销售金额由121计算 |
| `inventoryQuantity` | 当日库存 | integer | 是 | 0—999999999，由调用方提供 |
| `detailTaxRate` | 明细税率 | number | 否 | 数值范围-9999999999999.99至9999999999999.99，最多两位小数；0.09表示9%；省略或null时使用主单taxRate |
| `materialName` | 物料名称 | string | 否 | 最长100，仅接收校验，不更新物料描述 |
| `productCode` | 产品编码 | string | 否 | 最长64，不更新物料主数据 |
| `brandName` | 品牌名称 | string | 否 | 最长100，不更新品牌主数据 |
| `remark` | 明细备注 | string | 否 | 最长500 |

多个物料编码不存在时一次返回全部缺失编码；编码对应多个物料时报 `MATERIAL_AMBIGUOUS`。未命中物料不能自行创建或按名称匹配。

供应商ID不存在时返回422 `REFERENCE_INVALID`，message为“供应商xxx，在121系统未找到，请先核实供应商再推送至121”；xxx优先使用supplierName，未提供名称时使用supplierId，名称不参与匹配。物料缺失返回422 `MATERIAL_NOT_FOUND`，message为“物料编码xxx、xxx在121系统未找到，请先核实物料编码再推送至121”，一次列出全部缺失编码。

### 返回字段

P01、P02、P03和P04使用相同业务对象。

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `data.draftId` | string | 121采购单唯一ID |
| `data.purchaseOrderNo` | string | 121采购单序号，与draftId相同 |
| `data.sourceSystem` | string | 来源系统，当前为INTELLIGENT_OFFICE |
| `data.sourceOrderId` | string | 来源订单号 |
| `data.syncBatchId` | string | 本轮同步UUID，用于查询、重试和防止旧事件覆盖新批次 |
| `data.status` | string | 当前采购状态；全部取值和含义见P01“采购单状态值” |
| `data.details` | array | 来源明细与121明细ID的映射 |
| `data.details[].sourceDetailId` | string | 创建时传入的来源明细号 |
| `data.details[].detailId` | string | 121采购明细ID |

### 采购单状态值

下面是P01、P02、P03、P04的 `data.status`，与响应外层 `code` 含义不同。

| 返回值 | 中文含义 | 业务说明 | 可通过P04取消 |
| --- | --- | --- | --- |
| `DRAFT` | 草稿 | 已在121建立有效采购草稿，尚未锁单 | 是 |
| `LOCKED` | 已锁单 | 已进入121锁单流程，来源关联继续有效 | 否 |
| `RETURN_PENDING` | 退单待审核 | 已申请退单，等待审核；不能视为删除 | 否 |
| `RETURNED` | 退单通过 | 退单审核通过；来源关联仍保留 | 否 |
| `RETURN_REJECTED` | 退单驳回 | 退单申请被驳回；来源关联仍有效 | 否 |
| `DELETED` | 已删除 | 草稿已软删除，本轮批次可查询历史ID；不能恢复为已同步 | 重复调用返回DELETED |

已删除标记优先于其他状态。只有确认旧批次为 `DELETED` 后，调用方才能使用新批次和新幂等键重新同步。锁单及退单仍保留来源有效关联。

### 请求与响应示例

除公共鉴权头外再带 `Idempotency-Key: intelligent-office:PC202611170001:550e8400-e29b-41d4-a716-446655440001`。

```json
{
  "sourceSystem": "INTELLIGENT_OFFICE",
  "sourceOrderId": "PC202611170001",
  "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
  "deptId": "80001",
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
  "code": 200, "errorCode": null,
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
  "code": 422, "errorCode": "MATERIAL_NOT_FOUND",
  "message": "物料编码MAT-001、MAT-002在121系统未找到，请先核实物料编码再推送至121",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "field": "details.materialCode",
    "missingMaterialCodes": ["MAT-001", "MAT-002"],
    "errors": [
      {"field":"details[0].materialCode","sourceDetailId":"line-1","materialCode":"MAT-001"},
      {"field":"details[1].materialCode","sourceDetailId":"line-2","materialCode":"MAT-002"}
    ]
  }
}
```

金额不一致示例，HTTP 422：

```json
{
  "code": 422, "errorCode": "AMOUNT_MISMATCH",
  "message":"来源明细line-1采购金额99与数量乘单价的计算金额100.00不一致",
  "requestId":"7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data":{"field":"details[0].purchaseAmount","sourceDetailId":"line-1","provided":99,"calculated":100.00}
}
```

### 幂等、超时与重新同步

| 场景 | 处理结果/要求 |
| --- | --- |
| 同应用、同操作人、同批次、同幂等键、同原始正文 | 返回已有单当前状态及原ID，不重复创建；可能返回DELETED |
| 批次或幂等键与原请求组合/内容不一致 | 409 IDEMPOTENCY_CONFLICT |
| 同sourceSystem+sourceOrderId仍有有效121关联 | 409 SOURCE_ALREADY_SYNCED，data返回可访问原单及原批次，按返回draftId调用P03查询 |
| 创建超时、网关502/504、网络中断 | 先P02查本批次；查不到再用原批次、原键、原始正文重试 |
| 每次HTTP重试 | 沿用本环境的App-Id与App-Secret；无需额外鉴权参数 |
| 原草稿已确认DELETED | 新批次UUID、新幂等键再创建；旧批次保留历史可查询 |

同一批次重试须保持loginAccount、幂等键及原始正文一致，包括正文空格、属性顺序和可选字段。结果未知时先查询P02。幂等键示例：`purchase:<sourceOrderId>:<syncBatchId>`。

### 业务返回码

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 新建成功或命中原请求 | 保存主单及明细映射，并判断当前status |
| 400 | `PARAM_INVALID` | 缺字段、格式/类型/金额精度/范围或幂等键不合法 | 修正完整正文 |
| 403 | `OPERATOR_FORBIDDEN` | 部门越权、用户不属部门、操作人与发起人不同 | 核对授权，不自动重试 |
| 404 | `USER_NOT_FOUND` | 操作人不存在或已删除 | 核对loginAccount并使用有效账号 |
| 409 | `IDEMPOTENCY_CONFLICT` | 批次/键已用于不一致请求 | 查询原请求，不能覆盖原结果 |
| 409 | `SOURCE_ALREADY_SYNCED` | 来源订单存在有效121单据 | 按data.draftId查询原单，勿重复创建 |
| 422 | `USER_DISABLED` | 操作人已停用 | 更换可用账号 |
| 422 | `REFERENCE_INVALID` | 供应商不存在、字典/审批人或联动关系不可用 | 核实供应商提示，或重新拉取字典并修正 |
| 422 | `MATERIAL_NOT_FOUND` | 一个或多个物料编码不存在 | 在121补齐/核实后重新推送 |
| 422 | `MATERIAL_AMBIGUOUS` | 物料编码匹配多条记录 | 121消除重复数据后重试 |
| 422 | `AMOUNT_MISMATCH` | 采购金额与数量×单价不同 | 修正金额或省略purchaseAmount |
| 422 | `ORDER_CREATE_FAILED` | 采购主单未保存成功 | 核对原因后重新提交 |
| 422 | `DETAIL_CREATE_FAILED` | 明细添加失败 | 按data定位来源明细；本次全部回滚 |
| 503 | `SYSTEM_BUSY` | 服务暂不可用 | 按原业务批次退避重试；持续发生联系121 |

还可能返回公共鉴权/系统错误。遇到500先查询结果，不应直接认为未创建。

## P02 按来源和批次查询草稿（GET）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **GET** |
| 请求路径 | `/open-api/v1/purchase-drafts/by-source` |
| 传参位置 | Query查询参数；无正文，不传幂等键 |
| Content-Type | 无请求正文，无需设置 |

### 请求参数

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `sourceSystem` | 来源系统 | Query | string | 是 | 使用创建时的INTELLIGENT_OFFICE；查询入口只校验非空及长度≤128码点 |
| `sourceOrderId` | 来源订单号 | Query | string | 是 | 使用创建时的来源订单号；非空且≤128码点 |
| `syncBatchId` | 同步批次 | Query | string | 是 | 使用创建时的UUID；查询入口只校验非空及长度≤128码点，不自动返回最近批次 |

### 返回字段

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `data.draftId` | string | 121采购单唯一ID |
| `data.purchaseOrderNo` | string | 121采购单序号，与draftId相同 |
| `data.sourceSystem` | string | 来源系统，当前为INTELLIGENT_OFFICE |
| `data.sourceOrderId` | string | 来源订单号 |
| `data.syncBatchId` | string | 本轮同步UUID，用于查询、重试和防止旧事件覆盖新批次 |
| `data.status` | string | 当前采购状态；全部取值和含义见P01“采购单状态值” |
| `data.details` | array | 来源明细与121明细ID的映射 |
| `data.details[].sourceDetailId` | string | 创建时传入的来源明细号 |
| `data.details[].detailId` | string | 121采购明细ID |

### 请求与响应示例

`data`字段逐项见P01“返回字段”；`data.status`的所有值见P01“采购单状态值”。仅查询本客户端及授权部门范围内的批次。删除后仍返回原ID和明细映射，状态为DELETED。

```http
GET /open-api/v1/purchase-drafts/by-source?sourceSystem=INTELLIGENT_OFFICE&sourceOrderId=PC202611170001&syncBatchId=550e8400-e29b-41d4-a716-446655440001
```

HTTP 200：

```json
{
  "code": 200, "errorCode": null,
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

### 业务返回码

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 找到本轮结果 | 依据status恢复本地同步结果 |
| 400 | `PARAM_INVALID` | 缺参数或参数不合法 | 使用原创建批次参数 |
| 404 | `DRAFT_NOT_FOUND` | 批次不存在、主单不存在或不在本客户/授权部门范围 | 超时恢复时核对原参数，再按原业务请求重试；不得据此取消其他批次 |

公共错误码仍适用。P02不是按来源查询“最新一单”，syncBatchId必须准确。三个查询字段均禁止控制字符及尖括号；查询入口不另校验来源常量、订单号格式或UUID格式，格式错误且无匹配时返回404 DRAFT_NOT_FOUND。

## P03 按121采购单ID查询草稿（GET）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **GET** |
| 请求路径 | `/open-api/v1/purchase-drafts/{draftId}` |
| 传参位置 | Path路径参数；无正文，不接受查询参数或幂等键 |
| Content-Type | 无请求正文，无需设置 |

### 请求参数

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `draftId` | 121采购单ID | Path | string | 是 | P01返回的正整数ID，只支持本接口同步来源单 |

### 返回字段

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `data.draftId` | string | 121采购单唯一ID |
| `data.purchaseOrderNo` | string | 121采购单序号，与draftId相同 |
| `data.sourceSystem` | string | 来源系统，当前为INTELLIGENT_OFFICE |
| `data.sourceOrderId` | string | 来源订单号 |
| `data.syncBatchId` | string | 本轮同步UUID，用于查询、重试和防止旧事件覆盖新批次 |
| `data.status` | string | 当前采购状态；全部取值和含义见P01“采购单状态值” |
| `data.details` | array | 来源明细与121明细ID的映射 |
| `data.details[].sourceDetailId` | string | 创建时传入的来源明细号 |
| `data.details[].detailId` | string | 121采购明细ID |

### 请求与响应示例

响应外壳见“统一响应字段”，完整data字段见P01“返回字段”，全部status含义见P01“采购单状态值”。

```http
GET /open-api/v1/purchase-drafts/12345
```

HTTP 200，锁单示例：

```json
{
  "code": 200, "errorCode": null, "message": "success",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": {
    "draftId": "12345", "purchaseOrderNo": "12345",
    "sourceSystem": "INTELLIGENT_OFFICE", "sourceOrderId": "PC202611170001",
    "syncBatchId": "550e8400-e29b-41d4-a716-446655440001",
    "status": "LOCKED", "details": [{"sourceDetailId":"line-1","detailId":"67890"}]
  }
}
```

### 业务返回码

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 查询成功 | 以status判断当前状态 |
| 400 | `PARAM_INVALID` | draftId或附加参数不合法 | 修正请求 |
| 404 | `DRAFT_NOT_FOUND` | 无同步来源记录、不存在、其他客户或超授权部门 | 核对ID/授权，不当作可重新创建的充分依据 |

公共错误码仍适用。

## P04 取消同步并删除草稿（POST）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 请求方法 | **POST** |
| 请求路径 | `/open-api/v1/purchase-drafts/{draftId}/cancel` |
| 传参位置 | Path路径参数 + Body JSON；不接受查询参数或幂等键 |
| Content-Type | `application/json` |

### 请求参数

取消等同软删除121对应采购草稿，并产生C01删除事件。

| 参数名 | 名称 | 位置 | 类型 | 是否必填 | 说明 |
| --- | --- | --- | --- | --- | --- |
| `draftId` | 121采购单ID | Path | string | 是 | 本客户授权范围内的来源采购单ID |
| `operatorLoginAccount` | 可选操作人 | Body | string / null | 否 | 建议省略，由公共loginAccount取得；非null时须与公共账号相同，用户须属于该单部门 |
| `reason` | 取消原因 | Body | string | 否 | 最长500；省略或仅空格使用CANCEL_SYNC，非空原因原样写入事件 |

### 返回字段

| 返回字段 | 类型 | 含义 |
| --- | --- | --- |
| `data.draftId` | string | 121采购单唯一ID |
| `data.purchaseOrderNo` | string | 121采购单序号，与draftId相同 |
| `data.sourceSystem` | string | 来源系统，当前为INTELLIGENT_OFFICE |
| `data.sourceOrderId` | string | 来源订单号 |
| `data.syncBatchId` | string | 本轮同步UUID，用于查询、重试和防止旧事件覆盖新批次 |
| `data.status` | string | 当前采购状态；全部取值和含义见P01“采购单状态值” |
| `data.details` | array | 来源明细与121明细ID的映射 |
| `data.details[].sourceDetailId` | string | 创建时传入的来源明细号 |
| `data.details[].detailId` | string | 121采购明细ID |

### 请求与响应示例

```json
{"reason":"调用方取消同步"}
```

HTTP 200；data完整字段见P01“返回字段”，成功取消返回DELETED：

```json
{
  "code": 200, "errorCode": null, "message": "success",
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
  "code": 409, "errorCode": "DRAFT_NOT_EDITABLE",
  "message": "Only an active draft can be deleted",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

### 取消规则及返回码

仅DRAFT可删除；已DELETED重复调用返回同样结果，不再生成重复事件。主单、明细及同步关联一并删除，并发送C01通知。退单状态不等同于删除。

| HTTP | errorCode | 含义 | 调用方处理 |
| --- | --- | --- | --- |
| 200 | `null` | 删除成功或该批次已删除 | 按批次清空本地关联，不影响新批次 |
| 400 | `PARAM_INVALID` | 参数无效 | 修正请求 |
| 403 | `OPERATOR_FORBIDDEN` | 操作人不属该单部门或越权 | 核对部门和操作人 |
| 404 | `USER_NOT_FOUND` | 操作人不存在/已删 | 更换有效账号 |
| 404 | `DRAFT_NOT_FOUND` | 来源单不存在或客户/部门不可见 | 核对ID及授权 |
| 409 | `DRAFT_NOT_EDITABLE` | 非草稿或并发状态已变化 | 查询当前状态，不强制删除 |
| 422 | `USER_DISABLED` | 操作人已停用 | 更换启用账号 |

另见公共错误码。重试沿用本环境App-Id、App-Secret及本轮loginAccount；结果未知时先查询当前草稿状态。

## C01 采购草稿删除通知（POST）

### 请求方式与地址

| 项目 | 内容 |
| --- | --- |
| 接收方 | 调用方配置的回调服务 |
| 发送方 | 121服务端 |
| 请求方法 | **POST** |
| 传参位置 | Body JSON |
| 地址 | 接收方提供固定完整URL；正式HTTPS，测试可约定HTTP；固定URL不可带用户名密码、查询参数或fragment |
| 触发 | 121页面删除来源草稿，或P04成功取消 |
| 鉴权 | 仅使用App-Id、App-Secret反向请求头，不要求员工loginAccount；接收方提供独立凭据 |
| 幂等 | eventId业务幂等；不带Idempotency-Key；重试沿用该目标的App-Id、App-Secret |

接收方应提供固定回调URL及独立的App-Id、App-Secret，并在开始接收通知前完成连通性验证。

### 通知正文参数

| 参数名 | 名称 | 类型 | 是否必填 | 含义与取值 |
| --- | --- | --- | --- | --- |
| `eventId` | 事件ID | string | 是 | UUID，同事件所有重试不变；接收方去重键 |
| `eventType` | 事件类型 | string | 是 | 固定PURCHASE_DRAFT_DELETED，表示采购草稿已删除 |
| `eventTime` | 事件发生时间 | string | 是 | UTC ISO 8601，不是本次重试时间 |
| `sourceSystem` | 来源系统 | string | 是 | 当前固定INTELLIGENT_OFFICE |
| `sourceOrderId` | 来源订单号 | string | 是 | 来源系统原订单号 |
| `syncBatchId` | 被删同步批次 | string | 是 | 仅清除此UUID对应的关联 |
| `draftId` | 被删121主单ID | string | 是 | 与本地当前关联一起核对 |
| `purchaseOrderNo` | 121采购单序号 | string | 是 | 与draftId相同 |
| `status` | 删除结果 | string | 是 | 固定DELETED |
| `reason` | 删除原因 | string | 是 | USER_DELETE=121页面删除；CANCEL_SYNC=未提供原因的接口取消；其他非空文本=取消方自定义原因 |

### 请求示例

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

### 接收方响应与每个返回值含义

121以HTTP状态判断投递结果，**不读取回调响应的JSON业务码**。接收方需按下表返回。

| HTTP返回值 | 含义 | 121行为 |
| --- | --- | --- |
| 任意2xx，例如200、204 | 事件已通过凭据校验、持久处理成功，或eventId已成功处理过 | 标记DELIVERED，不再正常重试 |
| 3xx | 重定向，未视为成功 | 不跟随重定向，进入失败重试 |
| 4xx | 接收方拒绝、鉴权失败或请求错误 | 仍按失败重试，接收方应排查原因 |
| 5xx | 接收方内部错误或暂不可用 | 失败重试 |
| 超时/网络失败 | 无法确认接收结果 | 失败重试，可能重复投递 |

推荐成功返回 `204 No Content`。若返回JSON，可自定内容，但不能用HTTP200加失败业务码表达失败，否则121会视为成功。必须先完成可靠落库/处理再回2xx；重复eventId也回2xx。

### 重试、乱序与本地清理

- 至少一次投递。默认每10秒轮询，连接超时5秒、读取超时10秒；首次发送失败后按30秒、2分钟、10分钟、30分钟间隔重试，最多5次发送，随后转人工补偿队列。
- 同一事件重试保持eventId和正文，沿用该回调目标的App-Id、App-Secret。进程崩溃也可能导致重投。
- 先校验两项系统凭据，再按eventId幂等处理。必须同时核对sourceOrderId、syncBatchId、draftId，仅清除完全匹配的当前主单/明细映射。
- 旧批次通知不能清除重新同步的新单；即使不匹配当前关联，也应将该旧事件记为已处理并回2xx。
- 通知可能早于P01响应到达。接收方须保留批次已删记录，迟到的创建响应不得恢复已删除批次的关联。

## 返回码说明

### 业务返回码总表

下表列出全部errorCode及对应HTTP/数字code。成功errorCode为null；错误行第一列同时是HTTP状态和响应体数字code。鉴权和系统码适用于121入口；其余业务码按各接口业务校验产生。客户端按数字code及errorCode判断，不解析message。

| HTTP | errorCode | 中文含义 | 产生原因 | 建议处理 |
| --- | --- | --- | --- | --- |
| 200 | `null` | 成功 | 查询、创建或取消已处理；公共账号已校验；P01可能是幂等返回 | 读取data；按status确认业务状态 |
| 400 | `PARAM_INVALID` | 请求参数错误 | 缺少/重复/非法公共loginAccount，非法账号编码，必填项或字段格式、类型、长度、精度/范围错误，未知/重复JSON字段，错误query/幂等键 | 修正请求，不原样自动重试 |
| 401 | `AUTH_INVALID` | 系统凭据无效 | App-Id/App-Secret缺失、重复、错误，应用停用或入口关闭 | 核对本环境的App-Id和App-Secret |
| 403 | `OPERATOR_FORBIDDEN` | 业务操作越权 | 公共用户无可用部门，deptId越权，正文账号与请求头loginAccount不一致，禁止代发或变更来源单部门 | 修正用户/部门并确认授权 |
| 404 | `USER_NOT_FOUND` | 用户不存在 | 账号不存在或已删除 | 核对loginAccount并使用有效账号 |
| 404 | `DRAFT_NOT_FOUND` | 同步采购单不可见 | 没有来源记录、主单不存在、其他客户或非授权部门 | 核对ID、来源、批次与授权 |
| 404 | `NOT_FOUND` | 字典类型不存在 | D01 type不在支持列表 | 使用D01“字典类型及联动”枚举 |
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
| 422 | `ORDER_CREATE_FAILED` | 主单创建失败 | 主单未成功保存 | 核对具体原因，本次全部回滚 |
| 422 | `DETAIL_CREATE_FAILED` | 明细添加失败 | 明细写入失败 | 按data定位来源明细，本次全部回滚 |
| 500 | `SYSTEM_ERROR` | 内部处理失败 | 未预期异常或状态数据异常 | 留存requestId；P01先P02查询，联系121 |
| 503 | `SYSTEM_BUSY` | 暂不可用 | 服务暂不可用 | 退避重试，P01保留原批次/键/正文 |

`SUPPLIER_IMMUTABLE` 是来源单的121编辑业务保护码；当前开放接口不提供修改供应商接口。未知URL还可能由网关/框架返回普通404，不能保证包含此JSON外壳。

### 失败响应与重试分组

```json
{
  "code": 401, "errorCode": "AUTH_INVALID",
  "message": "App-Id或App-Secret错误、缺失，或应用已停用",
  "requestId": "7f7c8f45-0c8c-4b56-9b77-4c4bd7de9a41",
  "data": null
}
```

| 类别 | 情况 | 操作 |
| --- | --- | --- |
| 修正后再发 | 400、401、403、404、409、415、422 | 根据具体errorCode修正，不盲目循环重试 |
| 等待后重发 | 503、网关502/504、网络错误 | 建议退避2秒、5秒、15秒 |
| 结果不确定 | P01读取超时、500、连接在发送后中断 | 先查本批次，再按原业务请求重试 |

不要把所有非200都解释为“参数错误”，也不要把所有200都解释为“仍然有效同步”：成功查询可能返回DELETED。

## 附录

### 文档下载

- [完整OpenAPI 3.1 YAML](./downloads/openapi.yaml)
- [完整OpenAPI 3.1 JSON](./downloads/openapi.json)
- [U01独立OpenAPI YAML](./downloads/u01.openapi.yaml)
- [本页Markdown](./downloads/api-reference.md)

可导入支持OpenAPI 3.1的Apifox、Swagger等工具。导入后在Authorize中填写App-Id和App-Secret，所有六个121入口还须在Headers中填写公共loginAccount；C01只用反向系统凭据。

### 外部测试接口与材料

测试与正式环境使用相同的接口路径，通过基础地址和凭据区分。测试地址如下；请求头均须携带App-Id、App-Secret和loginAccount。

| 编号 | 方法 | 测试接口地址 |
| --- | --- | --- |
| U01 | POST | `http://testfcst.chinaptac.com/open-api/v1/users/verify` |
| D01 | GET | `http://testfcst.chinaptac.com/open-api/v1/dictionaries/{type}` |
| P01 | POST | `http://testfcst.chinaptac.com/open-api/v1/purchase-drafts` |
| P02 | GET | `http://testfcst.chinaptac.com/open-api/v1/purchase-drafts/by-source` |
| P03 | GET | `http://testfcst.chinaptac.com/open-api/v1/purchase-drafts/{draftId}` |
| P04 | POST | `http://testfcst.chinaptac.com/open-api/v1/purchase-drafts/{draftId}/cancel` |
| C01 | POST | 由调用方提供固定测试接收URL，121主动调用 |

- [外部测试操作说明](./downloads/external-testing.md)
- [Postman测试集合](./downloads/121-open-api-external.postman_collection.json)
- [Postman测试环境模板（无密钥）](./downloads/121-open-api-test.postman_environment.json)

导入集合和环境模板，填写appId、appSecret、loginAccount及业务数据。集合包含23个请求，覆盖账号查询、字典查询和采购草稿操作。先执行查询，再逐项执行创建和取消。

### HTTP调用示例

将示例凭据和业务ID替换为本环境的有效值。

```http
POST /open-api/v1/users/verify HTTP/1.1
Host: testfcst.chinaptac.com
App-Id: your-app-id
App-Secret: your-app-secret
loginAccount: zhangsan
Content-Type: application/json

{}
```

```http
GET /open-api/v1/dictionaries/departments?pageNum=1&pageSize=100 HTTP/1.1
Host: testfcst.chinaptac.com
App-Id: your-app-id
App-Secret: your-app-secret
loginAccount: zhangsan
Accept: application/json
```

### Java服务端示例（JDK 11及以上）

下载 [OpenApiExample.java](./downloads/OpenApiExample.java)，仅使用JDK标准库。配置环境变量后执行下列任一命令，只发送用户查询或部门查询，不创建和取消采购单。

| 环境变量 | 说明 |
| --- | --- |
| OPEN_API_BASE_URL | API基础地址，例如测试地址http://testfcst.chinaptac.com/open-api/v1 |
| OPEN_API_APP_ID | 121交付的appId |
| OPEN_API_APP_SECRET | 121交付的appSecret |
| OPEN_API_LOGIN_ACCOUNT | 每次请求使用的121测试账号，两种查询均必填；示例自动UTF-8百分号编码 |

```shell
java OpenApiExample.java user
java OpenApiExample.java departments
```

### 常见问题

| 情况 | 核对内容 |
| --- | --- |
| 401 / AUTH_INVALID | 核对本环境App-Id、App-Secret及请求头是否缺失或重复 |
| 400 / PARAM_INVALID | loginAccount头是否缺失、重复、非法编码，或业务字段是否错误 |
| 403 / OPERATOR_FORBIDDEN | 核对账号所属部门、应用授权部门及正文账号与请求头是否一致 |
| 422 / 业务错误码 | 按errorCode及data定位供应商、物料、字段或金额错误 |
| P01超时或连接断开 | 先P02查询，保留本轮批次、幂等键和原始正文 |

反馈问题时提供环境、接口、HTTP状态、errorCode、requestId和时间，请勿提供密钥或完整人员信息。
