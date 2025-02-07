package org.matsim.run.policies;

import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.application.MATSimApplication;
import org.matsim.core.controler.Controler;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.run.OpenBerlinDrtScenario;
import org.matsim.run.OpenBerlinScenario;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;
import picocli.CommandLine;

import java.util.List;

/**
 * This class can be used to run the OpenBerlin scenario with an absolute tempo limit for all non-motorway and non-trunk links in Berlin.
 * Note that initially, the freespeed for every link was set indivially based on https://doi.org/10.1016/j.procs.2024.06.080.
 */
public class RunBerlinAbsoluteTempoLimitScenario extends OpenBerlinScenario {

    /**
     * The freespeed for all links, except motorways and trunk roads, within Berlin with a higher freespeed than the given value will be reduced to given value.
     * Remember to provide the value in m/s.
     */
    @CommandLine.Option(names = "--tempo-limit", defaultValue = "8.333",
            description = "absolute tempo limit for all non-motorway and non-trunk links in Berlin. Remember to provide the value in m/s.")
    private double tempoLimit_m_s;

    public static void main(String[] args) {
            MATSimApplication.run(RunBerlinAbsoluteTempoLimitScenario.class, args);
    }

    @Override
    protected void prepareScenario(Scenario scenario) {
        super.prepareScenario(scenario);

        Network network = scenario.getNetwork();

        List<PreparedGeometry> geometries = ShpGeometryUtils.loadPreparedGeometries(
                IOUtils.resolveFileOrResource("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.4/input/shp/Berlin_25832.shp"));

        //set the absolute tempo limit for all links in Berlin with a higher freespeed than the given value
        network.getLinks().values().stream()
                //filter car links
                .filter(link -> link.getAllowedModes().contains(TransportMode.car))
                //spatial filter
                .filter(link -> ShpGeometryUtils.isCoordInPreparedGeometries(link.getCoord(), geometries))
                //we won't change motorways and motorway_links, neither trunk roads
                .filter(link -> !((String) link.getAttributes().getAttribute("type")).contains("motorway"))
                .filter(link -> !((String) link.getAttributes().getAttribute("type")).contains("trunk"))
                //get links above the absolute tempo limit
                .filter(link -> link.getFreespeed() > tempoLimit_m_s)
                //now apply the changes, i.e. reduce the freespeed to the absolute tempo limit
                .forEach(link -> link.setFreespeed(tempoLimit_m_s));

    }

}
