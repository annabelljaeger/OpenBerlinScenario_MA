package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileReader;
import org.matsim.core.utils.gis.GeoFileWriter;
import org.matsim.core.utils.gis.PointFeatureFactory;
import org.matsim.core.utils.gis.PolygonFeatureFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "greenSpace-analysis",
	description = "Green Space availability and accessibility Analysis. Required Input: persons.csv.gz, accessPoints (to be generated e.g. via QGIS - see ReadMe-File)",
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
		"output_persons.csv.gz",
	//	"accessPoints.shp",
	//	"allGreenSpaces_min1ha.shp"
	},
	produces = {
		"greenSpace_stats_perAgent.csv",
		"greenSpace_utilization.csv",
		"greenSpace_TilesOverall.csv",
		"greenSpace_TilesDistance.csv",
		"greenSpace_TilesUtilization.csv",
		"XYTAgentBasedGreenSpaceMap.xyt.csv",
		"XYTGreenSpaceUtilizationMap.xyt.csv",
		"greenSpace_perAgentGeofile.gpkg"
	}
)

// This Class delivers all calculations and outputs for Green Space analyses.
// 1. all declarations, initializations of variables and maps with relevant agent info
// 2. Identifying the closes green space per agent home location
// 3. Calculating utilization per green space based on number of agents having each green space as their nearest green space
// 4. Transferring utilization per green space to each agent according to closest green space id
// 5. writing all output files per agent, per green space and overall and handing information over to superior collection files
// for the overview dashboard and agentbased liveability info
public class AgentBasedGreenSpaceAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);

	//overwritten with config value at beginning of call method
	double sampleSize = 0.1;

	// defining the limits for the indicators
	private final double limitEuclideanDistanceToGreenSpace = 500;
	private final double limitGreenSpaceUtilization = (double) 1/6; // ATTENTION: THIS IS FOR THE 100% REALITY CASE - FOR SMALLER SAMPLES (E.G. 10pct), THIS HAS TO BE ADAPTED ACCORDINGLY
	// for calculating and unifying reasons, since larger Values than 6m²/EW would be positive, the return value is used. Accordingly, the sample size is multiplied (not divided).
	private final double limitGreenSpaceUtilizationSampleSizeAdjusted = limitGreenSpaceUtilization * sampleSize;

	// constants for paths
	// input paths
	private final Path inputPersonsCSVPath = ApplicationUtils.matchInput("output_persons.csv.gz", getValidOutputDirectory());
	//accessPoint shp Layer has to include the osm_id of the corresponding green space (column name "osm_id") as well as the area of the green space (column name "area")
	//private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("test_accessPoints.shp", getValidOutputDirectory());
	//private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("test_accessPoints.shp", getValidInputDirectory());
	//private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("zusammengefügteFiles_NEU_accessPoints.shp", getValidInputDirectory());
	private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("accessPoints.shp", getValidInputDirectory());
	//private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("relevante_accessPoints.shp", getValidInputDirectory());
	private final Path inputGreenSpaceShpPath = ApplicationUtils.matchInput("allGreenSpaces_min1ha.shp", getValidInputDirectory());
	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());

	// output paths
	private final Path XYTAgentBasedGreenSpaceMapPath = getValidLiveabilityOutputDirectory().resolve("XYTAgentBasedGreenSpaceMap.xyt.csv");
	private final Path outputRankingValueCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_TilesOverall.csv");
	private final Path outputUtilizationTilesCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_TilesUtilization.csv");
	private final Path outputDistanceTilesCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_TilesDistance.csv");
	private final Path outputGreenSpaceUtilizationPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_utilization.csv");
	private final Path XYTGreenSpaceUtilizationMapPath = getValidLiveabilityOutputDirectory().resolve("XYTGreenSpaceUtilizationMap.xyt.csv");
	private final Path outputPersonsCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_stats_perAgent.csv");
	private final Path outputGreenSpaceSHP = getValidLiveabilityOutputDirectory().resolve("greenSpaces_withUtilization.shp");
	private final Path outputAgentGreenSpaceGeofile = getValidLiveabilityOutputDirectory().resolve("greenSpace_perAgentGeofile.shp");
	private final Path outputGreenSpaceGeofile = getValidLiveabilityOutputDirectory().resolve("greenSpace_statsGeofile.shp");
	private final Path outputGeofileAgentGS = getValidLiveabilityOutputDirectory().resolve("greenSpaceAndAgent_geofile.shp");

	public static void main(String[] args) {
		new AgentBasedGreenSpaceAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		//loads sample size from config
		Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("config.xml", input.getRunDirectory()).toAbsolutePath().toString());
		SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		this.sampleSize = simwrapper.sampleSize;

		// initialising collections and data structures
		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();
		Collection<SimpleFeature> accessPointFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource(String.valueOf(inputAccessPointShpPath)));

		// defining all maps to be able to put and get values of those throughout the analysis
		Map<String, List<String>> homeCoordinatesPerAgent = new HashMap<>();
		Map<String, List<String>> homeCoordinatesPerAgentInStudyArea = new HashMap<>();
		Map<String, List<Double>> greenSpaceUtilization = new HashMap<>();
		Map<String, Double> distancePerAgent = new HashMap<>();
		Map<String, String> greenSpaceIdPerAgent = new HashMap<>();
		Map<String, Double> utilizationPerGreenSpace = new HashMap<>();
		Map<String, Double> utilizationPerAgent = new HashMap<>();
		Map<String, Double> areaPerGreenSpace = new HashMap<>();
		Map<String, Integer> nrOfPeoplePerGreenSpace = new HashMap<>();
		Map<String, Double> meanDistancePerGreenSpace = new HashMap<>();
		Map<String, Double> limitDistanceToGreenSpace = new HashMap<>();
		Map<String, Double> limitUtilizationOfGreenSpace = new HashMap<>();
		Map<String, Double> greenSpaceUtilizationDeviationValuePerAgent = new HashMap<>();
		Map<String, Double> distanceToGreenSpaceDeviationValuePerAgent = new HashMap<>();
		Map<String, Double> greenSpaceOverallRankingValuePerAgent = new HashMap<>();
		Map<String, String> dimensionOverallRankingValue = new HashMap<>();

		// initializing counters for index value calculations
		double counterOverall = 0;
		double counterDistance = 0;
		double counterUtilization = 0;
		double counter50PercentUnderLimit = 0;
		double counter50PercentOverLimit = 0;
		double sumDistance = 0;
		double sumUtilization = 0;

		// declaring other variables for later use
		double greenSpaceRankingValue;
		String formattedRankingGreenSpace;
		double greenSpace50PercentUnderLimitIndexValue;
		String formatted50PercentUnderLimitIndexGreenSpace;
		double greenSpace50PercentOVerLimitIndexValue;
		String formatted50PercentOverLimitIndexGreenSpace;
		double greenSpaceDistanceRankingValue;
		String formattedDistanceRankingGreenSpace;
		double greenSpaceUtilizationRankingValue;
		String formattedRankingUtilizationGreenSpace;
		double avgDistance;
		String formattedAvgDistance;
		double avgUtilization;
		String formattedAvgUtilization;
		double medianDistance;
		String formattedMedianDistance;
		double medianUtilization;
		String formattedMedianUtilization;


		//prepping a map for all study area agents to be used for writing files and calculating index values
		try (Reader studyAreaAgentReader = new FileReader(inputAgentLiveabilityInfoPath.toFile());
			 CSVParser studyAreaAgentParser = new CSVParser(studyAreaAgentReader, CSVFormat.DEFAULT.withFirstRecordAsHeader())){

			for (CSVRecord record : studyAreaAgentParser) {
				String id = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");
				homeCoordinatesPerAgentInStudyArea.put(id, Arrays.asList(homeX, homeY));
			}
		}

		// collecting data and calculating values for all simulated agents of the scenario
		try (InputStream fileStream = new FileInputStream(inputPersonsCSVPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader personsReader = new InputStreamReader(gzipStream);
			 CSVParser personsParser = new CSVParser(personsReader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter agentCSVWriter = new CSVWriter(new FileWriter(String.valueOf(outputPersonsCSVPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END);
			 CSVWriter greenSpaceUtilizationWriter = new CSVWriter(new FileWriter(String.valueOf(outputGreenSpaceUtilizationPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			// processing access point features
			for (SimpleFeature simpleFeature : accessPointFeatures) {
				String osmId = (String) simpleFeature.getAttribute("osm_id");
				if (!greenSpaceUtilization.containsKey(osmId)) {
					greenSpaceUtilization.putIfAbsent(osmId, Arrays.asList(0.0, 0.0));
				}
				Double area = (Double) simpleFeature.getAttribute("area");
				if (!areaPerGreenSpace.containsKey(osmId)) {
					areaPerGreenSpace.putIfAbsent(osmId, area);
				}
				if (!nrOfPeoplePerGreenSpace.containsKey(osmId)) {
					nrOfPeoplePerGreenSpace.putIfAbsent(osmId, 0);
				}
				if (!utilizationPerGreenSpace.containsKey(osmId)) {
					utilizationPerGreenSpace.putIfAbsent(osmId, 0.0);
				}
			}

			for (CSVRecord record : personsParser) {
				String id = record.get("person");
				String homeX = record.get("home_x");
				String homeY = record.get("home_y");

				// excluding all agents without valid home coordinates (keeping all other agents for calculations to reduce edge effects)
				if (homeX != null && !homeX.isEmpty() && homeY != null && !homeY.isEmpty()) {
					homeCoordinatesPerAgent.put(id, Arrays.asList(homeX, homeY));
					processPerson(id, homeX, homeY, accessPointFeatures, greenSpaceIdPerAgent, distancePerAgent, nrOfPeoplePerGreenSpace, greenSpaceUtilization);
				}
			}

			// calculate utilization per Green Space
			calculateGreenSpaceUtilization(areaPerGreenSpace, nrOfPeoplePerGreenSpace, utilizationPerGreenSpace);

			for(Map.Entry<String, String> entry : greenSpaceIdPerAgent.entrySet()) {
				String agentID = entry.getKey();
				String nearestGreenSpaceId = greenSpaceIdPerAgent.get(agentID);
				utilizationPerAgent.put(agentID, utilizationPerGreenSpace.get(nearestGreenSpaceId));
			}

			// calculating deviation per Agent for distance and utilization
			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgent.entrySet()) {
				String agentId = entry.getKey();

				double distanceDeviation = (distancePerAgent.get(agentId) - limitEuclideanDistanceToGreenSpace) / limitEuclideanDistanceToGreenSpace;
				distanceToGreenSpaceDeviationValuePerAgent.put(agentId, distanceDeviation);

				double utilizationDeviation = (utilizationPerAgent.get(agentId) - limitGreenSpaceUtilization) / limitGreenSpaceUtilization;
				greenSpaceUtilizationDeviationValuePerAgent.put(agentId, utilizationDeviation);

				double overallGreenSpaceDeviationValue = Math.max(distanceDeviation, utilizationDeviation);
				greenSpaceOverallRankingValuePerAgent.put(agentId, overallGreenSpaceDeviationValue);

				limitDistanceToGreenSpace.put(agentId, limitEuclideanDistanceToGreenSpace);
				limitUtilizationOfGreenSpace.put(agentId, (1/limitGreenSpaceUtilization)); // for better understanding the return value of the limit is used for the dashboard display
			}

			// calculating percentage of agents with deviation under 0 (index value per indicator) and for further analysis also for deviation -0.5 and +0.5
			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentId = entry.getKey();

				double distanceDeviation = distanceToGreenSpaceDeviationValuePerAgent.get(agentId);
				double utilizationDeviation = greenSpaceUtilizationDeviationValuePerAgent.get(agentId);
				double overallGreenSpaceRankingValue = greenSpaceOverallRankingValuePerAgent.get(agentId);

				if (distanceDeviation <= 0) {counterDistance++;}
				if (utilizationDeviation <= 0) {counterUtilization++;}
				if (overallGreenSpaceRankingValue <= 0) {counterOverall++;}
				if(overallGreenSpaceRankingValue <= -0.5){counter50PercentUnderLimit++;}
				if(overallGreenSpaceRankingValue <= 0.5) {counter50PercentOverLimit++;}

				sumDistance += distancePerAgent.get(agentId);
				sumUtilization += utilizationPerAgent.get(agentId);
			}

			// calculating overall index value and indicator index values
			greenSpaceRankingValue = counterOverall / homeCoordinatesPerAgentInStudyArea.size();
			formattedRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceRankingValue * 100);
			//dimensionOverallRankingValue.put("Green Space", formattedRankingGreenSpace);

			greenSpace50PercentUnderLimitIndexValue = counter50PercentUnderLimit / homeCoordinatesPerAgentInStudyArea.size();
			formatted50PercentUnderLimitIndexGreenSpace = String.format(Locale.US, "%.2f%%", greenSpace50PercentUnderLimitIndexValue * 100);

			greenSpace50PercentOVerLimitIndexValue = counter50PercentOverLimit / homeCoordinatesPerAgentInStudyArea.size();
			formatted50PercentOverLimitIndexGreenSpace = String.format(Locale.US, "%.2f%%", greenSpace50PercentOVerLimitIndexValue * 100);

			greenSpaceDistanceRankingValue = counterDistance / homeCoordinatesPerAgentInStudyArea.size();
			formattedDistanceRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceDistanceRankingValue * 100);

			greenSpaceUtilizationRankingValue = counterUtilization / homeCoordinatesPerAgentInStudyArea.size();
			formattedRankingUtilizationGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceUtilizationRankingValue * 100);

			avgDistance = sumDistance / homeCoordinatesPerAgentInStudyArea.size();
			formattedAvgDistance = String.format(Locale.US, "%.2f", avgDistance);

			// the values are turned here to generate the formatted output in the readable format of m²/person instead of person/m²
			avgUtilization = homeCoordinatesPerAgentInStudyArea.size() / sumUtilization;		// --> RICHTIG SO?? AVERAGE JETZT KLEINER ALS MEDIAN UND BEIDES SEHR GROß..
			formattedAvgUtilization = String.format(Locale.US, "%.2f", avgUtilization);

			medianDistance = calculateMedian(homeCoordinatesPerAgentInStudyArea, distancePerAgent);
			// the values are turned here to generate the formatted output in the readable format of m²/person instead of person/m²
			medianUtilization = calculateMedian(homeCoordinatesPerAgentInStudyArea, utilizationPerAgent);

			formattedMedianDistance = String.format(Locale.US, "%.2f", medianDistance);
			formattedMedianUtilization = String.format(Locale.US, "%.2f", (1.0/medianUtilization));

			// handing results over to the superior SummaryDashboard files
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(distancePerAgent, "MinGreenSpaceEuclideanDistance");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitDistanceToGreenSpace, "limit_EuclideanDistanceToNearestGreenSpace");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(distanceToGreenSpaceDeviationValuePerAgent, "rankingValue_DistanceToGreenSpace");

			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(utilizationPerAgent, "GreenSpaceUtilization (m²/person)");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitUtilizationOfGreenSpace, "limit_SpacePerAgentAtNearestGreenSpace");
			agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(greenSpaceUtilizationDeviationValuePerAgent, "rankingValue_GreenSpaceUtilization");

			agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedRankingGreenSpace, "GreenSpace");

			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Green Space", "Distance to nearest green space", formattedMedianDistance, String.valueOf(limitEuclideanDistanceToGreenSpace), formattedDistanceRankingGreenSpace, 1);
			agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Green Space", "Utilization of Green Space", formattedMedianUtilization, String.valueOf(1/limitGreenSpaceUtilization), formattedRankingUtilizationGreenSpace, 1);

			// writing csv-headers for agent- and green space-based information-output files
			agentCSVWriter.writeNext(new String[]{"AgentID", "home_x", "home_y", "ClosestGreenSpace", "DistanceToGreenSpace", "UtilizationOfGreenSpace [m²/person]", "GSDistanceDeviationFromLimit", "GSUtilizationDeviationFromLimit"});
			greenSpaceUtilizationWriter.writeNext(new String[]{"osm_id", "nrOfPeople", "meanDistance", "utilization [m²/person]", "area", "areaCategory"});

			// writing results in the csv files
			for (Map.Entry<String, List<Double>> entry : greenSpaceUtilization.entrySet()) {
				String id = entry.getKey();
				List<Double> values = entry.getValue();
				Integer count = nrOfPeoplePerGreenSpace.get(id);
				String meanDistance = String.valueOf(values.get(1));
				String utilization = utilizationPerGreenSpace.get(id).toString();
				Double area = areaPerGreenSpace.get(id);
				String areaCategory;
				    //if (area < 10000) {areaCategory = "< 1 ha";}
					if (area < 20000) {areaCategory = "01 - 02 ha";}
					else if (area < 50000) {areaCategory = "02 - 05 ha";}
					else if (area < 100000) {areaCategory = "05 - 10 ha";}
					else if (area < 200000) {areaCategory = "10 - 20 ha";}
					else {areaCategory = "> 20 ha";}
//				if (area < 10000) {areaCategory = "a: < 1 ha";}
//				else if (area < 20000) {areaCategory = "b: 1 - 2 ha";}
//				else if (area < 50000) {areaCategory = "c: 2 - 5 ha";}
//				else if (area < 100000) {areaCategory = "d: 5 - 10 ha";}
//				else if (area < 200000) {areaCategory = "e: 10 - 20 ha";}
//				else {areaCategory = "f: > 20 ha";}
				greenSpaceUtilizationWriter.writeNext(new String[]{id, String.valueOf(count), meanDistance, utilization, String.valueOf(area), areaCategory});
			}

			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentId = entry.getKey();
				agentCSVWriter.writeNext(new String[]{
					agentId,
					String.valueOf(homeCoordinatesPerAgent.get(agentId).get(0)),
					String.valueOf(homeCoordinatesPerAgent.get(agentId).get(1)),
					greenSpaceIdPerAgent.get(agentId),
					String.valueOf(distancePerAgent.get(agentId)),
					String.valueOf(utilizationPerGreenSpace.get(greenSpaceIdPerAgent.get(agentId))),
					String.valueOf(distanceToGreenSpaceDeviationValuePerAgent.get(agentId)),
					String.valueOf(greenSpaceUtilizationDeviationValuePerAgent.get(agentId))
				});
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// generating output files for the green space dashboard page
		try (CSVWriter GSTileWriter = new CSVWriter(new FileWriter(outputRankingValueCSVPath.toFile()));
			 CSVWriter DistanceTileWriter = new CSVWriter(new FileWriter(outputDistanceTilesCSVPath.toFile()));
			 CSVWriter UtilizationTileWriter = new CSVWriter(new FileWriter(outputUtilizationTilesCSVPath.toFile()));
			 CSVWriter GSxytAgentMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTAgentBasedGreenSpaceMapPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotations
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END);
			 CSVWriter XYTMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTGreenSpaceUtilizationMapPath)),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotations
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			GSTileWriter.writeNext(new String[]{"GreenSpace50%underLimit", formatted50PercentUnderLimitIndexGreenSpace});
			GSTileWriter.writeNext(new String[]{"GreenSpaceWithinLimit", formattedRankingGreenSpace});
			GSTileWriter.writeNext(new String[]{"GreenSpace50%overLimit", formatted50PercentOverLimitIndexGreenSpace});

			UtilizationTileWriter.writeNext(new String[]{"UtilizationWithinLimit", formattedRankingUtilizationGreenSpace});
			UtilizationTileWriter.writeNext(new String[]{"AverageUtilization(m²/person)", formattedAvgUtilization});
			UtilizationTileWriter.writeNext(new String[]{"MedianUtilization(m²/person)", formattedMedianUtilization});

			DistanceTileWriter.writeNext(new String[]{"DistanceWithinLimit", formattedDistanceRankingGreenSpace});
			DistanceTileWriter.writeNext(new String[]{"AverageDistance(m)", formattedAvgDistance});
			DistanceTileWriter.writeNext(new String[]{"MedianDistance(m)", formattedMedianDistance});

			GSxytAgentMapWriter.writeNext(new String[]{"# EPSG:25832"});
			GSxytAgentMapWriter.writeNext(new String[]{"time", "x", "y", "value"});

			for (Map.Entry<String, List<String>> entry : homeCoordinatesPerAgentInStudyArea.entrySet()) {
				String agentName = entry.getKey();
				GSxytAgentMapWriter.writeNext(new String[]{String.valueOf(0.0),
					homeCoordinatesPerAgentInStudyArea.get(agentName).get(0),
					homeCoordinatesPerAgentInStudyArea.get(agentName).get(1),
					String.valueOf(greenSpaceOverallRankingValuePerAgent.get(entry.getKey()))});
			}
		}

