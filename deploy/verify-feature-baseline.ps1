[CmdletBinding()]
param(
    [string]$RepoRoot = ''
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = Split-Path -Parent $PSScriptRoot
}

function Require-File([string]$RelativePath) {
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required feature file is missing: $RelativePath"
    }
}

function Require-Marker([string]$RelativePath, [string]$Marker) {
    $path = Join-Path $RepoRoot $RelativePath
    $content = Get-Content -LiteralPath $path -Raw -Encoding utf8
    if ($content.IndexOf($Marker, [StringComparison]::Ordinal) -lt 0) {
        throw "Required feature marker is missing: $RelativePath -> $Marker"
    }
}

$requiredFiles = @(
    'admin/starfree-admin/source/admin/dynamicAnalytics.php',
    'admin/starfree-admin/source/admin/dynamicUserAnalytics.php',
    'admin/starfree-admin/source/admin/aiModeration.php',
    'admin/starfree-admin/source/admin/aiModerationPost.php',
    'backend/database/migrations/011_dynamic_core_extensions.sql',
    'backend/database/migrations/012_space_presentation.sql',
    'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/AiModerationService.java',
    'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/SpacePollService.java',
    'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/user/UserProfileService.java',
    'pages/contents/userinfo.vue',
    'pages/user/useredit.vue',
    'pages/space/info.vue',
    'pages/space/post.vue',
    'components/space-poll/space-poll.vue'
)
foreach ($file in $requiredFiles) { Require-File $file }

Require-Marker 'admin/starfree-admin/source/admin/Menu.php' 'dynamicAnalytics.php'
Require-Marker 'admin/starfree-admin/source/admin/Menu.php' 'aiModeration.php'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/SpaceController.java' '/pollVote'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/SpaceController.java' '/spacePresentationList'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/SpaceReportService.java' 'reviewAi'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/space/SpaceReportService.java' 'normalizeAi'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/user/UserController.java' 'profiles.attach'
Require-Marker 'backend/starfree-replacement/src/main/java/cn/lcxqy/starfree/user/UserController.java' 'containsProfileFields'
Require-Marker 'pages/contents/userinfo.vue' 'gender'
Require-Marker 'pages/user/useredit.vue' 'showBirthday'
Require-Marker 'pages/space/info.vue' 'space-poll'
Require-Marker 'pages/space/post.vue' 'pollPayload'
Require-Marker 'pages/home/square.vue' 'spacePresentationList'
Require-Marker 'pages/manage/space.vue' 'spacePresentation'

$migrationNames = Get-ChildItem (Join-Path $RepoRoot 'backend/database/migrations') -File -Filter '*.sql' |
    ForEach-Object { if ($_.BaseName -match '^([0-9]+)_') { $Matches[1] } }
$duplicateMigrations = $migrationNames | Group-Object | Where-Object Count -gt 1
if ($duplicateMigrations) {
    $ids = ($duplicateMigrations | ForEach-Object Name) -join ', '
    Write-Warning "Duplicate migration prefixes detected: $ids. Review ordering before any migration is run."
}

Write-Output 'feature-baseline=ok'
Write-Output 'checked=dynamic-analytics,user-details,ai-moderation,polls,dynamic-presentation'
Write-Output 'production-connection=false'
