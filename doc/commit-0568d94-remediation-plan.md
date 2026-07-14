# Commit `0568d94` 严格审计问题处理计划

> 审计基线：`0568d94cf156ad92fd535f13cbfc8079fabab8ad`（`feat: 重构界面状态树并修复分层与隐私问题`）
>
> 复核日期：2026-07-14
>
> 适用分支：`develop`
>
> 文档状态：问题已复核，尚未实施修复

## 1. 复核范围与结论

本计划以 `doc/coding-guidelines.md` 为主入口，并按问题范围复核了 MVI/UiState、Intent/ViewEvent、ViewModel/Activity/Compose、Room 数据层以及 RPClient 领域规范。复核对象包括最新提交的差异、相关持久化和生命周期上下文、现有单元测试、Debug/Release 构建与 Lint 报告。

结论如下：

- 原审计列出的 11 项中确认 10 项问题：1 项 P1、6 项 P2、3 项 P3；AUD-01 属于 Debug 请求日志的预期行为，不作为缺陷处理。
- P1/P2 表示需要修复的隐私、数据一致性、流程或性能问题；P3 表示已确认的分层、维护性、注释或规范问题。
- “确认”不等同于每项都已在当前电脑上造成崩溃。例如头像问题确认的是全量原尺寸解码和强引用，实际 OOM 取决于图片规模与设备堆限制；群聊状态树问题确认的是规范和生命周期归属错误，当前尚无稳定用户功能故障。
- 对问题 7 的表述已校准：新建流式回复会在创建空占位消息时提前刷新 `latestTime`；确定遗漏的是全部停止路径的 `worldInfoStateJson`，以及 Update/Regenerate 路径的 `latestTime`。核心缺陷是生成结果没有原子收尾。
- 当前没有连接 Android 设备，因此本轮快速复核未执行 instrumentation 和真实旋转/文件选择器操作；生命周期问题通过应用代码、Android 36 平台源码和 AndroidX Activity 1.13 源码的确定时序交叉确认。计划中的设备测试是发布前硬门槛。

### 1.1 复核结果总表

| ID | 优先级 | 已确认问题 | 复核结论 |
|---|---:|---|---|
| AUD-01 | — | Debug 请求日志保存并展示原始请求与响应 | 非问题，用户主动开启 Debug 后需要原文排障 |
| AUD-02 | P1 | 升级用户的历史原始请求日志未迁移，且主数据库会进入备份 | 确认，影响曾开启 Debug 并产生日志的用户 |
| AUD-03 | P2 | Provider API Key/自定义请求头存在不可见草稿 | 确认，可由滚动离屏或配置重建触发 |
| AUD-04 | P2 | Regex 导入基于旧快照覆盖并发修改 | 确认，存在确定性丢更新时序 |
| AUD-05 | P2 | Activity Result 后的 Resume 刷新与导入/导出 Job 竞争 | 确认，结果回调确定早于 Resume |
| AUD-06 | P2 | 角色列表全量原尺寸解码并常驻所有头像 | 确认，OOM/ANR 的触发规模依设备而异 |
| AUD-07 | P2 | 停止、返回或异常保存流式 partial 时未完整提交生成元数据 | 确认，世界书状态和部分活跃时间不一致 |
| AUD-08 | P2 | 通用错误文案吞掉安全且可行动的失败分类 | 确认，不能通过恢复 `Throwable.message` 修复 |
| AUD-09 | P3 | 群聊页面跨 sibling UiState 读取共享数据和设置草稿 | 确认违反状态树规范，未确认现有稳定功能故障 |
| AUD-10 | P3 | 群聊镜像枚举使用 `valueOf(name)`，创建页反向依赖聊天页实现 | 确认，当前枚举恰好同名所以尚未必现崩溃 |
| AUD-11 | P3 | 请求日志 KDoc 过期、群聊 KDoc 链接错误、About 触发 `UseKtx` | 确认，属于维护性和规范问题 |

### 1.2 已执行的快速验证

