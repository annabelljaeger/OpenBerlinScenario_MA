package org.matsim.analysis;

import ch.sbb.matsim.routing.pt.raptor.*;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityModule;
import org.matsim.contrib.accessibility.AccessibilityUtils;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.contrib.accessibility.run.RunAccessibilityExample;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.LinkWrapperFacilityWithSpecificCoord;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.modechoice.InformedModeChoiceConfigGroup;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "accessibility-analysis",
	description = "PT-quality and Accessibility Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	requires = {
		"berlin-v6.3.output_trips.csv.gz"
	},
	produces = {
		"accessibility_stats_perAgent.csv",
		"ptQuality_stats_perAgent.csv",
		"ptAccessibility_RankingValue.csv",
		"reisezeitvergleich_perTrip.csv",
		"ptAndAccessibility_RankingValue.csv"
	}
)

public class AgentBasedAccessibilityAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedAccessibilityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedAccessibilityAnalysis.class);

	private Config config;
	private Scenario scenario;
	private Population population;
	private Network network;
	private org.matsim.pt.transitSchedule.api.TransitSchedule TransitSchedule;
	private AccessibilityConfigGroup accConfig;

	private final Path CONFIG_FILE = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
	private final Path tripsCsvPath = ApplicationUtils.matchInput("trips.csv.gz", getValidOutputDirectory());
	private final Path outputReisezeitvergleichPath = ApplicationUtils.matchInput("reisezeitvergleich_perTrip.csv", getValidLiveabilityOutputDirectory());
	private final Path outputRankingValuePath = ApplicationUtils.matchInput("ptAndAccessibility_RankingValue.csv", getValidLiveabilityOutputDirectory());

	private static final Logger LOG = LogManager.getLogger(AgentBasedAccessibilityAnalysis.class);

	public static void main(String[] args) {
		new AgentBasedAccessibilityAnalysis().execute(args);

	}

	@Override
	public Integer call() throws Exception {

		runAccessibilityContrib();

		AgentLiveabilityInfo agentLiveabilityInfo = new AgentLiveabilityInfo();

		//use CSVParser for reading the trips.csv.gz file and CSVWriter for writing the output files
		try (InputStream fileStream = new FileInputStream(tripsCsvPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser tripParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter reisezeitVergleichsWriter = new CSVWriter(new FileWriter(outputReisezeitvergleichPath.toFile()));
			 CSVWriter ptQualityStatsWriter = new CSVWriter(new FileWriter(outputRankingValuePath.toFile()))) {

			//Write headers of the output-CSV-Files
			reisezeitVergleichsWriter.writeNext(new String[]{"person", "trip_id", "supertrip_id", "euclidean_distance", "currentMainMode", "travTime_pt", "travTime_car", "Reisezeitvergleich", "tripTravTimeComparisonRankingValue"});

			int trueEntries = 0;
			int totalEntries = 0;

			initializeScenario();
			//um zu Testzwecken mit einer begrenzten Anzahl an Trips zu starten:
//			int limit = 600;
//			int count = 0;

			//read trips input file line by line, extract the content and use for iterations over all trips (instead of other for or while-loop)
			for (CSVRecord tripRecord : tripParser) {
//				if (count >= limit) {
//					break;
//				}


				String personId = tripRecord.get("person");
				String tripId = tripRecord.get("trip_id");
				String currentTravTime = tripRecord.get("trav_time");
				int euclideanDistance = Integer.parseInt(tripRecord.get("euclidean_distance"));
				String mainMode = tripRecord.get("main_mode");


				//sort out all very short trips as they should be neither using car nor pt but walk - pt routing will most likely not be successful, leading to car vs. walk which makes little sense
				if (euclideanDistance > 300) {

					ActivityFacilities facilities = scenario.getActivityFacilities();

					String startX = tripRecord.get("start_x");
					String startY = tripRecord.get("start_y");
					String startLinkString = tripRecord.get("start_link");
					Id<Link> startLinkId = Id.createLinkId(startLinkString);
					Link startLink = network.getLinks().get(startLinkId);
					String endX = tripRecord.get("end_x");
					String endY = tripRecord.get("end_y");
					String endLinkString = tripRecord.get("end_link");
					Id<Link> endLinkId = Id.createLinkId(endLinkString);
					Link endLink = network.getLinks().get(endLinkId);
					String depTime = tripRecord.get("dep_time");
					String currentStartFacilityString = tripRecord.get("start_facility_id");
					Id<ActivityFacility> currentStartFacilityId = Id.create(currentStartFacilityString, ActivityFacility.class);
					ActivityFacility currentStartFacility = facilities.getFacilities().get(currentStartFacilityId);
					String currentEndFacilityString = tripRecord.get("end_facility_id");
					Id<ActivityFacility> currentEndFacilityId = Id.create(currentEndFacilityString, ActivityFacility.class);
					ActivityFacility currentEndFacility = facilities.getFacilities().get(currentEndFacilityId);

					Facility startFacility;
					Facility endFacility;

					Coord tripStartCoord = new Coord(Double.parseDouble(startX), Double.parseDouble(startY));
					Coord tripEndCoord = new Coord(Double.parseDouble(endX), Double.parseDouble(endY));

//					if(currentStartFacilityId != null && currentEndFacility != null) {
//						startFacility = currentStartFacility;
//						endFacility = currentEndFacility;
//					} else {
						startFacility =  new LinkWrapperFacilityWithSpecificCoord(startLink, tripStartCoord);
						endFacility = new LinkWrapperFacilityWithSpecificCoord(endLink, tripEndCoord);
//					}

					if (startFacility.getCoord() == null || endFacility.getCoord() == null) {
						throw new IllegalArgumentException("Facility is null. Check input data or initialization.");
					}

					int travTimePt = 1;
					int travTimeCar = 1;

					if (Objects.equals(mainMode, "car")) {

						travTimeCar = timeToSeconds(currentTravTime);


						//routing for pt with SwissRailRaptor (from tripStartCoord to tripEndCoord for depTime)
						travTimePt = calculatePtTravelTime(startFacility, endFacility, depTime);
					//	travTimePt = calculator.calculateTravelTime(tripStartCoord, tripEndCoord, depTime);


					} else if (Objects.equals(mainMode, "pt")) {

						travTimePt = timeToSeconds(currentTravTime);

						//wie route ich Autos im Simulationsverkehr für gesetzte Start-Ziel-Verbindnugen zu einer festgelegten Zeit?? So muss ich hier vorgehen
						//ggf. als Platzhalter routing im leeren Netz aber das wäre komplett falsch als Annahme für die finale Version
						travTimeCar = calculateCarTravelTime(tripStartCoord, startLinkString, tripEndCoord, endLinkString, depTime, personId);

						//auch walk, ride und bike sollten ab einer gewissen Wegelänge auf ihr MIV/ÖV Reisezeitverhältnis geprüft werden
						//ggf. nicht ab 300m sondern erst ab 1/2/3 km da darunter schon walk/bike das beste ist? Oder einfach komplett da es keinen wirklichen Grund dagegen gibt?
					} else {
						//berechne travTimeCar wie bei mainMode = pt
						travTimeCar = calculateCarTravelTime(tripStartCoord, startLinkString, tripEndCoord, endLinkString, depTime, personId);
						//berechne travTimePt wie bei mainMode = car
						travTimePt = calculatePtTravelTime(startFacility, endFacility, depTime);
					}

					//SUPERTRIPS IDENTIFIZIEREN FEHLT!!!

					double reisezeitvergleich = (double) travTimePt / travTimeCar;
					//Alternative (sieht schöner aus oder?):
					//boolean tripTravTimeComparisonRankingValue = reisezeitvergleich < 2;
					boolean tripTravTimeComparisonRankingValue;
					if (reisezeitvergleich < 2) {
						tripTravTimeComparisonRankingValue = true;
					} else {
						tripTravTimeComparisonRankingValue = false;
					}

					if (tripTravTimeComparisonRankingValue) {
						trueEntries++;
					}
					totalEntries++;
					//Reminder Kopfzeile:
					//reisezeitVergleichsWriter.writeNext(new String[]{"person", "trip_id", "supertrip_id", "euclidean_distance", "currentMainMode", "travTime_pt", "travTime_car", "Reisezeitvergleich", "tripTravTimeComparisonRankingValue"});
					reisezeitVergleichsWriter.writeNext(new String[]{
						personId, tripId, "fehlt", String.valueOf(euclideanDistance), mainMode, String.valueOf(travTimePt), String.valueOf(travTimeCar),
						String.format("%.2f", reisezeitvergleich), String.valueOf(tripTravTimeComparisonRankingValue)
					});


				} else {
					reisezeitVergleichsWriter.writeNext(new String[]{
						personId, tripId, "fehlt", String.valueOf(euclideanDistance), mainMode, "trip too short", "trip too short", null, TRUE});
					trueEntries++;
					totalEntries++;
				}

//				count++;

			}

			try (BufferedReader agentBasedReader = Files.newBufferedReader(outputReisezeitvergleichPath)) {
//				String entry;
////				int totalEntries = 0;
////				int trueEntries = 0;
//
//				// Überspringen der Header-Zeile
//				agentBasedReader.readLine();
//
//				// Iteration über alle Zeilen
//				while ((entry = agentBasedReader.readLine()) != null) {
//					String[] values = entry.split(";");
//					// Prüfen, ob die Spalte "rankingStatus" auf True gesetzt ist
//					if (values.length > 8 && "true".equalsIgnoreCase(values[8].trim())) {
//						trueEntries++;
//					}
//					totalEntries++;


				// Anteil der True-Einträge berechnen
				double rankingPtQuality = (totalEntries > 0) ? ((double) trueEntries / totalEntries) * 100 : 0.0;
				String formattedRankingPtQuality = String.format(Locale.US, "%.2f%%", rankingPtQuality);

				//	double ptQualityRankingValue = ptQualityRankingValuePerAgent.values().stream().filter(Boolean::booleanValue).count()*100/ ptQualityRankingValuePerAgent.size();
				System.out.println("PT Quality Ranking Value is:" + rankingPtQuality);
				//	String formattedRankingPtQuality = String.format(Locale.US, "%.2f%%", ptQualityRankingValue);
				//BERECHNUNG FEHLT!!!
				//	double travelTimeComparisonRankingValue = 5;
				//	String formattedTravelTimeComparisonRankingValue = "5";
				ptQualityStatsWriter.writeNext(new String[]{"TravelTimeComparison", String.valueOf(rankingPtQuality)});

//				agentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(formattedRankingPtQuality, "PtQuality", "https://github.com/simwrapper/simwrapper/blob/master/public/images/tile-icons/directions_bus.svg");
				agentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(formattedRankingPtQuality, "PtQuality");

			}
		}

		return 0;
	}

	public void runAccessibilityContrib() {
		List<String> activityTypes = AccessibilityUtils.collectAllFacilityOptionTypes(scenario);
		LOG.info("The following activity types were found: " + activityTypes);

		Controler controler = new Controler(scenario);
		for (final String actType : activityTypes) { // Add an overriding module for each activity type.
			final AccessibilityModule module = new AccessibilityModule();
			module.setConsideredActivityType(actType);
			controler.addOverridingModule(module);
		}
		controler.run();
	}

	private SwissRailRaptor createTransitRouter(TransitSchedule schedule, Config config, Network network) {
		SwissRailRaptorData data = SwissRailRaptorData.create(schedule, (Vehicles)null, RaptorUtils.createStaticConfig(config), network, (OccupancyData)null);
		SwissRailRaptor raptor = (new SwissRailRaptor.Builder(data, config)).build();
		return raptor;
	}

		//Facilities übergeben (wenn facility in trips vorhanden: nutze Facility; wenn nicht:aus Coord und Link mit LinkWrapperFacilityWithSpecificCoord)
	private int calculatePtTravelTime(Facility start, Facility end, String time) {

		initializeScenario();

		RaptorParameters raptorParams = RaptorUtils.createParameters(config);
		TransitRouter router = this.createTransitRouter(TransitSchedule, config, network);

		List<? extends PlanElement> planElements = router.calcRoute(DefaultRoutingRequest.withoutAttributes(start, end, timeToSeconds(time), (Person)null));

		if (planElements == null) {
			System.out.println("No route found for " + start + " and " + end);
			return 99;
		} else {
			List<Leg> legs = TripStructureUtils.getLegs(planElements);

			double ptTravelTime = 0.0;
			for (Leg leg : legs) {
				ptTravelTime += leg.getTravelTime().seconds();
				System.out.println("ptTravelTime for " + leg + "is " + ptTravelTime);

			}

			return (int) ptTravelTime;
			//	return 99;
		}
	}
//		//FEHLER: "LEGS" IS NULL
//		TransitPassengerRoute ptRoute = (TransitPassengerRoute)((Leg)legs.get(1)).getRoute();
//		double actualTravelTime = (double)0.0F;
//		double distance = (double)0.0F;
//		double expectedTravelTime;
//		for(PlanElement leg : legs) {
//			PrintStream var10000 = System.out;
//			String var10001 = String.valueOf(leg);
//			var10000.println(var10001 + " " + ((Leg)leg).getRoute().getDistance());
//			actualTravelTime += ((Leg)leg).getTravelTime().seconds();
//			distance += ((Leg)leg).getRoute().getDistance();
//		}
//
//		TransitStopFacility facility = (TransitStopFacility) TransitSchedule.getFacilities().get(Id.create("6", TransitStopFacility.class));
//		if (facility != null) {
//			expectedTravelTime = 1740.0 + CoordUtils.calcEuclideanDistance(
//				facility.getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();
//			System.out.println("Travel Time is:" + expectedTravelTime);
//
//		} else {
////			throw new IllegalArgumentException("Facility with ID '6' not found.");
////		}
//


	//	double expectedTravelTime = (double)1740.0F + CoordUtils.calcEuclideanDistance(((TransitStopFacility).schedule.getFacilities().get(Id.create("6", TransitStopFacility.class))).getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();


//
//		//Kopie aus RunSwissRailRaptorExample:
////					String configFilename = "C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.output_config.xml";
////					Config config = ConfigUtils.loadConfig(configFilename);
////
////					Scenario scenario = ScenarioUtils.loadScenario(config);
////					Controler controler = new Controler(scenario);
////
////					// This is the important line:
////					controler.addOverridingModule(new SwissRailRaptorModule());
//		//	controler.run();
////Chat-GPT Alternative zu den zwei vorherigen Zeilen;
//		// SwissRailRaptor-Modul hinzufügen
//		//   controler.addOverridingModule(new TransitRouterModule());


	//	public int calculateTravelTime(Coord start, Coord end, String depTime) {
			// Parse departure time
//			int departureInSeconds = AgentBasedAccessibilityAnalysis.timeToSeconds(time);
//
//			// Load transit data and schedule from the scenario
//			TransitSchedule schedule = scenario.getTransitSchedule();
//			SwissRailRaptorData data = new SwissRailRaptorData(schedule, scenario.getNetwork());
//
//			// Convert coordinates to facilities
//			ActivityFacilities facilities = scenario.getActivityFacilities();
//			ActivityFacility startFacility = facilities.getFacilities().get(Id.create(start, ActivityFacility.class));
//			ActivityFacility endFacility = facilities.getFacilities().get(Id.create(end, ActivityFacility.class));
//
//			if (startFacility == null || endFacility == null) {
//				throw new RuntimeException("Start or end facility could not be found for given coordinates.");
//			}
//
//			// Perform Raptor-based routing
//			SwissRailRaptor raptor = new SwissRailRaptor(data, scenario.getConfig().transitRouter());
//			double travelTime = raptor.calcTravelTime(startFacility, endFacility, departureInSeconds);
//
//			// Return travel time as integer
//			return (int) travelTime;
//		}


//	private int calculateCarTravelTime(Coord start, Coord end, String time) {
//		return 99;
//	}

	private void initializeScenario() {
		if (this.scenario == null) {
			this.config = ConfigUtils.loadConfig(String.valueOf(CONFIG_FILE));
			this.scenario = ScenarioUtils.loadScenario(config);
			this.population = scenario.getPopulation();
			this.network = scenario.getNetwork();
			this.TransitSchedule = scenario.getTransitSchedule();
			this.accConfig = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class );
			accConfig.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, true);
		}

	}
	private int calculateCarTravelTime(Coord start, String startLinkID, Coord end, String endLinkID, String departureTime, String personId) {
		// Szenario aus der Konfiguration erstellen
//		Config config = ConfigUtils.loadConfig(String.valueOf(CONFIG_FILE));
//		Scenario scenario = ScenarioUtils.loadScenario(config);
//
//		// Netzwerk und TravelTimeCalculator aus dem Szenario
//		Network network = scenario.getNetwork();
		initializeScenario();
		TravelTimeCalculator travelTimeCalculator = TravelTimeCalculator.create(network, config.travelTimeCalculator());
		TravelTime travelTime = travelTimeCalculator.getLinkTravelTimes();

		// Router initialisieren
		TravelDisutility travelDisutility = new TravelDisutility() {
			@Override
			public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {
				return travelTime.getLinkTravelTime(link, v, person, vehicle);
			}

			@Override
			public double getLinkMinimumTravelDisutility(Link link) {
				return link.getLength() / link.getFreespeed(); // Minimale Reisezeit basierend auf Freigeschwindigkeit
			}
		};

		LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, travelTime);

		// Start- und Ziel-Links definieren
		Link startLink = network.getLinks().get(Id.createLinkId(startLinkID));
		Link endLink = network.getLinks().get(Id.createLinkId(endLinkID));

		// Berechnung der optimalen Route
		Node startNode = startLink.getToNode();
		Node endNode = endLink.getToNode();
		LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, timeToSeconds(departureTime), null, null);



		// Gesamte Reisezeit berechnen
		double totalTravelTime = 0.0;
		double currentTime = timeToSeconds(departureTime);

		for (Link link : path.links) {
			totalTravelTime += travelTime.getLinkTravelTime(link, currentTime, null, null);
			currentTime += travelTime.getLinkTravelTime(link, currentTime, null, null);
		}

		return (int) totalTravelTime;
	}


//	public static int timeToSeconds(String time) {
//		// Parse the input time string into a LocalTime object
//		LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));
//
//		// Calculate seconds since midnight
//		return localTime.toSecondOfDay();
//	}

	public static int timeToSeconds(String time) {
		// Split the time string into hours, minutes, and seconds
		String[] parts = time.split(":");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid time format: " + time);
		}

		// Parse hours, minutes, and seconds
		int hours = Integer.parseInt(parts[0]);
		int minutes = Integer.parseInt(parts[1]);
		int seconds = Integer.parseInt(parts[2]);

		// Convert to total seconds
		return hours * 3600 + minutes * 60 + seconds;
	}
}

