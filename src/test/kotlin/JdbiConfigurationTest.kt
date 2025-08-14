import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.UnableToCreateStatementException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import java.util.UUID

class JdbiConfigurationTest {
    
    private lateinit var jdbi: Jdbi
    
    @BeforeEach
    fun setup() {
        jdbi = Jdbi.create("jdbc:h2:mem:config_test;DB_CLOSE_DELAY=-1")
        
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
    fun `should NOT throw exception when setUnusedBindingAllowed is true`() {
        assertDoesNotThrow {
            jdbi.useHandle<Exception> { handle ->
                // 設定でエラーを無効化
                handle.configure(org.jdbi.v3.core.statement.SqlStatements::class.java) { 
                    it.setUnusedBindingAllowed(true)
                }
                
                val sql = "SELECT * FROM ufo_sightings"
                
                // プレースホルダーなし + 未使用バインド → 設定により例外なし
                handle.createQuery(sql)
                    .bind("unusedParam", "value")
                    .mapToMap().list()
            }
        }
    }
    
    @Test
    fun `should throw exception when setUnusedBindingAllowed is false (default)`() {
        val exception = assertThrows<UnableToCreateStatementException> {
            jdbi.useHandle<Exception> { handle ->
                // デフォルト設定 (false) のまま
                val sql = "SELECT * FROM ufo_sightings"
                
                // プレースホルダーなし + 未使用バインド → エラー
                handle.createQuery(sql)
                    .bind("unusedParam", "value")
                    .mapToMap().list()
            }
        }
        
        println("Configuration test exception: ${exception.message}")
        assert(exception.message?.contains("Superfluous named parameters") == true)
    }
    
    @Test
    fun `should throw exception when setUnusedBindingAllowed is explicitly set to false`() {
        val exception = assertThrows<UnableToCreateStatementException> {
            jdbi.useHandle<Exception> { handle ->
                // 明示的にfalseに設定
                handle.configure(org.jdbi.v3.core.statement.SqlStatements::class.java) { 
                    it.setUnusedBindingAllowed(false)
                }
                
                val sql = "SELECT * FROM ufo_sightings"
                
                // プレースホルダーなし + 未使用バインド → エラー
                handle.createQuery(sql)
                    .bind("unusedParam", "value")
                    .mapToMap().list()
            }
        }
        
        println("Explicit false configuration test exception: ${exception.message}")
        assert(exception.message?.contains("Superfluous named parameters") == true)
    }
}