# Rikunode 再生回数フィルタ（YouTube向け）

YouTubeのフィードを再生回数でフィルタする**非公式・独立した第三者パッチ**です。Morphe公式、Google、YouTube、Anddea、ReVancedとの提携・公認関係はありません。

[English README](README.md) · [技術設計と検証](DESIGN.md) · [ライセンス](LICENSE) · [権利表示](NOTICE)

## 対応環境

- **YouTube 21.30.216**（versionCode `1561187872`）
- **公式Morphe Patches v2.3.0** と同時適用
- 公式の **Hide layout components** に含まれるKeywordContentFilterとの共存をWindowsの構造検証で確認
- 21.46.522など、上記以外のYouTubeバージョンは対象外

公開されている `app.morphe.patcher.*` APIのみ使用し、公式パッチ内部の共有APIや拡張クラスを取り込んでいません。

## 設定と動作

公式Morphe Patchesと同時適用した場合は、公式のアドオン設定XMLを使って **Morphe > Settings** 内に **View count filter / 再生回数フィルタ** の入口を追加します。公式Morphe設定がない場合は、YouTube 21.30.216の実際の難読化Preference APIを使ってYouTube本体の設定トップへ追加します。どちらの入口も自前の設定画面を開き、保存した値はアプリ内のSharedPreferencesに保持します。

| 設定 | 初期値 |
|---|---|
| ホームのフィルタ | OFF |
| 検索のフィルタ | OFF |
| 登録チャンネルのフィルタ | OFF |
| この再生回数未満を非表示 | 1,000 |
| この再生回数を超える動画を非表示 | 1兆 |

`1.2万回視聴`、`3億回再生`、`1.2K views`、`2.5M views`、`1B views`、全角数字などを解釈します。再生回数を特定できない場合や画面の種類を判別できない場合は、**非表示にせず素通り（fail-open）**します。

**制限事項：** 画面判定は要素の識別子にある `home` / `feed` / `search` / `subscription` に依存します。動画によっては識別できず、その場合はフィルタが効きません。M1確認用の「video要素を非表示」テスト設定は初期値OFFです。通常利用ではONにしないでください。

## ビルドと適用

JDK 23、Android SDK 36およびbuild-tools 36、Git submoduleを用意します。

```sh
git clone --recurse-submodules https://github.com/rikunode/youtube-view-count-filter-patches.git
cd youtube-view-count-filter-patches
./gradlew build
```

成果物は `patches/build/libs/patches-0.1.5.mpp` です。Release添付の `SHA256SUMS` を照合してください。

パッチ適用時には**公式Morphe v2.3.0と本パッチを同じ処理で選択**してください。元APKMirrorの配布物は分割APKです。base.apk単体には密度別drawableがないため、公式フィルタのパッチ適用には使えません。Windowsでの検証ではAntiSplit-G2でbaseとリソース系30スプリットを結合しました。本リポジトリではYouTubeのAPKを配布しません。

## Morphe Managerへのパッチソース追加

Morphe Managerの **パッチソース → ソースを追加** から、`.mpp` の直リンクではなく、次の **GitHubリポジトリURL** を登録してください。

`https://github.com/rikunode/youtube-view-count-filter-patches`

Android端末では [ワンタップ追加リンク](https://morphe.software/add-source?github=rikunode/youtube-view-count-filter-patches) も使用できます。Managerはリポジトリ直下の `patches-list.json` と `patches-bundle.json` を取得し、**v0.1.5** をダウンロードします。公式Morpheソースを残したまま、パッチ適用時には公式と本パッチの両方を選択してください。
## 検証と注意

- Gradleビルド、公式フィルタとの同時適用、DEXの両フック共存、設定Activity/Providerのマニフェスト登録は確認済み
- 日本語・英語・全角・解析不能を含むパーサー9件のテスト合格
- Windows上のMorphe Desktop 2.2.0は鍵ストア署名で失敗したため、**構造検証用APKは未署名**
- **実機で動画が消えること、設定UIが表示・保存されることは未検収。v0.1.5は実験版です**

所有者の実機検収では、設定入口・保存・再起動後の維持、ホーム/検索/登録チャンネルの独立ON/OFF、万/億・K/M/B、上下限、fail-open、公式Hide layout componentsとKeywordContentFilterへの無影響を確認してください。

## ライセンス

GPL-3.0-or-later。詳細は [LICENSE](LICENSE) と [NOTICE](NOTICE) を参照してください。
