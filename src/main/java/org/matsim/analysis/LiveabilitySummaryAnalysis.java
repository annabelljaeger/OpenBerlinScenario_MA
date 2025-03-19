package org.matsim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;

@CommandLine.Command(
	name = "liveabilitySummary-analysis",
	description = "Liveability Index Summary",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	dependsOn = {
		//	@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overall_stats_agentLiveabilityInfo.csv"),
		//	@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overall_tiles_summaryPerIndex.csv"),
		@Dependency(value = AgentBasedGreenSpaceAnalysis.class, files =  "greenSpace_stats_perAgent.csv"),
		@Dependency(value = AgentBasedTrafficQualityAnalysis.class, files =  "travelTime_stats_perAgent.csv"),
	},
	produces = {
		"overall_tiles_ranking.csv",
		"overall_tiles_summary.csv",
		"overall_tiles_worstIndexValue.csv",
		"overall_stats_overviewIndicator.csv",
		"overall_XYT_AgentRankingForSummary.xyt.csv",
		"overall_tiles_highestLowestIndicator.csv"
	}

)

// overall liveability ranking analysis class to calculate the overall ranking and generating the indicator overview table for the Overall Dashboard page
public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

//	private final Logger log = LogManager.getLogger(LiveabilitySummaryAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LiveabilitySummaryAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LiveabilitySummaryAnalysis.class);

//	private final Path inputSummaryTilesPath = ApplicationUtils.matchInput("summaryTiles.csv", getValidLiveabilityOutputDirectory());
	////	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
//
//	private final Path worstIndicatorOutputPath = getValidLiveabilityOutputDirectory().resolve("overallWorstIndexValueTile.csv");
//	private final Path bestIndicatorOutputPath = getValidLiveabilityOutputDirectory().resolve("overallBestIndexValueTile.csv");
	// input path
	private final Path agentLivabilityInfoPath = getValidLiveabilityOutputDirectory().resolve("overall_stats_agentLiveabilityInfo.csv");
	//output Path
	private final Path outputOverallRankingPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_ranking.csv");
	private final Path XYTMapOutputPath = getValidLiveabilityOutputDirectory().resolve("overall_XYT_AgentRankingForSummary.xyt.csv");
	//	private final Path inputIndicatorValuesPath = ApplicationUtils.matchInput("indexIndicatorValues.csv", getValidLiveabilityOutputDirectory());
