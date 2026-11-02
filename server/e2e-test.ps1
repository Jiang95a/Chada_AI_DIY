# 端到端验收脚本：对着已经启动的 http://127.0.0.1:8080/api/v1 跑一遍主流程
# 用法：powershell -ExecutionPolicy Bypass -File e2e-test.ps1
$ErrorActionPreference = 'Continue'
$base = "http://127.0.0.1:8080/api/v1"
$pass = 0
$fail = 0

function Hit($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{
        Uri             = "$base$path"
        Method          = $method
        UseBasicParsing = $true
        TimeoutSec      = 40
        MaximumRedirection = 0
        Headers         = $headers
    }
    if ($null -ne $body) {
        $params['ContentType'] = 'application/json; charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 20))
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

# PowerShell 5.1 的 Invoke-WebRequest 按 ISO-8859-1 解码响应体，
# 会把 UTF-8 中文变成乱码。这里统一按 UTF-8 重新解码。
function ReadUtf8($resp) {
    $ms = New-Object System.IO.MemoryStream
    $resp.RawContentStream.Position = 0
    $resp.RawContentStream.CopyTo($ms)
    return [System.Text.Encoding]::UTF8.GetString($ms.ToArray())
}

# PowerShell 5.1 的 Invoke-WebRequest 没有 -Form，手动拼 multipart/form-data
function UploadFile($path, $field, $token) {
    $boundary = [guid]::NewGuid().ToString()
    $fileBytes = [System.IO.File]::ReadAllBytes($path)
    $fileName = [System.IO.Path]::GetFileName($path)
    $bStart = [System.Text.Encoding]::UTF8.GetBytes("--$boundary`r`n")
    $bHead  = [System.Text.Encoding]::UTF8.GetBytes("Content-Disposition: form-data; name=`"$field`"; filename=`"$fileName`"`r`nContent-Type: image/png`r`n`r`n")
    $bEnd   = [System.Text.Encoding]::UTF8.GetBytes("`r`n--$boundary--`r`n")
    $buf = New-Object System.Collections.Generic.List[byte]
    $buf.AddRange($bStart); $buf.AddRange($bHead); $buf.AddRange($fileBytes); $buf.AddRange($bEnd)
    try {
        $resp = Invoke-WebRequest -Uri "$base/assets" -Method POST `
            -Headers @{ Authorization = "Bearer $token" } `
            -ContentType "multipart/form-data; boundary=$boundary" `
            -Body $buf.ToArray() -UseBasicParsing -TimeoutSec 40
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

function Check($name, $got, $want) {
    if ($got -eq $want) {
        Write-Host ("  [PASS] {0}  -> {1}" -f $name, $got) -ForegroundColor Green
        $script:pass++
    } else {
        Write-Host ("  [FAIL] {0}  -> 期望 {1}，实际 {2}" -f $name, $want, $got) -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "`n===== 1. 健康检查 =====" -ForegroundColor Cyan
$r = Hit 'GET' '/health' $null $null
Check 'GET /health' $r.code 200
Write-Host "  $($r.body)"
$r = Hit 'GET' '/ping' $null $null
Check 'GET /ping' $r.code 200

Write-Host "`n===== 2. 注册 / 登录 =====" -ForegroundColor Cyan
$email = "e2e$((Get-Random -Maximum 999999))@example.com"
$r = Hit 'POST' '/auth/register' @{ email = $email; password = 'Passw0rd!123'; displayName = '李同学' } $null
Check 'POST /auth/register' $r.code 200
$reg = $r.body | ConvertFrom-Json
$token = $reg.data.accessToken
$refresh = $reg.data.refreshToken
if ($token) { Write-Host "  token 长度 $($token.Length)，refresh 长度 $($refresh.Length)" }

$r = Hit 'POST' '/auth/register' @{ email = $email; password = 'Passw0rd!123'; displayName = '重复' } $null
Check '重复注册应 400' $r.code 400

$r = Hit 'POST' '/auth/login' @{ email = $email; password = 'Passw0rd!123' } $null
Check 'POST /auth/login' $r.code 200

$r = Hit 'POST' '/auth/login' @{ email = $email; password = 'wrong' } $null
Check '错误密码应 401' $r.code 401

$r = Hit 'GET' '/auth/me' $null $token
Check 'GET /auth/me（带 token）' $r.code 200
Write-Host "  $($r.body)"

$r = Hit 'PUT' '/auth/me' @{ displayName = '李同学（已更新）' } $token
Check 'PUT /auth/me（更新个人资料）' $r.code 200

$r = Hit 'POST' '/feedback' @{ category = 'suggestion'; content = '希望增加更多校园活动模板'; contact = 'e2e@example.com' } $token
Check 'POST /feedback（用户反馈）' $r.code 200

$r = Hit 'GET' '/auth/me' $null $null
Check 'GET /auth/me（无 token）应 401' $r.code 401

$r = Hit 'POST' '/auth/refresh' @{ refreshToken = $refresh } $null
Check 'POST /auth/refresh' $r.code 200
$newToken = ($r.body | ConvertFrom-Json).data.accessToken

$r = Hit 'POST' '/auth/refresh' @{ refreshToken = $refresh } $null
Check '旧 refresh 应失效 401' $r.code 401

Write-Host "`n===== 3. 模板 =====" -ForegroundColor Cyan
$token = $newToken
$r = Hit 'GET' '/templates' $null $token
Check 'GET /templates' $r.code 200
$tpls = ($r.body | ConvertFrom-Json).data
Write-Host "  模板数量: $($tpls.Count)"
$tplId = $tpls[0].id

Write-Host "`n===== 4. 项目 CRUD =====" -ForegroundColor Cyan
$r = Hit 'POST' '/projects' @{ title = '社团招新 2026'; description = 'E2E 测试项目'; templateId = $tplId } $token
Check 'POST /projects' $r.code 200
$proj = ($r.body | ConvertFrom-Json).data
$pid2 = $proj.id
Write-Host "  projectId=$pid2 status=$($proj.status)"

$r = Hit 'GET' "/projects/$pid2" $null $token
Check 'GET /projects/{id}' $r.code 200

$r = Hit 'GET' '/projects?q=招新&page=1&pageSize=10' $null $token
Check 'GET /projects?q=招新' $r.code 200
Write-Host "  $($r.body)"

Write-Host "`n===== 5. 草稿乐观锁 =====" -ForegroundColor Cyan
$doc = @{
    title = '社团招新 2026'
    theme = @{ primary = '#3b82f6'; accent = '#f59e0b'; bg = '#ffffff'; text = '#111827'; radius = 12; fontScale = 1; spacing = 1; buttonStyle = 'solid'; font = 'sans' }
    pages = @(
        @{ id = 'p1'; name = '首页'; role = 'home'; bg = '#ffffff'; blocks = @(
                @{ id = 'b1'; type = 'banner'; props = @{ title = 'XX 社团招新'; subtitle = '欢迎加入' }; hidden = $false },
                @{ id = 'b2'; type = 'form'; props = @{ fields = @(
                            @{ id = 'f1'; label = '姓名'; type = 'text'; required = $true },
                            @{ id = 'f2'; label = '学号'; type = 'text'; required = $true }
                        ) }; hidden = $false }
            ) }
    )
}
$r = Hit 'PUT' "/projects/$pid2/draft" @{ document = $doc; baseRevision = 0 } $token
Check 'PUT /draft（baseRevision=0）' $r.code 200
$draft = ($r.body | ConvertFrom-Json).data
$rev = $draft.revision
Write-Host "  新 revision=$rev"

$r = Hit 'PUT' "/projects/$pid2/draft" @{ document = $doc; baseRevision = 0 } $token
Check 'PUT /draft（旧 baseRevision=0）应 409' $r.code 409

Write-Host "`n===== 6. 校验与发布 =====" -ForegroundColor Cyan
$badDoc = @{ title = '坏文档'; pages = 'not-an-array' }
$r = Hit 'PUT' "/projects/$pid2/draft" @{ document = $badDoc; baseRevision = $rev } $token
Check '非法 PageDocument 应 422' $r.code 422
Write-Host "  $($r.body)"

$slug = 'club-' + (Get-Random -Minimum 100000 -Maximum 999999)
$r = Hit 'POST' "/projects/$pid2/publish" @{ settings = @{}; slug = $slug } $token
Check 'POST /projects/{id}/publish' $r.code 200
Write-Host "  $($r.body)"
if ($r.code -ne 200) { $slug = $null }

if ($slug) {
    # 把同样的 slug 手动拨到另一个项目上，验证唯一约束翻译成 409 而不是 500
    $r = Hit 'POST' '/projects' @{ title = '撞车项目'; templateId = $tplId } $token
    $pid3 = ($r.body | ConvertFrom-Json).data.id
    $r = Hit 'PUT' "/projects/$pid3/draft" @{ baseRevision = 0; document = $doc } $token
    $r = Hit 'POST' "/projects/$pid3/publish" @{ settings = @{}; slug = $slug } $token
    Check '重复 slug 应 409' $r.code 409
}

Write-Host "`n===== 7. 公开页与提交 =====" -ForegroundColor Cyan
if ($slug) {
    $r = Hit 'GET' "/public/activities/$slug" $null $null
    Check 'GET /public/activities/{slug}（匿名）' $r.code 200
    $pub = ($r.body | ConvertFrom-Json).data
    Write-Host "  status=$($pub.status) title=$($pub.title)"
}

$r = Hit 'GET' '/public/activities/nonexistent-slug' $null $null
Check '不存在的 slug 应 404' $r.code 404

# 前端提交时 answers 的 key 是 "<blockId>:<fieldId>"（见 web/src/views/PublicActivityView.vue:65）
$ans = @{ 'b2:f1' = '张三'; 'b2:f2' = '2026001' }
$idem = 'idem-' + [guid]::NewGuid().ToString()
$firstId = $null
if ($slug) {
    $r = Hit 'POST' "/public/activities/$slug/events" @{ eventType = 'form_started'; pageId = 'p1'; metadata = @{ source = 'e2e' } } $null
    Check 'POST 表单开始事件' $r.code 200

    $r = Hit 'POST' "/public/activities/$slug/submissions" @{ pageId = 'p1'; formComponentId = 'b2'; answers = $ans; idempotencyKey = $idem } $null
    Check 'POST 提交（首次）' $r.code 200
    $firstId = ($r.body | ConvertFrom-Json).data.submissionId
    Write-Host "  submissionId=$firstId"

    $r = Hit 'POST' "/public/activities/$slug/submissions" @{ pageId = 'p1'; formComponentId = 'b2'; answers = $ans; idempotencyKey = $idem } $null
    Check 'POST 提交（同 key 幂等）' $r.code 200
    $secondId = ($r.body | ConvertFrom-Json).data.submissionId
    if ($firstId -and $firstId -eq $secondId) {
        Write-Host "  [PASS] 幂等命中：两次返回同一 submissionId" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 幂等失效：$firstId vs $secondId" -ForegroundColor Red
        $fail++
    }

    $r = Hit 'POST' "/public/activities/$slug/submissions" @{ pageId = 'p1'; formComponentId = 'b2'; answers = @{ 'b2:f1' = '李四' }; idempotencyKey = ('idem-' + [guid]::NewGuid().ToString()) } $null
    Check '缺少必填项应 422' $r.code 422
    Write-Host "  $($r.body)"
}

Write-Host "`n===== 8. 提交列表与统计 =====" -ForegroundColor Cyan
$r = Hit 'GET' "/projects/$pid2/submissions?page=1&pageSize=10" $null $token
Check 'GET 提交列表' $r.code 200
Write-Host "  $($r.body.Substring(0, [Math]::Min(300, $r.body.Length)))"

$r = Hit 'GET' "/projects/$pid2/analytics/summary" $null $token
Check 'GET analytics/summary' $r.code 200
Write-Host "  $($r.body)"
$sumMain = ($r.body | ConvertFrom-Json).data
if ($sumMain.formStarted -ge 1 -and $sumMain.conversionRate -gt 0 -and $sumMain.publishedPages -ge 1) {
    Write-Host "  [PASS] 浏览页面数、form_started 与转化率已产生真实数据" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] 统计埋点仍不完整" -ForegroundColor Red
    $fail++
}

Write-Host "`n===== 9. 导出 =====" -ForegroundColor Cyan
$r = Hit 'POST' "/projects/$pid2/exports" @{} $token
Check 'POST exports' $r.code 200
$exp = ($r.body | ConvertFrom-Json).data
Write-Host "  $($r.body)"

$r = Hit 'GET' "/projects/$pid2/exports" $null $token
Check 'GET 导出历史' $r.code 200
$exports = ($r.body | ConvertFrom-Json).data
if ($exports | Where-Object { $_.id -eq $exp.id }) {
    Write-Host "  [PASS] 新导出出现在历史列表" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] 新导出没有出现在历史列表" -ForegroundColor Red
    $fail++
}

$r = Hit 'GET' "/exports/$($exp.id)/download" $null $token
Check 'GET 导出下载' $r.code 200
Write-Host "  CSV 前 120 字符: $($r.body.Substring(0,[Math]::Min(120,$r.body.Length)))"

Write-Host "`n===== 10. 越权检查 =====" -ForegroundColor Cyan
$email2 = "other$((Get-Random -Maximum 999999))@example.com"
$r = Hit 'POST' '/auth/register' @{ email = $email2; password = 'Passw0rd!123'; displayName = '王同学' } $null
$token2 = ($r.body | ConvertFrom-Json).data.accessToken
$r = Hit 'GET' "/projects/$pid2" $null $token2
Check '别人读我的项目应 403/404' $r.code 403
if ($exp.id) {
    $r = Hit 'GET' "/exports/$($exp.id)" $null $token2
    Check '别人读取我的导出任务应 403' $r.code 403
}

Write-Host "`n===== 11. AI 降级 =====" -ForegroundColor Cyan
$r = Hit 'POST' '/ai/chat' @{ message = '你好' } $token
Check 'POST /ai/chat（agent 未启动应 503）' $r.code 503
Write-Host "  $($r.body)"

$r = Hit 'GET' '/ai/agent/health' $null $token
Check 'GET /ai/agent/health' $r.code 200
Write-Host "  $($r.body)"

$r = Hit 'POST' '/ai/poster/extract' @{ dataUrl = 'data:image/png;base64,iVBORw0KGgo=' } $token
Check 'POST /ai/poster/extract（agent 未启动应 503）' $r.code 503

Write-Host "`n===== 12. 登出 =====" -ForegroundColor Cyan
$r = Hit 'POST' '/auth/logout' @{} $token
Check 'POST /auth/logout' $r.code 200
$r = Hit 'GET' '/auth/me' $null $token
Check '登出后 /auth/me 应 401' $r.code 401

# ============================================================================
# 以下是「补全后端功能」后新增的验收：
#   13. 封面字段 14. 模板种子 15. 公开活动列表 16. 草稿 PATCH 17. 统计明细 18. 文件绝对 URL
# ============================================================================

# 需要重新拿一个新 token（上面刚登出）
$email3 = "e2e$((Get-Random -Maximum 999999))@example.com"
$r = Hit 'POST' '/auth/register' @{ email = $email3; password = 'Passw0rd!123'; displayName = '赵同学' } $null
$token = ($r.body | ConvertFrom-Json).data.accessToken

Write-Host "`n===== 13. 封面字段 =====" -ForegroundColor Cyan
$r = Hit 'POST' '/projects' @{
    title        = '封面字段测试'
    templateId   = $tplId
    activityType = '社团招新'
    coverTone    = 'vote'
    coverCode    = 'PHOTO / 26'
    coverTitle   = '寻找校园里的下一帧光'
} $token
Check 'POST /projects（带封面四项）' $r.code 200
$p3 = ($r.body | ConvertFrom-Json).data
$pidF = $p3.id
Check 'activityType 回显' $p3.activityType '社团招新'
Check 'coverTone 回显' $p3.coverTone 'vote'
Check 'coverCode 回显' $p3.coverCode 'PHOTO / 26'
Check 'coverTitle 回显' $p3.coverTitle '寻找校园里的下一帧光'

# 不传封面时要能自动推导，不能是 null
$r = Hit 'POST' '/projects' @{ title = '无封面参数项目'; templateId = $tplId } $token
$p4 = ($r.body | ConvertFrom-Json).data
$autoTone = $p4.coverTone
$autoCode = $p4.coverCode
$autoTitle = $p4.coverTitle
if ($autoTone -and $autoCode -and $autoTitle) {
    Write-Host ("  [PASS] 未传封面时自动推导：tone={0} code={1} title={2}" -f $autoTone, $autoCode, $autoTitle) -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] 未传封面时推导失败，出现了 null" -ForegroundColor Red
    $fail++
}

# 更新封面
$r = Hit 'PUT' "/projects/$pidF" @{ coverTone = 'music'; coverCode = 'MUSIC / 26' } $token
Check 'PUT /projects/{id}（改封面）' $r.code 200
$p3u = ($r.body | ConvertFrom-Json).data
Check '封面改后 coverTone' $p3u.coverTone 'music'
Check '封面改后标题不变' $p3u.coverTitle '寻找校园里的下一帧光'

# 列表里必须带封面字段
$r = Hit 'GET' '/projects?page=1&pageSize=5' $null $token
Check 'GET /projects 带封面' $r.code 200
$firstProj = ($r.body | ConvertFrom-Json).data[0]
if ($firstProj.PSObject.Properties.Name -contains 'coverTone') {
    Write-Host "  [PASS] 列表返回体包含 coverTone / coverCode / coverTitle / activityType" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] 列表返回体缺少封面字段" -ForegroundColor Red
    $fail++
}

# 按活动类型过滤
$r = Hit 'GET' ('/projects?activityType=' + [uri]::EscapeDataString('社团招新') + '&page=1&pageSize=5') $null $token
Check 'GET /projects?activityType=（按类型筛选）' $r.code 200

Write-Host "`n===== 14. 模板种子 =====" -ForegroundColor Cyan
$r = Hit 'GET' '/templates' $null $token
Check 'GET /templates' $r.code 200
$allTpl = ($r.body | ConvertFrom-Json).data
Write-Host "  模板总数: $($allTpl.Count)"
if ($allTpl.Count -ge 7) {
    Write-Host "  [PASS] 模板数量 >= 7" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] 模板数量 $($allTpl.Count) < 7，004_more_templates.sql 可能没执行" -ForegroundColor Red
    $fail++
}
$categories = $allTpl | ForEach-Object { $_.category } | Select-Object -Unique
Write-Host ("  覆盖分类: " + ($categories -join ', '))

