package org.matsim.analysis;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.modechoice.commands.StrategyOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.*;

import java.io.BufferedWriter;
import java.io.IOException;


@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	requires = {
		"berlin-v6.3.output_legs.csv.gz"
	},
	produces = {
		"output_legsLossTime_new.csv",
		"summary_modeSpecificLegsLossTime.csv",
		"lossTime_stats_perAgent.csv",
		"lossTime_RankingValue.csv"
	}
)

public class AgentBasedLossTimeAnalysis implements MATSimAppCommand {

//	private static final Logger log = LogManager.getLogger(AgentBasedLossTimeAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedLossTimeAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedLossTimeAnalysis.class);

	//	public static void main(String[] args) {
	public static void main(String[] args) {
		new AgentBasedLossTimeAnalysis().execute(args);
	}

	Path outputRankingValuePath = output.getPath("lossTime_RankingValue.csv");

	@Override
	public Integer call() throws Exception {

		//Netzwerk laden & NetworkCleaner laufen lassen
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; //Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		//legs.csv als Inputfile laden und Output-path festlegen
		//	Path inputLegsCSVPath = Path.of(input.getPath("berlin-v6.3.output_legs.csv.gz"));
		String inputLegsCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv";
		Path outputSummaryPath = output.getPath("summary_modeSpecificLegsLossTime.csv");
		Path outputCSVPath = output.getPath("output_legsLossTime_new.csv");
		Path outputRankingAgentStatsPath = output.getPath("lossTime_stats_perAgent.csv");
		Path outputRankingValuePath = output.getPath("lossTime_RankingValue.csv");

		//	Path inputLegsCSVPath = Path.of(input.getPath("berlin-v6.3.output_legs.csv.gz"));

		//Input-CSV-Datei einlesen und für jedes Leg die Reisezeit berechnen
		try (BufferedReader br = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));
			 //	try (BufferedReader br = new BufferedReader(new FileReader(String.valueOf(inputLegsCSVPath)));
			 //	 BufferedWriter bw = new BufferedWriter(new FileWriter(outputCsvFile))) {
			 BufferedWriter bw = new BufferedWriter(Files.newBufferedWriter(outputCSVPath))) {

			// Header-Zeile lesen und überspringen
			String line = br.readLine();

			//Map für kumulierte LossTime-Summen
			Map<String, Long> cumulativeLossTime = new HashMap<>();
			Map<String, Long> failedRoutingOccurances = new HashMap<>();
			Map<String, Double> sumTravTimePerAgent = new HashMap<>();
			Map<String, Double> sumLossTimePerAgent = new HashMap<>();
			Map<String, Set<String>> modePerPerson = new HashMap<>();


			// Spalten-header für neue output-Datei festlegen (Semikolongetrennt)
			bw.write("person;trip_id;mode;trav_time;fs_trav_time;loss_time;percent_lossTime;trav_time_hms;fs_trav_time_hms;loss_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			// mittels for-Schleife über alle Legs-Einträge iterieren und die Werte berechnen
			for (int i = 0; i < 600 && (line = br.readLine()) != null; i++) {
				//		for (; (line = br.readLine()) != null;){
				// Zeile parsen und in Felder aufteilen (legs.csv ist Semikolon-getrennt)
				String[] values = line.split(";");

				// Aufbau der neuen output-Datei - dafür Werte aus bestehender legs.csv über Spaltennummer als Werte festlegen
				double startX = Double.parseDouble(values[8]);
				double startY = Double.parseDouble(values[9]);
				String startLink = values[7];
				double endX = Double.parseDouble(values[11]);
				double endY = Double.parseDouble(values[12]);
				String endLink = values[10];

				// Umwandlung der travTime (hh:mm:ss) in das Format PTnHnMnS (Duration)
				String travTimeString = values[3];

				if (travTimeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
					String[] timeParts = travTimeString.split(":");
					travTimeString = "PT" + timeParts[0] + "H" + timeParts[1] + "M" + timeParts[2] + "S";
				} else {
					System.out.println("Ungültiges Zeitformat: " + travTimeString);
					continue;
				}

				// Duration aus dem umgewandelten String parsen
				Duration travTime = Duration.parse(travTimeString);
				long hours = travTime.toHours();
				long minutes = travTime.toMinutes() % 60;
				long seconds = travTime.getSeconds() % 60;
				String formattedTravTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

				String person = values[0];
				String tripId = values[1];
				String mode = values[6];
				String depTime = values[2];

				//Reisezeit im FreeSpeed-Modus berechnen
				Coord point1 = new Coord(startX, startY);
				Coord point2 = new Coord(endX, endY);

				Node startNodeFound = NetworkUtils.getNearestNode(network, point1);
				Node endNodeFound = NetworkUtils.getNearestNode(network, point2);

				long travTimeInSeconds = travTime.getSeconds();

				long freeSpeedTravelTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, point1, point2, mode, travTimeInSeconds);

				Duration fsTravTimeHMS = Duration.ofSeconds(freeSpeedTravelTimeInSeconds);
				long hours_fs = fsTravTimeHMS.toHours();
				long minutes_fs = fsTravTimeHMS.toMinutes() % 60;
				long seconds_fs = fsTravTimeHMS.getSeconds() % 60;
				String formattedFreeSpeedTravTime = String.format("%02d:%02d:%02d", hours_fs, minutes_fs, seconds_fs);

				//Verlustzeit (LossTime) als Differenz berechnen
				Long lossTimeInSeconds;

				if (freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
					lossTimeInSeconds = travTimeInSeconds - freeSpeedTravelTimeInSeconds;
					//			if (lossTimeInSeconds < 0) lossTimeInSeconds = 0L; //falsche Differenzen vermeiden

				} else {
					lossTimeInSeconds = 0L;
					failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
				}

				if (lossTimeInSeconds != null && lossTimeInSeconds < 0) {
					lossTimeInSeconds = 0L;
				}

				// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
				Duration lostTimeHMS = Duration.ofSeconds(lossTimeInSeconds);
				long hours_lt = lostTimeHMS.toHours();
				long minutes_lt = lostTimeHMS.toMinutes() % 60;
				long seconds_lt = lostTimeHMS.getSeconds() % 60;
				String formattedLostTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);

				//Prozentuale Loss Time ausgeben
				double percentLossTime = 0.0;
				if (freeSpeedTravelTimeInSeconds != 0 && freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
					percentLossTime = (double) lossTimeInSeconds / freeSpeedTravelTimeInSeconds;
				} else {
					System.out.println("Warnung: Division by Zero for trip " + tripId + " avoided.");

				}

				if (lossTimeInSeconds != null) {
					cumulativeLossTime.put(mode, cumulativeLossTime.getOrDefault(mode, 0L) + lossTimeInSeconds);
				} else {
					System.out.println("Warnung: Loss Time für Modus" + mode + " ist null.");
				}

				sumLossTimePerAgent.put(person, (double) (sumLossTimePerAgent.getOrDefault((Object) person, (double) 0L) + lossTimeInSeconds));

				sumTravTimePerAgent.put(person, (double) (sumTravTimePerAgent.getOrDefault((Object) person, (double) 0L) + travTimeInSeconds));

				//modePerPerson.put(person, (String) (modePerPerson.getOrDefault((Object) person, (String) "")+mode));
				modePerPerson.computeIfAbsent(person, k -> new HashSet<>()).add(mode);

				//Die neue Zeile in die Ausgabe-CSV schreiben
				bw.write(String.format("%s;%s;%s;%s;%d;%d;%f;%s;%s;%s;%s;%f;%f;%s;%s;%f;%f;%s;%s\n", person, tripId, mode, travTimeInSeconds, freeSpeedTravelTimeInSeconds, lossTimeInSeconds,
					percentLossTime, formattedTravTime, formattedFreeSpeedTravTime, formattedLostTime, depTime,
					startX, startY, startNodeFound.getId(), startLink, endX, endY, endNodeFound.getId(), endLink));
			}


			try (BufferedWriter summaryBw = new BufferedWriter(Files.newBufferedWriter(outputSummaryPath))) {
				summaryBw.write("mode;cumulative_loss_time;failed_routings\n");

				for (String mode : cumulativeLossTime.keySet()) {
					long cumulativeLoss = cumulativeLossTime.getOrDefault(mode, 0L);
					long failedCount = failedRoutingOccurances.getOrDefault(mode, 0L);
					summaryBw.write(String.format("%s;%d;%d\n", mode, cumulativeLoss, failedCount));
				}
			}

			//	try (BufferedReader br = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));

			//Tabelle mit Summen pro Person - aus diesen können die Agentenbasierte True/False Werte gebildet werden und aus deren Summe der Ranking-Prozentsatz
			try (BufferedWriter agentBasedBw = new BufferedWriter(Files.newBufferedWriter(outputRankingAgentStatsPath))) {
				agentBasedBw.write("Person;lossTimePerAgent;travTimePerAgent;percentageLossTime;rankingStatus;modesUsed\n");


				for (String person : sumLossTimePerAgent.keySet()) {
					double lossTimePerAgent = sumLossTimePerAgent.getOrDefault((Object) person, 0.0);
					double travTimePerAgent = sumTravTimePerAgent.getOrDefault((Object) person, 0.0);
					double percentageLossTime = lossTimePerAgent / travTimePerAgent;
					//	String modeUsed = modePerPerson.getOrDefault((Object) person, "");
					String modeUsed = String.join(",", modePerPerson.getOrDefault(person, Set.of()));
					boolean rankingStatus = false;
					if (percentageLossTime < 0.15) {
						rankingStatus = true;
					}
					agentBasedBw.write(String.format("%s;%f;%f;%f;%s;%s \n", person, lossTimePerAgent, travTimePerAgent, percentageLossTime, rankingStatus, modeUsed));
				}
			}

			// Summiere die Spalte loss_time aus der Datei output_legsLossTime_new.csv
			try (BufferedReader reader = Files.newBufferedReader(outputCSVPath)) {
				long totalLossTimeInSeconds = 0;

				// Überspringe die Header-Zeile
				reader.readLine();

				// Iteriere über alle Zeilen der CSV
				while ((line = reader.readLine()) != null) {
					String[] values = line.split(";");
					String lossTimeValue = values[5]; // Spalte "loss_time"
					if (!lossTimeValue.isEmpty()) {
						totalLossTimeInSeconds += Long.parseLong(lossTimeValue); // Aufsummieren
					}
				}

				// Konvertiere die Gesamtsumme in das Format HH:mm:ss
				Duration totalLossTime = Duration.ofSeconds(totalLossTimeInSeconds);
				long hours = totalLossTime.toHours();
				long minutes = totalLossTime.toMinutes() % 60;
				long seconds = totalLossTime.getSeconds() % 60;
				String formattedTotalLossTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

				try (BufferedReader agentBasedReader = Files.newBufferedReader(outputRankingAgentStatsPath)) {
					String entry;
					int totalEntries = 0;
					int trueEntries = 0;

					// Überspringen der Header-Zeile
					agentBasedReader.readLine();

					// Iteration über alle Zeilen
					while ((entry = agentBasedReader.readLine()) != null) {
						String[] values = entry.split(";");
						// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
						if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
							trueEntries++;
						}
						totalEntries++;
					}

					// Anteil der True-Einträge berechnen
					double rankingLossTime = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
					String formattedRankingLossTime = String.format(Locale.US, "%.2f%%", rankingLossTime);

					// Schreibe das Ergebnis zusammen mit rankingLossTime in die Datei lossTime_RankingValue.csv
					try (BufferedWriter writer = Files.newBufferedWriter(outputRankingValuePath)) {
					//	writer.write("Dimension;Value\n"); // Header
						writer.write(String.format("LossTimeRanking;%s\n", formattedRankingLossTime)); // Ranking
						writer.write(String.format("LossTimeSum;%s\n", formattedTotalLossTime)); // Summe der Verlustzeit
					}

					System.out.println("LossTimeSum (HH:mm:ss): " + formattedTotalLossTime);
					System.out.println("LossTimeRanking: " + formattedRankingLossTime);

				} catch (IOException e) {
					e.printStackTrace();
				}


				return 0;
			}
		}
	}