- `LLMRequestLogRepository.saveLog()` 仅在用户主动开启 Debug 后落库原始 `requestJson`/`responseJson`，符合该功能的排障用途。
- 请求日志与业务数据共用主数据库且会参与备份仍是独立问题，需要通过迁移、独立数据库和备份排除解决。
- 原审计运行的 149 个 Debug 单元测试全部通过；Debug/Release 的 assemble 与 lint 均完成。Lint 为 0 error、101 warning，其中最新提交直接新增 `AboutActivity.kt` 的 `UseKtx` 告警。

## 2. 总体修复原则

1. **隐私边界由用户开关控制。** 原始 Provider payload 只允许进入用户主动开启的 Debug 请求日志，不进入普通 UiState、Toast、错误提示或系统备份。
2. **数据一致性下沉到 Repository。** UI 禁用只能作为交互防线，不能替代仓储层的原子读改写和 Room 事务。
3. **流式生成只有一个收尾所有者。** 正常结束、网络异常、Stop 和 Back 必须复用同一提交路径，避免重复保存或字段漂移。
4. **敏感原文不进入 UiState/SavedState。** API Key 和请求头只在局部未确认编辑器及 ViewModel 私有内存中短暂停留。
5. **长任务保持可取消。** 不把独立 Job 简单退回阻塞 Intent 消费者的长 `suspend` handler，以免 Back 无法及时执行。
6. **修复必须有反例测试。** 每项至少加入一个能在修复前稳定失败、修复后稳定通过的测试。
7. **不扩大无关重构。** 群聊状态上移和共享模型移动以消除当前违规为边界，不重写整套页面框架。

## 3. 分项处理计划

### AUD-01：Debug 请求日志保存原文（非问题）

Request Log 是用户主动开启的本地调试功能，目的就是查看和复制实际发送的请求与供应商返回内容。仓库必须在 Debug 关闭时拒绝写入，开启后则原样保存请求与响应，不做字段映射或内容投影。

原文可能包含 Prompt 和供应商返回内容，因此安全边界放在存储生命周期上：使用独立、可丢弃的数据库，明确排除云备份和设备迁移；普通错误提示继续通过安全失败分类生成，不直接展示异常正文。

回归测试应覆盖 Debug 开关以及请求、响应逐字保存，避免后续再次把调试日志替换为摘要或投影。

### AUD-02（P1）：历史原始请求日志未迁移且可进入备份

**确认依据**

- 父提交会把完整 request/response 原文写入 `llm_request_logs`。
- 当前提交只保护新写入和 UI 展示，`AppDatabase.version` 仍为 1，`autoMigrations` 为空。
- `AndroidManifest.xml` 设置 `allowBackup=true`，两套备份规则仍为空模板。Android Auto Backup 默认包含数据库；空规则没有排除 `primary.sqlite`。
- 因此升级后旧 Prompt、响应、密钥或请求头仍留在主数据库，日志页只是把它们隐藏。

**最佳解决方案**

按“立即清旧数据 + 永久隔离调试数据”两层实施：

1. 将主 `AppDatabase` 升到 v2，导出 schema，并显式注册 `MIGRATION_1_2`。
2. v2 主库 schema 移除 `LLMRequestLog`，迁移中删除 `llm_request_logs`。日志是可丢弃调试数据，清空/删表比尝试重新脱敏历史任意文本更可靠。
3. 新建独立 `RequestLogDatabase`，只包含日志 Entity/DAO；仓库改为依赖该数据库。
4. 在 `backup_rules.xml` 和 `data_extraction_rules.xml` 的 cloud backup/device transfer 中显式排除新日志数据库及相关 sidecar 文件，继续保留主业务数据库备份。
5. 对已发布的 1→2 路径禁止依赖 destructive fallback；缺少迁移时应让测试和开发阶段直接失败，不能静默清空角色、会话和世界书。
6. 如威胁模型要求物理清除 SQLite 空闲页/WAL，另做一次性、可恢复的安全清理流程，在 IO 线程 checkpoint/truncate 并重整数据库；该流程必须独立测试，不能在 Room migration 事务内直接 `VACUUM`。

已上传到用户云端的历史备份无法由应用主动撤回。可保证的是：旧 v1 备份恢复到新版本后会先迁移并删除日志，且新版本不再把日志加入后续备份。

**不采用的方案**