$r = Hit 'GET' '/templates?category=vote' $null $token
Check 'GET /templates?category=vote' $r.code 200
$voteTpl = ($r.body | ConvertFrom-Json).data
if ($voteTpl.Count -ge 1) {
    Write-Host "  [PASS] vote 分类模板数 $($voteTpl.Count)" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] vote 分类没有模板" -ForegroundColor Red
    $fail++
}

# 用非 recruitment 模板建项目，验证模板能被正确实例化（不能报 422）
if ($voteTpl.Count -ge 1) {
    $r = Hit 'POST' '/projects' @{ title = '用投票模板建的项目'; templateId = $voteTpl[0].id } $token
    Check 'POST /projects（用 vote 模板）' $r.code 200
    $pVote = ($r.body | ConvertFrom-Json).data
    if ($pVote.draftDocument -and $pVote.draftDocument.pages.Count -ge 1) {
        $blkCount = ($pVote.draftDocument.pages | ForEach-Object { $_.blocks.Count } | Measure-Object -Sum).Sum
        Write-Host ("  [PASS] 模板实例化成功：{0} 页 / {1} 个区块" -f $pVote.draftDocument.pages.Count, $blkCount) -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 模板实例化后 draftDocument 没有 pages" -ForegroundColor Red
        $fail++
    }
    # 模板没有 id，后端要自动补齐，否则发布会被 schema 打回
    $firstPage = $pVote.draftDocument.pages[0]
    $firstBlock = $firstPage.blocks[0]
    if ($firstPage.id -and $firstBlock.id) {
        Write-Host ("  [PASS] 自动补 id：page={0} block={1}" -f $firstPage.id, $firstBlock.id) -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 模板实例化后没补 page.id / block.id" -ForegroundColor Red
        $fail++
    }
}

