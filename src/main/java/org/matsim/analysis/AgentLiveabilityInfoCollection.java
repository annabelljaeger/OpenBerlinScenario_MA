package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "Utility - Agent Liveability Info Collection",
	description = "create agentLiveabilityInfo.csv, summaryTiles.csv and rankingIndicatorValues.csv to collect the liveability values from the different dimensions",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	group="liveability",
	produces = {
		"agentLiveabilityInfo.csv",
		"summaryTiles.csv",
		"rankingIndicatorValues.csv"
	}
)

/**
 * utility class to generate csv-Files and write methods to extend those generated files for each dimension-analysis class
  */
public class AgentLiveabilityInfoCollection implements MATSimAppCommand {

	// defining constants for paths
	private final Path outputAgentLiveabilityCSVPath = getValidLiveabilityOutputDirectory().resolve("agentLiveabilityInfo.csv");
	private final Path tempAgentLiveabilityOutputPath = getValidLiveabilityOutputDirectory().resolve("agentLiveabilityInfo_tmp.csv");

	private final Path outputIndicatorValuesCsvPath = getValidLiveabilityOutputDirectory().resolve("rankingIndicatorValues.csv");
	private final Path tempIndicatorValuesCsvPath = getValidOutputDirectory().resolve("rankingIndicatorValues.csv");

	private final Path personsCsvPath = getValidOutputDirectory().resolve("berlin-v6.3.output_persons.csv.gz");
	private final Path outputCategoryRankingCsvPath = getValidLiveabilityOutputDirectory().resolve("summaryTiles.csv");
	private final Path tempSummaryTilesOutputPath = getValidLiveabilityOutputDirectory().resolve("summaryTiles_tmp.csv");

	// method generates the csv-Files - the methods to extend the files are called in the dimension analysis classes
	@Override
	public Integer call() throws Exception {

		generateLiveabilityData();

		generateSummaryTilesFile();

		generateIndicatorFile();

		return 0;
	}

