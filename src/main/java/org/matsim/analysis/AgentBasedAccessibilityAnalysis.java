package org.matsim.analysis;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

import static org.apache.commons.lang3.BooleanUtils.TRUE;

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

	public static void main(String[] args) {
		new AgentBasedAccessibilityAnalysis().execute(args);

//		if (args.length != 0 && args.length <= 1) {
//			Config config = ConfigUtils.loadConfig(args[0], new ConfigGroup[0]);
//			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
//			AccessibilityConfigGroup accConfig = (AccessibilityConfigGroup) ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
//			accConfig.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, true);
//			Scenario scenario = ScenarioUtils.loadScenario(config);
//			//	run(scenario);
//		} else {
//			throw new RuntimeException("No config.xml file provided. The config file needs to reference a network file and a facilities file.");
//		}

	}

	private static Path runDirectory = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");


	@Override
	public Integer call() throws Exception {

		//Define input and output paths
		Path tripsCsvPath = ApplicationUtils.matchInput("trips.csv.gz", runDirectory);

		Path outputReisezeitvergleichPath = output.getPath("reisezeitvergleich_perTrip.csv");
		Path outputRankingValuePath = output.getPath("ptAndAccessibility_RankingValue.csv");

		//use CSVParser for reading the trips.csv.gz file and CSVWriter for writing the output files
		try (InputStream fileStream = new FileInputStream(tripsCsvPath.toFile());
			 InputStream gzipStream = new GZIPInputStream(fileStream);
			 Reader reader = new InputStreamReader(gzipStream);
			 CSVParser tripParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'));
			 CSVWriter reisezeitVergleichsWriter = new CSVWriter(new FileWriter(outputReisezeitvergleichPath.toFile()));
			 CSVWriter ptQualityStatsWriter = new CSVWriter(new FileWriter(outputRankingValuePath.toFile()))) {

			//Write headers of the output-CSV-Files
			reisezeitVergleichsWriter.writeNext(new String[]{"person", "trip_id", "supertrip_id", "euclidean_distance", "currentMainMode", "travTime_pt", "travTime_car", "Reisezeitvergleich", "tripTravTimeComparisonRankingValue"});
			ptQualityStatsWriter.writeNext(new String[]{"dimension", "rankingValue"});

			//read trips input file line by line, extract the content and use for iterations over all trips (instead of other for or while-loop)
			for (CSVRecord tripRecord : tripParser) {

				String personId = tripRecord.get("person");
				String tripId = tripRecord.get("trip_id");
				String currentTravTime = tripRecord.get("trav_time");
				int euclideanDistance = Integer.parseInt(tripRecord.get("euclidean_distance"));
				String mainMode = tripRecord.get("main_mode");


				//sort out all very short trips as they should be neither using car nor pt but walk - pt routing will most likely not be successful, leading to car vs. walk which makes little sense
				if (euclideanDistance > 300) {

					String startX = tripRecord.get("start_x");
					String startY = tripRecord.get("start_y");
					String endX = tripRecord.get("end_x");
					String endY = tripRecord.get("end_y");
					String depTime = tripRecord.get("dep_time");

					Coord tripStartCoord = new Coord(Double.parseDouble(startX), Double.parseDouble(startY));
					Coord tripEndCoord = new Coord(Double.parseDouble(endX), Double.parseDouble(endY));

					int travTimePt = 1;
					int travTimeCar = 1;

					if (Objects.equals(mainMode, "car")) {

						travTimeCar = timeToSeconds(currentTravTime);


						//routing for pt with SwissRailRaptor (from tripStartCoord to tripEndCoord for depTime)
						travTimePt = calculatePtTravelTime(tripStartCoord, tripEndCoord, depTime);

					} else if (Objects.equals(mainMode, "pt")) {

						travTimePt = timeToSeconds(currentTravTime);

						//wie route ich Autos im Simulationsverkehr für gesetzte Start-Ziel-Verbindnugen zu einer festgelegten Zeit?? So muss ich hier vorgehen
						//ggf. als Platzhalter routing im leeren Netz aber das wäre komplett falsch als Annahme für die finale Version
						travTimeCar = calculateCarTravelTime(tripStartCoord, tripEndCoord, depTime);


						//auch walk, ride und bike sollten ab einer gewissen Wegelänge auf ihr MIV/ÖV Reisezeitverhältnis geprüft werden
						//ggf. nicht ab 300m sondern erst ab 1/2/3 km da darunter schon walk/bike das beste ist? Oder einfach komplett da es keinen wirklichen Grund dagegen gibt?
					} else {
						//berechne travTimeCar wie bei mainMode = pt
						travTimeCar = calculateCarTravelTime(tripStartCoord, tripEndCoord, depTime);
						//berechne travTimePt wie bei mainMode = car
						travTimePt = calculatePtTravelTime(tripStartCoord, tripEndCoord, depTime);
					}

					//SUPERTRIPS IDENTIFIZIEREN FEHLT!!!

					double reisezeitvergleich = (double) travTimePt / travTimeCar;
					boolean tripTravTimeComparisonRankingValue;
					if (reisezeitvergleich < 2) {
						tripTravTimeComparisonRankingValue = true;
					} else {
						tripTravTimeComparisonRankingValue = false;
					}
					//Reminder Kopfzeile:
					//reisezeitVergleichsWriter.writeNext(new String[]{"person", "trip_id", "supertrip_id", "euclidean_distance", "currentMainMode", "travTime_pt", "travTime_car", "Reisezeitvergleich", "tripTravTimeComparisonRankingValue"});
					reisezeitVergleichsWriter.writeNext(new String[]{
						personId, tripId, "fehlt", String.valueOf(euclideanDistance), mainMode, String.valueOf(travTimePt), String.valueOf(travTimeCar),
						String.format("%.2f", reisezeitvergleich), String.valueOf(tripTravTimeComparisonRankingValue)
					});


				} else {
					reisezeitVergleichsWriter.writeNext(new String[]{
						personId, tripId, "fehlt", String.valueOf(euclideanDistance), mainMode, "trip too short", "trip too short", null, TRUE});
				}

			}

			//BERECHNUNG FEHLT!!!
			double travelTimeComparisonRankingValue = 5;
			String formattedTravelTimeComparisonRankingValue = "5";
			ptQualityStatsWriter.writeNext(new String[]{"TravelTimeComparison", formattedTravelTimeComparisonRankingValue});
		}

		return 0;
	}

	//METHODE MUSS NOCH GESCHRIEBEN WERDEN!!!
	private int calculatePtTravelTime(Coord start, Coord end, String time) {

		//Kopie aus RunSwissRailRaptorExample:
//					String configFilename = "C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.output_config.xml";
//					Config config = ConfigUtils.loadConfig(configFilename);
//
//					Scenario scenario = ScenarioUtils.loadScenario(config);
//					Controler controler = new Controler(scenario);
//
//					// This is the important line:
//					controler.addOverridingModule(new SwissRailRaptorModule());
		//	controler.run();
//Chat-GPT Alternative zu den zwei vorherigen Zeilen;
		// SwissRailRaptor-Modul hinzufügen
		//   controler.addOverridingModule(new TransitRouterModule());
		return 99;
	}

	//METHODE MUSS NOCH GESCHRIEBEN WERDEN!!!
	private int calculateCarTravelTime(Coord start, Coord end, String time) {
		return 99;
	}

	public static int timeToSeconds(String time) {
		// Parse the input time string into a LocalTime object
		LocalTime localTime = LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss"));

		// Calculate seconds since midnight
		return localTime.toSecondOfDay();
	}
}