Write-Host "`n===== 15. 公开活动列表 =====" -ForegroundColor Cyan
$r = Hit 'GET' '/public/activities' $null $null
Check 'GET /public/activities（匿名）' $r.code 200
$pubList = ($r.body | ConvertFrom-Json)
Write-Host "  公开活动总数: $($pubList.pagination.total)"

if ($slug) {
    $found = $pubList.data | Where-Object { $_.slug -eq $slug }
    if ($found) {
        Write-Host ("  [PASS] 刚发布的活动出现在公开列表里：$slug") -ForegroundColor Green
        $pass++
        if ($found.status -eq 'published') {
            Write-Host "  [PASS] 公开列表 status=published" -ForegroundColor Green
            $pass++
        } else {
            Write-Host "  [FAIL] 公开列表 status=$($found.status)，期望 published" -ForegroundColor Red
            $fail++
        }
        if ($found.PSObject.Properties.Name -contains 'coverTone') {
            Write-Host "  [PASS] 公开列表带封面字段" -ForegroundColor Green
            $pass++
        } else {
            Write-Host "  [FAIL] 公开列表缺封面字段" -ForegroundColor Red
            $fail++
        }
        # 绝不能泄露草稿内容
        $leak = $found.PSObject.Properties.Name -contains 'draftDocument'
        if ($leak) {
            Write-Host "  [FAIL] 公开列表泄露了 draftDocument" -ForegroundColor Red
            $fail++
        } else {
            Write-Host "  [PASS] 公开列表不泄露 draftDocument" -ForegroundColor Green
            $pass++
        }
    } else {
        Write-Host "  [FAIL] 刚发布的活动 $slug 没出现在公开列表里" -ForegroundColor Red
        $fail++
    }
}

