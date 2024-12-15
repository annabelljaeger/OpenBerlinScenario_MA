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

//example RunSimwrappercontribOfflineExample used and adapted

public class RunLiveabilityDashboard {
	private static Path runDirectory = Paths.get("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct");

	//Rufe Klassen Übersicht + Unter-Dashboards 2-7 auf

	public static void main( String[] args ) throws IOException{

		Path configPath = ApplicationUtils.matchInput("config.xml", runDirectory);
		Config config = ConfigUtils.loadConfig(configPath.toString());
		SimWrapper sw = SimWrapper.create(config);

	//	new AgentLiveabilityInfo().execute(args);
		new AgentLiveabilityInfo().execute(new String[]{
			"--input", runDirectory.toString(), // Übergabe des Eingabeverzeichnisses
			"--output", runDirectory.resolve("analysis/analysis/liveabilityInfo.csv").toString() // Übergabe des Ausgabepfads
		});
		/*
		new LiveabilitySummaryAnalysis().execute(new String[]{
			"--input", runDirectory.toString(), // Übergabe des Eingabeverzeichnisses
			"--output", runDirectory.toString() // Übergabe des Ausgabepfads
		});
*/


		//	Path liveabilityCsvPath = Paths.get(runDirectory.toString(), "liveability_info.csv");
	//	liveabilityInfo.generateLiveabilityData(runDirectory, liveabilityCsvPath);

	//	sw.addDashboard(new EmissionsDashboard());
	//	sw.addDashboard(new NoiseDashboard());

		sw.addDashboard( new AgentBasedLossTimeDashboard());
		//sw.addDashboard( new AgentBasedNoiseDashbaord());
		//sw.addDashboard( new AgentBasedEmissionsDashbaord());
		//sw.addDashboard( new AgentBasedSafetyDashboard());
		//sw.addDashboard( new AgentBasedGreenSpaceDashboard());
		//sw.addDashboard( new AgentBasedPtQualityDashboard());
		sw.addDashboard( new LiveabilitySummaryDashboard());

//		sw.run( Path.of("./output" ) );

		sw.generate(runDirectory);
		sw.run(runDirectory);

		// now: open simwrapper in Chrome, allow the "dashboard" local directory, point simwrapper to it.
		// It fails, possibly because "file.csv" is not there.

	}

}
