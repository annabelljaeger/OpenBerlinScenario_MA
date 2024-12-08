package org.matsim.analysis;

import cadyts.calibrators.filebased.Agent;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.core.network.algorithms.NetworkCleaner;
import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
	name = "agentLiv",
	description = "agent liv info",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)

@CommandSpec(
	requireRunDirectory = true,
//	requires = {
//		"berlin-v6.3.output_legs.csv.gz"
//	},
	produces = {
		"liveabilityInfo.csv"
	}
)

public class AgentLiveabilityInfo implements MATSimAppCommand {


	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(AgentLiveabilityInfo.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(AgentLiveabilityInfo.class);

	//	public static void main(String[] args) {
	public static void main(String[] args) {
		new AgentLiveabilityInfo().execute(args);
	}

	@Override
	public Integer call() throws Exception {


		Path outputAgentLiveabilityCSVPath = output.getPath("liveabilityInfo.csv");
		generateLiveabilityData(outputAgentLiveabilityCSVPath);

		System.out.println("Ich bin nicht hier");
		return 0;
	}


	// Beispielmethode zur Erstellung der Liveability-CSV-Datei
	public void generateLiveabilityData( Path outputCsvPath) throws IOException {
		// Beispiel: Sammeln von Daten aus Analysen und Schreiben in die CSV
		List<String> data = List.of(
			"AgentID,Metric1,Metric2,Metric3",
			"1,10.0,20.0,30.0",
			"2,15.0,25.0,35.0"
		);

		// Schreibe die Daten in die CSV
		try (BufferedWriter writer = Files.newBufferedWriter(outputCsvPath)) {
			for (String line : data) {
				writer.write(line);
				writer.newLine();
			}
		}

		System.out.println("Liveability-CSV erstellt unter: " + outputCsvPath);
	}


}



