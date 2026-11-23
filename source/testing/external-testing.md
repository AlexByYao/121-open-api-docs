# 121开放接口联调指南

适用接口版本：**2.9.0** · 更新日期：**2026-11-23**

小数月份联调：接口允许0.5等不足1个月的正数；121原采购、退单页面输入框仍最小1，页面及原编辑规则不调整。此类单据暂不要通过原页面编辑保存，以免月份变为1。智能化按返回的月份展示，不取整、不换算为天。

本指南用于验证用户查询、基础字典、采购草稿及删除通知。接口字段、约束和错误码以《121接口交接文档》及OpenAPI定义为准。

## 1. 接入准备

测试基础地址：`http://testfcst.chinaptac.com/open-api/v1`。

开始联调前，请准备以下资料：

- 测试环境App-Id、App-Secret和有效的121账号。
- 该账号可访问的部门，以及有效的供应商、项目、库位和物料数据。
- 用于创建和取消草稿的专用测试订单号。
- 验证删除通知时，提供固定接收URL及独立的回调凭据。

所有请求均须携带App-Id、App-Secret和loginAccount。应用密钥仅保存在服务端。测试凭据和业务示例值不得用于正式环境。

## 2. 导入Postman

1. 导入`121-open-api-external.postman_collection.json`和`121-open-api-test.postman_environment.json`。
2. 选择测试环境，填写appId、appSecret和loginAccount。baseUrl为上述基础地址，末尾不加斜杠。
3. 发送U01。成功响应应为HTTP 200、code=200、errorCode=null。
4. 从`data.departments`中选择本次业务部门，将其deptId填入deptId变量。
5. 依次查询D01字典，填写采购草稿所需的业务ID。审批人员按roleCode查询；库位可按projectId筛选。

loginAccount最多30个字符。普通ASCII账号直接填写；包含中文或百分号时，使用UTF-8百分号编码。例如“张三”填写`%E5%BC%A0%E4%B8%89`，`a%b`填写`a%25b`。请勿重复编码。

集合包含23个请求：用户查询1个、基础字典11个、审批角色字典7个、采购草稿操作4个。请逐项执行创建和取消请求，避免使用Runner批量执行写操作。

## 3. 接口清单

以下路径相对于测试基础地址。

| 编号 | 方法 | 路径 | 验证内容 |
| --- | --- | --- | --- |
| U01 | POST | `/users/verify` | 账号信息、可用部门及无效账号错误 |
| D01 | GET | `/dictionaries/{type}` | 字典、分页、部门关联和审批角色 |
| P01 | POST | `/purchase-drafts` | 主单与明细保存、ID映射、幂等和错误详情 |
| P02 | GET | `/purchase-drafts/by-source` | 按来源记录及原幂等键查询，确认超时请求结果 |
| P03 | GET | `/purchase-drafts/{draftId}` | 查询采购单状态及首次明细映射 |
| P04 | POST | `/purchase-drafts/{draftId}/cancel` | 取消草稿、重复取消及不可取消状态 |
| C01 | POST | 接收方提供的固定URL | 删除事件接收、凭据校验、去重和重试 |

## 4. 采购草稿验证

1. 查询字典后填写有效的业务ID和物料编码。未填写的可选ID省略，不传空字符串。
2. 首次创建前确定并保存sourceOrderId：使用调用方已有采购记录ID；没有ID时生成UUID。另生成并保存idempotencyKey，可直接使用另一个UUID。首次创建及超时重试使用相同的来源记录ID和幂等键；确认旧单DELETED后重新创建时只换幂等键。sourceSystem可省略，由121使用默认值。填写budgetMonth和purchaseDate。
3. 发送P01，核对HTTP 200、code=200、errorCode=null、data.status=DRAFT。保存draftId及details中的明细ID映射。
4. 保持loginAccount、sourceOrderId、幂等键及原始正文不变，重复发送P01，应返回相同draftId，不重复创建。
5. P02在Query传sourceOrderId，在Header传原Idempotency-Key；P03直接使用draftId。两者查询同一采购单。返回的details为首次同步明细映射，不包含后续新增明细或完整实时明细内容。
6. 对本次未锁单测试草稿执行P04，预期状态为DELETED。再次取消应返回同一删除结果。

