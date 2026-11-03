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
            $txt = $sr.ReadToEnd()
            $code = [int]$resp.StatusCode
            try { $j = $txt | ConvertFrom-Json } catch { $j = $txt }
            return @{ code = $code; body = $j }
        }
        return @{ code = -1; body = $_.Exception.Message }
    }
}

# 1. 注册
$email = "wb$(Get-Random)@campus.local"
$reg = Call "POST" "/auth/register" $null @{ email = $email; password = "Passw0rd!2026"; displayName = "心理社团" }
Check "POST /auth/register" ($reg.code -eq 200 -or $reg.code -eq 201) $reg.code
$token = $reg.body.data.accessToken
$userId = $reg.body.data.user.id

# 2. 建项目
$doc = @{ title = "心屿·心理知识问答"; pages = @(@{ id = "home"; name = "首页"; role = "home"; blocks = @(
    @{ id = "b1"; type = "heading"; props = @{ text = "心理知识问答" } },
    @{ id = "b2"; type = "form"; props = @{ fields = @(@{ name = "nick"; label = "怎么称呼"; type = "text" }) } },
    @{ id = "b3"; type = "button"; props = @{ text = "提交"; action = @{ type = "submit" } } }
) }) }
$mk = Call "POST" "/projects" $token @{ title = "心屿·心理知识问答"; activityType = "活动报名"; initialDocument = $doc }
Check "POST /projects" ($mk.code -eq 200 -or $mk.code -eq 201) $mk.code
$projId = $mk.body.data.id

# 3. 读隐私设置（默认应存在）
$g1 = Call "GET" "/projects/$projId/privacy" $token
Check "GET /projects/{id}/privacy" ($g1.code -eq 200) $g1.code
Check "  默认 privacyMode=optional" ($g1.body.data.privacy.privacyMode -eq "optional") $g1.body.data.privacy.privacyMode
Check "  默认 retentionDays=30" ($g1.body.data.privacy.retentionDays -eq 30) $g1.body.data.privacy.retentionDays

# 4. 存隐私设置：全匿名
$put = Call "PUT" "/projects/$projId/privacy" $token @{
    privacyMode = "anonymous"; consentRequired = $true
    consentText = "本活动只收集参与所需信息，不做心理诊断。"
    retentionDays = 7; allowExport = $true; allowLeave = $true; quietSpace = $true
}
Check "PUT /projects/{id}/privacy" ($put.code -eq 200) $put.code
Check "  回读 privacyMode=anonymous" ($put.body.data.privacy.privacyMode -eq "anonymous") $put.body.data.privacy.privacyMode
Check "  回读 retentionDays=7" ($put.body.data.privacy.retentionDays -eq 7) $put.body.data.privacy.retentionDays

$slug = "wellbeing-$([guid]::NewGuid().ToString("N").Substring(0,8))"
# 5. 发布
$pub = Call "POST" "/projects/$projId/publish" $token @{ slug = $slug }
Check "POST /projects/{id}/publish" ($pub.code -eq 200) $pub.code

# 6. 公开页应带隐私
$act = Call "GET" "/public/activities/$slug" $null $null
Check "GET /public/activities/{slug}" ($act.code -eq 200) $act.code
Check "  公开页带 privacy.privacyMode=anonymous" ($act.body.data.privacy.privacyMode -eq "anonymous") $act.body.data.privacy.privacyMode

# 7. 读参与说明（不需要登录）
$des = Call "GET" "/public/activities/$slug/privacy" $null $null
Check "GET /public/activities/{slug}/privacy" ($des.code -eq 200) $des.code
Check "  说明文本一致" ($des.body.data.consentText.Length -gt 0) $des.body.data.consentText

# 8. 不勾选直接提交 -> 应当 422
$noConsent = Call "POST" "/public/activities/$slug/submissions" $null @{
    pageId = "home"; formComponentId = "b2"; answers = @{ nick = "小明" }; idempotencyKey = "k-noconsent-$projId"
}
Check "未同意直接提交 -> 422" ($noConsent.code -eq 422) "$($noConsent.code) $($noConsent.body | ConvertTo-Json -Compress)"

# 9. 勾选同意
$cons = Call "POST" "/public/activities/$slug/consents" $null @{ consentText = "本活动只收集参与所需信息，不做心理诊断。" }
Check "POST /public/activities/{slug}/consents" ($cons.code -eq 200) $cons.code
Check "  拿到 consentId" ([bool]$cons.body.data.consentId) $cons.body.data.consentId
$consentId = $cons.body.data.consentId

# 10. 带同意提交
$sub = Call "POST" "/public/activities/$slug/submissions" $null @{
    pageId = "home"; formComponentId = "b2"; answers = @{ nick = "小明" }
    idempotencyKey = "k-withconsent-$projId"; privacy = @{ consentId = $consentId; anonymous = $false }
}
Check "带同意提交" ($sub.code -eq 200) "$($sub.code) $($sub.body | ConvertTo-Json -Compress)"
$subId = $sub.body.data.submissionId