- 排除整个 `primary.sqlite`：会同时丢失角色、会话、世界书等业务数据的备份能力。
- 设置 `allowBackup=false`：影响范围远大于日志问题。
- Kotpref 一次性清理标记：不如数据库版本迁移可靠，无法天然覆盖旧备份恢复和崩溃重试。

**修改范围**

- `libs/room/AppDatabase.kt`、新增 `libs/room/migration/AppDatabaseMigrations.kt`。
- 新增 `RequestLogDatabase.kt`，调整 `LLMRequestLogRepository.kt` 和 `RPClientApp.kt`。
- `app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml`。
- Gradle 的 Room schema 导出与 migration test 配置。

**回归测试与验收**

- 使用 `MigrationTestHelper` 建立 v1 数据库，插入含 sentinel 的日志和业务数据；迁移到 v2 后日志不存在，角色/会话等业务行完整保留。
- 模拟恢复 v1 备份后首次打开 v2，重复验证清理。
- 新日志数据库覆盖 save/read/delete 和诊断投影集成测试。
- 静态解析两套备份 XML，断言日志数据库和 sidecar 均被排除，主数据库未被排除。
- 检查导出的 v1/v2 schema 并纳入版本控制。

### AUD-03（P2）：Provider 凭据存在不可见草稿

**确认依据**

- `LLMProviderEditLayout.kt` 在 LazyColumn item 内以 `remember(form.id)` 保存 API Key 和 custom headers 的空白输入。
- item 离开组合或 Activity 配置重建时，本地输入会丢失；ViewModel 的 `mApiKeyDraft`/`mCustomHeadersDraft` 仍保留新值。
- Save/Test 使用 ViewModel 私有草稿，UI 又没有消费 `hasApiKey`/`hasCustomHeaders`，会出现“输入框为空但保存隐藏值”。
- 直接改成 `rememberSaveable` 会把原文放入 SavedState/Bundle，违反项目隐私规范。

**最佳解决方案**

为 API Key 和 custom headers 分别建立非敏感三态：`KeepExisting / Replace / Clear`。

1. UiState 只保存“原值是否存在”、三态和“已有已确认替换值”等非敏感信息。
2. Replace 使用独立敏感编辑对话框；未确认输入只存在于对话框局部状态。用户确认后，原文才进入 ViewModel 私有暂存区，UI 明确显示“保存时替换”。
3. Clear 必须显式点击，空白文本框不能隐式解释为清除。
4. Save 和 Test 共用一个 credential resolver，保证测试值与最终保存值一致。
5. Back 的未保存判断依据三态；Discard、Finished 和 `onCleared()` 清空私有替换值。
6. custom headers 在确认时完成 JSON 结构校验，错误提示不得回显原始头内容。

配置重建发生在未确认对话框时，局部输入允许丢弃，因为它从未改变暂存状态；已确认替换在重建后仍以明确的非敏感状态展示，不再是隐藏修改。

**修改范围**

- `feature/llmprovideredit/model/LLMProviderEditForm.kt`、`LLMProviderEditMapper.kt`。
- `presentation/LLMProviderEditUiState.kt`、`LLMProviderEditUiIntent.kt`。
- `LLMProviderEditViewModel.kt`、`ui/LLMProviderEditLayout.kt` 和 strings。
- 表单测试及新增 ViewModel/Compose 测试。

**回归测试与验收**

- Existing + Keep 保留原值；Replace 的 Test/Save 使用同一新值；Clear 保存空值。
- Back 对 Replace/Clear 正确提示未保存修改。
- 滚动离屏和配置重建后只显示准确三态，不出现隐藏保存。
- 新建 Provider 覆盖未设置、设置、撤销和清除流程。
- UiState、SavedState、Toast、日志中不得出现凭据或请求头 sentinel。

### AUD-04（P2）：Regex 导入覆盖并发修改

**确认依据**

- `RegexScriptViewModel.onImportJson()` 捕获旧 `state` 后启动独立 Job。
- Job 完成时用旧 `state.scripts + imported` 做 ID 冲突处理并覆盖保存。
- 导入期间启停、编辑、复制、删除、排序、作用域/角色切换等 handler 仍可执行且没有传输守卫。
- 确定性时序为：导入捕获 `A(enabled=true)` → 用户保存 `A(false)` → 导入完成写回旧 `A(true)+imported`，用户修改丢失。

