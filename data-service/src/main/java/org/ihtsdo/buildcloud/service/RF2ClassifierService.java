package org.ihtsdo.buildcloud.service;

import com.google.common.io.Files;
import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.dao.io.AsyncPipedStreamBean;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.BuildConfiguration;
import org.ihtsdo.buildcloud.service.build.RF2Constants;
import org.ihtsdo.buildcloud.service.build.transform.RepeatableRelationshipUUIDTransform;
import org.ihtsdo.buildcloud.service.build.transform.TransformationService;
import org.ihtsdo.buildcloud.service.exception.ProcessingException;
import org.ihtsdo.classifier.ClassificationException;
import org.ihtsdo.classifier.ClassificationRunner;
import org.ihtsdo.classifier.CycleCheck;
import org.ihtsdo.snomed.util.rf2.schema.ComponentType;
import org.ihtsdo.snomed.util.rf2.schema.SchemaFactory;
import org.ihtsdo.snomed.util.rf2.schema.TableSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RF2ClassifierService {

	@Autowired
	private BuildDAO buildDAO;

	@Autowired
	private String coreModuleSctid;

	@Autowired
	private TransformationService transformationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Checks for required files, performs cycle check then generates inferred relationships.
	 */
	public String generateInferredRelationshipSnapshot(Build build, Map<String, TableSchema> inputFileSchemaMap) throws ProcessingException {
		ClassifierFilesPojo classifierFiles = new ClassifierFilesPojo();
		BuildConfiguration configuration = build.getConfiguration();

		// Collect names of concept and relationship output files
		for (String inputFilename : inputFileSchemaMap.keySet()) {
			TableSchema inputFileSchema = inputFileSchemaMap.get(inputFilename);

			if (inputFileSchema == null) {
				logger.warn("Failed to recover schema mapped to {}.", inputFilename);
				continue;
			}

			if (inputFileSchema.getComponentType() == ComponentType.CONCEPT) {
				classifierFiles.getConceptSnapshotFilenames().add(inputFilename.replace(SchemaFactory.REL_2, SchemaFactory.SCT_2).replace(RF2Constants.DELTA, RF2Constants.SNAPSHOT));
			} else if (inputFileSchema.getComponentType() == ComponentType.STATED_RELATIONSHIP) {
				classifierFiles.getStatedRelationshipSnapshotFilenames().add(inputFilename.replace(SchemaFactory.REL_2, SchemaFactory.SCT_2).replace(RF2Constants.DELTA, RF2Constants.SNAPSHOT));
			}
		}

		if (classifierFiles.isSufficientToClassify()) {
			try {
				// Download snapshot files
				logger.info("Sufficient files for relationship classification. Downloading local copy...");
				File tempDir = Files.createTempDir();
				List<String> localConceptFilePaths = downloadFiles(build, tempDir, classifierFiles.getConceptSnapshotFilenames());
				List<String> localStatedRelationshipFilePaths = downloadFiles(build, tempDir, classifierFiles.getStatedRelationshipSnapshotFilenames());
				File cycleFile = new File(tempDir, RF2Constants.CONCEPTS_WITH_CYCLES_TXT);
				if (checkNoStatedRelationshipCycles(build, localConceptFilePaths, localStatedRelationshipFilePaths,
						cycleFile)) {

					logger.info("No cycles in stated relationship snapshot. Performing classification...");

					String effectiveTimeSnomedFormat = configuration.getEffectiveTimeSnomedFormat();
					List<String> previousInferredRelationshipFilePaths = new ArrayList<>();
					String previousInferredRelationshipFilePath = null;
					if (!configuration.isFirstTimeRelease()) {
						previousInferredRelationshipFilePath = getPreviousInferredRelationshipFilePath(build, classifierFiles, tempDir);
						if (previousInferredRelationshipFilePath != null) {
							previousInferredRelationshipFilePaths.add(previousInferredRelationshipFilePath);
						} else {
							logger.info(RF2Constants.DATA_PROBLEM + "No previous inferred relationship file found.");
						}
					}

					String statedRelationshipDeltaPath = localStatedRelationshipFilePaths.iterator().next();
					String inferredRelationshipSnapshotFilename = statedRelationshipDeltaPath.substring(statedRelationshipDeltaPath.lastIndexOf("/") + 1)
							.replace(ComponentType.STATED_RELATIONSHIP.toString(), ComponentType.RELATIONSHIP.toString())
							.replace(RF2Constants.DELTA, RF2Constants.SNAPSHOT);

					File inferredRelationshipsOutputFile = new File(tempDir, inferredRelationshipSnapshotFilename);
					File equivalencyReportOutputFile = new File(tempDir, RF2Constants.EQUIVALENCY_REPORT_TXT);

					ClassificationRunner classificationRunner = new ClassificationRunner(coreModuleSctid, effectiveTimeSnomedFormat,
							localConceptFilePaths, localStatedRelationshipFilePaths, previousInferredRelationshipFilePaths,
							inferredRelationshipsOutputFile.getAbsolutePath(), equivalencyReportOutputFile.getAbsolutePath());
					classificationRunner.execute();

					logger.info("Classification finished.");

					uploadLog(build, equivalencyReportOutputFile, RF2Constants.EQUIVALENCY_REPORT_TXT);

					// Upload inferred relationships file with null ids
					buildDAO.putTransformedFile(build, inferredRelationshipsOutputFile);

					// Generate inferred relationship ids using transform
					Map<String, String> uuidToSctidMap = null;
					if (!configuration.isFirstTimeRelease()) {
						uuidToSctidMap = buildUuidSctidMapFromPreviousRelationshipFile(previousInferredRelationshipFilePath);
					}
					transformationService.transformInferredRelationshipFile(build, inferredRelationshipSnapshotFilename, uuidToSctidMap);

					return inferredRelationshipSnapshotFilename;
				} else {
					logger.info(RF2Constants.DATA_PROBLEM + "Cycles detected in stated relationship snapshot file. " +
							"See " + RF2Constants.CONCEPTS_WITH_CYCLES_TXT + " in build package logs for more details.");
				}
			} catch (ClassificationException | IOException e) {
				throw new ProcessingException("Failed to generate inferred relationship snapshot.", e);
			}
		} else {
			logger.info("Stated relationship and concept files not present. Skipping classification.");
		}
		return null;
	}

	private Map<String, String> buildUuidSctidMapFromPreviousRelationshipFile(String previousInferredRelationshipFilePath) throws ProcessingException {
		try {
			Map<String, String> uuidSctidMap = new HashMap<>();
			RepeatableRelationshipUUIDTransform relationshipUUIDTransform = new RepeatableRelationshipUUIDTransform();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(previousInferredRelationshipFilePath)))) {
				String line;
				String[] columnValues;
				reader.readLine(); // Discard header
				while ((line = reader.readLine()) != null) {
					columnValues = line.split(RF2Constants.COLUMN_SEPARATOR, -1);
					uuidSctidMap.put(relationshipUUIDTransform.getCalculatedUuidFromRelationshipValues(columnValues), columnValues[0]);
				}
			}
			return uuidSctidMap;
		} catch (IOException e) {
			throw new ProcessingException("Failed to read previous relationship file during id reconciliation.", e);
		} catch (NoSuchAlgorithmException e) {
			throw new ProcessingException("Failed to use previous relationship file during id reconciliation.", e);
		}
	}

	public boolean checkNoStatedRelationshipCycles(Build build, List<String> localConceptFilePaths,
			List<String> localStatedRelationshipFilePaths, File cycleFile) throws ProcessingException {
		try {
			logger.info("Performing stated relationship cycle check...");
			CycleCheck cycleCheck = new CycleCheck(localConceptFilePaths, localStatedRelationshipFilePaths, cycleFile.getAbsolutePath());
			boolean cycleDetected = cycleCheck.cycleDetected();
			if (cycleDetected) {
				// Upload cycles file
				uploadLog(build, cycleFile, RF2Constants.CONCEPTS_WITH_CYCLES_TXT);
			}
			return !cycleDetected;
		} catch (IOException | ClassificationException e) {
			String message = e.getMessage();
			throw new ProcessingException("Error during stated relationship cycle check: " +
					e.getClass().getSimpleName() + (message != null ? " - " + message : ""), e);
		}
	}

	public void uploadLog(Build build, File logFile, String targetFilename) throws ProcessingException {
		try (FileInputStream in = new FileInputStream(logFile);
			 AsyncPipedStreamBean logFileOutputStream = buildDAO.getLogFileOutputStream(build, targetFilename)) {
			OutputStream outputStream = logFileOutputStream.getOutputStream();
			StreamUtils.copy(in, outputStream);
			outputStream.close();
		} catch (IOException e) {
			throw new ProcessingException("Failed to upload file " + targetFilename + ".", e);
		}
	}

	private String getPreviousInferredRelationshipFilePath(Build build, ClassifierFilesPojo classifierFiles, File tempDir) throws IOException {
		String previousPublishedPackage = build.getConfiguration().getPreviousPublishedPackage();
		String inferredRelationshipFilename = classifierFiles.getStatedRelationshipSnapshotFilenames().get(0).replace(RF2Constants.STATED, "");

		File localFile = new File(tempDir, inferredRelationshipFilename + ".previous_published");
		try (InputStream publishedFileArchiveEntry = buildDAO.getPublishedFileArchiveEntry(build.getProduct().getReleaseCenter(), inferredRelationshipFilename, previousPublishedPackage);
			 FileOutputStream out = new FileOutputStream(localFile)) {
			if (publishedFileArchiveEntry != null) {
				StreamUtils.copy(publishedFileArchiveEntry, out);
				return localFile.getAbsolutePath();
			}
		}

		return null;
	}

	private List<String> downloadFiles(Build build, File tempDir, List<String> filenameLists) throws ProcessingException {
		List<String> localFilePaths = new ArrayList<>();
		for (String downloadFilename : filenameLists) {

			File localFile = new File(tempDir, downloadFilename);
			try (InputStream inputFileStream = buildDAO.getOutputFileInputStream(build, downloadFilename);
				 FileOutputStream out = new FileOutputStream(localFile)) {
				if (inputFileStream != null) {
					StreamUtils.copy(inputFileStream, out);
					localFilePaths.add(localFile.getAbsolutePath());
				} else {
					throw new ProcessingException("Didn't find output file " + downloadFilename);
				}
			} catch (IOException e) {
				throw new ProcessingException("Failed to download snapshot file for classifier cycle check.", e);
			}
		}
		return localFilePaths;
	}

	private static class ClassifierFilesPojo {

		private List<String> conceptSnapshotFilenames;
		private List<String> statedRelationshipSnapshotFilenames;

		ClassifierFilesPojo() {
			conceptSnapshotFilenames = new ArrayList<>();
			statedRelationshipSnapshotFilenames = new ArrayList<>();
		}

		public boolean isSufficientToClassify() {
			return !conceptSnapshotFilenames.isEmpty() && !statedRelationshipSnapshotFilenames.isEmpty();
		}

		public List<String> getConceptSnapshotFilenames() {
			return conceptSnapshotFilenames;
		}

		public List<String> getStatedRelationshipSnapshotFilenames() {
			return statedRelationshipSnapshotFilenames;
		}
	}
}
