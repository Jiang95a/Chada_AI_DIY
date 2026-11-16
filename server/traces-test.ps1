$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080/api/v1"
$pass = 0; $fail = 0
function Check($name, $cond, $detail) {
    if ($cond) { $script:pass++; Write-Host "[PASS] $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "[FAIL] $name -> $detail" -ForegroundColor Red }
}
function Call($method, $path, $token, $body) {
    $h = @{}
    if ($token) { $h["Authorization"] = "Bearer $token" }
    $p = @{ Method = $method; Uri = "$base$path"; Headers = $h; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($body -ne $null) {
        $p["Body"] = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20 -Compress))
        $p["ContentType"] = "application/json; charset=utf-8"
    }
    try {
        $r = Invoke-WebRequest @p
        $txt = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        try { $j = $txt | ConvertFrom-Json } catch { $j = $txt }
        return @{ code = [int]$r.StatusCode; body = $j; raw = $txt }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
            $txt = $sr.ReadToEnd(); $code = [int]$resp.StatusCode
            try { $j = $txt | ConvertFrom-Json } catch { $j = $txt }
            return @{ code = $code; body = $j; raw = $txt }
        }
        return @{ code = -1; body = $_.Exception.Message; raw = $_.Exception.Message }
    }
}

Write-Host "`n===== 0. 准备：注册两个用户 + 找一个已发布活动 =====" -ForegroundColor Cyan
$emailA = "tr$(Get-Random)@campus.local"
$regA = Call "POST" "/auth/register" $null @{ email = $emailA; password = "Passw0rd!2026"; displayName = "轨迹A" }
$tokenA = $regA.body.data.accessToken
Check "注册用户A" ($regA.code -eq 200 -or $regA.code -eq 201) $regA.code
$userIdA = $regA.body.data.user.id

$emailB = "tr$(Get-Random)@campus.local"
$regB = Call "POST" "/auth/register" $null @{ email = $emailB; password = "Passw0rd!2026"; displayName = "轨迹B" }
$tokenB = $regB.body.data.accessToken
Check "注册用户B" ($regB.code -eq 200 -or $regB.code -eq 201) $regB.code

# 取一个已发布活动（公开页用 slug 访问）
$probe = Call "GET" "/templates" $tokenA
$tplId = $probe.body.data[0].id
$proj = Call "POST" "/projects" $tokenA @{ title = "轨迹测试活动"; templateId = $tplId }
$projId = $proj.body.data.id
$slug = $proj.body.data.publicSlug
if (-not $slug) {
    $pub = Call "POST" "/projects/$projId/publish" $tokenA @{ baseRevision = $proj.body.data.revision }
    $slug = $pub.body.data.publicSlug
}
Write-Host "  活动 slug = $slug"
Check "创建并拿到活动 slug" (-not [string]::IsNullOrWhiteSpace($slug)) "slug 为空"

Write-Host "`n===== 1. 鉴权 =====" -ForegroundColor Cyan
$anonGet = Call "GET" "/traces?activityId=$slug" $null
Check "未登录 GET /traces -> 401" ($anonGet.code -eq 401) $anonGet.code
$anonPut = Call "PUT" "/traces/$slug" $null @{ summary = "x" }
Check "未登录 PUT /traces -> 401" ($anonPut.code -eq 401) $anonPut.code
$anonList = Call "GET" "/traces/list" $null
Check "未登录 GET /traces/list -> 401" ($anonList.code -eq 401) $anonList.code

Write-Host "`n===== 2. 首次读取返回 data:null =====" -ForegroundColor Cyan
$g0 = Call "GET" "/traces?activityId=$slug" $tokenA
Check "GET 不存在的轨迹 200" ($g0.code -eq 200) $g0.code
Check "  data 为 null（前端据此判定远端无记录）" ($null -eq $g0.body.data) "data=$($g0.body.data)"