**最佳解决方案**

1. 定义稳定的 `RegexScriptTarget(scope, characterId)`。
2. `RegexScriptRepository` 提供由单例 `Mutex` 保护的 `updateScripts(target, transform)`：锁内重读最新列表、执行 transform、持久化并返回权威结果。
3. 文件读取和 JSON 解析在锁外完成；真正提交时才基于最新列表处理 ID 冲突。
4. 启停、编辑、复制、删除、排序和导入全部改走同一原子入口，禁止继续用 UiState 全量覆盖仓库。
5. Character target 通过 `CharacterRepository` 的事务/专用扩展字段更新入口写入，保留其他 extension，Feature 不直接访问 DAO。
6. 导入提交到开始时捕获的 target；用户切换页面后只清除传输状态，不把旧 target 的列表发布到当前页面。
7. UI 可在传输期间禁用冲突操作作为辅助防线；Job 在 `finally` 中按身份清理引用。

**修改范围**

- `libs/regex/RegexScriptRepository.kt`，可新增 `RegexScriptTarget.kt`。
- 必要时扩展 `libs/room/repository/CharacterRepository.kt` 的事务更新能力。
- `feature/regexscript/RegexScriptViewModel.kt`；若冻结交互则调整 `RegexScriptLayout.kt`。

**回归测试与验收**

- 用 `CompletableDeferred` 暂停导入读取，分别并发启停、编辑、删除、复制和排序，断言双方修改都保留。
- 导入期间切换 target，断言写入原 target 且当前页面不被覆盖。
- 提交前新增同 ID 脚本，断言按最新列表重新生成 ID。
- 两个并发 Repository mutation 最终无丢更新。
- Back/取消不留下半写入或永久进度状态。

### AUD-05（P2）：Activity Result 后 Resume 刷新与传输任务竞争

**确认依据**

- Android 36 对本应用目标版本会在 `onResume` 前交付 pending Activity Result；AndroidX ActivityResultRegistry 在 callback 活跃时同步交付，Stop 状态下也会在 `ON_START` 交付，仍早于 `ON_RESUME`。
- 因而文件选择器返回后的 Intent 顺序是 Import/Export Result → Resume。
- `CharacterListViewModel` 和 `WorldBookListViewModel` 的 Result handler 启动 `mTransferJob` 后立即返回；串行 Intent 消费者随后处理 Resume，两次刷新开始并发。
- 两个刷新都捕获旧 UiState，较晚返回的旧查询可提前清除 Loading、覆盖导入后的列表/选中项或写回中途条目数。

**最佳解决方案**

1. 保留独立、可取消的 `mTransferJob`，确保 Back 能及时取消。
2. 两个 `onResume()` 在刷新前检查 `mTransferJob?.isActive == true`，传输中直接跳过；导入 Job 负责唯一的最终刷新。
3. 刷新增加递增 generation/request token，只有最新请求可发布状态，防御未来新增并发入口。
4. Job 的 `finally` 仅在引用仍指向当前 Job 时清理；页面进入 Finished 后不得重新发布 Normal。
5. 在 ViewModel 添加中文生命周期注释，明确 Result 先于 Resume 以及为什么必须跳过刷新。

不让 Resume 等待 Mutex：这会占住串行 Intent 消费者，使 Back 再次无法及时处理。

**修改范围**

- `feature/characterlist/CharacterListViewModel.kt`。
- `feature/worldbooklist/WorldBookListViewModel.kt`。
- 新增对应 ViewModel 并发测试；Activity 本身无需改动。

**回归测试与验收**

- 延迟导入后发送 Resume，断言没有第二次列表查询且 Loading 保持到传输完成。
- Back 可立即进入 Finished 并取消 Job；取消后不得发布普通状态。
- 成功后角色列表包含导入角色并选中其 ID；世界书条目数是最终值。
- 覆盖失败、导出期间 Resume、取消选择器和“旧查询最后完成”场景。
- 在设备/模拟器上补一条 Activity Result + 生命周期集成测试。

### AUD-06（P2）：角色列表全量原尺寸头像解码并常驻

