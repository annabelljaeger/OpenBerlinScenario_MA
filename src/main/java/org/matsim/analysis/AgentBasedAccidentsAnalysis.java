package org.matsim.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accidents.AccidentsConfigGroup;
import org.matsim.contrib.accidents.AccidentsModule;
import org.matsim.contrib.accidents.runExample.AccidentsNetworkModification;
import org.matsim.contrib.accidents.runExample.RunAccidents;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;

@CommandLine.Command(
	name = "accidents-analysis",
	description = "Accidents Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
	requires = {
		"berlin-v6.3.output_persons.csv",
	//	"BVWPAccidentsRoadType.shp"
	},
	produces = {
		"accidents_stats_perAgent.csv",
		"accidents_RankingValue.csv"
	}
)

public class AgentBasedAccidentsAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedAccidentsAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedAccidentsAnalysis.class);

	private static final Logger log = LogManager.getLogger(AgentBasedAccidentsAnalysis.class);

//	public static void main(String[] args) {
//		new AgentBasedAccidentsAnalysis().execute(args);
//	}

	@Override
	public void execute(String... args) {
		MATSimAppCommand.super.execute(args);
	}

	@Override
	public MATSimAppCommand withArgs(String... args) {
		return MATSimAppCommand.super.withArgs(args);
	}

	@Override
	public Integer call() throws Exception {

		runAccidentsContrib();


		return 0;
	}

	private static Path runDirectory = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");
	private static Path inputDirectory = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/input_OBS_Base/berlin-v6.3-10pct");

	private void runAccidentsContrib() throws MalformedURLException, IOException {
		log.info("Loading scenario...");

		//Path configPath = ApplicationUtils.matchInput("berlin-v6.3.output_config.xml", runDirectory);
		Path configPath = Path.of("C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.output_config.xml");
		Config config = ConfigUtils.loadConfig(configPath.toString());
		AccidentsConfigGroup accidentsSettings = ConfigUtils.addOrGetModule(config, AccidentsConfigGroup.class);
		accidentsSettings.setEnableAccidentsModule(true);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		AccidentsNetworkModification networkModification = new AccidentsNetworkModification(scenario);
	//	String[] tunnelLinks = this.readCSVFile("tunnelLinksCSVfile_alt");
		String[] tunnelLinks = this.readCSVFile("C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.tunnel-linkIDs_alt.csv");
		String[] planfreeLinks = this.readCSVFile("C:\\Users\\annab\\MatSim for MA\\Output_Cluster\\OBS_Base\\output_OBS_Base\\berlin-v6.3-10pct\\berlin-v6.3.planfree-linkIDs_alt.csv");
		//String[] planfreeLinks = this.readCSVFile("planfree-linkIDs_alt");

		//networkModification.setLinkAttributsBasedOnOSMFile("osmlandUseFile", "EPSG:31468", tunnelLinks, planfreeLinks);
		networkModification.setLinkAttributsBasedOnOSMFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/original-data/osmBerlin/gis.osm_landuse_a_free_1_GK4.shp", "EPSG:31468", tunnelLinks, planfreeLinks);


		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new AccidentsModule());
		controler.getConfig().controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		controler.run();
	}

	private String[] readCSVFile(String csvFile) {
		ArrayList<Id<Link>> links = new ArrayList();
		BufferedReader br = IOUtils.getBufferedReader(csvFile);
		String line;

//		try {
//			line = br.readLine();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		try {
			int countWarning = 0;

			while((line = br.readLine()) != null) {
				String[] columns = line.split(";");
				Id<Link> linkId = null;

				for(int column = 0; column < columns.length; ++column) {
					if (column == 0) {
						linkId = Id.createLinkId(columns[column]);
					} else {
						if (countWarning < 1) {
							log.warn("Expecting the link Id to be in the first column. Ignoring further columns...");
						} else if (countWarning == 1) {
							log.warn("This message is only given once.");
						}

						++countWarning;
					}
				}

				log.info("Adding link ID " + linkId);
				links.add(linkId);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		String[] linkIDsArray = links.stream()
			.filter(Objects::nonNull) // Filtert null-Werte heraus
			.map(Id::toString)       // Konvertiert Id<Link> zu String
			.toArray(String[]::new);

		return linkIDsArray;


//		// Richtiges Array erstellen
//		String[] linkIDsArray = links.stream().map(Id::toString).toArray(String[]::new);
//		return linkIDsArray;

//		String[] linkIDsArray = (String[])links.toArray();
//		return linkIDsArray;
	}


}