//	private final Path outputIndicatorValuesForDashboardPath = getValidLiveabilityOutputDirectory().resolve("overall_overviewIndicatorTable.csv");
	private final Path overallHighestLowestIndicatorPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_highestLowestIndicator.csv");


	//private final Map<String, Double> liveabilityMetrics = new LinkedHashMap<>();

	public static void main(String[] args) {
		new LiveabilitySummaryAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		//	log.info("Starting LiveabilitySummaryAnalysis...");
		System.out.println("Starting LiveabilitySummaryAnalysis...");

		Map<String, Double> overallRankingValuePerAgent = new LinkedHashMap<>();
		Map<String, String> homeXCoordinatePerAgent = new LinkedHashMap<>();
		Map<String, String> homeYCoordinatePerAgent = new LinkedHashMap<>();
		Map<String, String> highestIndexValueIndicatorMap = new LinkedHashMap<>();
		Map<String, String> lowestIndexValueIndicatorMap = new LinkedHashMap<>();
		Map<String, Double> overallBestRankingValuePerAgent = new LinkedHashMap<>();
		Map<String, Map<String, Double>> indexValuesPerAgent = new HashMap<>();
		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		Map<String, Double> worstValuePerAgent = new HashMap<>();
		Map<String, String> worstCategoryPerAgent = new HashMap<>();
		Map<String, Double> bestValuePerAgent = new HashMap<>();
		Map<String, String> bestCategoryPerAgent = new HashMap<>();

		// Counter for all Agents with all Livability Index <= 0;
		long countHighLivabilityAgents = 0;


		try (//CSVReader agentLiveabilityInfoReader = new CSVReader(new FileReader(inputAgentLiveabilityInfoPath
			 CSVParser agentLiveabilityInfoParser = new CSVParser(new FileReader(String.valueOf(agentLivabilityInfoPath)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(','));



		) {

			// Check whether the file is empty
			if (agentLiveabilityInfoParser.getHeaderMap().isEmpty()) {
				throw new IOException("The CSV file is empty or does not contain any valid headers.");
			}

			Set<String> headers = agentLiveabilityInfoParser.getHeaderMap().keySet();
			List<String> valueColumns = new ArrayList<>();

			// Check whether columns with “indexValue_” exist
			for (String header : headers) {
				if (header.startsWith("indexValue_")) {
					valueColumns.add(header);
				}
			}

			if (valueColumns.isEmpty()) {
				throw new IOException("No columns with 'indexValue_' found in the header.");
			}

			for (CSVRecord record : agentLiveabilityInfoParser) {
				String personKey = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(personKey, Arrays.asList(homeX, homeY));

				Map<String, Double> valuesMap = new HashMap<>();

				for (String column : valueColumns) {
					try {
						// Try to parse the value and add it to the valuesMap
						valuesMap.put(column, Double.valueOf(record.get(column)));
					} catch (NumberFormatException e) {
						// If parsing fails (e.g., empty string or invalid value), skip the iteration
						System.out.println("Warning: Invalid value in column '" + column + "' for agent " + record.get("person") + ". Value skipped.");
						continue; // Proceed to the next iteration
					}
				}

				indexValuesPerAgent.put(personKey, valuesMap);
			}
		}

		for (Map.Entry<String, Map<String, Double>> agentEntry : indexValuesPerAgent.entrySet()) {
			String agent = agentEntry.getKey();
			Map<String, Double> values = agentEntry.getValue();

			if (values.isEmpty()) {
				continue; // Falls ein Agent keine Werte hat, überspringen
			}

			String worstCategory = null;
			double worstValue = Double.NEGATIVE_INFINITY;
			String bestCategory = null;
			double bestValue = Double.POSITIVE_INFINITY;

			for (Map.Entry<String, Double> valueEntry : values.entrySet()) {
				String category = valueEntry.getKey();
				double value = valueEntry.getValue();

				if (value > worstValue) {
					worstValue = value;
					worstCategory = category;
				}

				if (value < bestValue) {
					bestValue = value;
					bestCategory = category;
				}
			}

			worstValuePerAgent.put(agent, worstValue);
			worstCategoryPerAgent.put(agent, worstCategory);
			bestValuePerAgent.put(agent, bestValue);
			bestCategoryPerAgent.put(agent, bestCategory);
		}

		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTMapOutputPath, worstValuePerAgent, homeCoordinatesPerAgentInStudyArea);

		try(CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(String.valueOf(outputOverallRankingPath)));
			CSVWriter highestLowestIndicatorTileWriter = new CSVWriter(new FileWriter(String.valueOf(overallHighestLowestIndicatorPath)));){

			Map.Entry<String, Double> resultWorst = findMostFrequentCategory(worstCategoryPerAgent);
			Map.Entry<String, Double> resultBest = findMostFrequentCategory(bestCategoryPerAgent);

			String worstIndicatorName = resultWorst.getKey();
			String formattedWorstIndicatorName = worstIndicatorName.substring(worstIndicatorName.indexOf('_') + 1);
			String formattedPercentageWorstIndicator = String.format(Locale.US, "%.2f%%", resultWorst.getValue()*100);
			String bestIndicatorName = resultBest.getKey();
			String formattedBestIndicatorName = bestIndicatorName.substring(bestIndicatorName.indexOf('_') + 1);
			String formattedPercentageBestIndicator = String.format(Locale.US, "%.2f%%", resultBest.getValue()*100);

			countHighLivabilityAgents = worstValuePerAgent.values().stream().filter(value -> value <= 0).count();
			double overallRankingValue = (double) countHighLivabilityAgents / worstValuePerAgent.size();
			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue*100);

			highestLowestIndicatorTileWriter.writeNext(new String[]{"Worst Indicator", formattedWorstIndicatorName});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Worst Indicator Percentage", formattedPercentageWorstIndicator});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Best Indicator", formattedBestIndicatorName});
			highestLowestIndicatorTileWriter.writeNext(new String[]{"Best Indicator Percentage", formattedPercentageBestIndicator});
			overallRankingWriter.writeNext(new String[]{"Overall Liveability-Index Value", formattedOverallRankingValue});

		}