**确认依据**

- `CharacterListViewModel.refreshCharacters()` 对全部角色执行 `characters.map`，每项调用 `FileRepository.loadBitmap()`。
- `loadBitmap()` 直接 `BitmapFactory.decodeFile(path)`，没有 bounds 检查或 `inSampleSize`。
- 全部 `ImageBitmap` 被 `mAllCharacterItems` 在 ViewModel 生命周期内强引用；LazyColumn 可见范围不再限制解码数。
- 默认 ARGB_8888 约为每像素 4 字节：2048² 约 16 MiB，4096² 约 64 MiB。每次刷新还可能出现新旧批次短时重叠。

**最佳解决方案**

采用“可见项驱动 + 边界采样 + 字节有界缓存”，不能只把全量原图换成全量缩略图：

1. 文件边界新增 `loadSampledBitmap(uuid, requestedWidthPx, requestedHeightPx)`；先 `inJustDecodeBounds`，用 `Long` 安全计算 power-of-two `inSampleSize`，再解码并按需精确缩放。
2. 列表刷新只构造轻量文本、颜色和私有 `characterId -> avatarUuid` 映射，不做 Bitmap IO。
3. LazyColumn 根据 `LazyListState.layoutInfo.visibleItemsInfo` 发出 `VisibleCharactersChanged(ids, targetSizePx)` Intent；Compose 不直接读文件。
4. ViewModel 只加载新增可见 ID，取消离屏未完成任务，并从最新 UiState 移除离屏图片强引用。
5. 私有 LRU 以 `Bitmap.byteCount` 计量，key 包含 avatar UUID 和目标尺寸；头像或尺寸变化时失效。
6. Back/`onCleared()` 取消加载任务；快速滚动必须通过请求 token 防止错图。

**修改范围**

- `libs/room/repository/FileRepository.kt`，或新增专职 `StoredImageThumbnailLoader`。
- `feature/characterlist/CharacterListViewModel.kt`、`CharacterListUiIntent.kt`、`CharacterListLayout.kt`。
- 视状态方案调整 `CharacterListItem.kt`/`CharacterListUiState.kt`。

**回归测试与验收**

- 2048×2048 测试图请求小尺寸时，输出边长和 `byteCount` 不得接近原图；覆盖超宽/超高、损坏文件和非法尺寸。
- 100 个带头像角色初始化时 loader 调用数为 0；发 6 个可见 ID 后只加载 6 个。
- 重复可见事件命中缓存；离屏任务取消，UiState 不再强引用离屏 Bitmap。
- 超过字节预算时逐出最旧非可见项；UUID 更新不显示旧缓存。
- instrumentation 快速滚动确认占位图正确替换、无错图、无主线程文件 IO。

### AUD-07（P2）：停止流式生成时没有原子提交 partial 与元数据

**确认依据**

- `ChatViewModel.onBack()` 和 Stop 都调用 `persistStoppedGeneration()`。
- 该函数只更新/创建消息正文；不写 `worldInfoStateJson`，Update/Regenerate 场景也不刷新 `latestTime`。
- 正常完成路径会更新正文、`latestTime` 和 `worldInfoStateJson`；流式异常保存 partial 的分支同样漏写世界书状态。
- 新建回复的空占位消息会提前刷新 `latestTime`，所以不能笼统表述为所有 partial 都没有时间；但最终接受时刻和多字段原子性仍不统一。
- 已保存 partial 会参与下一轮历史，旧 sticky/cooldown 状态会导致世界书错误重触发或冷却失效。

**最佳解决方案**

1. 用一个生成协程私有的 `ActiveStreamingGeneration` 快照替代分散的可变字段，包含 session/output/messageId/是否新建/正文/Regex 上下文/`worldInfoStateJson`。
2. `ChatRepository` 增加事务型 `commitGenerationResult(...)`，同一事务内完成：
   - 写入或更新非空正文；
   - 删除覆盖该消息的失效摘要；
   - 更新 `latestTime`；
   - 写入 `worldInfoStateJson`。
