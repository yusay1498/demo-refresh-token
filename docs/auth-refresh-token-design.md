# LDAPログインAPI Refresh Token 要件定義・詳細設計

- 作成日: 2026-10-20
- 文書状態: 初版。要確認事項を確定後、実装前に更新する
- 対象: `api` モジュールの認証APIと、それを利用するブラウザフロントエンド

## 1. 目的

LDAPログイン後に発行するAccess tokenの有効期間が切れた際、ユーザーに再入力を求めずに認証を継続できるようにする。Refresh tokenをサーバー側で管理し、ログアウト、tokenローテーション、再利用検知を可能にする。

## 2. 現行構成

- `POST /auth/signin` がLDAPのユーザー名・パスワードを認証し、JWTを1つ発行する。
- 成功レスポンスは `token`、`username`、`authorities` を持つ。既存クライアントとの互換性のため、このフィールド名と意味を維持する。
- JWTにはissuer、subject、issued-at、expires-at、JWT ID、authoritiesが含まれる。
- Composeの `APP.JWT.TTL` は `15m`。JWT設定プロパティは `app.jwt.ttl`。
- `/auth/me` はBearer JWTを利用する。Security設定はstatelessだが、URLごとの認可ルールが明示されていないため、実装時に公開/認証必須ルートを明示する。
- PostgreSQL JDBC依存とテスト用Testcontainers設定はある。一方、通常のCompose環境にはPostgreSQLサービスと接続設定がない。
- 現在のLDAPサンプルはユーザーDNとグループ所属を定義しているが、アカウント有効/無効を示す属性は確認できない。

## 3. 要件

### 3.1 機能要件

1. LDAP認証に成功したとき、既存のAccess JWTに加えてRefresh tokenを発行する。
2. Refresh tokenを使ってAccess JWTと新しいRefresh tokenを発行する。各Refresh tokenは一度だけ利用可能とする。
3. Refresh tokenの更新時にLDAP上のユーザー状態と権限を再確認し、最新権限をAccess JWTに反映する。
4. 現在のブラウザセッションのログアウトと、そのユーザーの全ブラウザセッションのログアウトを提供する。
5. 期限切れ、失効済み、形式不正のtokenでは更新を拒否する。
6. 使用済みRefresh tokenの再利用を検知した場合、そのtoken family（ログインセッション）を失効させる。
7. 同一ブラウザの複数タブで認証状態を共有する。フロントエンドはRefreshをブラウザ単位で排他し、重複更新を避ける。

### 3.2 非機能・セキュリティ要件

- フロントエンドはAccess/Refresh tokenをLocalStorageに保存する。ログアウト時に本アプリが管理するtokenキーを削除する。
- tokenはHTTPSで送受信し、URL、アクセスログ、例外ログ、分析イベントに含めない。
- Refresh tokenは十分な暗号学的乱数から生成した不透明tokenとし、DBにはハッシュのみ保存する。
- Refresh tokenをcookieで送受信しない。Access tokenは `Authorization: Bearer` headerで送信し、Refresh tokenはJSON bodyで送信する。認証にcookieを用いないため、cookieベースのCSRF対策には依存しない。
- LocalStorageはJavaScriptから読み取れるため、XSSが成立するとtoken窃取の可能性がある。出力エスケープ、CSP、依存関係管理、不要な第三者スクリプトの排除を実施する。サニタイズだけでXSSリスクがなくなるとは扱わない。
- 本番CORS originはフロントエンドのoriginに限定する。cookie credentialsは有効化しない。
- DBまたはLDAPが利用できない場合、Refreshを許可せず失敗させる（fail closed）。
- 認証失敗レスポンスから、ユーザーの存在有無やtokenの具体的な失効理由を不必要に漏らさない。

## 4. Tokenポリシー

| 項目 | 方針 |
|---|---|
| Access token | 署名付きJWT。現行の `token` フィールドを継続利用 |
| Access token有効期間 | 15分を維持する案 |
| Refresh token | 256-bit相当以上のランダムな不透明token |
| Refresh token保管 | SHA-256 hashをPostgreSQLに保存。平文は発行レスポンス以外に保存しない |
| Refresh rotation | 成功のたびに旧tokenを使用済みにし、新tokenを発行 |
| idle timeout | 最終利用から8時間を仮案とする |
| absolute timeout | 初回発行から最大24時間を仮案とする |
| 保存場所 | フロントエンドのLocalStorage。複数タブ間で共有 |

