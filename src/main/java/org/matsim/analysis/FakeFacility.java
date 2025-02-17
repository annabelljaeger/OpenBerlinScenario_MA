package org.matsim.analysis;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.facilities.Facility;

import java.util.Map;

public final class FakeFacility implements Facility {
	private final Coord coord;
	private final Id<Link> linkId;

	public FakeFacility(Coord coord) {
		this(coord, (Id)null);
	}

	FakeFacility(Coord coord, Id<Link> linkId) {
		this.coord = coord;
		this.linkId = linkId;
	}

	public Coord getCoord() {
		return this.coord;
	}

	public Id getId() {
		throw new RuntimeException("not implemented");
	}

	public Map<String, Object> getCustomAttributes() {
		throw new RuntimeException("not implemented");
	}

	public Id getLinkId() {
		return this.linkId;
	}
}