# 11. 幂等
$sub2 = Call "POST" "/public/activities/$slug/submissions" $null @{
    pageId = "home"; formComponentId = "b2"; answers = @{ nick = "小明" }
    idempotencyKey = "k-withconsent-$projId"; privacy = @{ consentId = $consentId; anonymous = $false }
}
Check "重复提交幂等" ($sub2.body.data.submissionId -eq $subId) "$($sub2.body.data.submissionId) vs $subId"

# 12. 报名列表（组织者）
$list = Call "GET" "/projects/$projId/submissions" $token $null
Check "GET /projects/{id}/submissions" ($list.code -eq 200) $list.code
Check "  记录匿名档快照" ($list.body.data.Count -ge 1 -and $list.body.data[0].privacyMode -eq "anonymous") $list.body.data.Count
Check "  匿名档 anonymous=true" ($list.body.data.Count -ge 1 -and $list.body.data[0].anonymous -eq $true) "n/a"
Check "  匿名档有匿名编号" ($list.body.data.Count -ge 1 -and [bool]$list.body.data[0].anonymousToken) "n/a"

# 13. 聚合
$sum = Call "GET" "/projects/$projId/submissions/summary" $token $null
Check "GET /projects/{id}/submissions/summary" ($sum.code -eq 200) $sum.code
Check "  total=1" ($sum.body.data.total -eq 1) $sum.body.data.total
Check "  anonymous=1" ($sum.body.data.anonymous -eq 1) $sum.body.data.anonymous
Check "  consented=1" ($sum.body.data.consented -eq 1) $sum.body.data.consented

# 14. 活动反馈
$fb = Call "POST" "/public/activities/$slug/feedback" $null @{ choice = "helpful"; score = 5; comment = "很放松" }
Check "POST /public/activities/{slug}/feedback" ($fb.code -eq 200) "$($fb.code) $($fb.body | ConvertTo-Json -Compress)"
$badFb = Call "POST" "/public/activities/$slug/feedback" $null @{ choice = "bogus" }
Check "非法反馈选项 -> 400" ($badFb.code -eq 400) $badFb.code

# 15. 反馈聚合
$fbs = Call "GET" "/projects/$projId/feedback/summary" $token $null
Check "GET /projects/{id}/feedback/summary" ($fbs.code -eq 200) $fbs.code
Check "  total=1" ($fbs.body.data.total -eq 1) $fbs.body.data.total
Check "  averageScore=5" ($fbs.body.data.averageScore -eq 5) $fbs.body.data.averageScore
Check "  helpful=1" ($fbs.body.data.distribution.helpful -eq 1) $fbs.body.data.distribution.helpful

# 16. 通知
$noti = Call "GET" "/notifications" $token $null
Check "GET /notifications" ($noti.code -eq 200) $noti.code
Check "  有报名通知" ($noti.body.data.items.Count -ge 1) $noti.body.data.items.Count
Check "  通知里不含参与者信息" (-not (($noti.body.data | ConvertTo-Json -Depth 20 -Compress).Contains("小明"))) "leaked"
$nid = $noti.body.data.items[0].id
$rd = Call "POST" "/notifications/$nid/read" $token $null
Check "POST /notifications/{id}/read" ($rd.code -eq 200) $rd.code
$noti2 = Call "GET" "/notifications" $token $null
Check "  已读生效" ($noti2.body.data.items.Count -ge 1 -and $noti2.body.data.items[0].read -eq $true) "n/a"
$allrd = Call "POST" "/notifications/read-all" $token $null
Check "POST /notifications/read-all" ($allrd.code -eq 200) $allrd.code

# 17. 导出（隐私开关开着应该成功）
$exp = Call "POST" "/projects/$projId/exports" $token $null
Check "POST /projects/{id}/exports" ($exp.code -eq 200 -or $exp.code -eq 201) "$($exp.code) $($exp.body | ConvertTo-Json -Compress)"

# 18. 关闭导出后再导出 -> 403
Call "PUT" "/projects/$projId/privacy" $token @{ privacyMode = "anonymous"; allowExport = $false } | Out-Null
$exp2 = Call "POST" "/projects/$projId/exports" $token $null
Check "关闭导出后 -> 403" ($exp2.code -eq 403) $exp2.code

# 19. 清理
$purge = Call "POST" "/projects/$projId/submissions/purge" $token $null
Check "POST /projects/{id}/submissions/purge" ($purge.code -eq 200) $purge.code

# 20. 越权：另一个用户读别人项目的隐私
$email2 = "other$(Get-Random)@campus.local"
$reg2 = Call "POST" "/auth/register" $null @{ email = $email2; password = "Passw0rd!2026"; displayName = "路人" }
$t2 = $reg2.body.data.accessToken
$p2 = Call "GET" "/projects/$projId/privacy" $t2 $null
Check "他人读隐私 -> 403/404" ($p2.code -eq 403 -or $p2.code -eq 404) $p2.code

Write-Host ""
Write-Host "==== PASS=$pass FAIL=$fail ====" -ForegroundColor Cyan
