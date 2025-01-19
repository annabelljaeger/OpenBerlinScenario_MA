package org.matsim.dashboard;

import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Tile;

public class LiveabilitySummaryDashboard implements Dashboard {

	public double priority() {return 2;}

	//Definiere und erstelle Übersichtsseite

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Liveability Ranking Summary";
		header.description = "Prozent der Agenten, die Kriterien erfüllen";

		layout.row("Overall Ranking")
				.el(Tile.class, (viz, data) -> {
				viz.dataset = data.compute(LiveabilitySummaryAnalysis.class, "overallRankingTile.csv");
				viz.height = 0.1;
				});

		layout.row("ScoringTiles").el(Tile.class, (viz, data) -> {
		//	viz.dataset = data.compute(AgentLiveabilityInfo.class, "summaryTiles.csv");
		//	viz.dataset = data.fromFile("summaryTiles.csv");
			viz.dataset = data.output("analysis/analysis/summaryTiles.csv");
			viz.height = 0.1; //})
//			.el(Tile.class, (viz, data) -> {
//			viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_RankingValue.csv");
//
//			viz.height = 0.1;
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
