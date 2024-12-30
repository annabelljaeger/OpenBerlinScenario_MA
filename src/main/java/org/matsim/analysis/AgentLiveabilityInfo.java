package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
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

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "Utility Agent Liveability Info Collection",
	description = "create agentLiveabilityInfo.csv and summaryTiles.csv to collect the liveability values from the different dimensions",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
//	requireRunDirectory = true,
//	requires = {
//		"berlin-v6.3.output_persons.csv.gz"
//	},
	produces = {
		"agentLiveabilityInfo.csv",
		"summaryTiles.csv"
	}
)

public class AgentLiveabilityInfo implements MATSimAppCommand {

//	@CommandLine.Mixin
//	private final InputOptions input = InputOptions.ofCommand(AgentLiveabilityInfo.class);
//	@CommandLine.Mixin
//	private static final OutputOptions output = OutputOptions.ofCommand(AgentLiveabilityInfo.class);

	// constants for paths
	//ALTERNATIV MIT NUR PATH IN DIE METHODEN REINSCHREIBEN - DANN NAMEN KLEINSCHREIBEN
	private final Path outputAgentLiveabilityCSVPath = getValidOutputDirectory().resolve("analysis/analysis/agentLiveabilityInfo.csv");
	private final Path tempOutputPath = getValidOutputDirectory().resolve("analysis/analysis/agentLiveabilityInfo_tmp.csv");
	private final Path personsCsvPath = getValidOutputDirectory().resolve("berlin-v6.3.output_persons.csv");;
	private final Path categoryRankingCsvPath= getValidOutputDirectory().resolve("analysis/analysis/summaryTiles.csv");
	private final Path tempSummaryTilesOutputPath = getValidOutputDirectory().resolve("analysis/analysis/summaryTiles_tmp.csv");

	public static void main() {
		new AgentLiveabilityInfo().execute();
	}

	@Override
	public Integer call() throws Exception {

		//Path outputAgentLiveabilityCSVPath = output.getPath("agentLiveabilityInfo.csv");
		//generateLiveabilityData(outputAgentLiveabilityCSVPath);
		generateLiveabilityData();


		//Path categoryRankingCsvPath = output.getPath("summaryTiles.csv");
		generateSummaryTilesFile();

		return 0;
	}

	// method to introduce the liveabilityInfo.csv and fill it with the person ids

		public void generateLiveabilityData( ) throws IOException {

	//	Path outputAgentLiveabilityCSVPath = output.getPath("agentLiveabilityInfo.csv");

	//	Path outputAgentLiveabilityCSVPath = getValidOutputDirectory().resolve("analysis/analysis/agentLiveabilityInfo.csv");

			//Path personsCsvPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv.gz"));
	//	Path personsCsvPath = Path.of("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/berlin-v6.3.output_persons.csv");

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

		try (BufferedWriter writer = Files.newBufferedWriter(outputAgentLiveabilityCSVPath)) {
			// Header schreiben
			writer.write("person");
			writer.newLine();

			// Extrahierte Personendaten schreiben
			for (String person : extractedPersons) {
				writer.write(person); // Beispiel: Platzhalter für weitere Spalten
				writer.newLine();
			}
		}

		System.out.println("Liveability-CSV erstellt unter: " + outputAgentLiveabilityCSVPath);
	}

	public  void extendAgentLiveabilityInfoCsvWithAttribute(Map additionalData, String newAttributeName) throws IOException {
		//Path inputCsvPath = output.getPath("agentLiveabilityInfo.csv");
	//	Path tempOutputPath = inputCsvPath.resolveSibling("agentLiveabilityInfo_updated.csv");



		try (BufferedReader reader = Files.newBufferedReader(outputAgentLiveabilityCSVPath);
			 BufferedWriter writer = Files.newBufferedWriter(tempOutputPath)) {

			String header = reader.readLine();
			if (header == null) {
				throw new IOException("Die Eingabe-CSV ist leer.");
			}

			// Add new Column to the header
			writer.write(header + ";" + newAttributeName);
			writer.newLine();
			System.out.println("Spaltenname geschrieben: "+newAttributeName);

			String line;
			while ((line = reader.readLine()) != null) {
				String[] columns = line.split(";", -1);
				String personKey = columns[0];

				//get new attribute values from the map
				Object value = additionalData.containsKey(personKey)? additionalData.get(personKey):"";

				// Überprüfe, ob der Wert null ist
				String formattedValue = (value != null) ? value.toString() : "";

				// writing values into the new column
				writer.write(line + ";" + formattedValue);
				writer.newLine();
			}
		}

		// Rewrite original file by temp file
		Files.move(tempOutputPath, outputAgentLiveabilityCSVPath, StandardCopyOption.REPLACE_EXISTING);
	}

	public void generateSummaryTilesFile() throws IOException {

	//	Path categoryRankingCsvPath = output.getPath("summaryTiles.csv");

		// Wenn die Datei existiert, wird sie gelöscht
		if (Files.exists(categoryRankingCsvPath)) {
			Files.delete(categoryRankingCsvPath);
			System.out.println("Die Datei summaryTiles.csv existierte bereits und wurde überschrieben.");
		}

		// Erstellen und initialisieren der leeren CSV-Datei
		try (CSVWriter writer = new CSVWriter(new FileWriter(categoryRankingCsvPath.toFile()))) {
			// Schreiben eines Headers (optional)
			writer.writeNext(new String[]{"Warum ist hier nichts? :("}); // Beispiel für einen Header
			//writer.newLine();

			System.out.println("Die leere Datei summaryTiles.csv wurde erstellt unter: " + categoryRankingCsvPath);
		}
	}

	public void extendSummaryTilesCsvWithAttribute(String RankingValue, String CategoryName) throws IOException {
	//	Path inputSummaryTileCsvPath = output.getPath("summaryTiles.csv");
	//	Path tempSummaryTilesOutputPath = inputSummaryTileCsvPath.resolveSibling("summaryTiles_updated.csv");

			try (CSVReader tilesReader = new CSVReader(new FileReader(categoryRankingCsvPath.toFile()));
				 CSVWriter tilesWriter = new CSVWriter(new FileWriter(tempSummaryTilesOutputPath.toFile()))) {

				// Kopiere bestehende Zeilen
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
		// Rewrite original file by temp file
		Files.move(tempSummaryTilesOutputPath, categoryRankingCsvPath, StandardCopyOption.REPLACE_EXISTING);

	}

}