/*
				try (BufferedReader legsLossTimeReader = Files.newBufferedReader(outputCSVPath)) {
					String lines;
					Long sumLossTime = 0L;
					// Überspringen der Header-Zeile
					agentBasedReader.readLine();
					// Iteration über alle Zeilen
					while ((entry = agentBasedReader.readLine()) != null) {
						String[] values = entry.split(";");

						sumLossTime += (Long) values[5];
						// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
						if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
							trueEntries++;
						}
						totalEntries++;
					}

					// Ergebnis in die Datei schreiben
					try (BufferedWriter lossTimeRankingBw = Files.newBufferedWriter(outputRankingValuePath)) {
						//	lossTimeRankingBw.write("Dimension;RankingPercentage\n");

					String dimension = "LossTimeAgentRanking";
					// Prozentsatz formatieren und schreiben (z. B. 12.34%)
					String formattedRankingLossTime = String.format(Locale.US, "%.2f%%", rankingLossTime);
				//	lossTimeRankingBw.write(String.format("%s;%s\n", dimension, formattedRankingLossTime));
				//	System.out.println("Info: Loss Time Ranking Wert ist "+formattedRankingLossTime);

//						String sum = "LossTimeSum";

//						lossTimeRankingBw.write(String.format("%s;%s\n", sum, sumLossTime));
//						System.out.println("Info: Loss Time Gesamtsumme ist " + sumLossTime);
					}

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
				try (CSVParser parser = new CSVParser(Files.newBufferedReader(outputCSVPath), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
					Long sumLossTime = 0L;

					for (CSVRecord record : parser) {
						String value = record.get("loss_time");
						sumLossTime += Long.parseLong(value);
					}
					// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
					Duration sumLossTimeHMS = Duration.ofSeconds(sumLossTime);
					long hours_lt = sumLossTimeHMS.toHours();
					long minutes_lt = sumLossTimeHMS.toMinutes() % 60;
					long seconds_lt = sumLossTimeHMS.getSeconds() % 60;
					String formattedSumLossTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);

*/








	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}





	// Berechnung der Reisezeit auf der Strecke (FreeSpeed-Modus)
	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2, String mode, long InputTravTimeInSeconds) {
		double travelTimeInSeconds = 0;
		double beelineFactor = 1.3; // Faktor für bike und walk

		switch (mode.toLowerCase()) {
			case "bike":
			case "walk":
				double distance = CoordUtils.calcEuclideanDistance(point1, point2);

				// Beeline-Faktor berücksichtigen
				distance *= beelineFactor;

				// Durchschnittsgeschwindigkeit festlegen
				double averageSpeed = mode.equals("bike") ? 3.1388889 : 1.23; // 5 m/s für bike, 1.4 m/s für walk
				travelTimeInSeconds = distance / averageSpeed;
				break;

			case "car":
			case "freight":
			case "truck":
			case "ride":
				Node startNode = NetworkUtils.getNearestNode(network, point1);
				Node endNode = NetworkUtils.getNearestNode(network, point2);

				// TravelTime-Implementierung für Freifahrtgeschwindigkeit
				TravelTime freeSpeedTravelTime = (link, time, person, vehicle) -> {
					if (link.getAllowedModes().contains(mode)) {
						return link.getLength() / link.getFreespeed();
					} else {
						return Double.POSITIVE_INFINITY; // Modus nicht erlaubt
					}
				};

				TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(freeSpeedTravelTime);
				LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, freeSpeedTravelTime);

				LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, 0, null, null);

				if (path == null || path.links.isEmpty()) {
					System.out.println("Keine Route für Modus " + mode + " gefunden.");
					return -1;
				}

				// Verwende die Gesamtzeit der Route, die bereits berechnet wurde
				travelTimeInSeconds = (long) path.travelTime;
				break;

			case "pt":
				return InputTravTimeInSeconds;

			default:
				System.out.println("Ungültiger Modus: " + mode);
				return -2; // Ungültiger Modus
		}
		return travelTimeInSeconds;
	}

	public static double getLossTimeSum(Path outputDirectory) throws IOException {
		int sum = 0;

	//	Path filePath = outputDirectory.resolve("summary_modeSpecificLegsLossTime.csv");
		if (!Files.exists(outputDirectory)) {
			throw new IOException(("Die Datei summary_modeSpecificLegsLossTime.csv wurde im Ordner nicht gefunden: " + outputDirectory.toAbsolutePath()));
		}
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(outputDirectory),CSVFormat.DEFAULT.withFirstRecordAsHeader())){
			for (CSVRecord record : parser) {
				String value = record.get("cumulative_loss_time");
				sum += (int) Double.parseDouble(value);
			}
		}
	catch (NumberFormatException e) {
		throw new IOException("Fehler beim Parsen der Werte in der Datei: " + e.getMessage(), e);
	}
		return sum;
	}
