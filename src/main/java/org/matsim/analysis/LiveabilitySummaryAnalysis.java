package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
	//	Path inputSummaryTilesPath = Path.of(input.getPath("summaryTiles.csv"));
	//	Path inputSummaryTilesPath = Path.of("C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\analysis\\analysis\\summaryTiles.csv");
	//	System.out.println(Paths.get(inputSummaryTilesPath.toUri()).toAbsolutePath());

		Map<String, Double> RankingValueMap = new LinkedHashMap<>();

		try (//CSVReader summaryTileReader = new CSVReader(new FileReader(inputSummaryTilesPath.toFile()));
			 CSVWriter overallRankingWriter = new CSVWriter(new FileWriter(outputOverallRankingPath.toFile()))) {

//			String[] nextLine;
//			while ((nextLine = summaryTileReader.readNext()) != null) {
//				String key = nextLine[0];
//				try {
//					double value = Double.parseDouble(nextLine[1]);
//					RankingValueMap.put(key, value);
//				} catch (NumberFormatException e) {
//					System.err.println("Could not parse " + nextLine[1] + " as double");
//				}
//			}
//			double overallRankingValue = RankingValueMap.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
//			String formattedOverallRankingValue = String.format(Locale.US, "%.2f%%", overallRankingValue);

		//	overallRankingWriter.writeNext(new String[]{"Overall Ranking", formattedOverallRankingValue});
			//Test WErt damit csv erstellt wird
			overallRankingWriter.writeNext(new String[]{"Overall Ranking", "0.3"});

			System.out.println("Der Gesamt-Rankingwert lautet " + "0,3");

	} catch(
	IOException e)

	{
		e.printStackTrace();
	}

	//	log.info("LiveabilitySummaryAnalysis completed successfully.");

		return 0;
}
}
/*
	private void writeSummaryFile() {
		Path outputPath = output.getPath("summaryTiles.csv");

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(outputPath.toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Category", "Percentage");

			for (Map.Entry<String, Double> entry : liveabilityMetrics.entrySet()) {
				printer.printRecord(entry.getKey(), entry.getValue());
//				printer.printRecord(entry.getKey(), formatPercentage(entry.getValue()));

			}

			log.info("Summary file written to {}", outputPath);
		} catch (IOException e) {
			log.error("Error writing summary file: {}", e.getMessage());
		}
	}


 */
	/*
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
	private void writeDetailedFile() {
		Path outputPath = output.getPath("liveability_details.csv");

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(outputPath.toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Category", "Percentage");

			for (Map.Entry<String, Double> entry : liveabilityMetrics.entrySet()) {
				printer.printRecord(entry.getKey(), formatPercentage(entry.getValue()));
			}

			log.info("Detailed file written to {}", outputPath);
		} catch (IOException e) {
			log.error("Error writing detailed file: {}", e.getMessage());
		}
		log.info("Output path resolved to: {}", output.getPath("summaryTiles.csv"));

	}

	private String formatPercentage(double value) {
		return new DecimalFormat("#.0#", DecimalFormatSymbols.getInstance(Locale.US)).format(value) + " %";
	}
}
*/

/*
		// Einträge je Dimension für die Liveability-Analyse (teils Platzhalter)
		liveabilityMetrics.put("OverallRanking", 65.0);

		Path outputDirectory = Path.of("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/summary_modeSpecificLegsLossTime.csv");

	//	liveabilityMetrics.put("Loss Time", AgentBasedLossTimeAnalysis.getRankingLossTime;
		//	Path lossTimeFile = Path.of("summary_modeSpecificLegsLossTime.csv");
	//	log.info("Versuche Datei zu lesen: {}", lossTimeFile.toAbsolutePath());

		double cumulativeLossTimeSum = 0.0;
		/*try {
			cumulativeLossTimeSum = LostTimeAnalysisLegs_ModeSpecific_adapted.getLossTimeSum(lossTimeFile);
			log.info("Cumulative Loss Time Sum: {}", cumulativeLossTimeSum);
		} catch (IOException e) {
			log.error("Error reading cumulative loss time: {}", e.getMessage());
		}

		try {
			double totalLossTime = AgentBasedLossTimeAnalysis.getLossTimeSum(outputDirectory);
			log.info("Gesamte Verlustzeit: {}", totalLossTime);
		} catch (IOException e) {
			log.error("Fehler beim Laden der Verlustzeitdaten: {}", e.getMessage());
		}
		liveabilityMetrics.put("LossTime", cumulativeLossTimeSum / 3600.0);

	//	double cumulativeLossTimeSum = calculateCumulativeLossTimeSum(Path.of(input.getPath("summary_modeSpecificLegsLossTime.csv")));
	//	liveabilityMetrics.put("LossTime", cumulativeLossTimeSum/3600.0);

		// Generieren der CSV-Dateien
		writeSummaryFile();
		writeDetailedFile();
*/
//TEST SECTION FOR SUMMARY FILE IMPLEMENTATION
		/*
		Path summaryTestValuePath = output.getPath("summaryTestValue.csv");
		try (BufferedReader testReader = Files.newBufferedReader(summaryTestValuePath)) {

			String entry;
		}
		double summaryTestValue = 99.99;
		String formattedSummaryTestValue = String.format(Locale.US, "%.2f%%", summaryTestValue);

		// Schreibe das Ergebnis zusammen mit rankingLossTime in die Datei lossTime_RankingValue.csv
		try (BufferedWriter writer = Files.newBufferedWriter(summaryTestValuePath)) {
			//	writer.write("Dimension;Value\n"); // Header
			writer.write(String.format("SummaryTestRanking;%s\n", summaryTestValue)); // Ranking
		}

		System.out.println("SummaryTestRanking: " + summaryTestValue);

		AgentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(summaryTestValuePath);
*/

/*

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
