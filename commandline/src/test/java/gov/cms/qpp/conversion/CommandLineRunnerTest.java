package gov.cms.qpp.conversion;

import gov.cms.qpp.conversion.segmentation.QrdaScope;
import gov.cms.qpp.test.jimfs.JimfsContract;
import gov.cms.qpp.test.jimfs.JimfsTest;
import gov.cms.qpp.test.logging.LoggerContract;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;

import com.google.common.truth.Truth;
import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CommandLineRunnerTest implements LoggerContract, JimfsContract {

	private static final String INVALID_FILE = "THIS_FILE_SHOULD_NOT_EXIST.xml";

	@Test
	void testNewNull() {
		NullPointerException exception = Assertions.assertThrows(NullPointerException.class, () -> new CommandLineRunner(null));
		Truth.assertThat(exception).hasMessageThat().isEqualTo("commandLine");
	}

	@Test
	void testRunHelp() {
		CommandLineRunner runner = new CommandLineRunner(line("-" + CommandLineMain.HELP));
		runner.run();
		Truth.assertThat(getLogs()).isNotEmpty();
	}

	@Test
	void testRunWithoutFiles() {
		CommandLineRunner runner = new CommandLineRunner(line());
		runner.run();
		Truth.assertThat(getLogs()).contains("You must specify files to convert");
	}

	@Test
	void testRunWithInvalidScope() {
		CommandLineRunner runner = new CommandLineRunner(line(INVALID_FILE, "-t", "SOME_INVALID_SCOPE"));
		runner.run();
		Truth.assertThat(getLogs()).contains("A given template scope was invalid");
	}

	@Test
	void testRunWithValidScope() {
		CommandLineRunner runner = new CommandLineRunner(line(INVALID_FILE, "-t", QrdaScope.CLINICAL_DOCUMENT.name()));
		runner.run();
		Truth.assertThat(getLogs()).doesNotContain("A given template scope was invalid");
	}

	@Test
	void testRunWithMissingFile() {
		CommandLineRunner runner = new CommandLineRunner(line(INVALID_FILE));
		runner.run();
		Truth.assertThat(getLogs()).contains("Invalid or missing paths: [FILE]".replace("FILE", INVALID_FILE));
	}

	@JimfsTest
	void testRunWithValidFile(FileSystem fileSystem) throws IOException {
		CommandLineRunner runner = new CommandLineRunner(line("src/test/resources/valid-QRDA-III-abridged.xml"), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("valid-QRDA-III-abridged.qpp.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithValidFileSkipDefaults(FileSystem fileSystem) throws IOException {
		CommandLineRunner runner = new CommandLineRunner(line("src/test/resources/valid-QRDA-III-abridged.xml",
				"-" + CommandLineMain.SKIP_DEFAULTS), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("valid-QRDA-III-abridged.qpp.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithInvalidFile(FileSystem fileSystem) throws IOException {
		CommandLineRunner runner = new CommandLineRunner(line("src/test/resources/qrda_bad_denominator.xml"), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("qrda_bad_denominator.err.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithInvalidFileWithoutValidation(FileSystem fileSystem) throws IOException {
		CommandLineRunner runner = new CommandLineRunner(line("src/test/resources/qrda_bad_denominator.xml",
				"-" + CommandLineMain.SKIP_VALIDATION), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("qrda_bad_denominator.qpp.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithValidFileGlobAtHeadInRoot(FileSystem fileSystem) throws IOException {
		Files.copy(fileSystem.getPath("src/test/resources/valid-QRDA-III-abridged.xml"), fileSystem.getPath("valid-QRDA-III-abridged.xml"));
		CommandLineRunner runner = new CommandLineRunner(line("*.xml"), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("valid-QRDA-III-abridged.qpp.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithValidFileGlobAtTailInRoot(FileSystem fileSystem) throws IOException {
		Files.copy(fileSystem.getPath("src/test/resources/valid-QRDA-III-abridged.xml"), fileSystem.getPath("valid-QRDA-III-abridged.xml"));
		CommandLineRunner runner = new CommandLineRunner(line("valid-QRDA-III-abridged.*"), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("valid-QRDA-III-abridged.qpp.json"))).isTrue();
	}

	@JimfsTest
	void testRunWithValidGlobAllFiles(FileSystem fileSystem) throws IOException {
		CommandLineRunner runner = new CommandLineRunner(line("src/test/resources/*"), fileSystem);
		runner.run();
		Truth.assertThat(Files.exists(fileSystem.getPath("valid-QRDA-III-abridged.qpp.json"))).isTrue();
		Truth.assertThat(Files.exists(fileSystem.getPath("not-a-QRDA-III-file.err.json"))).isTrue();
	}

	private CommandLine line(String... arguments) {
		return CommandLineMain.cli(arguments);
	}

	@Override
	public Class<?> getLoggerType() {
		return CommandLineMain.class;
	}

}