$r = Hit 'GET' '/public/activities?q=不存在的关键词xyz' $null $null
Check 'GET /public/activities?q=（搜不到也应 200）' $r.code 200

Write-Host "`n===== 16. 草稿 PATCH（局部更新） =====" -ForegroundColor Cyan
$r = Hit 'POST' '/projects' @{ title = 'PATCH 测试项目'; templateId = $tplId } $token
$pidPatch = ($r.body | ConvertFrom-Json).data.id
$r = Hit 'GET' "/projects/$pidPatch/draft" $null $token
Check 'GET /draft（新建项目）' $r.code 200
$d0 = ($r.body | ConvertFrom-Json).data
$rev0 = $d0.revision
$page0 = $d0.document.pages[0]
$pageId0 = $page0.id
$blockId0 = $page0.blocks[0].id
$oldTitle = $page0.name
Write-Host "  revision=$rev0 pageId=$pageId0 blockId=$blockId0 pageName=$oldTitle"

# 只改一个页面的 name
$patchDoc = @{ pages = @(@{ id = $pageId0; name = '改名后的首页' }) }
$r = Hit 'PATCH' "/projects/$pidPatch/draft" @{ baseRevision = $rev0; patch = $patchDoc } $token
Check 'PATCH /draft（改页面名）' $r.code 200
$rev1 = ($r.body | ConvertFrom-Json).data.revision
if ($rev1 -eq ($rev0 + 1)) {
    Write-Host "  [PASS] revision 递增 $rev0 -> $rev1" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] revision 没递增：$rev0 -> $rev1" -ForegroundColor Red
    $fail++
}

