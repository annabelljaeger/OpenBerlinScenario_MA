package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedGreenSpaceAnalysis;
import org.matsim.analysis.AgentLiveabilityInfoCollection;
import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.application.ApplicationUtils;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

import java.nio.file.Path;
import java.util.List;

import org.matsim.simwrapper.*;

import static org.matsim.dashboard.RunLiveabilityDashboard.getValidLiveabilityOutputDirectory;


public class LiveabilitySummaryDashboard implements Dashboard {

	public double priority() {return 2;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability-Index Summary";
		header.description = "Agents and their fulfillment of liveability indicators";

		// map as xyt-Map - better: SHP for more interactive use of the data
		layout.row("Overall Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Overall index value map";
				viz.description = "Here you can see the agents according to their overall ranking index depicted on their home location";
				viz.height = 10.0;
				viz.radius = 15.0;
				viz.file = data.compute(LiveabilitySummaryAnalysis.class, "agentRankingForSummaryMap.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

		layout.row("Overall Ranking")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overallRankingTile.csv");
				viz.height = 0.1;
			});

		layout.row("ScoringTiles")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "summaryTiles.csv");
				viz.height = 0.1; //})
			});

		layout.row("WorstIndicator")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overallHighestLowestIndicator.csv");
				viz.height = 0.1; //})

			});

		layout.row("Indicator Overview")
			.el(Table.class, (viz, data) -> {
				viz.dataset = data.compute(AgentLiveabilityInfoCollection.class, "indexIndicatorValues.csv");
				viz.height = 6.0;
			});
	}
}
