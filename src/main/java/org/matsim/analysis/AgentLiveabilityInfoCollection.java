package org.matsim.analysis;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.geotools.api.data.FileDataStore;
import org.geotools.api.data.FileDataStoreFinder;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.WKTReader;
import org.matsim.api.core.v01.Coord;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.Dependency;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.gis.PointFeatureFactory;
import picocli.CommandLine;

import org.geotools.data.*;
import org.geotools.data.simple.*;
import org.apache.commons.csv.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;

@CommandLine.Command(
	name = "Utility - Agent Liveability Info Collection",
	description = "create agentLiveabilityInfo.csv, summaryTiles.csv and indexIndicatorValues.csv to collect the liveability values from the different dimensions",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	group="liveability",
//	dependsOn = {
//		@Dependency(value = AgentBasedGreenSpaceAnalysis.class, files =  "greenSpace_stats_perAgent.csv"),
//	},
	produces = {
		"overall_stats_agentLiveabilityInfo.csv",
		"overall_tiles_summaryPerIndex.csv",
		"overall_stats_indexIndicatorValues.csv"
	}
)

/**
 * utility class to generate csv-Files and write methods to extend those generated files for each dimension-analysis class
 */
public class AgentLiveabilityInfoCollection implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentLiveabilityInfoCollection.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentLiveabilityInfoCollection.class);

	// defining constants for paths
	private final Path outputAgentLiveabilityCSVPath = getValidLiveabilityOutputDirectory().resolve("overall_stats_agentLiveabilityInfo.csv");
	private final Path tempAgentLiveabilityOutputPath = getValidLiveabilityOutputDirectory().resolve("overall_stats_agentLiveabilityInfo_tmp.csv");

	private final Path outputIndicatorValuesCsvPath = getValidLiveabilityOutputDirectory().resolve("overall_stats_indexIndicatorValues.csv");
	private final Path tempIndicatorValuesCsvPath = getValidLiveabilityOutputDirectory().resolve("overall_stats_indexIndicatorValues_tmp.csv");

	//	private final Path personsCsvPath = getValidOutputDirectory().resolve("berlin-v6.3.output_persons.csv.gz");
	private final Path personsCsvPath = ApplicationUtils.matchInput("output_persons.csv.gz", getValidOutputDirectory());
	private final Path studyAreaShpPath = ApplicationUtils.matchInput("studyArea.shp", getValidInputDirectory());

	private final Path outputCategoryRankingCsvPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_summaryPerIndex.csv");
	private final Path tempSummaryTilesOutputPath = getValidLiveabilityOutputDirectory().resolve("overall_tiles_summaryPerIndex_tmp.csv");

	private Geometry studyAreaGeometry;
	private final GeometryFactory geometryFactory = new GeometryFactory();

	// method generates the csv-Files - the methods to extend the files are called in the dimension analysis classes
	@Override
	public Integer call() throws Exception {

		loadStudyArea(studyAreaShpPath.toString());
		generateLiveabilityData();

		generateSummaryTilesFile();

		generateIndicatorFile();

		return 0;
	}

	// method to introduce the agentLiveabilityInfo.csv and fill it with the person ids from the persons.csv.gz file in the output folder
	private void generateLiveabilityData( ) throws IOException {

		if (!Files.exists(personsCsvPath)) {
			throw new IOException("Die Datei output_persons.csv.gz wurde nicht gefunden: " + personsCsvPath);
		}

		try (InputStream fileStream = new FileInputStream(personsCsvPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader personsReader = new InputStreamReader(gzipStream);
			 CSVParser personsParser = new CSVParser(personsReader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter agentLiveabilityWriter = new CSVWriter(new FileWriter(outputAgentLiveabilityCSVPath.toFile()),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			agentLiveabilityWriter.writeNext(new String[]{"person", "home_x", "home_y"});

			for (CSVRecord record : personsParser) {
				String person = record.get("person");
				String homeX = (record.get("home_x"));
				String homeY = (record.get("home_y"));

				System.out.println("Prüfe Person " + person + " mit Koordinaten: (" + homeX + ", " + homeY + ")");


				if (homeX != null && !homeX.isEmpty() && homeY != null && !homeY.isEmpty()) {
					try {
						double x = Double.parseDouble(homeX);
						double y = Double.parseDouble(homeY);
						if (isInsideStudyArea(x, y)) {
							agentLiveabilityWriter.writeNext(new String[]{person, homeX, homeY});
						}
					} catch (NumberFormatException e) {
						System.err.println("Ungültige Koordinaten für Person " + person + ": " + homeX + ", " + homeY);
					}

//						 double x = Double.parseDouble(homeX);
//						 double y = Double.parseDouble(homeY);
//						 Coord coord = new Coord(x, y);
//						 Coordinate coordinate = new Coordinate(coord.getX(), coord.getY());
//
//						 if (isInsideStudyArea(coordinate)) {
//							 agentLiveabilityWriter.writeNext(new String[]{person, String.valueOf(homeX), String.valueOf(homeY)});
//						 }

//						 // not universally transferable for other regions yet!
//						 String pointInStudyArea = record.get("RegioStaR7");
//
//						 // the Liveability-Ranking is only useful for a defined study area, which in this case is the boarder of Berlin. In this case, this includes all points in the RegioStaR7 = 1 area.
//						 if ("1".equals(pointInStudyArea)) {
//							 agentLiveabilityWriter.writeNext(new String[]{person, homeX, homeY});
//						 }
				}
			}
		}
		System.out.println("Liveability-CSV generated under: " + outputAgentLiveabilityCSVPath);
	}

	// method to extend the agentLiveabilityInfo.csv file with agent based information from the analysis classes of the dimensions (this is where they are called).
	// Data has to be provided as maps - those are used throughout the code to guarantee agent based values and the mapping of those to the universal peron ids.
	public void extendAgentLiveabilityInfoCsvWithAttribute(Map<String, Double> additionalData, String newAttributeName) throws IOException {

		try (CSVReader personEntryReader = new CSVReader(new FileReader(outputAgentLiveabilityCSVPath.toFile()));
			 CSVWriter valueWriter = new CSVWriter(new FileWriter(tempAgentLiveabilityOutputPath.toFile()),
				 CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotation
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			//read header
			String[] header = personEntryReader.readNext();
			if (header == null) {
				throw new IOException("The persons input csv is empty.");
			}

			// add new column for the header extracted from the input coming from the analysis classes
			String[] newHeader = new String[header.length + 1];
			System.arraycopy(header, 0, newHeader, 0, header.length);
			newHeader[header.length] = newAttributeName;
			valueWriter.writeNext(newHeader);
			System.out.println("Spaltenname geschrieben: " + newAttributeName);

			// extends each person line with the content from the input in a next column
			String[] line;
			while ((line = personEntryReader.readNext()) != null) {
				String personKey = line[0];

				// get new attribute values from the map
				Object value = additionalData.containsKey(personKey)? additionalData.get(personKey):"";
				String formattedValue = (value != null) ? value.toString() : "";

				// add new column to the line
				String[] newLine = new String[line.length + 1];
				System.arraycopy(line, 0, newLine, 0, line.length);
				newLine[line.length] = formattedValue;

				valueWriter.writeNext(newLine);
			}

		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempAgentLiveabilityOutputPath, outputAgentLiveabilityCSVPath, StandardCopyOption.REPLACE_EXISTING);
	}

	// generates an empty summaryTiles.csv file for information from each analysis to be added
	private void generateSummaryTilesFile() throws IOException {

		// if file exists: delete
		if (Files.exists(outputCategoryRankingCsvPath)) {
			Files.delete(outputCategoryRankingCsvPath);
			System.out.println("The file summaryTiles.csv exists already and has been replaced.");
		}

		// generate and initialize empty/placeholder-filled csv-file
		try (CSVWriter writer = new CSVWriter(new FileWriter(outputCategoryRankingCsvPath.toFile()))) {
			writer.writeNext(new String[]{"Warum ist hier nichts? :( - CSV angelegt aber kein Inhalt hinzugefügt"});

			System.out.println("The prepared file Datei summaryTiles.csv has been generated: " + outputCategoryRankingCsvPath);
		}
	}

	// method to extend the summaryTiles.csv file with agent based information from the analysis classes of the dimensions (this is where they are called)
	public void extendSummaryTilesCsvWithAttribute(String RankingValue, String CategoryName) throws IOException {

		try (CSVReader tilesReader = new CSVReader(new FileReader(outputCategoryRankingCsvPath.toFile()));
			 CSVWriter tilesWriter = new CSVWriter(new FileWriter(tempSummaryTilesOutputPath.toFile()))) {

			String[] nextLine;
			while ((nextLine = tilesReader.readNext()) != null) {
				if (String.join(";", nextLine).contains("Warum ist hier nichts? :(")) {
					continue;
				}
				tilesWriter.writeNext(nextLine);
			}

			tilesWriter.writeNext(new String[]{CategoryName, RankingValue});

		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempSummaryTilesOutputPath, outputCategoryRankingCsvPath, StandardCopyOption.REPLACE_EXISTING);
	}

	// generates an empty indexIndicatorValues.csv file for information from each analysis to be added
	private void generateIndicatorFile() throws IOException {

		// delete file if already existent
		if (Files.exists(outputIndicatorValuesCsvPath)) {
			Files.delete(outputIndicatorValuesCsvPath);
			System.out.println("The file indexIndicatorValues.csv already exists and has been replaced.");
		}

		// generating the empty csv-file with a prefilled header
		try (CSVWriter indicatorTableWriter =  new CSVWriter(new FileWriter(String.valueOf(outputIndicatorValuesCsvPath)),
			CSVWriter.DEFAULT_SEPARATOR,
			CSVWriter.NO_QUOTE_CHARACTER, // without quotations
			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
			CSVWriter.DEFAULT_LINE_END)) {

			indicatorTableWriter.writeNext(new String[]{"dimension","indicator","median value","limit","ranking value","weight of indicator"});

			System.out.println("The empty file indexIndicatorValues.csv has been generated under: " + outputIndicatorValuesCsvPath);
		}
	}

	// method to extend the indexIndicatorValues.csv file with agent based information from the analysis classes of the dimensions (this is where they are called)
	public void extendIndicatorValuesCsvWithAttribute(String dimension, String indicator, String medianValue, String limit, String RankingValue, double weightOfIndicator) throws IOException {

		try (CSVReader indicatorReader = new CSVReader(new FileReader(outputIndicatorValuesCsvPath.toFile()));
			 CSVWriter indicatorWriter = new CSVWriter(new FileWriter(tempIndicatorValuesCsvPath.toFile()),  CSVWriter.DEFAULT_SEPARATOR,
				 CSVWriter.NO_QUOTE_CHARACTER, // without quotations
				 CSVWriter.DEFAULT_ESCAPE_CHARACTER,
				 CSVWriter.DEFAULT_LINE_END)) {

			String[] line;
			while ((line = indicatorReader.readNext()) != null) {
				indicatorWriter.writeNext(line);
			}
			indicatorWriter.writeNext(new String[]{dimension, indicator, medianValue, limit, RankingValue, String.valueOf(weightOfIndicator)});


		} catch (CsvValidationException e) {
			throw new RuntimeException(e);
		}
		// rewrite original file by temp file
		Files.move(tempIndicatorValuesCsvPath, outputIndicatorValuesCsvPath, StandardCopyOption.REPLACE_EXISTING);

	}

	private void loadStudyArea(String shapefilePath) throws IOException {
		File file = new File(shapefilePath);
		FileDataStore store = FileDataStoreFinder.getDataStore(file);
		SimpleFeatureSource featureSource = store.getFeatureSource();
		SimpleFeatureCollection featureCollection = featureSource.getFeatures();

		try (FeatureIterator<SimpleFeature> features = featureCollection.features()) {
			if (features.hasNext()) {
				SimpleFeature feature = features.next();
				studyAreaGeometry = (Geometry) feature.getDefaultGeometry();
			}
		}

		System.out.println("Geladene Study Area Geometrie: " + studyAreaGeometry);

		//PointFeatureFactory pointFactoryBuilder = new PointFeatureFactory.Builder().setCrs(CRS.decode(config.global().getCoordinateSystem)).create();
	}

	private boolean isInsideStudyArea(double x, double y) {
		Point point = geometryFactory.createPoint(new Coordinate(x, y));

//		private boolean isInsideStudyArea(Coordinate coordinate) {
//
//			Point point = geometryFactory.createPoint(coordinate);
		boolean inside = studyAreaGeometry != null && studyAreaGeometry.contains(point);
		System.out.println("Person an (" + x + ", " + y + ") ist " + (inside ? "innerhalb" : "außerhalb") + " der Study Area.");
		return inside;


		//	return studyAreaGeometry != null && studyAreaGeometry.contains(point);

	}

	public static void writeXYTDataToCSV(Path filePath, Map<String, Double> dataMap,
										 Map<String, List<String>> coordinatesMap) throws IOException {
		try (CSVWriter writer = new CSVWriter(new FileWriter(String.valueOf(filePath)),
			CSVWriter.DEFAULT_SEPARATOR,
			CSVWriter.NO_QUOTE_CHARACTER,
			CSVWriter.DEFAULT_ESCAPE_CHARACTER,
			CSVWriter.DEFAULT_LINE_END)) {

			// Write Heading
			writer.writeNext(new String[]{"# EPSG:25832"});
			writer.writeNext(new String[]{"time", "x", "y", "value"});

			// Write Data
			for (Map.Entry<String, Double> entry : dataMap.entrySet()) {
				String agentName = entry.getKey();
				List<String> coordinates = coordinatesMap.get(agentName);

				if (coordinates != null && coordinates.size() >= 2) {
					writer.writeNext(new String[]{
						String.valueOf(0.0),
						coordinates.get(0), // X-Coordinate
						coordinates.get(1), // Y-Coordinate
						String.valueOf(entry.getValue()) // Value
					});
				}
			}
		}
	}

	public static double calculateMedian(Map<String, Double> values) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("Map is empty");
		}
		List<Double> sortedValues = new ArrayList<>(values.values());
		Collections.sort(sortedValues);

		int size = sortedValues.size();
		if (size % 2 == 0) {
			// Average of the two middle values with an even number
			return (sortedValues.get(size / 2 - 1) + sortedValues.get(size / 2)) / 2.0;
		} else {
			// The average value for an odd number
			return sortedValues.get(size/2);
		}
	}

}
