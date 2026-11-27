param(
    [Parameter(Mandatory=$true)][string]$JavaBin,
    [Parameter(Mandatory=$true)][string]$JsonJar,
    [Parameter(Mandatory=$true)][string]$ReceiptDir
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$classes=Join-Path $ReceiptDir 'runtime-classes'
$fixture=Join-Path $ReceiptDir 'runtime-fixture'
if(Test-Path $fixture){throw 'Use a fresh receipt directory'}
New-Item -ItemType Directory -Force $classes,$fixture | Out-Null
$src=@(Get-ChildItem "$PSScriptRoot/runtime" -Recurse -Filter '*.java' | ForEach-Object FullName)
$src+=@(Get-ChildItem "$root/extensions/extension/src/diagnostic" -Recurse -Filter '*.java' | Where-Object Name -in @('RelatedItemsProbe.java','CaptureQueue.java','ViewCountParser.java') | ForEach-Object FullName)
& "$JavaBin/javac.exe" -encoding UTF-8 -cp $JsonJar -d $classes $src
if($LASTEXITCODE){exit $LASTEXITCODE}
& "$JavaBin/java.exe" -cp "$classes;$JsonJar" app.rikunode.morphe.extension.youtube.diagnostic.filters.CaptureQueueTest | Tee-Object -FilePath "$ReceiptDir/runtime-tests.log"
if($LASTEXITCODE){exit $LASTEXITCODE}
& "$JavaBin/java.exe" -cp "$classes;$JsonJar" ProbeRuntimeTest $fixture | Tee-Object -FilePath "$ReceiptDir/runtime-tests.log" -Append
exit $LASTEXITCODE
