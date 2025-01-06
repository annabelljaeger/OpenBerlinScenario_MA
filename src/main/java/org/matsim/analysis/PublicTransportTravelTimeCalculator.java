//package org.matsim.analysis;
//
//
//
//import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
//import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
//import ch.sbb.matsim.routing.pt.raptor.utils.RaptorUtils;
//import org.matsim.api.core.v01.Coord;
//import org.matsim.api.core.v01.Id;
//import org.matsim.api.core.v01.Scenario;
//import org.matsim.core.config.Config;
//import org.matsim.core.config.ConfigUtils;
//import org.matsim.core.scenario.ScenarioUtils;
//import org.matsim.pt.transitSchedule.api.TransitSchedule;
//import org.matsim.core.router.DefaultRoutingRequest;
//import org.matsim.facilities.ActivityFacility;
//import org.matsim.facilities.ActivityFacilities;
//import org.matsim.core.utils.misc.Time;
//
//import java.util.List;
//import org.matsim.api.core.v01.population.Leg;
//import org.matsim.api.core.v01.population.PlanElement;
//
//public class PublicTransportTravelTimeCalculator {
//
//	private final SwissRailRaptor swissRailRaptor;
//	private final TransitSchedule transitSchedule;
//	private final Config config;
//
//	public PublicTransportTravelTimeCalculator(Scenario scenario) {
//		this.transitSchedule = scenario.getTransitSchedule();
//		this.config = scenario.getConfig();
//		SwissRailRaptorData raptorData = SwissRailRaptorData.create(
//			transitSchedule, null, RaptorUtils.createStaticConfig(config), scenario.getNetwork(), null
//		);
//		this.swissRailRaptor = new SwissRailRaptor.Builder(raptorData, config).build();
//	}
//
//	public int calculatePtTravelTime(Coord start, Coord end, String time) {
//		// Parse time to seconds
//		double departureTime = Time.parseTime(time);
//
//		// Create facilities for start and end points
//		ActivityFacility startFacility = createFakeFacility(start);
//		ActivityFacility endFacility = createFakeFacility(end);
//
//		// Calculate route
//		List<? extends PlanElement> route = swissRailRaptor.calcRoute(
//			DefaultRoutingRequest.withoutAttributes(startFacility, endFacility, departureTime, null)
//		);
//
//		// Initialize result variables
//		double totalTravelTime = 0;
//		double walkingDistance = 0;
//		double waitingTime = 0;
//
//		// Process route legs
//		for (PlanElement element : route) {
//			if (element instanceof Leg) {
//				Leg leg = (Leg) element;
//				totalTravelTime += leg.getTravelTime().seconds();
//
//				if ("walk".equals(leg.getMode())) {
//					walkingDistance += leg.getRoute().getDistance();
//				} else if ("pt".equals(leg.getMode())) {
//					waitingTime += calculateWaitingTime(leg, departureTime);
//				}
//			}
//		}
//
//		// Print details
//		System.out.println("Total travel time: " + totalTravelTime);
//		System.out.println("Walking distance: " + walkingDistance);
//		System.out.println("Waiting time: " + waitingTime);
//
//		return (int) totalTravelTime;
//	}
//
//	private double calculateWaitingTime(Leg ptLeg, double departureTime) {
//		double ptDeparture = ptLeg.getDepartureTime().seconds();
//		return ptDeparture - departureTime;
//	}
//
//	private ActivityFacility createFakeFacility(Coord coord) {
//		ActivityFacilities facilities = ScenarioUtils.setActivityFacilities();
//		ActivityFacility facility = facilities.getFactory().createActivityFacility(Id.create("tempFacility", ActivityFacility.class), coord);
//		facilities.addActivityFacility(facility);
//		return facility;
//	}
//
//	public static void main(String[] args) {
//		// Example usage
//		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
//		PublicTransportTravelTimeCalculator calculator = new PublicTransportTravelTimeCalculator(scenario);
//
//		Coord start = new Coord(3800, 5100);
//		Coord end = new Coord(16100, 5050);
//		String time = "05:00:00";
//
//		int travelTime = calculator.calculatePtTravelTime(start, end, time);
//		System.out.println("Calculated travel time: " + travelTime + " seconds");
//	}
//}
