# 启动脚本：.\start.ps1
# 如果报"禁止运行脚本"，先执行：
#   Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$py = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
if (-not (Test-Path $py)) {
    Write-Host "找不到虚拟环境：$py" -ForegroundColor Red
    Write-Host "请先执行：py -3.12 -m venv .venv" -ForegroundColor Yellow
    exit 1
}

# ---------------------------------------------------------------- 配置自检
# 为什么有这一段：.env 被 `copy .env.example .env` 覆盖过一次，
# 结果服务照常启动、端口照常监听，只是每个 AI 接口都返回 503。
# 排查了半天才发现是 key 空了 —— 这种"静默半残"最费时间。
# 现在缺配置就直接说缺哪几行，不闷头启动。
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host ""
    Write-Host "缺少配置文件：$envFile" -ForegroundColor Red
    Write-Host "第一次安装执行一次：copy .env.example .env   然后填 LLM_API_KEY" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

$envMap = @{}
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    if ($line -match '^\s*#') { continue }
    if ($line -notmatch '=') { continue }
    $name, $value = $line -split '=', 2
    $envMap[$name.Trim()] = $value.Trim()
}

$missing = @()
foreach ($name in @('LLM_API_KEY')) {
    if (-not $envMap.ContainsKey($name) -or -not $envMap[$name]) { $missing += $name }
}
if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "配置文件不完整，缺少这些值：" -ForegroundColor Red
    foreach ($name in $missing) { Write-Host "    $name" -ForegroundColor Red }
    Write-Host ""
    Write-Host "打开下面这个文件填上再启动：" -ForegroundColor Yellow
    Write-Host "    $envFile" -ForegroundColor Yellow
    Write-Host "⚠️  不要用 'copy .env.example .env' 来补 —— 那会把整个 .env 清成空模板。" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

# 可选能力：缺了不拦启动，但说清楚会降级成什么样，免得以为坏了
$warn = @()
if (-not $envMap['VISION_MODEL']) {
    $warn += "VISION_MODEL 为空  -> 海报解析 /poster/extract 会返回 503，前端回退手动填写"
}
if (-not $envMap['IMAGE_MODEL']) {
    $warn += "IMAGE_MODEL 为空   -> AI 出图 /poster/generate 会返回 503，前端提示改用上传"
}
if (-not $envMap['IMAGE_API_KEY']) {
    $warn += "IMAGE_API_KEY 为空 -> 出图会拿 LLM 的 key 去请求，通常是 401"
}
if ($warn.Count -gt 0) {
    Write-Host "以下能力未就绪（不影响启动）：" -ForegroundColor Yellow
    foreach ($item in $warn) { Write-Host "    - $item" -ForegroundColor DarkYellow }
}

Write-Host "启动中... 文档地址 http://127.0.0.1:8000/docs" -ForegroundColor Cyan
& $py -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