//			String[] header = agentLiveabilityInfoReader.readNext();
//			if (header == null) {throw new IOException("The csv file is empty.");}
//
//			// Determine column index
//			List<Integer> rankingValueColumnIndices = new ArrayList<>();
//			for (int i = 0; i < header.length; i++) {
//				if (header[i].startsWith("indexValue_")){
//					rankingValueColumnIndices.add(i);
//				}
//			}
//
//			if (rankingValueColumnIndices.isEmpty()) {
//				throw new IllegalArgumentException("No columns with 'indexValue_' found in the header.");
//			}
//
//
//			for (CSVRecord record : agentLiveabilityInfoParser) {
//				String personKey = record.get("person");
//				Map<String, String> valuesMap = new HashMap<>();
//
//				for (String header : record.toMap().keySet()) {
//					if (header.startsWith("indexValue_")) {
//						valuesMap.put(header, record.get(header));
//					}
//				}
//
//				indexValuesPerAgent.put(personKey, valuesMap); // Letzter Eintrag überschreibt ältere
//			}
//
//
//			XYTMapWriter.writeNext(new String[]{"# EPSG:25832"});
//			XYTMapWriter.writeNext(new String[]{"time", "x", "y", "value"});
//
//			int totalRows = 0;
//			int matchingRows = 0;

//			String[] nextLine;
////			while ((nextLine = agentLiveabilityInfoReader.readNext()) != null) {
//				String perspn = nextLine.get("Person");
//				Map<String, Double> valueMapPerIndex = new HashMap<>();
////				boolean allValuesValid = true;
//				boolean validAgent = true;
//				double highestIndexValue=-Double.MIN_VALUE;
//				double lowestIndexValue=Double.MIN_VALUE;
//				String highestIndexValueIndicator = "";
//				String lowestIndexValueIndicator = "";
//				boolean isFirstIteration = true;

//				// find out whether an agent has a valid ranking value for all indices
//				for(int columnIndex : rankingValueColumnIndices) {
//
//					valueMapPerIndex.put(headers[columnIndex], row[columnIndex]);

//					String cellValue = nextLine[columnIndex]; // Der Wert der aktuellen Spalte
//					if (cellValue == null || cellValue.trim().isEmpty()) {
////						validAgent = false;
//						break;
//					}
//
//					double doubleValue = Double.parseDouble(cellValue);
//					if (doubleValue > highestIndexValue || isFirstIteration){
//						highestIndexValue = doubleValue;
//						highestIndexValueIndicator = header[columnIndex];
//						isFirstIteration = false;
//					}
//
//					double doubleValue2 = Double.parseDouble(cellValue);
//					if (doubleValue > lowestIndexValue || isFirstIteration){
//						lowestIndexValue = doubleValue2;
//						lowestIndexValueIndicator = header[columnIndex];
//						isFirstIteration = false;
//					}
//
//				}
//
//				if (!validAgent) {
//					continue;


//				overallRankingValuePerAgent.put(nextLine[0], highestIndexValue);
//				highestIndexValueIndicatorMap.put(nextLine[0], highestIndexValueIndicator);
//				overallBestRankingValuePerAgent.put(nextLine[0], lowestIndexValue);
//				lowestIndexValueIndicatorMap.put(nextLine[0], lowestIndexValueIndicator);
//				homeXCoordinatePerAgent.put(nextLine[0], nextLine[1]);
//				homeYCoordinatePerAgent.put(nextLine[0], nextLine[2]);
//
//				totalRows++;
//
//				if (highestIndexValue <= 0.0) {
//					matchingRows++;
//				}

//				XYTMapWriter.writeNext(new String[]{String.valueOf(0.0), nextLine[1], nextLine[2], String.valueOf(highestIndexValue)});
//
//			}
//
//
//
//
//			// Berechne den Anteil
//			double overallRankingValue = (double) matchingRows / totalRows * 100;
//
//			// Map zur Zählung der Häufigkeiten der Values
//			Map<String, Integer> worstValuesfrequencyMap = new HashMap<>();
//			Map<String, Integer> bestValuesfrequencyMap = new HashMap<>();
//
//			for (String value : highestIndexValueIndicatorMap.values()) {
//				worstValuesfrequencyMap.put(value, worstValuesfrequencyMap.getOrDefault(value, 0) + 1);
//			}
//
//			for (String value : lowestIndexValueIndicatorMap.values()) {
//				bestValuesfrequencyMap.put(value, bestValuesfrequencyMap.getOrDefault(value, 0) + 1);
//			}
//
//			// Den häufigsten Value bestimmen
//			String mostFrequentWorstValue = null;
//			int maxCount = Collections.max(worstValuesfrequencyMap.values());
//
//			for (Map.Entry<String, Integer> entry : worstValuesfrequencyMap.entrySet()) {
//				if (entry.getValue() == maxCount) {
//					mostFrequentWorstValue = entry.getKey();
//					break; // Falls nur ein Eintrag gewünscht ist
//				}
//			}
//
//			String mostFrequentBestValue = null;
//			int maxCountBest = Collections.max(bestValuesfrequencyMap.values());
//
//			for (Map.Entry<String, Integer> entry : bestValuesfrequencyMap.entrySet()) {
//				if (entry.getValue() == maxCountBest) {
//					mostFrequentBestValue = entry.getKey();
//					break; // Falls nur ein Eintrag gewünscht ist
//				}
//			}
//
//
//
//
//
//
//			overallRankingWriter.writeNext(new String[]{"Overall Liveability-Index Value", formattedOverallRankingValue});
//			System.out.println("Der Gesamt-Rankingwert lautet " + formattedOverallRankingValue);
//
//		}




