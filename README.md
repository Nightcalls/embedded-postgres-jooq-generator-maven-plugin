# Embedded Postgres jOOQ Generator Maven Plugin

A maven plugin that allows to generate jOOQ bindings inside embedded PostgreSQL instance with schema created by
Liquibase.

### Why not use existing embedded pg / liquibase / jooq codegen plugins?
We did. It was not good. Amount of configuration required is 5x more, we had issues with embedded pg state between goals.
TL;DR this one is simpler.

### How to use?
**Not published in public repositories yet**. If you need that - please create issue.

Example configuration:
```xml
<plugin>
    <groupId>io.github.nightcalls</groupId>
    <artifactId>embedded-postgres-jooq-generator-maven-plugin</artifactId>
    <version>1.0.0</version>

    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>

    <configuration>
        <!-- Path to your changelog file relative to module dir -->
        <liquibase.changeLogFile>src/main/resources/sql/changelogs.xml</liquibase.changeLogFile>
        <!-- jOOQ codegen config - see https://www.jooq.org/doc/latest/manual/code-generation/codegen-configuration/ -->
        <jooq.generator>
            <database>
                <includes>.*</includes>
            </database>
            <generate>
                <sequences>true</sequences>
            </generate>
            <target>
                <packageName>io.github.nightcalls.jooq</packageName>
                <directory>src/main/java</directory>
                <encoding>${project.build.sourceEncoding}</encoding>
            </target>
        </jooq.generator>
    </configuration>
</plugin>
```

**By default embedded postgres supports only amd64**, for other platforms add explicit dependencies:
```xml
<dependency>
    <groupId>io.zonky.test.postgres</groupId>
    <artifactId>embedded-postgres-binaries-linux-i386</artifactId>
</dependency>
```