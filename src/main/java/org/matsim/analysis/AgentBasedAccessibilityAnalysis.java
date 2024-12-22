package org.matsim.analysis;

import org.matsim.api.core.v01.Scenario;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

@CommandLine.Command(
	name = "greenSpace-analysis",
	description = "Green Space availability and accessibility Analysis",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
//	requires = {
//		"berlin-v6.3.output_legs.csv.gz"
//	},
	produces = {
		"greenSpace_stats_perAgent.csv",
		"greenSpace_RankingValue.csv"
	}
)

public class AgentBasedAccessibilityAnalysis implements MATSimAppCommand {

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentBasedGreenSpaceAnalysis.class);

	public static void main(String[] args) {
		new AgentBasedGreenSpaceAnalysis().execute(args);

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

	@Override
	public Integer call() throws Exception {

		return 0;
	}
	}