3. 只有确实接受了非空 partial 才推进世界书状态；首个 delta 前取消时删除空占位且不推进状态。
4. 正常完成、网络异常 partial、Stop 和 Back 共用同一收尾函数。
5. 生成协程是唯一收尾所有者；取消时在 `NonCancellable` 中提交自己的不可变快照。Stop/Back 使用 `cancelAndJoin()` 等待收尾后再刷新/Finished，调用方不得重复写库。

**修改范围**

- `feature/chat/ChatViewModel.kt`。
- `libs/room/repository/ChatRepository.kt`，必要时扩展 `ChatSessionDao.kt`。
- 新增生成收尾单元测试和 Repository instrumentation test。

**回归测试与验收**

- Regenerate 收到 delta 后 Stop/Back：正文、摘要失效、`latestTime`、世界书状态全部正确，Back 必须落库后 Finished。
- 新建回复 partial 和网络异常 partial 使用同一元数据规则。
- 首个 delta 前取消删除占位且不改变世界书状态。
- 并发取消不会双写、重复创建或在快照清空后读取可变字段。
- Repository 测试证明全部字段处于同一 Room transaction。

### AUD-08（P2）：错误处理过度泛化，丢失安全且可行动的信息

**确认依据**

- Chat 的 Send、Regenerate、Continue、Impersonate、Summary，以及 GroupChat 的 Continue、Generate、Summary 均把异常压成操作级通用文案。
- 可稳定产生但被吞掉的安全错误包括：未配置 Provider、`PromptBudgetExceededException`、HTTP 401/403/429、网络连接失败和 `LLMEmptyResponseException`。
- 最新提交用固定文案替换直接展示 `Throwable.message` 是正确的隐私方向；问题在于缺少安全的类型化分类，不能退回原始 message。

**最佳解决方案**

1. 新增只含安全字段的领域异常，如 `NoEnabledLLMProviderException`、只携带状态码的 `LLMHttpStatusException`。
2. 建立共享纯函数 classifier，输出无资源依赖的 `GenerationFailure`：NoProvider、PromptBudget、Unauthorized/Forbidden、RateLimited、HttpFailure、Network、EmptyResponse、Unknown。
3. Prompt budget 只携带经过约束的 token 数；HTTP 异常禁止携带正文、URL、请求头或 API Key。
4. Chat/GroupChat 把分类映射到本地化资源；Unknown 继续使用各操作自己的 generic fallback。
5. 普通请求、流式请求和 Summary 统一使用此策略；`CancellationException` 永远不映射为失败。

**修改范围**

- `libs/llm/adapter/LLMHttpUtils.kt`、`LLMResponseValidator.kt`。
- `libs/room/repository/LLMRepository.kt`，新增 `LLMExceptions.kt` 和共享 failure classifier。
- `feature/chat/ChatViewModel.kt`、`feature/groupchat/GroupChatViewModel.kt`。
- `res/values*/strings.xml` 与分类单元测试。

**回归测试与验收**

- 分别断言无 Provider、budget、401、403、429、500、`IOException`、空响应和 Unknown 的分类。
- Unknown exception 的 message 注入伪 API Key、私密正文、请求头和路径，断言 UiState/Toast 只含 fallback。
- 普通和流式 HTTP 入口都只能抛出安全 typed exception。
- 单聊/群聊每个操作保留正确的 fallback；取消不显示失败。

### AUD-09（P3）：群聊 UiState 跨 sibling 读取共享数据和设置草稿

**确认依据**

- 设置页从 `conversationState.members` 读取成员；会话页和 `onSelectSpeaker()` 从 `settingsState.activationStrategy` 读取策略。
- `activationStrategy` 同时承担“数据库已提交策略”和“设置页未保存草稿”两种生命周期，状态归属不清。
- 这违反规范中“同一父节点多个状态共享的数据应上移到父状态”的要求，并可能让未保存草稿提前影响会话行为。

**最佳解决方案**

1. 将共享且已提交的数据上移到 `GroupChatUiState.Normal`：`members`、`activeActivationStrategy`。
2. `settingsState.activationStrategy` 只表示未保存草稿；会话页和 `onSelectSpeaker()` 只读 `activeActivationStrategy`。
3. `conversationState` 只保存消息、输入、发言人选择及生成生命周期。
4. `loadNormalState()` 同时初始化已提交策略和设置草稿；取消设置只丢草稿，保存成功并刷新后才更新 active 值。