$r = Hit 'GET' "/projects/$pidPatch/draft" $null $token
$d1 = ($r.body | ConvertFrom-Json).data
if ($d1.document.pages[0].name -eq '改名后的首页') {
    Write-Host "  [PASS] patch 生效：页面名已改" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] patch 没生效：页面名还是 $($d1.document.pages[0].name)" -ForegroundColor Red
    $fail++
}
if ($d1.document.pages[0].blocks.Count -eq $page0.blocks.Count) {
    Write-Host "  [PASS] 未 patch 的字段（blocks）被保留" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] patch 误删了 blocks" -ForegroundColor Red
    $fail++
}

# 旧 revision 再来一次应该 409
$r = Hit 'PATCH' "/projects/$pidPatch/draft" @{ baseRevision = $rev0; patch = @{ title = '过期写法' } } $token
Check 'PATCH /draft（旧 baseRevision）应 409' $r.code 409

# patch 不是对象应 422
$r = Hit 'PATCH' "/projects/$pidPatch/draft" @{ baseRevision = $rev1; patch = @(1, 2, 3) } $token
Check 'PATCH /draft（patch 不是对象）应 422' $r.code 422

# patch 把文档改坏，必须被 schema 拦住
$r = Hit 'PATCH' "/projects/$pidPatch/draft" @{ baseRevision = $rev1; patch = @{ pages = 'not-an-array' } } $token
Check 'PATCH /draft（改坏文档）应 422' $r.code 422

