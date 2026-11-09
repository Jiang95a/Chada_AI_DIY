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
        $p["Body"] = ($body | ConvertTo-Json -Depth 20 -Compress)
        $p["ContentType"] = "application/json; charset=utf-8"
    }
    try {
        $r = Invoke-WebRequest @p
        return @{ code = [int]$r.StatusCode; body = ($r.Content | ConvertFrom-Json) }
    } catch {
        $resp = $_.Exception.Response
        if ($resp) {
            $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
            $txt = $sr.ReadToEnd(); $code = [int]$resp.StatusCode
            try { $j = $txt | ConvertFrom-Json } catch { $j = $txt }
            return @{ code = $code; body = $j }
        }
        return @{ code = -1; body = $_.Exception.Message }
    }
}

Write-Host "`n===== 0. 准备：登录 =====" -ForegroundColor Cyan
$email = "pb$(Get-Random)@campus.local"
$reg = Call "POST" "/auth/register" $null @{ email = $email; password = "Passw0rd!2026"; displayName = "档案测试" }
$regOk = ($reg.code -eq 200) -or ($reg.code -eq 201)
Check "注册新用户" $regOk $reg.code
$token = $reg.body.data.accessToken

Write-Host "`n===== 1. /templates?kind=career =====" -ForegroundColor Cyan
$anonT = Call "GET" "/templates" $null
Check "未登录 GET /templates -> 401（需鉴权）" ($anonT.code -eq 401) $anonT.code
$all = Call "GET" "/templates" $token
Check "登录后 GET /templates 200" ($all.code -eq 200) $all.code
$total = $all.body.data.Count
Write-Host "  全部模板数: $total"
$career = Call "GET" "/templates?kind=career" $token
Check "GET /templates?kind=career 200" ($career.code -eq 200) $career.code
Check "  career 返回 4 条" ($career.body.data.Count -eq 4) $career.body.data.Count
Check "  每条 kind=career" (($career.body.data | Where-Object { $_.kind -ne 'career' }).Count -eq 0) "有非 career 项"
$wb = Call "GET" "/templates?kind=wellbeing" $token
Check "GET /templates?kind=wellbeing 返回 4 条" ($wb.body.data.Count -eq 4) $wb.body.data.Count
$gen = Call "GET" "/templates?kind=general" $token
Check "GET /templates?kind=general 返回 7 条" ($gen.body.data.Count -eq 7) $gen.body.data.Count
Check "  三者相加 = 全部" (($career.body.data.Count + $wb.body.data.Count + $gen.body.data.Count) -eq $total) "sum=$($career.body.data.Count + $wb.body.data.Count + $gen.body.data.Count) total=$total"
$one = Call "GET" "/templates/$($career.body.data[0].id)" $token
Check "GET /templates/{id} 200" ($one.code -eq 200) $one.code

Write-Host "`n===== 2. /me/portfolio =====" -ForegroundColor Cyan
$anon = Call "GET" "/me/portfolio" $null
Check "未登录访问 /me/portfolio 被拒(401)" ($anon.code -eq 401) $anon.code
$pf = Call "GET" "/me/portfolio" $token
Check "GET /me/portfolio 200" ($pf.code -eq 200) $pf.code
Check "  新用户 totalActivities=0" ($pf.body.data.totalActivities -eq 0) $pf.body.data.totalActivities
Check "  有 items 数组" ($null -ne $pf.body.data.items) "items 缺失"
Check "  有 byMonth 数组" ($null -ne $pf.body.data.byMonth) "byMonth 缺失"
Check "  有 wellbeingCount" ($null -ne $pf.body.data.wellbeingCount) "wellbeingCount 缺失"
Check "  有 careerCount" ($null -ne $pf.body.data.careerCount) "careerCount 缺失"
Check "  displayName 非空（前端是 string 类型）" (-not [string]::IsNullOrWhiteSpace($pf.body.data.displayName)) "displayName 为空"

# 用 career 模板建活动并发布，再参与，档案应有记录
$tplId = $career.body.data[0].id
$mk = Call "POST" "/projects" $token @{ title = "档案测试活动"; templateId = $tplId }
Check "用 career 模板建项目" ($mk.code -eq 200 -or $mk.code -eq 201) $mk.code
$projId = $mk.body.data.id
Check "  项目 activityType 有值" ($mk.body.data.activityType -ne $null) "activityType 空"

Write-Host "`n===== 3. /referrals =====" -ForegroundColor Cyan
$rfAnon = Call "GET" "/referrals" $null
Check "未登录访问 /referrals 被拒(401)" ($rfAnon.code -eq 401) $rfAnon.code
$rf = Call "GET" "/referrals" $token
Check "GET /referrals 200" ($rf.code -eq 200) $rf.code
Check "  返回 items 数组" ($null -ne $rf.body.data.items -and $rf.body.data.items -is [array]) "items 缺失"
Check "  新用户工单为空" (@($rf.body.data.items).Count -eq 0) (@($rf.body.data.items).Count)
$rfP = Call "GET" "/referrals?status=pending" $token
Check "GET /referrals?status=pending 200" ($rfP.code -eq 200) $rfP.code
$rfB = Call "GET" "/referrals?limit=10" $token
Check "GET /referrals?limit=10 200" ($rfB.code -eq 200) $rfB.code
$rfBad = Call "POST" "/referrals/not-exist/status" $token @{ status = "contacted" }
Check "修改不存在工单 -> 404" ($rfBad.code -eq 404) $rfBad.code

# 公开转介入口：需要已发布活动
$pub = Call "GET" "/public/activities" $null
Check "GET /public/activities 200" ($pub.code -eq 200) $pub.code
$slug = $null
if ($pub.body.data.items -and $pub.body.data.items.Count -gt 0) { $slug = $pub.body.data.items[0].slug }
if (-not $slug) { $slug = $pub.body.data[0].slug }
Write-Host "  用活动 slug: $slug"
if ($slug) {
    $refPath = '/public/activities/' + $slug + '/referrals'
    $body1 = @{ wantsContact = $true; contact = '13800000000'; preferredTime = '周五下午' }
    $rc = Call 'POST' $refPath $null $body1
    $ok1 = ($rc.code -eq 200) -or ($rc.code -eq 201)
    Check 'POST /public/activities/{slug}/referrals 匿名可提交' $ok1 ($rc.code)
    if ($ok1) {
        Check '  返回 referralId' ($null -ne $rc.body.data.referralId) '无 referralId'
        Check '  状态 pending' ($rc.body.data.status -eq 'pending') $rc.body.data.status
    }
    $body2 = @{ wantsContact = $false }
    $rc2 = Call 'POST' $refPath $null $body2
    Check 'wantsContact=false -> 400' ($rc2.code -eq 400) $rc2.code
}

Write-Host "`n================ 汇总：PASS $pass / FAIL $fail ================" -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
