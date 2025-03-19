package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedPtQualityAnalysis;
import org.matsim.analysis.AgentBasedTrafficQualityAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Tile;
import org.matsim.simwrapper.viz.XYTime;

public class AgentBasedPtQualityDashboard implements Dashboard {

	public double priority() {return -2;}

	public void configure(Header header, Layout layout) {

		header.title = "PT Quality and Accessibility";
		header.description = "1. PT Quality (Reisezeitverhältnis ÖV/ MIV and 2. Accessibility per Agent According to the grid";

		layout.row("PT Quality Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "PT Quality Overall Index Value Map";
				viz.description = "Here you can see the agents according to their public transport quality index values depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_XYT_travelTimeComparisonPerAgent.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

		layout.row("overall ranking results accessibillity")
			.el(Tile.class, (viz, data) -> {

				viz.title = "PT Quality and Accessibility Ranking Value";
				viz.description = "...";

				viz.dataset = data.compute(AgentBasedPtQualityAnalysis.class, "ptQuality_stats_RankingValue.csv");
				viz.height = 0.1;
			});

	}

}
