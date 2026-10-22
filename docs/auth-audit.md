# 認証監査ログとNew Relicでの運用

## 目的と前提

トークンの不正利用の兆候を発見し、どのログインセッションで発生し、失効できたかを調査できるようにする。
ログの経路は、アプリのJSON標準出力 → ECSの既存ログ転送基盤 → New Relicの`Log`とする。
アプリからNew Relic Custom Eventを別送しない。Java Agentによるログ転送は無効のままとし、二重転送を避ける。

再利用検知は盗難の確証ではない。正規クライアントの二重送信や、応答を受け取れなかった後の再送でも発生する。
有効なAccess tokenを攻撃者だけが使うケースや、Refresh tokenを本人と競合せず使うケースは、この監査では検知できない。

## パッケージ構成

kt-member / kt-terms / kt-world-memberの`aop.LoggingAdvice`を参考に、横断的なログ出力を`aop`へ配置する。

```text
api/src/main/java/com/yusay/api/
├── application/
│   ├── AuthApplicationService.java
│   └── event/AuthAuditEvent.java
└── aop/
    └── AuthAuditLogger.java
```

- Application層は監査すべき事実を判断し、`ApplicationEventPublisher`でイベントを発行する。
- `aop.AuthAuditLogger`は`@EventListener`で同期的に受信し、MDCへの項目付与とログ出力を担当する。
- 名前と配置はktの横断ログに揃えるが、メソッド引数の一括出力や`@Before`による成功推定は行わない。認証情報の漏えいと、保存前の成功記録を避けるためである。
- Domain層はログ基盤へ依存しない。Application層も`aop`を直接参照しない。
- Logback設定はktと同様に、`NewRelicAsyncAppender` → `ConsoleAppender` + `NewRelicEncoder`とする。

## イベント一覧

| event.action | タイミング | event.outcome | レベル |
|---|---|---|---|
| `sign_in_succeeded` | token発行・保存のトランザクションがコミットした後 | `success` | INFO |
| `sign_in_failed` | 認証情報またはアカウント状態により認証を拒否したとき | `rejected` | INFO |
| `session_revoked` | サインアウト・全セッション失効のコミット後 | `success` | INFO |
| `refresh_token_reuse_detected` | 使用済みRefresh tokenの再提示を検知したとき。失効保存より前 | `detected` | WARN |
| `reuse_revocation_completed` | 再利用検知に伴うトランザクション完了時 | `success` / `failure` / `unknown` | 成功はINFO、それ以外はWARN |

`sign_in_failed`は`BadCredentialsException`と`AccountStatusException`を対象とする。
LDAP接続・DB障害を認証情報拒否として数えない。ただし、基盤障害が必ず5xxになるわけではない。
LDAP接続障害が`InternalAuthenticationServiceException`として通知された場合、現行の`AuthRestController`は親型の`AuthenticationException`を捕捉して401を返す。
この例外は`sign_in_failed`の記録対象外でもあるため、HTTP 5xx監視と監査ログのログイン失敗件数だけでは検知できない。
一方、Controllerで個別に扱うDB接続・トランザクション開始失敗や、権限取得の`UserAuthoritiesUnavailableException`は503となる。
存在しないRefresh tokenでのサインアウトや、すでに失効したfamilyの再サインアウトでは、新たなfamily失効成功を記録しない。
全セッション失効は処理完了を記録するため、対象familyが0件でも1件の`session_revoked`を出す。

## 記録項目と機密情報

| 項目 | 内容 |
|---|---|
| `event.category` | 常に`authentication` |
| `event.action` | 上記の固定イベント名 |
| `event.outcome` | 検知・拒否・成功・失敗の区別 |
| `reason` | `authenticated` / `credentials_rejected` / `consumed_token_presented` / `token_reuse` / `sign_out` / `sign_out_all` |
| `session.family_id` | 対象のログインセッションのID。ログイン失敗・全セッション失効では省略 |
| `revocation.scope` | `none` / `family` / `all_families` |

既存のMDCは保存し、出力後に元へ戻す。トランザクション完了後のログには、イベント受信時のMDCを引き継ぐ。
trace IDはAgentなどが提供する情報を利用し、この実装では生成しない。サービス名・環境・ECS task情報は既存転送基盤で付与する想定である。

パスワード、token本体、tokenハッシュ、Authorizationヘッダー、リクエスト/レスポンス本文、例外の生メッセージは監査イベントへ渡さない。
username・IP・User-Agentも現時点では記録しない。本人特定や送信元別の検知が必要なら、仮名化識別子・個人情報の保存方針・信頼するproxyを先に定義する。
このため、現状のログイン失敗アラートは全体件数の監視に限られ、ユーザー別・IP別の攻撃分析は未対応である。
他のコンポーネントがMDCに機密情報を置かないことも前提となる。

## トランザクションとログの関係

