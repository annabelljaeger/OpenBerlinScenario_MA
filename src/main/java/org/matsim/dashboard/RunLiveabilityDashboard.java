package org.matsim.dashboard;

import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.*;
import org.matsim.simwrapper.dashboard.EmissionsDashboard;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandSpec(
	group="liveability"
)

public class RunLiveabilityDashboard implements MATSimAppCommand {

	// input and output paths can either be provided via the command line or below as an absolute path (beginner friendly)
	@CommandLine.Option(names = "--inputDirectory", description = "Path to the input directory")
	private static Path inputDirectory;
	@CommandLine.Option(names = "--outputDirectory",description = "Path to the output directory")
	private static Path outputDirectory;
//	@CommandLine.Option(names = "--outputLiveabilityDirectory",description = "Path to the liveability output directory")
//	private static Path outputLiveabilityDirectory;

	// option to insert standard input and output (general output and liveability analysis output) paths for users
	private static final Path DEFAULT_INPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/input_OBS_Base");
//	private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/10pct.absSpdLim2.777");
	private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");
	private static final Path DEFAULT_LIVEABILITY_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/analysis/liveability");

	// public static attributes - necessary to save the valid input and output directory paths
	public static Path validInputDirectory;
	public static Path validOutputDirectory;
	public static Path validLiveabilityOutputDirectory;

	// NUR FÜR EMISSIONS CONTRIB - KLAPPT NOCH NICHT MIT ECONFIG
//	private static final String HBEFA_2020_PATH = "https://svn.vsp.tu-berlin.de/repos/public-svn/3507bb3997e5657ab9da76dbedbb13c9b5991d3e/0e73947443d68f95202b71a156b337f7f71604ae/";
//	private static final String HBEFA_FILE_COLD_DETAILED = HBEFA_2020_PATH + "82t7b02rc0rji2kmsahfwp933u2rfjlkhfpi2u9r20.enc";
//	private static final String HBEFA_FILE_WARM_DETAILED = HBEFA_2020_PATH + "944637571c833ddcf1d0dfcccb59838509f397e6.enc";
//	private static final String HBEFA_FILE_COLD_AVERAGE = HBEFA_2020_PATH + "r9230ru2n209r30u2fn0c9rn20n2rujkhkjhoewt84202.enc" ;
//	private static final String HBEFA_FILE_WARM_AVERAGE = HBEFA_2020_PATH + "7eff8f308633df1b8ac4d06d05180dd0c5fdf577.enc";


	// main method to run this class with the given input via the CommandLine or the given Paths
	public static void main(String[] args) throws IOException {
		new RunLiveabilityDashboard().execute(args);

	}

	// call method calling the config, AgentLiveabilityInfoCollection-Class and all the liveability-Dimension-Dashboards
	@Override
	public Integer call() throws Exception {

		createLiveabilityDirectory();

		new AgentLiveabilityInfoCollection().execute();

		Path configPath = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
		Config config = ConfigUtils.loadConfig(configPath.toString());
		SimWrapper sw = SimWrapper.create(config);

		//for work on code purposes to activate the two contribs - temporary
		//sw.addDashboard(new EmissionsDashboard());
		//sw.addDashboard(new NoiseDashboard());

		// calling the seperate liveability-dimension dashboards and thereby activating them
	//	sw.addDashboard( new AgentBasedLossTimeDashboard());
		//sw.addDashboard( new AgentBasedNoiseDashbaord());
		//sw.addDashboard( new AgentBasedEmissionsDashbaord());
		//sw.addDashboard( new AgentBasedSafetyDashboard());
		sw.addDashboard( new AgentBasedGreenSpaceDashboard());
		//sw.addDashboard( new AgentBasedAccessibilityDashboard());
		sw.addDashboard( new LiveabilitySummaryDashboard());

		sw.generate(getValidOutputDirectory());
		sw.run(getValidOutputDirectory());

		return 0;
	}


	// deciding whether path given as absolute path or via command line is used for the input path
	public static Path getValidInputDirectory() {
		validInputDirectory = (inputDirectory != null) ? inputDirectory : (DEFAULT_INPUT_DIRECTORY);
		return validInputDirectory;
	}

	// deciding whether path given as absolute path or via command line is used for the output path
	public static Path getValidOutputDirectory() {
		validOutputDirectory = (outputDirectory != null) ? outputDirectory : (DEFAULT_OUTPUT_DIRECTORY);
		return validOutputDirectory;
	}

	// deciding whether path given as absolute path or via command line is used for the analysis output path
	public static Path getValidLiveabilityOutputDirectory() {
		if (validLiveabilityOutputDirectory == null) {
			String groupName = RunLiveabilityDashboard.class.getAnnotation(CommandSpec.class).group();
			validLiveabilityOutputDirectory = getValidOutputDirectory().resolve("analysis/"+groupName);
		}
	return validLiveabilityOutputDirectory;
	}

	public static void createLiveabilityDirectory() throws IOException {
		Path baseOutputDirectory = getValidOutputDirectory();
		String groupName = RunLiveabilityDashboard.class.getAnnotation(CommandSpec.class).group();
		validLiveabilityOutputDirectory = baseOutputDirectory.resolve("analysis/" + groupName);
		if (!Files.exists(validLiveabilityOutputDirectory)) {
			Files.createDirectories(validLiveabilityOutputDirectory);
		}
	}

		//vorherige getValidLiveability... Methode
//	public static Path getValidLiveabilityOutputDirectory() {
//		validLiveabilityOutputDirectory = (outputLiveabilityDirectory != null) ? outputLiveabilityDirectory : (DEFAULT_LIVEABILITY_OUTPUT_DIRECTORY);
//		return validLiveabilityOutputDirectory;
//	}
}
