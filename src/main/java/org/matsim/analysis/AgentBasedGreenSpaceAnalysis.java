package org.matsim.analysis;


import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.run.RunAccessibilityExample;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.legacy.run.RunBerlinScenario;
import picocli.CommandLine;
import org.matsim.contrib.accessibility.*;
import org.matsim.contrib.accessibility.utils.VisualizationUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.lang.Double.NaN;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidInputDirectory;
import static org.matsim.dashboard.RunLiveabilityDashboard.getValidOutputDirectory;

@CommandLine.Command(
	name = "greenSpace-analysis",
	description = "Green Space availability and accessibility Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	requires = {
		"berlin-v6.3.output_persons.csv",
		"test_accessPoints.shp",
		//"berlin_allGreenSpacesLarger1ha.shp"
		"allGreenSpaces_min1ha.shp"
	},
	produces = {
		"greenSpace_stats_perAgent.csv",
		"greenSpace_RankingValue.csv",
		"greenSpace_utilization.csv"
	}
)

public class AgentBasedGreenSpaceAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);

	// constants for paths
	private final Path inputPersonsCSVPath = getValidOutputDirectory().resolve("berlin-v6.3.output_persons.csv");
	//accessPoint shp Layer has to include the osm_id of the corresponding green space (column name "osm_id") as well as the area of the green space (column name "area")
	private final Path accessPointShpPath = getValidOutputDirectory().resolve("test_accessPoints.shp");
	private final Path outputPersonsCSVPath = getValidOutputDirectory().resolve("analysis/analysis/greenSpace_stats_perAgent.csv");
	private final Path outputGreenSpaceUtilizationPath = getValidOutputDirectory().resolve("analysis/analysis/greenSpace_utilization.csv");
	private final Path outputRankingValueCSVPath = getValidOutputDirectory().resolve("analysis/analysis/greenSpace_RankingValue.csv");


	public static void main(String[] args) {
		new AgentBasedGreenSpaceAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {

//		Path inputPersonsCSVPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv"));
//		Path outputPersonsCSVPath = output.getPath("greenSpace_stats_perAgent.csv");
//		//accessPoint shp Layer has to include the osm_id of the corresponding green space (column name "osm_id") as well as the area of the green space (column name "area")
//		Path accessPointShpPath = Path.of(input.getPath("test_accessPoints.shp"));
//	//	Path greenSpaceShpPath = Path.of(input.getPath("allGreenSpaces_min1ha.shp"));
//		Path outputGreenSpaceUtilizationPath = output.getPath("greenSpace_utilization.csv");
//		Path outputRankingValueCSVPath = output.getPath("greenSpace_RankingValue.csv");

		AgentLiveabilityInfo agentLiveabilityInfo = new AgentLiveabilityInfo();

		Collection<SimpleFeature> accessPointFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource(String.valueOf(accessPointShpPath)));

		try (CSVReader reader = new CSVReaderBuilder(new FileReader(String.valueOf(inputPersonsCSVPath)))
			.withCSVParser(new CSVParserBuilder().withSeparator(';').build())
			.build();) {

			String[] personRecord;
			personRecord = reader.readNext();

			CSVWriter agentCSVWriter = new CSVWriter(new FileWriter(outputPersonsCSVPath.toFile()));
			CSVWriter greenSpaceUtilizationWriter = new CSVWriter(new FileWriter(outputGreenSpaceUtilizationPath.toFile()));

			agentCSVWriter.writeNext(new String[]{"AgentID", "ClosestGreenSpace", "DistanceToGreenSpace", "UtilizationOfGreenSpace [m²/person]"});
			greenSpaceUtilizationWriter.writeNext(new String[]{"osm_id", "nrOfPeople", "meanDistance", "utilization [m²/person]"});

			Map<String, List<Double>> greenSpaceUtilization = new HashMap<>();
			Map<String, Double> distancePerAgent = new HashMap<>();
			Map<String, String> greenSpaceIdPerAgent = new HashMap<>();
			Map<String, Double> utilizationPerGreenSpace = new HashMap<>();
			Map<String, Double> areaPerGreenSpace = new HashMap<>();
			Map<String, Integer> nrOfPeoplePerGreenSpace = new HashMap<>();

			for (SimpleFeature simpleFeature : accessPointFeatures) {
				String osmId = (String) simpleFeature.getAttribute("osm_id");
				if(!greenSpaceUtilization.containsKey(osmId)) {
					greenSpaceUtilization.putIfAbsent(osmId, Arrays.asList(0.0,0.0));
				}
				Double area = (Double) simpleFeature.getAttribute("area");
				if(!areaPerGreenSpace.containsKey(osmId)) {
					areaPerGreenSpace.putIfAbsent(osmId, area);
				}
				if(!nrOfPeoplePerGreenSpace.containsKey(osmId)) {
					nrOfPeoplePerGreenSpace.putIfAbsent(osmId, 0);
				}
				if(!utilizationPerGreenSpace.containsKey(osmId)) {
					utilizationPerGreenSpace.putIfAbsent(osmId, 0.0);
				}
			}

			while ((personRecord = reader.readNext()) != null) {
				String id = personRecord[0];
				String homeX = personRecord[15];
				String homeY = personRecord[16];
				String RegioStaR7 = personRecord[5];

				// Validierung der Koordinatenwerte
				if (homeX == null || homeX.isEmpty() || homeY == null || homeY.isEmpty()) {
				//	System.err.println("Ungültige Koordinaten für AgentID: " + id);
					continue; // Überspringe diesen Datensatz
				}

				if (Objects.equals(RegioStaR7, "1")) {

					try {
						Coord homeCoord = new Coord(Double.parseDouble(homeX), Double.parseDouble(homeY));

						double shortestDistance = Double.MAX_VALUE;
						String closestGeometryName = null;
						double areaOfGreenSpace = 0.0;

						for (SimpleFeature simpleFeature : accessPointFeatures) {
							// Geometrie aus dem SimpleFeature extrahieren
							Geometry geometry = (Geometry) simpleFeature.getDefaultGeometry();

							if (geometry instanceof Point) {
								Point point = (Point) geometry; // In Point umwandeln
								double distanceToClosestAccessPoint = CoordUtils.calcEuclideanDistance(
									homeCoord,
									MGC.point2Coord(point)
								);

								if (distanceToClosestAccessPoint < shortestDistance) {
									shortestDistance = distanceToClosestAccessPoint;
									distancePerAgent.put(id, shortestDistance);
									closestGeometryName = (String) simpleFeature.getAttribute("osm_id");
									greenSpaceIdPerAgent.put(id, closestGeometryName);
								}

							} else {
								System.out.println("Geometrie ist kein Punkt: " + geometry.getGeometryType());
							}
						}

						nrOfPeoplePerGreenSpace.put(closestGeometryName, nrOfPeoplePerGreenSpace.get(closestGeometryName)+1);
						Double nrOfPeople = greenSpaceUtilization.get(closestGeometryName).get(0)+1;
						Double meanDistance = (greenSpaceUtilization.get(closestGeometryName).get(1)*(nrOfPeople-1)+shortestDistance)/(nrOfPeople);

						greenSpaceUtilization.put(closestGeometryName, Arrays.asList(nrOfPeople,meanDistance));

						// Schreibe Ergebnisse in die Output-CSV
					//	agentCSVWriter.writeNext(new String[]{id, closestGeometryName, String.valueOf(shortestDistance), String.valueOf(utilization)});

					} catch (NumberFormatException e) {
						System.err.println("Fehlerhafte Koordinaten für AgentID: " + id + " (" + homeX + ", " + homeY + ")");
					}
				}
			}


			for (String key : utilizationPerGreenSpace.keySet()) {
				Double utilization = areaPerGreenSpace.get(key) / nrOfPeoplePerGreenSpace.get(key);
				utilizationPerGreenSpace.put(key, utilization);
			}

			// Schreibe Ergebnisse pro Green Space in die Output-CSV
			for (Map.Entry<String, List<Double>> greenSpaceUtilizationEntry : greenSpaceUtilization.entrySet()) {

				List<Double> values = greenSpaceUtilizationEntry.getValue();
				String id = greenSpaceUtilizationEntry.getKey();
				String countAgentUtilization = String.valueOf(values.get(0).intValue());
				String meanDistance = String.valueOf(values.get(1));
				String utilization = utilizationPerGreenSpace.get(id).toString();

				greenSpaceUtilizationWriter.writeNext(new String[]{id, countAgentUtilization, meanDistance, utilization});
			}

			// Schreibe Ergebnisse pro Agent in die Output-CSV
			for (Map.Entry<String, String> AgentEntry : greenSpaceIdPerAgent.entrySet()) {
				agentCSVWriter.writeNext(new String[]{AgentEntry.getKey(), AgentEntry.getValue(), String.valueOf(distancePerAgent.get(AgentEntry.getKey())), String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue()))});

			}

			Map<String, Double> utilizationPerAgent = new HashMap<>();
			for (String Key : greenSpaceIdPerAgent.keySet()) {
				utilizationPerAgent.put(Key, utilizationPerGreenSpace.get(greenSpaceIdPerAgent.get(Key)));
			}

			Map<String, Boolean> greenSpaceRankingValuePerAgent = new HashMap<>();
			for (String Key : greenSpaceIdPerAgent.keySet()) {

				if (distancePerAgent.get(Key) < 500 && utilizationPerAgent.get(Key) < 6) {
					greenSpaceRankingValuePerAgent.put(Key, Boolean.TRUE);
				}
				else {
					greenSpaceRankingValuePerAgent.put(Key, Boolean.FALSE);
				}
			}

			double greenSpaceRankingValue = greenSpaceRankingValuePerAgent.values().stream().filter(Boolean::booleanValue).count()*100/greenSpaceRankingValuePerAgent.size();
			System.out.println(greenSpaceRankingValue);
			String formattedRankingGreenSpace = String.format(Locale.US, "%.2f%%", greenSpaceRankingValue);

			double averageDistance = distancePerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
			System.out.println(averageDistance);
			String formattedAverageDistance = String.format(Locale.US, "%.2f", averageDistance);

			//Ziel: Median statt Mittelwert, da dieser durch große Randgrünflächen verzerrt wird
