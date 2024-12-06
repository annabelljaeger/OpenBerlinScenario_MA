package org.matsim.dashboard;

import org.matsim.analysis.AgentBasedLossTimeAnalysis;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.Bar;
import org.matsim.simwrapper.viz.ColorScheme;
import org.matsim.simwrapper.viz.Plotly;
import org.matsim.simwrapper.viz.Tile;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.ScatterTrace;

import java.util.List;

public class AgentBasedLossTimeDashboard implements Dashboard {

	public double priority(){return -1;}

	public void configure(Header header, Layout layout) {

		header.title = "Loss Time";
		header.description = "Detailed Loss Time Analysis";

		layout.row("overall ranking result loss time")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Loss Time Ranking Value";
				viz.description = "percentage of agents with less than 15 % loss time";

				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "lossTime_RankingValue.csv");
				viz.height = 0.1;

				});

		layout.row("BarChart Modes")
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Mode Specific Loss Time";
				viz.description = "sum by mode";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "summary_modeSpecificLegsLossTime.csv"));
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
			})
			.el(Bar.class, (viz, data) -> {
					viz.title = "Loss Time per mode v2";
					viz.description = "in seconds";

					viz.stacked = false;
					viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "summary_modeSpecificLegsLossTime.csv");
					viz.x = "mode";
					viz.xAxisName = "Mode";
					viz.yAxisName = "Loss Time [s]";
					viz.columns = List.of("cumulative_loss_time", "failed_routings");
		});

		layout.row("histogramm")
			.el(Bar.class, (viz, data) -> {
				viz.title = "Histogram";
				viz.description = "loss time over time";

				viz.stacked = false;
				viz.dataset = data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv");
				viz.x = "dep_time";
			})
			.el(Plotly.class, (viz, data) -> {

				viz.title = "Histogramm Loss Time";
				viz.description = "loss time over time";

				Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"))
					.aggregate(List.of("mode"), "dep_time", Plotly.AggrFunc.SUM);

				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.barMode(tech.tablesaw.plotly.components.Layout.BarMode.GROUP)
					.xAxis(Axis.builder().title("Mode").build())
					.yAxis(Axis.builder().title("Loss Time").build())
					.build();

				viz.addTrace(BarTrace.builder(Plotly.OBJ_INPUT, Plotly.INPUT).orientation(BarTrace.Orientation.VERTICAL).build(), ds.mapping()
					.x("mode")
					.y("dep_time")
					.name("lost_time", ColorScheme.RdYlBu)
				);
			})

					.el(Plotly.class, (viz, data) -> {
						viz.title = "Lost Time per Quarter Hour";
						viz.description = "Sum of lost times (in seconds) for each 15-minute interval of the day";

						// Add the legs dataset (adjust the filename as necessary)
						Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv"));

						// Define the layout for the plot
						viz.layout = tech.tablesaw.plotly.components.Layout.builder()
							.xAxis(Axis.builder().title("Time Interval").build())
							.yAxis(Axis.builder().title("Total Lost Time (s)").build())
							.build();
/*
						double[] lossTimes = dataset.column("loss_time").asDoubleArray(); // Verlustzeiten als Array
						String[] intervals = dataset.column("dep_time").asStringArray(); // Viertelstundenintervalle als Array

											// Add a BarTrace for the sum of lost time
						viz.addTrace(BarTrace.builder(intervals, lossTimes)
								.name("Lost Time")
								.build(),
							dataset.mapping()
								.x("dep_time") // Adjust to the column that groups the time intervals
								.y("loss_time") // Adjust to the column with the sum of lost times
						);

						viz.addTrace(BarTrace.builder(Plotly.INPUT, Plotly.INPUT) // Verwenden von INPUT
								.name("Lost Time") // Name des Traces
								.build(),
							dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
								.x("dep_time") // Spaltenname für die X-Achse (Zeitintervalle)
								.y("loss_time") // Spaltenname für die Y-Achse (Verlustzeiten)
						);
					})

						.el(context, Plotly.class, (viz, data) -> {

							viz.title = "Avg. error / bias";

							viz.layout = tech.tablesaw.plotly.components.Layout.builder()
								.xAxis(Axis.builder().title("Hour").build())
								.yAxis(Axis.builder().title("Mean rel. error [%]").build())
								.yAxis2(Axis.builder().title("Mean (abs.) error [veh/h]")
									.side(Axis.Side.right)
									.overlaying(ScatterTrace.YAxis.Y)
									.build())
								.build();

							Plotly.DataSet ds = viz.addDataset(data.compute(AgentBasedLossTimeAnalysis.class, "output_legsLossTime_new.csv", args));

							viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.LINE)
								.name("Mean rel. error")
								.build(), ds.mapping()
								.x("loss_time")
								.y("dep_time"));

				 */
					});






	}
}