无需为此重写整个 sealed page 架构；字段上移即可满足当前状态树边界。

**修改范围**

- `feature/groupchat/presentation/GroupChatUiState.kt`。
- `feature/groupchat/ui/GroupChatLayout.kt`。
- `feature/groupchat/GroupChatViewModel.kt`。

**回归测试与验收**

- 修改策略草稿但不保存时，会话有效策略保持原值；保存后才切换。
- 设置页和会话页使用同一根级 members。
- 成员增删、排序、禁言后两页都显示最新列表。
- Manual/非 Manual 的发言选择只依据已提交策略。

### AUD-10（P3）：群聊枚举名称映射和 sibling feature 依赖

**确认依据**

- `GroupChatViewModel` 有 5 处通过 `valueOf(name)` 映射消息来源、发言策略和角色卡模式；`GroupChatCreateViewModel` 另有策略转 Entity 的同类映射。
- 镜像枚举当前成员恰好同名；任一方改名或新增成员后，编译可能继续通过而运行时抛 `IllegalArgumentException`。
- `groupchatcreate` 直接导入 `feature.groupchat.presentation.GroupChatActivationStrategy`，还复用 `feature.groupchat.model` 的 lorebook UI model 和 `feature.groupchat.ui.GroupChatLorebookSelector`，形成 sibling feature 反向依赖。

**最佳解决方案**

1. 将两个 Feature 共同使用的纯群聊类型移动到 presentation-neutral 的 `libs/groupchat/model`，至少包括 activation strategy；共享 lorebook 纯模型也移到公共模型位置。
2. 将共享 Compose selector 移到 `ui/widgets/groupchat`，两个 Feature 都依赖公共组件，不互相导入实现包。
3. Room Entity 可继续保留持久化枚举以避免 schema 变化；所有 Entity ↔ shared/UI 映射改为集中、穷举的 `when`。
4. `GroupChatMessage.Source` 和 CharacterCardMode 同样使用穷举映射；映射提取为可测试 internal 顶层函数。
5. 完成后 `groupchatcreate` 不得再导入 `feature.groupchat.*`。

**修改范围**

- 新增 `libs/groupchat/model` 共享类型和 `ui/widgets/groupchat` 公共 selector。
- 两个 Feature 的 UiState、UiIntent、ViewModel、Layout 及相关 model import。
- 新增群聊枚举映射测试。

**回归测试与验收**

- 对所有发言策略做 entity → shared → entity round-trip。
- 对全部 CharacterCardMode 和 GroupChatMessage.Source 做穷举双向/展示映射测试。
- 创建页四种策略均正确写入 Repository。
- 静态检查 `groupchatcreate` 不再引用 `feature.groupchat.*`。
- 对比 Room schema，确保持久化字符串兼容；如最终改变 Entity 字段类型，必须另给 migration。

### AUD-11（P3）：注释与 Lint 规范问题

**确认依据**

- `GroupChatUiState.Normal` KDoc 的 `[generationState]` 不是该类直接成员，链接目标错误；实际字段位于 conversation 子状态。
- `AboutActivity.onOpenRepository()` 使用 `Uri.parse()`，Debug/Release Lint 均报告 `UseKtx`；该行由最新提交新增。

**最佳解决方案**

1. 群聊 KDoc 改为显式链接 `GroupChatConversationState.generationState`，或直接用无歧义文字描述。
2. `AboutActivity` 引入 `androidx.core.net.toUri`，改为 `uiState.githubRepoUrl.toUri()` 并移除 `android.net.Uri` import。
3. 不顺带清理与本提交无关的全部 101 条历史 warning；本项门槛是目标 KDoc 正确、最新提交改动文件无新增 warning、About 的 `UseKtx` 消失。

**修改范围**

- `feature/groupchat/presentation/GroupChatUiState.kt`。
- `feature/about/AboutActivity.kt`。

**回归测试与验收**

- 运行 Debug/Release lint，`AboutActivity` 不再命中 `UseKtx`。
- 点击仓库项仍发送 `ACTION_VIEW`，Intent data 等于配置中的仓库 URI。
- 人工复核 KDoc 与最终实现一致。

