# JDBI『Superfluous named parameters』エラーの原因と対処法

## はじめに

JDBIで動的クエリを書く際に、以下のようなコードで`UnableToCreateStatementException: Superfluous named parameters`エラーが発生することがあります。

```kotlin
val sql = if (scope == SearchScope.WORLDWIDE) {
    "SELECT * FROM ufo_sightings"
} else {
    "SELECT * FROM ufo_sightings WHERE location_id = :locationId"
}

val query = handle.createQuery(sql)
if (scope == SearchScope.COUNTRY_ONLY) {
    query.bind("locationId", locationId)
}
query.bind("witnessId", witnessId) // 常に未使用だが、WORLDWIDE時のみエラー
```

このエラーの挙動が面白かったので、検証してみました。この記事では、エラーの原因と対処法を解説します。

## エラーの正体

`UnableToCreateStatementException: Superfluous named parameters`エラーは、以下の条件が両方満たされた時に発生します：

1. **SQLクエリにプレースホルダーが存在しない**
2. **バインドされたパラメータが存在する**

JDBIの実際のソースコード（`ArgumentBinder.java`）を見ると、エラー判定は以下のように書かれています：

```java
void bindNamedCheck(Binding binding, List<String> paramNames) {
    // パラメータが提供されているが、何も宣言されていない状態
    boolean argumentsProvidedButNoneDeclared = paramNames.isEmpty() && !binding.isEmpty();
    
    if (argumentsProvidedButNoneDeclared && !ctx.getConfig(SqlStatements.class).isUnusedBindingAllowed()) {
        throw new UnableToCreateStatementException(format(
                "Superfluous named parameters provided while the query "
                        + "declares none: '%s'. This check may be disabled by calling "
                        + "getConfig(SqlStatements.class).setUnusedBindingAllowed(true) "
                        + "or using @AllowUnusedBindings in SQL object.", binding), ctx);
    }
}
```

つまり：
- `paramNames.isEmpty()`: SQLにプレースホルダーがない
- `!binding.isEmpty()`: バインドパラメータがある
- `!isUnusedBindingAllowed()`: 未使用バインドが許可されていない

この3つの条件がすべて満たされた時にエラーが発生します。

特に動的クエリ構築で条件分岐がある場合によく発生します。

このケースでは`WORLDWIDE`の時、SQLにプレースホルダーがないのに`witnessId`をバインドしているため、JDBIが「使われないパラメータがある」と判断してエラーを投げます。

エラーメッセージからも分かるように、JDBIはデフォルトで未使用のバインドパラメータをエラーとして扱います。

## 実証実験

実際にテストコードを書いて、どのパターンでエラーが発生するかを検証してみました：

| SQLプレースホルダー | バインド | 結果 | 理由 |
|------------------|--------|------|------|
| なし | なし | ✅ 成功 | 両方なしは問題なし |
| なし | あり | ❌ **エラー** | 今回の問題パターン |
| あり | あり（使用） | ✅ 成功 | 正常なパターン |
| あり | あり（一部未使用） | ✅ 成功 | プレースホルダーがあれば余分バインドOK |

```kotlin
@Test
fun `should throw exception when binding parameter with no placeholders in SQL`() {
    val exception = assertThrows<UnableToCreateStatementException> {
        jdbi.useHandle<Exception> { handle ->
            // WORLDWIDE: プレースホルダーなし + バインドあり = エラー
            val sql = "SELECT * FROM ufo_sightings"
            val query = handle.createQuery(sql)
            query.bind("witnessId", witnessId)
            query.mapToMap().list()
        }
    }
    
    assert(exception.message?.contains("Superfluous named parameters") == true)
}

@Test
fun `should not throw exception when SQL has placeholders`() {
    assertDoesNotThrow {
        jdbi.useHandle<Exception> { handle ->
            // COUNTRY_ONLY: プレースホルダーあり = 成功
            val sql = "SELECT * FROM ufo_sightings WHERE location_id = :locationId"
            val query = handle.createQuery(sql)
            query.bind("locationId", locationId)
            query.bind("witnessId", witnessId) // 未使用だが問題なし
            query.mapToMap().list()
        }
    }
}
```

**意外な発見**：SQLにプレースホルダーが1つでもあれば、余分な未使用バインドがあってもエラーになりません。JDBIは「プレースホルダーが全くないSQLに対してバインドがある」ケースのみをエラーとして扱います。

## 3つの対処法

### 1. 【推奨】不要なバインドを削除

根本的な解決方法です。条件に応じてバインドを動的に行います：

```kotlin
// Before: エラー発生
val query = handle.createQuery(sql)
    .bind("witnessId", witnessId) // 不要なバインド

// After: 成功  
val query = handle.createQuery(sql)
// witnessIdのバインドを削除
```


### 2. @AllowUnusedBindingsアノテーション

SQL Objectを使用している場合、アノテーションで局所的に対処できます：

```kotlin
@RegisterRowMapper(MapMapper::class)
interface UfoQueries {
    @SqlQuery("SELECT * FROM ufo_sightings")
    @AllowUnusedBindings
    fun getAllWithUnusedBinding(@Bind("witnessId") witnessId: String): List<Map<String, Any>>
}
```

### 3. 設定で許可

`setUnusedBindingAllowed(true)`で一括対処：

```kotlin
jdbi.useHandle<Exception> { handle ->
    // 設定でエラーを無効化
    handle.configure(SqlStatements::class.java) { 
        it.setUnusedBindingAllowed(true)
    }
    
    val sql = "SELECT * FROM ufo_sightings"
    // 未使用バインドがあってもエラーにならない
    handle.createQuery(sql)
        .bind("witnessId", witnessId)
        .mapToMap().list()
}
```

## まとめ

`Superfluous named parameters`エラーの本質は、**動的クエリでのプレースホルダーとバインドパラメータのミスマッチ**です。

**対処法の優先順位**：
1. **根本的解決**：不要なバインドを削除（新規開発・リファクタリング時）
2. **アノテーション**：局所的対処（既存システムの緊急対処）
3. **設定変更**：一括対処（レガシーシステムの暫定対処）

動的クエリを構築する際は、プレースホルダーとバインドの対応を常に意識することが重要です。JDBIの設計思想を理解して、より堅牢なコードを書いていきましょう。

## 参考

検証用のサンプルコードは以下で公開しています：
- テストケース：JdbiBindingErrorTest.kt
- アノテーション検証：JdbiAnnotationTest.kt  
- 設定検証：JdbiConfigurationTest.kt
