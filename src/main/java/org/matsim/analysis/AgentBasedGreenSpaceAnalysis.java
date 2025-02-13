package org.matsim.analysis;


import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.run.RunAccessibilityExample;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import picocli.CommandLine;
import org.matsim.contrib.accessibility.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "greenSpace-analysis",
	description = "Green Space availability and accessibility Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	group="liveability",
	requires = {
		"berlin-v6.3.output_persons.csv", //GGF. SPÄTER NICHT MEHR WENN NICHT ALLE PERSONEN EINGELESEN?
		"test_accessPoints.shp",
	//	"allGreenSpaces_min1ha.shp"
	},
	produces = {
		"XYTAgentBasedGreenSpaceMap.xyt.csv",
		"greenSpace_RankingValue.csv",
		"greenSpace_utilization.csv",
		"XYTGreenSpaceUtilizationMap.xyt.csv",
		"greenSpace_stats_perAgent.csv"
	}
)

public class AgentBasedGreenSpaceAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);

	//Weg finden, hier oben aus der Config die sample size auszulesen!!! --> siehe in call methode (klappt nicht hier oben)
	double sampleSize = 0.1;
//	Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("config.xml", input.getRunDirectory()).toAbsolutePath().toString());
//	SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
//	this.sampleSize = simwrapper.sampleSize;

	// defining the limits
	private final double limitEuclideanDistanceToGreenSpace = 500;
	private final double limitGreenSpaceUtilization = 6; // ATTENTION: THIS IS FOR THE 100% REALITY CASE - FOR SMALLER SAMPLES (E.G. 10pct), THIS HAS TO BE ADAPTED ACCORDINGLY
	private final double limitGreenSpaceUtilizationSampleSizeAdjusted = limitGreenSpaceUtilization / sampleSize;

	// constants for paths
	// input paths
	private final Path inputPersonsCSVPath = ApplicationUtils.matchInput("berlin-v6.3.output_persons.csv", getValidOutputDirectory());
	//accessPoint shp Layer has to include the osm_id of the corresponding green space (column name "osm_id") as well as the area of the green space (column name "area")
	private final Path inputAccessPointShpPath = ApplicationUtils.matchInput("test_accessPoints.shp", getValidOutputDirectory());
	private final Path inputAgentLiveabilityInfoPath = ApplicationUtils.matchInput("agentLiveabilityInfo.csv", getValidLiveabilityOutputDirectory());
	//	Path greenSpaceShpPath = Path.of(input.getPath("allGreenSpaces_min1ha.shp"));

	// output paths
	private final Path XYTAgentBasedGreenSpaceMapPath = getValidLiveabilityOutputDirectory().resolve("XYTAgentBasedGreenSpaceMap.xyt.csv");
	private final Path outputRankingValueCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_RankingValue.csv");
	private final Path outputGreenSpaceUtilizationPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_utilization.csv");
	private final Path XYTGreenSpaceUtilizationMapPath = getValidLiveabilityOutputDirectory().resolve("XYTGreenSpaceUtilizationMap.xyt.csv");
	private final Path outputPersonsCSVPath = getValidLiveabilityOutputDirectory().resolve("greenSpace_stats_perAgent.csv");

	public static void main(String[] args) {
		new AgentBasedGreenSpaceAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

		Config config = ConfigUtils.loadConfig(ApplicationUtils.matchInput("config.xml", input.getRunDirectory()).toAbsolutePath().toString());
		SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		this.sampleSize = simwrapper.sampleSize;

		// initialising collections and data structures
		AgentLiveabilityInfoCollection agentLiveabilityInfoCollection = new AgentLiveabilityInfoCollection();
		Collection<SimpleFeature> accessPointFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource(String.valueOf(inputAccessPointShpPath)));

		// defining all maps to be able to put and get values of those throughout the analysis
		Map<String, List<String>> homeCoordinatesPerAgent = new HashMap<>();
		Map<String, List<Double>> greenSpaceUtilization = new HashMap<>();
		Map<String, Double> distancePerAgent = new HashMap<>();
		Map<String, String> greenSpaceIdPerAgent = new HashMap<>();
		Map<String, Double> utilizationPerGreenSpace = new HashMap<>();
		Map<String, Double> utilizationPerAgent = new HashMap<>();
		Map<String, Double> areaPerGreenSpace = new HashMap<>();
		Map<String, Integer> nrOfPeoplePerGreenSpace = new HashMap<>();

		Map<String, Double> limitDistanceToGreenSpace = new HashMap<>();
		Map<String, Double> limitUtilizationOfGreenSpace = new HashMap<>();
		Map<String, Double> greenSpaceUtilizationRankingValuePerAgent = new HashMap<>();
		Map<String, Double> distanceToGreenSpaceRankingValuePerAgent = new HashMap<>();
		Map<String, Double> GreenSpaceOverallRankingValuePerAgent = new HashMap<>();
		Map<String, String> dimensionOverallRankingValue = new HashMap<>();


		//1. nächste Grünfläche pro Agent bestimmen
		//2. Auslastung pro Grünfläche
		//3. Auslastung pro Agent
		//4. alle Outputdateien beschreiben
		//4a. agentenbezogen
		//4b. gesamt/pro Grünfläche

		try (CSVReader personsCsvReader = new CSVReaderBuilder(new FileReader(String.valueOf(inputPersonsCSVPath)))
			.withCSVParser(new CSVParserBuilder().withSeparator(';').build())
			.build();
			 CSVWriter agentCSVWriter = new CSVWriter(new FileWriter(outputPersonsCSVPath.toFile()));
			 CSVWriter greenSpaceUtilizationWriter = new CSVWriter(new FileWriter(outputGreenSpaceUtilizationPath.toFile()))) {

			// writing csv-headers
			agentCSVWriter.writeNext(new String[]{"AgentID", "ClosestGreenSpace", "DistanceToGreenSpace", "UtilizationOfGreenSpace [m²/person]"});
			greenSpaceUtilizationWriter.writeNext(new String[]{"osm_id", "nrOfPeople", "meanDistance", "utilization [m²/person]"});

			// processing green space features
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

			//STATTDESSEN PARSER VERWENDEN?!?
			String[] personRecord;
			//personRecord = personsCsvReader.readNext();
			while ((personRecord = personsCsvReader.readNext()) != null) {
				String id = personRecord[0];
				String homeX = personRecord[15];
				String homeY = personRecord[16];
				homeCoordinatesPerAgent.put(id, Arrays.asList(homeX, homeY));

				String RegioStaR7 = personRecord[5];

				// Validierung der Koordinatenwerte
				if (homeX == null || homeX.isEmpty() || homeY == null || homeY.isEmpty() || (!"1".equals(RegioStaR7) && !"2".equals(RegioStaR7) && !"3".equals(RegioStaR7) && !"4".equals(RegioStaR7))) {
					continue; // skip those agents without valid home coordinates or outside the RegioStaR7 zones 1 and 2
				}

				processPerson(id, homeX, homeY, accessPointFeatures, greenSpaceIdPerAgent, distancePerAgent, nrOfPeoplePerGreenSpace, greenSpaceUtilization);
			}

			// Nutzung und Auslastung pro Grünfläche berechnen
			calculateGreenSpaceUtilization(areaPerGreenSpace, nrOfPeoplePerGreenSpace, utilizationPerGreenSpace);

			// Ergebnisse in CSVs schreiben
			for (Map.Entry<String, List<Double>> entry : greenSpaceUtilization.entrySet()) {
				String id = entry.getKey();
				List<Double> values = entry.getValue();
				String count = String.valueOf(values.get(0).intValue());
				String meanDistance = String.valueOf(values.get(1));
				String utilization = utilizationPerGreenSpace.get(id).toString();
				greenSpaceUtilizationWriter.writeNext(new String[]{id, count, meanDistance, utilization});
			}
			for (Map.Entry<String, String> entry : greenSpaceIdPerAgent.entrySet()) {
				String agentId = entry.getKey();
				String nearestGreenSpaceId = entry.getValue();
				utilizationPerAgent.put(agentId, utilizationPerGreenSpace.get(nearestGreenSpaceId));
				agentCSVWriter.writeNext(new String[]{
					agentId, nearestGreenSpaceId,
					String.valueOf(distancePerAgent.get(agentId)),
					String.valueOf(utilizationPerGreenSpace.get(nearestGreenSpaceId))
				});
			}
		} catch (IOException | CsvValidationException e) {
			throw new RuntimeException(e);
		}

		// Berechnung der Rankingwerte (einzelindikatoren und gesamt-Green Space)

		double counterOverall = 0;
		double counterDistance = 0;
		double counterUtilization = 0;
		// distance to GS Ranking Value berechnen
		for (Map.Entry<String, Double> entry : distancePerAgent.entrySet()) {
			String agentId = entry.getKey();
			double distanceRankingValue = (distancePerAgent.get(agentId) - limitEuclideanDistanceToGreenSpace) / limitEuclideanDistanceToGreenSpace;
			distanceToGreenSpaceRankingValuePerAgent.put(agentId, distanceRankingValue);
			if (distanceRankingValue <= 0) {
				counterDistance++;
			}


			double utilizationRankingValue = (limitGreenSpaceUtilizationSampleSizeAdjusted - utilizationPerAgent.get(agentId)) / limitGreenSpaceUtilizationSampleSizeAdjusted;
			greenSpaceUtilizationRankingValuePerAgent.put(agentId, utilizationRankingValue);
			if (utilizationRankingValue <= 0) {
				counterUtilization++;
			}


			double overallGreenSpaceRankingValue = Math.max(distanceRankingValue, utilizationRankingValue);
			GreenSpaceOverallRankingValuePerAgent.put(agentId, overallGreenSpaceRankingValue);

			if (overallGreenSpaceRankingValue <= 0) {
				counterOverall++;
			}

			limitDistanceToGreenSpace.put(agentId, limitEuclideanDistanceToGreenSpace);
			limitUtilizationOfGreenSpace.put(agentId, limitGreenSpaceUtilization);
		}

		// calculate overall ranking value
		double greenSpaceRankingValue = counterOverall / greenSpaceUtilizationRankingValuePerAgent.size();
		String formattedRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceRankingValue * 100);
		dimensionOverallRankingValue.put("Green Space", formattedRankingGreenSpace);

		double greenSpaceDistanceRankingValue = counterDistance / greenSpaceUtilizationRankingValuePerAgent.size();
		String formattedDistanceRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceDistanceRankingValue * 100);

		double greenSpaceUtilizationRankingValue = counterUtilization / greenSpaceUtilizationRankingValuePerAgent.size();
		String formattedRankingUtilizationGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceUtilizationRankingValue * 100);

		double avgDistance = distancePerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double medianDistance = distancePerAgent.values().stream()
			.sorted() // Werte sortieren
			.toList() // In eine Liste umwandeln
			.stream() // Stream aus der sortierten Liste
			.collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
				int size = list.size();
				if (size == 0) return 0.0; // Sonderfall: Leere Liste
				if (size % 2 == 1) { // Ungerade Anzahl
					return list.get(size / 2);
				} else { // Gerade Anzahl
					return (list.get(size / 2 - 1) + list.get(size / 2)) / 2.0;
				}
			}));
		double avgUtilization = utilizationPerGreenSpace.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
		double medianUtilization = utilizationPerAgent.values().stream()
			.sorted() // Werte sortieren
			.toList() // In eine Liste umwandeln
			.stream() // Stream aus der sortierten Liste
			.collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
				int size = list.size();
				if (size == 0) return 0.0; // Sonderfall: Leere Liste
				if (size % 2 == 1) { // Ungerade Anzahl
					return list.get(size / 2);
				} else { // Gerade Anzahl
					return (list.get(size / 2 - 1) + list.get(size / 2)) / 2.0;
				}
			}));

		String formattedAvgDistance = String.format(Locale.US, "%.2f", avgDistance);
		String formattedMedianDistance = String.format(Locale.US, "%.2f", medianDistance);

		String formattedAvgUtilization = String.format(Locale.US, "%.2f", avgUtilization);
		String formattedMedianUtilization = String.format(Locale.US, "%.2f", medianUtilization);


		// übergeben der Ergebnisse an die SummaryDashboard Dateien
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(distancePerAgent, "MinGreenSpaceEuclideanDistance");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitDistanceToGreenSpace, "limit_EuclideanDistanceToNearestGreenSpace");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(distanceToGreenSpaceRankingValuePerAgent, "rankingValue_DistanceToGreenSpace");

		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(utilizationPerAgent, "GreenSpaceUtilization (m²/person)");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(limitUtilizationOfGreenSpace, "limit_SpacePerAgentAtNearestGreenSpace");
		agentLiveabilityInfoCollection.extendAgentLiveabilityInfoCsvWithAttribute(greenSpaceUtilizationRankingValuePerAgent, "rankingValue_GreenSpaceUtilization");

		agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedRankingGreenSpace, "GreenSpace");
		//	agentLiveabilityInfoCollection.extendSummaryTilesCsvWithAttribute(formattedRankingGreenSpace, "GreenSpace", "https://github.com/simwrapper/simwrapper/blob/master/public/images/tile-icons/emoji_transportation.svg");

		agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Green Space", "Distance to nearest green space", formattedMedianDistance, String.valueOf(limitEuclideanDistanceToGreenSpace), formattedDistanceRankingGreenSpace, 1);
		agentLiveabilityInfoCollection.extendIndicatorValuesCsvWithAttribute("Green Space", "Utilization of Green Space", formattedAvgDistance, String.valueOf(limitGreenSpaceUtilization), formattedRankingUtilizationGreenSpace, 1);


		// output dateien erzeugen für GS Dashboard-Seite
		try (CSVWriter GSTileWriter = new CSVWriter(new FileWriter(outputRankingValueCSVPath.toFile()));
			 CSVWriter GSxytAgentMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTAgentBasedGreenSpaceMapPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END);
			CSVWriter XYTMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTGreenSpaceUtilizationMapPath)),
				CSVWriter.DEFAULT_SEPARATOR,
				CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				CSVWriter.DEFAULT_LINE_END)) {

			GSTileWriter.writeNext(new String[]{"GreenSpaceRanking", formattedRankingGreenSpace});
			GSTileWriter.writeNext(new String[]{"AverageDistance(m)", formattedAvgDistance});
			GSTileWriter.writeNext(new String[]{"MedianDistance(m)", formattedMedianDistance});
			GSTileWriter.writeNext(new String[]{"AverageUtilization(m²/Person)", formattedAvgUtilization});
			GSTileWriter.writeNext(new String[]{"MedianUtilization(m²/person)", formattedMedianUtilization});

			GSxytAgentMapWriter.writeNext(new String[]{"# EPSG:25832"});
			GSxytAgentMapWriter.writeNext(new String[]{"time", "x", "y", "value"});

			for (Map.Entry<String, Double> entry : GreenSpaceOverallRankingValuePerAgent.entrySet()) {
				String agentName = entry.getKey();
				GSxytAgentMapWriter.writeNext(new String[]{String.valueOf(0.0), homeCoordinatesPerAgent.get(agentName).get(0), homeCoordinatesPerAgent.get(agentName).get(1), String.valueOf(GreenSpaceOverallRankingValuePerAgent.get(entry.getKey()))});
			}

			return 0;
		}
	}

