package org.matsim.analysis;

import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandLine.Command(
	name = "Utility Agent Liveability Info Collection",
	description = "create persons_liveability_output csv to collect the liveability values",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
//	requires = {
//		"berlin-v6.3.output_persons.csv.gz"
//	},
	produces = {
		"agentLiveabilityInfo.csv",
		"summaryTiles.csv"
	}
)

public class AgentLiveabilityInfo implements MATSimAppCommand {


	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentLiveabilityInfo.class);
	@CommandLine.Mixin
	private static final OutputOptions output = OutputOptions.ofCommand(AgentLiveabilityInfo.class);

	//	public static void main(String[] args) {
	public static void main(String[] args) {

		new AgentLiveabilityInfo().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		Path outputAgentLiveabilityCSVPath = output.getPath("agentLiveabilityInfo.csv");
		generateLiveabilityData(outputAgentLiveabilityCSVPath);

		Path categoryRankingCsvPath = output.getPath("summaryTiles.csv");
		generateSummaryTilesFile();

		return 0;
	}

	// method to introduce the liveabilityInfo.csv and fill it with the person ids
	public void generateLiveabilityData( Path outputCsvPath) throws IOException {

		//Path personsCsvPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv.gz"));
		Path personsCsvPath = Path.of("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/berlin-v6.3.output_persons.csv/berlin-v6.3.output_persons.csv");

		if (!Files.exists(personsCsvPath)) {
			throw new IOException("Die Datei output_persons.csv wurde nicht gefunden: " + personsCsvPath);
		}

		List<String> extractedPersons = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(String.valueOf(personsCsvPath)))) {

				String headerLine = reader.readLine();

				String[] headers = headerLine.split(";");
				int personColumnIndex = -1;
				for (int i = 0; i < headers.length; i++) {
					if (headers[i].trim().equalsIgnoreCase("person")) {
						personColumnIndex = i;
						break;
					}
				}

			// Prüfen, ob die Spalte gefunden wurde
			if (personColumnIndex == -1) {
				throw new IllegalArgumentException("Die Spalte 'person' wurde im Header nicht gefunden: " + headerLine);
			}

			String line;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(";");
				if (values.length > personColumnIndex) {
					extractedPersons.add(values[personColumnIndex].trim());
				}
			}


		}


		try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
			// Header schreiben
			writer.write("person");
			writer.newLine();

			// Extrahierte Personendaten schreiben
			for (String person : extractedPersons) {
				writer.write(person); // Beispiel: Platzhalter für weitere Spalten
				writer.newLine();
			}
		}

		System.out.println("Liveability-CSV erstellt unter: " + outputCsvPath);
	}

	public static void extendAgentLiveabilityInfoCsvWithAttribute(Map<String, ?> additionalData, String newAttributeName) throws IOException {
		Path inputCsvPath = output.getPath("agentLiveabilityInfo.csv");
		Path tempOutputPath = inputCsvPath.resolveSibling("agentLiveabilityInfo_updated.csv");

		try (BufferedReader reader = Files.newBufferedReader(inputCsvPath);
			 BufferedWriter writer = Files.newBufferedWriter(tempOutputPath)) {

			String header = reader.readLine();
			if (header == null) {
				throw new IOException("Die Eingabe-CSV ist leer.");
			}

			// Add new Column to the header
			writer.write(header + ";" + newAttributeName);
			writer.newLine();

			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split(";", -1);
				String personKey = columns[0];

			//	Object value = additionalData.getOrDefault(personKey, null);

				Object value = additionalData.containsKey(personKey)? additionalData.get(personKey):"";

				String formattedValue = value.toString();

				// get new attribute values from the map

				// writing values into the new column
				writer.write(line + ";" + formattedValue);
				writer.newLine();
			}
		}

		// Rewrite original file by temp file
		Files.move(tempOutputPath, inputCsvPath, StandardCopyOption.REPLACE_EXISTING);
	}

	public void generateSummaryTilesFile() throws IOException {

		Path categoryRankingCsvPath = output.getPath("summaryTiles.csv");

/*
		if (Files.exists(categoryRankingCsvPath)) {
			System.out.println("Die Datei summaryTiles.csv existiert bereits unter: " + categoryRankingCsvPath);
			return;
		}
		*/

		// Wenn die Datei existiert, wird sie gelöscht
		if (Files.exists(categoryRankingCsvPath)) {
			Files.delete(categoryRankingCsvPath);
			System.out.println("Die Datei summaryTiles.csv existierte bereits und wurde überschrieben.");
		}

		// Erstellen und initialisieren der leeren CSV-Datei
		try (BufferedWriter writer = Files.newBufferedWriter(categoryRankingCsvPath)) {
			// Schreiben eines Headers (optional)
			//writer.write("Column1,Column2,Column3"); // Beispiel für einen Header
			writer.newLine();

			System.out.println("Die leere Datei summaryTiles.csv wurde erstellt unter: " + categoryRankingCsvPath);
		}
	}

	public static void extendSummaryTilesCsvWithAttribute(Path categoryRankingCsvPath) throws IOException {
		Path inputSummaryTileCsvPath = output.getPath("summaryTiles.csv");
		//Path tempSummaryTilesOutputPath = inputSummaryTileCsvPath.resolveSibling("summaryTiles_updated.csv");

		if (!Files.exists(categoryRankingCsvPath)) {
			throw new IOException("Die zusätzliche CSV-Datei wurde nicht gefunden: " + categoryRankingCsvPath);
		}

		try (BufferedReader additionalReader = Files.newBufferedReader(categoryRankingCsvPath);
		//	 BufferedWriter writer = Files.newBufferedWriter(tempSummaryTilesOutputPath)) {
			 BufferedWriter writer = Files.newBufferedWriter(inputSummaryTileCsvPath, StandardOpenOption.APPEND)) {

			String header = additionalReader.readLine();
			if (header == null) {
				throw new IOException("Die zusätzliche CSV ist leer.");
			}

			// Add new Column to the header
		//	writer.write(header + ";" + newAttributeName);
		//	writer.newLine();

			String line;
			while ((line = additionalReader.readLine()) != null) {
				writer.write(line);
				writer.newLine();

			}
		}
		System.out.println("Daten aus der zusätzlichen CSV wurden an " + inputSummaryTileCsvPath + " angehängt.");

		// Rewrite original file by temp file
	//	Files.move(tempSummaryTilesOutputPath, inputSummaryTileCsvPath, StandardCopyOption.REPLACE_EXISTING);
	}

}
