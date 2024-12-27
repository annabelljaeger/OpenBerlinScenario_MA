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

	public double priority(){return -1;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "green space";
		header.description = "Detailed green space Analysis";

		layout.row("overall ranking result loss time")
			.el(Bar.class, (viz, data) -> {

				viz.title = "Green Space Ranking Value";
				viz.description = "test file";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv");
				viz.height = 0.1;

			});

		layout.row("overall ranking result green space")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Green Space Ranking Value";
				viz.description = "first try";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_RankingValue.csv");
				viz.height = 0.1;

			});
	}
}
