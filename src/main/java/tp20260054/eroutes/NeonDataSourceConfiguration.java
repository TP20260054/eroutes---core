package tp20260054.eroutes;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Primary;

@Configuration
public class NeonDataSourceConfiguration {

	@Bean
	@Primary
	HikariDataSource dataSource(Environment environment) {
		String databaseUrl = environment.getProperty("DATABASE_URL");
		if (databaseUrl == null || databaseUrl.isBlank()) {
			databaseUrl = readLocalDatabaseUrl();
		}
		if (databaseUrl == null || databaseUrl.isBlank()) {
			throw new IllegalStateException("DATABASE_URL must be configured");
		}

		URI databaseUri = URI.create(databaseUrl);
		String userInfo = databaseUri.getRawUserInfo();
		if (userInfo == null || !userInfo.contains(":")) {
			throw new IllegalStateException("DATABASE_URL must include database credentials");
		}

		String[] credentials = userInfo.split(":", 2);
		String authority = databaseUri.getRawAuthority();
		String hostAuthority = authority.substring(authority.indexOf('@') + 1);
		String jdbcUrl = "jdbc:postgresql://" + hostAuthority + databaseUri.getRawPath();
		if (databaseUri.getRawQuery() != null) {
			jdbcUrl += "?" + databaseUri.getRawQuery();
		}

		return createDataSource(jdbcUrl, credentials);
	}

	@Bean(name = "flywayDataSource")
	@FlywayDataSource
	DataSource flywayDataSource(Environment environment) {
		String databaseUrl = environment.getProperty("DATABASE_URL_UNPOOLED");
		if (databaseUrl == null || databaseUrl.isBlank()) {
			databaseUrl = readLocalDatabaseUrl("DATABASE_URL_UNPOOLED");
		}
		if (databaseUrl == null || databaseUrl.isBlank()) {
			throw new IllegalStateException("DATABASE_URL_UNPOOLED must be configured for Flyway");
		}

		URI databaseUri = URI.create(databaseUrl);
		String userInfo = databaseUri.getRawUserInfo();
		if (userInfo == null || !userInfo.contains(":")) {
			throw new IllegalStateException("DATABASE_URL_UNPOOLED must include database credentials");
		}

		String[] credentials = userInfo.split(":", 2);
		String authority = databaseUri.getRawAuthority();
		String hostAuthority = authority.substring(authority.indexOf('@') + 1);
		String jdbcUrl = "jdbc:postgresql://" + hostAuthority + databaseUri.getRawPath();
		if (databaseUri.getRawQuery() != null) {
			jdbcUrl += "?" + databaseUri.getRawQuery();
		}

		return createDataSource(jdbcUrl, credentials);
	}

	private HikariDataSource createDataSource(String jdbcUrl, String[] credentials) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(jdbcUrl);
		dataSource.setUsername(URLDecoder.decode(credentials[0], StandardCharsets.UTF_8));
		dataSource.setPassword(URLDecoder.decode(credentials[1], StandardCharsets.UTF_8));
		return dataSource;
	}

	private String readLocalDatabaseUrl() {
		return readLocalDatabaseUrl("DATABASE_URL");
	}

	private String readLocalDatabaseUrl(String variableName) {
		Path envFile = Path.of(".env.local");
		if (!Files.exists(envFile)) {
			return null;
		}

		try {
			List<String> lines = Files.readAllLines(envFile);
			for (String line : lines) {
				String normalizedLine = line.replace("\uFEFF", "").trim();
				if (normalizedLine.startsWith(variableName + "=")) {
					return normalizedLine.substring((variableName + "=").length()).trim().replaceAll("^\"|\"$", "");
				}
			}
			return null;
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to read .env.local", exception);
		}
	}
}