Write-Host "`n===== 3. 保存与回读 =====" -ForegroundColor Cyan
$now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$body = @{
    userId = "伪造的别的用户"
    domain = "mental"
    semester = "2026-秋"
    summary = "这学期想先把作息调回来"
    openQuestion = "到底要不要继续考研"
    change = "开始早睡了"
    promise = "每周三跑步"
    tags = @(
        @{ content = "作息"; source = "user"; createdAt = $now },
        @{ content = "考研"; source = "auto"; createdAt = $now }
    )
    sessionContext = @{ currentHint = "先照顾作息"; availablePaths = @("写便签","看看别人的回响") }
    notes = @(
        @{ activityId = $slug; text = "今天有点累"; mood = "tired"; at = $now }
    )
    updatedAt = $now
}
$put = Call "PUT" "/traces/$slug" $tokenA $body
Check "PUT /traces/{slug} 200" ($put.code -eq 200) $put.code
Check "  userId 被强制为本人（忽略请求体里的伪造值）" ($put.body.data.userId -eq $userIdA) "got=$($put.body.data.userId) expect=$userIdA"
Check "  activityId 回传 slug 而非 UUID" ($put.body.data.activityId -eq $slug) $put.body.data.activityId
Check "  summary 正确" ($put.body.data.summary -eq "这学期想先把作息调回来") $put.body.data.summary
Check "  semester 中文正确" ($put.body.data.semester -eq "2026-秋") $put.body.data.semester
Check "  openQuestion 正确" ($put.body.data.openQuestion -eq "到底要不要继续考研") $put.body.data.openQuestion
Check "  change 正确" ($put.body.data.change -eq "开始早睡了") $put.body.data.change
Check "  promise 正确" ($put.body.data.promise -eq "每周三跑步") $put.body.data.promise
Check "  tags 保留 2 条" (@($put.body.data.tags).Count -eq 2) (@($put.body.data.tags).Count)
Check "  tags 中文与 source 原样保留" ($put.body.data.tags[0].content -eq "作息" -and $put.body.data.tags[0].source -eq "user") $put.body.data.tags[0].content
Check "  sessionContext 原样保留" (@($put.body.data.sessionContext.availablePaths).Count -eq 2) (@($put.body.data.sessionContext.availablePaths).Count)
Check "  notes 私有副本保留" (@($put.body.data.notes).Count -eq 1) (@($put.body.data.notes).Count)
Check "  updatedAt 原样回传（不被服务端时钟覆盖）" ($put.body.data.updatedAt -eq $now) "got=$($put.body.data.updatedAt) expect=$now"

$g1 = Call "GET" "/traces?activityId=$slug" $tokenA
Check "回读 GET 一致" ($g1.body.data.summary -eq "这学期想先把作息调回来") $g1.body.data.summary
Check "  回读 tags 仍是 2 条" (@($g1.body.data.tags).Count -eq 2) (@($g1.body.data.tags).Count)
Check "  回读 updatedAt 一致" ($g1.body.data.updatedAt -eq $now) $g1.body.data.updatedAt

Write-Host "`n===== 4. 整条覆盖（不是增量合并）=====" -ForegroundColor Cyan
$now2 = $now + 5000
$body2 = @{
    domain = "mental"
    summary = "改成只留一句"
    tags = @(@{ content = "作息"; source = "user"; createdAt = $now2 })
    sessionContext = @{ currentHint = ""; availablePaths = @() }
    notes = @()
    updatedAt = $now2
}
$put2 = Call "PUT" "/traces/$slug" $tokenA $body2
Check "第二次 PUT 200" ($put2.code -eq 200) $put2.code
Check "  tags 被覆盖成 1 条（删掉的标签不会复活）" (@($put2.body.data.tags).Count -eq 1) (@($put2.body.data.tags).Count)
Check "  openQuestion 被清空（未传即覆盖为空）" ($null -eq $put2.body.data.openQuestion -or $put2.body.data.openQuestion -eq "") "got=$($put2.body.data.openQuestion)"
Check "  notes 被清空" (@($put2.body.data.notes).Count -eq 0) (@($put2.body.data.notes).Count)
Check "  updatedAt 更新为新的值" ($put2.body.data.updatedAt -eq $now2) $put2.body.data.updatedAt

Write-Host "`n===== 5. 隐私边界：读不到别人的轨迹 =====" -ForegroundColor Cyan
$gB = Call "GET" "/traces?activityId=$slug" $tokenB
Check "用户B 读同一活动返回 null（隔离）" ($null -eq $gB.body.data) "data=$($gB.body.data)"
$putB = Call "PUT" "/traces/$slug" $tokenB @{ summary = "B 自己的"; tags = @(); updatedAt = $now2 }
Check "用户B 写入 200" ($putB.code -eq 200) $putB.code
Check "  B 的 userId 是 B 自己" ($putB.body.data.userId -eq $regB.body.data.user.id) $putB.body.data.userId
$gA2 = Call "GET" "/traces?activityId=$slug" $tokenA
Check "用户A 的轨迹未被 B 覆盖" ($gA2.body.data.summary -eq "改成只留一句") $gA2.body.data.summary