通常の成功イベントはコミット後に記録する。ロールバックした処理を成功と記録しない。
再利用は検知時に即時記録し、DB更新前に完了コールバックを登録する。
失効保存に失敗してロールバックしても、検知ログと`reuse_revocation_completed`の`failure`が残る。
トランザクション結果が不明なら`unknown`とし、成功とは扱わない。
成功/完了コールバックは実トランザクションと同期処理が有効な場合のみ登録する。

再利用検知と失効完了は異なる事実であり、アラートでは再利用検知イベントだけを数える。
family IDとトレース情報で2つのイベントを関連付ける。

## New Relicの検索とアラート

以下は監査属性がトップレベルに取り込まれる場合の例である。
実環境のサービス・本番環境の条件を必ず追加する。ECSのパーサーがJSONをネストさせる場合は実際の属性名に合わせる。

### 再利用検知

```sql
SELECT count(*) FROM Log
WHERE `event.action` = 'refresh_token_reuse_detected'
```

初期案: 5分の評価窓で1件以上を調査用チャネルへ通知する。オンコール呼び出しの条件は通常の再送頻度を確認して決める。
通知文は「Refresh token再利用を検知」とし、盗難確定とは書かない。

### 検知後に失効できなかったケース

```sql
SELECT count(*) FROM Log
WHERE `event.action` = 'reuse_revocation_completed'
  AND `event.outcome` IN ('failure', 'unknown')
```

初期案: 1件以上で高優先度の調査対象とする。検知済みのセッション更新を停止できていない可能性がある。

### ログイン失敗

```sql
SELECT count(*) FROM Log
WHERE `event.action` = 'sign_in_failed'
```

全体の通常件数を測定して閾値を決める。単なる失敗増加を攻撃確定として扱わない。
LDAP・DBなどの障害アラートとは通知先と対応手順を区別する。

### LDAP接続障害の監視上の制約

サインイン時の`InternalAuthenticationServiceException`は401で処理されるため、5xx件数からLDAP接続障害の有無を判断しない。
また、401全体には通常の認証拒否も含まれるため、401の増加だけでLDAP障害と断定しない。

New Relic Java Agentが、Controllerで処理済みの例外をAPMエラーとして収集し、通知できるかを検証環境で確認する。
収集の可否はAgentの計装・エラー収集設定に依存するため、自動的に検知されるとは限らない。
収集されない場合は、認証基盤障害専用の構造化ログや、明示的なAPMエラー通知など、別の検知経路を設計・実装する必要がある。
現状ではその専用ログ・明示的な通知処理は未実装である。

## 通知後の対応

1. 同じ`session.family_id`の検知ログと失効結果を確認する。
2. 関連trace・APIエラーから、二重送信・タイムアウト・DB障害がなかったか調べる。
3. 必要に応じて影響ユーザーを安全な経路で特定し、セッション失効と再認証を案内する。
4. 複数familyでの発生や継続発生はセキュリティ対応へエスカレーションする。

失効するのはRefresh tokenのfamilyである。発行済みAccess tokenを即時無効化する実装ではない。
現行の15分TTLに加え、JWT検証の時刻許容差が適用される場合があるため、失効直後にアクセスできなくなるとは限らない。

## 本番導入チェックと限界

- `NEW_RELIC_LOG_EXTENSION_ADD_MDC=true`を設定する。現在のAPI Dockerfileでは設定済み。
- ローカルでAgentを付けずに動かす場合も、監査属性を出すにはMDC出力の設定が必要。
- 検証環境で再利用を発生させ、ECS経由でJSON属性が検索できることを確認する。
- アラートの評価窓・集約・通知先をNew Relic上で設定し、実際にテスト通知を受ける。
- 検証環境でLDAPへの接続を意図的に失敗させ、サインインのHTTPステータス、監査ログの記録対象、APMの例外収集と通知を確認する。401で処理されても別経路で障害通知が届くことを、本番導入前に確認する。
- token・パスワードが含まれず、二重転送がないことを確認する。
- 保存期間、ログ閲覧権限、転送失敗、非同期Appenderの破棄/キュー設定も確認する。

この実装は監視用ログであり、DBとログ配送の原子性や欠損ゼロを保証しない。
プロセスの強制終了や転送障害では失われる可能性がある。
厳密な監査証跡が必要なら、監査テーブル・transactional outbox・耐久性のある保管先を別途設計する。
New Relic上のアラート作成やECS転送設定の変更は、このリポジトリの実装には含まれない。

## 参考

- [New Relic: Java Logs in Context](https://docs.newrelic.com/docs/logs/logs-context/java-configure-logs-context-all/)
- [New Relic: NRQL Alert Conditions](https://docs.newrelic.com/docs/alerts/create-alert/create-alert-condition/create-nrql-alert-conditions/)
- [Spring Framework: Application Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Spring Security: InternalAuthenticationServiceException](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/authentication/InternalAuthenticationServiceException.html)
- [OWASP: Logging Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html)
- [RFC 9700: Refresh Token Protection](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)
