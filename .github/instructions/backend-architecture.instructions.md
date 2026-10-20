---
applyTo: "**/*.java"
---

# アーキテクチャ/レイヤー構成レビュー観点

## 4層構成

Javaバックエンドコードは以下の4層構成を採用する：

| 層 | パッケージ名 | 責務 |
|---|---|---|
| Presentation | `presentation` | Controller（REST/Kafka Listener/Scheduler）、リクエスト/レスポンス型 |
| Application | `application` | ユースケース、アプリケーションサービス、トランザクション境界、認可制御 |
| Domain | `domain` | Entity、ValueObject、Aggregate、DomainService、Repositoryインターフェース |
| Infrastructure | `infrastructure` | Repository実装（PostgreSQL）、DAO、外部API呼び出し |

例外的に `config`（横断的設定）が各層を参照することは許容する。

## 依存ルール（厳守）
- `domain` は他のいかなる層にも依存してはならない（純粋なビジネスロジック）
- 依存方向: `presentation → application → domain ← infrastructure`
- Repository のインターフェースは `domain` に定義し、実装は `infrastructure` に置く（依存性逆転）
- 層を跨ぐ逆方向の依存は絶対に許容しない

## Presentation層
- Controller、KafkaListener、Scheduler 等、システム外部からのトリガーを受け取る機能群を配置する
- 外部からの入力を受け取り、Application層に処理を委譲する

## Application層
- 複数のDomainServiceやEntityを調整してユースケースを実行する
- トランザクション境界はこの層で管理する
- 認可制御はこの層で管理する
- Repositoryインターフェースを介してEntityの取得・永続化を行う
- サービスクラスの命名は、アプリケーションサービスかドメインサービスか判別できるようにする
- メソッド命名はユースケースを表現する（`recordBeginWork`、`approveRequest` 等）

## Domain層
- Entity、Aggregate、ValueObject、DomainService、Repositoryインターフェースを配置する
- ビジネスロジックはDomainServiceまたはAggregate/Entityのメソッドとして表現する
- Aggregateはデータの一貫性と整合性を保持するエントリーポイントを提供する
- ValueObjectは不変であり、ビジネスの属性を表現する
- Repositoryインターフェースは集約やEntityの永続化と再構成のための抽象化層を提供する
- record型Entityの部分更新は専用メソッドで表現する（イミュータブル性を保ちつつ変更意図を明示する）

```java
// 例: Entityの部分更新メソッド
public Attendance updateBeginWork(LocalDateTime newBeginWork) {
    return new Attendance(id(), employeeId(), newBeginWork, finishWork());
}
```

## Infrastructure層
- Domain層で定義されたRepositoryインターフェースの具体的な実装を提供する
- DAO（データアクセスオブジェクト）によるデータベース操作の抽象化を行う
- 外部API呼び出し等、技術的詳細の実装もこの層に配置する
