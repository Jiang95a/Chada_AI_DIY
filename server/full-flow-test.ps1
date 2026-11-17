# 主流程端到端验收：注册 → 模板 → 创建 → 编辑 → 发布 → 公开提交 → 轨迹 → 回看 facts
# 用法：powershell -ExecutionPolicy Bypass -File server/full-flow-test.ps1
# 依赖：本地后端已在 127.0.0.1:8080 跑起来，且执行过 012 / 013 迁移。
$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080/api/v1'
$pass = 0
$fail = 0

function Hit($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{
        Uri                = "$base$path"
        Method             = $method
        UseBasicParsing    = $true
        TimeoutSec         = 40
        MaximumRedirection = 0
        Headers            = $headers
    }
    if ($null -ne $body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 30))
    }
    try {
        $resp = Invoke-WebRequest @params
        return @{ code = [int]$resp.StatusCode; body = (ReadUtf8 $resp) }
    } catch {
        $r = $_.Exception.Response
        if ($r) {
            $sr = New-Object System.IO.StreamReader($r.GetResponseStream(), [System.Text.Encoding]::UTF8)
            return @{ code = [int]$r.StatusCode; body = $sr.ReadToEnd() }
        }
        return @{ code = 0; body = $_.Exception.Message }
    }
}

function ReadUtf8($resp) {
    $ms = New-Object System.IO.MemoryStream
    $resp.RawContentStream.Position = 0
    $resp.RawContentStream.CopyTo($ms)
    return [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
}

function Check($name, $got, $want) {
    if ($got -eq $want) {
        Write-Host ("  [PASS] {0} -> {1}" -f $name, $got) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0} -> 期望 {1}，实际 {2}" -f $name, $want, $got) -ForegroundColor Red
        $script:fail++
    }
}

function Json($body) {
    return $body | ConvertFrom-Json
}

Write-Host "`n===== 1. 注册与登录 =====" -ForegroundColor Cyan
$email = "flow$((Get-Random -Maximum 99999999))@example.com"
$r = Hit 'POST' '/auth/register' @{ email = $email; password = 'Passw0rd!123'; displayName = '主流程测试' } $null
Check 'POST /auth/register' $r.code 200
$token = (Json $r.body).data.accessToken
if (-not $token) { Write-Host '无法拿到 accessToken，停止' -ForegroundColor Red; exit 1 }

Write-Host "`n===== 2. 模板只剩心理 / 生涯 =====" -ForegroundColor Cyan
$r = Hit 'GET' '/templates' $null $token
Check 'GET /templates' $r.code 200
$templates = @((Json $r.body).data)
Check '模板总数 = 8' $templates.Count 8
$general = @($templates | Where-Object { $_.kind -eq 'general' })
Check '通用模板 = 0' $general.Count 0
$templateId = $templates[0].id

Write-Host "`n===== 3. 创建 → 保存草稿 → 发布 =====" -ForegroundColor Cyan
$r = Hit 'POST' '/projects' @{ title = '主流程测试活动'; description = '端到端测试'; templateId = $templateId } $token
Check 'POST /projects' $r.code 200
$projectId = (Json $r.body).data.id
$slug = 'flow-' + (Get-Random -Minimum 100000 -Maximum 999999)
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$doc = @{
    title = '主流程测试活动'
    theme = @{ primary = '#2d5046'; accent = '#b88c72'; bg = '#faf8f5'; text = '#1f3129'; radius = 12; fontScale = 1; spacing = 1; buttonStyle = 'solid'; font = 'sans' }
    pages = @(
        @{ id = 'p1'; name = '首页'; role = 'home'; bg = ''; blocks = @(
                @{ id = 'b1'; type = 'banner'; props = @{ title = '主流程测试活动'; subtitle = '从创建到回看'; image = ''; height = 220 }; style = @{ bg = ''; paddingX = 20; paddingY = 10; radius = 0; align = 'left' }; hidden = $false },
                @{ id = 'b2'; type = 'form'; props = @{ title = '报名信息'; buttonText = '提交报名'; successHint = '提交成功'; fields = @(
                            @{ id = 'f1'; label = '姓名'; type = 'text'; required = $true; placeholder = '请输入姓名'; options = @() }
                        ) }; style = @{ bg = ''; paddingX = 20; paddingY = 10; radius = 0; align = 'left' }; hidden = $false }
            ) }
    )
}
$r = Hit 'PUT' "/projects/$projectId/draft" @{ document = $doc; baseRevision = 0 } $token
Check 'PUT /projects/{id}/draft' $r.code 200
$r = Hit 'POST' "/projects/$projectId/publish" @{ settings = @{}; slug = $slug } $token
Check 'POST /projects/{id}/publish' $r.code 200
if ($r.code -ne 200) { Write-Host $r.body -ForegroundColor Red; exit 1 }

