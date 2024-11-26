package org.matsim.dashboard;

import org.matsim.simwrapper.*;
import java.io.IOException;
import java.nio.file.Path;

//example RunSimwrappercontribOfflineExample used and adapted

public class RunLiveabilityDashboard {

	//Rufe Klassen Übersicht + Unter-Dashboards 2-7 auf

	public static void main( String[] args ) throws IOException{

		SimWrapper sw = SimWrapper.create();

	//	sw.addDashboard( new AgentBasedLossTimeDashboard());
		//sw.addDashboard( new AgentBasedNoiseDashbaord());
		//sw.addDashboard( new AgentBasedEmissionsDashbaord());
		//sw.addDashboard( new AgentBasedSafetyDashboard());
		//sw.addDashboard( new AgentBasedGreenSpaceDashboard());
		//sw.addDashboard( new AgentBasedPtQualityDashboard());
		sw.addDashboard( new LiveabilitySummaryDashboard());

//		sw.run( Path.of("./output" ) );

		sw.generate( Path.of("dashboard") );

		// now: open simwrapper in Chrome, allow the "dashboard" local directory, point simwrapper to it.
		// It fails, possibly because "file.csv" is not there.

	}




}
