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
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.LinkWrapperFacilityWithSpecificCoord;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.Vehicles;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;


@CommandLine.Command(
	name = "Pt Quality Analysis",
	description = "Analysis class that implements the Pt to Car Travel Time Ratio and maxWalkDistanceToPtStop indicators for the public transport quality target dimension.",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	dependsOn = {
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overall_stats_agentLiveabilityInfo.csv"),
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overall_tiles_indexDimensionValues.csv")
	},
	produces = {
		"ptQuality_stats_perAgent.csv",
		"ptAccessibility_RankingValue.csv",
		"ptQuality_stats_travelTimeComparison.csv",
		"ptQuality_stats_RankingValue.csv",
		"ptQuality_XYT_agentBasedPtQuality.csv",
		"ptQuality_XYT_maxWalkToPTPerAgent.csv",
		"ptQuality_XYT_PtToCarRatioPerAgent.csv",
		"ptQuality_XYT_EcoMobilityToCarRatioPerAgent.csv",
		"ptQuality_Tiles_PtQuality.csv",
		"ptQuality_Tiles_MaxWalkToPt.csv",
		"ptQuality_Tiles_PtToCarRatio.csv",
		"ptQuality_Tiles_EcoMobilityRatio.csv"
	}
)

public class AgentBasedPtQualityAnalysis implements MATSimAppCommand {
	private static final Logger log = LogManager.getLogger(AgentBasedPtQualityAnalysis.class);
	private final double limitTravelTimeComparison = 2.0;
	private final double limitMaxWalkToPTDistance = 500;


	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedPtQualityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedPtQualityAnalysis.class);

	private Config config;
	private RoutingConfigGroup routingConfig;
	private Scenario scenario;
	private Network network;
	private org.matsim.pt.transitSchedule.api.TransitSchedule TransitSchedule;
	private TransitRouter transitRouter;
	private TravelTimeCalculator travelTimeCalculator;
	private TravelTime travelTime;
	private TravelDisutility travelDisutility;
	private LeastCostPathCalculator router;


	//Input paths
	private final Path CONFIG_FILE = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
	private final Path tripsPath = ApplicationUtils.matchInput("trips.csv.gz", getValidOutputDirectory());
	private final Path legsPath = ApplicationUtils.matchInput("legs.csv.gz", getValidOutputDirectory());
	private final Path eventsPath = ApplicationUtils.matchInput("events.xml.gz", getValidOutputDirectory());
	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("overall_stats_agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());

	//Output paths
	private final Path statsPtQualityPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_stats_perAgent.csv");
	private final Path statsModeComparisonPerTripPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_stats_modeComparisonPerTrip.csv");
	private final Path XYTPtToCarRatioMap = getValidLiveabilityOutputDirectory().resolve("ptQuality_XYT_PtToCarRatioPerAgent.csv");
	private final Path XYTEcoMobilityToCarRatioMap = getValidLiveabilityOutputDirectory().resolve("ptQuality_XYT_EcoMobilityToCarRatioPerAgent.csv");
	private final Path XYTWalkToPtPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_XYT_maxWalkToPTPerAgent.csv");
	private final Path XYTPtQualityPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_XYT_agentBasedPtQuality.csv");
	private final Path TilesPtQualityPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_Tiles_PtQuality.csv");
	private final Path TilesMaxWalkToPtPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_Tiles_MaxWalkToPt.csv");
	private final Path TilesPtToCarPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_Tiles_PtToCarRatio.csv");
	private final Path TilesEcoMobilityRatioPath = getValidLiveabilityOutputDirectory().resolve("ptQuality_Tiles_EcoMobilityRatio.csv");


	private long counter = 0L;
	private long nextCounterMsg = 1L;
	private long counterWalkLegs = 0L;
	private long nextCounterWalkLegsMsg = 1;

	public static void main(String[] args) {
		new AgentBasedPtQualityAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		// initialising collections and data structures
		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();

		initializeScenario();
		initializeSwissRailRaptor();

		log.info("Initialization of Scenario and SwissRailRaptor successful");

		Map<String, Double> beelineDistanceFactors = routingConfig.getBeelineDistanceFactors();
		beelineDistanceFactors.putIfAbsent("bike", 1.3);
		Map<String, Double> teleportedModeSpeeds = routingConfig.getTeleportedModeSpeeds();
		teleportedModeSpeeds.putIfAbsent("bike", 3.138889);

		log.info("Beeline and teleportedModeSpeed successfully entered into maps.");

		// defining all maps to be able to put and get values of those throughout the analysis
		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		Map<String, String> mainModePerTrip = new HashMap<>();
		Map<String, List<String>> startCoordinatesPerTrip = new HashMap<>();
		Map<String, List<String>> endCoordinatesPerTrip = new HashMap<>();
		Map<String, Double> euclideanDistancePerTrip = new HashMap<>();
		Map<String, Double> maxWalkDistancesPerAgent = new HashMap<>();
		Map<String, Double> maxWalkDistancesPerAgentIndexValue = new HashMap<>();
		Map<String, Double> maxPtToCarRatioPerAgent = new HashMap<>();
		Map<String, Double> maxPtToCarRatioPerAgentIndexValue = new HashMap<>();
		Map<String, Double> maxEcoMobilityToCarRatioPerAgent = new HashMap<>();
		Map<String, Double> maxEcoMobilityToCarRatioPerAgentIndexValue = new HashMap<>();
		Map<String, Double> overallPtQualityPerAgentIndexValue = new HashMap<>();
		Map<String, Map<String, Map<String, Map<String, Double>>>> valuesPerModePerTripPerAgent = new HashMap<>();
		Map<String, Double> tripMaxWalkDistances = new HashMap<>();

		// initializing counters
		int counterTesting = 0;
		int limitTesting = -1; // no limit for  -1

		// initializing counters for index value calculations
		double counterOverallPtQuality = 0;
		double counterIndexMaxWalk = 0;
		double counterIndexPtToCarRatio = 0;
		double counter50PercentUnderLimit = 0;
		double counter50PercentOverLimit = 0;

		// declaring other variables for later use
		double maxWalkToPtIndexValue;
		String formattedMaxWalkToPtIndexValue;
		double medianMaxWalkToPt;
		String formattedMedianMaxWalkToPt;
		double meanMaxWalkToPt;
		String formattedMeanMaxWalkToPt;

		double ptToCarRatioIndexValue;
		String formattedPtToCarRatioIndexValue;
		double medianPtToCarRatio;
		String formattedMedianPtToCarRatio;
		double meanPtToCarRatio;
		String formattedMeanPtToCarRatio;

		double ptQualityIndexValue;
		String formattedPtQualityIndexValue;
		double ptQuality50PercentUnderIndexValue;
		String formattedPtQuality50PercentUnderIndexValue;
		double ptQuality50PercentOverIndexValue;
		String formattedPtQuality50PercentOverIndexValue;

		double ecoMobilityRatioIndexValue;
		String formattedEcoMobilityRatioIndexValue;
		double ecoMobilityRatio50PercentUnderIndexValue;
		String formattedEcoMobilityRatio50PercentUnderIndexValue;
		double ecoMobilityRatio50PercentOverIndexValue;
		String formattedEcoMobilityRatio50PercentOverIndexValue;
		double counterIndexEcoToCarRatio = 0;
		double counterEco50PercentUnderLimit = 0;
		double counterEco50PercentOverLimit = 0;

		tripMaxWalkDistances = getAllLegWalkDistanceMax();

		//prepping a map for all study area agents to be used for writing files and calculating index values
		try (Reader studyAreaAgentReader = new FileReader(inputAgentLiveabilityInfoPath.toFile());
			 CSVParser studyAreaAgentParser = new CSVParser(studyAreaAgentReader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			for (CSVRecord record : studyAreaAgentParser) {
				String id = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(id, Arrays.asList(homeX, homeY));
				maxWalkDistancesPerAgent.put(id, null);
				maxWalkDistancesPerAgentIndexValue.put(id, null);
				maxPtToCarRatioPerAgent.put(id, null);
				maxPtToCarRatioPerAgentIndexValue.put(id, null);
				overallPtQualityPerAgentIndexValue.put(id, null);
				maxEcoMobilityToCarRatioPerAgent.put(id, null);
				maxEcoMobilityToCarRatioPerAgentIndexValue.put(id, null);
			}
		}

		log.info("Travel Time calculations per Trip for comparison begin.");

		//use CSVParser for reading the trips.csv.gz file and CSVWriter for writing the output files
		try (InputStream fileStream = new FileInputStream(tripsPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser tripParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'))) {

			//read trips input file line by line, extract the content and use for iterations over all trips (instead of other for or while-loop)
			for (CSVRecord tripRecord : tripParser) {

				++this.counter;
				if (this.counter == this.nextCounterMsg) {
					this.nextCounterMsg *= 2L;
					log.info(" trip # " + this.counter);
				}

				Map<String, Map<String, Double>> modeValues = new HashMap<>();
				Map<String, Double> ptTripValues = new HashMap<>();
				Map<String, Double> carTripValues = new HashMap<>();
				Map<String, Double> backupCarTripValues = new HashMap<>();
				Map<String, Double> bikeTripValues = new HashMap<>();
				Map<String, Double> walkTripValues = new HashMap<>();
				Map<String, Map<String, Map<String, Double>>> tripValues = new HashMap<>();

				String person = tripRecord.get("person");
				String tripId = tripRecord.get("trip_id");

				startCoordinatesPerTrip.put(tripId, Arrays.asList(tripRecord.get("start_x"), tripRecord.get("start_y")));
				endCoordinatesPerTrip.put(tripId, Arrays.asList(tripRecord.get("end_x"), tripRecord.get("end_y")));

				double euclideanDistance = Double.parseDouble(tripRecord.get("euclidean_distance"));

				euclideanDistancePerTrip.put(tripId, euclideanDistance);

				//avoid calculation for people outside the study area
				if (!homeCoordinatesPerAgentInStudyArea.containsKey(person)) {
					continue;
				}

				//limit iterations for code testing code
				if (counterTesting >= limitTesting && limitTesting != -1) {
					System.out.println("Limit of iterations for testing reached");
					break;
				}
				counterTesting++;

				//sort out all very short trips as they should be neither using car nor pt but walk - pt routing will most likely not be successful, leading to car vs. walk which makes little sense
				String mainMode = null;
				mainMode = tripRecord.get("main_mode");
				mainModePerTrip.put(tripId, mainMode);
				String currentTravTime = tripRecord.get("trav_time");
				String depTime = tripRecord.get("dep_time");

				//Create Start and End Facility
				String startLink = tripRecord.get("start_link");
				String endLink = tripRecord.get("end_link");
				Facility startFacility = createFacility(tripRecord.get("start_x"), tripRecord.get("start_y"), startLink);
				Facility endFacility = createFacility(tripRecord.get("end_x"), tripRecord.get("end_y"), endLink);
				if (euclideanDistance > 300.0) {

					switch (mainMode) {
						case "car":
						case "ride":
							ptTripValues = calculatePtTrip(startFacility, endFacility, depTime);
							carTripValues.put("tripOverallTravelTime", (double) timeToSeconds(currentTravTime));
							walkTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("walk") / teleportedModeSpeeds.get("walk"));
							bikeTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("bike") / teleportedModeSpeeds.get("bike"));
							backupCarTripValues.put("tripOverallTravelTime", null);
							break;

						case "drt":
						case "pt":
							ptTripValues.put("legWalkMaxDistance", tripMaxWalkDistances.getOrDefault(tripId, null));
							ptTripValues.put("tripOverallTravelTime", (double) timeToSeconds(currentTravTime));
							carTripValues = calculateCarTrip(startLink, endLink, depTime);
							walkTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("walk") / teleportedModeSpeeds.get("walk"));
							bikeTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("bike") / teleportedModeSpeeds.get("bike"));
							backupCarTripValues.put("tripOverallTravelTime", null);
							break;

						case "walk":
							ptTripValues = calculatePtTrip(startFacility, endFacility, depTime);
							carTripValues = calculateCarTrip(startLink, endLink, depTime);
							walkTripValues.put("tripOverallTravelTime", (double) timeToSeconds(currentTravTime));
							bikeTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("bike") / teleportedModeSpeeds.get("bike"));
							backupCarTripValues.put("tripOverallTravelTime", null);
							break;

						case "bike":
							ptTripValues = calculatePtTrip(startFacility, endFacility, depTime);
							carTripValues = calculateCarTrip(startLink, endLink, depTime);
							walkTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("walk") / teleportedModeSpeeds.get("walk"));
							bikeTripValues.put("tripOverallTravelTime", (double) timeToSeconds(currentTravTime));
							backupCarTripValues.put("tripOverallTravelTime", null);
							break;

						default:
							ptTripValues = calculatePtTrip(startFacility, endFacility, depTime);
							carTripValues = calculateCarTrip(startLink, endLink, depTime);
							walkTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("walk") / teleportedModeSpeeds.get("walk"));
							bikeTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("bike") / teleportedModeSpeeds.get("bike"));
							backupCarTripValues.put("tripOverallTravelTime", null);
							break;
					}
				} else {
					// Insert the default null values into the map
					ptTripValues.put("tripOverallTravelTime", null);
					ptTripValues.put("legWalkMaxDistance", null);
					carTripValues.put("tripOverallTravelTime", null);
					walkTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("walk") / teleportedModeSpeeds.get("walk"));
					bikeTripValues.put("tripOverallTravelTime", euclideanDistance * beelineDistanceFactors.get("bike") / teleportedModeSpeeds.get("bike"));
					backupCarTripValues = calculateCarTrip(startLink, endLink, depTime);
				}
				modeValues.put("pt", ptTripValues);
				modeValues.put("car", carTripValues);
				modeValues.put("walk", walkTripValues);
				modeValues.put("bike", bikeTripValues);
				modeValues.put("backupCar", backupCarTripValues);

				tripValues.put(tripId, modeValues);
				valuesPerModePerTripPerAgent.put(person, tripValues);
			}
			log.info("PT and Car Routing completed.");
		}

		for (String agent : valuesPerModePerTripPerAgent.keySet()) {
			Double maxWalkDistance = null;
			Double maxRatio = null;
			Double maxPtToCarRatioIndexValue = null;
			Double maxEcoMobilityRatio = null;
			Double maxEcoMobilityToCarRatioIndexValue = null;
			Double maxWalkDistanceIndexValue = null;
			Map<String, Map<String, Map<String, Double>>> trips = valuesPerModePerTripPerAgent.get(agent);


			for (String tripId : trips.keySet()) {
				Double ecoCarTime;
				Double legWalkMaxDistance = trips.get(tripId).get("pt").get("legWalkMaxDistance");
				Double ptTime = trips.get(tripId).get("pt").get("tripOverallTravelTime");
				Double carTime = trips.get(tripId).get("car").get("tripOverallTravelTime");
				Double walkTime = trips.get(tripId).get("walk").get("tripOverallTravelTime");
				Double bikeTime = trips.get(tripId).get("bike").get("tripOverallTravelTime");
				Double backupCarTime = trips.get(tripId).get("backupCar").get("tripOverallTravelTime");
				List<Double> ecoMobilityTimes = Arrays.asList(ptTime, walkTime, bikeTime);
				double minEcoMobilityTime = ecoMobilityTimes.stream().filter(Objects::nonNull).min(Double::compareTo).orElse(Double.MAX_VALUE);

				// Get longest walk to PT
				if (legWalkMaxDistance != null) {
					if (maxWalkDistance == null) {
						maxWalkDistance = legWalkMaxDistance;
					} else {
						maxWalkDistance = Math.max(maxWalkDistance, legWalkMaxDistance);
					}
					maxWalkDistanceIndexValue = (maxWalkDistance - limitMaxWalkToPTDistance) / limitMaxWalkToPTDistance;
				}

				//Get PT to Car travel time ratio
				if (ptTime != null && carTime != null) {
					double ratio = ptTime / carTime;
					if (maxRatio == null) {
						maxRatio = ratio;
					} else {
						maxRatio = Math.max(maxRatio, ratio);
					}
					maxPtToCarRatioIndexValue = (maxRatio - limitTravelTimeComparison) / limitTravelTimeComparison;
				}

				if (carTime != null || backupCarTime != null) {
					ecoCarTime = (carTime != null) ? carTime : backupCarTime;
					double ratio = minEcoMobilityTime / ecoCarTime;
					if (maxEcoMobilityRatio == null) {
						maxEcoMobilityRatio = ratio;
					} else {
						maxEcoMobilityRatio = Math.max(maxEcoMobilityRatio, ratio);
					}
					maxEcoMobilityToCarRatioIndexValue = (maxEcoMobilityRatio - limitTravelTimeComparison) / limitTravelTimeComparison;
				}
			}
				maxWalkDistancesPerAgent.put(agent, maxWalkDistance);
				maxWalkDistancesPerAgentIndexValue.put(agent, maxWalkDistanceIndexValue);
				maxPtToCarRatioPerAgent.put(agent, maxRatio);
				maxPtToCarRatioPerAgentIndexValue.put(agent, maxPtToCarRatioIndexValue);
				maxEcoMobilityToCarRatioPerAgent.put(agent, maxEcoMobilityRatio);
				maxEcoMobilityToCarRatioPerAgentIndexValue.put(agent, maxEcoMobilityToCarRatioIndexValue);
			}

			// calculating percentage of agents with deviation under 0 (index value per indicator) and for further analysis also for deviation -0.5 and +0.5
			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentId = entry.getKey();
				Double overallPtQualityIndexValue = null;

				boolean hasMaxWalkDistance = maxWalkDistancesPerAgentIndexValue.get(agentId) != null;
				boolean hasMaxPtToCarRatio = maxPtToCarRatioPerAgentIndexValue.get(agentId) != null;

				// If neither map has an entry for the agent, skip the iteration
				if (!hasMaxWalkDistance && !hasMaxPtToCarRatio) {
					continue;
				}

				Double maxWalkDistancesIndexValue = hasMaxWalkDistance ? maxWalkDistancesPerAgentIndexValue.get(agentId) : null;
				Double maxPtToCarRatioIndexValue = hasMaxPtToCarRatio ? maxPtToCarRatioPerAgentIndexValue.get(agentId) : null;

				// If one value is missing, the other value alone should apply
				if (!hasMaxWalkDistance) {
					overallPtQualityIndexValue = maxPtToCarRatioIndexValue;
				} else if (!hasMaxPtToCarRatio) {
					overallPtQualityIndexValue = maxWalkDistancesIndexValue;
				} else {
					overallPtQualityIndexValue = Math.max(maxWalkDistancesIndexValue, maxPtToCarRatioIndexValue);
				}

				overallPtQualityPerAgentIndexValue.put(agentId, overallPtQualityIndexValue);

			// Only increment the counter if the respective map contains the agent
			if (hasMaxWalkDistance && maxWalkDistancesIndexValue <= 0) {
				counterIndexMaxWalk++;
			}
			if (hasMaxPtToCarRatio && maxPtToCarRatioIndexValue <= 0) {
				counterIndexPtToCarRatio++;
			}
			if (overallPtQualityIndexValue <= 0) {
				counterOverallPtQuality++;
			}
			if (overallPtQualityIndexValue <= -0.5) {
				counter50PercentUnderLimit++;
			}
			if (overallPtQualityIndexValue <= 0.5) {
				counter50PercentOverLimit++;
			}
			if (maxEcoMobilityToCarRatioPerAgentIndexValue.get(agentId) <= 0) {
				counterIndexEcoToCarRatio++;
			}
			if (maxEcoMobilityToCarRatioPerAgentIndexValue.get(agentId)  <= -0.5) {
				counterEco50PercentUnderLimit++;
			}
			if (maxEcoMobilityToCarRatioPerAgentIndexValue.get(agentId)  <= 0.5) {
				counterEco50PercentOverLimit++;
			}
		}

		// calculating overall index values and indicator index values
		maxWalkToPtIndexValue = counterIndexMaxWalk / sizeWithoutNulls(maxWalkDistancesPerAgentIndexValue);
		formattedMaxWalkToPtIndexValue = String.format(Locale.US, "%.2f%%", maxWalkToPtIndexValue * 100);

		ptToCarRatioIndexValue = counterIndexPtToCarRatio / sizeWithoutNulls(maxPtToCarRatioPerAgentIndexValue);
		formattedPtToCarRatioIndexValue = String.format(Locale.US, "%.2f%%", ptToCarRatioIndexValue * 100);

		ptQualityIndexValue = counterOverallPtQuality / sizeWithoutNulls(overallPtQualityPerAgentIndexValue);
		formattedPtQualityIndexValue = String.format(Locale.US, "%.2f%%", ptQualityIndexValue * 100);

		ptQuality50PercentUnderIndexValue = counter50PercentUnderLimit / sizeWithoutNulls(overallPtQualityPerAgentIndexValue);
		formattedPtQuality50PercentUnderIndexValue = String.format(Locale.US, "%.2f%%", ptQuality50PercentUnderIndexValue * 100);

		ptQuality50PercentOverIndexValue = counter50PercentOverLimit / sizeWithoutNulls(overallPtQualityPerAgentIndexValue);
		formattedPtQuality50PercentOverIndexValue = String.format(Locale.US, "%.2f%%", ptQuality50PercentOverIndexValue * 100);

		ecoMobilityRatioIndexValue = counterIndexEcoToCarRatio / sizeWithoutNulls(maxEcoMobilityToCarRatioPerAgentIndexValue);
		formattedEcoMobilityRatioIndexValue = String.format(Locale.US, "%.2f%%", ecoMobilityRatioIndexValue * 100);

		ecoMobilityRatio50PercentUnderIndexValue = counterEco50PercentUnderLimit / sizeWithoutNulls(maxEcoMobilityToCarRatioPerAgentIndexValue);
		formattedEcoMobilityRatio50PercentUnderIndexValue = String.format(Locale.US, "%.2f%%", ecoMobilityRatio50PercentUnderIndexValue * 100);

		ecoMobilityRatio50PercentOverIndexValue = counterEco50PercentOverLimit / sizeWithoutNulls(maxEcoMobilityToCarRatioPerAgentIndexValue);
		formattedEcoMobilityRatio50PercentOverIndexValue = String.format(Locale.US, "%.2f%%", ecoMobilityRatio50PercentOverIndexValue * 100);

		meanMaxWalkToPt = maxWalkDistancesPerAgent.values().stream().filter(Objects::nonNull).collect(Collectors.averagingDouble(Double::doubleValue));
		formattedMeanMaxWalkToPt = String.format(Locale.US, "%.2f m", meanMaxWalkToPt);

		medianMaxWalkToPt = AgentLiveabilityInfoCollection.calculateMedian(maxWalkDistancesPerAgent);
		formattedMedianMaxWalkToPt = String.format(Locale.US, "%.2f m", medianMaxWalkToPt);

		meanPtToCarRatio = maxPtToCarRatioPerAgent.values().stream().filter(Objects::nonNull).collect(Collectors.averagingDouble(Double::doubleValue));
		formattedMeanPtToCarRatio = String.format(Locale.US, "%.2f", meanPtToCarRatio);

		medianPtToCarRatio = AgentLiveabilityInfoCollection.calculateMedian(maxPtToCarRatioPerAgent);
		formattedMedianPtToCarRatio = String.format(Locale.US, "%.2f", medianPtToCarRatio);


		//Write Information in Agent Livability Info Collection
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(maxWalkDistancesPerAgent, "maxWalkToPtDistance");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(maxWalkDistancesPerAgentIndexValue, "indexValue_maxWalkToPtDistance");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(maxPtToCarRatioPerAgent, "maxPtToCarRatio");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(maxPtToCarRatioPerAgentIndexValue, "indexValue_maxPtToCarRatio");

		agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedPtQualityIndexValue, "Pt Quality Index Value");

		agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Pt Quality", "Max Walk To Pt Distance", formattedMedianMaxWalkToPt, String.valueOf(limitMaxWalkToPTDistance), formattedMaxWalkToPtIndexValue);
		agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Pt Quality", "Pt to Car travel time ratio", formattedMedianPtToCarRatio, String.valueOf(limitTravelTimeComparison), formattedPtToCarRatioIndexValue);

		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTPtQualityPath, overallPtQualityPerAgentIndexValue, homeCoordinatesPerAgentInStudyArea);
		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTPtToCarRatioMap, maxPtToCarRatioPerAgentIndexValue, homeCoordinatesPerAgentInStudyArea);
		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTWalkToPtPath, maxWalkDistancesPerAgentIndexValue, homeCoordinatesPerAgentInStudyArea);
		AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTEcoMobilityToCarRatioMap, maxEcoMobilityToCarRatioPerAgentIndexValue, homeCoordinatesPerAgentInStudyArea);

		// generating output files for the green space dashboard page
		try (CSVWriter PTQTileWriter = new CSVWriter(new FileWriter(TilesPtQualityPath.toFile()));
			 CSVWriter MaxWalkToPtWriter = new CSVWriter(new FileWriter(TilesMaxWalkToPtPath.toFile()));
			 CSVWriter MaxPtToCarRatioWriter = new CSVWriter(new FileWriter(TilesPtToCarPath.toFile()));
			 CSVWriter EcoMobilityRatioWriter = new CSVWriter(new FileWriter(TilesEcoMobilityRatioPath.toFile()))) {

			PTQTileWriter.writeNext(new String[]{"Public Transport Quality: 50% under limit", formattedPtQuality50PercentUnderIndexValue});
			PTQTileWriter.writeNext(new String[]{"Public Transport Quality within limit", formattedPtQualityIndexValue});
			PTQTileWriter.writeNext(new String[]{"Public Transport Quality: 50% over limit", formattedPtQuality50PercentOverIndexValue});

			MaxWalkToPtWriter.writeNext(new String[]{"Max walk to Pt Index Value", formattedMaxWalkToPtIndexValue});
			MaxWalkToPtWriter.writeNext(new String[]{"Mean max walk to Pt distance", formattedMeanMaxWalkToPt});
			MaxWalkToPtWriter.writeNext(new String[]{"Median max walk to Pt distance", formattedMedianMaxWalkToPt});

			MaxPtToCarRatioWriter.writeNext(new String[]{"Pt to Car travel time ratio Index Value", formattedPtToCarRatioIndexValue});
			MaxPtToCarRatioWriter.writeNext(new String[]{"Mean Pt to car travel time ratio", formattedMeanPtToCarRatio});
			MaxPtToCarRatioWriter.writeNext(new String[]{"Median Pt to car travel time ratio", formattedMedianPtToCarRatio});

			EcoMobilityRatioWriter.writeNext(new String[]{"EcoMobility to car travel time ratio: 50 % under limit", formattedEcoMobilityRatio50PercentUnderIndexValue});
			EcoMobilityRatioWriter.writeNext(new String[]{"EcoMobility to car travel time ratio within limit", formattedEcoMobilityRatioIndexValue});
			EcoMobilityRatioWriter.writeNext(new String[]{"EcoMobility to car travel time ratio: 50 % over limit", formattedEcoMobilityRatio50PercentOverIndexValue});
		}

		try (CSVWriter agentBasedWriter = new CSVWriter(new FileWriter(String.valueOf(statsPtQualityPath)),
			CSVWriter.DEFAULT_SEPARATOR,
			CSVWriter.NO_QUOTE_CHARACTER,
			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
			CSVWriter.DEFAULT_LINE_END)) {

			agentBasedWriter.writeNext(new String[]{
				"Person",
				"MaxWalkToPtPerAgent",
				"indexValue_MaxWalkToPtPerAgent",
				"PtToCarTravelTimeRatioPerAgent",
				"indexValue_PtToCarTravelTimeRatioPerAgent",
				"PtQuality IndexValue"
			});

			for (String person : homeCoordinatesPerAgentInStudyArea.keySet()) {
				agentBasedWriter.writeNext(new String[]{
					person,
					String.valueOf(maxWalkDistancesPerAgent.getOrDefault(person, null)),
					String.valueOf(maxWalkDistancesPerAgentIndexValue.getOrDefault(person, null)),
					String.valueOf(maxPtToCarRatioPerAgent.getOrDefault(person, null)),
					String.valueOf(maxPtToCarRatioPerAgentIndexValue.getOrDefault(person, null)),
					String.valueOf(overallPtQualityPerAgentIndexValue.getOrDefault(person, null)),
				});
			}
		}

		try (CSVWriter modeComparisonWriter = new CSVWriter(new FileWriter(String.valueOf(statsModeComparisonPerTripPath)),
			CSVWriter.DEFAULT_SEPARATOR,
			CSVWriter.NO_QUOTE_CHARACTER,
			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
			CSVWriter.DEFAULT_LINE_END))
		{

			modeComparisonWriter.writeNext(new String[]{
				"Person",
				"TripID",
				"tripMainMode",
				"euclideanDistance",
				"CarTraveTime",
				"PTTraveTime",
				"BikeTraveTime",
				"WalkTraveTime",
				"PtmaxLegWalkDistance",
				"startX",
				"startY",
				"endX",
				"endY"
			});

			for (String person : valuesPerModePerTripPerAgent.keySet()) {
				for (String trip : valuesPerModePerTripPerAgent.get(person).keySet()) {
					modeComparisonWriter.writeNext(new String[]{
						person,
						trip,
						mainModePerTrip.get(trip),
						String.valueOf(euclideanDistancePerTrip.get(trip)),
						String.valueOf(valuesPerModePerTripPerAgent.get(person).get(trip).get("car").getOrDefault("tripOverallTravelTime", null)),
						String.valueOf(valuesPerModePerTripPerAgent.get(person).get(trip).get("pt").getOrDefault("tripOverallTravelTime", null)),
						String.valueOf(valuesPerModePerTripPerAgent.get(person).get(trip).get("bike").getOrDefault("tripOverallTravelTime", null)),
						String.valueOf(valuesPerModePerTripPerAgent.get(person).get(trip).get("walk").getOrDefault("tripOverallTravelTime", null)),
						String.valueOf(valuesPerModePerTripPerAgent.get(person).get(trip).get("pt").getOrDefault("legWalkMaxDistance", null)),
						String.valueOf(startCoordinatesPerTrip.get(trip).get(0)),
						String.valueOf(startCoordinatesPerTrip.get(trip).get(1)),
						String.valueOf(endCoordinatesPerTrip.get(trip).get(0)),
						String.valueOf(endCoordinatesPerTrip.get(trip).get(1))
					});
				}
			}
		}
		return 0;
	}

	/**
	 * method that transfers time formats to seconds
	 */
	public static int timeToSeconds (String time){
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

	/**
	 * 	Stream-based method to count entries without null values
	 */
	public static long sizeWithoutNulls (Map < String, Double > map){
		return map.values().stream()
			.filter(Objects::nonNull)
			.count();
	}

	/**
	 * method to create the Transit Router for the SwissRailRaptor
	 */
		private SwissRailRaptor createTransitRouter (TransitSchedule schedule, Config config, Network network){
		SwissRailRaptorData data = SwissRailRaptorData.create(schedule, (Vehicles) null, RaptorUtils.createStaticConfig(config), network, (OccupancyData) null);
		return (new SwissRailRaptor.Builder(data, config)).build();
	}

	/**
	 * method to initialize the SwissRailRaptor
 	 */
	public void initializeSwissRailRaptor () {
		RaptorParameters raptorParams = RaptorUtils.createParameters(config);
		this.transitRouter = this.createTransitRouter(TransitSchedule, config, network);
	}

	/**
	 * method to calculate the car trip in the occupied network for all trips that are not car trips
 	 */
	private Map<String, Double> calculateCarTrip (String startLinkID, String endLinkID, String departureTime){
		double tripOverallTravelTime = 0.0;
		double currentTime = timeToSeconds(departureTime);
		Map<String, Double> CarTripValues = new HashMap<>();

		// Define start and end link
		Link startLink = network.getLinks().get(Id.createLinkId(startLinkID));
		Link endLink = network.getLinks().get(Id.createLinkId(endLinkID));

		// Calculate optimal Route
		Node startNode = startLink.getToNode();
		Node endNode = endLink.getToNode();
		LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, timeToSeconds(departureTime), null, null);

		if (path == null) {
			//	System.out.println("No car route found for " + startLinkID + " and " + endLinkID);
			CarTripValues.put("tripOverallTravelTime", null);
		}

		// Calculate Overall Car travel time
		for (Link link : path.links) {
			tripOverallTravelTime += travelTime.getLinkTravelTime(link, currentTime, null, null);
			currentTime += travelTime.getLinkTravelTime(link, currentTime, null, null);
		}

		CarTripValues.put("tripOverallTravelTime", tripOverallTravelTime);
		return CarTripValues;
	}

	/**
	 * method to get the information for the occupation of the network from the events file
 	 */
	public TravelTimeCalculator readEventsIntoTravelTimeCalculator (Network network ){
		EventsManager manager = EventsUtils.createEventsManager();
		TravelTimeCalculator.Builder builder = new TravelTimeCalculator.Builder(network);
		TravelTimeCalculator tcc = builder.build();
		manager.addHandler(tcc);
		manager.initProcessing();
		new MatsimEventsReader(manager).readFile(String.valueOf(eventsPath));
		manager.finishProcessing();
		return tcc;
	}

	/**
	 * method to calculate the pt trip
 	 */
	private Map<String, Double> calculatePtTrip (Facility start, Facility end, String time){
		double tripOverallTravelTime = 0.0;
		double legWalkMaxDistance = 0.0;
		Map<String, Double> PtTripValues = new HashMap<>();

		if (transitRouter == null) {
			throw new IllegalStateException("TransitRouter has not been initialized. Call initializeSwissRailRaptor first.");
		}

		List<? extends PlanElement> planElements = transitRouter.calcRoute(DefaultRoutingRequest.withoutAttributes(start, end, timeToSeconds(time), (Person) null));

		if (planElements == null) {
			//	System.out.println("No pt route found for " + start + " and " + end);
			PtTripValues.put("tripOverallTravelTime", null);
			PtTripValues.put("legWalkMaxDistance", null);

		} else {
			List<Leg> legs = TripStructureUtils.getLegs(planElements);

			for (Leg leg : legs) {
				tripOverallTravelTime += leg.getTravelTime().seconds();
				//	System.out.println("ptTravelTime for " + leg + "is " + tripOverallTravelTime);
				if (Objects.equals(leg.getMode(), "walk")) {
					legWalkMaxDistance = Math.max(legWalkMaxDistance, leg.getRoute().getDistance());
				}
			}

			PtTripValues.put("tripOverallTravelTime", tripOverallTravelTime);
			PtTripValues.put("legWalkMaxDistance", legWalkMaxDistance);
		}
		return PtTripValues;
	}

	/**
	 * method to create a facility from coordinates and a link
 	 */
	private Facility createFacility (String coordinateX, String coordinateY, String string_link){
		Id<Link> LinkId = Id.createLinkId(string_link);
		Link link = network.getLinks().get(LinkId);
		Coord coordinate = new Coord(Double.parseDouble(coordinateX), Double.parseDouble(coordinateY));
		return new LinkWrapperFacilityWithSpecificCoord(link, coordinate);
	}

	/**
	 * method to initialize the scenario
 	 */
	private void initializeScenario () {
		if (this.scenario == null) {
			this.config = ConfigUtils.loadConfig(String.valueOf(CONFIG_FILE));
			this.routingConfig = config.routing();
			this.scenario = ScenarioUtils.loadScenario(config);
			Population population = scenario.getPopulation();
			this.network = scenario.getNetwork();
			this.TransitSchedule = scenario.getTransitSchedule();
			this.travelTimeCalculator = readEventsIntoTravelTimeCalculator(network);
			this.travelTime = travelTimeCalculator.getLinkTravelTimes();
			// initialize router
			this.travelDisutility = new TravelDisutility() {
				@Override
				public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {
					return travelTime.getLinkTravelTime(link, v, person, vehicle);
				}

				@Override
				public double getLinkMinimumTravelDisutility(Link link) {
					return link.getLength() / link.getFreespeed(); // Minimale Reisezeit basierend auf Freigeschwindigkeit
				}
			};

			this.router = new DijkstraFactory().createPathCalculator(network, travelDisutility, travelTime);
		}
	}

	/**
	 * catch all maximum leg walk distances per trip
	 */
	private Map<String, Double> getAllLegWalkDistanceMax() throws IOException {
		Map<String, Double> tripMaxDistances = new HashMap<>();
		try (InputStream fileStream = new FileInputStream(legsPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'))) {

			// Iteration über alle Zeilen der CSV-Datei
			for (CSVRecord legsRecord : legsParser) {
				++this.counterWalkLegs;
				if (this.counterWalkLegs == this.nextCounterWalkLegsMsg) {
					this.nextCounterWalkLegsMsg *= 4L;
					log.info(" GetTripWithWalk # " + this.counterWalkLegs);
				}

				String tripId = legsRecord.get("trip_id");
				String mode = legsRecord.get("mode");
				double distance = Double.parseDouble(legsRecord.get("distance"));

				if (Objects.equals(mode, "walk")) {
					tripMaxDistances.put(tripId, Math.max(tripMaxDistances.getOrDefault(tripId, 0.0), distance));
				}
			}
		}
		return tripMaxDistances;
	}
}
