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

  $searchRoots = @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft', 'C:\Program Files\Java')
  foreach ($root in $searchRoots) {
    if (Test-Path $root) {
      $candidates += @(
        Get-ChildItem $root -Directory -Filter 'jdk*21*' -ErrorAction SilentlyContinue |
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
  throw '未找到 Maven。请安装 Maven，或把 MAVEN_HOME 指向 Maven 安装目录。'
}

$env:JAVA_HOME = Resolve-Jdk21
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$maven = Resolve-Maven
$repoArg = "-Dmaven.repo.local=$($mavenRepo.Replace('\', '/'))"
$mavenArgs = if ($MavenArguments.Count -gt 0) { $MavenArguments } else {
  # 默认激活 dev profile：application.yml 里 DB_PASSWORD / JWT_SECRET 已经没有默认值，
  # 不激活 dev 会直接启动失败（这是刻意的，见 application-dev.yml 顶部注释）。
  # spring-boot:run 用 -D 传 profile，打好的 jar 用 --spring.profiles.active=dev。
  @('spring-boot:run', '-Dspring-boot.run.profiles=dev')
}

Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "Maven: $maven"
Write-Host "Maven repository: $mavenRepo"
Write-Host "Maven args: $($mavenArgs -join ' ')"

Push-Location $serverRoot
try {
  & $maven $repoArg ($mavenArgs -join ' ')
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
