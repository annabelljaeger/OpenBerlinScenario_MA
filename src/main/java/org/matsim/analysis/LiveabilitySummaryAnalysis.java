package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.matsim.application.ApplicationUtils;
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
				@Dependency(value = AgentLiveabilityInfoCollection.class, files = "agentLiveabilityInfo.csv"),
				@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overallRankingTile.csv"),
				@Dependency(value = AgentBasedGreenSpaceAnalysis.class, files =  "greenSpace_stats_perAgent.csv"),
 	},
	produces = {
		"overallRankingTile.csv",
		"overallWorstIndexValueTile.csv",
		"overviewIndicatorTable.csv",
		"agentRankingForSummaryMap.xyt.csv",
		"overallHighestLowestIndicator.csv"
	}

)

// overall liveability ranking analysis class to calculate the overall ranking and generating the indicator overview table for the Overall Dashboard page
public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

//	private final Logger log = LogManager.getLogger(LiveabilitySummaryAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LiveabilitySummaryAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LiveabilitySummaryAnalysis.class);

	private final Path inputSummaryTilesPath = ApplicationUtils.matchInput("summaryTiles.csv", getValidLiveabilityOutputDirectory());
//	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	private final Path outputOverallRankingPath = getValidLiveabilityOutputDirectory().resolve("overallRankingTile.csv");
	private final Path worstIndicatorOutputPath = getValidLiveabilityOutputDirectory().resolve("overallWorstIndexValueTile.csv");

	private final Path XYTMapOutputPath = getValidLiveabilityOutputDirectory().resolve("agentRankingForSummaryMap.xyt.csv");
	private final Path inputIndicatorValuesPath = ApplicationUtils.matchInput("indexIndicatorValues.csv", getValidLiveabilityOutputDirectory());
	private final Path outputIndicatorValuesForDashboardPath = getValidLiveabilityOutputDirectory().resolve("overviewIndicatorTable.csv");
	private final Path overallHighestLowestIndicatorPath = getValidLiveabilityOutputDirectory().resolve("overallHighestLowestIndicator.csv");

	private final Map<String, Double> liveabilityMetrics = new LinkedHashMap<>();

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

		// Prüfe, ob alle Werte in den "rankingValue_"-Spalten kleiner oder gleich 0 sind
		String inputAgentLiveabilityInfoPath = input.getPath(AgentLiveabilityInfoCollection.class,"agentLiveabilityInfo.csv");

		try (CSVReader agentLiveabilityInfoReader = new CSVReader(new FileReader(inputAgentLiveabilityInfoPath));

			 CSVWriter worstIndicatorWriter = new CSVWriter(new FileWriter(output.getPath("overallWorstIndexValueTile.csv").toFile()));
			 CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(output.getPath("overallRankingTile.csv").toFile()));

			 CSVWriter XYTMapWriter = new CSVWriter(
				new FileWriter(String.valueOf(XYTMapOutputPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

			String[] header = agentLiveabilityInfoReader.readNext();
			if (header == null) {throw new IOException("The csv file is empty.");}

			List<Integer> rankingValueColumnIndices = new ArrayList<>();
			for (int i = 0; i < header.length; i++) {
				if (header[i].startsWith("indexValue_")){
					rankingValueColumnIndices.add(i);
				}
			}

			if (rankingValueColumnIndices.isEmpty()) {
				throw new IllegalArgumentException("Keine Spalten mit 'indexValue_' im Header gefunden.");
			}

			XYTMapWriter.writeNext(new String[]{"# EPSG:25832"});
			XYTMapWriter.writeNext(new String[]{"time", "x", "y", "value"});

			int totalRows = 0;
			int matchingRows = 0;

			String[] nextLine;
			while ((nextLine = agentLiveabilityInfoReader.readNext()) != null) {

				boolean allValuesValid = true;
				boolean validAgent = true;
				double highestRankingValue=-Double.MIN_VALUE;
				String highestIndexValueIndicator = "";
				boolean isFirstIteration = true;

				// find out whether an agent has a valid ranking value for all indices
				for(int columnIndex : rankingValueColumnIndices) {
					String cellValue = nextLine[columnIndex]; // Der Wert der aktuellen Spalte
					if (cellValue == null || cellValue.trim().isEmpty()) {
//						validAgent = false;
						break;
					}

					double doubleValue = Double.parseDouble(cellValue);
					if (doubleValue > highestRankingValue || isFirstIteration){
						highestRankingValue = doubleValue;
						highestIndexValueIndicator = header[columnIndex];
						isFirstIteration = false;
					}

				}

				if (!validAgent) {
					continue;
				}

				overallRankingValuePerAgent.put(nextLine[0], highestRankingValue);
				highestIndexValueIndicatorMap.put(nextLine[0], highestIndexValueIndicator);
				homeXCoordinatePerAgent.put(nextLine[0], nextLine[1]);
				homeYCoordinatePerAgent.put(nextLine[0], nextLine[2]);

				totalRows++;

				if (highestRankingValue <= 0.0) {
					matchingRows++;
				}


				XYTMapWriter.writeNext(new String[]{String.valueOf(0.0), nextLine[1], nextLine[2], String.valueOf(highestRankingValue)});

			}

			// Berechne den Anteil
			double overallRankingValue = (double) matchingRows / totalRows * 100;

			// Map zur Zählung der Häufigkeiten der Values
			Map<String, Integer> frequencyMap = new HashMap<>();

			for (String value : highestIndexValueIndicatorMap.values()) {
				frequencyMap.put(value, frequencyMap.getOrDefault(value, 0) + 1);
			}

			// Den häufigsten Value bestimmen
			String mostFrequentValue = null;
			int maxCount = Collections.max(frequencyMap.values());

			for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
				if (entry.getValue() == maxCount) {
					mostFrequentValue = entry.getKey();
					break; // Falls nur ein Eintrag gewünscht ist
				}
			}

			String worstIndicatorName = mostFrequentValue;
			double AnteilWorstIndicator = (double) maxCount / overallRankingValuePerAgent.size() * 100;
			String formattedPercentageWorstIndicator = String.format(Locale.US, "%.2f%%", AnteilWorstIndicator);
			worstIndicatorWriter.writeNext(new String[]{"Worst Indicator", worstIndicatorName});
			worstIndicatorWriter.writeNext(new String[]{"Worst Indicator Percentage", formattedPercentageWorstIndicator});

			//			double overallRankingValue = RankingValueMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue);

			overallRankingWriter.writeNext(new String[]{"Overall Liveability-Index Value", formattedOverallRankingValue});
			System.out.println("Der Gesamt-Rankingwert lautet " + formattedOverallRankingValue);

		}


		try (CSVReader indexIndicatorReader = new CSVReader(new FileReader(inputIndicatorValuesPath.toFile()));
		CSVWriter overviewIndicatorValuesWriter = new CSVWriter(new FileWriter(outputIndicatorValuesForDashboardPath.toFile()))) {

			String[] nextLine;
			while ((nextLine = indexIndicatorReader.readNext()) != null) {

				overviewIndicatorValuesWriter.writeNext(nextLine);

			}

			System.out.println("The table has been successfully copied " + outputIndicatorValuesForDashboardPath.toFile());
		} catch(
		IOException e)

		{
			e.printStackTrace();
		}

		//	log.info("LiveabilitySummaryAnalysis completed successfully.");
		return 0;
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