Write-Host "`n===== 4. 公开页 → 同意 → 提交表单 =====" -ForegroundColor Cyan
$r = Hit 'GET' "/public/activities/$slug" $null $null
Check 'GET /public/activities/{slug}' $r.code 200
$cr = Hit 'POST' "/public/activities/$slug/consents" @{} $null
Check 'POST /consents' $cr.code 200
$consentId = (Json $cr.body).data.consentId
$idem = 'flow-' + [guid]::NewGuid().ToString()
$answers = @{ 'b2:f1' = '张三' }
$r = Hit 'POST' "/public/activities/$slug/submissions" @{
    pageId = 'p1'; formComponentId = 'b2'; answers = $answers;
    idempotencyKey = $idem; privacy = @{ consentId = $consentId }
} $token
Check 'POST /submissions（登录态，计入个人表单数）' $r.code 200

Write-Host "`n===== 5. 保存轨迹（含 history） =====" -ForegroundColor Cyan
$trace = @{
    domain = 'mental'
    semester = '2026-秋'
    summary = '今天把压力写下来了'
    change = '从不说，到写下一句'
    tags = @(@{ content = '压力'; source = 'auto'; createdAt = $now })
    history = @(@{ at = $now; kind = 'input'; tags = @('压力'); summary = '今天把压力写下来了'; change = '从不说，到写下一句' })
    updatedAt = $now
}
$r = Hit 'PUT' "/traces/$slug" $trace $token
Check 'PUT /traces/{slug}' $r.code 200
$r = Hit 'GET' "/traces?activityId=$slug" $null $token
Check 'GET /traces 回读' $r.code 200
$readTrace = (Json $r.body).data
Check 'history 回读条数 = 1' @($readTrace.history).Count 1

Write-Host "`n===== 6. 回看 facts =====" -ForegroundColor Cyan
$r = Hit 'GET' '/insights/facts?days=30' $null $token
Check 'GET /insights/facts' $r.code 200
$facts = (Json $r.body).data
Check 'activityCount >= 1' ($facts.activityCount -ge 1) $true
Check 'formCount >= 1' ($facts.formCount -ge 1) $true
Check 'eventCount >= 1' ($facts.eventCount -ge 1) $true
$toolCount = @($facts.events | Where-Object { $_.source -eq 'tool' }).Count
Write-Host "  facts: activity=$($facts.activityCount) form=$($facts.formCount) event=$($facts.eventCount) tool=$toolCount"

Write-Host "`n===== 7. 模板 API 显式查 general =====" -ForegroundColor Cyan
$r = Hit 'GET' '/templates?kind=general' $null $token
Check 'GET /templates?kind=general' $r.code 200
Check 'general 返回 0' @((Json $r.body).data).Count 0

Write-Host "`n===============================" -ForegroundColor Cyan
Write-Host ("结果：PASS {0} / FAIL {1}" -f $pass, $fail) -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
if ($fail -gt 0) { exit 1 }