	// method to introduce the agentLiveabilityInfo.csv and fill it with the person ids from the persons.csv.gz file in the output folder
	private void generateLiveabilityData( ) throws IOException {

		if (!Files.exists(personsCsvPath)) {
			throw new IOException("Die Datei output_persons.csv.gz wurde nicht gefunden: " + personsCsvPath);
		}

		try (InputStream fileStream = new FileInputStream(personsCsvPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader personsReader = new InputStreamReader(gzipStream);
			 CSVParser personsParser = new CSVParser(personsReader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter agentLiveabilityWriter = new CSVWriter(new FileWriter(outputAgentLiveabilityCSVPath.toFile()))) {

					 agentLiveabilityWriter.writeNext(new String[]{"person", "home_x", "home_y"});

					 for (CSVRecord record : personsParser) {
						 String person = record.get("person");
						 String homeX = record.get("home_x");
						 String homeY = record.get("home_y");

						 // not universally transferable for other regions yet!
						 String pointInBerlin = record.get("RegioStaR7");

						 // the Liveability-Ranking is only useful for a defined study area, which in this case is the boarder of Berlin. In this case, this includes all points in the RegioStaR7 = 1 area.
						 if ("1".equals(pointInBerlin)) {
							 agentLiveabilityWriter.writeNext(new String[]{person, homeX, homeY});
						 }
					 }
			}
		System.out.println("Liveability-CSV generated under: " + outputAgentLiveabilityCSVPath);
	}

	// method to extend the agentLiveabilityInfo.csv file with agent based information from the analysis classes of the dimensions (this is where they are called).
	// Data has to be provided as maps - those are used throughout the code to guarantee agent based values and the mapping of those to the universal peron ids.
	public void extendAgentLiveabilityInfoCsvWithAttribute(Map<String, Double> additionalData, String newAttributeName) throws IOException {

		try (CSVReader personEntryReader = new CSVReader(new FileReader(outputAgentLiveabilityCSVPath.toFile()));
			CSVWriter valueWriter = new CSVWriter(new FileWriter(tempAgentLiveabilityOutputPath.toFile()))) {

			//read header
			String[] header = personEntryReader.readNext();
			if (header == null) {
				throw new IOException("The persons input csv is empty.");
			}

			// add new column for the header extracted from the input coming from the analysis classes
			String[] newHeader = new String[header.length + 1];
			System.arraycopy(header, 0, newHeader, 0, header.length);
			newHeader[header.length] = newAttributeName;
			valueWriter.writeNext(newHeader);
			System.out.println("Spaltenname geschrieben: " + newAttributeName);

			// extends each person line with the content from the input in a next column
			String[] line;
			while ((line = personEntryReader.readNext()) != null) {
				String personKey = line[0];

				// get new attribute values from the map
				Object value = additionalData.containsKey(personKey)? additionalData.get(personKey):"";
				String formattedValue = (value != null) ? value.toString() : "";

				// add new column to the line
				String[] newLine = new String[line.length + 1];
				System.arraycopy(line, 0, newLine, 0, line.length);
				newLine[line.length] = formattedValue;

				valueWriter.writeNext(newLine);
			}

		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempAgentLiveabilityOutputPath, outputAgentLiveabilityCSVPath, StandardCopyOption.REPLACE_EXISTING);
	}

	// generates an empty summaryTiles.csv file for information from each analysis to be added
	private void generateSummaryTilesFile() throws IOException {

		// if file exists: delete
		if (Files.exists(outputCategoryRankingCsvPath)) {
			Files.delete(outputCategoryRankingCsvPath);
			System.out.println("The file summaryTiles.csv exists already and has been replaced.");
		}

		// generate and initialize empty/placeholder-filled csv-file
		try (CSVWriter writer = new CSVWriter(new FileWriter(outputCategoryRankingCsvPath.toFile()))) {
			writer.writeNext(new String[]{"Warum ist hier nichts? :( - CSV angelegt aber kein Inhalt hinzugefügt"});

			System.out.println("The prepared file Datei summaryTiles.csv has been generated: " + outputCategoryRankingCsvPath);
		}
	}

	// method to extend the summaryTiles.csv file with agent based information from the analysis classes of the dimensions (this is where they are called)
	public void extendSummaryTilesCsvWithAttribute(String RankingValue, String CategoryName) throws IOException {

			try (CSVReader tilesReader = new CSVReader(new FileReader(outputCategoryRankingCsvPath.toFile()));
				 CSVWriter tilesWriter = new CSVWriter(new FileWriter(tempSummaryTilesOutputPath.toFile()))) {

				String[] nextLine;
				while ((nextLine = tilesReader.readNext()) != null) {
					if (String.join(";", nextLine).contains("Warum ist hier nichts? :(")) {
						continue;
					}
					tilesWriter.writeNext(nextLine);
				}

				tilesWriter.writeNext(new String[]{CategoryName, RankingValue});

			} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempSummaryTilesOutputPath, outputCategoryRankingCsvPath, StandardCopyOption.REPLACE_EXISTING);
	}

	// generates an empty rankingIndicatorValues.csv file for information from each analysis to be added
	private void generateIndicatorFile() throws IOException {

		// delete file if already existent
		if (Files.exists(outputIndicatorValuesCsvPath)) {
			Files.delete(outputIndicatorValuesCsvPath);
			System.out.println("The file rankingIndicatorValues.csv already exists and has been replaced.");
		}

		// generating the empty csv-file with a prefilled header
	//	try (CSVWriter writer = new CSVWriter(new FileWriter(outputIndicatorValuesCsvPath.toFile()))) {
		try (CSVWriter writer = new CSVWriter(new FileWriter(outputIndicatorValuesCsvPath.toFile()),
			CSVWriter.DEFAULT_SEPARATOR,
			CSVWriter.NO_QUOTE_CHARACTER,
			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
			CSVWriter.DEFAULT_LINE_END)) {

			writer.writeNext(new String[]{"dimension","indicator","median value","limit","ranking value","weight of indicator"});

			System.out.println("The empty file rankingIndicatorValues.csv has been generated under: " + outputIndicatorValuesCsvPath);
		}
	}

	// method to extend the rankingIndicatorValues.csv file with agent based information from the analysis classes of the dimensions (this is where they are called)
	public void extendIndicatorValuesCsvWithAttribute(String dimension, String indicator, String medianValue, String limit, String RankingValue, double weightOfIndicator) throws IOException {

		try (CSVReader indicatorReader = new CSVReader(new FileReader(outputIndicatorValuesCsvPath.toFile()));
			 CSVWriter indicatorWriter = new CSVWriter(new FileWriter(tempIndicatorValuesCsvPath.toFile()))) {

//			String[] header = indicatorReader.readNext();
//			if (header == null) {
//				indicatorWriter.writeNext(header);
//			}

			String[] line;
			while ((line = indicatorReader.readNext()) != null) {
				indicatorWriter.writeNext(line);
				indicatorWriter.writeNext(new String[]{dimension, indicator, medianValue, limit, RankingValue, String.valueOf(weightOfIndicator)});
			}

		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempIndicatorValuesCsvPath, outputIndicatorValuesCsvPath, StandardCopyOption.REPLACE_EXISTING);

	}

}