//		//creating two shapefiles for the dashboard - one for the agents with its indicator deviation values and one for the green spaces
//		// creating the builder for the Point Features (Agents) in the Geofile
//		PointFeatureFactory pointFactoryBuilder = new PointFeatureFactory.Builder()
//			.setName("AgentFeatures")
//			.setCrs(CRS.decode(config.global().getCoordinateSystem()))
//			.addAttribute("nearestGreenSpaceID", String.class)
//			.addAttribute("greenSpaceUtilizationDeviation", Double.class)
//			.addAttribute("distanceToGreenSpaceDeviationValue", Double.class)
//			.addAttribute("greenSpaceOverallIndexValue", Double.class)
//			.create();
//
//		Collection<SimpleFeature> featureCollection = new ArrayList<SimpleFeature>();
//
//		// creating a SimpleFeature with attributes for every agent and adding them to the collection
//		for (String agentId : homeCoordinatesPerAgentInStudyArea.keySet()) {
//			List<String> coordinates = homeCoordinatesPerAgentInStudyArea.get(agentId);
//			double x = (coordinates.get(0).isEmpty() || coordinates.get(0).equals("0")) ? 0.0 : Double.parseDouble(coordinates.get(0));
//			double y = (coordinates.get(1).isEmpty() || coordinates.get(1).equals("0")) ? 0.0 : Double.parseDouble(coordinates.get(1));
//			Coord coord = new Coord(x, y);
//			Coordinate coordinate = new Coordinate(coord.getX(), coord.getY());
//			if (coordinates == null || coordinates.size() < 2 || coordinates.get(0).isEmpty() || coordinates.get(1).isEmpty()) {
//				continue; // skip false agents
//			}
//
//			Object[] attributes = new Object[]{
//				greenSpaceIdPerAgent.getOrDefault(agentId, null),
//				greenSpaceUtilizationDeviationValuePerAgent.getOrDefault(agentId, 0.0),
//				distanceToGreenSpaceDeviationValuePerAgent.getOrDefault(agentId, 0.0),
//				greenSpaceOverallRankingValuePerAgent.getOrDefault(agentId,0.0)
//			};
//
//			SimpleFeature feature = pointFactoryBuilder.createPoint(coordinate, attributes, null);
//			if (feature == null) {
//				System.err.println("Feature konnte nicht erstellt werden für Agent: " + agentId);
//			}
//			featureCollection.add(feature);
//			System.out.println("Anzahl Features: " + featureCollection.size() + "Einträge: " + Arrays.toString(attributes));
//
//		}
//
//		// Beispiel PolygonFeatureFactory erstellen
//		PolygonFeatureFactory polygonFeatureFactory = new PolygonFeatureFactory.Builder()
//			.setName("GreenSpaceFeatures")
//			.setCrs(CRS.decode(config.global().getCoordinateSystem()))  // CRS aus Konfiguration
//			.addAttribute("greenSpaceID", String.class)
//			.addAttribute("greenSpaceUtilization", Double.class)
//			.addAttribute("area", Double.class)
//			.addAttribute("nrOfPeople", Integer.class)
//			.create();
//
//
//		// Angenommene bereits existierende GreenSpace-Collection
//		Collection<SimpleFeature> existingGreenSpaceCollection = GeoFileReader.getAllFeatures(String.valueOf(inputGreenSpaceShpPath));
//		// NewCollection
//		Collection<SimpleFeature> polygonCollection = new ArrayList<SimpleFeature>();
//
//
//		// Iteration über alle bestehenden Features, Geometrie und Attribute setzen
//		for (SimpleFeature existingFeature : existingGreenSpaceCollection) {
//			// Geometrie und existierende Attribute (greenSpaceID, area) übernehmen
//			Polygon geometry = (Polygon) existingFeature.getDefaultGeometry();
//			String greenSpaceID = (String) existingFeature.getAttribute("greenSpaceID");
//			Double area = (Double) existingFeature.getAttribute("area");
//
//			// Werte aus Map1 und Map2 anhand der greenSpaceID holen
//			Object[] attributes = new Object[]{
//				greenSpaceID,
//				area,
//				utilizationPerGreenSpace.getOrDefault(greenSpaceID, 0.0),
//				nrOfPeoplePerGreenSpace.getOrDefault(greenSpaceID,0)
//			};
//
//			// Das neue Feature in die Sammlung einfügen
//			SimpleFeature feature = polygonFeatureFactory.createPolygon(geometry, new Object[]{}, null);
//			if (feature == null) {
//				System.err.println("Feature konnte nicht erstellt werden für Agent: " + greenSpaceID);
//			}
//			polygonCollection.add(feature);
//		}
//
//		GeoFileWriter.writeGeometries(polygonCollection, String.valueOf(outputGreenSpaceGeofile));
//
//		Collection<SimpleFeature> mergedCollection = new ArrayList<>();
//		mergedCollection.addAll(polygonCollection);
//		mergedCollection.addAll(featureCollection);
//
//		GeoFileWriter.writeGeometries(mergedCollection, String.valueOf(outputGeofileAgentGS));
//
//		PolygonFeatureFactory polygonFeatureFactory = new PolygonFeatureFactory.Builder()
//			.setName("GreenSpaceFeatures")
//			.setCrs(CRS.decode(config.global().getCoordinateSystem()))
//			.addAttribute("greenSpaceID", String.class)
//			.addAttribute("greenSpaceUtilization", Double.class)
//			.addAttribute("area", Double.class)
//			.addAttribute("nrOfPeople", Integer.class)
//			.create();
//
//		// Erstelle eine Sammlung für die Features
//		Collection<SimpleFeature> polygonCollection = new ArrayList<SimpleFeature>();
//
//		Collection<SimpleFeature> existingGreenSpaceCollection = GeoFileReader.getAllFeatures(greenSpaceShpInputPath);
//
//
//		// Erstelle SimpleFeature für jeden Agenten und füge es zur Sammlung hinzu
//		for (String greenSpaceId : greenSpaceUtilization.keySet()) {
//
//			List<String> coordinates = homeCoordinatesPerAgentInStudyArea.get(agentId);
//			double x = (coordinates.get(0).isEmpty() || coordinates.get(0).equals("0")) ? 0.0 : Double.parseDouble(coordinates.get(0));
//			double y = (coordinates.get(1).isEmpty() || coordinates.get(1).equals("0")) ? 0.0 : Double.parseDouble(coordinates.get(1));
//			Coord coord = new Coord(x, y);
//			Coordinate coordinate = new Coordinate(coord.getX(), coord.getY());
//			if (coordinates == null || coordinates.size() < 2 || coordinates.get(0).isEmpty() || coordinates.get(1).isEmpty()) {
//				continue; // skip false agents
//			}
//
//			Object[] attributes = new Object[]{
//				greenSpaceIdPerAgent.getOrDefault(agentId, null),
//				greenSpaceUtilizationDeviationValuePerAgent.getOrDefault(agentId, 0.0),
//				distanceToGreenSpaceDeviationValuePerAgent.getOrDefault(agentId, 0.0),
//				greenSpaceOverallRankingValuePerAgent.getOrDefault(agentId,0.0)
//			};
//
//			SimpleFeature feature = pointFactoryBuilder.createPoint(coordinate, attributes, null);
//			if (feature == null) {
//				System.err.println("Feature konnte nicht erstellt werden für Agent: " + agentId);
//			}
//			featureCollection.add(feature);
//			System.out.println("Anzahl Features: " + featureCollection.size() + "Einträge: " + Arrays.toString(attributes));
//
//		}
//
//		// writing the FeatureCollection into a gpkg (because shp was not working)
//		GeoFileWriter.writeGeometries(featureCollection, String.valueOf(outputAgentGreenSpaceGeofile));
//
//		System.out.println("Shapefile erfolgreich geschrieben!");

		// end of call method
		return 0;
	}

	// because of repeated use - method to calculate the median value is excluded from the call method into its own method
	private double calculateMedian(Map<String, List<String>> mapOfRelevantAgents, Map<String, Double> mapToAnalyse) {
		double medianValue;
		medianValue = mapToAnalyse.entrySet().stream()
			.filter(stringDoubleEntry -> mapOfRelevantAgents.containsKey(stringDoubleEntry.getKey()))
			.map(Map.Entry::getValue)
			.sorted() // sorting the values
			.collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
				int size = list.size();
				if (size == 0) return 0.0; // in case the list is empty
				if (size % 2 == 1) { // odd number of entries
					return list.get(size / 2);
				} else { // even number of entries
					return (list.get(size / 2 - 1) + list.get(size / 2)) / 2.0;
				}
			}));
		return medianValue;
	}

	// method to identify the nearest green space for each agent and put the information into a map as well as count this person on the identified green space
	private void processPerson(String id, String homeX, String homeY, Collection<SimpleFeature> features,
							   Map<String, String> greenSpaceIdPerAgent, Map<String, Double> distancePerAgent,
							   Map<String, Integer> nrOfPeoplePerGreenSpace, Map<String, List<Double>> greenSpaceUtilization) {
		Coord homeCoord = new Coord(Double.parseDouble(homeX), Double.parseDouble(homeY));
		double shortestDistance = Double.MAX_VALUE;
		String closestGreenSpace = null;

		for (SimpleFeature feature : features) {
			Geometry geometry = (Geometry) feature.getDefaultGeometry();
			if (geometry instanceof Point point) {
				double distance = CoordUtils.calcEuclideanDistance(homeCoord, MGC.point2Coord(point));
				if (distance < shortestDistance) {
					shortestDistance = distance;
					closestGreenSpace = (String) feature.getAttribute("osm_id");
				}
			}
		}

		if (closestGreenSpace != null) {
			greenSpaceIdPerAgent.put(id, closestGreenSpace);
			distancePerAgent.put(id, shortestDistance);
			nrOfPeoplePerGreenSpace.merge(closestGreenSpace, 1, Integer::sum);
			calculateMeanDistancePerGreenSpace(greenSpaceUtilization, closestGreenSpace, shortestDistance);
		}
	}

	// calculating the mean distance people have to walk to reach each green space
	private void calculateMeanDistancePerGreenSpace(Map<String, List<Double>> greenSpaceUtilization, String greenSpaceId, double distance) {
		List<Double> values = greenSpaceUtilization.get(greenSpaceId);
		double count = values.get(0) + 1;
		double meanDistance = (values.get(1) * (count - 1) + distance) / count;
		greenSpaceUtilization.put(greenSpaceId, Arrays.asList(count, meanDistance));
	}

	// calculating the utilization per green space by dividing the people by the area of the green space to receive the number of people per m² of green space
	private void calculateGreenSpaceUtilization(Map<String, Double> areaPerGreenSpace,
												Map<String, Integer> nrOfPeoplePerGreenSpace,
												Map<String, Double> utilizationPerGreenSpace) {
		for (Map.Entry<String, Double> entry : areaPerGreenSpace.entrySet()) {
			String greenSpaceId = entry.getKey();
			double area = entry.getValue();
			double peopleCount = nrOfPeoplePerGreenSpace.getOrDefault(greenSpaceId, 0)/sampleSize;
			utilizationPerGreenSpace.put(greenSpaceId, area > 0 ? peopleCount / area : 0);
		}
	}

	// NOT YET USED method to create the XYT map
	private void processAgentLiveabilityData(Path inputPath, Map<String, Double> distancePerAgent,
											 Map<String, String> greenSpaceIdPerAgent) throws IOException {
		try(CSVReader agentLiveabilityReader = new CSVReader(new FileReader(String.valueOf(inputAgentLiveabilityInfoPath)));
			CSVWriter XYTGreenSpaceMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTAgentBasedGreenSpaceMapPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)){

			XYTGreenSpaceMapWriter.writeNext(new String[]{"# EPSG:25832"});
			XYTGreenSpaceMapWriter.writeNext(new String[]{"time", "x", "y", "value"});

			String[] nextLine;
			while ((nextLine = agentLiveabilityReader.readNext()) != null) {
				String homeX = nextLine[1];
				String homeY = nextLine[2];
				String person = nextLine[0];

				Double rankingValue = distancePerAgent.getOrDefault(person, 99.0);
				XYTGreenSpaceMapWriter.writeNext(new String[]{"0.0", homeX, homeY, String.valueOf(rankingValue)});
			}
		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
	}
}
