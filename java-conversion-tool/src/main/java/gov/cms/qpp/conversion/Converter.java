package gov.cms.qpp.conversion;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.cms.qpp.conversion.decode.XmlInputDecoder;
import gov.cms.qpp.conversion.decode.XmlInputFileException;
import gov.cms.qpp.conversion.decode.placeholder.DefaultDecoder;
import gov.cms.qpp.conversion.encode.EncodeException;
import gov.cms.qpp.conversion.encode.JsonOutputEncoder;
import gov.cms.qpp.conversion.encode.QppOutputEncoder;
import gov.cms.qpp.conversion.model.Node;
import gov.cms.qpp.conversion.model.ValidationError;
import gov.cms.qpp.conversion.model.Validations;
import gov.cms.qpp.conversion.validate.QrdaValidator;
// import gov.cms.qpp.conversion.validate.QrdaValidator;
import gov.cms.qpp.conversion.xml.XmlException;
import gov.cms.qpp.conversion.xml.XmlUtils;

/**
 * Converter provides the command line processing for QRDA III to QPP json.
 * 
 * @author David Uselmann
 *
 */
public class Converter {

	public static final String SKIP_VALIDATION = "--skip-validation";
	public static final String SKIP_DEFAULTS = "--skip-defaults";

	private static final Logger LOG = LoggerFactory.getLogger(Converter.class);

	private static boolean doDefaults = true;
	private static boolean doValidation = true;

	final Path inFile;

	public Converter(Path inFile) {
		this.inFile = inFile;
	}

	public Integer transform() {
		boolean hasValidations = false;
		
		if (Files.notExists(inFile)) {
			return 0; // it should if check prior to instantiation.
		}

		try {
			Node decoded = XmlInputDecoder.decodeXml(XmlUtils.fileToDOM(inFile));

			if (!doDefaults) {
				DefaultDecoder.removeDefaultNode(decoded.getChildNodes());
			}
			
			QrdaValidator validator = new QrdaValidator();

			List<ValidationError> validationErrors = Collections.emptyList();
			
			if (doValidation) {
				validationErrors = validator.validate(decoded);
			} 
			
			String name = inFile.getFileName().toString().trim();
			
			if (validationErrors.isEmpty()) {

				JsonOutputEncoder encoder = new QppOutputEncoder();

				LOG.info("Decoded template ID {} from file '{}'", decoded.getId(), name);
				// do something with decode validations
				// Validations.clear();
				// Validations.init();

				String outName = name.replaceFirst("(?i)(\\.xml)?$", ".qpp.json");

				Path outFile = Paths.get(outName);
				LOG.info("Writing to file '{}'", outFile.toAbsolutePath());

				try (Writer writer = Files.newBufferedWriter(outFile)) {
					encoder.setNodes(Arrays.asList(decoded));
					encoder.encode(writer);
					// do something with encode validations
				} catch (IOException | EncodeException e) {
					throw new XmlInputFileException("Issues decoding/encoding.", e);
				} finally {
					Validations.clear();
				}
			} else {
				hasValidations = true;
				
				String errName = name.replaceFirst("(?i)(\\.xml)?$", ".err.txt");

				Path outFile = Paths.get(errName);
				LOG.info("Writing to file '{}'", outFile.toAbsolutePath());

				try (Writer errWriter = Files.newBufferedWriter(outFile)) {
					for (ValidationError error : validationErrors) {
						errWriter.write("Validation Error: " + error.getErrorText() + System.lineSeparator());
					}
				} catch (IOException e) {
					LOG.error("Could not write to file: {}", errName);
				} finally {
					Validations.clear();
				}
			}
		} catch (XmlInputFileException | XmlException xe) {
			LOG.error("The file is not a valid XML document");
		} catch (Exception allE) {
			// Eat all exceptions in the call
			LOG.error(allE.getMessage());
		}
		return hasValidations ?0 :1;
	}

	public static Collection<Path> validArgs(String[] args) {
		if (args.length < 1) {
			LOG.error("No filename found...");
			return new LinkedList<>();
		}

		Collection<Path> validFiles = new LinkedList<>();

		for (String arg : args) {
			if (SKIP_VALIDATION.equals(arg)) {
				doValidation = false;
				continue;
			}
			if (SKIP_DEFAULTS.equals(arg)) {
				doDefaults = false;
				continue;
			}

			validFiles.addAll(checkPath(arg));
		}

		return validFiles;
	}

	public static Collection<Path> checkPath(String path) {
		Collection<Path> existingFiles = new LinkedList<>();

		if (path == null || path.trim().isEmpty()) {
			return existingFiles;
		}

		if (path.contains("*")) {
			return manyPath(path);
		}

		Path file = Paths.get(path);
		if (Files.exists(file)) {
			existingFiles.add(file);
		} else {
			LOG.error(path + " does not exist.");
		}

		return existingFiles;
	}

	public static Collection<Path> manyPath(String path) {
		Path inDir = Paths.get(extractDir(path));
		Pattern fileRegex = wildCardToRegex(path);
		try {
			return Files.walk(inDir)
					.filter(file -> fileRegex.matcher(file.toString()).matches())
					.filter(file -> !Files.isDirectory(file))
					.collect(Collectors.toList());
		} catch (Exception e) {
			LOG.error("Cannot file path {} {}", inDir, fileRegex.pattern());
			return new LinkedList<>();
		}
	}

	public static String extractDir(String path) {
		String[] parts = path.split("[\\/\\\\]");

		StringJoiner dirPath = new StringJoiner(FileSystems.getDefault().getSeparator());
		for (String part : parts) {
			// append until a wild card
			if (part.contains("*")) {
				break;
			}
			dirPath.add(part);
		}
		// if no path then use the current dir
		if (dirPath.length() == 0) {
			return ".";
		}

		return dirPath.add("").toString();
	}

	public static Pattern wildCardToRegex(String path) {
		String regex = "";

		// this replace should work if the user does not give conflicting OS
		// path separators
		String dirPath = extractDir(path);
		String wild = path;
		if (dirPath.length() > 1) {
			wild = wild.substring(dirPath.length());
		}

		String[] parts = wild.split("[\\/\\\\]");

		if (parts.length > 2) {
			LOG.error("Too many wild cards in {}", path);
			return Pattern.compile("");
		}
		String lastPart = parts[parts.length - 1];

		if ("**".equals(lastPart)) {
			regex = "."; // any and all files
		} else {
			// turn the last part into REGEX from file wild cards
			regex = lastPart.replaceAll("\\.", "\\\\.");
			regex = regex.replaceAll("\\*", ".*");
		}

		return Pattern.compile(regex);
	}

	public static void main(String[] args) {
		Collection<Path> filenames = validArgs(args);
		filenames.parallelStream().forEach(
				(filename) -> new Converter(filename).transform());
	}

}
