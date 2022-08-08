package io.github.nightcalls.embeddedpgjooqgenplugin;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jooq.codegen.GenerationTool;
import org.jooq.codegen.JavaGenerator;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.postgres.PostgresDatabase;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import static liquibase.database.DatabaseFactory.getInstance;

/**
 * Mojo that will:</br>
 * 1. Start embedded postgres instance.</br>
 * 2. Run liquibase on that instance.</br>
 * 3. Run jOOQ generator from current schema.</br>
 * 4. Cleanup.</br>
 * </br>
 * Configuration:</br>
 * 1. Liquibase: set liquibase.changelogFile to relative to working dir path to valid liquibase changelog file.
 * </br>
 * 2. jOOQ properties are specified in &lt;jooq.generator&gt;...&lt;/jooq.generator&gt;. See jooq docs for codegen info.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class EmbeddedPostgresJooqGeneratorMojo extends AbstractMojo {
    // These are the default Embedded Postgres creds, nothing wrong with using them here
    private static final String USER = "postgres";
    private static final String PASSWORD = "postgres";
    private static final String DB_NAME = "postgres";

    /**
     * Relative to working dir path to valid liquibase changelog file.
     * Example: src/main/properties/changelog.xml
     */
    @Parameter(alias = "liquibase.changeLogFile", property = "liquibase.changeLogFile", required = true)
    private String liquibaseChangeLogFile;

    @Parameter(alias = "jooq.generator", property = "jooq.generator", required = true)
    private Generator jooqGenerator;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Log log = getLog();

        final EmbeddedPostgres postgres = startEmbeddedPostgres(log);

        runLiquibase(log, postgres);
        generateJooq(log, postgres);

        try {
            postgres.close();
        } catch (IOException e) {
            throw new MojoFailureException("Failed to stop Embedded Postgres", e);
        }
    }

    private EmbeddedPostgres startEmbeddedPostgres(Log log) throws MojoExecutionException {
        EmbeddedPostgres postgres;
        try {
            postgres = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to start Embedded Postgres", e);
        }
        log.info("Started Embedded Postgres on port " + postgres.getPort());
        return postgres;
    }

    private void runLiquibase(Log log, EmbeddedPostgres postgres) throws MojoExecutionException {
        try (Connection connection = postgres.getPostgresDatabase().getConnection(USER, PASSWORD)) {
            try (Database database = getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))) {
                log.info("Configuring liquibase: " +
                        "\nchangeLogFile=" + liquibaseChangeLogFile);
                // Resources are searched for relative to working dir
                ResourceAccessor resourceAccessor = new FileSystemResourceAccessor(
                        mavenProject.getBasedir().getAbsoluteFile()
                );

                // No need to close - will be closed when database is closed
                Liquibase liquibase = new Liquibase(liquibaseChangeLogFile, resourceAccessor, database);
                liquibase.update(new Contexts());
                log.info("Liquibase ran successfully");
            }
        } catch (LiquibaseException | SQLException e) {
            throw new MojoExecutionException("Failed to run liquibase", e);
        }
    }

    private void generateJooq(Log log, EmbeddedPostgres postgres) throws MojoExecutionException {
        log.info("Configuring jOOQ");

        if (jooqGenerator.getName() == null) {
            // Force pg by default
            jooqGenerator.setName(JavaGenerator.class.getName());
        }

        if (jooqGenerator.getDatabase().getName() == null) {
            // Force pg by default
            jooqGenerator.getDatabase().setName(PostgresDatabase.class.getName());
        }

        File targetDirectory = new File(mavenProject.getBasedir(), jooqGenerator.getTarget().getDirectory());
        jooqGenerator.getTarget().setDirectory(targetDirectory.getAbsolutePath());

        Configuration configuration = new Configuration()
                // For some reason jooq doesn't provide a way to pass db connection directly
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.getJdbcUrl(USER, DB_NAME)))
                .withGenerator(jooqGenerator);

        try {
            GenerationTool.generate(configuration);
            log.info("Generated jOOQ to '" + jooqGenerator.getTarget().getDirectory() + "'");
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate jOOQ", e);
        }
    }
}
