package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

@CommandLine.Command(
	name = "liveabilitySummary-analysis",
	description = "Liveability Ranking Summary",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	produces = {
		"summaryTiles2.csv",
		"liveability_details.csv"
	}
)

public class LiveabilitySummaryAnalysis implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(LiveabilitySummaryAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LiveabilitySummaryAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LiveabilitySummaryAnalysis.class);

	private final Map<String, Double> liveabilityMetrics = new LinkedHashMap<>();
	private final Map<String, String> bestPolicies = new LinkedHashMap<>();

	public static void main(String[] args) {
		new LiveabilitySummaryAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		log.info("Starting LiveabilitySummaryAnalysis...");

		// Beispielhafte Daten für die Liveability-Analyse (Platzhalter)
		liveabilityMetrics.put("OverallRanking", 65.0);
		liveabilityMetrics.put("LossTime", 21.0);
		liveabilityMetrics.put("Emissions", 54.0);
		liveabilityMetrics.put("Noise", 39.0);

		bestPolicies.put("LossTime", "BaseCase");
		bestPolicies.put("Emissions", "PolicyA");
		bestPolicies.put("Noise", "PolicyB");

		// Generieren der CSV-Dateien
		writeSummaryFile();
		writeDetailedFile();

		log.info("LiveabilitySummaryAnalysis completed successfully.");
		return 0;
	}

	private void writeSummaryFile() {
		Path outputPath = output.getPath("summaryTiles2.csv");

		try (CSVPrinter printer = new CSVPrinter(IOUtils.getBufferedWriter(outputPath.toString()), CSVFormat.DEFAULT)) {
			printer.printRecord("Category", "Percentage", "Best Policy");

			for (Map.Entry<String, Double> entry : liveabilityMetrics.entrySet()) {
				String policy = bestPolicies.getOrDefault(entry.getKey(), "N/A");
				printer.printRecord(entry.getKey(), formatPercentage(entry.getValue()), policy);
			}

			log.info("Summary file written to {}", outputPath);
		} catch (IOException e) {
			log.error("Error writing summary file: {}", e.getMessage());
		}
	}

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
		log.info("Output path resolved to: {}", output.getPath("summary_tiles.csv"));

	}

	private String formatPercentage(double value) {
		return new DecimalFormat("#.0#", DecimalFormatSymbols.getInstance(Locale.US)).format(value) + " %";
	}
}

	/*
		// Der Pfad zur Ausgabe-CSV-Datei
	//	String fileName = "summaryTiles2.csv";
		// Pfad zur CSV-Datei dynamisch erstellen
		Path outputDirectory = output.getOutputPath();
		Path("filename");
		Files.createDirectories(outputDirectory);

		Path filePath = outputDirectory.resolve("summaryTiles2.csv");



		// Inhalt der CSV-Datei
		String[] rows = {
			"OverallRanking,65 %,",
			"LossTime,21 %,BaseCase",
			"Emissions,54 %,PolicyA",
			"Noise,39 %,PolicyB"
		};

		// CSV-Datei erstellen und Daten schreiben
		try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
			for (String row : rows) {
				writer.write(row);
				writer.newLine(); // Zeilenumbruch
			}
			System.out.println("CSV-Datei wurde erfolgreich erstellt: " + filePath);
		} catch (IOException e) {
			System.err.println("Fehler beim Erstellen der CSV-Datei: " + e.getMessage());
		}

		return 0;
	}
}

	 */
