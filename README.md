# JDBI Binding Error Learning Project

JDBIの`UnableToCreateStatementException: Superfluous named parameters`エラーを再現・検証するプロジェクトです。

## エラーの概要

JDBIでは以下の条件が両方満たされた場合にバインディングエラーが発生します：

1. **SQLクエリに有効なプレースホルダーが存在しない**（`paramNames.isEmpty() = true`）
2. **バインドされたパラメータが存在する**（`!binding.isEmpty() = true`）

## プロジェクト構成

```
src/
├── main/kotlin/
│   └── QueryCondition.kt      # 検索条件モデル
└── test/kotlin/
    ├── JdbiBindingErrorTest.kt     # 基本バインディングエラー検証
    ├── JdbiAnnotationTest.kt       # アノテーション制御検証
    └── JdbiConfigurationTest.kt    # 設定制御検証
```

## テストケース

### 1. JdbiBindingErrorTest - 基本パターン検証

| テストケース | SQLプレースホルダー | バインド | 結果 |
|-------------|-----------------|--------|------|
| `should throw exception when binding parameter with no placeholders in SQL` | なし | あり | ❌ エラー |
| `should not throw exception when SQL has placeholders` | あり | あり（使用） | ✅ 成功 |
| `should NOT throw exception when SQL has placeholders and extra unused bindings` | あり | あり（一部未使用） | ✅ 成功 |
| `should NOT throw exception when SQL has no placeholders and no bindings` | なし | なし | ✅ 成功 |
| `should throw exception when SQL has no placeholders but has unused bindings` | なし | あり | ❌ エラー |

### 2. JdbiAnnotationTest - アノテーション制御検証

| テストケース | アノテーション | 結果 |
|-------------|----------------|------|
| `should NOT throw exception with @AllowUnusedBindings annotation` | `@AllowUnusedBindings` | ✅ 成功 |
| `should throw exception without @AllowUnusedBindings annotation` | なし | ❌ エラー |

### 3. JdbiConfigurationTest - 設定制御検証

| テストケース | 設定 | 結果 |
|-------------|------|------|
| `should NOT throw exception when setUnusedBindingAllowed is true` | `setUnusedBindingAllowed(true)` | ✅ 成功 |
| `should throw exception when setUnusedBindingAllowed is false (default)` | デフォルト(false) | ❌ エラー |
| `should throw exception when setUnusedBindingAllowed is explicitly set to false` | `setUnusedBindingAllowed(false)` | ❌ エラー |

## エラー回避方法

### 1. 根本的解決：不要なバインドを削除

```kotlin
// 修正前（エラー）
val query = handle.createQuery("SELECT * FROM table")
    .bind("unusedParam", value)  // ← 削除

// 修正後（成功）
val query = handle.createQuery("SELECT * FROM table")
```

### 2. アノテーションによる無効化

```kotlin
interface MyDao {
    @SqlQuery("SELECT * FROM table")
    @AllowUnusedBindings
    fun queryWithUnusedBinding(@Bind("unusedParam") value: String): List<Map<String, Any>>
}
```

### 3. 設定による無効化

```kotlin
handle.configure(SqlStatements::class.java) { 
    it.setUnusedBindingAllowed(true)
}
```

## 技術仕様

- **Kotlin**: 2.1.0
- **JDBI**: 3.41.3
- **Database**: H2 (インメモリ)
- **Build Tool**: Gradle 9.0
- **Java**: 19

## 実行方法

```bash
# 全テスト実行
./gradlew test

# 特定のテストクラス実行
./gradlew test --tests "*BindingError*"
./gradlew test --tests "*Annotation*" 
./gradlew test --tests "*Configuration*"
```

## 依存関係

```kotlin
dependencies {
    implementation("org.jdbi:jdbi3-core:3.41.3")
    implementation("org.jdbi:jdbi3-kotlin:3.41.3")
    implementation("org.jdbi:jdbi3-kotlin-sqlobject:3.41.3")
    implementation("org.jdbi:jdbi3-sqlobject:3.41.3")
    implementation("com.h2database:h2:2.2.224")
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}
```

## 重要なポイント

1. **MapMapperの登録が必要**: SQL Objectで`List<Map<String, Any>>`を返す場合、`@RegisterRowMapper(MapMapper::class)`が必要
2. **Early Returnの危険性**: 動的クエリ構築で条件によってプレースホルダーが全く存在しない場合に発生しやすい
3. **JDBIの設計思想**: 未使用バインドはバグの可能性が高いため、デフォルトでエラーとしている