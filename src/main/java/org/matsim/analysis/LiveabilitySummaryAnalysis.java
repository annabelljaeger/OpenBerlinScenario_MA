package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "liveabilitySummary-analysis",
	description = "Liveability Ranking Summary",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
//	group="liveability",
	produces = {
		"overallRankingTile.csv",
		"overviewIndicatorTable.csv"
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
	private final Path outputOverallRankingPath = getValidLiveabilityOutputDirectory().resolve("overallRankingTile.csv");

	private final Path inputIndicatorValuesPath = ApplicationUtils.matchInput("rankingIndicatorValues.csv", getValidLiveabilityOutputDirectory());
	//private final Path outputIndicatorValuesForDashboardPath = ApplicationUtils.matchInput("overviewIndicatorTable.csv", getValidLiveabilityOutputDirectory());

	private final Path outputIndicatorValuesForDashboardPath = getValidLiveabilityOutputDirectory().resolve("overviewIndicatorTable.csv");

	private final Map<String, Double> liveabilityMetrics = new LinkedHashMap<>();

	public static void main(String[] args) {
		new LiveabilitySummaryAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

	//	log.info("Starting LiveabilitySummaryAnalysis...");
		System.out.println("Starting LiveabilitySummaryAnalysis...");

		Map<String, Double> RankingValueMap = new LinkedHashMap<>();

		try (CSVReader summaryTileReader = new CSVReader(new FileReader(inputSummaryTilesPath.toFile()));
			 CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(outputOverallRankingPath.toFile()))) {

			//HIER ICONS ALS OPTIONALE ERGÄNZUNG EINBAUEN UND DIREKT ÜBER NAMEN DER RANKINGKATEGORIEN SUCHEN UND EINFÜGEN
			String[] nextLine;
			while ((nextLine = summaryTileReader.readNext()) != null) {

				if (nextLine.length < 2) {
					System.err.println("Zeile hat nicht genügend Spalten: " + String.join(", ", nextLine));
					continue; // Überspringe diese Zeile
				}

				String key = nextLine[0];
				try {
					double value = convertPercentageToDouble(nextLine[1]);

				//	double value = Double.parseDouble(nextLine[1]);
					RankingValueMap.put(key, value);
					System.out.println("eingelesener Wert" + RankingValueMap.get(key));
				} catch (NumberFormatException e) {
					System.err.println("Could not parse " + nextLine[1] + " as double");
				}
			}

			double overallRankingValue = RankingValueMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue);

		    // overallRankingWriter.writeNext(new String[]{"Overall Ranking", formattedOverallRankingValue});
			overallRankingWriter.writeNext(new String[]{"Overall Ranking", formattedOverallRankingValue});

			System.out.println("Der Gesamt-Rankingwert lautet " + formattedOverallRankingValue);

		} catch(
		IOException e)

		{
			e.printStackTrace();
		}


		try (CSVReader rankingIndicatorReader = new CSVReader(new FileReader(inputIndicatorValuesPath.toFile()));
		CSVWriter overviewIndicatorValuesWriter = new CSVWriter(new FileWriter(outputIndicatorValuesForDashboardPath.toFile()))) {

			String[] nextLine;
			while ((nextLine = rankingIndicatorReader.readNext()) != null) {

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
