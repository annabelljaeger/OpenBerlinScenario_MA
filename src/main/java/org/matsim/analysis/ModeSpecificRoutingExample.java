package org.matsim.analysis;
/*
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutilityFactory;
import org.matsim.core.router.util.DefaultTravelTime;
import org.matsim.core.router.util.FreeSpeedTravelTime;

public class ModeSpecificRoutingExample {

	public static void main(String[] args) {
		// Beispiel-Modus
		String mode = "car"; // Kann aus einer CSV geladen werden (z.B. "bike", "walk", etc.)

		// Netzwerk und TravelTime-Objekte laden
		Network network = loadNetwork("path/to/network.xml");
		TravelTime travelTime = getTravelTimeForMode(mode);
		TravelDisutility travelDisutility = getTravelDisutilityForMode(mode);

		// Router erstellen
		LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, travelTime);

		// Beispiel-Koordinaten für Start- und Endpunkt
		Coord point1 = new Coord(802507.1816726763, 5804976.895634595);
		Coord point2 = new Coord(784590.01, 5790196.3);

		Node startNode = getClosestNode(network, point1);
		Node endNode = getClosestNode(network, point2);

		// Pfad berechnen
		LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, 0, null, null);

		// Reisezeit ausgeben
		if (path != null) {
			double travelTimeInSeconds = calculateTravelTime(path);
			System.out.println("Die Reisezeit von Punkt 1 zu Punkt 2 im " + mode + " Modus beträgt: " + travelTimeInSeconds + " Sekunden.");
		} else {
			System.out.println("Keine Route gefunden.");
		}
	}

	// TravelTime für den Modus auswählen
	private static TravelTime getTravelTimeForMode(String mode) {
		switch (mode) {
			case "car":
				return new FreeSpeedTravelTime();
			case "bike":
				return new BikeTravelTime();
			case "walk":
				return new WalkTravelTime();
			default:
				throw new IllegalArgumentException("Modus nicht unterstützt: " + mode);
		}
	}

	// TravelDisutility für den Modus auswählen
	private static TravelDisutility getTravelDisutilityForMode(String mode) {
		switch (mode) {
			case "car":
				return new OnlyTimeDependentTravelDisutility(new FreeSpeedTravelTime());
			case "bike":
				return new BikeTravelDisutility();
			case "walk":
				return new WalkTravelDisutility();
			default:
				throw new IllegalArgumentException("Modus nicht unterstützt: " + mode);
		}
	}

	// Methode zum Berechnen der Reisezeit für den Pfad
	private static double calculateTravelTime(LeastCostPathCalculator.Path path) {
		double time = 0.0;
		for (Link link : path.links) {
			time += link.getLength() / link.getFreespeed(); // Zum Beispiel für "car"
		}
		return time;
	}

	// Weitere Methoden zum Laden des Netzwerks und der Knoten...
}
*/
