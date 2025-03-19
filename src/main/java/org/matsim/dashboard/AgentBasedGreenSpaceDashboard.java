package org.matsim.dashboard;

import org.jaitools.numeric.Histogram;
import org.matsim.analysis.*;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.prepare.network.CreateGeoJsonNetwork;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.viz.*;
import tech.tablesaw.api.DoubleColumn;
import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.BarTrace;
import tech.tablesaw.plotly.traces.HistogramTrace;
import tech.tablesaw.plotly.traces.Trace;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.matsim.dashboard.RunLiveabilityDashboard.*;
import static tech.tablesaw.plotly.components.Axis.CategoryOrder.ARRAY;
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

		// map as xyt-Map displaying the worst deviation from either of the two indicator deviation values as the dimension index value
		layout.row("GreenSpace Index Value Map")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace Index Value Map";
				viz.description = "Here you can see the agents according to their green space index values depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTAgentBasedGreenSpaceMap.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);
			});

		layout.row("overall index result green space")
			.el(Tile.class, (viz, data) -> {

				viz.title = "Green Space Index Value";
				viz.description = "According to the 'Deutsche Städtetag' every person should have accessibility to a green space " +
					"of at least 1 ha of size within 500 m footpath of their home location. Furthermore those green spaces should " +
					"offer at least 6 m² per person assigned to it (here: always choice of the nearest green space to the home location).";

				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesOverall.csv");
				viz.height = 0.1;
			});

		layout.row("Green Space info - size groups")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Area Distribution";
				viz.description = "Distribution of green spaces by size categories";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_utilization.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("areaCategory").build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("areaCategory"));

				Axis.CategoryOrder categoryOrder = Axis.CategoryOrder.ARRAY;
				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Green Space Size").categoryOrder(CATEGORY_ASCENDING).build())
					.yAxis(Axis.builder().title("Number of Green Spaces").build())
					.build();

			});

		layout.row("Green Space Flächen mit Breakpoint und Farbe")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "Green Spaces and their utilization";
				viz.height = 15.0;
				viz.setShape(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_statsGeofile.gpkg"));
				viz.display.fill.dataset = "greenSpace_statsGeofile.gpkg";
				viz.display.fill.columnName = "utilizationDeviationValue";

				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 12, false, "-0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0");
				viz.display.fill.join = "";
				viz.display.fill.fixedColors = new String[]{"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};

			});

		layout.row("Stats per indicator - Tiles")
			.el(Tile.class, (viz, data) -> {
				viz.title = "Distance Indicator Details";
				viz.description = "Displays how many agents have a green space within 500m of their home location. " +
					"Also shows the average and mean distance that agents from the study area live away from green spaces.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesDistance.csv");
			})

			.el(Tile.class, (viz, data) -> {
				viz.title = "Utilization Indicator Details";
				viz.description = "Displays how many agents have at least 6m² of space in the green space closest to their home location. " +
					"Also shows the average and mean utilization that agents from the study area face when visiting their nearest green space.";
				viz.dataset = data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_TilesUtilization.csv");
			});

		layout.row("Stats per indicator - Diagram")
			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Distance - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the green space distance. Zero represents the aim of 500 m and " +
					"values below zero show all values that fall below, meaning a better value for citizens. Agents that exceed the 500m euclidean " +
					"distance to a green space are represented in the values above zero, with 1 meaning 100 % over the limit (1000m euclidean distance).";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSDistanceDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("GSDistanceDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			})

			.el(Plotly.class, (viz, data) -> {
				viz.title = "Green Space Utilization - Distribution of deviations";
				viz.description = "Displays the distribution of deviation for the green space utilization. Zero represents the aim of 6 m²/person and " +
					"values below zero show all values that fall below, meaning a better value for citizens, as the return value is used (how many people per m² of green space)." +
					"Agents that exceed the 6 m²/person for their nearest green space are represented in the values above zero, with 1 meaning 100 % over the limit (3 m²/person).";

				// Add the dataset
				Plotly.DataSet dataset = viz.addDataset(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_stats_perAgent.csv"));

				viz.addTrace(HistogramTrace.builder(Plotly.INPUT).name("GSUtilizationDeviationFromLimit").nBinsX(200).build(),
					dataset.mapping() // Mapping verwenden, um Spalten aus dem Dataset zuzuordnen
						.x("GSUtilizationDeviationFromLimit"));

				// Define the layout for the plot
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Deviation").build())
					.yAxis(Axis.builder().title("Number of Agents").build())
					.build();
			});

		// map as xyt-Map - better: SHP for more interactive use of the data
		layout.row("GreenSpace Indicator Maps")
			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace distance index value map";
				viz.description = "Here you can see the agents according to their green space distance index value depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTGreenSpaceDistanceMap.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);

			})
			.el(XYTime.class, (viz, data) -> {
				viz.title = "GreenSpace utilization index value map";
				viz.description = "Here you can see the agents according to their green space utilization index value depicted on their home location";
				viz.height = 15.0;
				viz.radius = 15.0;
				viz.file = data.compute(AgentBasedGreenSpaceAnalysis.class, "XYTGreenSpaceUtilizationMap.xyt.csv");

				String[] colors = {"#008000", "#6eaa5e", "#93bf85", "#f0a08a", "#d86043", "#c93c20", "#af230c", "#9b88d3", "#7863c4", "#4f3fb4", "#001ca4", "#191350"};
				viz.setBreakpoints(colors, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0, 8.0, 16.0);

			});


/*
		// geofile based map - issue: visualization cannot be adapted here
		layout.row("Green Space Deviations Geofile")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "GreenSpace Index Results Map";
				viz.height = 10.0;
				viz.setShape(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_perAgentGeofile.gpkg"));
				viz.display.fill.dataset = "greenSpace_perAgentGeofile.gpkg";
				viz.display.fill.columnName = "greenSpaceOverallIndexValue";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 6, false);
				viz.display.fill.join = "";

			});

		layout.row("Green Space Deviations mit Breakpoint und Farbe nin Maß")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "GreenSpace Index Results Map - Farben selbst gesetzt";
				viz.height = 10.0;
				viz.setShape(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_statsGeofile.gpkg"));
				viz.display.fill.dataset = "greenSpace_statsGeofile.gpkg";
				viz.display.fill.columnName = "greenSpaceUtilization";
				viz.display.fill.setColorRamp(ColorScheme.RdYlBu, 6, false, "-0.5, 0.0, 0.5, 1.0, 1.5");
				viz.display.fill.join = "";
				viz.display.fill.fixedColors = new String[]{"#1175b3", "#95c7df", "#dfdb95", "#dfb095", "#f4a986", "#cc0c27"};
			});

		// geofile based map - issue: visualization cannot be adapted here
		layout.row("Green Space Deviations Geofile")
			.el(MapPlot.class, (viz, data) -> {
				viz.title = "GreenSpace Index Results Map";
				viz.height = 10.0;
				viz.setShape(data.compute(AgentBasedGreenSpaceAnalysis.class, "greenSpace_perAgentGeofile.gpkg"));
				viz.display.fill.dataset = "greenSpace_perAgentGeofile.gpkg";
				viz.display.fill.columnName = "greenSpaceOverallIndexValue";
				viz.display.fill.setColorRamp(ColorScheme.Set1, 6, true, "-0.5, 0.0, 0.5, 1.0, 1.5");
				viz.display.fill.join = "";
			});
*/
	}
}
