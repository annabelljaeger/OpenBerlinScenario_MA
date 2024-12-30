package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedLossTimeAnalysis;
import org.matsim.analysis.AgentLiveabilityInfo;
import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.*;
import org.matsim.simwrapper.dashboard.EmissionsDashboard;
import org.matsim.simwrapper.dashboard.NoiseDashboard;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RunLiveabilityDashboard implements MATSimAppCommand {

	@CommandLine.Option(names = "--inputDirectory", description = "Path to the input directory")
	private static Path inputDirectory;
	@CommandLine.Option(names = "--outputDirectory",description = "Path to the output directory")
	private static Path outputDirectory;

	// option to insert standard input and output paths for users
	private static final Path DEFAULT_INPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/input_OBS_Base");
	private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");

	// public static attributes - necessary to save the valid input and output directory paths
	public static Path validInputDirectory;
	public static Path validOutputDirectory;

	// main method to run this class with the given input via the CommandLine
	public static void main(String[] args) throws IOException {
		new RunLiveabilityDashboard().execute(args);
	}

	// call method calling the config, AgentLiveabilityInfo-Class and all the liveability-Dimension-Dashboards
	@Override
	public Integer call() throws Exception {
		Path configPath = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
		Config config = ConfigUtils.loadConfig(configPath.toString());
		SimWrapper sw = SimWrapper.create(config);

		new AgentLiveabilityInfo().execute();

		//for work on code purposes to activate the two contribs
	//	sw.addDashboard(new EmissionsDashboard());
	//	sw.addDashboard(new NoiseDashboard());

		// calling the seperate liveability-dimension dashboards and thereby activating them
		//sw.addDashboard( new AgentBasedLossTimeDashboard());
		//sw.addDashboard( new AgentBasedNoiseDashbaord());
		//sw.addDashboard( new AgentBasedEmissionsDashbaord());
		//sw.addDashboard( new AgentBasedSafetyDashboard());
		//sw.addDashboard( new AgentBasedGreenSpaceDashboard());
		//sw.addDashboard( new AgentBasedAccessibilityDashboard());
		sw.addDashboard( new LiveabilitySummaryDashboard());

		sw.generate(getValidOutputDirectory());
		sw.run(getValidOutputDirectory());

		return 0;
	}

	public static Path getValidInputDirectory() {
		// Standardpfad verwenden, falls noch nicht gesetzt
		validInputDirectory = (inputDirectory != null) ? inputDirectory : (DEFAULT_INPUT_DIRECTORY);
		return validInputDirectory;
	}

	public static Path getValidOutputDirectory() {
		// Standardpfad verwenden, falls noch nicht gesetzt
		validOutputDirectory = (outputDirectory != null) ? outputDirectory : (DEFAULT_OUTPUT_DIRECTORY);
		return validOutputDirectory;
	}
}
