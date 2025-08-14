import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.UnableToCreateStatementException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import java.util.UUID

class JdbiBindingErrorTest {
    
    private lateinit var jdbi: Jdbi
    private val userId = UUID.randomUUID()
    
    @BeforeEach
    fun setup() {
        jdbi = Jdbi.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
        
        jdbi.useHandle<Exception> { handle ->
            handle.execute("""
                CREATE TABLE IF NOT EXISTS ufo_sightings (
                    id BIGINT PRIMARY KEY,
                    witness_id UUID,
                    location_id UUID,
                    shape VARCHAR(255)
                )
            """.trimIndent())
        }
    }
    
    @Test
    fun `should throw exception when binding parameter with no placeholders in SQL`() {
        val exception = assertThrows<UnableToCreateStatementException> {
            executeQuery(SearchScope.WORLDWIDE)
        }
        
        println("Exception: ${exception.message}")
        assert(exception.message?.contains("Superfluous named parameters") == true)
    }
    
    @Test
    fun `should not throw exception when SQL has placeholders`() {
        assertDoesNotThrow {
            executeQuery(SearchScope.COUNTRY_ONLY)
        }
    }
    
    @Test
    fun `should NOT throw exception when SQL has placeholders and extra unused bindings`() {
        assertDoesNotThrow {
            jdbi.useHandle<Exception> { handle ->
                val sql = "SELECT * FROM ufo_sightings WHERE witness_id = :witnessId"
                
                // SQLにあるプレースホルダー + 余分な未使用バインド
                handle.createQuery(sql)
                    .bind("witnessId", userId.toString())
                    .bind("extraParam1", "unused") // 余分
                    .bind("extraParam2", 123)      // 余分
                    .mapToMap().list()
            }
        }
    }
    
    @Test
    fun `should NOT throw exception when SQL has no placeholders and no bindings`() {
        assertDoesNotThrow {
            jdbi.useHandle<Exception> { handle ->
                val sql = "SELECT * FROM ufo_sightings"
                
                // プレースホルダーなし + バインドなし
                handle.createQuery(sql)
                    .mapToMap().list()
            }
        }
    }
    
    @Test
    fun `should throw exception when SQL has no placeholders but has unused bindings`() {
        val exception = assertThrows<UnableToCreateStatementException> {
            jdbi.useHandle<Exception> { handle ->
                val sql = "SELECT * FROM ufo_sightings"
                
                // プレースホルダーなし + 未使用バインド → エラー
                handle.createQuery(sql)
                    .bind("unusedParam", "value")
                    .mapToMap().list()
            }
        }
        
        println("Exception: ${exception.message}")
        assert(exception.message?.contains("Superfluous named parameters") == true)
    }
    
    
    private fun executeQuery(scope: SearchScope) {
        jdbi.useHandle<Exception> { handle ->
            val condition = buildCondition(scope)
            val sql = buildSql(condition)
            
            // 問題: このパラメータが常にバインドされるが使われない場合がある
            val query = handle.createQuery(sql)
                .bind("userId", userId.toString())
                .applyCondition(condition)
            
            query.mapToMap().list()
        }
    }
    
    private fun buildCondition(scope: SearchScope): QueryCondition {
        return when (scope) {
            SearchScope.COUNTRY_ONLY -> QueryCondition(
                conditions = listOf("(witness_id = :witnessId OR location_id = :locationId)"),
                parameters = mapOf(
                    "witnessId" to userId.toString(),
                    "locationId" to userId.toString()
                )
            )
            SearchScope.WORLDWIDE -> QueryCondition()
        }
    }
    
    private fun buildSql(condition: QueryCondition): String {
        val base = "SELECT * FROM ufo_sightings"
        return if (condition.conditions.isNotEmpty()) {
            "$base WHERE ${condition.conditions.joinToString(" AND ")}"
        } else {
            base
        }
    }
    
    private fun org.jdbi.v3.core.statement.Query.applyCondition(condition: QueryCondition): org.jdbi.v3.core.statement.Query {
        condition.parameters.forEach { (key, value) ->
            this.bind(key, value)
        }
        return this
    }
}