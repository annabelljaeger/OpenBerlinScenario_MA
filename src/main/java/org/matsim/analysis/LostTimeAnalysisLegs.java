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
import java.time.LocalTime;
import java.time.Duration;

public class LostTimeAnalysisLegs {

	public static void main(String[] args) {

		// 1. Netzwerk laden & NetworkCleaner laufen lassen
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; // Dein Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		// 2. legs.csv als Inputfile laden und Output-path festlegen
		String legsCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv";
		String outputCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTimeErsterDurchlaufGesamt.csv";

		// 3. CSV-Datei einlesen und für jedes Leg die Reisezeit berechnen
		try (BufferedReader br = new BufferedReader(new FileReader(legsCsvFile));
			 BufferedWriter bw = new BufferedWriter(new FileWriter(outputCsvFile))) {

			// Header-Zeile überspringen
			String line = br.readLine(); // Lese und ignoriere die Header-Zeile


			// Spalten-header für neue output-Datei festlegen (Semikolongetrennt)
			bw.write("person;trip_id;mode;trav_time;fs_trav_time;lost_time;trav_time_hms;fs_trav_time_hms;lost_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			// mittels for-Schleife über alle Legs-Einträge iterieren und die Werte berechnen
		//	for ( int i  = 0; i < 10 && (line = br.readLine()) != null; i++ ){
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

				long freeSpeedTravelTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, point1, point2);
				Duration fsTravTimeHMS = Duration.ofSeconds(freeSpeedTravelTimeInSeconds);
				long hours_fs = fsTravTimeHMS.toHours();
				long minutes_fs = fsTravTimeHMS.toMinutes() % 60;
				long seconds_fs = fsTravTimeHMS.getSeconds() % 60;
				String formattedFreeSpeedTravTime = String.format("%02d:%02d:%02d", hours_fs, minutes_fs, seconds_fs);

				long travTimeInSeconds = travTime.getSeconds();
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
					startX, startY, startNodeFound, startLink, endX, endY, endNodeFound, endLink));

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
	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2) {
		Node startNode = getClosestNode(network, point1);
		Node endNode = getClosestNode(network, point2);

		// TravelTime-Implementierung für Freifahrtgeschwindigkeit
		TravelTime freeSpeedTravelTime = (link, time, person, vehicle) -> link.getLength() / link.getFreespeed();

		// TravelDisutility-Implementierung
		TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(freeSpeedTravelTime);

		// Verwende die Factory für die Dijkstra-Initialisierung
		LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, freeSpeedTravelTime);

		// Pfad berechnen
		LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, 0, null, null);

		if (path == null) {
			System.out.println("Keine Route gefunden. Überprüfen Sie das Netzwerk auf Verbindungen und unterstützte Modi.");
			return -1; // Verhindert, dass die Berechnung mit einer Null-Pointer-Exception endet
		}

// Berechne die Reisezeit nur, wenn der Pfad vorhanden ist
		long travelTime = 0;
		for (Link link : path.links) {
			travelTime += link.getLength() / link.getFreespeed();
		}

		return travelTime;
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
