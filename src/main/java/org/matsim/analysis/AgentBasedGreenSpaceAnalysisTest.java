package org.matsim.analysis;


import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileReader;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;


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

	public class AgentBasedGreenSpaceAnalysisTest implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(org.matsim.analysis.AgentBasedGreenSpaceAnalysisTest.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(org.matsim.analysis.AgentBasedGreenSpaceAnalysisTest.class);

	public static void main(String[] args) {
		new org.matsim.analysis.AgentBasedGreenSpaceAnalysis().execute(args);

	}

	@Override
	public Integer call() throws Exception {

		Path inputPersonsCSVPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv"));
		Path outputPersonsCSVPath = output.getPath("greenSpace_stats_perAgent.csv");
		Path accessPointShpPath = Path.of(input.getPath("berlin_accessPoints.shp"));
		Path greenSpaceShpPath = Path.of(input.getPath("allGreenSpaces_min1ha.shp"));

		Collection<SimpleFeature> accessPointFeatures = GeoFileReader.getAllFeatures(
			IOUtils.resolveFileOrResource(String.valueOf(accessPointShpPath))
		);

		try (CSVReader reader = new CSVReaderBuilder(new FileReader(String.valueOf(inputPersonsCSVPath)))
			.withCSVParser(new CSVParserBuilder().withSeparator(';').build())
			.build();
			 CSVWriter writer = new CSVWriter(new FileWriter(outputPersonsCSVPath.toFile()))) {

			String[] personRecord;

			// Schreibe Header in die Output-CSV
			writer.writeNext(new String[]{"AgentID", "ClosestGreenSpace", "DistanceToGreenSpace"});

			// Überspringe Header der Eingabe-CSV
			personRecord = reader.readNext();

			while ((personRecord = reader.readNext()) != null) {
				String id = personRecord[0];
				String homeX = personRecord[15];
				String homeY = personRecord[16];
				String regioStar = personRecord[5];

				// Validierung der Koordinatenwerte
				if (homeX == null || homeX.isEmpty() || homeY == null || homeY.isEmpty()) {
					System.err.println("Ungültige Koordinaten für AgentID: " + id);
					continue; // Überspringe diesen Datensatz
				}

				if (Objects.equals(regioStar, "1")) {

					try {
						Coord homeCoord = new Coord(Double.parseDouble(homeX), Double.parseDouble(homeY));

						double shortestDistance = Double.MAX_VALUE;
						String closestGeometryName = null;

						for (SimpleFeature simpleFeature : accessPointFeatures) {
							Geometry geometry = (Geometry) simpleFeature.getDefaultGeometry();

							if (geometry instanceof Point) {
								Point point = (Point) geometry;
								double distanceToAccessPoint = CoordUtils.calcEuclideanDistance(
									homeCoord,
									MGC.point2Coord(point)
								);

								if (distanceToAccessPoint < shortestDistance) {
									shortestDistance = distanceToAccessPoint;
									closestGeometryName = (String) simpleFeature.getAttribute("osm_id");
								}
							}
						}

						// Schreibe Ergebnisse in die Output-CSV
						writer.writeNext(new String[]{id, closestGeometryName, String.valueOf(shortestDistance)});

					} catch (NumberFormatException e) {
						System.err.println("Fehlerhafte Koordinaten für AgentID: " + id + " (" + homeX + ", " + homeY + ")");
					}
				}
			}
		}
		return 0;
	}
}
