# PureStart v15.1.5（114）测试版

修复你反馈的三点：**磨砂模糊开关点不动/像卡死**、**底栏会跳到错误位置**、**普通模式卡片仍有白框框**。

> 仍未真机验收，按 **pre-release** 发布。

## 1. 磨砂模糊开关无法切换（这是真 bug）

两个原因叠加：

- **单观察者槽被顶掉**：`SettingsRepository` 只有一个 `observer` 字段，四个页面各自 `setObserver` 会互相覆盖。左右滑动页面时 Home/Apps/Settings 都会注册一次，最后注册的那个拿到通知，**设置页从此收不到任何配置变化**。→ 改成 `CopyOnWriteArrayList` 多观察者 + `addObserver/removeObserver`，并统一切主线程回调。
- **开关状态只在首次组合时读一次**：`PreferenceSwitch` 用 `remember(...) { prefs.getBoolean(...) }` 读快照，点击 `apply()` 后没有任何东西触发重读，Switch 就弹回原位——表现就是「点了没反应/卡死」。→ 新增 `PrefFlows.kt`，每个开关/滑杆订阅自己的键（`callbackFlow` + `collectAsState`），写入即回读；设置页还额外挂了偏好监听做双保险。
- 顺带修掉：以前切换背景（极光/系统壁纸）会无条件 `recreate()` 整页，看着也像卡死；现在只有**进出系统壁纸**（需要窗口透明标志）才重建。

## 2. 底栏弹性过大 / 跳到其他位置

- **错误换算**：旧代码把「手指从按下点的位移」直接加到**按下的槽位**上（`basePosition + travelled / tabWidth`），按下点偏在槽位边缘时胶囊就整体偏半槽，松手吸到相邻的错误页。更早的版本还会在按下瞬间把胶囊瞬移到手指位置。
- 新版语义与系统 Pager 一致：**胶囊目标位 = 当前页 − 手指位移 / 单槽宽度**，手指左移一槽宽 = 前进一页，按下不动就不动，首尾钳制在 0..3。
- 落点用「胶囊当前位置」判定（位移过 1/5 槽或甩动够快才翻页），松手回位弹簧的下限从 `.25` 抬到 `.45`、刚度 360→420，**不再甩过头再弹回来**。

## 3. 普通模式卡片白框框

上一版把卡片填充删掉了，结果剩下的**只有那条 1dp 白色高光描边**和一圈浅色阴影光晕——白底没了，白框就露出来了。这版：

- 描边压到 `border × .45`（最大 18%），不再是一条亮白线；
- 卡片**不再投阴影**（浅色背景上的灰白晕边同样会被当成白框）；
- 填充改成上浓下淡的白色渐变，且**由「卡片底色」滑杆唯一控制**（默认 10%，范围 0–40%）；
- **想要彻底没有白框：设置 → 玻璃细则 → 卡片底色拉到 0**，此时卡片只剩背景与极淡描边。

## 新增/更新的测试

- `tests/test_settings_repository.py`：新增多观察者用例——两个观察者都要收到通知、移除后不再收到、切换纯 UI 键（磨砂模糊）也必须触发通知。这条用例正是「开关卡死」的回归保护。
- `tests/check_glass_surfaces.py` / `check_ui_contract.py`：断言卡片不得有阴影/亮白描边、必须存在「卡片底色」参数、底栏拖拽必须按「位移 ÷ 单槽」换算（禁止旧的 `basePosition + slots` 写法）。

## 产物

- `PureStart-v15.1.5-release.apk`（versionCode 114，versionName 15.1.5）
- `PureStart-v15.1.5-source.zip`（源码包，不含签名密钥）

- APK SHA-256：`073f577a5dc593bb8242e85925f49ddf1dd543f5eedcb9baeea4bad543e09750`
- 证书 SHA-256：`6c82544006f036be304a14d7334f102dd186d97998718764f88e7f3e477ab3ee`（与 v15.1.0+ 相同，可直接覆盖安装）

## 验证

`bash gradlew clean :app:testReleaseUnitTest :app:assembleRelease` 通过；`scripts/check.py`、`tests/check_ui_contract.py`、`tests/check_glass_surfaces.py`、`tests/check_apk.py`、`tests/test_settings_repository.py`、`tests/test_backup_repository.py` 全部通过；已确认 `PrefFlows` 与新字段进入 dex。

## 已知限制

- 卡片是否「够通透」是主观项：默认 10% 仍会有一层很淡的雾面，觉得白就把「卡片底色」拉到 0。
- 底栏拖拽手感（阻尼、甩动阈值）尚未在真机上校准，若仍偏弹可告知具体机型，我再调弹簧参数。
- 系统壁纸的窗口合成在个别 ROM 上可能不透出，建议改用自定义图片。
