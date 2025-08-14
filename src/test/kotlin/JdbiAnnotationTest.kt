import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.UnableToCreateStatementException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.jdbi.v3.sqlobject.customizer.AllowUnusedBindings
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import java.util.UUID

class JdbiAnnotationTest {
    
    @RegisterRowMapper(org.jdbi.v3.core.mapper.MapMapper::class)
    interface UfoQueries {
        @SqlQuery("SELECT * FROM ufo_sightings")
        @AllowUnusedBindings
        fun getAllWithUnusedBinding(@Bind("unusedParam") value: String): List<Map<String, Any>>
        
        @SqlQuery("SELECT * FROM ufo_sightings") 
        fun getAllWithoutAnnotation(@Bind("unusedParam") value: String): List<Map<String, Any>>
    }
    
    private lateinit var jdbi: Jdbi
    
    @BeforeEach
    fun setup() {
        jdbi = Jdbi.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
        jdbi.installPlugin(org.jdbi.v3.sqlobject.SqlObjectPlugin())
        
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
    fun `should NOT throw exception with @AllowUnusedBindings annotation`() {
        assertDoesNotThrow {
            val queries = jdbi.onDemand(UfoQueries::class.java)
            
            // @AllowUnusedBindingsにより例外なし
            queries.getAllWithUnusedBinding("unused value")
        }
    }
    
    @Test 
    fun `should throw exception without @AllowUnusedBindings annotation`() {
        val exception = assertThrows<Exception> {
            val queries = jdbi.onDemand(UfoQueries::class.java)
            
            // アノテーションなしで例外発生
            queries.getAllWithoutAnnotation("unused value")
        }
        
        println("Annotation test exception: ${exception.message}")
        assert(exception.message?.contains("Superfluous") == true)
    }
}