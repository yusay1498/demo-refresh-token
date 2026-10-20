---
applyTo: "**/*.java"
---

# Spring Boot 固有レビュー観点

## 前提
- Java 25 / Spring Boot 4 以上を使用すること

## DI/コンポーネント設計
- コンストラクタインジェクションを使用しているか（`@Autowired` フィールドインジェクションを避ける）
- `@Component` / `@Service` / `@Repository` の使い分けが責務に合っているか
- 循環依存が発生していないか

## Clock DI（テスタビリティ）
- `LocalDateTime.now()` を直接使用せず、`Clock` をDIして `LocalDateTime.now(clock)` を使用する
- `Clock` インスタンスは `@Configuration` クラスで `@Bean` 登録する（`Clock.systemDefaultZone()`）
- これによりテスト時に時間を固定でき、アサーションが確実になる

## トランザクション管理
- `@Transactional` がApplication層（サービスクラス）に適切に設定されているか
- トランザクションの伝播属性が適切か

## 認可制御
- メソッドセキュリティ（`@PreAuthorize`、`@PostAuthorize`）がApplication層で適用されているか
- メタアノテーション（`@IsAdmin` 等）を活用して認可ルールを見通しよく表現しているか

## REST API 設計
- HTTPメソッド・ステータスコードのセマンティクスが正しいか
- リクエスト/レスポンスの型に `record` を活用しているか
- バリデーション（`@Valid`、`@NotNull` 等）が適切に設定されているか
- `@PathVariable` はエンティティのIDのみに使用する（それ以外の属性は `@RequestParam`）
- リソース作成時（POST）は `201 Created` + `Location` ヘッダを返す（`ResponseEntity.created(URI)`）
- 更新時はエンティティIDを指定して更新する（IDは `save` の返却値から取得して呼び出し元に伝える）
- Controller のメソッド命名はHTTPメソッドに寄せる（`get` / `post` / `put` / `delete`）

## Spring Data Repository 設計
- `findById` は `Optional` で返す
- `save` メソッドで insert / update の両方を処理する（メソッドを分けない）
- `save` は保存した結果のエンティティを返却する（自動採番IDを呼び出し元に伝えるため）
- `save` 後に永続化結果を呼び出し元へ返す方針を明確にする（引数ベースの値をそのまま返すと、永続化失敗時でも成功したように見えるおそれがある）
- 削除は `deleteById` の命名に統一する

## エラーハンドリング
- `@RestControllerAdvice` でグローバルな例外ハンドリングを行っているか
- エラーレスポンスは [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457.html) に準拠しているか
- Spring の [`ProblemDetail`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ProblemDetail.html) を活用しているか
- `ResponseEntity.of(ProblemDetail)` は Spring Boot 3.x（Spring Framework 6.0）以降で利用可能なオーバーロードであり、`ProblemDetail` を直接渡して `.build()` で返せる
- Spring Framework 6.0 より前（Spring Boot 2.x 以前）では `ResponseEntity.of()` は `Optional` 専用のため、`ResponseEntity.of(ProblemDetail)` と混同しない
- エラーレスポンスにスタックトレースや内部実装の情報を含めていないか

## データアクセス
- N+1 問題が発生していないか
- エンティティをそのままレスポンスに返していないか（層ごとに型を分離）
- SQLはデータアクセスメソッド内に直接記述する（別の場所に分離しない。パラメータとSQLの対応が確認しづらくなる）
- 更新行数が想定と異なる場合（2件以上更新など）は例外をスローしてロールバックする
- クエリ内では*の使用は禁止、エイリアスの省略も禁止

## テスト（Spring固有）
- Repository テストでは `@DataJdbcTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` を使用する（自動トランザクション＋ロールバック）
- テストデータは `@Sql` で用意する（`schema.sql` / `data.sql` に依存しない）
- save のテストでは、返却値の確認に加えて、DB上に永続化されていることまで検証する（JdbcClient等で直接確認）
- 返却されたエンティティの全フィールドをアサーションする
- `empty` ではなくダミーインスタンスを返却してアサーションする（どの時点の値か特定できるように）

## セキュリティ（最重要）
- 認証・認可の設定が適切か（Spring Security の設定漏れがないか）
- エンドポイントのアクセス制御が明示的に設定されているか（デフォルト許可になっていないか）
- CORS設定が最小限に制限されているか
- CSRF対策が適切か（API専用なら無効化の根拠があるか）
- 機密情報がレスポンスボディ・ヘッダーに漏洩していないか
- パスワードが `BCryptPasswordEncoder` 等で適切にハッシュ化されているか
- Rate Limiting / ブルートフォース対策が考慮されているか
- 依存ライブラリに既知の脆弱性がないか
- `@Valid` によるバリデーションが全ての外部入力に適用されているか

## 設定/プロファイル
- 環境依存の値が外部化（`application.yml`、環境変数）されているか
- ハードコードされたマジックナンバーや接続情報がないか
- プロファイル（`dev` / `prod`）による切り替えが適切か
