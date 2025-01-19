package org.matsim.dashboard;

import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RunLiveabilityDashboard implements MATSimAppCommand {

	// input and output paths can either be provided via the command line or below as an absolute path (beginner friendly)
	@CommandLine.Option(names = "--inputDirectory", description = "Path to the input directory")
	private static Path inputDirectory;
	@CommandLine.Option(names = "--outputDirectory",description = "Path to the output directory")
	private static Path outputDirectory;
	@CommandLine.Option(names = "--outputLiveabilityDirectory",description = "Path to the liveability output directory")
	private static Path outputLiveabilityDirectory;

	// option to insert standard input and output (general output and liveability analysis output) paths for users
	private static final Path DEFAULT_INPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/input_OBS_Base");
	private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");
	private static final Path DEFAULT_LIVEABILITY_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/analysis/analysis");

	// public static attributes - necessary to save the valid input and output directory paths
	public static Path validInputDirectory;
	public static Path validOutputDirectory;
	public static Path validLiveabilityOutputDirectory;


	// main method to run this class with the given input via the CommandLine or the given Paths
	public static void main(String[] args) throws IOException {
		new RunLiveabilityDashboard().execute(args);
	}

	// call method calling the config, AgentLiveabilityInfoCollection-Class and all the liveability-Dimension-Dashboards
	@Override
	public Integer call() throws Exception {

		new AgentLiveabilityInfoCollection().execute();

		Path configPath = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
		Config config = ConfigUtils.loadConfig(configPath.toString());
		SimWrapper sw = SimWrapper.create(config);

		//for work on code purposes to activate the two contribs - temporary
		//sw.addDashboard(new EmissionsDashboard());
		//sw.addDashboard(new NoiseDashboard());

		// calling the seperate liveability-dimension dashboards and thereby activating them
		//sw.addDashboard( new AgentBasedLossTimeDashboard());
		//sw.addDashboard( new AgentBasedNoiseDashbaord());
		//sw.addDashboard( new AgentBasedEmissionsDashbaord());
		//sw.addDashboard( new AgentBasedSafetyDashboard());
		sw.addDashboard( new AgentBasedGreenSpaceDashboard());
		sw.addDashboard( new AgentBasedAccessibilityDashboard());
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
		validLiveabilityOutputDirectory = (outputLiveabilityDirectory != null) ? outputLiveabilityDirectory : (DEFAULT_LIVEABILITY_OUTPUT_DIRECTORY);
		return validLiveabilityOutputDirectory;
	}
}
