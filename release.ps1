<#
.SYNOPSIS
    Builds a signed Fowi release and tags it in Git.

.DESCRIPTION
    Run after raising versionCode and versionName in app/build.gradle.kts and adding the
    version to CHANGELOG.md. The script checks that the working tree is committed, runs the
    unit tests, builds the signed release APK, copies it to release/Fowi-<version>.apk with a
    SHA-256 checksum, and creates the Git tag v<version>. Uploading to GitHub stays a manual step.
#>
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) { $git = "$env:ProgramFiles\Git\cmd\git.exe" }

if (-not (Test-Path 'keystore.properties')) {
    throw 'keystore.properties is missing, so the release cannot be signed.'
}
if (& $git status --porcelain) {
    throw 'There are uncommitted changes. Commit them first so the tag matches the release.'
}

$version = [regex]::Match((Get-Content 'app/build.gradle.kts' -Raw), 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $version) { throw 'versionName not found in app/build.gradle.kts.' }
$tag = "v$version"
if (& $git tag --list $tag) { throw "Tag $tag exists already. Raise versionName and versionCode first." }
if (-not (Select-String -Path 'CHANGELOG.md' -Pattern "^## $([regex]::Escape($version))\b" -Quiet)) {
    throw "CHANGELOG.md has no section for $version."
}

if (-not $env:JAVA_HOME) {
    $jbr = "$env:ProgramFiles\Android\Android Studio\jbr"
    if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
}
& .\gradlew.bat testDebugUnitTest assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed.' }

New-Item -ItemType Directory -Force 'release' | Out-Null
$apk = "release/Fowi-$version.apk"
Copy-Item 'app/build/outputs/apk/release/app-release.apk' $apk -Force
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
"$hash  Fowi-$version.apk" | Set-Content "$apk.sha256" -Encoding ascii

& $git tag -a $tag -m "Fowi $version"

Write-Host ""
Write-Host "Release $version ready:"
Write-Host "  $apk"
Write-Host "  SHA-256 $hash"
Write-Host "Next: git push --follow-tags, then create a GitHub release for $tag and upload both files."