Write-Host "`n===== 17. 统计明细 =====" -ForegroundColor Cyan
# 注意：上面 13 组换了新用户，$pid2 属于旧用户；这里要用当前 token 自己的项目
# 先给 $pidF 制造一次浏览 + 一次提交，否则 byDay / byEventType 天然是空的
$slugF = 'pick-' + (Get-Random -Minimum 100000 -Maximum 999999)
$r = Hit 'GET' "/projects/$pidF/draft" $null $token
$dF = ($r.body | ConvertFrom-Json).data.document
$pgF = $dF.pages[0].id
$blkF = $dF.pages[0].blocks[0].id
$r = Hit 'POST' "/projects/$pidF/publish" @{ settings = @{}; slug = $slugF } $token
Check 'POST publish（封面项目）' $r.code 200
$r = Hit 'GET' "/public/activities/$slugF" $null $null
Check 'GET 公开页（制造浏览事件）' $r.code 200

$r = Hit 'GET' "/projects/$pidF/analytics/summary" $null $token
Check 'GET analytics/summary' $r.code 200
$sum = ($r.body | ConvertFrom-Json).data
Write-Host "  submissions=$($sum.submissions) views=$($sum.pageViews) formStarted=$($sum.formStarted) conversion=$($sum.conversionRate)"
if ($null -ne $sum.byEventType) {
    Write-Host ("  byEventType: " + (($sum.byEventType.PSObject.Properties | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ', '))
    Write-Host "  [PASS] summary 带 byEventType" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] summary 缺 byEventType" -ForegroundColor Red
    $fail++
}
if ($sum.byDay.Count -gt 0) {
    Write-Host ("  [PASS] byDay 有数据：" + (($sum.byDay | ForEach-Object { "$($_.date)(views=$($_.views),subs=$($_.submissions))" }) -join '; ')) -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] byDay 仍是空的（浏览埋点没聚合进来）" -ForegroundColor Red
    $fail++
}

$r = Hit 'GET' "/projects/$pidF/analytics/events?page=1&pageSize=10" $null $token
Check 'GET analytics/events' $r.code 200
$ev = ($r.body | ConvertFrom-Json)
Write-Host "  事件总数: $($ev.pagination.total)"

$r = Hit 'GET' "/projects/$pidF/analytics/events?eventType=public_page_view" $null $token
Check 'GET analytics/events?eventType=' $r.code 200

Write-Host "`n===== 18. 文件绝对 URL =====" -ForegroundColor Cyan
# 造一张 1x1 PNG 直接上传
$pngBytes = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==')
$tmpPng = Join-Path $env:TEMP ('e2e-' + [guid]::NewGuid().ToString() + '.png')
[System.IO.File]::WriteAllBytes($tmpPng, $pngBytes)
try {
    $up = UploadFile $tmpPng 'file' $token
    Check 'POST /assets（上传 PNG）' $up.code 200
    $asset = ($up.body | ConvertFrom-Json).data
    Write-Host "  url=$($asset.url)"
    if ($asset.url -match '^https?://') {
        Write-Host "  [PASS] 上传返回绝对 URL" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 上传返回的是相对路径，前端跨端口会 404" -ForegroundColor Red
        $fail++
    }
    # 绝对 URL 必须真能取到
    if ($asset.url -match '^https?://') {
        $dl = Invoke-WebRequest -Uri $asset.url -UseBasicParsing -TimeoutSec 20
        Check 'GET 上传文件（用返回的绝对 URL）' ([int]$dl.StatusCode) 200
    }
} catch {
    Write-Host "  [FAIL] 上传失败：$($_.Exception.Message)" -ForegroundColor Red
    $fail++
} finally {
    if (Test-Path $tmpPng) { Remove-Item $tmpPng -Force }
}

