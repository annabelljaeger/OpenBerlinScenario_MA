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
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	produces = {
		"output_legsLostTime_Test3.csv",
		"summary_modeSpecificLegsLossTime.csv"
	}
)

public class LostTimeAnalysisLegs_ModeSpecific_adapted implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(LostTimeAnalysisLegs_ModeSpecific_adapted.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(LostTimeAnalysisLegs_ModeSpecific_adapted.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(LostTimeAnalysisLegs_ModeSpecific_adapted.class);

	//	public static void main(String[] args) {
	public static void main(String[] args) {
		new LostTimeAnalysisLegs_ModeSpecific_adapted().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		//Netzwerk laden & NetworkCleaner laufen lassen
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; //Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		//legs.csv als Inputfile laden und Output-path festlegen
		String inputLegsCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv";
		String outputCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTime_Test3.csv";
		String outputSummaryFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/summary_modeSpecificLegsLossTime.csv";
		Path outputSummaryPath = output.getPath("summary_modeSpecificLegsLossTime.csv");


		//Input-CSV-Datei einlesen und für jedes Leg die Reisezeit berechnen
		try (BufferedReader br = new BufferedReader(new FileReader(inputLegsCsvFile));
			 BufferedWriter bw = new BufferedWriter(new FileWriter(outputCsvFile))) {

			// Header-Zeile lesen und überspringen
			String line = br.readLine();

			//Map für kumulierte LossTime-Summen
			Map<String, Long> cumulativeLossTime = new HashMap<>();
			Map<String, Long> failedRoutingOccurances = new HashMap<>();

			// Spalten-header für neue output-Datei festlegen (Semikolongetrennt)
			bw.write("person;trip_id;mode;trav_time;fs_trav_time;lost_time;trav_time_hms;fs_trav_time_hms;lost_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			// mittels for-Schleife über alle Legs-Einträge iterieren und die Werte berechnen
			for (int i = 0; i < 6000 && (line = br.readLine()) != null; i++) {
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
/*
				// Formatierte Ausgabe für Lost_Time
				String formattedLostTime = (lossTimeInSeconds != null)
					? formatDuration(Duration.ofSeconds(lossTimeInSeconds))
					: "NULL";
*/
				if (lossTimeInSeconds != null && lossTimeInSeconds < 0) {
					lossTimeInSeconds = 0L;
				}

				// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
				Duration lostTimeHMS = Duration.ofSeconds(lossTimeInSeconds);
				long hours_lt = lostTimeHMS.toHours();
				long minutes_lt = lostTimeHMS.toMinutes() % 60;
				long seconds_lt = lostTimeHMS.getSeconds() % 60;
				String formattedLostTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);


				if (lossTimeInSeconds != null) {
					cumulativeLossTime.put(mode, cumulativeLossTime.getOrDefault(mode, 0L) + lossTimeInSeconds);
				} else {
					System.out.println("Warnung: Loss Time für Modus" + mode + " ist null.");
				}
				//Die neue Zeile in die Ausgabe-CSV schreiben
				bw.write(String.format("%s;%s;%d;%d;%d;%s;%s;%s;%s;%f;%f;%s;%s;%f;%f;%s;%s\n", tripId, mode, travTimeInSeconds, freeSpeedTravelTimeInSeconds, lossTimeInSeconds,
					formattedTravTime, formattedFreeSpeedTravTime, formattedLostTime, depTime,
					startX, startY, startNodeFound.getId(), startLink, endX, endY, endNodeFound.getId(), endLink));
			}


			try (BufferedWriter summaryBw = new BufferedWriter(Files.newBufferedWriter(outputSummaryPath))) {
				summaryBw.write("mode;cumulative_loss_time;failed_routings\n");
				//for (Map.Entry<String, Long> entry : cumulativeLossTime.entrySet()) {
				//		summaryBw.write(String.format("%s;%d\n", entry.getKey(), entry.getValue()));
				//		}
				for (String mode : cumulativeLossTime.keySet()) {
					long cumulativeLoss = cumulativeLossTime.getOrDefault(mode, 0L);
					long failedCount = failedRoutingOccurances.getOrDefault(mode, 0L);
					summaryBw.write(String.format("%s;%d;%d\n", mode, cumulativeLoss, failedCount));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}


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
		double sum = 0.0;

		Path filePath = outputDirectory.resolve("summary_modeSpecificLegsLossTime.csv");
		if (!Files.exists(filePath)) {
			throw new IOException(("Die Datei summary_modeSpecificLegsLossTime.csv wurde im Ordner nicht gefunden: " + filePath.toAbsolutePath()));
		}
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(filePath),CSVFormat.DEFAULT.withFirstRecordAsHeader())){
			for (CSVRecord record : parser) {
				String value = record.get("cumulative_loss_time");
				sum += Double.parseDouble(value);
			}
		}
	catch (NumberFormatException e) {
		throw new IOException("Fehler beim Parsen der Werte in der Datei: " + e.getMessage(), e);
	}
		return sum;
	}

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