//		try {
//						Coord homeCoord = new Coord(Double.parseDouble(homeX), Double.parseDouble(homeY));
//
//						double shortestDistance = Double.MAX_VALUE;
//						String closestGeometryName = null;
//						double areaOfGreenSpace = 0.0;
//
//						for (SimpleFeature simpleFeature : accessPointFeatures) {
//							// Geometrie aus dem SimpleFeature extrahieren
//							Geometry geometry = (Geometry) simpleFeature.getDefaultGeometry();
//
//							if (geometry instanceof Point) {
//								Point point = (Point) geometry; // In Point umwandeln
//								double distanceToClosestAccessPoint = CoordUtils.calcEuclideanDistance(
//									homeCoord,
//									MGC.point2Coord(point)
//								);
//
//								if (distanceToClosestAccessPoint < shortestDistance) {
//									shortestDistance = distanceToClosestAccessPoint;
//									distancePerAgent.put(id, shortestDistance);
//									closestGeometryName = (String) simpleFeature.getAttribute("osm_id");
//									greenSpaceIdPerAgent.put(id, closestGeometryName);
//									Double GSdistanceRankingValue = (shortestDistance-limitEuclideanDistanceToGreenSpace)/limitEuclideanDistanceToGreenSpace;
//									distanceToGreenSpaceRankingValue.put(id, GSdistanceRankingValue);
//								}
//
//							} else {
//								System.out.println("Geometrie ist kein Punkt: " + geometry.getGeometryType());
//							}
//						}
//
//						nrOfPeoplePerGreenSpace.put(closestGeometryName, nrOfPeoplePerGreenSpace.get(closestGeometryName)+1);
//						Double nrOfPeople = greenSpaceUtilization.get(closestGeometryName).get(0)+1;
//						Double meanDistance = (greenSpaceUtilization.get(closestGeometryName).get(1)*(nrOfPeople-1)+shortestDistance)/(nrOfPeople);
//
//						greenSpaceUtilization.put(closestGeometryName, Arrays.asList(nrOfPeople,meanDistance));
//
//					} catch (NumberFormatException e) {
//						System.err.println("Fehlerhafte Koordinaten für AgentID: " + id + " (" + homeX + ", " + homeY + ")");
//					}
//				}
//			}
//
//
//			for (String key : utilizationPerGreenSpace.keySet()) {
//				Double utilization = areaPerGreenSpace.get(key) / nrOfPeoplePerGreenSpace.get(key);
//				utilizationPerGreenSpace.put(key, utilization);
//			}
//
//			// Schreibe Ergebnisse pro Green Space in die Output-CSV
//			for (Map.Entry<String, List<Double>> greenSpaceUtilizationEntry : greenSpaceUtilization.entrySet()) {
//
//				List<Double> values = greenSpaceUtilizationEntry.getValue();
//				String id = greenSpaceUtilizationEntry.getKey();
//				String countAgentUtilization = String.valueOf(values.get(0).intValue());
//				String meanDistance = String.valueOf(values.get(1));
//				String utilization = utilizationPerGreenSpace.get(id).toString();
//
//				greenSpaceUtilizationWriter.writeNext(new String[]{id, countAgentUtilization, meanDistance, utilization});
//			}
//
//			//perspektivisch so - dafür aber csv Parser mit personen IDS
//			//limitDistanceToGreenSpace.put(Arrays.toString(personRecord), limitEuclideanDistanceToGreenSpace);
//			//limitUtilizationOfGreenSpace.put(Arrays.toString(personRecord), limitGreenSpaceUtilization);
//
//			// Schreibe Ergebnisse pro Agent in die Output-CSV
//			for (Map.Entry<String, String> AgentEntry : greenSpaceIdPerAgent.entrySet()) {
//				agentCSVWriter.writeNext(new String[]{AgentEntry.getKey(), AgentEntry.getValue(), String.valueOf(distancePerAgent.get(AgentEntry.getKey())), String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue()))});
//
//			}
//
//			int counterAll;
//			int counterTrueAgents=0;
//
//			Map<String, Boolean> greenSpaceRankingValuePerAgent = new HashMap<>();
//			Map<String, Double> utilizationPerAgent = new HashMap<>();
//			for (String Key : greenSpaceIdPerAgent.keySet()) {
//				utilizationPerAgent.put(Key, utilizationPerGreenSpace.get(greenSpaceIdPerAgent.get(Key)));
//				greenSpaceUtilizationRankingValue.put(Key, (limitGreenSpaceUtilization-utilizationPerAgent.get(Key))/limitGreenSpaceUtilization);

