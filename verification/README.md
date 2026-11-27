# Release acceptance (private local fixtures)

The filter and diagnostic bundles use the same version and display name, but distinct source URLs. Build the diagnostic first with `./gradlew build :patches:buildAndroid :patches:generatePatchesList -PdiagnosticBundle=true`, copy the MPP to diagnostic-0.1.15.mpp, then build without the property and copy patches-0.1.15.mpp. Do not overwrite the tested copies. Use the exact tested binaries for publication; CI rebuilds are supplementary, not release assets.

Run Validate-Release.ps1 with the private merged 21.30.216 input, ToolDir containing the pinned Desktop JARs and official MPPs, BundleDir containing the two tested bundles, ReceiptDir, Sdk and Java. Run Environment stable and dev14, each with Case probe, filter, both, neither. Probe cases require SDK DEX verifier and MethodAnalyzer checks. Both must reject with the retained guard. Single uses the verified stock APK; partial uses a private synthetic fixture with the predicate fingerprint string changed, leaving other instructions intact. Preserve the resulting logs, JSON, input and output hashes. Verify-Output.py takes the private analysis directory (containing inspect_dex.py), receipt directory and aapt2.exe path, and checks the resulting DEX/control flow, embedded coverage, settings and manifest.

Run Validate-Runtime.ps1 with JDK bin directory, a local org.json JAR (tested 20080701), and a fresh receipt directory. It compiles the real diagnostic queue and recording code with minimal Android stubs. Tests are JVM fixtures, not an ART or device benchmark.

Before publication verify both MPPs' generated patch lists and inner DEX/extension isolation, compare filter implementation with the prior release, and require all acceptance checks. Create a draft prerelease using the tested copies and SHA256SUMS, download and compare them byte-for-byte before making it public. Download and verify public assets again. Never publish APK fixtures, output APKs or private capture logs.

The related-items identification decision and on-device capture/smoke remain separate work.
