package org.matsim.dashboard;

import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;

public class AgentBasedSafetyDashboard implements Dashboard {

	public double priority(){return -1;}

	@Override
	public void configure(Header header, Layout layout) {

		header.title = "Safety";
		header.description = "Agent-based dashboard on traffic safety";

	}
}