//			double medianDistance = distancePerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
//			System.out.println(medianDistance);
//			String formattedMedianDistance = String.format(Locale.US, "%.2f", medianDistance);

			double averageUtilization = utilizationPerAgent.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
			System.out.println(averageUtilization);
			String formattedAverageUtilization = String.format(Locale.US, "%.2f", averageUtilization);

			// Schreibe das Ergebnis zusammen mit rankingLossTime in die Datei lossTime_RankingValue.csv
			try (BufferedWriter writer = Files.newBufferedWriter(outputRankingValueCSVPath)) {
				//	writer.write("Dimension;Value\n"); // Header
				writer.write(String.format("GreenSpaceRanking;%s\n", formattedRankingGreenSpace)); // Ranking
				writer.write(String.format("AverageDistance(m);%s\n", formattedAverageDistance)); // Summe der
				writer.write(String.format("AverageUtilization(m²/Person);%s\n", formattedAverageUtilization)); // Summe der

			}

//			CSVWriter rankingWriter = null;
//			rankingWriter.writeNext(greenSpaceRankingValuePerAgent.);
//
//			agentCSVWriter.writeNext(new String[]{AgentEntry.getKey(), AgentEntry.getValue(), String.valueOf(distancePerAgent.get(AgentEntry.getKey())), String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue()))});


			agentLiveabilityInfo.extendAgentLiveabilityInfoCsvWithAttribute(distancePerAgent, "MinGreenSpaceEuclideanDistance");
			agentLiveabilityInfo.extendAgentLiveabilityInfoCsvWithAttribute(utilizationPerAgent, "GreenSpaceUtilization (m²/person)");

			agentLiveabilityInfo.extendSummaryTilesCsvWithAttribute(formattedRankingGreenSpace, "GreenSpace");

		} catch (IOException | CsvValidationException e) {
			throw new RuntimeException(e);
		}

		List<Coord> activityCoords = new ArrayList<>();
		//AgentLiveabilityInfo.extendAgentLiveabilityInfoCsvWithAttribute(String.valueOf(utilizationPerGreenSpace.get(AgentEntry.getValue())));extendCsvWithAttribute(sumLossTimePerAgent, "Loss Time");

		return 0;
	}

	private static final Logger LOG = LogManager.getLogger(RunAccessibilityExample.class);

	public static void run(Scenario scenario) {
		List<String> activityTypes = AccessibilityUtils.collectAllFacilityOptionTypes(scenario);
		LOG.info("The following activity types were found: " + String.valueOf(activityTypes));
		Controler controler = new Controler(scenario);

		for(String actType : activityTypes) {
			AccessibilityModule module = new AccessibilityModule();
			module.setConsideredActivityType(actType);
			controler.addOverridingModule(module);
		}

		controler.run();
	}
}

