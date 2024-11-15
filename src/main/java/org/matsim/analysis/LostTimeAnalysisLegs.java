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

public class LostTimeAnalysisLegs {

	public static void main(String[] args) {

		// 1. Netzwerk laden
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; // Dein Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		// 2. Definiere die Koordinaten der zwei Punkte
		String legsCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv";
		String outputCsvFile = "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legsLostTimeTest.csv";
		//Coord point1 = new Coord(802507.1816726763, 5804976.895634595);
		//Coord point2 = new Coord(784590.01, 5790196.3);

		// 3. CSV-Datei einlesen und für jedes Leg die Reisezeit berechnen
		try (BufferedReader br = new BufferedReader(new FileReader(legsCsvFile));
			 BufferedWriter bw = new BufferedWriter(new FileWriter(outputCsvFile))) {

			// Überspringe die Header-Zeile
			String line = br.readLine(); // Lese und ignoriere die Header-Zeile


			// Schreibe Header in die neue Datei
			bw.write("person;trip_id;mode;dep_time;start_x;start_y;end_x;end_y;trav_time;freeSpeedTravelTime;lostTime;lostTimeHMS\n");

			for ( int i  = 0; i < 10 && (line = br.readLine()) != null; i++ ){
		//	while ((line = br.readLine()) != null) {
				// Zeile parsen und in Felder aufteilen
				String[] values = line.split(";");  // Spalten trennen (falls durch Komma getrennt)

				// Überprüfe, ob die Zeile genügend Spalten hat
				if (values.length < 13) {
					System.out.println("Zeile hat nicht genug Spalten: " + line);
					continue; // Überspringe diese Zeile und fahre mit der nächsten fort
				}

				// Annahme: CSV-Format: start_x, start_y, end_x, end_y, trav_time
				double startX = Double.parseDouble(values[8]);
				double startY = Double.parseDouble(values[9]);
				double endX = Double.parseDouble(values[11]);
				double endY = Double.parseDouble(values[12]);
				LocalTime travTime = LocalTime.parse(values[3]);  // Bereits existierende travel time in der CSV
				String person = values[0];
				String tripId = values[1];
				String mode = values[6];
				String depTime = values[2];




				// 4. Berechne die Reisezeit im FreeSpeed-Modus
				Coord point1 = new Coord(startX, startY);
				Coord point2 = new Coord(endX, endY);
				double freeSpeedTravelTime = calculateFreeSpeedTravelTime(network, point1, point2);

				long travTimeInSeconds = localTimeToSeconds(travTime);
				// 5. Berechne die verlorene Zeit (LostTime) als Differenz
				long lostTime = travTimeInSeconds - (long) freeSpeedTravelTime;
				// Überprüfe, ob die LostTime negativ ist
				if (lostTime < 0) {
					// Warnung ausgeben, dass die Zeit negativ ist
					//System.out.println("Warnung: Negative Lost Time! Berechnete Lost Time: " + lostTime + " Sekunden.");
					lostTime = 0;  // Setze die Lost Time auf 0, wenn sie negativ ist
				}

				// Wandelt die Sekunden in LocalTime um (nur für positive Werte)
				Time lostTimeHMS = new Time(lostTime);
				// 6. Schreibe die neue Zeile in die Ausgabe-CSV
				bw.write(String.format("%s;%s;%s;%s;%f;%f;%f;%f;%s;%f;%d;%s\n", person, tripId, mode, depTime.toString(),startX, startY, endX, endY, travTime.toString(), freeSpeedTravelTime, lostTime, lostTimeHMS));
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
		double travelTime = 0.0;
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
