package org.matsim.dashboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.*;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandSpec(
	group="liveability"
)

public class RunLiveabilityDashboard implements MATSimAppCommand {

	// input and output paths can either be provided via the command line or below as an absolute path (beginner-friendly)
	@CommandLine.Option(names = "--inputDirectory", description = "Path to the input directory")
	private static Path inputDirectory;
	@CommandLine.Option(names = "--outputDirectory",description = "Path to the output directory")
	private static Path outputDirectory;

	// option to insert standard input and output paths for users
	private static final Path DEFAULT_INPUT_DIRECTORY = Paths.get("Insert your input path here");
	private static final Path DEFAULT_OUTPUT_DIRECTORY = Paths.get("Insert your output path here");

	// public static attributes - necessary to save the valid input and output directory paths
	public static Path validInputDirectory;
	public static Path validOutputDirectory;
	public static Path validLiveabilityOutputDirectory;

	private static final Logger log = LogManager.getLogger(RunLiveabilityDashboard.class);

	// main method to run this class with the given input via the CommandLine or the given Paths
	public static void main(String[] args) throws IOException {
		new RunLiveabilityDashboard().execute(args);
	}

	// call method calling the config, AgentLiveabilityInfoCollection-Class and all the liveability-Dimension-Dashboards
	@Override
	public Integer call() throws Exception {

		log.info("Starting RunLiveabilityDashboard");

		createLiveabilityDirectory();

		new AgentLiveabilityInfoCollection().execute();

		Path configPath = ApplicationUtils.matchInput("config.xml", getValidOutputDirectory());
		Config config = ConfigUtils.loadConfig(configPath.toString());
		SimWrapper sw = SimWrapper.create(config);

		// calling the seperate liveability-dimension dashboards and thereby activating them
		sw.addDashboard( new AgentBasedTrafficQualityDashboard());
		sw.addDashboard( new AgentBasedPtQualityDashboard());
		sw.addDashboard( new AgentBasedGreenSpaceDashboard());

		// todo: implement safety, noise and emissions based on the contribs but with agent-specific output
		//sw.addDashboard( new AgentBasedNoiseDashboard());
		//sw.addDashboard( new AgentBasedEmissionsDashboard());
		//sw.addDashboard( new AgentBasedSafetyDashboard());

		sw.addDashboard( new LiveabilitySummaryDashboard());

		sw.generate(getValidOutputDirectory());
		sw.run(getValidOutputDirectory());

		log.info("RunLiveabilityDashboard completed.");

		return 0;
	}

	/**
	* deciding whether path given as absolute path or via command line is used for the input path
	 */
	public static Path getValidInputDirectory() {
		validInputDirectory = (inputDirectory != null) ? inputDirectory : (DEFAULT_INPUT_DIRECTORY);
		return validInputDirectory;
	}

	/**
	 * deciding whether path given as absolute path or via command line is used for the output path
	 */
	public static Path getValidOutputDirectory() {
		validOutputDirectory = (outputDirectory != null) ? outputDirectory : (DEFAULT_OUTPUT_DIRECTORY);
		return validOutputDirectory;
	}


	/**
	 * deciding whether path given as absolute path or via command line is used for the analysis output path
	 */
	public static Path getValidLiveabilityOutputDirectory() {
		if (validLiveabilityOutputDirectory == null) {
			String groupName = RunLiveabilityDashboard.class.getAnnotation(CommandSpec.class).group();
			validLiveabilityOutputDirectory = getValidOutputDirectory().resolve("analysis/"+groupName);
		}
	return validLiveabilityOutputDirectory;
	}

	/**
	 * creates liveability directory
	 */
	public static void createLiveabilityDirectory() throws IOException {
		Path baseOutputDirectory = getValidOutputDirectory();
		String groupName = RunLiveabilityDashboard.class.getAnnotation(CommandSpec.class).group();
		validLiveabilityOutputDirectory = baseOutputDirectory.resolve("analysis/" + groupName);
		if (!Files.exists(validLiveabilityOutputDirectory)) {
			Files.createDirectories(validLiveabilityOutputDirectory);
		}
	}
}