//
//				if (distancePerAgent.get(Key) < 500 && utilizationPerAgent.get(Key) > 6) {
//					counterTrueAgents++;
//				}
////				else {
////					greenSpaceRankingValuePerAgent.put(Key, Boolean.FALSE);
////				}
//			}
//
//			double greenSpaceRankingValue = (double) counterTrueAgents /utilizationPerAgent.size();
//
////			double greenSpaceRankingValue = greenSpaceRankingValuePerAgent.values().stream().filter(Boolean::booleanValue).count()*100/greenSpaceRankingValuePerAgent.size();
//			System.out.println(greenSpaceRankingValue);
		//	String formattedRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceRankingValue*100);
//
//			double averageDistance = distancePerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
//			System.out.println(averageDistance);
//			String formattedAverageDistance = String.format(Locale.US, "%.2f", averageDistance);
//
//			//Ziel: Median statt Mittelwert, da dieser durch große Randgrünflächen verzerrt wird
////			double medianDistance = distancePerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
////			System.out.println(medianDistance);
////			String formattedMedianDistance = String.format(Locale.US, "%.2f", medianDistance);
//
//			double averageUtilization = utilizationPerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
//			System.out.println(averageUtilization);
//			String formattedAverageUtilization = String.format(Locale.US, "%.2f", averageUtilization);
//
//			// Schreibe das Ergebnis zusammen mit rankingLossTime in die Datei lossTime_RankingValue.csv
//			try (BufferedWriter writer = Files.newBufferedWriter(outputRankingValueCSVPath)) {
//				//	writer.write("Dimension;Value\n"); // Header
//				writer.write(String.format("GreenSpaceRanking;%s\n", formattedRankingGreenSpace)); // Ranking
//				writer.write(String.format("AverageDistance(m);%s\n", formattedAverageDistance)); // Summe der
//				writer.write(String.format("AverageUtilization(m²/Person);%s\n", formattedAverageUtilization)); // Summe der
//
//			}
//
////			agentCSVWriter.writeNext(new String[]{AgentEntry.getKey(), AgentEntry.getValue(), String.valueOf(distancePerAgent.get(AgentEntry.getKey())), String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue()))});
//
//			for (String Key : greenSpaceIdPerAgent.keySet()) {
//				limitUtilizationOfGreenSpace.put(Key, limitGreenSpaceUtilization);
//				limitDistanceToGreenSpace.put(Key, limitEuclideanDistanceToGreenSpace);
//			}
//
//