//		try (CSVReader indexIndicatorReader = new CSVReader(new FileReader(inputIndicatorValuesPath.toFile()));
//		CSVWriter overviewIndicatorValuesWriter = new CSVWriter(new FileWriter(outputIndicatorValuesForDashboardPath.toFile()))) {
//
//			String[] nextLine;
//			while ((nextLine = indexIndicatorReader.readNext()) != null) {
//
//				overviewIndicatorValuesWriter.writeNext(nextLine);
//
//			}
//
//			System.out.println("The table has been successfully copied " + outputIndicatorValuesForDashboardPath.toFile());
//		} catch(
//		IOException e)
//
//		{
//			e.printStackTrace();
//		}

		//	log.info("LiveabilitySummaryAnalysis completed successfully.");
		return 0;
	}

	public static Map.Entry<String, Double> findMostFrequentCategory(Map<String, String> CategoryPerAgent) {
		Map<String, Integer> categoryCount = new HashMap<>();
		int totalAgents = CategoryPerAgent.size();

		// Count the frequency of each maxCategory
		for (String category : CategoryPerAgent.values()) {
			categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
		}

		// Determine the most frequent maxCategory
		String mostFrequentCategory = null;
		int maxCount = 0;

		for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
			if (entry.getValue() > maxCount) {
				maxCount = entry.getValue();
				mostFrequentCategory = entry.getKey();
			}
		}

		// Calculate the proportion of the most frequent maxCategory
		double percentage = (totalAgents > 0) ? ((double) maxCount / totalAgents) : 0.0;

		// Return as Map.Entry<String, Double>
		return new AbstractMap.SimpleEntry<>(mostFrequentCategory, percentage);
	}

	public static double convertPercentageToDouble(String percentageString) {
		// Entferne das Prozentzeichen und parse den numerischen Teil
		String numericPart = percentageString.replace("%", "");
		return Double.parseDouble(numericPart);
	}
}

/*
//eventuell die Ranking Berechnung auslagern? Dann Code schlanker und Berechnung nur einmal, nicht in jeder Dimensions-Analysis-Klasse

		Path testAgentStats = output.getPath("test_stats_perAgent.csv");
		try (BufferedReader agentBasedReader = Files.newBufferedReader(testAgentStats)) {
			String entry;
			int totalEntries = 0;
			int trueEntries = 0;

			// Überspringen der Header-Zeile
			agentBasedReader.readLine();

			// Iteration über alle Zeilen
			while ((entry = agentBasedReader.readLine()) != null) {
				String[] values = entry.split(";");
				// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
				if (values.length > 4 && "WAHR".equalsIgnoreCase(values[4].trim())) {
					trueEntries++;
				}
				totalEntries++;
			}

			// Anteil der True-Einträge berechnen
			double rankingTestValue = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
			String formattedTestValue = String.format(Locale.US, "%.2f%%", rankingTestValue);

			// Test-Input für extendSummaryTilesCsvWithAttribute vorbereiten
			Path tempTestInputPath = output.getPath("testInputForSummary.csv");

			try (BufferedWriter testInputWriter = Files.newBufferedWriter(tempTestInputPath)) {
				// Header für die Test-Input-Datei schreiben
			//	testInputWriter.write("Dimension;Value\n");
				// Test-Werte schreiben
				testInputWriter.write(String.format("TestValueRanking;%s\n", formattedTestValue));
			}

			System.out.println("Test-Input-Datei für SummaryTiles erstellt: " + tempTestInputPath);
*/
