package org.matsim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	dependsOn = {
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "agentLiveabilityInfo.csv"),
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overallRankingTile.csv")
	},
	requires = {
		"output_legs.csv.gz"
	},
	produces = {
		"travelTime_stats_legsLossTime.csv",
		"travelTime_stats_LegsLossTimePerMode.csv",
		"travelTime_stats_perAgent.csv",
		"travelTime_XYT_agentBasedTrafficQuality.xyt.csv",
		"travelTime_XYT_agentBasedLongestTrip.xyt.csv",
		"travelTime_XYT_agentBasedLossTime.xyt.csv",
		"travelTime_tiles_overall.csv",
		"travelTime_tiles_longestTrip.csv",
		"travelTime_tiles_lossTime.csv",
		"travelTime_histogram_longestCarTravel.csv",
		"travelTime_histogram_longestRideTravel.csv",
		"travelTime_histogram_longestPtTravel.csv",
		"travelTime_histogram_longestTripDep.csv",
		"travelTime_histogram_lossTimeDep.csv",
		"travelTime_bar_numberOfModes.csv"
	}
)

public class AgentBasedTrafficQualityAnalysis implements MATSimAppCommand {


	//OFFEN: PRÜFEN WIE OFT DER DER WERT ÜBERSCHRITTEN WIRD UND DIE GESAMTLOSSTIME DABEI KLEINER ALS 2/3 MINUTEN IST - WENN DAS VIELE SIND
	//MUSS EIN ZWEITER FAKTOR (MINIMALE ABSOLUTE LOSS TIME) ZUR EXKLUSION ERGÄNZT WERDEN

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedTrafficQualityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedTrafficQualityAnalysis.class);

	private static RoutingConfigGroup routingConfig;

	//overwritten with config value at beginning of call method
	double sampleSize = 0.1;

	// defining the limits for the indicators
	private static final Map<String, Duration> LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE = Map.of(
		"car", Duration.ofMinutes(30),
		"walk", Duration.ofNanos(Long.MAX_VALUE), // max value because of no limit
		"bike", Duration.ofNanos(Long.MAX_VALUE),  // max value because of no limit
		"pt", Duration.ofMinutes(60),
		"ride", Duration.ofMinutes(60),
		"fright", Duration.ofNanos(Long.MAX_VALUE) // max value because of no limit defined
	);
	private static final Map<String, Long> failedRoutingOccurances = new HashMap<>();

	private final double limitRelativeLossTime = 0.2;

	// constants for paths
	// inputPath
	private final Path inputLegsCsvFile = ApplicationUtils.matchInput("output_legs.csv.gz", getValidOutputDirectory());
	//private final Path agentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	private final Path networkPath = ApplicationUtils.matchInput("network.xml.gz", getValidOutputDirectory());
	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("overall_stats_agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	private final Path inputTripsCsvFile = ApplicationUtils.matchInput("output_trips.csv.gz", getValidOutputDirectory());

	// outputPath
	private final Path BarChartLossTimePerMode = getValidLiveabilityOutputDirectory().resolve("travelTime_stats_LegsLossTimePerMode.csv");
	private final Path outputCSVPath = getValidLiveabilityOutputDirectory().resolve("travelTime_stats_legsLossTime.csv");
	private final Path statsTravelTimePerAgentPath = getValidLiveabilityOutputDirectory().resolve("travelTime_stats_perAgent.csv");
	private final Path XYTLossTimeAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYT_agentBasedLossTime.xyt.csv");
	private final Path XYTLongestTripAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYT_agentBasedLongestTrip.xyt.csv");
	private final Path XYTTravelTimeAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYT_agentBasedTrafficQuality.xyt.csv");
	private final Path TilesTravelTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_tiles_overall.csv");
	private final Path TilesLogestTripTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_tiles_longestTrip.csv");
	private final Path TilesLossTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_tiles_lossTime.csv");
	private final Path HistogramLongestCarTravelPath = getValidLiveabilityOutputDirectory().resolve("travelTime_histogram_longestCarTravel.csv");
	private final Path HistogramLongestRideTravelPath = getValidLiveabilityOutputDirectory().resolve("travelTime_histogram_longestRideTravel.csv");
	private final Path HistogramLongestPtTravelPath = getValidLiveabilityOutputDirectory().resolve("travelTime_histogram_longestPtTravel.csv");
	private final Path HistogramLongestTripDepPath = getValidLiveabilityOutputDirectory().resolve("travelTime_histogram_longestTripDep.csv");
	private final Path HistogramLossTimeDepPath = getValidLiveabilityOutputDirectory().resolve("travelTime_histogram_lossTimeDep.csv");
	private final Path BarChartNumberOfModesPath = getValidLiveabilityOutputDirectory().resolve("travelTime_bar_numberOfModes.csv");

	public static void main(String[] args) {
		new AgentBasedTrafficQualityAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {

		//load network & execute NetworkCleaner
		Network network = NetworkUtils.readNetwork(String.valueOf(networkPath));

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		//loads sample size from config
		Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("config.xml", input.getRunDirectory()).toAbsolutePath().toString());
		SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		this.sampleSize = simwrapper.sampleSize;
		this.routingConfig = config.routing();

		// initialising collections and data structures
		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();

		// defining all maps to be able to put and get values of those throughout the analysis
		Map<String, String> dimensionOverallRankingValue = new HashMap<>();
		Map<String, Double> overallTravelTimeIndexValuePerAgent = new HashMap<>();
		Map<String, Long> counterModesPerLeg = new TreeMap<>();

		Map<String, Map<String, List<Object>>> longestTripsPerModePerAgentAbsoluteValue = new HashMap<>();
		Map<String, Map<String, Double>> longestTripsPerModePerAgentIndexValue = new HashMap<>();
		Map<String, Double> longestTripIndexValuePerAgent = new HashMap<>();
		Map<String, Double> longestTripLimitPerAgent = new HashMap<>();
		Map<String, String> longestTripModePerAgent = new HashMap<>();
		Map<String, Double> longestTripTravelTimePerAgent = new HashMap<>();
		Map<String, Double> longestTripCarTravelTimePerAgent = new HashMap<>();
		Map<String, Double> longestTripRideTravelTimePerAgent = new HashMap<>();
		Map<String, Double> longestTripPtTravelTimePerAgent = new HashMap<>();
		Map<String,String> longestTripDepTimePerAgent = new HashMap<>();


		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		//Map<String, Long> failedRoutingOccurances = new HashMap<>();
		Map<String, Double> travTimePerAgent = new HashMap<>();
		Map<String, Double> freeSpeedTravTimePerAgent = new HashMap<>();
		Map<String, Double> lossTimePerAgent = new HashMap<>();
		Map<String, Double> lossTimeLimitPerAgent = new HashMap<>();
		Map<String, Double> lossTimePercentagePerAgent = new HashMap<>();
		Map<String, Double> lossTimeIndexValuePerAgent = new HashMap<>();
		Map<String, Double> lossTimePerMode = new TreeMap<>();
		Map<String, Set<String>> modePerPerson = new HashMap<>();
		Map<String,Double> lossTimePerTimeIntervall = new HashMap<>();


		// initializing counters for index value calculations
		double counterOverallTravelTime = 0;
		double counterIndexLongestTrip = 0;
		double counterIndexLossTime = 0;
		double counter50PercentUnderLimit = 0;
		double counter50PercentOverLimit = 0;
		double sumTotalLossTime = 0.0;


		// initializing counters for calculate only for some agents
		int limit = -1; // set to -1 for no limit
		int count = 0;

		// declaring other variables for later use
		double longestTripIndexValue;
		String formattedLongestTripIndexValue;
		double lossTimeIndexValue;
		String formattedLossTimeIndexValue;
		double travelTimeIndexValue;
		String formattedTravelTimeIndexValue;
		double travelTime50PercentUnderIndexValue;
		String formattedTravelTime50PercentUnderIndexValue;
		double travelTime50PercentOverIndexValue;
		String formattedTravelTime50PercentOverIndexValue;
		double meanTotalLossTime;
		String formattedMeanTotalLossTime;
		double medianTotalLossTime;
		String formattedMedianTotalLossTime;
		double medianLongestCarTrip;
		String formattedMedianLongestCarTrip;
		double medianLongestPTTrip;
		String formattedMedianLongestPTTrip;
		double medianLongestRideTrip;
		String formattedMedianLongestRideTrip;
		double medianLongestTrip;
		String formattedMedianLongestTrip;

		String formattedSumTotalLossTime;


		//******************** indicator absolute travel time *******************************
		//prepping a map for all study area agents to be used for writing files and calculating index values
		try (Reader studyAreaAgentReader = new FileReader(inputAgentLiveabilityInfoPath.toFile());
			 CSVParser studyAreaAgentParser = new CSVParser(studyAreaAgentReader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			for (CSVRecord record : studyAreaAgentParser) {
				String id = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(id, Arrays.asList(homeX, homeY));
			}
		}

		try (InputStream fileStream = new FileInputStream(inputTripsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser tripsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'))) {

			// Iterate over each row of the CSV file
			for (CSVRecord record : tripsParser) {

				String person = record.get("person");
				if (!homeCoordinatesPerAgentInStudyArea.containsKey(person)) {
					continue;
				}

				// Extract relevant values from the CSV
				String mainMode = record.get("main_mode");
				String travTimeStr = record.get("trav_time");
				String depTimeStr = record.get("dep_time");  // Abfahrtszeit

				// Parse the travel time (trav_time) into a Duration
				Duration travelTime = parseTime(travTimeStr);

				// Parse the departure time (dep_time)
				String depTime = depTimeStr;  // Assuming dep_time is in a parsable format (e.g., HH:mm:ss)

				// Initialize the inner map if not already present
				longestTripsPerModePerAgentAbsoluteValue.putIfAbsent(person, new HashMap<>());
				Map<String, List<Object>> modeMap = longestTripsPerModePerAgentAbsoluteValue.get(person);

				// Create a list to store both the travel time and departure time
				List<Object> tripInfo = new ArrayList<>();
				tripInfo.add(travelTime);  // Index 0 will store travel time
				tripInfo.add(depTime);     // Index 1 will store departure time

				// Calculate the longest trip per mode and store the maximum duration with dep_time
				modeMap.merge(mainMode, tripInfo, (existing, newTripInfo) -> {
					Duration existingTravelTime = (Duration) ((List<Object>) existing).get(0);
					Duration newTravelTime = (Duration) newTripInfo.get(0);

					return newTravelTime.compareTo(existingTravelTime) > 0 ? newTripInfo : existing;
				});
			}


			// After processing all records, add missing agents with default values
			for (String agent : homeCoordinatesPerAgentInStudyArea.keySet()) {
				if (!longestTripsPerModePerAgentAbsoluteValue.containsKey(agent)) {
					// Create a list to store default values (00:00:00 for travel time, 00:00:00 for depTime, inactive for mainMode)
					List<Object> defaultTripInfo = new ArrayList<>();
					defaultTripInfo.add(Duration.ZERO);  // Default travel time = 00:00:00
					defaultTripInfo.add("00:00:00");    // Default depTime = 00:00:00

					// Initialize with default mainMode as "inactive"
					Map<String, List<Object>> defaultModeMap = new HashMap<>();
					defaultModeMap.put("inactive", defaultTripInfo);

					// Add the agent with default values
					longestTripsPerModePerAgentAbsoluteValue.put(agent, defaultModeMap);
				}
			}

			// Now process the data
			for (Map.Entry<String, Map<String, List<Object>>> personEntry : longestTripsPerModePerAgentAbsoluteValue.entrySet()) {
				String person = personEntry.getKey();
				Map<String, List<Object>> modeMap = personEntry.getValue();

				// Initialize the inner map for the second map if not already present
				longestTripsPerModePerAgentIndexValue.putIfAbsent(person, new HashMap<>());
				Map<String, Double> modeIndexMap = longestTripsPerModePerAgentIndexValue.get(person);

				// Initialize variables for storing max values
				double maxValue = -1;
				double limitPerAgent = 0;
				double maxTravelTime = Double.MAX_VALUE;
				String mode = "";
				String depTimeForMaxTrip = "";  // Variable to store the dep_time for the longest trip

				// Iterate over the mode map of the respective person
				for (Map.Entry<String, List<Object>> modeEntry : modeMap.entrySet()) {
					String currentMode = modeEntry.getKey();
					List<Object> tripInfo = modeEntry.getValue();
					Duration longestDuration = (Duration) tripInfo.get(0);  // Get the travel time
					String depTime = (String) tripInfo.get(1);              // Get the departure time

					double longestDurationInSeconds = longestDuration.toSeconds();
					double indexLimitSec = LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE.getOrDefault(currentMode, Duration.ofNanos(Long.MAX_VALUE)).toSeconds(); // Default biggest possible value as duration

					// Calculate the expression (Duration - indexLimit) / indexLimit
					double calculatedValue = (longestDurationInSeconds - indexLimitSec) / indexLimitSec;

					// Update the maximum values
					if (calculatedValue > maxValue) {
						maxValue = calculatedValue;
						mode = currentMode;
						limitPerAgent = LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE.get(mode).toMinutes();
						maxTravelTime = longestDuration.toMinutes();
						depTimeForMaxTrip = depTime;  // Store the departure time for the longest trip
					}

					// Store the result in the second map
					modeIndexMap.put(currentMode, calculatedValue);
				}

				// Store the results for the current person
				longestTripLimitPerAgent.put(person, limitPerAgent);
				longestTripIndexValuePerAgent.put(person, maxValue);
				longestTripTravelTimePerAgent.put(person, maxTravelTime);
				longestTripModePerAgent.put(person, mode);
				longestTripDepTimePerAgent.put(person, depTimeForMaxTrip); // Store the dep_time

				// Switch statement to store the longest trip travel time per agent for each mode
				switch (mode) {
					case "car":
						longestTripCarTravelTimePerAgent.put(person, maxTravelTime);
						break;
					case "ride":
						longestTripRideTravelTimePerAgent.put(person, maxTravelTime);
						break;
					case "pt":
						longestTripPtTravelTimePerAgent.put(person, maxTravelTime);
						break;
					default:
						break;
				}
			}
		}



		//******************** indicator loss time *******************************

		//read Input-legs.csv file and write new output files for loss time analysis
		try (InputStream fileStream = new FileInputStream(inputLegsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter legsLossTimeWriter = new CSVWriter(new FileWriter(String.valueOf(outputCSVPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			// writing csv-headers for bw Legs Loss time output files
			legsLossTimeWriter.writeNext(new String[]{
				"person",
				"trip_id",
				"mode",
				"trav_time",
				"fs_trav_time",
				"loss_time",
				"percent_lossTime",
				"trav_time_hms",
				"fs_trav_time_hms",
				"loss_time_hms",
				"dep_time",
				"start_x",
				"start_y",
				"start_node_found",
				"start_link",
				"end_x",
				"end_y",
				"end_node_found",
				"end_link"
			});


			for (CSVRecord legRecord : legsParser) {


				// if statement for calculating only some agents
				if (count >= limit && limit != -1) {
					break;
				}
				String person = legRecord.get("person");
				if (!homeCoordinatesPerAgentInStudyArea.containsKey(person)) {
					continue;
				}

				//Initializing leg variables
				double legLossTimePrecentage = 0.0;


				//Decelerating leg variables
				Duration legTravTime;
				String formattedLegTravTime;
				long legFreeSpeedTravTimeInSeconds;
				Duration legFreeSpeedTravTime;
				String formattedLegFsTravTime;

				Duration legLossTime;
				String formattedLegLegLossTime;



				// collection of values from existing legs.csv to take over to the new legsLossTime.csv
				double startX = Double.parseDouble(legRecord.get("start_x"));
				double startY = Double.parseDouble(legRecord.get("start_y"));
				String startLink = legRecord.get("start_link");
				double endX = Double.parseDouble(legRecord.get("end_x"));
				double endY = Double.parseDouble(legRecord.get("end_y"));
				String endLink = legRecord.get("end_link");


				String tripId = legRecord.get("trip_id");
				String mode = legRecord.get("mode");
				String depTime = legRecord.get("dep_time");
				String travTimeString = legRecord.get("trav_time");

				// calculate actual simulation values for export
				legTravTime = parseTime(travTimeString);
				formattedLegTravTime = formatDuration(legTravTime);

				// prep data for free speed travel time calculation
				Coord startPoint = new Coord(startX, startY);
				Coord endPoint = new Coord(endX, endY);

				Node startNodeFound = NetworkUtils.getNearestNode(network, startPoint);
				Node endNodeFound = NetworkUtils.getNearestNode(network, endPoint);

				// free speed travel time is calculated by calling the calculateFreeSpeedTravelTime method for every start-end-point-combination
				legFreeSpeedTravTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, startPoint, endPoint, mode, legTravTime.getSeconds());

				//transforming the resulting free speed travel time into the duration format as well es the String hh:mm:ss format
				legFreeSpeedTravTime = Duration.ofSeconds(legFreeSpeedTravTimeInSeconds);
				formattedLegFsTravTime = formatDuration(legFreeSpeedTravTime);

				// defining loss time as the difference of travel time minus freeSpeedTravelTime (place holders are intercepted by the if-clause)
				// data type is Long as no double is required since seconds is the smallest unit used in matsim
				//Long legLossTimeInSeconds;
				legLossTime = legTravTime.minus(legFreeSpeedTravTime);
				//legLossTimeInSeconds = legTravTimeInSeconds - legFreeSpeedTravTimeInSeconds;
				if (legLossTime.getSeconds() < 0) {
				//	System.out.println("Negativ leg loss time set to zero");
					legLossTime = Duration.ZERO;
				} // avoiding negative loss times


				// transforming the seconds of the loss time calculation to the duration format and the String (hh:mm:ss) afterwards
				//Duration lostTimeHMS = Duration.ofSeconds(legLossTimeInSeconds);
				formattedLegLegLossTime = formatDuration(legLossTime);

				// calculating the percentage of loss time compared to the free speed (minimum possible) travel time for positive loss time values
				//double percentLossTime = 0.0;
				if (legFreeSpeedTravTime.getSeconds() != 0) {
					legLossTimePrecentage = (double) legLossTime.getSeconds() / legFreeSpeedTravTime.getSeconds();
				} else {
				//	System.out.println("Warnung: Division by Zero for trip " + tripId + " avoided.");
				}

				SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
				String intervalStart = getIntervalStart(timeFormat.parse(depTime), 15);
				lossTimePerTimeIntervall.put(intervalStart, lossTimePerTimeIntervall.getOrDefault(intervalStart, 0.0) + legLossTime.getSeconds());



				// calculate sum of travel time and of loss time per agent. overall loss time sum and mode per person info
				freeSpeedTravTimePerAgent.put(person, freeSpeedTravTimePerAgent.getOrDefault(person, 0.0) + legFreeSpeedTravTime.getSeconds());
				lossTimePerAgent.put(person, lossTimePerAgent.getOrDefault(person, 0.0) + legLossTime.getSeconds());
				lossTimePerMode.put(mode, lossTimePerMode.getOrDefault(mode, 0.0) + legLossTime.getSeconds());
				travTimePerAgent.put(person, travTimePerAgent.getOrDefault((Object) person, 0.0) + legTravTime.getSeconds());
				modePerPerson.computeIfAbsent(person, k -> new HashSet<>()).add(mode);
				lossTimeLimitPerAgent.put(person, limitRelativeLossTime);
				counterModesPerLeg.compute(mode, (key, counter) -> (counter == null) ? 1 : counter + 1);

				// writing the desired columns in the new legsLossTime output csv file
				legsLossTimeWriter.writeNext(new String[]{
					person,
					tripId,
					mode,
					String.valueOf(legTravTime.getSeconds()),
					String.valueOf(legFreeSpeedTravTime.getSeconds()),
					String.valueOf(legLossTime.getSeconds()),
					String.valueOf(legLossTimePrecentage),
					formattedLegTravTime,
					formattedLegFsTravTime,
					formattedLegLegLossTime,
					depTime,
					String.valueOf(startX),
					String.valueOf(startY),
					String.valueOf(startNodeFound.getId()),
					startLink,
					String.valueOf(endX),
					String.valueOf(endY),
					String.valueOf(endNodeFound.getId()),
					endLink
				});

				//System.out.println("Neue LossTime für Person "+person);
				count++;

				//		});
			}

			// After processing all records, add missing agents with default values
			for (String person : homeCoordinatesPerAgentInStudyArea.keySet()) {
				if (!freeSpeedTravTimePerAgent.containsKey(person)) {
					freeSpeedTravTimePerAgent.put(person, 0.0);
					lossTimePerAgent.put(person, 0.0);
					travTimePerAgent.put(person, 0.0);
					modePerPerson.put(person, Collections.singleton("inactive"));
					lossTimeLimitPerAgent.put(person, limitRelativeLossTime);
				}
			}


			//calculate percentage loss time per agent
			for (String person : freeSpeedTravTimePerAgent.keySet()) {
				double temp_lossTimeIndexValue;

				// Avoid devision of 0
				if (freeSpeedTravTimePerAgent.get(person) != 0 && travTimePerAgent.get(person) >=freeSpeedTravTimePerAgent.get(person)) {
					double percentage = ((travTimePerAgent.get(person) - freeSpeedTravTimePerAgent.get(person)) / freeSpeedTravTimePerAgent.get(person));
					lossTimePercentagePerAgent.put(person, percentage);
				} else {
					lossTimePercentagePerAgent.put(person, 0.0); // If freeSpeedTime is 0 or greater than travTimePerAgent, we set the loss to 0%
				}

				temp_lossTimeIndexValue = (lossTimePercentagePerAgent.get(person) - limitRelativeLossTime) / limitRelativeLossTime;
				lossTimeIndexValuePerAgent.put(person, temp_lossTimeIndexValue);


			}

			for (double lossTime : lossTimePerAgent.values()) {
				sumTotalLossTime = sumTotalLossTime + lossTime;
			}


			formattedSumTotalLossTime = formatDuration(Duration.ofSeconds((long) sumTotalLossTime));

			//****************** Overall Index calculating ******************************************************

			// calculating percentage of agents with deviation under 0 (index value per indicator) and for further analysis also for deviation -0.5 and +0.5
			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentId = entry.getKey();

				double longestTripDeviation = longestTripIndexValuePerAgent.get(agentId);
				double lossTimeDeviation = lossTimeIndexValuePerAgent.get(agentId);
				double overallTravelTimeIndexValue = Math.max(longestTripDeviation, lossTimeDeviation);

				overallTravelTimeIndexValuePerAgent.put(agentId, overallTravelTimeIndexValue);

				if (longestTripDeviation <= 0) {
					counterIndexLongestTrip++;
				}
				if (lossTimeDeviation <= 0) {
					counterIndexLossTime++;
				}
				if (overallTravelTimeIndexValue <= 0) {
					counterOverallTravelTime++;
				}
				if (overallTravelTimeIndexValue <= -0.5) {
					counter50PercentUnderLimit++;
				}
				if (overallTravelTimeIndexValue <= 0.5) {
					counter50PercentOverLimit++;
				}

			}

			// calculating overall index values and indicator index values
			longestTripIndexValue = counterIndexLongestTrip / homeCoordinatesPerAgentInStudyArea.size();
			formattedLongestTripIndexValue = String.format(Locale.US, "%.2f%%", longestTripIndexValue * 100);

			lossTimeIndexValue = counterIndexLossTime / homeCoordinatesPerAgentInStudyArea.size();
			formattedLossTimeIndexValue = String.format(Locale.US, "%.2f%%", lossTimeIndexValue * 100);

			travelTimeIndexValue = counterOverallTravelTime / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTimeIndexValue = String.format(Locale.US, "%.2f%%", travelTimeIndexValue * 100);

			travelTime50PercentUnderIndexValue = counter50PercentUnderLimit / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTime50PercentUnderIndexValue = String.format(Locale.US, "%.2f%%", travelTime50PercentUnderIndexValue * 100);

			travelTime50PercentOverIndexValue = counter50PercentOverLimit / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTime50PercentOverIndexValue = String.format(Locale.US, "%.2f%%", travelTime50PercentOverIndexValue * 100);

			meanTotalLossTime = lossTimePerAgent.values().stream().collect(Collectors.averagingDouble(Double::doubleValue));
			formattedMeanTotalLossTime = String.format(Locale.US, "%.2f min", meanTotalLossTime / 60);

			medianTotalLossTime = AgentLiveabilityInfoCollection.calculateMedian(lossTimePerAgent);
			formattedMedianTotalLossTime = String.format(Locale.US, "%.2f min", medianTotalLossTime / 60);

			medianLongestCarTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "car".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			formattedMedianLongestCarTrip = String.format(Locale.US, "%.2f min", medianLongestCarTrip);

			medianLongestPTTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "pt".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			formattedMedianLongestPTTrip = String.format(Locale.US, "%.2f min", medianLongestPTTrip);

			medianLongestRideTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "ride".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			formattedMedianLongestRideTrip = String.format(Locale.US, "%.2f min", medianLongestRideTrip);

			medianLongestTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent);
			formattedMedianLongestTrip = String.format(Locale.US, "%.2f min", medianLongestTrip);

			//Write Information in Agent Livability Info Collection
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripTravelTimePerAgent, "maxTravelTimePerTrip");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripLimitPerAgent, "limit_maxTravelTimePerTrip");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripIndexValuePerAgent, "indexValue_maxTravelTimePerTrip");

			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimePerAgent, "Loss Time");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(travTimePerAgent, "Travel Time");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimePercentagePerAgent, "percentageLossTime");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimeLimitPerAgent, "limit_relativeLossTime");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimeIndexValuePerAgent, "indexValue_relativeLossTime");

			agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedTravelTimeIndexValue, "Travel Time Index Value");

			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Travel Time", "Longest trip", formattedMedianLongestTrip, "60 / 30 / inf min", formattedLongestTripIndexValue);
			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Travel Time", "Loss time", formattedMedianTotalLossTime, String.valueOf(limitRelativeLossTime), formattedLossTimeIndexValue);

			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTLossTimeAgentMapPath, lossTimeIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);
			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTLongestTripAgentMapPath, longestTripIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);
			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTTravelTimeAgentMapPath, overallTravelTimeIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);

			try (CSVWriter agentBasedWriter = new CSVWriter(new FileWriter(String.valueOf(statsTravelTimePerAgentPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

				agentBasedWriter.writeNext(new String[]{
					"Person",
					"lossTimePerAgent",
					"travTimePerAgent",
					"percentageLossTime",
					"TQLossTimeDeviationFromLimit",
					"modesUsed",
					"longestTripMode",
					"longestTripTravelTime",
					"TQLongestTripDeviationFromLimit",
					"Travel Quality Index Value"
				});

				for (String person : homeCoordinatesPerAgentInStudyArea.keySet()) {
					agentBasedWriter.writeNext(new String[]{
						person,
						String.valueOf(lossTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(travTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(lossTimePercentagePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(lossTimeIndexValuePerAgent.getOrDefault(person, 0.0)),
						String.join("-", modePerPerson.getOrDefault(person, Set.of())),
						String.valueOf(longestTripModePerAgent.getOrDefault(person, "")),
						String.valueOf(longestTripTravelTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(longestTripIndexValuePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(overallTravelTimeIndexValuePerAgent.get(person))
					});
				}
			}

			try (CSVWriter longestTripCarTravelTimePerAgentWriter = new CSVWriter(new FileWriter(String.valueOf(HistogramLongestCarTravelPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {


				longestTripCarTravelTimePerAgentWriter.writeNext(new String[]{
					"Person",
					"travTimePerAgent"
				});

				for (String person : longestTripCarTravelTimePerAgent.keySet()) {
					longestTripCarTravelTimePerAgentWriter.writeNext(new String[]{
						person,
						String.valueOf(longestTripCarTravelTimePerAgent.get(person)),
					});
				}
			}

			try (CSVWriter longestTripRideTravelTimePerAgentWriter = new CSVWriter(new FileWriter(String.valueOf(HistogramLongestRideTravelPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {


				longestTripRideTravelTimePerAgentWriter.writeNext(new String[]{
					"Person",
					"travTimePerAgent"
				});

				for (String person : longestTripRideTravelTimePerAgent.keySet()) {
					longestTripRideTravelTimePerAgentWriter.writeNext(new String[]{
						person,
						String.valueOf(longestTripRideTravelTimePerAgent.get(person)),
					});
				}
			}

			try (CSVWriter longestTripPtTravelTimePerAgentWriter = new CSVWriter(new FileWriter(String.valueOf(HistogramLongestPtTravelPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {


				longestTripPtTravelTimePerAgentWriter.writeNext(new String[]{
					"Person",
					"travTimePerAgent"
				});

				for (String person : longestTripPtTravelTimePerAgent.keySet()) {
					longestTripPtTravelTimePerAgentWriter.writeNext(new String[]{
						person,
						String.valueOf(longestTripPtTravelTimePerAgent.get(person)),
					});
				}
			}

			try (CSVWriter longestTripDepTimePerAgentWriter = new CSVWriter(new FileWriter(String.valueOf(HistogramLongestTripDepPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

				// Calculation of departures per time interval
				Map<String, Integer> departuresPerInterval = calculateDeparturesPerInterval(longestTripDepTimePerAgent, 15);
				Map<String, Integer> sortedMap = new TreeMap<>(departuresPerInterval);

				// Header
				longestTripDepTimePerAgentWriter.writeNext(new String[]{"timeInterval", "numberOfDepartures"});

				// Write the time intervals and the number of departures
				for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
					longestTripDepTimePerAgentWriter.writeNext(new String[]{entry.getKey(), String.valueOf(entry.getValue())});

				}
			}

			try (CSVWriter lossTimeDepWriter = new CSVWriter(new FileWriter(String.valueOf(HistogramLossTimeDepPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {


				Map<String, Double> sortedMap = new TreeMap<>(lossTimePerTimeIntervall);
				// Header
				lossTimeDepWriter.writeNext(new String[]{"timeInterval", "LossSeconds"});

				// Write the time intervals and the number of departures
				for (Map.Entry<String, Double> entry : sortedMap.entrySet()) {
					lossTimeDepWriter.writeNext(new String[]{entry.getKey(), String.valueOf(entry.getValue())});

				}
			}

			try (CSVWriter lossTimePerModeWriter = new CSVWriter(new FileWriter(String.valueOf(BarChartLossTimePerMode)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

				lossTimePerModeWriter.writeNext(new String[]{
					"mode",
					"cumulative_loss_time",
					"failed_routings"
				});

				for (String mode : lossTimePerMode.keySet()) {
					lossTimePerModeWriter.writeNext(new String[]{
						mode,
						String.valueOf(lossTimePerMode.getOrDefault(mode, 0.0)),
						String.valueOf(failedRoutingOccurances.getOrDefault(mode, 0L))
					});
				}
			}

			try(CSVWriter modesCounterWriter = new CSVWriter(new FileWriter(String.valueOf(BarChartNumberOfModesPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)){

				modesCounterWriter.writeNext(new String[]{"mode", "count"});
				counterModesPerLeg.forEach((mode, i) ->
					modesCounterWriter.writeNext(new String[]{mode, String.valueOf(i)})
				);
			}



			// generating output files for the green space dashboard page
			try (CSVWriter TQTileWriter = new CSVWriter(new FileWriter(TilesTravelTimePath.toFile()));
				 CSVWriter LongestTripWriter = new CSVWriter(new FileWriter(TilesLogestTripTimePath.toFile()));
				 CSVWriter LossTimeWriter = new CSVWriter(new FileWriter(TilesLossTimePath.toFile()))) {

				TQTileWriter.writeNext(new String[]{"Traffic Quality: 50% under limit", formattedTravelTime50PercentUnderIndexValue});
				TQTileWriter.writeNext(new String[]{"Traffic Quality within limit", formattedTravelTimeIndexValue});
				TQTileWriter.writeNext(new String[]{"Traffic Quality: 50% over limit", formattedTravelTime50PercentOverIndexValue});

				LongestTripWriter.writeNext(new String[]{"Longest Trip Index Value", formattedLongestTripIndexValue});
				LongestTripWriter.writeNext(new String[]{"Median Longest Car Trip Duration", formattedMedianLongestCarTrip});
				LongestTripWriter.writeNext(new String[]{"Median Longest PT Trip Duration", formattedMedianLongestPTTrip});
				LongestTripWriter.writeNext(new String[]{"Median Longest Ride Trip Duration", formattedMedianLongestRideTrip});

				LossTimeWriter.writeNext(new String[]{"Loss Time Index Value", formattedLossTimeIndexValue});
				LossTimeWriter.writeNext(new String[]{"Mean loss time (h:m:s)", formattedMeanTotalLossTime});
				LossTimeWriter.writeNext(new String[]{"Median loss time (h:m:s)", formattedMedianTotalLossTime});
				LossTimeWriter.writeNext(new String[]{"Sum loss time (h:m:s)", formattedSumTotalLossTime});

				System.out.println("LossTimeSum (HH:mm:ss): " + formattedSumTotalLossTime);
				System.out.println("LossTimeRanking: " + formattedLossTimeIndexValue);

			}


		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (NumberFormatException e) {
			throw new RuntimeException(e);
		}

		// end of call method
		return 0;
	}


	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}

	// Calculate free speed travel time


	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2, String mode, long backup_travelTime) {
		double travelTimeInSeconds = 0;
		Map<String, Double> beelineDistanceFactors = routingConfig.getBeelineDistanceFactors(); // beelineFactors
		Map<String, Double> teleportedModeSpeeds = routingConfig.getTeleportedModeSpeeds(); // get average Speeds

		switch (mode.toLowerCase()) {
			case "bike":
			case "walk":
				double distance = CoordUtils.calcEuclideanDistance(point1, point2);
				// use of beeline factor
				distance *= beelineDistanceFactors.getOrDefault(mode, 1.3);

				return distance / teleportedModeSpeeds.getOrDefault(mode, 1.23);

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
					//	System.out.println("Mode " + mode + " is not allowed on this link.");
						failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
						return backup_travelTime;  // In case the mode is not allowed on this link, return 0
					}
				};

				TravelDisutility travelDisutility = new OnlyTimeDependentTravelDisutility(freeSpeedTravelTime);
				LeastCostPathCalculator router = new DijkstraFactory().createPathCalculator(network, travelDisutility, freeSpeedTravelTime);
				LeastCostPathCalculator.Path path = router.calcLeastCostPath(startNode, endNode, 0, null, null);

				if (path == null || path.links.isEmpty()) {
				//	System.out.println("No route found for mode " + mode + ".");
					failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
					return backup_travelTime;
				} else {
					// Use the total time of the calculated path
					return path.travelTime;
				}

			case "pt":
			default:
			//	System.out.println("No calculation of free speed travel time defined for mode: " + mode);
		}

		return backup_travelTime;
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
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	// Method that calculates the departures per time interval, starting at 00:00
	public static Map<String, Integer> calculateDeparturesPerInterval(Map<String, String> depTimePerAgent, int intervalInMinutes) throws ParseException {
		Map<String, Integer> departuresPerInterval = new HashMap<>();

		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss"); // Time format "HH:mm:ss"

		// Calculate the number of intervals for a day
		int intervalsPerDay = (24 * 60) / intervalInMinutes;

		// Iterate over all persons and their departure times
		for (String person : depTimePerAgent.keySet()) {
			String depTimeStr = depTimePerAgent.get(person);
			Date depTime;
			try {
				depTime = timeFormat.parse(depTimeStr);
			} catch (ParseException e) {
				// Log the error or handle it differently
			//	System.err.println("Invalid departure time: " + depTimeStr);
				continue;  // Skip the invalid departure time
			} // Convert the time to a Date object

			// Determine the interval into which the departure time falls
			String intervalStart = getIntervalStart(depTime, intervalInMinutes);
			departuresPerInterval.put(intervalStart, departuresPerInterval.getOrDefault(intervalStart, 0) + 1);
		}

		return departuresPerInterval;
	}

	// Method to determine the interval start based on the departure time
	private static String getIntervalStart(Date depTime, int intervalInMinutes) {

		SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm"); // Time format "HH:mm"
		Calendar cal = Calendar.getInstance();
		cal.setTime(depTime);

		// Calculate the minutes since midnight (00:00)
		int minutesSinceMidnight = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

		// Determine the interval by dividing the minutes by the length of the interval
		int intervalIndex = minutesSinceMidnight / intervalInMinutes;

		// Calculate the start time of the interval
		Calendar intervalStartCal = Calendar.getInstance();
		intervalStartCal.set(Calendar.HOUR_OF_DAY, 0); // Set to midnight (00:00)
		intervalStartCal.set(Calendar.MINUTE, intervalIndex * intervalInMinutes); // Set to the interval
		intervalStartCal.set(Calendar.SECOND, 0);
		intervalStartCal.set(Calendar.MILLISECOND, 0);

		return timeFormat.format(intervalStartCal.getTime());
	}


}

/*package org.matsim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import java.io.IOException;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	dependsOn = {
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "agentLiveabilityInfo.csv"),
		@Dependency(value = AgentLiveabilityInfoCollection.class, files = "overallRankingTile.csv")
	},
	requires = {
		"output_legs.csv.gz"
	},
	produces = {
		"output_legsLossTime_new.csv",
		"summary_modeSpecificLegsLossTime.csv",
		"travelTime_stats_perAgent.csv",
		"travelTime_XYTAgentBasedTrafficQualityMap.xyt.csv",
		"travelTime_XYTAgentBasedLongestTripMap.xyt.csv",
		"travelTime_XYTAgentBasedLossTimeMap.xyt.csv",
		"travelTime_TilesOverall.csv",
		"travelTime_TilesLongestTrip.csv",
		"travelTime_TilesLossTime.csv"
	}
)

public class AgentBasedTrafficQualityAnalysis implements MATSimAppCommand {


	//OFFEN: PRÜFEN WIE OFT DER DER WERT ÜBERSCHRITTEN WIRD UND DIE GESAMTLOSSTIME DABEI KLEINER ALS 2/3 MINUTEN IST - WENN DAS VIELE SIND
	//MUSS EIN ZWEITER FAKTOR (MINIMALE ABSOLUTE LOSS TIME) ZUR EXKLUSION ERGÄNZT WERDEN

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedTrafficQualityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedTrafficQualityAnalysis.class);

	private static RoutingConfigGroup routingConfig;

	//overwritten with config value at beginning of call method
	double sampleSize = 0.1;

	// defining the limits for the indicators
	private static final Map<String, Duration> LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE = Map.of(
		"car", Duration.ofMinutes(30),
		"walk", Duration.ofNanos(Long.MAX_VALUE), // max value because of no limit
		"bike", Duration.ofNanos(Long.MAX_VALUE),  // max value because of no limit
		"pt", Duration.ofMinutes(60),
		"ride", Duration.ofMinutes(60),
		"fright", Duration.ofNanos(Long.MAX_VALUE) // max value because of no limit defined
	);

	private final double limitRelativeLossTime = 0.2;

	// constants for paths
	// inputPath
	private final Path inputLegsCsvFile = ApplicationUtils.matchInput("output_legs.csv.gz", getValidOutputDirectory());
	private final Path agentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	private final Path networkPath = ApplicationUtils.matchInput("network.xml.gz", getValidOutputDirectory());
	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	private final Path inputTripsCsvFile = ApplicationUtils.matchInput("output_trips.csv.gz", getValidOutputDirectory());

	// outputPath
	private final Path outputSummaryPath = getValidLiveabilityOutputDirectory().resolve("summary_modeSpecificLegsLossTime.csv");
	private final Path outputCSVPath = getValidLiveabilityOutputDirectory().resolve("output_legsLossTime_new.csv");
	private final Path statsTravelTimePerAgentPath = getValidLiveabilityOutputDirectory().resolve("travelTime_stats_perAgent.csv");
	private final Path XYTLossTimeAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYTAgentBasedLossTimeMap.xyt.csv");
	private final Path XYTLongestTripAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYTAgentBasedLongestTripMap.xyt.csv");
	private final Path XYTTravelTimeAgentMapPath = getValidLiveabilityOutputDirectory().resolve("travelTime_XYTAgentBasedTrafficQualityMap.xyt.csv");
	private final Path TilesTravelTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_TilesOverall.csv");
	private final Path TilesLogestTripTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_TilesLongestTrip.csv");
	private final Path TilesLossTimePath = getValidLiveabilityOutputDirectory().resolve("travelTime_TilesLossTime.csv");

	public static void main(String[] args) {
		new AgentBasedTrafficQualityAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {

		//load network & execute NetworkCleaner
		Network network = NetworkUtils.readNetwork(String.valueOf(networkPath));

		NetworkCleaner cleaner = new NetworkCleaner();
		cleaner.run(network);

		//loads sample size from config
		Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("config.xml", input.getRunDirectory()).toAbsolutePath().toString());
		SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		this.sampleSize = simwrapper.sampleSize;
		this.routingConfig = config.routing();

		// initialising collections and data structures
		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();

		// defining all maps to be able to put and get values of those throughout the analysis
		Map<String, String> dimensionOverallRankingValue = new HashMap<>();
		Map<String, Double> overallTravelTimeIndexValuePerAgent = new HashMap<>();

		Map<String, Map<String, Duration>> longestTripsPerModePerAgentAbsoluteValue = new HashMap<>();
		Map<String, Map<String, Double>> longestTripsPerModePerAgentIndexValue = new HashMap<>();
		Map<String, Double> longestTripIndexValuePerAgent = new HashMap<>();
		Map<String, Double> longestTripLimitPerAgent = new HashMap<>();
		Map<String, String> longestTripModePerAgent = new HashMap<>();
		Map<String, Double> longestTripTravelTimePerAgent = new HashMap<>();

		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		Map<String, Long> failedRoutingOccurances = new HashMap<>();
		Map<String, Double> travTimePerAgent = new HashMap<>();
		Map<String, Double> freeSpeedTravTimePerAgent = new HashMap<>();
		Map<String, Double> lossTimePerAgent = new HashMap<>();
		Map<String, Double> lossTimeLimitPerAgent = new HashMap<>();
		Map<String, Double> lossTimePercentagePerAgent = new HashMap<>();
		Map<String, Double> lossTimeIndexValuePerAgent = new HashMap<>();
		Map<String, Double> lossTimePerMode = new HashMap<>();
		Map<String, Set<String>> modePerPerson = new HashMap<>();


		// initializing counters for index value calculations
		double counterOverallTravelTime = 0;
		double counterIndexLongestTrip = 0;
		double counterIndexLossTime = 0;
		double counter50PercentUnderLimit = 0;
		double counter50PercentOverLimit = 0;
		double sumTotalLossTime = 0.0;


		// initializing counters for calculate only for some agents
		int limit = -1; // set to -1 for no limit
		int count = 0;

		// declaring other variables for later use
		double longestTripIndexValue;
		String formattedLongestTripIndexValue;
		double lossTimeIndexValue;
		String formattedLossTimeIndexValue;
		double travelTimeIndexValue;
		String formattedTravelTimeIndexValue;
		double travelTime50PercentUnderIndexValue;
		String formattedTravelTime50PercentUnderIndexValue;
		double travelTime50PercentOverIndexValue;
		String formattedTravelTime50PercentOverIndexValue;
		double meanTotalLossTime;
		String formattedMeanTotalLossTime;
		double medianTotalLossTime;
		String formattedMedianTotalLossTime;
		double medianLongestCarTrip;
		String formattedMedianLongestCarTrip;
		double medianLongestPTTrip;
		String formattedMedianLongestPTTrip;
		double medianLongestRideTrip;
		String formattedMedianLongestRideTrip;

		String formattedSumTotalLossTime;


		//******************** indicator absolute travel time *******************************
		//prepping a map for all study area agents to be used for writing files and calculating index values
		try (Reader studyAreaAgentReader = new FileReader(inputAgentLiveabilityInfoPath.toFile());
			 CSVParser studyAreaAgentParser = new CSVParser(studyAreaAgentReader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

			for (CSVRecord record : studyAreaAgentParser) {
				String id = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(id, Arrays.asList(homeX, homeY));
			}
		}

		try (InputStream fileStream = new FileInputStream(inputTripsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'))) {

			// Iterate over each row of the CSV file
			for (CSVRecord record : legsParser) {

				String person = record.get("person");
				if (!homeCoordinatesPerAgentInStudyArea.containsKey(person)) {
					continue;
				}
				// Extract relevant values from the CSV
				String mainMode = record.get("main_mode");
				String tripId = record.get("trip_id");
				String travTimeStr = record.get("trav_time");

				// Parse the travel time (trav_time) into a Duration
				Duration travelTime = parseTime(travTimeStr);

				// Initialize the inner map if not already present
				longestTripsPerModePerAgentAbsoluteValue.putIfAbsent(person, new HashMap<>());
				Map<String, Duration> modeMap = longestTripsPerModePerAgentAbsoluteValue.get(person);

				// Calculate the longest trip per mode and store the maximum duration
				modeMap.merge(mainMode, travelTime, (existing, newDuration) ->
					newDuration.compareTo(existing) > 0 ? newDuration : existing);
			}

			for (Map.Entry<String, Map<String, Duration>> personEntry : longestTripsPerModePerAgentAbsoluteValue.entrySet()) {
				String person = personEntry.getKey();
				Map<String, Duration> modeMap = personEntry.getValue();

				// Initialize the inner map for the second map if not already present
				longestTripsPerModePerAgentIndexValue.putIfAbsent(person, new HashMap<>());
				Map<String, Double> modeIndexMap = longestTripsPerModePerAgentIndexValue.get(person);

				// Iterate over the mode map of the respective person
				for (Map.Entry<String, Duration> modeEntry : modeMap.entrySet()) {
					String mode = modeEntry.getKey();
					double longestDuration = modeEntry.getValue().toSeconds();
					double indexLimitSec = LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE.getOrDefault(mode, Duration.ofNanos(Long.MAX_VALUE)).toSeconds(); // Default biggest possible value as duration

					// Calculate the expression (Duration - indexLimit) / indexLimit
					double calculatedValue = (longestDuration - indexLimitSec) / indexLimitSec;

					// Store the result in the second map
					modeIndexMap.put(mode, calculatedValue);
				}
			}

			for (Map.Entry<String, Map<String, Double>> personEntry : longestTripsPerModePerAgentIndexValue.entrySet()) {
				String person = personEntry.getKey();
				Map<String, Double> modeMap = personEntry.getValue();
				double maxValue = -1;
				double limitPerAgent = 0;
				double maxTravelTime = Double.MAX_VALUE;
				String mode = "";

				// Find the maximum value in the inner map for each person
				for (Map.Entry<String, Double> modeEntry : modeMap.entrySet()) {
					double currentValue = modeEntry.getValue();


					// If the current value is greater than the known maximum value, update the maxValue
					if (currentValue > maxValue) {
						maxValue = currentValue;
						mode = modeEntry.getKey();
						limitPerAgent = LIMIT_ABSOLUTE_TRAVEL_TIME_PER_MODE.get(mode).toMinutes();
						maxTravelTime = longestTripsPerModePerAgentAbsoluteValue.get(person).get(mode).toMinutes();
					}
				}
				longestTripLimitPerAgent.put(person, limitPerAgent);
				longestTripIndexValuePerAgent.put(person, maxValue);
				longestTripTravelTimePerAgent.put(person, maxTravelTime);
				longestTripModePerAgent.put(person, mode);
			}
		}


		//******************** indicator loss time *******************************

		//read Input-legs.csv file and write new output files for loss time analysis
		try (InputStream fileStream = new FileInputStream(inputLegsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter legsLossTimeWriter = new CSVWriter(new FileWriter(String.valueOf(outputCSVPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			// writing csv-headers for bw Legs Loss time output files
			legsLossTimeWriter.writeNext(new String[]{
				"person",
				"trip_id",
				"mode",
				"trav_time",
				"fs_trav_time",
				"loss_time",
				"percent_lossTime",
				"trav_time_hms",
				"fs_trav_time_hms",
				"loss_time_hms",
				"dep_time",
				"start_x",
				"start_y",
				"start_node_found",
				"start_link",
				"end_x",
				"end_y",
				"end_node_found",
				"end_link"
			});


			for (CSVRecord legRecord : legsParser) {


				// if statement for calculating only some agents
				if (count >= limit && limit != -1) {
					break;
				}
				String person = legRecord.get("person");
				if (!homeCoordinatesPerAgentInStudyArea.containsKey(person)) {
					continue;
				}

				//Initializing leg variables
				double legLossTimePrecentage = 0.0;

				//Decelerating leg variables
				Duration legTravTime;
				String formattedLegTravTime;
				long legFreeSpeedTravTimeInSeconds;
				Duration legFreeSpeedTravTime;
				String formattedLegFsTravTime;

				Duration legLossTime;
				String formattedLegLegLossTime;


				// collection of values from existing legs.csv to take over to the new legsLossTime.csv
				double startX = Double.parseDouble(legRecord.get("start_x"));
				double startY = Double.parseDouble(legRecord.get("start_y"));
				String startLink = legRecord.get("start_link");
				double endX = Double.parseDouble(legRecord.get("end_x"));
				double endY = Double.parseDouble(legRecord.get("end_y"));
				String endLink = legRecord.get("end_link");


				String tripId = legRecord.get("trip_id");
				String mode = legRecord.get("mode");
				String depTime = legRecord.get("dep_time");
				String travTimeString = legRecord.get("trav_time");

				// calculate actual simulation values for export
				legTravTime = parseTime(travTimeString);
				formattedLegTravTime = formatDuration(legTravTime);

				// prep data for free speed travel time calculation
				Coord startPoint = new Coord(startX, startY);
				Coord endPoint = new Coord(endX, endY);

				Node startNodeFound = NetworkUtils.getNearestNode(network, startPoint);
				Node endNodeFound = NetworkUtils.getNearestNode(network, endPoint);

				// free speed travel time is calculated by calling the calculateFreeSpeedTravelTime method for every start-end-point-combination
				legFreeSpeedTravTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, startPoint, endPoint, mode, legTravTime.getSeconds());

				//transforming the resulting free speed travel time into the duration format as well es the String hh:mm:ss format
				legFreeSpeedTravTime = Duration.ofSeconds(legFreeSpeedTravTimeInSeconds);
				formattedLegFsTravTime = formatDuration(legFreeSpeedTravTime);

				// defining loss time as the difference of travel time minus freeSpeedTravelTime (place holders are intercepted by the if-clause)
				// data type is Long as no double is required since seconds is the smallest unit used in matsim
				//Long legLossTimeInSeconds;
				if (legFreeSpeedTravTimeInSeconds != -1 && legFreeSpeedTravTimeInSeconds != -2) {
					legLossTime = legTravTime.minus(legFreeSpeedTravTime);
					//legLossTimeInSeconds = legTravTimeInSeconds - legFreeSpeedTravTimeInSeconds;
					if (legLossTime.getSeconds() < 0) {
						legLossTime = Duration.ZERO;
					} // avoiding negative loss times
				} else {
					legLossTime = Duration.ZERO;
					failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
				}

				// transforming the seconds of the loss time calculation to the duration format and the String (hh:mm:ss) afterwards
				//Duration lostTimeHMS = Duration.ofSeconds(legLossTimeInSeconds);
				formattedLegLegLossTime = formatDuration(legLossTime);

				// calculating the percentage of loss time compared to the free speed (minimum possible) travel time for positive loss time values
				//double percentLossTime = 0.0;
				if (legLossTime.getSeconds() != 0) {
					legLossTimePrecentage = (double) legLossTime.getSeconds() / legFreeSpeedTravTime.getSeconds();
				} else {
					System.out.println("Warnung: Division by Zero for trip " + tripId + " avoided.");
				}


				// calculate sum of travel time and of loss time per agent. overall loss time sum and mode per person info
				freeSpeedTravTimePerAgent.put(person, freeSpeedTravTimePerAgent.getOrDefault(person, 0.0) + legFreeSpeedTravTime.getSeconds());
				lossTimePerAgent.put(person, lossTimePerAgent.getOrDefault(person, 0.0) + legLossTime.getSeconds());
				lossTimePerMode.put(mode, lossTimePerMode.getOrDefault(mode, 0.0) + legLossTime.getSeconds());
				travTimePerAgent.put(person, travTimePerAgent.getOrDefault((Object) person, 0.0) + legTravTime.getSeconds());
				modePerPerson.computeIfAbsent(person, k -> new HashSet<>()).add(mode);
				lossTimeLimitPerAgent.put(person, limitRelativeLossTime);

				// writing the desired columns in the new legsLossTime output csv file
				legsLossTimeWriter.writeNext(new String[]{
					person,
					tripId,
					mode,
					String.valueOf(legTravTime.getSeconds()),
					String.valueOf(legFreeSpeedTravTime.getSeconds()),
					String.valueOf(legLossTime.getSeconds()),
					String.valueOf(legLossTimePrecentage),
					formattedLegTravTime,
					formattedLegFsTravTime,
					formattedLegLegLossTime,
					depTime,
					String.valueOf(startX),
					String.valueOf(startY),
					String.valueOf(startNodeFound.getId()),
					startLink,
					String.valueOf(endX),
					String.valueOf(endY),
					String.valueOf(endNodeFound.getId()),
					endLink
				});

				//System.out.println("Neue LossTime für Person "+person);
				count++;

				//		});
			}

			//calculate percentage loss time per agent
			for (String person : freeSpeedTravTimePerAgent.keySet()) {
				double freeSpeedTime = freeSpeedTravTimePerAgent.get(person);
				double temp_lossTimeIndexValue;
				double lossTime = lossTimePerAgent.getOrDefault(person, 0.0);

				// Avoid devision of 0
				if (freeSpeedTravTimePerAgent.get(person) != 0) {
					double percentage = ((travTimePerAgent.get(person) - freeSpeedTravTimePerAgent.get(person)) / freeSpeedTravTimePerAgent.get(person));
					lossTimePercentagePerAgent.put(person, percentage);
				} else {
					lossTimePercentagePerAgent.put(person, 0.0); // If freeSpeedTime is 0, we set the loss to 0%
				}

				temp_lossTimeIndexValue = (lossTimePercentagePerAgent.get(person) - limitRelativeLossTime) / limitRelativeLossTime;
				lossTimeIndexValuePerAgent.put(person, temp_lossTimeIndexValue);


			}

			for (double lossTime : lossTimePerAgent.values()) {
				sumTotalLossTime = sumTotalLossTime + lossTime;
			}


			formattedSumTotalLossTime = formatDuration(Duration.ofSeconds((long) sumTotalLossTime));

//			double lossTimeRankingValue = counterIndexLossTime / lossTimeIndexValuePerAgent.size();
//			String formattedRankingValueLossTime = String.format(Locale.US, "%.2f%%", lossTimeRankingValue * 100);
//			dimensionOverallRankingValue.put("Loss Time", formattedRankingValueLossTime);


			//****************** Overall Index calculating ******************************************************


			// calculating percentage of agents with deviation under 0 (index value per indicator) and for further analysis also for deviation -0.5 and +0.5
			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentId = entry.getKey();

				double longestTripDeviation = longestTripIndexValuePerAgent.getOrDefault(agentId, -1.0);
				double lossTimeDeviation = lossTimeIndexValuePerAgent.getOrDefault(agentId, -1.0);
				double overallTravelTimeIndexValue = Math.max(longestTripDeviation, lossTimeDeviation);

				overallTravelTimeIndexValuePerAgent.put(agentId, overallTravelTimeIndexValue);

				if (longestTripDeviation <= 0) {counterIndexLongestTrip++;}
				if (lossTimeDeviation <= 0) {counterIndexLossTime++;}
				if (overallTravelTimeIndexValue <= 0) {counterOverallTravelTime++;}
				if (overallTravelTimeIndexValue <= -0.5) {counter50PercentUnderLimit++;}
				if (overallTravelTimeIndexValue <= 0.5) {counter50PercentOverLimit++;}

			}

			// calculating overall index values and indicator index values
			longestTripIndexValue = counterIndexLongestTrip / homeCoordinatesPerAgentInStudyArea.size();
			formattedLongestTripIndexValue = String.format(Locale.US, "%.2f%%", longestTripIndexValue * 100);

			lossTimeIndexValue = counterIndexLossTime / homeCoordinatesPerAgentInStudyArea.size();
			formattedLossTimeIndexValue = String.format(Locale.US, "%.2f%%", lossTimeIndexValue * 100);

			travelTimeIndexValue = counterOverallTravelTime / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTimeIndexValue = String.format(Locale.US, "%.2f%%", travelTimeIndexValue * 100);

			travelTime50PercentUnderIndexValue = counter50PercentUnderLimit / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTime50PercentUnderIndexValue = String.format(Locale.US, "%.2f%%", travelTime50PercentUnderIndexValue * 100);

			travelTime50PercentOverIndexValue = counter50PercentOverLimit / homeCoordinatesPerAgentInStudyArea.size();
			formattedTravelTime50PercentOverIndexValue = String.format(Locale.US, "%.2f%%", travelTime50PercentOverIndexValue * 100);

			meanTotalLossTime = lossTimePerAgent.values().stream().collect(Collectors.averagingDouble(Double::doubleValue));
			formattedMeanTotalLossTime = String.format(Locale.US, "%.2f min", meanTotalLossTime /60);

			medianTotalLossTime = AgentLiveabilityInfoCollection.calculateMedian(lossTimePerAgent);
			formattedMedianTotalLossTime = String.format(Locale.US, "%.2f min", medianTotalLossTime /60);

			medianLongestCarTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "car".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
			formattedMedianLongestCarTrip  = String.format(Locale.US, "%.2f min", medianLongestCarTrip);
			medianLongestPTTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "pt".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));;
			formattedMedianLongestPTTrip  = String.format(Locale.US, "%.2f min", medianLongestPTTrip);
			medianLongestRideTrip = AgentLiveabilityInfoCollection.calculateMedian(longestTripTravelTimePerAgent.entrySet().stream()
				.filter(entry -> "ride".equals(longestTripModePerAgent.get(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));;
			formattedMedianLongestRideTrip = String.format(Locale.US, "%.2f min", medianLongestRideTrip);

			//Write Information in Agent Livability Info Collection
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripTravelTimePerAgent, "maxTravelTimePerTrip");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripLimitPerAgent, "limit_maxTravelTimePerTrip");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(longestTripIndexValuePerAgent, "indexValue_maxTravelTimePerTrip");

			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimePerAgent, "Loss Time");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(travTimePerAgent, "Travel Time");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimePercentagePerAgent, "percentageLossTime");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimeLimitPerAgent, "limit_relativeLossTime");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(lossTimeIndexValuePerAgent, "indexValue_relativeLossTime");

			agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedTravelTimeIndexValue, "Travel Time Index Value");

			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Travel Time", "Longest trip", formattedMedianLongestCarTrip, "60 / 30 / inf", formattedLongestTripIndexValue, 1);
			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Travel Time", "Loss time", formattedMedianTotalLossTime, String.valueOf(limitRelativeLossTime), formattedLossTimeIndexValue, 1);

			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTLossTimeAgentMapPath, lossTimeIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);
			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTLongestTripAgentMapPath, longestTripIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);
			AgentLiveabilityInfoCollection.writeXYTDataToCSV(XYTTravelTimeAgentMapPath, overallTravelTimeIndexValuePerAgent, homeCoordinatesPerAgentInStudyArea);

			try (CSVWriter agentBasedWriter = new CSVWriter(new FileWriter(String.valueOf(statsTravelTimePerAgentPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

				agentBasedWriter.writeNext(new String[]{
					"Person",
					"lossTimePerAgent",
					"travTimePerAgent",
					"percentageLossTime",
					"TQLossTimeDeviationFromLimit",
					"modesUsed",
					"longestTripMode",
					"longestTripTravelTime",
					"TQLongestTripDeviationFromLimit"
				});

				for (String person : homeCoordinatesPerAgentInStudyArea.keySet()) {
					agentBasedWriter.writeNext(new String[]{
						person,
						String.valueOf(lossTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(travTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(lossTimePercentagePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(lossTimeIndexValuePerAgent.getOrDefault(person, 0.0)),
						String.join("-", modePerPerson.getOrDefault(person, Set.of())),
						String.valueOf(longestTripModePerAgent.getOrDefault(person, "")),
						String.valueOf(longestTripTravelTimePerAgent.getOrDefault(person, 0.0)),
						String.valueOf(longestTripIndexValuePerAgent.getOrDefault(person, 0.0))
					});
				}
			}

			try (CSVWriter lossTimePerModeWriter = new CSVWriter(new FileWriter(String.valueOf(outputSummaryPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

				lossTimePerModeWriter.writeNext(new String[]{
					"mode",
					"cumulative_loss_time",
					"failed_routings"
				});

				for (String mode : lossTimePerMode.keySet()) {
					lossTimePerModeWriter.writeNext(new String[]{
						mode,
						String.valueOf(lossTimePerMode.getOrDefault(mode, 0.0)),
						String.valueOf(failedRoutingOccurances.getOrDefault(mode, 0L))
					});
				}
			}

			// generating output files for the green space dashboard page
			try (CSVWriter TQTileWriter = new CSVWriter(new FileWriter(TilesTravelTimePath.toFile()));
				 CSVWriter LongestTripWriter = new CSVWriter(new FileWriter(TilesLogestTripTimePath.toFile()));
				 CSVWriter LossTimeWriter = new CSVWriter(new FileWriter(TilesLossTimePath.toFile()))) {

				TQTileWriter.writeNext(new String[]{"Traffic Quality: 50% under limit", formattedTravelTime50PercentUnderIndexValue});
				TQTileWriter.writeNext(new String[]{"Traffic Quality within limit", formattedTravelTimeIndexValue});
				TQTileWriter.writeNext(new String[]{"Traffic Quality: 50% over limit", formattedTravelTime50PercentOverIndexValue});

				LongestTripWriter.writeNext(new String[]{"Longest Trip Index Value", formattedLongestTripIndexValue});
				LongestTripWriter.writeNext(new String[]{"Median Longest Car Trip Duration", formattedMedianLongestCarTrip});
				LongestTripWriter.writeNext(new String[]{"Median Longest PT Trip Duration", formattedMedianLongestPTTrip});
				LongestTripWriter.writeNext(new String[]{"Median Longest Ride Trip Duration", formattedMedianLongestRideTrip});

				LossTimeWriter.writeNext(new String[]{"Loss Time Index Value", formattedLossTimeIndexValue});
				LossTimeWriter.writeNext(new String[]{"Mean loss time (h:m:s)", formattedMeanTotalLossTime});
				LossTimeWriter.writeNext(new String[]{"Median loss time (h:m:s)", formattedMedianTotalLossTime});
				LossTimeWriter.writeNext(new String[]{"Sum loss time (h:m:s)", formattedSumTotalLossTime});

				System.out.println("LossTimeSum (HH:mm:ss): " + formattedSumTotalLossTime);
				System.out.println("LossTimeRanking: " + formattedLossTimeIndexValue);

			}


		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (NumberFormatException e) {
			throw new RuntimeException(e);
		}

		// end of call method
		return 0;
	}


	// Netzwerk laden
	private static Network loadNetwork(String networkFile) {
		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		return network;
	}

	// Calculate free speed travel time


	private static double calculateFreeSpeedTravelTime(Network network, Coord point1, Coord point2, String mode, long InputTravTimeInSeconds) {
		double travelTimeInSeconds = 0;
		Map<String, Double> beelineDistanceFactors = routingConfig.getBeelineDistanceFactors();// beelineFactors
		Map<String, Double> teleportedModeSpeeds = routingConfig.getTeleportedModeSpeeds(); // get average Speeds

		switch (mode.toLowerCase()) {
			case "bike":
			case "walk":
				double distance = CoordUtils.calcEuclideanDistance(point1, point2);
				// use of beeline factor
				distance *= beelineDistanceFactors.getOrDefault(mode, 1.3);

				travelTimeInSeconds = distance / teleportedModeSpeeds.getOrDefault(mode, 1.23);
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
						return Double.POSITIVE_INFINITY; // mode not allowed
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
		try (CSVParser parser = new CSVParser(Files.newBufferedReader(outputDirectory), CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
			for (CSVRecord record : parser) {
				String value = record.get("cumulative_loss_time");
				sum += (int) Double.parseDouble(value);
			}
		} catch (NumberFormatException e) {
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
		long minutes = duration.toMinutesPart();
		long seconds = duration.toSecondsPart();
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

}


//	private static void writeXYTDataToCSV(Path filePath, Map<String, Double> dataMap,
//										 Map<String, List<String>> coordinatesMap) throws IOException {
//		try (CSVWriter writer = new CSVWriter(new FileWriter(String.valueOf(filePath)),
//			CSVWriter.DEFAULT_SEPARATOR,
//			CSVWriter.NO_QUOTE_CHARACTER,
//			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
//			CSVWriter.DEFAULT_LINE_END)) {
//
//			// Schreiben der Kopfzeile
//			writer.writeNext(new String[]{"# EPSG:25832"});
//			writer.writeNext(new String[]{"time", "x", "y", "value"});
//
//			// Schreiben der Datenzeilen
//			for (Map.Entry<String, Double> entry : dataMap.entrySet()) {
//				String agentName = entry.getKey();
//				List<String> coordinates = coordinatesMap.get(agentName);
//
//				if (coordinates != null && coordinates.size() >= 2) {
//					writer.writeNext(new String[]{
//						String.valueOf(0.0),
//						coordinates.get(0), // X-Koordinate
//						coordinates.get(1), // Y-Koordinate
//						String.valueOf(entry.getValue()) // Wert aus der übergebenen Map
//					});
//				}
//			}
//		}
//	}




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
/*
package org.matsim.analysis;

import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.ApplicationUtils;
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
import java.time.Duration;
import java.util.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "lossTime-analysis",
	description = "Loss Time Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	requires = {
		"output_legs.csv.gz"
	},
	produces = {
		"output_legsLossTime_new.csv",
		"summary_modeSpecificLegsLossTime.csv",
		"lossTime_stats_perAgent.csv",
		"lossTime_RankingValue.csv",
		"XYTAgentBasedLossTimeMap.xyt.csv"
	}
)

public class AgentBasedTravelTimeAnalysis implements MATSimAppCommand {

	private final double limitRelativeLossTime = 0.15;
	//OFFEN: PRÜFEN WIE OFT DER DER WERT ÜBERSCHRITTEN WIRD UND DIE GESAMTLOSSTIME DABEI KLEINER ALS 2/3 MINUTEN IST - WENN DAS VIELE SIND
	//MUSS EIN ZWEITER FAKTOR (MINIMALE ABSOLUTE LOSS TIME) ZUR EXKLUSION ERGÄNZT WERDEN

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedTravelTimeAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedTravelTimeAnalysis.class);

	// constants for paths
	//private final Path inputLegsCsvFile = getValidOutputDirectory().resolve("berlin-v6.3.output_legs.csv.gz") ;
	private final Path inputLegsCsvFile = ApplicationUtils.matchInput("output_legs.csv.gz", getValidOutputDirectory());
	private final Path agentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());

	private final Path outputSummaryPath = getValidOutputDirectory().resolve("summary_modeSpecificLegsLossTime.csv");
	private final Path outputCSVPath = getValidOutputDirectory().resolve("output_legsLossTime_new.csv");
//	private final Path outputRankingAgentStatsPath = getValidOutputDirectory().resolve("analysis/analysis/lossTime_stats_perAgent.csv");
	private final Path outputRankingAgentStatsPath = getValidLiveabilityOutputDirectory().resolve("lossTime_stats_perAgent.csv");
	private final Path XYTLossTimeAgentMapPath = getValidLiveabilityOutputDirectory().resolve("XYTAgentBasedLossTimeMap.xyt.csv");

	private final Path outputRankingValuePath = getValidLiveabilityOutputDirectory().resolve("lossTime_RankingValue.csv");

	public static void main(String[] args) {
		new AgentBasedTravelTimeAnalysis().execute();
	}

	@Override
	public Integer call() throws Exception {

        //load network & execute NetworkCleaner
		Network network = NetworkUtils.readNetwork(String.valueOf(ApplicationUtils.matchInput("network.xml.gz", getValidOutputDirectory())));

//		String networkFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network-with-pt.xml.gz"; //Netzwerk-Dateipfad
//		Network network = loadNetwork(networkFile);

        NetworkCleaner cleaner = new NetworkCleaner();
        cleaner.run(network);



        //find input-File (legs.csv) and define Output-paths
        //	Path inputLegsCSVPath = Path.of(input.getPath("berlin-v6.3.output_legs.csv.gz"));

	//	Path inputLegsCsvFile = Path.of("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/output_legs.csv/berlin-v6.3.output_legs.csv");
////        String inputLegsCsvFile = ApplicationUtils.matchInput("legs.csv", input.getRunDirectory()).toAbsolutePath().toString();
//        Path outputSummaryPath = output.getPath("summary_modeSpecificLegsLossTime.csv");
//        Path outputCSVPath = output.getPath("output_legsLossTime_new.csv");
//        Path outputRankingAgentStatsPath = output.getPath("lossTime_stats_perAgent.csv");
//		Path outputRankingValuePath = output.getPath("lossTime_RankingValue.csv");

		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();

//		CSVReader csvReader = new CSVReader(new FileReader(inputLegsCsvFile));
//
//		List<String[]> rows = csvReader.readAll();
//		String[] headers = rows.get(0);
//
//		Map<String,Integer> columnIndexMap = new HashMap<>();
//		for (int i = 0; i < headers.length; i++){
//			columnIndexMap.put(headers[i],i);
//		}
//
//		for (int i = 1; i < rows.size(); i++){
//			String[] row = rows.get(i);
//			String waitTime = row[columnIndexMap.get("waitTime")];
//		}

		//defining maps for further csv-Writer tasks
		Map<String, List<String>> homeCoordinatesPerAgent = new HashMap<>();
		Map<String, Long> cumulativeLossTime = new HashMap<>();
		Map<String, Long> failedRoutingOccurances = new HashMap<>();
		Map<String, Double> sumTravTimePerAgent = new HashMap<>();
		Map<String, Double> sumLossTimePerAgent = new HashMap<>();
		Map<String, Double> percentageLossTimePerAgent = new HashMap<>();
		Map<String, Set<String>> modePerPerson = new HashMap<>();
		Map<String, Double> limitValueMap = new HashMap<>();
		Map<String, Double> overallLossTimeRankingValuePerAgent = new HashMap<>();
		Map<String, Boolean> boolLossTimeRankingValuePerAgent = new HashMap<>();
		Map<String, String> dimensionOverallRankingValue = new HashMap<>();

		//read Input-legs.csv file and write new output files for loss time analysis

		try (InputStream fileStream = new FileInputStream(inputLegsCsvFile.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
		//	 BufferedReader brInputLegs = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));
			 BufferedWriter bwLegsLossTime = new BufferedWriter(Files.newBufferedWriter(outputCSVPath))) {
			CSVParser legsParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			// CSVWriter reisezeitVergleichsWriter = new CSVWriter(new FileWriter(outputReisezeitvergleichPath.toFile()));
			//CSVWriter ptQualityStatsWriter = new CSVWriter(new FileWriter(outputRankingValuePath.toFile()))) {

			//	try (BufferedReader brInputLegs = new BufferedReader(new FileReader(String.valueOf(inputLegsCsvFile)));

			// writing new csv with added legs loss time information
//			 BufferedWriter bwLegsLossTime = new BufferedWriter(Files.newBufferedWriter(outputCSVPath))) {

			// read and skip header-line
			//	String line = brInputLegs.readLine();



			// defining column-headers (; as separator) for the new legsLossTime_csv
			bwLegsLossTime.write("person;trip_id;mode;trav_time;fs_trav_time;loss_time;percent_lossTime;trav_time_hms;fs_trav_time_hms;loss_time_hms;dep_time;start_x;start_y;start_node_found;start_link;end_x;end_y;end_node_found;end_link\n");

			//Berechnung der LossTime nur für agenten in Berlin
			Set<String> relevantPersons = new HashSet<>();
			try (CSVParser inputPersonParser = new CSVParser(new FileReader(String.valueOf(agentLiveabilityInfoPath)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(','))) {
				for (CSVRecord personRecord : inputPersonParser) {
					relevantPersons.add(personRecord.get("person"));

					String person = personRecord.get("person");
					String homeX = personRecord.get("home_x");
					String homeY = personRecord.get("home_y");

					List<String> homeCoordinates = new ArrayList<>();
					// Füge die X- und Y-Koordinaten separat hinzu
					homeCoordinates.add(homeX); // Index 0
					homeCoordinates.add(homeY); // Index 1
					// Schreibe die Liste in die Map
					homeCoordinatesPerAgent.put(person, homeCoordinates);

				}
			}

			int limit = 8000;
			int count = 0;

			for (CSVRecord legRecord : legsParser) {
				//			legsParser.stream().limit(500).forEach(leg -> {


				if (count >= limit) {
					break;
				}

				// collection of values from existing legs.csv to take over to the new legsLossTime.csv
				double startX = Double.parseDouble(legRecord.get("start_x"));
				double startY = Double.parseDouble(legRecord.get("start_y"));
				String startLink = legRecord.get("start_link");
				double endX = Double.parseDouble(legRecord.get("end_x"));
				double endY = Double.parseDouble(legRecord.get("end_y"));
				String endLink = legRecord.get("end_link");

				String person = legRecord.get("person");
				String tripId = legRecord.get("trip_id");
				String mode = legRecord.get("mode");
				String depTime = legRecord.get("dep_time");

				String travTimeString = legRecord.get("trav_time");

				if (relevantPersons.contains(person)) {

					// transforming travTime (hh:mm:ss) from the legs.csv into the string format (PTnHnMnS) and into the duration format (hh:mm:ss) afterwards [formattedTravTime as a result]
					if (travTimeString.matches("\\d{2}:\\d{2}:\\d{2}")) {
						String[] timeParts = travTimeString.split(":");
						travTimeString = "PT" + timeParts[0] + "H" + timeParts[1] + "M" + timeParts[2] + "S";
					} else {
						System.out.println("Ungültiges Zeitformat: " + travTimeString);
						//		continue;
					}
					Duration travTime = Duration.parse(travTimeString);
					long hours = travTime.toHours();
					long minutes = travTime.toMinutes() % 60;
					long seconds = travTime.getSeconds() % 60;
					String formattedTravTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

					// transform travel time into seconds
					long travTimeInSeconds = travTime.getSeconds();

					// prep data for free speed travel time calculation
					Coord startPoint = new Coord(startX, startY);
					Coord endPoint = new Coord(endX, endY);

					Node startNodeFound = NetworkUtils.getNearestNode(network, startPoint);
					Node endNodeFound = NetworkUtils.getNearestNode(network, endPoint);

					// free speed travel time is calculated by calling the calculateFreeSpeedTravelTime method for every start-end-point-combination
					long freeSpeedTravelTimeInSeconds = (long) calculateFreeSpeedTravelTime(network, startPoint, endPoint, mode, travTimeInSeconds);

					//transforming the resulting free speed travel time into the duration format as well es the String hh:mm:ss format
					Duration fsTravTimeHMS = Duration.ofSeconds(freeSpeedTravelTimeInSeconds);
					long hours_fs = fsTravTimeHMS.toHours();
					long minutes_fs = fsTravTimeHMS.toMinutes() % 60;
					long seconds_fs = fsTravTimeHMS.getSeconds() % 60;
					String formattedFreeSpeedTravTime = String.format("%02d:%02d:%02d", hours_fs, minutes_fs, seconds_fs);

					// defining loss time as the difference of travel time minus freeSpeedTravelTime (place holders are intercepted by the if-clause)
					// data type is Long as no double is required since seconds is the smallest unit used in matsim
					Long lossTimeInSeconds;
					if (freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
						lossTimeInSeconds = travTimeInSeconds - freeSpeedTravelTimeInSeconds;
						if (lossTimeInSeconds < 0) {
							lossTimeInSeconds = 0L;
						} // avoiding negative loss times
					} else {
						lossTimeInSeconds = 0L;
						failedRoutingOccurances.put(mode, failedRoutingOccurances.getOrDefault(mode, 0L) + 1);
					}

					// transforming the seconds of the loss time calculation to the duration format and the String (hh:mm:ss) afterwards
					Duration lostTimeHMS = Duration.ofSeconds(lossTimeInSeconds);
					long hours_lt = lostTimeHMS.toHours();
					long minutes_lt = lostTimeHMS.toMinutes() % 60;
					long seconds_lt = lostTimeHMS.getSeconds() % 60;
					String formattedLossTime = String.format("%02d:%02d:%02d", hours_lt, minutes_lt, seconds_lt);

					// calculating the percentage of loss time compared to the free speed (minimum possible) travel time for positive loss time values
					double percentLossTime = 0.0;
					if (freeSpeedTravelTimeInSeconds != 0 && freeSpeedTravelTimeInSeconds != -1 && freeSpeedTravelTimeInSeconds != -2) {
						percentLossTime = (double) lossTimeInSeconds / freeSpeedTravelTimeInSeconds;
					} else {
						System.out.println("Warnung: Division by Zero for trip " + tripId + " avoided.");
					}

					// calculate sum of travel time and of loss time per agent. overall loss time sum and mode per person info
					sumLossTimePerAgent.put(person, sumLossTimePerAgent.getOrDefault(person, 0.0) + lossTimeInSeconds);
					sumTravTimePerAgent.put(person, sumTravTimePerAgent.getOrDefault((Object) person, 0.0) + travTimeInSeconds);
					modePerPerson.computeIfAbsent(person, k -> new HashSet<>()).add(mode);
					percentageLossTimePerAgent.put(person, percentLossTime);

					limitValueMap.put(person, limitRelativeLossTime);

					double lossTimeRankingValuePerAgent = (percentageLossTimePerAgent.get(person)-limitValueMap.get(person))/limitValueMap.get(person);

					overallLossTimeRankingValuePerAgent.put(person, lossTimeRankingValuePerAgent);

					if (lossTimeInSeconds != 0L) {
						cumulativeLossTime.put(mode, cumulativeLossTime.getOrDefault(mode, 0L) + lossTimeInSeconds);
					} else {
						System.out.println("Warnung: Loss Time for trip" + tripId + " is zero.");
					}

					// writing the desired columns in the new legsLossTime output csv file
					try {
						bwLegsLossTime.write(String.format("%s;%s;%s;%s;%d;%d;%f;%s;%s;%s;%s;%f;%f;%s;%s;%f;%f;%s;%s\n",
							person, tripId, mode, travTimeInSeconds, freeSpeedTravelTimeInSeconds, lossTimeInSeconds,
							percentLossTime, formattedTravTime, formattedFreeSpeedTravTime, formattedLossTime, depTime,
							startX, startY, startNodeFound.getId(), startLink, endX, endY, endNodeFound.getId(), endLink));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}

					//System.out.println("Neue LossTime für Person "+person);
					count++;
				}
		//		});
			}

					//HIER WEITER MIT CODE-ÜBERARBEITUNG UND KOMMENTIERUNG


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
					agentBasedBw.write("Person;lossTimePerAgent;travTimePerAgent;percentageLossTime;rankingStatus;rankingValue;modesUsed\n");


					for (String person : sumLossTimePerAgent.keySet()) {
						double lossTimePerAgent = sumLossTimePerAgent.getOrDefault((Object) person, 0.0);
						double travTimePerAgent = sumTravTimePerAgent.getOrDefault((Object) person, 0.0);
						double percentageLossTime = lossTimePerAgent / travTimePerAgent;
						//	String modeUsed = modePerPerson.getOrDefault((Object) person, "");
						String modeUsed = String.join(",", modePerPerson.getOrDefault(person, Set.of()));
						boolean rankingStatus = false;
						if (percentageLossTime < limitRelativeLossTime) {
							rankingStatus = true;
						}
						agentBasedBw.write(String.format("%s;%f;%f;%f;%s;%f;%s \n", person, lossTimePerAgent, travTimePerAgent, percentageLossTime, rankingStatus, overallLossTimeRankingValuePerAgent.get(person) ,modeUsed));
						boolLossTimeRankingValuePerAgent.put(person, rankingStatus);
					}

					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(sumLossTimePerAgent, "Loss Time");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(sumTravTimePerAgent, "Travel Time");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(percentageLossTimePerAgent, "percentageLossTime");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitValueMap, "limit_relativeLossTime");
					agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(overallLossTimeRankingValuePerAgent, "rankingValue_relativeLossTime");

				}

				try {

					// Summiere die Spalte loss_time aus der Datei output_legsLossTime_new.csv
					long totalLossTimeInSeconds = 0;
					try (Reader outputLegsReader = Files.newBufferedReader(outputCSVPath);
						 CSVParser csvParser = new CSVParser(outputLegsReader, CSVFormat.DEFAULT.withDelimiter(';').withFirstRecordAsHeader())) {
						for (CSVRecord record : csvParser) {
							String lossTimeValue = record.get("loss_time");
							if (!lossTimeValue.isEmpty()) {
								totalLossTimeInSeconds += Long.parseLong(lossTimeValue);
							}
						}
					}

					// oder Methode unten: Format Duration verwenden
					Duration totalLossTime = Duration.ofSeconds(totalLossTimeInSeconds);
					String formattedTotalLossTime = String.format("%02d:%02d:%02d", totalLossTime.toHours(), totalLossTime.toMinutes() % 60, totalLossTime.getSeconds() % 60);

//					// behalten falls es nicht funktioniert
//					// Konvertiere die Gesamtsumme in das Format HH:mm:ss
//					Duration totalLossTime = Duration.ofSeconds(totalLossTimeInSeconds);
//					long hours = totalLossTime.toHours();
//					long minutes = totalLossTime.toMinutes() % 60;
//					long seconds = totalLossTime.getSeconds() % 60;
//					String formattedTotalLossTime = String.format("%02d:%02d:%02d", hours, minutes, seconds);

					//modifiziert mit Boolean
					double counterLossTimeOverall = 0;
					// distance to GS Ranking Value berechnen
					for (Map.Entry<String, Boolean> entry : boolLossTimeRankingValuePerAgent.entrySet()) {
						String agentId = entry.getKey();

						boolean overallLossTimeRankingValue = entry.getValue();

						if (overallLossTimeRankingValue == true) {
							counterLossTimeOverall++;
						}
					}

//					//Sicherung wie es sein soll
//					double counterLossTimeOverall = 0;
//					// distance to GS Ranking Value berechnen
//					for (Map.Entry<String, Double> entry : overallLossTimeRankingValuePerAgent.entrySet()) {
//						String agentId = entry.getKey();
//
//						double overallLossTimeRankingValue = entry.getValue();
//
//						if (overallLossTimeRankingValue <= 0) {
//							counterLossTimeOverall++;
//						}
//					}

					// calculate overall ranking value
					double lossTimeRankingValue = counterLossTimeOverall / overallLossTimeRankingValuePerAgent.size();
					String formattedRankingValueLossTime = String.format(Locale.US, "%.2f%%", lossTimeRankingValue * 100);
					dimensionOverallRankingValue.put("Loss Time", formattedRankingValueLossTime);


					// output dateien erzeugen für GS Dashboard-Seite
					try (CSVWriter LTTileWriter = new CSVWriter(new FileWriter(outputRankingValuePath.toFile()));
						 CSVWriter XYTAgentMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTLossTimeAgentMapPath)),
							 CSVWriter.DEFAULT_SEPARATOR,
							 CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
							 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
							 CSVWriter.DEFAULT_LINE_END)) {

						LTTileWriter.writeNext(new String[]{"LossTimeRanking", formattedRankingValueLossTime});
						LTTileWriter.writeNext(new String[]{"LossTimeSum(h:m:s)", formattedTotalLossTime});

						System.out.println("LossTimeSum (HH:mm:ss): " + formattedTotalLossTime);
						System.out.println("LossTimeRanking: " + formattedRankingValueLossTime);

						//muss man das so schreiben? Prüfen im Output ob die zeilen darüber auch funktionieren, sonst anpassen
//						writer.write(String.format("LossTimeRanking;%s\n", formattedRankingLossTime)); // Ranking
//						writer.write(String.format("LossTimeSum(h:m:s);%s\n", formattedTotalLossTime)); // Summe der Verlustzeit

						//median und mean folgen...
						//	LTTileWriter.writeNext(new String[]{"MedianLossTime(h:m:s)", formattedMedianDistance});
						//LTTileWriter.writeNext(new String[]{"AverageLossTime(h:m:s)", formattedAvgUtilization});

						XYTAgentMapWriter.writeNext(new String[]{"# EPSG:25832"});
						XYTAgentMapWriter.writeNext(new String[]{"time", "x", "y", "value"});

						for (Map.Entry<String, Double> entry : overallLossTimeRankingValuePerAgent.entrySet()) {
							String agentName = entry.getKey();
							XYTAgentMapWriter.writeNext(new String[]{String.valueOf(0.0), homeCoordinatesPerAgent.get(agentName).get(0), homeCoordinatesPerAgent.get(agentName).get(1), String.valueOf(overallLossTimeRankingValuePerAgent.get(agentName))});
						}

//						agentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(formattedRankingLossTime, "LossTime", "https://github.com/simwrapper/simwrapper/blob/master/public/images/tile-icons/route.svg");
						agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedRankingValueLossTime, "LossTime");

						agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Loss Time", "relative Loss Time", String.valueOf(totalLossTime), "15 %", String.valueOf(dimensionOverallRankingValue.get("Loss Time")), 1);

						return 0;
					}

				} catch (IOException e) {
					throw new RuntimeException(e);
				} catch (NumberFormatException e) {
					throw new RuntimeException(e);
				}

			}
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