//
//		} catch (IOException | CsvValidationException e) {
//			throw new RuntimeException(e);
//		}
//
//		//hier alle Writer für summaryTile, GreenSpaceRankingTile, XYTMap Green Space,
//		//****************************
//		try(CSVReader agentLiveabilityReader = new CSVReader(new FileReader(String.valueOf(inputAgentLiveabilityInfoPath)));
//			CSVWriter XYTGreenSpaceMapWriter = new CSVWriter(new FileWriter(String.valueOf(XYTAgentBasedGreenSpaceMapPath)),
//				CSVWriter.DEFAULT_SEPARATOR,
//				CSVWriter.NO_QUOTE_CHARACTER, // Keine Anführungszeichen
//				CSVWriter.DEFAULT_ESCAPE_CHARACTER,
//				CSVWriter.DEFAULT_LINE_END)){
//			String[] nextLine;
//			XYTGreenSpaceMapWriter.writeNext(new String[]{"# EPSG:25832"});
//			XYTGreenSpaceMapWriter.writeNext(new String[]{"time", "x", "y", "value"});
//
//			agentLiveabilityReader.readNext();
//
//			while ((nextLine = agentLiveabilityReader.readNext()) != null) {
//
//				String homeX = nextLine[1];
//				String homeY = nextLine[2];
//				String person = nextLine[0];
//
//			//	Double GSdistanceRankingValue = distanceToGreenSpaceRankingValue.getOrDefault(person,99.0);
//				Double GSdistanceRankingValue = distanceToGreenSpaceRankingValue.get(person);
//
//				Double GSutilizationRankingValue = greenSpaceUtilizationRankingValue.get(person);
//
//				Double greenSpaceRankingValue = Math.max(GSdistanceRankingValue, GSutilizationRankingValue);
//
//			//	XYTGreenSpaceMapWriter.writeNext(new String[]{"0.0",homeX, homeY, String.valueOf(greenSpaceRankingValue)});
//				XYTGreenSpaceMapWriter.writeNext(new String[]{"0.0",homeX, homeY, String.valueOf(GSdistanceRankingValue), String.valueOf(GSutilizationRankingValue)});
//
//			}
//		}
//
//		List<Coord> activityCoords = new ArrayList<>();
//		//AgentLiveabilityInfo.extendAgentLiveabilityInfoCsvWithAttribute(String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue())));extendCsvWithAttribute(sumLossTimePerAgent, "Loss Time");
//
//		return 0;
//	}
//
//	private static final Logger LOG = LogManager.getLogger(RunAccessibilityExample.class);
//
//	public static void run(Scenario scenario) {
//		List<String> activityTypes = AccessibilityUtils.collectAllFacilityOptionTypes(scenario);
//		LOG.info("The following activity types were found: " + String.valueOf(activityTypes));
//		Controler controler = new Controler(scenario);
//
//		for(String actType : activityTypes) {
//			AccessibilityModule module = new AccessibilityModule();
//			module.setConsideredActivityType(actType);
//			controler.addOverridingModule(module);
//		}
//
//		controler.run();
//	}

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
			updateGreenSpaceUtilization(greenSpaceUtilization, closestGreenSpace, shortestDistance);
		}
	}

	private void updateGreenSpaceUtilization(Map<String, List<Double>> greenSpaceUtilization, String greenSpaceId, double distance) {
		List<Double> values = greenSpaceUtilization.get(greenSpaceId);
		double count = values.get(0) + 1;
		double meanDistance = (values.get(1) * (count - 1) + distance) / count;
		greenSpaceUtilization.put(greenSpaceId, Arrays.asList(count, meanDistance));
	}

	private void calculateGreenSpaceUtilization(Map<String, Double> areaPerGreenSpace,
												Map<String, Integer> nrOfPeoplePerGreenSpace,
												Map<String, Double> utilizationPerGreenSpace) {
		for (Map.Entry<String, Double> entry : areaPerGreenSpace.entrySet()) {
			String greenSpaceId = entry.getKey();
			double area = entry.getValue();
			int peopleCount = nrOfPeoplePerGreenSpace.getOrDefault(greenSpaceId, 0);
			utilizationPerGreenSpace.put(greenSpaceId, peopleCount > 0 ? area / peopleCount : 0);
		}
	}

	//PAth nicht übergeben!!
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