P01仅保存采购草稿及全部明细。预算不足、未填写超预算说明不影响合法草稿的保存；生成、锁单和导出不属于本接口范围。

主单或任一明细保存失败时，本次请求整体回滚。失败响应通过message说明原因，data可包含stage、rolledBack、sourceDetailId和field。可使用原sourceOrderId和Idempotency-Key调用P02查询确认不存在已提交结果。明确收到保存失败后，修正数据再提交；网络中断或结果未知时，先查询结果再决定是否重试。

采购字段说明：purchaseAmount选填，省略或null由121按数量×单价计算；提供时必须精确相等，不做容差或自动四舍五入。transportCost可手填非负金额，不要求存在于运费字典。供应商按完整名称精确匹配字典后用id建单，code当前为空。与手工页面一致：requiresProject=true时须传有效项目；false时忽略传入项目并清空（内部ID=0），不从库位带出。项目为全局字典，库位按部门选择，不强制匹配库位关联项目；D01的projectId仅为可选筛选，复现手工页面应省略。detailTaxRate继续沿用原DECIMAL(15,2)有符号范围。变现周期单位为月，name如1.5、value=1.5、unit=MONTH，没有中文单位字段，展示端自行映射为“个月”；P01的cashCycle为number，1.5表示1.5个月，最小0.01、最多两位有效小数，整数仍兼容；1.500可接收，1.501返回400 INVALID_ARGUMENT且data.field=cashCycle，不四舍五入，不接收数字字符串，不进行天/月换算。2.9.0需先将数据库两列升级为DECIMAL(21,2)再部署后端，随后测试1.5和0.5。

## 5. 删除通知验证

接收方提供固定回调URL及独立App-Id、App-Secret。删除来源草稿或调用P04成功后，121向该URL发送C01通知。

接收方须先校验凭据，再按eventId去重，并核对sourceOrderId、draftId。可靠处理完成或已处理的重复事件返回2xx，推荐204；处理失败返回非2xx。

应覆盖以下情况：正常接收、重复事件、超时或非2xx后的重试，以及旧采购单删除通知到达时不清除新采购单关联。

## 6. 错误处理

| 结果 | 处理方式 |
| --- | --- |
| 400 / PARAM_INVALID | 按data.field、reason及message修正参数 |
| 401 / AUTH_INVALID | 核对本环境App-Id、App-Secret及请求头 |
| 403 / OPERATOR_FORBIDDEN | 核对账号和业务部门是否在可访问范围 |
| 404 / USER_NOT_FOUND | 使用存在且启用的121账号 |
| 404 / DRAFT_NOT_FOUND | 原单不存在或不在当前应用/用户授权范围；不返回不可见单据详情 |
| 409 / SOURCE_ALREADY_SYNCED | 同来源已有可访问的有效原单，按data.draftId查询，不重复创建 |
| 409 / IDEMPOTENCY_CONFLICT | 查询原创建请求，不覆盖已成功的请求内容 |
| 422 / 业务错误码 | 按message及data定位供应商、物料、字段或保存错误 |
| 500、503或网络异常 | 保留requestId；创建请求先通过P02确认结果 |

P01会先检查幂等内容冲突，冲突返回409 IDEMPOTENCY_CONFLICT；匹配后查询原单不可访问才返回404 DRAFT_NOT_FOUND。当前P04取消失败提示仍可能为英文，调用方按errorCode处理，不解析message。

接口代码没有专门的QPS或并发限制，部署网关另行核对；弹窗多字典建议并发4—6个。联调初始建议连接超时5秒、P01等待响应30秒，实际耗时未压测，不承诺固定时延。超时先按原来源记录和原幂等键查询P02，再决定原样重试。

反馈问题时提供环境、接口、时间、HTTP状态、errorCode及requestId，请勿提供密钥。

## 7. Java示例

`OpenApiExample.java`适用于JDK 11及以上版本，提供用户和部门只读查询。配置OPEN_API_BASE_URL、OPEN_API_APP_ID、OPEN_API_APP_SECRET及OPEN_API_LOGIN_ACCOUNT后执行：

```shell
java OpenApiExample.java user
java OpenApiExample.java departments
```
