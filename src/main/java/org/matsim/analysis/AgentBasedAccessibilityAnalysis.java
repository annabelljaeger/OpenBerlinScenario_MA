package org.matsim.analysis;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import picocli.CommandLine;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

@CommandLine.Command(
	name = "accessibility-analysis",
	description = "PT-quality and Accessibility Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	requires = {
		"berlin-v6.3.output_trips.csv"
	},
	produces = {
		"accessibility_stats_perAgent.csv",
		"ptQuality_stats_perAgent.csv",
		"reisezeitvergleich.csv",
		"ptAccessibility_RankingValue.csv"
	}
)

public class AgentBasedAccessibilityAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedAccessibilityAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedAccessibilityAnalysis.class);

	public static void main(String[] args) {
		new AgentBasedAccessibilityAnalysis().execute(args);

		if (args.length != 0 && args.length <= 1) {
			Config config = ConfigUtils.loadConfig(args[0], new ConfigGroup[0]);
			config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
			AccessibilityConfigGroup accConfig = (AccessibilityConfigGroup)ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
			accConfig.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, true);
			Scenario scenario = ScenarioUtils.loadScenario(config);
		//	run(scenario);
		} else {
			throw new RuntimeException("No config.xml file provided. The config file needs to reference a network file and a facilities file.");
		}

	}
	private static Path runDirectory = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");


	@Override
	public Integer call() throws Exception {

		Path tripsCsvPath = ApplicationUtils.matchInput("trips.csv", runDirectory);

		Path outputReisezeitvergleichPath = output.getPath("reisezeitvergleich.csv");
		Path rankingValuePath = output.getPath("ptAccessibility_RankingValue.csv");

		//CSVParser scheint bessere Lösung zu sein! Hier aber die Befehle hinten veraltet.. prüfen und code entsprechend umstellen!
		try(CSVParser parser = CSVParser.parse(new FileReader(String.valueOf(tripsCsvPath)), CSVFormat.DEFAULT.withFirstRecordAsHeader().withDelimiter(';'))) {
			for (CSVRecord record : parser) {

				String personId = record.get("person");
				String travTime = record.get("trav_time");

				System.out.println(personId + " " + travTime);
			}
		}

		try (//GZIPInputStream gzipInputStream = new GZIPInputStream(Files.newInputStream(tripsCsvPath));
			 //CSVReader tripsCsvReader = new CSVReader(new InputStreamReader(gzipInputStream));
			 CSVReader tripsCsvReader = new CSVReader(new FileReader(String.valueOf(tripsCsvPath)));
			 CSVWriter reisezeitvergleichsWriter = new CSVWriter(new FileWriter(outputReisezeitvergleichPath.toFile()));
			 CSVWriter ptAccessibilityRankingValueWriter = new CSVWriter(new FileWriter(rankingValuePath.toFile()));) {

			String line;
		//	tripRecord = tripsCsvReader.readNext();
			for (int i = 0; i < 600 && (line = Arrays.toString(tripsCsvReader.readNext())) != null; i++) {
//			for(; (tripRecord = tripsCsvReader.readNext()) !=null;) {

				String[] header = tripsCsvReader.readNext();

				String[] tripRecord = line.split(";");

				reisezeitvergleichsWriter.writeNext(new String[]{"person", "trip_id", "euclidean_distance", "travTime_pt", "travTime_car", "Reisezeitvergleich"});
				String personId = tripRecord[0];
				String tripId = tripRecord[2];
				String currentTravelTime = tripRecord[4];

				int euclideanDistance = Integer.parseInt(tripRecord[7]);
				//evtl. in for-Schleife integrieren?
				if (euclideanDistance > 300) {

					String startX = tripRecord[15];
					String startY = tripRecord[16];
					String endX = tripRecord[19];
					String endY = tripRecord[20];
					String mainMode = tripRecord[8];
					String depTime = tripRecord[3];

					Coord tripStartCoord = new Coord(Double.parseDouble(startX), Double.parseDouble(startY));
					Coord tripEndCoord = new Coord(Double.parseDouble(endX), Double.parseDouble(endY));


					if (Objects.equals(mainMode, "car")) {

						//hier fehlt: Anwendung SwissRailRaptor nur für tripStart nach tripEndCoord für depTime
//Kopie aus RunSwissRailRaptorExample:
						String configFilename = "C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.output_config.xml";
						Config config = ConfigUtils.loadConfig(configFilename);

						Scenario scenario = ScenarioUtils.loadScenario(config);
						Controler controler = new Controler(scenario);

						// This is the important line:
						controler.addOverridingModule(new SwissRailRaptorModule());

						controler.run();

					//	SwissRailRaptor
						int travTime_pt = 1;
						int travTime_car = Integer.parseInt(currentTravelTime);
						double reisezeitvergleich = (double) travTime_pt / travTime_car;
						reisezeitvergleichsWriter.writeNext(new String[] {personId, tripId, String.valueOf(euclideanDistance), String.valueOf(travTime_pt), String.valueOf(travTime_car), String.valueOf(reisezeitvergleich)});

					}

					if (Objects.equals(mainMode, "pt")){

						int travTime_car = 1;
						int travTime_pt = Integer.parseInt(currentTravelTime);
						double reisezeitvergleich = (double) travTime_pt / travTime_car;
						reisezeitvergleichsWriter.writeNext(new String[] {personId, tripId, String.valueOf(euclideanDistance), String.valueOf(travTime_pt), String.valueOf(travTime_car), String.valueOf(reisezeitvergleich)});

					}

					//auch walk, ride und bike sollten ab einer gewissen Wegelänge auf ihr MIV/ÖV Reisezeitverhältnis geprüft werden
					//ggf. nicht ab 300m sondern erst ab 1/2/3 km da darunter schon walk/bike das beste ist? Oder einfach komplett da es keinen wirklichen Grund dagegen gibt?
					else {

						//Berechnung wie in mainmode = pt
						int travTime_car = 1;
						//Berechnung wie in mainmode = car
						int travTime_pt = 1;

						double reisezeitvergleich = (double) travTime_pt / travTime_car;
						reisezeitvergleichsWriter.writeNext(new String[] {personId, tripId, String.valueOf(euclideanDistance), String.valueOf(travTime_pt), String.valueOf(travTime_car), String.valueOf(reisezeitvergleich)});

					}



				}
				else {
					reisezeitvergleichsWriter.writeNext(new String[] {personId, tripId, String.valueOf(euclideanDistance), "trip too short", "trip too short", null});
				}

				ptAccessibilityRankingValueWriter.writeNext(new String[]{personId, currentTravelTime});

			}


		}



		return 0;
	}



}
