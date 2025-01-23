package org.matsim.dashboard;

import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;

import java.util.List;

import org.matsim.simwrapper.*;



public class LiveabilitySummaryDashboard implements Dashboard {

	public double priority() {return 2;}

	private static final String RANKING = "ranking";
//	private final List<String> dirs;
//	private final Integer noRuns;
//
//	public LiveabilitySummaryDashboard(List<String> dirs, Integer noRuns) {
//		this.dirs = dirs;
//		this.noRuns = noRuns;
//	}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability Ranking Summary";
		header.description = "Percentage of agents fulfilling at least 80 % of the indicators";

//		layout.row("Test RankingMap")
//			.el(MapPlot.class, (viz, data) -> {
//				viz.title = "Ranking results map";
//				viz.description = "Here you can see the agents according to their liveability ranking depicted on their home location";
//				viz.height = 12.0;
//				viz.
//				viz.center = data.context().getCenter();
//				viz.zoom = data.context().mapZoomLevel;
//				viz.minValue = 0.0;
//				viz.maxValue = 100000.0;
//				viz.setShape("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/berlin-v6.3.output_network.xml.gz", "id");
//			//	viz.addDataset(RANKING, postProcess(data, "mean_emission_per_day.csv"));
//				viz.addDataset("agentLiveabilityInfo.csv", "C:/Users/annab/MatSim for MA/Output_Cluster/analysis/agentLiveabilityInfo.csv");
//				viz.display.lineColor.dataset = RANKING;
//				viz.display.lineColor.columnName = "LossTime";
//				viz.display.lineColor.join = "linkId";
//				viz.display.lineColor.setColorRamp(ColorScheme.RdYlGn, 8, false, "0.5, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95");
//				viz.display.lineWidth.dataset = RANKING;
//				viz.display.lineWidth.columnName = "TravTime";
//				viz.display.lineWidth.scaleFactor = 100d;
//				viz.display.lineWidth.join = "linkId";
//			});

		layout.row("Test RankingMap")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Ranking results map";
				viz.description = "Here you can see the agents according to their liveability ranking depicted on their home location";
				viz.height = 12.0;
				viz.file = "C:/Users/annab/MatSim for MA/Output_Cluster/analysis/agentLiveabilityInfo_testTXY.xyt.csv";
//				viz.zoom = data.context().mapZoomLevel;
//				viz.minValue = 0.0;
//				viz.maxValue = 100000.0;
//				viz.setShape("C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/berlin-v6.3-10pct/berlin-v6.3.output_network.xml.gz", "id");
//				//	viz.addDataset(RANKING, postProcess(data, "mean_emission_per_day.csv"));
//				viz.addDataset("agentLiveabilityInfo.csv", "C:/Users/annab/MatSim for MA/Output_Cluster/analysis/agentLiveabilityInfo.csv");
//				viz.display.lineColor.dataset = RANKING;
//				viz.display.lineColor.columnName = "LossTime";
//				viz.display.lineColor.join = "linkId";
//				viz.display.lineColor.setColorRamp(ColorScheme.RdYlGn, 8, false, "0.5, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95");
//				viz.display.lineWidth.dataset = RANKING;
//				viz.display.lineWidth.columnName = "TravTime";
//				viz.display.lineWidth.scaleFactor = 100d;
//				viz.display.lineWidth.join = "linkId";
			});

		layout.row("Overall Ranking")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overallRankingTile.csv");
				viz.height = 0.1;
			});

		layout.row("ScoringTiles")
			.el(Tile.class, (viz, data) -> {
				viz.dataset = data.output("analysis/analysis/summaryTiles.csv");
				viz.height = 0.1; //})
			});

		layout.row("Indicator Overview")
			.el(Table.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overviewIndicatorTable.csv");

			});
	}
}
/*
Library for Simwrapper Dashboard Outputs

	layout.row("second")
		.el(Plotly.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per Mode";

			Plotly.DataSet ds = viz.addDataset(data.compute(StuckAgentAnalysis.class, "stuck_agents_per_mode.csv"));
			viz.addTrace(PieTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).build(), ds.mapping()
				.text("Mode")
				.x("Agents")
			);
		})
		.el(Table.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per Mode";
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_mode.csv");
		});

	layout.row("third")
		.el(Bar.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per hour";

			viz.stacked = true;
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_hour.csv");
			viz.x = "hour";
			viz.xAxisName = "Hour";
			viz.yAxisName = "# Stuck";
			viz.columns = List.of("pt","walk");
		})
		.el(Table.class, (viz, data) -> {
			viz.title = "Stuck Agents";
			viz.description = "per hour";
			viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_hour.csv");
		});

	layout.row("four").el(Table.class, (viz, data) -> {
		viz.title = "Stuck Agents";
		viz.description = "per Link (Top 20)";
		viz.dataset = data.compute(StuckAgentAnalysis.class, "stuck_agents_per_link.csv");
	});
}
}
*/
