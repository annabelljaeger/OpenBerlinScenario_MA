package org.matsim.dashboard;

import org.matsim.analysis.LiveabilitySummaryAnalysis;
import org.matsim.analysis.LostTimeAnalysisLegs_ModeSpecific_adapted;
import org.matsim.application.MATSimAppCommand;
import org.matsim.core.utils.charts.BarChart;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.ColorScheme;
import org.matsim.simwrapper.viz.Plotly;
import org.matsim.simwrapper.viz.Tile;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;

import java.util.List;

public class AgentBasedLossTimeDashboard implements Dashboard {

	//private final String lossTimePath;

//	public AgentBasedLossTimeDashboard(String lossTimePath) {
//		this.lossTimePath = lossTimePath;
//	}

	public double priority(){return -1;}

	public void configure(Header header, Layout layout) {

		header.title = "Loss Time";
		header.description = "Details on Loss Time";

		layout.row("BarChart Modes")
			.el(Plotly.class, (viz, data) -> {

			viz.title = "Mode Specific Loss Time";
			viz.description = "sum by mode";

			Plotly.DataSet ds = viz.addDataset(data.compute(LostTimeAnalysisLegs_ModeSpecific_adapted.class, "summary_modeSpecificLegsLostTime3.csv")); //, "--input", "C:/Users/annab/MatSim for MA/Output_Cluster/OBS_Base/output_OBS_Base/summary_modeSpecificLegsLostTime3.csv"))
					//.aggregate(List.of("mode"), "cumulative_loss_time", Plotly.AggrFunc.SUM);

			viz.layout = tech.tablesaw.plotly.components.Layout.builder()
				.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
				.xAxis(Axis.builder().title("Mode").build())
				.yAxis(Axis.builder().title("Loss Time").build())
				.build();

			viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
				.x("mode")
				.y("cumulative_loss_time")
				.name("cumulative_loss_time", ColorScheme.RdYlBu)
			);
		});

	//	layout.row("second").el(Tile.class, (viz, data) -> {})
	}
}

