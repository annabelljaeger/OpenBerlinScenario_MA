package org.matsim.analysis;


import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
		"berlin_accessPoints.shp",
		//"berlin_allGreenSpacesLarger1ha.shp"
		"allGreenSpaces_min1ha.shp"
	},
	produces = {
		"greenSpace_stats_perAgent.csv",
		"greenSpace_RankingValue.csv"
	}
)

public class AgentBasedGreenSpaceAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);

	public static void main(String[] args) {
		new AgentBasedGreenSpaceAnalysis().execute(args);

	}

	@Override
	public Integer call() throws Exception {

		//String
		Path inputPersonsCSVPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv"));
		Path outputPersonsCSVPath = output.getPath("greenSpace_stats_perAgent.csv");
		Path accessPointShpPath = Path.of(input.getPath("berlin_accessPoints.shp"));
		Path greenSpaceShpPath = Path.of(input.getPath("allGreenSpaces_min1ha.shp"));


		Collection<SimpleFeature> allFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource(String.valueOf(greenSpaceShpPath)));
		Collection<SimpleFeature> accessPointFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource(String.valueOf(accessPointShpPath)));

		try {
			// CSVReader mit Semikolon als Trennzeichen konfigurieren
			CSVReader reader = new CSVReaderBuilder(new FileReader(String.valueOf(inputPersonsCSVPath)))
				.withCSVParser(new CSVParserBuilder().withSeparator(';').build())
				.build();

			String[] personRecord;

			// Erste Zeile lesen (optional, z. B. Header überspringen)
			personRecord = reader.readNext();

//		try {
//			CSVReader reader = new CSVReader(new FileReader(String.valueOf(inputPersonsCSVPath)));
//
//			String[] personRecord = reader.readNext();


			while ((personRecord = reader.readNext()) != null) {

				for (String value : personRecord) {
					System.out.print(value + " | ");
				}
				String id = personRecord[0];
				String homeX = personRecord[15];
				String homeY = personRecord[16];

				Coord homeCoord = new Coord(Double.parseDouble(homeX), Double.parseDouble(homeY));

				for (SimpleFeature simpleFeature : accessPointFeatures) {
					// Geometrie aus dem SimpleFeature extrahieren
					Geometry geometry = (Geometry) simpleFeature.getDefaultGeometry();

					double shortestDistance = Double.MAX_VALUE;
					Geometry closestGeometry = null;

					if (geometry instanceof Point) {
						Point point = (Point) geometry; // In Point umwandeln
						double distanceToClosestAccessPoint = CoordUtils.calcEuclideanDistance(
							homeCoord,
							MGC.point2Coord(point)
						);

						if (distanceToClosestAccessPoint < shortestDistance) {
							shortestDistance = distanceToClosestAccessPoint;
							closestGeometry = geometry;
						}

						String name = (String) simpleFeature.getAttribute("osm_id");
						System.out.println("OSM-ID der Grünfläche: " + name + " Distanz: " + shortestDistance);
					} else {
						System.out.println("Geometrie ist kein Punkt: " + geometry.getGeometryType());
					}
				}



//				for (SimpleFeature simpleFeature : accessPointFeatures) {
//
//					Geometry defaultGeometry = (Geometry) simpleFeature.getDefaultGeometry();
//
//					double shortestDistance = Double.MAX_VALUE;
//					Geometry closestGeometry = null;
//
//					double distanceToClosestAccessPoint = CoordUtils.calcEuclideanDistance(homeCoord, MGC.point2Coord((Point) simpleFeature));
//					if (distanceToClosestAccessPoint < shortestDistance) {
//						shortestDistance = distanceToClosestAccessPoint;
//						closestGeometry = defaultGeometry;
//					}
//
//
//					String name = (String) simpleFeature.getAttribute("osm_id");
//
//					System.out.println("OSM-ID der Grünfläche: " + name + "Distanz: " + shortestDistance);
//
//					CSVWriter writer = new CSVWriter("greenSpace_stats_perAgent.csv");
//					writer.writeNewLine();
//
//				}
			}

		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		List<Coord> activityCoords = new ArrayList<>();


		//   PreparedGeometry flaeche = gruenflachen.get(0);

		//   flaeche.contains(flaeche.getGeometry());







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

