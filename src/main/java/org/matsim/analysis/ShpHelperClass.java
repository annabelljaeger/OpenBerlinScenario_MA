package org.matsim.analysis;

import org.geotools.api.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;

import java.util.ArrayList;
import java.util.List;

public class ShpHelperClass {


    public static void main(String[] args) {

        List<PreparedGeometry> gruenflachen = ShpGeometryUtils.loadPreparedGeometries(IOUtils.resolveFileOrResource("pathTo/myShapeFile.shp"));


        List<Coord> activityCoords = new ArrayList<>();

        for (Coord activityCoord : activityCoords) {

            double shortestDistance = Double.MAX_VALUE;
            PreparedGeometry closestGeometry = null;

            double buffer = 10;

            for (PreparedGeometry preparedGeometry : gruenflachen) {

                preparedGeometry.getGeometry().buffer(buffer)

                double distanceToCentroid = CoordUtils.calcEuclideanDistance(activityCoord, MGC.point2Coord(preparedGeometry.getGeometry().getCentroid()));
                if (distanceToCentroid < shortestDistance){
                    shortestDistance = distanceToCentroid;
                    closestGeometry = preparedGeometry;
                }

                //
                for (Coordinate envelopeCoordinate : preparedGeometry.getGeometry().getEnvelope().getCoordinates()) {

                }

            }

        }


        PreparedGeometry flaeche = gruenflachen.get(0);

        flaeche.contains(flaeche.getGeometry());


    }
}
