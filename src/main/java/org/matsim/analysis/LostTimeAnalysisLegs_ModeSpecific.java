package org.matsim.analysis;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.*;
import java.time.Duration;
import java.time.LocalTime;

public class LostTimeAnalysisLegs_ModeSpecific {

	public static void main(String[] args) {

		// 1. Netzwerk laden & NetworkCleaner laufen lassen
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; // Dein Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		// 2. legs.csv als Inputfile laden und Output-path festlegen
		String legsCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv";
		String outputCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTimeModespecificZweiterDurchlauf.csv";

		// 3. CSV-Datei einlesen und für jedes Leg die Reisezeit berechnen
		try (BufferedReader br = new BufferedReader(new FileReader(legsCsvFile));
			 BufferedWriter bw = new BufferedWriter(new FileWriter(outputCsvFile))) {

			// Header-Zeile überspringen
			String line = br.readLine(); // Lese und ignoriere die Header-Zeile


			// Spalten-header für neue output-Datei festlegen (Semikolongetrennt)
			bw.write("person;trip_id;mode;trav_time;fs_trav_time;lost_time;trav_time_hms;fs_trav_time_hms;lost_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			// mittels for-Schleife über alle Legs-Einträge iterieren und die Werte berechnen
		//	for ( int i  = 0; i < 30 && (line = br.readLine()) != null; i++ ){
				for (; (line = br.readLine()) != null;){
				// Zeile parsen und in Felder aufteilen (legs.csv ist Semikolon-getrennt)
				String[] values = line.split(";");

				// Aufbau der neuen output-Datei - dafür Werte aus bestehender legs.csv über Spaltennummer als Werte festlegen
				double startX = Double.parseDouble(values[8]);
				double startY = Double.parseDouble(values[9]);
				String startLink = values[7];
				double endX = Double.parseDouble(values[11]);
				double endY = Double.parseDouble(values[12]);
				String endLink = values[10];

			//	String travTimeString = values[3].replace(":", "H") + "M0S";  // Beispiel: "27:58:32" -> "PT27H58M32S"
			//	Duration travTime = Duration.parse("PT" + travTimeString);  // Parse in Duration
			//	Duration travTime = Duration.parse("PT" + values[3].replace(":", "H").replace(":", "M") + "S");

// Umwandlung der travTime (hh:mm:ss) in das Format PTnHnMnS für Duration
				String travTimeString = values[3];
/*
// Überprüfen, ob die Zeit im richtigen Format vorliegt (hh:mm:ss)
				if (travTimeString != null && travTimeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
					// Umwandlung von hh:mm:ss in PTnHnMnS
					String[] timeParts = travTimeString.split(":");
					// Die Teile des Strings in Stunden, Minuten und Sekunden aufteilen
					travTimeString = "PT" + timeParts[0] + "H" + timeParts[1] + "M" + timeParts[2] + "S";
				} else {
					// Falls das Format nicht stimmt, eine Ausnahme werfen oder eine Standardzeit festlegen
					System.out.println("Ungültiges Zeitformat: " + travTimeString);
					continue;  // Zum nächsten Durchlauf der Schleife gehen, wenn das Format nicht korrekt ist
				}
*/
				// Umwandlung in Duration
				if (travTimeString != null && travTimeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
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


				// 4. Berechne die Reisezeit im FreeSpeed-Modus
				Coord point1 = new Coord(startX, startY);
				Coord point2 = new Coord(endX, endY);

				Node startNodeFound = getClosestNode(network, point1);

				Node endNodeFound = getClosestNode(network, point2);

				long travTimeInSeconds = travTime.getSeconds();

				long freeSpeedTravelTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, point1, point2, mode, travTimeInSeconds);

				Duration fsTravTimeHMS = Duration.ofSeconds(freeSpeedTravelTimeInSeconds);
				long hours_fs = fsTravTimeHMS.toHours();
				long minutes_fs = fsTravTimeHMS.toMinutes() % 60;
				long seconds_fs = fsTravTimeHMS.getSeconds() % 60;
				String formattedFreeSpeedTravTime = String.format("%02d:%02d:%02d", hours_fs, minutes_fs, seconds_fs);

					// 5. Berechne die Verlustzeit (LostTime) als Differenz
				long lostTimeInSeconds = travTimeInSeconds - freeSpeedTravelTimeInSeconds;
				// Überprüfe, ob die LostTime negativ ist
				if (lostTimeInSeconds < 0) {
					// Warnung ausgeben, dass die Zeit negativ ist
					//System.out.println("Warnung: Negative Lost Time! Berechnete Lost Time: " + lostTime + " Sekunden.");
					lostTimeInSeconds = 0;  // Setze die Lost Time auf 0, wenn sie negativ ist
				}

				// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
				Duration lostTimeHMS = Duration.ofSeconds(lostTimeInSeconds);
				long hours_lt = lostTimeHMS.toHours();
				long minutes_lt = lostTimeHMS.toMinutes() % 60;
				long seconds_lt = lostTimeHMS.getSeconds() % 60;
				String formattedLostTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);


				// 6. Schreibe die neue Zeile in die Ausgabe-CSV
				bw.write(String.format("%s;%s;%s;%d;%d;%d;%s;%s;%s;%s;%f;%f;%s;%s;%f;%f;%s;%s\n", person, tripId, mode, travTimeInSeconds, freeSpeedTravelTimeInSeconds, lostTimeInSeconds,
					formattedTravTime,formattedFreeSpeedTravTime, formattedLostTime, depTime,
					startX, startY, startNodeFound.getId(), startLink, endX, endY, endNodeFound.getId(), endLink));

			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}

	private static long localTimeToSeconds(LocalTime time) {
		return time.toSecondOfDay(); // Umwandlung von LocalTime in Sekunden seit Mitternacht
	}
	// Umwandlung von Sekunden in LocalTime
	/*
	private static LocalTime secondsToLocalTime(long seconds) {
		int hours = (int) (seconds / 3600);
		int minutes = (int) ((seconds % 3600) / 60);
		int remainingSeconds = (int) (seconds % 60);
		return LocalTime.of(hours, minutes, remainingSeconds);
	}
*/
	private static LocalTime secondsToLocalTime(long seconds) {
		boolean isNegative = seconds < 0;  // Überprüfe, ob die Zeit negativ ist
		if (isNegative) {
			seconds = Math.abs(seconds);  // Mache die Sekunden positiv, um sie zu formatieren
		}

		// Berechne Stunden, Minuten und Sekunden
		long hours = seconds / 3600;
		seconds %= 3600;
		long minutes = seconds / 60;
		seconds %= 60;
		long remainingSeconds = seconds;

		return LocalTime.of((int) hours, (int) minutes, (int) remainingSeconds);
		/*
		// Erstelle eine benutzerdefinierte Zeitdarstellung
		StringBuilder timeString = new StringBuilder();
		if (isNegative) {
			timeString.append("-");  // Wenn die Zeit negativ war, füge ein Minuszeichen hinzu
		}
		timeString.append(String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds));

		return timeString.toString();

		 */
	}

	// Berechnung der Reisezeit auf der Strecke (FreeSpeed-Modus)
	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2, String mode, long InputTravTimeInSeconds) {
		double travelTimeInSeconds = 0;
		double beelineFactor = 1.3; // Faktor für bike und walk

		switch (mode.toLowerCase()) {
			case "bike":
			case "walk":
				double distance = CoordUtils.calcEuclideanDistance(point1, point2);

				// Berücksichtige den Beeline-Faktor
				distance *= beelineFactor;

				// Durchschnittsgeschwindigkeit festlegen
				double averageSpeed = mode.equals("bike") ? 3.1388889 : 1.23; // 5 m/s für bike, 1.4 m/s für walk
				travelTimeInSeconds = distance / averageSpeed;
				break;

			case "car":
			case "freight":
			case "truck":
			case "ride":
				Node startNode = getClosestNode(network, point1);
				Node endNode = getClosestNode(network, point2);

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



	// Finde den nächsten Knoten für eine gegebene Koordinate
	private static Node getClosestNode(Network network, Coord point) {
		Node closestNode = null;
		double minDistance = Double.MAX_VALUE;
		for (Node node : network.getNodes().values()) {
			double distance = CoordUtils.calcEuclideanDistance(point, node.getCoord());
			if (distance < minDistance) {
				minDistance = distance;
				closestNode = node;
			}
		}
		return closestNode;
	}
}
