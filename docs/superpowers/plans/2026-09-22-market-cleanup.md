# 校园二手市场精简与站内导览 Implementation Plan

**Goal:** 精简不可用和重复入口，提升校园二手交易检索与发布体验，校园全景可站内搜索查看，GitHub 保留可回退版本。

**Architecture:** 保留现有 Vue / Spring Boot 架构；复用后端真实商品搜索及价格区间接口。全景使用学校官网链接的 720 云嵌入页及真实场景 ID 索引，无 API 密钥，不虚构三维建筑。地图不承担路线规划。

**实施方式:** 在当前分支按 executing-plans 流程直接完成；用户已授权精简和 GitHub 版本保存。先保存基线，再提交功能版本，不改远端 release 分支。

- [x] Git 分支与精简前快照：`hebeu/campus-market-20260922`；基线提交 `6a166a8`，标签 `hebeu-before-market-cleanup-20260922`。本地账号、数据库、依赖和密钥不提交。
- [x] `PublishView.vue` 移除 AI 估价状态、请求、按钮和提示，保留手动价格与校验，补充成色、瑕疵、面交描述提示。
- [x] `App.vue` 去除校园地图和未配置 AI 助手导航，`router/index.ts` 旧助手路由转到集市，保留买卖双方私聊。
- [x] `HomeView.vue` 清除演示商品、演示跑腿、重复滚动卡片和悬浮 AI 入口；真实商品有加载、空列表、错误和重试状态，保留高清校园视觉。
- [x] `CampusGuide.vue` + `config/campusScenes.ts`：站内关键词筛选真实场景名称；按用户确认嵌入学校全景，固定主广场开场，需在全景内部选择地点；明确实景全景与三维模型区别；移除官网和高德入口。
- [x] `MarketView.vue` + 价格工具：预算范围筛选、URL 保留条件、一键清空；删除无依据的全员认证标记；收藏按钮支持触摸与键盘；无结果提供清空和发布入口。
- [x] 验证：价格解析边界、场景搜索、发布页无估价且价格格式化正常、首页真空态、站内全景打开与名称筛选、手机无溢出、构建与 API 冒烟检查。
- [x] 完成后提交、推送同一独立分支；记录基线标签、恢复方法与限制，不强制重置用户文件。

## 参考与取舍

- eBay 的本地自提说明建议在平台内沟通、现场检查物品再确认交付：https://www.ebay.com/help/buying/postage-delivery/changing-delivery-address-method/local-pickup?id=4056 。本项目借鉴描述瑕疵、校内面交和站内沟通；不宣称具有 eBay 的保障机制。
- 学校官网 https://www.hebeu.edu.cn/ 链接的全景 https://www.720yun.com/t/2avktm1qs2m 可公开访问，含 43 个场景。场景 ID 使用公开展示数据，不绕过权限或复制私有模型。
- 高德 JS API 要求申请 key 与安全密钥；当前无用户密钥，采用学校已有实景全景满足站内查看，不冒充完整 3D GIS：https://developer.amap.com/api/javascript-api-v2/guide/abc/jscode 。
- 用户只要求删冗余入口，不删除已有交易记录、账户、订单及可用跑腿功能。

## 封装复用与验证记录

- 参考校园交易功能划分：https://github.com/Sycml/XYESJYPT ，未复制项目代码。
- 复用已安装的 Element Plus：https://element-plus.org/en-US/component/image.html 、https://element-plus.org/en-US/component/empty 、https://element-plus.org/en-US/component/skeleton 。无新增依赖。
- 2026-09-22：前端生产构建成功；价格解析与范围测试 2 项通过；学校、校区、分类、搜索、推荐、管理员登录、用户信息及后台统计共 8 项接口通过。
- 浏览器验证：逆序价格提示且不提交；有效预算 URL 刷新保留；初始逆序 URL 清除；首页与集市 390px 无横向溢出；嵌入全景能进入校园画面；发布页不再有 AI 估价。
- 独立静态审查发现初始 URL 逆序校验遗漏及主按钮文字颜色遗漏，均已修复并重建验证。
- 720 云匿名会员请求返回 401，不影响公开全景显示；全景质量和可用性由学校与第三方提供，未复制或伪造模型。
- 图片上传仍依赖未配置的 COS，因此未宣称完成上传到发布的端到端验证。
- 回退方法见 README：从基线标签新建恢复分支，保留当前修改及历史。
