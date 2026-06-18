# OuterTune

[![OuterTune app icon](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/outertune.webp)](https://github.com/yuuichi-s/OuterTune/blob/dev/assets/outertune.webp)

Android向け Material 3 YouTube Music クライアント & ローカル音楽プレイヤー

> [!NOTE]
> これは [OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) の**個人メンテナンス用フォーク**です。
>
> 上流がYouTube Music機能の開発を停止したため、個人利用を目的として維持しています。
>
> - APKの配布は現在予定していません。

[![Latest release](https://img.shields.io/github/v/release/OuterTune/OuterTune?include_prereleases)](https://github.com/OuterTune/OuterTune/releases) [![License](https://img.shields.io/github/license/OuterTune/OuterTune)](https://www.gnu.org/licenses/gpl-3.0) [![Downloads](https://img.shields.io/github/downloads/OuterTune/OuterTune/total)](https://github.com/OuterTune/OuterTune/releases)

<a href="https://github.com/OuterTune/OuterTune/releases/latest"><img src="assets/badge_github.png" alt="Get it on GitHub" height="50"></a>&nbsp;&nbsp;<a href="https://apt.izzysoft.de/fdroid/index/apk/com.dd3boh.outertune"><img src="assets/IzzyOnDroidButtonGreyBorder.svg" alt="Get it on IzzyOnDroid" height="50"></a>&nbsp;&nbsp;<a href="https://f-droid.org/en/packages/com.dd3boh.outertune/"><img src="assets/badge_fdroid.svg" alt="Get it on F-Droid" height="50"></a>&nbsp;&nbsp;<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.dd3boh.outertune%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FDD3Boh%2FOuterTune%22%2C%22author%22%3A%22DD3Boh%22%2C%22name%22%3A%22OuterTune%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22dontSortReleasesList%5C%22%3Afalse%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22A%20Material%203%20YouTube%20Music%20client%20%26%20local%20music%20player%20for%20Android%5C%22%7D%22%2C%22overrideSource%22%3A%22GitHub%22%7D"><img src="assets/badge_obtainium.png" alt="Get it on Obtainium" height="50"></a>

> [!WARNING]
> OuterTune は上記のプラットフォームでのみ配布しています。Play Store や当プロジェクトを騙る偽サイトには掲載していません。偽バージョンやクローンアプリを発見した場合は、速やかに削除することをお勧めします。

## 上流との差分

[OuterTune/OuterTune](https://github.com/OuterTune/OuterTune) をベースに以下の変更を加えています：

- YouTube Music の複数バグを修正（アルバムトラック未表示、プレイリストクラッシュ、検索結果パースエラー）；YTM サムネイルの解像度を改善
- 歌詞取得の精度向上と表示遅延の改善（LrcLib + キャプショントラック使用）；再生中の操作バーに歌詞切替ボタンを追加
- ボトムナビゲーションを修正：タブタップでルートに直接移動、アクティブタブの再タップで先頭スクロール＆検索バーリセット、ルートごとに検索バーの状態を保持
- オーディオフォーカスを維持するプレイヤー設定を追加
- タブレットUIを復元、プレイヤーの二重オーバーレイを修正、ダイアログをスクロール可能に変更
- taglib を ffMetadataEx に置き換え、ローカル楽曲リンクとギャップレス再生を改善
- Kotlin・KSP・NewPipeExtractor・Ktor・AGP・Gradle を更新

## 機能

OuterTune は [InnerTune](https://github.com/z-huang/InnerTune) を強化したフォークです。ローカル音楽プレイヤーと YouTube Music クライアントの両機能を備えています。

- YouTube Music クライアント機能
    * 楽曲のダウンロード（オフライン再生）
    * 広告なし・バックグラウンド再生によるシームレスな再生
    * アカウント同期
        + アプリからリモートアカウントへのプレイリスト完全同期は現在一時的に利用不可
- ローカル音声ファイルの再生（MP3、OGG、FLACなど）
    * ローカル楽曲と YouTube Music の楽曲を同時に再生可能
    * MediaStore の壊れたメタデータ抽出器の代わりにカスタムタグ抽出器を使用（`\` 区切りのタグなども正しく表示）
- スタイリッシュな Material 3 デザイン
- 複数キュー
- 同期歌詞、および単語単位・カラオケ形式の歌詞に対応（LRC、TTMLなど）
- 音量正規化、テンポ・ピッチ調整、その他各種オーディオエフェクト
- Android Auto 対応
- Android 8（Oreo）以降をサポート

> [!NOTE]
> Android 8（Oreo）以降をサポートしています。Android 7.x（Nougat）でも動作する可能性はありますが、正式サポート対象外です。

> [!NOTE]
> FAQ やガイドは[wiki](https://github.com/OuterTune/OuterTune/wiki/Frequently-Asked-Questions-(FAQ))をご覧ください。

## スクリーンショット

[![メインプレイヤー画面](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/main-interface.jpg)](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/main-interface.jpg)

[![プレイヤー画面](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/player.jpg)](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/player.jpg)

[![YouTube Music との同期](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/ytm-sync.jpg)](https://github.com/yuuichi-s/OuterTune/raw/dev/assets/ytm-sync.jpg)

[全画像ギャラリー](https://github.com/yuuichi-s/OuterTune/tree/dev/assets/gallery)

> [!WARNING]
> YouTube Music が利用できない地域では、プロキシまたは VPN を使用しない限りこのアプリは使用できません。

## ビルド & コントリビュート

ご自身でビルドしたい方は[ビルドおよびコントリビュートに関するノート](CONTRIBUTING.md)をご覧ください。

### 翻訳の投稿

OuterTune の翻訳には Weblate を使用しています。詳細や翻訳の投稿は[Weblate ページ](https://hosted.weblate.org/projects/outertune/)をご覧ください。

[![翻訳ステータス](https://hosted.weblate.org/widget/outertune/multi-auto.svg)](https://hosted.weblate.org/projects/outertune/)

世界中の方々に OuterTune をお届けするためにご協力いただきありがとうございます。

## ヘルプ & サポート

- **このフォーク固有のバグ**については、[このリポジトリの Issue](https://github.com/yuuichi-s/OuterTune/issues) を作成してください。

## クレジット

すべてのコントリビューターに感謝します。[こちら](https://github.com/OuterTune/OuterTune/graphs/contributors)からご確認いただけます。

このフォークの素晴らしいベースを提供してくださった [z-huang/InnerTune](https://github.com/z-huang/InnerTune) なしには実現できませんでした。

ローカル音楽プレイヤーの理想的な体験のインスピレーションをくれた [Musicolet](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)。

精神的サポートと伝説の歌詞パーサーを提供してくれた [Gramophone](https://github.com/FoedusProgramme/Gramophone)。

[![Star History Chart](https://api.star-history.com/svg?repos=outertune/outertune&type=Date)](https://www.star-history.com/#outertune/outertune&Date)

## 免責事項

本プロジェクトおよびその内容は、YouTube、Google LLC またはその関連会社・子会社と一切関係なく、資金提供、承認、推薦も受けていません。

本プロジェクトで使用されている商標、サービスマーク、商号、その他の知的財産権はそれぞれの権利者に帰属します。