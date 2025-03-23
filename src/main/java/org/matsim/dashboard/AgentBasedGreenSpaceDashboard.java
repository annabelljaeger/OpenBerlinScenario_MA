package org.matsim.dashboard;

import org.matsim.analysis.*;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.HistogramTrace;

import static java.lang.Double.NaN;
import static tech.tablesaw.plotly.components.Axis.CategoryOrder.CATEGORY_ASCENDING;

// This is the Class creating the Dashboard for the green space related analyses.
// 1. Map of all agents with their deviation values from the set limit
// 2. Tiles with details on overall fulfillment
// 3. additional information on the green spaces to see how big they are
// 4. Indicator specific achievement values & Distribution of the deviations for each indicator (distance and utilitzation) with zero being the limit, positive values exceeding the limit and negative ones for falling below
public class AgentBasedGreenSpaceDashboard implements Dashboard {

	public double priority(){return -3;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Green Space";
		header.description = "A key indicator of spatial justice is the availability and accessibility of " +
			"relaxing green spaces for relaxation and stress relief. Therefore the walking distance to the nearest " +
			"green space as well as the amount of green space in this nearest area per Person is analysed.";


		// *********************************** Green Space - Overall Visualization elements ************************************

		// map as xyt-Map displaying the worst deviation from either of the two indicator deviation values as the dimension index value
		layout.row("GreenSpace Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "Green Space Index Value Map";
				viz.description = "The Green Space target dimension is represented by showing the deviation from a limit that the agent is below on all green space indicators. " +
					"It is therefore the maximum indicator index value of green space distance or utilization per agent, which is displayed on the agent's home location";

				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTAgentBasedGreenSpaceMap.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});

		layout.row("overall index result green space")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Green Space Index Value";
				viz.description = "According to the 'Deutsche Städtetag' everyone should have access to a public green space of at least 1 hectare of size within 500 metres " +
					"of their home. Furthermore, these green spaces should offer at least 6 m² per person assigned to it. In this analysis, the 500 m is the Euclidean distance " +
					"and it is assumed that each agent chooses the green space with the closest access point to its home location.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesOverall.csv");
				viz.height = 0.1;
			});

		layout.row("Green Space info - size groups")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Area Distribution";
				viz.description = "Shows how many green spaces of each size category occur in the dataset. This can be used to see how many and whether there " +
					"are rather large or small green spaces in the study area.";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_utilization.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("areaCategory")
					.build(), dataset.mapping()
					.x("areaCategory"));
				Axis.CategoryOrder categoryOrder = Axis.CategoryOrder.ARRAY;
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Green Space Size").categoryOrder(CATEGORY_ASCENDING).build())
					.yAxis(Axis.builder().title("Number of Green Spaces").build())
					.build();
			});

		layout.row("Green Space Area and Utilization Map")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "Green Spaces And Their Utilization";
				viz.description = "Shows the green spaces and their utilization in relation to one person per six square metres. Some green spaces may " +
					"have no access points and therefore receive the optimum deviation value of -1 as no people are using the area and therefore polluting it.";

				viz.height = 15.0;
				viz.setShape(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_statsGeofile.gpkg"));
				viz.display.fill.dataset = "greenSpace_statsGeofile.gpkg";
				viz.display.fill.columnName = "utilizationDeviationValue";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 13, false, "-0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0");
				viz.display.fill.join = "";
				viz.display.fill.fixedColors = new String[]{"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28"};
			});


		// *********************************** Green Space - Indicator Specific Dashboard Values ************************************

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Distance Indicator Details";
				viz.description = "Displays how many agents have a green space within 500 m of their home location. " +
					"Also shows the average and mean distance that agents from the study area live away from green spaces.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesDistance.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Utilization Indicator Details";
				viz.description = "Displays how many agents have at least 6 m² of space in the green space closest to their home location. " +
					"Also shows the average and mean utilization that agents from the study area face when visiting their nearest green space.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesUtilization.csv");
			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Distance - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the green space distance. Zero represents the aim of 500 m and " +
					"values below zero show all values that fall below, meaning a better value for citizens. Agents that exceed the 50 0m euclidean " +
					"distance to a green space are represented in the values above zero, with 1 meaning 100 % over the limit (1000 m euclidean distance).";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSDistanceDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping()
						.x("GSDistanceDeviationFromLimit"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Utilization - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the green space utilization. For normalization reasons of the index with lower values resulting in " +
					"lower index values and therefore better results, the return value of 1 person per 6 m² is used as a limit here. Therefore index values below " +
					"zero show all agents, whose closest green space has a utilization of 1 person per 6 or more m²." +
					"Agents that have less than 6 m² of individual space at their closest green space available are represented in the values above zero, with 1 meaning 100 % over the limit (2 person per 6 m² = 1 person per 3 m²).";

				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));
				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSUtilizationDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping()
						.x("GSUtilizationDeviationFromLimit"));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});

		layout.row("GreenSpace Indicator Maps")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace distance index value map";
				viz.description = "The map shows each agent's deviation value from the limit of 500 m distance from their home displayed at their home location.";

				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTGreenSpaceDistanceMap.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			})

			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace utilization index value map";
				viz.description = "The map shows each agent's deviation value from the limit of 6 m² per person displayed at their home location";

				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTGreenSpaceUtilizationMap.xyt.csv");
				viz.height = 15.0;
				viz.radius = 15.0;
				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350","#0d0a28", "#363636"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0, 128.0, NaN);
			});
	}
}
