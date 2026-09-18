<#
Saves the clipboard as a UTF-8 test fixture and records its URL.

In the browser's DevTools console on the page you want:
    copy(document.documentElement.outerHTML)
then:
    tools/capture-fixture.ps1 novelphoenix chapter-page https://novelphoenix.com/novel/the-primal-hunter/chapter-2
#>
param(
    [Parameter(Mandatory)] [string] $Source,
    [Parameter(Mandatory)] [string] $Name,
    [Parameter(Mandatory)] [string] $Url
)

$dir = Join-Path $PSScriptRoot "..\app\src\test\resources\sources\$Source"
New-Item -ItemType Directory -Force $dir | Out-Null

$html = Get-Clipboard -Raw
if (-not $html -or $html -notmatch '<html') { throw "Clipboard does not contain an HTML document" }

$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText((Join-Path $dir "$Name.html"), $html, $utf8)

$urls = Join-Path $dir 'urls.txt'
$lines = if (Test-Path $urls) { Get-Content $urls } else { @() }
$lines = @($lines | Where-Object { $_ -notmatch "^$Name\.html\s" }) + "$Name.html  $Url"
[System.IO.File]::WriteAllText($urls, (($lines -join "`n") + "`n"), $utf8)

Write-Host "Wrote $Name.html ($($html.Length) chars) and updated urls.txt"
