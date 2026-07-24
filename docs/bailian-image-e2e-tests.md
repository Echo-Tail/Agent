# 阿里百炼图片真实端到端测试

真实测试覆盖 `BailianImageAdapter` 的文生图、图生图、多图输出、同步/异步结果处理以及结果 URL 下载验证。
测试会调用付费接口，因此普通 `gradle test` 会排除 `bailian-e2e` 标签，必须显式执行专用任务。

PowerShell 示例：

```powershell
$env:BAILIAN_E2E_ENABLED='true'
$env:BAILIAN_E2E_API_KEY='<API Key>'
$env:BAILIAN_E2E_BASE_URL='https://llm-<workspace-id>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1'
$env:BAILIAN_E2E_MODEL='qwen-image-2.0-pro'
gradle --no-daemon bailianE2eTest
```

`BAILIAN_E2E_BASE_URL` 未设置时使用
`https://dashscope.aliyuncs.com/compatible-mode/v1`；模型未设置时使用 `qwen-image-2.0-pro`。

不要把 API Key 写入 Gradle 配置、测试代码、日志或 Git。测试执行完毕后可通过
`Remove-Item Env:BAILIAN_E2E_API_KEY` 清除当前 PowerShell 会话中的密钥。

超时、取消、部分成功和各模型尺寸边界使用本地确定性测试覆盖，避免为了异常路径持续创建收费任务：

- `BailianImageAdapterTest.providerTimeoutShouldReturnSafeBusinessError`
- `ImageGenerationJobExecutorTest` 的取消与部分成功用例
- `ImageSizePolicyTest` 的 qwen-image-2.0、max、plus 尺寸用例
