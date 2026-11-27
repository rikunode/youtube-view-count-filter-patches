# Requires private stock/merged APKs and previously built bundles. Never publishes an APK.
param(
    [Parameter(Mandatory=$true)][ValidateSet('stable','dev14')][string]$Environment,
    [Parameter(Mandatory=$true)][ValidateSet('probe','filter','both','neither','partial','single')][string]$Case,
    [Parameter(Mandatory=$true)][string]$InputApk,
    [Parameter(Mandatory=$true)][string]$ToolDir,
    [Parameter(Mandatory=$true)][string]$BundleDir,
    [Parameter(Mandatory=$true)][string]$ReceiptDir,
    [Parameter(Mandatory=$true)][string]$Sdk,
    [Parameter(Mandatory=$true)][string]$Java
)
$ErrorActionPreference='Stop'
$env:JAVA_HOME=Split-Path (Split-Path $Java)
$env:Path="$(Split-Path $Java);$env:Path"
$jar=Join-Path $ToolDir $(if($Environment -eq 'stable'){'morphe-desktop-2.3.0-all.jar'}else{'morphe-desktop-2.4.0-dev.3-all.jar'})
$official=Join-Path $ToolDir $(if($Environment -eq 'stable'){'patches-2.4.0.mpp'}else{'patches-2.4.0-dev.14.mpp'})
$diag=Join-Path $BundleDir 'diagnostic-0.1.14.mpp'
$filter=Join-Path $BundleDir 'patches-0.1.14.mpp'
$folder=Join-Path $ReceiptDir "$Environment-$Case"
New-Item -ItemType Directory -Force $folder | Out-Null
$out=Join-Path $folder 'patched.apk'
$result=Join-Path $folder 'result.json'
if(Test-Path $out){throw "Output already exists: $out. Use a fresh receipt directory."}
$inputs=@($InputApk,$jar,$diag)
$cli=@('-jar',$jar,'patch')
if($Case -in @('partial','single')){
    $cli+=@("--patches=$diag",'--enable=Related items diagnostic probe','--exclusive')
}else{
    $inputs+=@($official,$filter)
    # Desktop scopes each enable/disable to the immediately preceding bundle.
    $cli+=@("--patches=$official","--patches=$diag")
    if($Case -in @('probe','both')){$cli+='--enable=Related items diagnostic probe'}else{$cli+='--disable=Related items diagnostic probe'}
    $cli+="--patches=$filter"
    if($Case -in @('filter','both')){$cli+='--enable=View count filter'}else{$cli+='--disable=View count filter'}
}
$cli+=@('--unsigned',"--out=$out","--result-file=$result",$InputApk)
function Get-Info($p){
    $f=Get-Item -LiteralPath $p
    [ordered]@{path=$f.FullName;bytes=$f.Length;sha256=(Get-FileHash -LiteralPath $p).Hash.ToLowerInvariant()}
}
$receipt=[ordered]@{
    environment=$Environment; case=$Case; java=$Java; args=$cli;
    inputs=@($inputs|ForEach-Object {Get-Info $_}); officialSelection=if($Case -in @('partial','single')){'none'}else{'defaults'};
    expected=if($Case -eq 'both'){'guard rejection'}else{'success'};
}
# Native stderr may contain warnings; preserve them and use the actual process exit code.
$ErrorActionPreference='Continue'
& $Java @cli 2>&1 | Tee-Object -FilePath (Join-Path $folder 'patch.log') | Out-Null
$code=$LASTEXITCODE
$ErrorActionPreference='Stop'
$receipt.exitCode=$code
if(Test-Path $result){$receipt.result=Get-Content $result -Raw | ConvertFrom-Json}
if(Test-Path $out){$receipt.output=Get-Info $out}
$receipt | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 (Join-Path $folder 'receipt.json')
if($Case -eq 'both'){
    if($code -eq 0 -or (Test-Path $out) -or !$receipt.result -or
        !($receipt.result.failedPatches | Where-Object {$_.name -eq 'Related items diagnostic probe' -and $_.error -match 'Select only Related items diagnostic probe, not View count filter'})){
        throw 'Expected cross-source diagnostic purity guard did not reject'
    }
    Write-Output "$Environment/${Case}: expected rejection"; exit 0
}
if($code -ne 0 -or !$receipt.result.success -or $receipt.result.failedPatches.Count -ne 0 -or !(Test-Path $out)){
    throw "Patching failed; see $folder"
}
$applied=@($receipt.result.appliedPatches)
if(($applied -contains 'Related items diagnostic probe') -ne ($Case -in @('probe','partial','single'))){throw 'Unexpected diagnostic patch selection'}
if(($applied -contains 'View count filter') -ne ($Case -eq 'filter')){throw 'Unexpected filter selection'}
if($Case -eq 'probe'){
    $ErrorActionPreference='Continue'
    & $Java -Xmx4g -cp $jar "$PSScriptRoot/VerifyDiagnostic.java" $out $Sdk (Join-Path $folder 'dex') 2>&1 |
        Tee-Object -FilePath (Join-Path $folder 'sdk-verifier.log') | Out-Null
    $verify=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    $receipt.sdkVerifierExit=$verify
    $receipt | ConvertTo-Json -Depth 20 | Set-Content -Encoding UTF8 (Join-Path $folder 'receipt.json')
    if($verify -ne 0){throw "SDK verifier failed: $folder"}
}
Write-Output "$Environment/${Case}: exit=$code applied=$($applied.Count) failed=0 bytes=$($receipt.output.bytes) SHA256=$($receipt.output.sha256)"
