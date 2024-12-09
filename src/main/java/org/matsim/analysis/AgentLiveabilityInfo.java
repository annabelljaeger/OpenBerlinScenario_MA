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
		"agentLiveabilityInfo.csv"
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

	public static void extendCsvWithAttribute(Map<String, ?> additionalData, String newAttributeName) throws IOException {
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


}
