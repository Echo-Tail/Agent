package cafe.snails.ecomagents.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Applies idempotent schema adjustments that Hibernate ddl-auto cannot safely infer. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageSuperResolutionSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                ALTER TABLE IF EXISTS image_super_resolution_jobs
                ALTER COLUMN source_record_id DROP NOT NULL
                """);
        jdbcTemplate.execute("""
                ALTER TABLE IF EXISTS image_generation_records
                ALTER COLUMN mode TYPE varchar(20)
                """);
        log.info("Image super-resolution schema migration applied");
    }
}