/*
	public double getRankingLossTime() throws IOException {
		double ranklossTime = 0.0;

		String entry;
		int totalEntries = 0;
		int trueEntries = 0;

		try (BufferedReader readerRankLT = Files.newBufferedReader(outputRankingValuePath)) {
			// Überspringen der Header-Zeile
			readerRankLT.readLine();
			// Iteration über alle Zeilen
			while ((entry = readerRankLT.readLine()) != null) {
				String[] values = entry.split(";");
				if (values.length > 4 && "true".equalsIgnoreCase(values[4].trim())) {
					trueEntries++;
				}
				totalEntries++;
			}


			double rankLossTime = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
		}

		return ranklossTime;
	}
*/
	private static Duration parseTime(String timeString) {
		if (timeString != null && timeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
			String[] parts = timeString.split(":");
			return Duration.ofHours(Long.parseLong(parts[0]))
				.plusMinutes(Long.parseLong(parts[1]))
				.plusSeconds(Long.parseLong(parts[2]));
		}
		throw new IllegalArgumentException("Ungültiges Zeitformat: " + timeString);
	}

	private static String formatDuration(Duration duration) {
		long hours = duration.toHours();
		long minutes = duration.toMinutes() % 60;
		long seconds = duration.getSeconds() % 60;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

}