# ============================================================================
# 19. AI Proposal 链路（队友反馈：agent 返回缺 id 的稿子时，应用会 422）
#
# 这一段不需要真的 agent 在线：它直接测「后端拿到畸形候选稿之后的行为」。
#   - agent 不在线 → /ai/generate 必须 503（降级），不能 500
#   - 手工往库里塞一条缺 id 的「遗留提案」→ apply 时后端要能自动补齐再落库
# 真正跑通「模型长什么样」的验证见 docs/frontend-backend-handoff.md。
# ============================================================================
Write-Host "`n===== 19. AI Proposal 链路 =====" -ForegroundColor Cyan

$r = Hit 'POST' '/projects' @{ title = 'AI 链路验收项目'; templateId = $tplId } $token
Check 'POST /projects（AI 链路用）' $r.code 200
$aiProj = ($r.body | ConvertFrom-Json).data
$pidAi = $aiProj.id
$revAi = [int]$aiProj.draftRevision

$r = Hit 'POST' "/ai/projects/$pidAi/generate" @{
    prompt           = '帮我做一个摄影社秋季招新页面'
    baseRevision     = $revAi
    clientRequestId  = "e2e-$(Get-Random)"
} $token
# agent 未启动时必须 503（能用模板兜底），不能是 500 把前端打挂
if ($r.code -eq 200) {
    Write-Host "  [INFO] agent 在线，生成成功，继续验证提案内容" -ForegroundColor Yellow
    $prop = ($r.body | ConvertFrom-Json).data
    $doc = $prop.candidateDocument
    $allHaveId = $true
    foreach ($pg in $doc.pages) {
        if (-not $pg.id) { $allHaveId = $false }
        foreach ($b in $pg.blocks) { if (-not $b.id) { $allHaveId = $false } }
    }
    if ($allHaveId) {
        Write-Host "  [PASS] 候选稿的每个 page/block 都有 id（应用时不会 422）" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 候选稿里仍有缺 id 的 page/block" -ForegroundColor Red
        $fail++
    }
    if ($prop.summary) {
        Write-Host "  [PASS] summary 说明变更：$($prop.summary)" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] summary 为空，前端没东西可提示" -ForegroundColor Red
        $fail++
    }
    if ($null -ne $prop.operations -and $prop.operations.Count -gt 0) {
        Write-Host "  [PASS] operations 带撤销方案（op=$($prop.operations[0].op)）" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] operations 为空，前端无法做撤销" -ForegroundColor Red
        $fail++
    }
} elseif ($r.code -eq 503) {
    Write-Host "  [PASS] agent 不在线时 /ai/generate 返回 503（前端可退到本地模板）" -ForegroundColor Green
    $pass++
} else {
    Write-Host "  [FAIL] /ai/generate 期望 503 或 200，实际 $($r.code)：$($r.body)" -ForegroundColor Red
    $fail++
}

