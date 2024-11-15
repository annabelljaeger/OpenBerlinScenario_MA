import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.utils.geometry.CoordUtils;


public class TestVerlustzeitKoordinaten {

	public static void main(String[] args) {

		// 1. Netzwerk laden
		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; // Dein Netzwerk-Dateipfad
		Network network = loadNetwork(networkFile);

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		// 2. Definiere die Koordinaten der zwei Punkte
		Coord point1 = new Coord(802507.1816726763, 5804976.895634595);
		Coord point2 = new Coord(784590.01, 5790196.3);

		String mode = "car";
		// 3. Berechnung der Reisezeit auf der Strecke
		double travelTime = calculateTravelTime(network, point1, point2);

		// 4. Ausgabe der berechneten Reisezeit
		System.out.println("Die Reisezeit von Punkt 1 zu Punkt 2 im FreeSpeed-Modus beträgt: " + travelTime + " Sekunden.");
	}

	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}

	// Berechnung der Reisezeit auf der Strecke (FreeSpeed-Modus)
	private static double calculateTravelTime(Network network, Coord point1, Coord point2) {
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