Write-Host "`n===== 6. 我的轨迹列表 =====" -ForegroundColor Cyan
$list = Call "GET" "/traces/list" $tokenA
Check "GET /traces/list 200" ($list.code -eq 200) $list.code
Check "  含刚写的这条" (@($list.body.data | Where-Object { $_.activityId -eq $slug }).Count -ge 1) "未找到"
$item = @($list.body.data | Where-Object { $_.activityId -eq $slug })[0]
if ($item) {
    Check "  activityTitle 回显活动名" ($item.activityTitle -eq "轨迹测试活动") $item.activityTitle
    Check "  tagCount = 1" ($item.tagCount -eq 1) $item.tagCount
    Check "  summary 一致" ($item.summary -eq "改成只留一句") $item.summary
}
$listB = Call "GET" "/traces/list" $tokenB
Check "用户B 的列表里看不到A的活动条目" (@($listB.body.data | Where-Object { $_.summary -eq "改成只留一句" }).Count -eq 0) "串数据"

Write-Host "`n===== 7. 空轨迹不进列表（与前端同口径）=====" -ForegroundColor Cyan
$proj2 = Call "POST" "/projects" $tokenA @{ title = "空轨迹活动"; templateId = $tplId }
$pid2 = $proj2.body.data.id
$slug2 = $proj2.body.data.publicSlug
if (-not $slug2) {
    $pub2 = Call "POST" "/projects/$pid2/publish" $tokenA @{ baseRevision = $proj2.body.data.revision }
    $slug2 = $pub2.body.data.publicSlug
}
$emptyPut = Call "PUT" "/traces/$slug2" $tokenA @{ summary = ""; tags = @(); sessionContext = @{ currentHint = ""; availablePaths = @() }; updatedAt = $now }
Check "保存一条空轨迹 200" ($emptyPut.code -eq 200) $emptyPut.code
$list2 = Call "GET" "/traces/list" $tokenA
Check "  空轨迹不出现在列表里" (@($list2.body.data | Where-Object { $_.activityId -eq $slug2 }).Count -eq 0) "空轨迹被列出"

Write-Host "`n===== 8. domain 归一 =====" -ForegroundColor Cyan
$careerPut = Call "PUT" "/traces/$slug" $tokenA @{ domain = "career"; summary = "职规方向"; tags = @(); updatedAt = $now }
Check "domain=career 保存成功" ($careerPut.body.data.domain -eq "career") $careerPut.body.data.domain
$badPut = Call "PUT" "/traces/$slug" $tokenA @{ domain = "乱写的"; summary = "职规方向"; tags = @(); updatedAt = $now }
Check "非法 domain 归一到 mental（不报错丢数据）" ($badPut.code -eq 200 -and $badPut.body.data.domain -eq "mental") "$($badPut.code) $($badPut.body.data.domain)"

Write-Host "`n===== 9. 校验与边界 =====" -ForegroundColor Cyan
$longSummary = "长" * 501
$tooLong = Call "PUT" "/traces/$slug" $tokenA @{ summary = $longSummary; tags = @() }
Check "summary 超 500 字 -> 400" ($tooLong.code -eq 400) $tooLong.code
$noSlug = Call "GET" "/traces?activityId=" $tokenA
Check "activityId 为空 -> 400" ($noSlug.code -eq 400) $noSlug.code
$badSlug = Call "GET" "/traces?activityId=not-exist-slug-xyz" $tokenA
Check "不存在的活动 -> 404" ($badSlug.code -eq 404) $badSlug.code
$noTs = Call "PUT" "/traces/$slug" $tokenA @{ summary = "不传时间戳"; tags = @() }
Check "不传 updatedAt 时服务端补当前时间" ($noTs.code -eq 200 -and $noTs.body.data.updatedAt -gt 0) $noTs.body.data.updatedAt

Write-Host "`n===== 10. 删除 =====" -ForegroundColor Cyan
$del = Call "DELETE" "/traces/$slug" $tokenA
Check "DELETE /traces/{slug} 200" ($del.code -eq 200) $del.code
$gAfter = Call "GET" "/traces?activityId=$slug" $tokenA
Check "  删除后 GET 返回 null" ($null -eq $gAfter.body.data) "data=$($gAfter.body.data)"
$gBAfter = Call "GET" "/traces?activityId=$slug" $tokenB
Check "  删 A 的不影响 B" ($gBAfter.body.data.summary -eq "B 自己的") $gBAfter.body.data.summary
$delAgain = Call "DELETE" "/traces/$slug" $tokenA
Check "  重复删除仍 200（幂等）" ($delAgain.code -eq 200) $delAgain.code

Write-Host "`n================ 汇总：PASS $pass / FAIL $fail ================" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
