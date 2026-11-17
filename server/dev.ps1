param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]] $MavenArguments
)

# ─────────────────────────────────────────────────────────────────────────────
# 本机开发启动脚本：mvn spring-boot:run，默认激活 dev profile。
#
# 为什么不直接开一个 JWT_SECRET 环境变量：Spring Boot 的 profile 配置
# 本来就是为「同一份代码、不同环境有不同默认值」设计的，用 dev profile
# 比让每个开发者各自设环境变量更容易复制，也不会有人把生产密钥写进 shell。
#
# 想跑生产式启动（无默认值、必须给环境变量）：
#   .\dev.ps1 spring-boot:run
#   $env:DB_PASSWORD='...'; $env:JWT_SECRET='...'
# ─────────────────────────────────────────────────────────────────────────────

$ErrorActionPreference = 'Stop'

$serverRoot = $PSScriptRoot
$mavenRepo = Join-Path $serverRoot '.maven-repo'

function Resolve-Jdk21 {
  $candidates = @()
  $candidates += 'D:\2025\java\IntelliJ IDEA 2025.1\jbr'
  if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

  # USERPROFILE\.jdks 是 IDEA「Download JDK」装 JDK 的位置，也是本机最常见的一种。
  # 少了这一条，凡是 JDK 由 IDEA 托管、又没设 JAVA_HOME 的机器都会报「未找到 JDK 21」。
  $searchRoots = @(
    "$env:USERPROFILE\.jdks",
    'C:\Program Files\Eclipse Adoptium',
    'C:\Program Files\Microsoft',
    'C:\Program Files\Java'
  )
  foreach ($root in $searchRoots) {
    if (Test-Path $root) {
      # 过滤用 *21* 而不是 jdk*21*：IDEA 托管的 JDK 目录常叫 ms-21.0.13.0.2（Microsoft 版）
      # 或 corretto-21.x，不一定以 jdk 开头。真正是不是 21 由后面的 javac -version 复核，
      # 所以这里宽松一点没有风险。
      $candidates += @(
        Get-ChildItem $root -Directory -Filter '*21*' -ErrorAction SilentlyContinue |
          Select-Object -ExpandProperty FullName
      )
    }
  }

  foreach ($candidate in $candidates) {
    if (!$candidate) { continue }
    $java = Join-Path $candidate 'bin\java.exe'
    $javac = Join-Path $candidate 'bin\javac.exe'
    if (!(Test-Path $java) -or !(Test-Path $javac)) { continue }
    $version = (& $javac -version 2>&1 | Out-String)
    if ($version -match '^javac\s+21\.') { return $candidate }
  }

  throw '未找到 JDK 21。请安装 JDK 21，或把 JAVA_HOME 指向 JDK 21。'
}

function Resolve-Maven {
  $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
  $candidates = @()
  $candidates += 'D:\2025\java\IntelliJ IDEA 2025.1\plugins\maven\lib\maven3\bin\mvn.cmd'
  if ($mavenCommand -and $mavenCommand.Source) { $candidates += $mavenCommand.Source }
  if ($env:MAVEN_HOME) {
    $candidates += (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd')
  }

  if (Test-Path 'C:\Program Files\Apache') {
    $candidates += @(
      Get-ChildItem 'C:\Program Files\Apache' -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty FullName
    )
  }

  foreach ($candidate in $candidates) {
    if ($candidate -and (Test-Path $candidate)) { return $candidate }
  }

  # 最后兜底：仓库自带 Maven Wrapper（server/mvnw.cmd），第一次跑会自己下 Maven。
  # 这样没装独立 Maven 的机器也能起服务，不用先去装一个。
  $wrapper = Join-Path $PSScriptRoot 'mvnw.cmd'
  if (Test-Path $wrapper) { return $wrapper }

  throw '未找到 Maven。请安装 Maven，或把 MAVEN_HOME 指向 Maven 安装目录。'
}

$env:JAVA_HOME = Resolve-Jdk21
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$maven = Resolve-Maven
$mavenArgs = if ($MavenArguments.Count -gt 0) { $MavenArguments } else {
  # 默认激活 dev profile：application.yml 里 DB_PASSWORD / JWT_SECRET 已经没有默认值，
  # 不激活 dev 会直接启动失败（这是刻意的，见 application-dev.yml 顶部注释）。
  # spring-boot:run 用 -D 传 profile，打好的 jar 用 --spring.profiles.active=dev。
  @('spring-boot:run', '-Dspring-boot.run.profiles=dev')
}

# 有 .maven-repo 才用它（内网/离线场景）；没有就走默认的 ~/.m2。
# 强行指向一个不存在的目录，会让 Maven 把所有依赖从头下一遍——第一次启动要等十几分钟。
$effectiveArgs = @()
if (Test-Path $mavenRepo) {
  $effectiveArgs += "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))"
}
$effectiveArgs += $mavenArgs

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Maven: $maven"
Write-Host "Maven repository: $(if (Test-Path $mavenRepo) { $mavenRepo } else { '默认（~/.m2）' })"
Write-Host "Maven args: $($effectiveArgs -join ' ')"

Push-Location $serverRoot
try {
  # 参数用数组展开，别拼成一个字符串：拼字符串时 "-Dxxx=dev" 会被当成一个带空格的整串
  & $maven @effectiveArgs
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
