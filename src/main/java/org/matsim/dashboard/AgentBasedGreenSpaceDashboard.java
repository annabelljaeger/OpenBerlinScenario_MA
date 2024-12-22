package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.analysis.AgentBasedGreenSpaceAnalysisTest;
import org.matsim.analysis.AgentBasedLossTimeAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Bar;
import org.matsim.simwrapper.viz.Tile;

public class AgentBasedGreenSpaceDashboard implements Dashboard {
	@Override
	public void configure(Header header, Layout layout) {

		header.title = "green space";
		header.description = "Detailed green space Analysis";

		layout.row("overall ranking result loss time")
			.el(Bar.class, (viz, data) -> {

				viz.title = "Green Space Ranking Value";
				viz.description = "test file";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysisTest.class, "greenSpace_stats_perAgent.csv");
				viz.height = 0.1;

			});
	}
}