有効期間は「勤務時間内のみ」という要望に合わせた推奨仮案であり、数値は業務責任者の最終確認対象とする。更新後の有効期限は `min(now + idle timeout, family absolute expiry)` とする。

Access JWTは自己完結型のため、logoutやLDAPアカウント無効化後も、発行済みJWTが最大15分有効な場合がある。この猶予は許容する。即時失効が必要になった場合は、JWT denylist、ユーザー単位のtoken version照会などを別途設計する。

## 5. API設計

すべてのtoken交換APIはHTTPSを必須とする。JSONのContent-Typeは `application/json` とする。

### 5.1 `POST /auth/signin`

LDAP資格情報を認証し、Access/Refresh tokenを発行する。

Request:

```json
{
  "username": "cid",
  "password": "..."
}
```

Response `200 OK`（既存項目は維持し、新規項目を追加）:

```json
{
  "token": "<access-jwt>",
  "username": "cid",
  "authorities": ["ROLE_USER"],
  "refreshToken": "<opaque-refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

`expiresIn` はAccess tokenの残存秒数。資格情報不正は `401 Unauthorized` とし、詳細な失敗理由は返さない。

### 5.2 `POST /auth/refresh`

Refresh tokenを検証し、tokenをローテーションする。

Request:

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

Response `200 OK`:

```json
{
  "token": "<new-access-jwt>",
  "refreshToken": "<new-opaque-refresh-token>",
  "username": "cid",
  "authorities": ["ROLE_USER"],
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

- 旧Refresh tokenは直ちに再利用不可とする。
- tokenが期限切れ・存在しない・失効済みの場合は `401 Unauthorized`。
- 使用済みtokenの再送を検出した場合、そのfamily全体を失効させ、`401 Unauthorized` を返す。
- LDAPに接続できない場合は `503 Service Unavailable` 相当とし、tokenをローテーションしない。
- フロントエンドはRefresh処理を単一化する。複数タブ間の排他方式（Web Locks API等）は対象ブラウザを確認して確定する。

### 5.3 `POST /auth/logout`

Request bodyのRefresh tokenが属するfamilyを失効させる。正常・既に失効済みの場合とも `204 No Content` を返し、tokenの有効性を外部から判別できないようにする。フロントエンドはtokenをLocalStorageから削除する。

```json
{
  "refreshToken": "<opaque-refresh-token>"
}
```

### 5.4 `POST /auth/logout-all`

有効なAccess JWTのsubjectに紐づく全Refresh token familyを失効させる。Access JWTが期限切れの場合は認証できないため `401 Unauthorized`。成功時は `204 No Content`。

### 5.5 既存API

- `GET /auth/me` は引き続きAccess JWTを要求する。
- サインイン、Refresh、単一logoutは未認証で呼び出せる。Refresh/logoutはbodyのtokenを必須とする。
- `logout-all`、`/auth/me`、その他の保護対象APIは認証必須とする。
- Security設定でルート単位の許可/認証ルールを明示し、意図しない匿名アクセスがないことをテストする。

## 6. Refresh tokenデータ設計

PostgreSQLにtoken単位の履歴と、安定したロック対象となるfamily親レコードを保持する。LDAPユーザーは外部管理のため、ユーザーテーブルへの外部キーは設けない。

#### `refresh_token_family`（family親レコード）

| カラム | 型案 | 制約・用途 |
|---|---|---|
| `id` | `VARCHAR(36)` | PK、family ID |
| `username` | `VARCHAR(255)` | LDAP subject。logout-allの検索対象 |
| `created_at` | `TIMESTAMPTZ` | family作成時刻 |
| `absolute_expires_at` | `TIMESTAMPTZ` | familyの絶対期限 |
| `revoked_at` | `TIMESTAMPTZ` | family失効時刻。未失効ならNULL |

この親レコードはtoken rotation後も削除・置換せず、familyの排他制御に使う。各token recordの `family_id` は親レコードを参照する外部キーとする。Signinで新しいfamilyを作る場合も、同じユーザー単位の排他制御に従う。

| カラム | 型案 | 制約・用途 |
|---|---|---|
| `id` | `VARCHAR(36)` | PK、UUID文字列 |
| `family_id` | `VARCHAR(36)` | `refresh_token_family.id` へのFK。index対象 |
| `username` | `VARCHAR(255)` | LDAP subject。logout-allの検索対象 |
| `token_hash` | `CHAR(64)` | SHA-256 hex。NOT NULL、UNIQUE |
| `issued_at` | `TIMESTAMPTZ` | 発行時刻 |
| `expires_at` | `TIMESTAMPTZ` | idle timeoutを反映した期限 |
| `consumed_at` | `TIMESTAMPTZ` | rotationで使用済みになった時刻。未使用ならNULL |
| `revoked_at` | `TIMESTAMPTZ` | token単体の失効時刻。未失効ならNULL |
| `replaced_by_id` | `VARCHAR(36)` | rotation後のtoken record ID。未更新ならNULL |
| `created_at` | `TIMESTAMPTZ` | レコード作成時刻 |

必要なindexはtoken hashのunique index、`family_id`、`username`。有効状態は未消費・未失効・期限内で判定する。消費済みrecordは再利用検知に必要な期間保持し、familyの絶対期限を過ぎたrecordのcleanup方針を運用要件として定める。

### 6.1 Family単位の排他性

token行だけのロックでは、family失効UPDATEが実行時点でまだ存在しない後継recordを対象にできない。特にPostgreSQLのREAD COMMITTEDでは、失効UPDATEがRefresh transactionを待った後も、そのUPDATE開始時のsnapshotに含まれなかった後継recordを取りこぼす可能性がある。このため、token行ロックだけではRefreshによる後継record追加とfamily全体の失効を直列化できない。

Refresh、使用済みtokenの再利用検知、単一logout、logout-allは、共通の安定したfamily親レコードを排他ロックし、family内のtoken状態変更と親レコードの失効をそのトランザクション内で行う。ロックは後継record追加またはfamily失効のcommitまで保持する。

Refresh/reuse/logoutの手順:

1. `token_hash` から対象tokenの `family_id` をロックなしで特定する。この検索結果はfamilyの特定だけに使い、tokenの有効性判定には使わない。
2. トランザクションを開始し、対応する `refresh_token_family` 親レコードを `SELECT ... FOR UPDATE` でロックする。
3. ロック取得後に親レコードと対象token recordを再読込し、失効・消費・期限を検証する。ここでの読み直しが競合処理のcommit後の状態を確認する。
4. Refreshなら旧tokenの消費と後継token recordの追加を、reuse/logoutならfamily失効と必要なtoken record更新を実施する。
5. 変更をcommitし、commit成功後にのみRefreshの新tokenをクライアントへ返す。

使用済みtokenの再送は、familyロック取得後の再読込でconsumed状態を確認してからreuseと判定し、そのfamily親レコードを失効させる。これにより、競合Refreshが先にcommitした場合は後継recordを含むfamily全体を失効し、reuse検知が先にcommitした場合は後続Refreshが失効済みfamilyを読み取って拒否する。

logout-allは `username` に紐づくfamily親レコードをID順など一貫した順序でロックし、取得後に対象familyを再検索・再確認してから全familyを失効させる。logout-allと同時にSigninが新しいfamilyを作成して対象から漏れることを防ぐため、Signinのfamily作成とlogout-allは、同一ユーザー単位の排他ロックを共通して取得する。ロック順序は「ユーザー単位ロック、family親ロック」の順に統一し、Signinはfamily作成後にユーザー単位ロックを解放する。logout-allは全familyの失効をcommitするまで両方のロックを保持する。

外部キーによる親レコードの暗黙ロックだけに依存せず、アプリケーションがfamily親を明示的にロックする。どの処理もtoken行を先にロックしてからfamily親をロックする逆順を取らない。フロントエンドのタブ間Refresh排他も維持するが、これはUX上の重複要求を抑える補助であり、サーバー側の排他制御の代替にはしない。

## 7. 認証フロー

```mermaid
sequenceDiagram
    participant Browser as Browser SPA
    participant API as Auth API
    participant LDAP as LDAP
    participant DB as PostgreSQL
    Browser->>API: POST /auth/signin (username/password)
    API->>LDAP: bind認証・authorities取得
    LDAP-->>API: 認証結果・authorities
    API->>DB: Refresh token hashを保存
    API-->>Browser: Access JWT + Refresh token
    Browser->>API: API request (Authorization: Bearer)
    API-->>Browser: API response
    Browser->>API: POST /auth/refresh (refreshToken)
    API->>DB: family親をロックしtoken状態を再読込
    API->>LDAP: user状態・最新authoritiesを照会
    LDAP-->>API: user状態・authorities
    API->>DB: 旧token消費・新token保存・transaction commit
    API-->>Browser: 新Access JWT + 新Refresh token
```

## 8. エラー・運用

- 認証・Refresh拒否: `401 Unauthorized`。内部理由やtoken値をログ出力しない。
- 不正なJSON、必須項目欠落: `400 Bad Request`。
- LDAP/DBの一時障害: `503 Service Unavailable`。Refreshはfail closed。
- Refresh tokenをURL query parameterに指定する形式は禁止する。
- Signinにはrate limitとブルートフォース監視を適用する。適用箇所（API gateway、proxy、アプリ）はデプロイ構成に合わせて決定する。
- JWT secret、LDAP管理資格情報、DB資格情報はComposeの固定値を本番に流用せず、環境変数またはsecret managerから供給する。
- 本番LDAP接続はTLS（LDAPSまたはStartTLS）を利用する。
- PostgreSQL接続設定、volume、backup/restore、schema migration方式をCompose/デプロイ設計に追加する。migrationツールは現行依存にないため、導入方式は実装計画で決定する。

## 9. 受け入れ条件・テスト観点

1. 正しいLDAP資格情報でsigninすると、既存3フィールドを保ったレスポンスにRefresh tokenが追加される。
2. 誤った資格情報ではtokenを発行せず、401を返す。
3. 有効なRefresh tokenで新しいAccess/Refresh tokenを発行し、旧Refresh tokenは再利用できない。
4. 期限切れ、失効済み、未知のRefresh tokenは拒否される。
5. consumed tokenの再利用で同familyの全tokenが失効し、後続refreshも拒否される。
6. 同一tokenへの並行refreshで複数の有効な後継tokenを作らない。
7. Refreshとreuse検知が競合しても、両処理の完了後に有効な後継tokenが残らない。
8. Refreshと単一logoutが競合しても、両処理の完了後に対象family内の有効なtokenが残らない。
9. Refreshとlogout-allが競合しても、両処理の完了後に対象ユーザーのfamily内に有効な後継tokenが残らない。
10. 単一logoutは対象familyだけ、logout-allは対象ユーザーの全familyを失効する。logout-allとSigninの競合で新規familyが失効対象から漏れない。
11. Refresh時のLDAP権限変更が新Access JWTとレスポンスに反映される。
12. LDAPユーザー無効化時にRefreshが拒否される（無効化属性・判定ルール確定後に検証）。
13. Logout/LDAP無効化後も既発行Access JWTは最長15分有効となり得る。
14. token平文がDB・ログ・URLに保存/出力されない。
15. signin/refresh/logoutのアクセス制御、CORS preflight、HTTPS前提のデプロイ設定を確認する。
16. PostgreSQLを使うrepository/integration testと、APIの正常系・異常系・境界値テストを用意する。

## 10. 要確認事項

1. Access 15分、Refresh idle 8時間、absolute 24時間を最終値として承認するか。
2. LDAPのユーザー無効化を何で判定するか。現行LDIFとプロパティには有効/無効属性がなく、削除、ロック属性、別属性のいずれが運用ルールか確認が必要。
3. フロントエンドのoriginと対象ブラウザ。複数タブでのRefresh排他にWeb Locks API等を利用できるか。
4. PostgreSQLの実行環境、volume/backup要件、schema migration方式。
5. logout-allを呼ぶためのAccess JWTが期限切れの場合、再ログインを求める挙動でよいか（本書ではその前提）。
6. LDAP/DB障害時にRefreshを503で失敗させ、再試行をフロント側で制御する方針でよいか。

## 参考

- [RFC 9700: OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700.html)
- [OWASP HTML5 Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)
- [Spring Security: OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
