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
//	requires = {
//		"summaryTiles.csv"
//	},
	produces = {
//		"summaryTiles_multiPolicy.csv",
//		"liveability_details.csv",
//		"summaryTestValue.csv",
//		"test_stats_perAgent.csv",
		"overallRankingTile.csv"
	}
)

public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

//	private final Logger log = LogManager.getLogger(LiveabilitySummaryAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LiveabilitySummaryAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LiveabilitySummaryAnalysis.class);

	private final Path inputSummaryTilesPath = ApplicationUtils.matchInput("summaryTiles.csv", getValidLiveabilityOutputDirectory());


	private final Map<String, Double> liveabilityMetrics = new LinkedHashMap<>();
//	private final Map<String, String> bestPolicies = new LinkedHashMap<>();

	public static void main(String[] args) {
		System.out.println("ich bin hier: Liveability Summary Analysis");
		new LiveabilitySummaryAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

	//	log.info("Starting LiveabilitySummaryAnalysis...");
		System.out.println("Starting LiveabilitySummaryAnalysis...");

		Path outputOverallRankingPath = output.getPath("overallRankingTile.csv");
	//	Path inputSummaryTilesPath = output.getPath("analysis/analysis/summaryTiles.csv");
	//	System.out.println(Paths.get(inputSummaryTilesPath.toUri()).toAbsolutePath());

		Map<String, Double> RankingValueMap = new LinkedHashMap<>();

		try (CSVReader summaryTileReader = new CSVReader(new FileReader(inputSummaryTilesPath.toFile()));
			 CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(outputOverallRankingPath.toFile()))) {

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
		//	double overallRankingValue = 2.0;
			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue);

		//	overallRankingWriter.writeNext(new String[]{"Overall Ranking", formattedOverallRankingValue});
			//Test WErt damit csv erstellt wird
			overallRankingWriter.writeNext(new String[]{"Overall Ranking", formattedOverallRankingValue});

			System.out.println("Der Gesamt-Rankingwert lautet " + formattedOverallRankingValue);

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
	//Mehrere Policys in einem Dashboard erstmal ausgeblendet - hat mehrere Schwierigkeiten, beginnend bei den verfügbaren Outputs
	private void writeSummaryFileMultiPolicy() {
		Path outputPath2 = output.getPath("summaryTiles_multiPolicy.csv");

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(outputPath2.toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Category", "Percentage", "Best Policy");

			for (Map.Entry<String, Double> entry : liveabilityMetrics.entrySet()) {
				String policy = bestPolicies.getOrDefault(entry.getKey(), "N/A");
				printer.printRecord(entry.getKey(), formatPercentage(entry.getValue()), policy);
			}

			log.info("Summary file written to {}", outputPath2);
		} catch (IOException e) {
			log.error("Error writing summary file: {}", e.getMessage());
		}
	}
	*/

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
