package org.matsim.analysis;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.contrib.accessibility.run.RunAccessibilityExample;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.legacy.run.RunBerlinScenario;
import picocli.CommandLine;
import org.matsim.contrib.accessibility.*;
import org.matsim.contrib.accessibility.utils.VisualizationUtils;

import java.nio.file.Path;
import java.util.List;


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

public class AgentBasedGreenSpaceAnalysis implements MATSimAppCommand {

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
			run(scenario);
		} else {
			throw new RuntimeException("No config.xml file provided. The config file needs to reference a network file and a facilities file.");
		}

	}

	@Override
	public Integer call() throws Exception {

		//String
		Path inputPersonsCSVPath = Path.of(input.getPath("berlin-v6.3.output_persons.csv.gz"));
		Path outputPersonsCSVPath = output.getPath("greenSpace_stats_perAgent.csv");

//		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
//		acg.setTimeOfDay((8 * 60. + 5.) * 60.); // Beispiel: 8:05 Uhr
//		acg.setTileSize_m(5000); // Größe der Rasterzellen in Metern
//		acg.setAreaOfAccessibilityComputation(AccessibilityConfigGroup.AreaOfAccesssibilityComputation.fromShapeFile);
//		acg.setShapeFileCellBasedAccessibility("<path_to_shapefile>");
//		acg.setComputingAccessibilityForMode(Modes4Accessibility.pt, true); // Beispiel: Berechnung für öffentlichen Verkehr
//		acg.setOutputCrs(config.global().getCoordinateSystem()); // Koordinatensystem für die Ausgabe




		return 0;
	}

	private static final Logger LOG = LogManager.getLogger(RunAccessibilityExample.class);

	public static void run(Scenario scenario) {
		List<String> activityTypes = AccessibilityUtils.collectAllFacilityOptionTypes(scenario);
		LOG.info("The following activity types were found: " + String.valueOf(activityTypes));
		Controler controler = new Controler(scenario);

		for(String actType : activityTypes) {
			AccessibilityModule module = new AccessibilityModule();
			module.setConsideredActivityType(actType);
			controler.addOverridingModule(module);
		}

		controler.run();
	}
}