## 4. 推荐实施顺序与依赖

### 阶段 A：隐私阻断项，完成前不得发布

1. AUD-02：实施主库 v2 迁移、独立日志库和备份排除。

### 阶段 B：数据一致性与生命周期

1. AUD-07：统一流式生成收尾和 Room 事务。
2. AUD-04：Regex 仓储层原子 mutation。
3. AUD-05：列表传输与 Resume 协调。
4. AUD-03：Provider 凭据三态协议。

这些任务可以由不同分支并行开发，但合入时每项必须携带自己的反例测试。

### 阶段 C：性能和可行动错误

1. AUD-06：缩略图 loader、可见项加载和字节 LRU。
2. AUD-08：共享安全错误分类及单聊/群聊接入。

### 阶段 D：状态与共享模型清理

1. AUD-09 和 AUD-10 建议同一分支连续完成，避免群聊 UiState 和 import 被反复改动。
2. 完成 AUD-11 剩余 KDoc/`UseKtx`，再跑全量验证。

## 5. 发布前验证矩阵

### 5.1 自动化命令

```powershell
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleDebug
.\gradlew.bat --offline --no-daemon --console=plain :app:lintDebug
.\gradlew.bat --offline --no-daemon --console=plain :app:assembleRelease
.\gradlew.bat --offline --no-daemon --console=plain :app:lintRelease
git diff --check
```

有设备或模拟器后必须补跑 migration、Repository、Bitmap 和 Activity Result instrumentation tests；不能只用 JVM 测试替代。

### 5.2 隐私门槛

- API Key、请求头和异常正文不得进入普通 UiState、Toast 或非调试日志。
- v1→v2 迁移保留所有业务数据并移除历史日志。
- 两套备份规则明确排除独立日志数据库，主业务库仍可备份。
- Unknown exception 不得把 message、URL、路径、头或凭据带入 UI。

### 5.3 流程与并发门槛

- 文件选择器返回后的 Result/Resume 不重复刷新、不提前结束 Loading、不覆盖导入结果。
- Regex 任意两项并发 mutation 不丢更新。
- Stop/Back/异常/正常结束共享生成收尾，消息、摘要、时间和世界书状态保持一致。
- Provider Keep/Replace/Clear 在滚动、旋转、测试、保存和返回路径中语义一致。

### 5.4 性能与架构门槛

- 角色列表首次加载不解码不可见头像；缩略图尺寸和缓存字节数有明确上限。
- Compose 不直接进行文件 IO，Feature 不直接访问 DAO。
- `groupchatcreate` 不依赖 `feature.groupchat.*`，所有持久化枚举映射穷举。
- Debug/Release 均可构建；目标改动文件无新增 Lint warning。

## 6. 完成定义

只有同时满足以下条件，处理计划才算完成：

1. 10 项已确认问题全部有对应代码变更和修复前失败/修复后通过的回归测试。
2. P1 的迁移与备份验证全部通过。
3. 所有 P2 的取消、失败、Back、配置重建和并发路径均覆盖，而不只验证成功路径。
4. Debug/Release 单元测试、assemble、lint 和 `git diff --check` 通过。
5. 有设备/模拟器的 instrumentation 矩阵通过，并记录 Android 版本与测试结果。
6. 最终 KDoc 与真实安全边界一致，明确原始请求日志只在 Debug 模式下产生且不参与备份。

## 7. 参考资料

- 项目规范：`doc/coding-guidelines.md` 及其 MVI、Intent、ViewModel、Room、RPClient 领域专题。
- [Android Compose 状态与 `remember`](https://developer.android.com/develop/ui/compose/state)
- [Android UI 状态保存与 `rememberSaveable`/SavedStateHandle](https://developer.android.com/develop/ui/compose/state-saving)
- [Android 高效加载大尺寸 Bitmap](https://developer.android.com/topic/performance/graphics/load-bitmap)
- [Android 平台 `ActivityThread` 源码](https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/app/ActivityThread.java)
- [AndroidX `ActivityResultRegistry` 源码](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/result/ActivityResultRegistry.kt)
- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Room 数据库迁移](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [MigrationTestHelper API](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper)