# 直接构造一条「老数据」：候选稿是缺 id 的 DocPlan（正是队友截图里说的 422 场景）
#
# 注意：SQL 必须写进临时文件再 source，不能走 -e "..."。
# 从 PowerShell 传 -e 时，内层单引号会被吃掉，CAST('{"title":"老数据缺 id",...}' AS JSON)
# 变成 CAST('' AS JSON)，MySQL 报 ERROR 3141，被 2>$null 一吞就变成「提案不存在 404」。
$mysqlExe = 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (Test-Path $mysqlExe) {
    function Invoke-MySql($sqlText) {
        $f = Join-Path $env:TEMP ("e2e_" + [guid]::NewGuid().ToString('N') + ".sql")
        [System.IO.File]::WriteAllText($f, $sqlText, (New-Object System.Text.UTF8Encoding($false)))
        # 2>&1 会把 stderr 并进管道，所以函数返回的是「输出对象」而不是退出码。
        # 必须先用变量接住输出、再单独取 $LASTEXITCODE 返回，否则调用方拿到的是 stderr 文本。
        $out = & $mysqlExe --default-character-set=utf8mb4 -ucampus_app -pCampus_2026 campus_diy -e "source $f" 2>&1
        $code = $LASTEXITCODE
        Remove-Item $f -ErrorAction SilentlyContinue
        if ($code -ne 0) { Write-Host ("    mysql: " + ($out -join ' ')) -ForegroundColor DarkGray }
        return $code
    }

    $realJob = (& $mysqlExe -ucampus_app -pCampus_2026 campus_diy -N -B -e "SELECT id FROM ai_jobs WHERE project_id='$pidAi' ORDER BY created_at DESC LIMIT 1;" 2>$null)
    $realJob = ("$realJob").Trim()
    if (-not $realJob) {
        # agent 离线时没有 job，伪造一个满足外键的
        $realJob = [guid]::NewGuid().ToString()
        $insJob = "INSERT INTO ai_jobs (id, project_id, user_id, kind, status, input_payload, started_at) " +
                  "SELECT '$realJob', '$pidAi', owner_id, 'generate_from_prompt', 'succeeded', JSON_OBJECT(), NOW() FROM projects WHERE id='$pidAi';"
        $rc = Invoke-MySql $insJob
        if ($rc -ne 0) { Write-Host "  [WARN] 伪造 ai_jobs 失败 rc=$rc" -ForegroundColor Yellow }
    }
    $dirtyId = [guid]::NewGuid().ToString()
    $candidate = '{"title":"老数据缺 id","pages":[{"role":"home","blocks":[{"type":"banner","props":{"title":"没有 id 的横幅"}},{"type":"text","props":{"text":"这一条正是会触发 422 的形状"}}]}]}'
    $insProp = "INSERT INTO ai_proposals (id, job_id, project_id, base_revision, target, summary, candidate_document, operations, rationale, risk, status) " +
               "VALUES ('$dirtyId','$realJob','$pidAi',$revAi,JSON_OBJECT(),'遗留脏提案',CAST('$candidate' AS JSON),JSON_ARRAY(),JSON_ARRAY(),'medium','proposed');"
    $rc = Invoke-MySql $insProp
    if ($rc -ne 0) { Write-Host "  [WARN] 插入遗留脏提案失败 rc=$rc（后面的用例会跟着 404）" -ForegroundColor Yellow }

    $r = Hit 'GET' "/ai/proposals/$dirtyId" $null $token
    Check 'GET /ai/proposals/{id}（遗留脏提案可读）' $r.code 200

    # 应用遗留脏提案：后端应自动补齐 id 后成功，而不是 422
    $r = Hit 'POST' "/ai/proposals/$dirtyId/apply" @{ baseRevision = $revAi } $token
    if ($r.code -eq 200) {
        $applied = ($r.body | ConvertFrom-Json).data
        $pg0 = $applied.document.pages[0]
        $hasIds = ($pg0.id -and $pg0.blocks[0].id -and $pg0.blocks[1].id)
        if ($hasIds) {
            Write-Host "  [PASS] 脏提案被自动补齐 id 后应用成功（revision=$($applied.revision)）" -ForegroundColor Green
            $pass++
        } else {
            Write-Host "  [FAIL] 应用成功但仍缺 id" -ForegroundColor Red
            $fail++
        }
        $revAi = [int]$applied.revision
    } else {
        Write-Host "  [FAIL] 脏提案应用失败：期望 200，实际 $($r.code)：$($r.body)" -ForegroundColor Red
        $fail++
    }

    # 已应用过的提案再应用一次 → 409
    $r = Hit 'POST' "/ai/proposals/$dirtyId/apply" @{ baseRevision = $revAi } $token
    Check 'POST apply（重复应用）应 409' $r.code 409

    # 过期 baseRevision → 409
    $r = Hit 'GET' "/projects/$pidAi/draft" $null $token
    $curRev = [int]($r.body | ConvertFrom-Json).data.revision
    $staleId = [guid]::NewGuid().ToString()
    $insStale = "INSERT INTO ai_proposals (id, job_id, project_id, base_revision, target, summary, candidate_document, operations, rationale, risk, status) " +
                "VALUES ('$staleId','$realJob','$pidAi',$curRev,JSON_OBJECT(),'过期提案',CAST('$candidate' AS JSON),JSON_ARRAY(),JSON_ARRAY(),'medium','proposed');"
    $rc = Invoke-MySql $insStale
    if ($rc -ne 0) { Write-Host "  [WARN] 插入过期提案失败 rc=$rc" -ForegroundColor Yellow }
    $r = Hit 'POST' "/ai/proposals/$staleId/apply" @{ baseRevision = ($curRev - 1) } $token
    Check 'POST apply（baseRevision 过期）应 409' $r.code 409

    # 经过 repair 的文档，用户写的内容和颜色不能被改坏
    $r = Hit 'GET' "/projects/$pidAi/draft" $null $token
    $finalDoc = ($r.body | ConvertFrom-Json).data.document
    $b0 = $finalDoc.pages[0].blocks[0]
    $b1 = $finalDoc.pages[0].blocks[1]
    $okTitle = ($b0.props.title -eq '没有 id 的横幅')
    $okText = ($b1.props.text -eq '这一条正是会触发 422 的形状')
    $theme = $finalDoc.theme
    $hasTheme = ($theme.primary -and $theme.bg -and $theme.text -and $theme.accent)
    if ($okTitle -and $okText -and $hasTheme) {
        Write-Host "  [PASS] 自动补齐不丢内容：标题/正文原样保留，theme 四色齐全" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  [FAIL] 补齐后内容被改坏：title=$okTitle text=$okText theme=$hasTheme" -ForegroundColor Red
        $fail++
    }
} else {
    Write-Host "  [SKIP] 没找到 mysql.exe，跳过「遗留脏提案」用例（不影响其他用例）" -ForegroundColor Yellow
}

Write-Host ("`n================ 汇总：PASS {0} / FAIL {1} ================" -f $pass, $fail) -ForegroundColor Yellow
if ($fail -eq 0) { Write-Host "全部通过" -ForegroundColor Green } else { Write-Host "有失败项，见上面 [FAIL]" -ForegroundColor Red }
exit $(if ($fail -eq 0) { 0 } else { 1 })
