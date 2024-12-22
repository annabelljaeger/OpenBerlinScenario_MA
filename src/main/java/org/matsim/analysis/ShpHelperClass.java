//package org.matsim.analysis;
//
//import com.opencsv.CSVReader;
//import com.opencsv.CSVWriter;
//import com.opencsv.exceptions.CsvValidationException;
//import org.geotools.api.feature.simple.SimpleFeature;
//import org.locationtech.jts.geom.Coordinate;
//import org.locationtech.jts.geom.Geometry;
//import org.locationtech.jts.geom.prep.PreparedGeometry;
//import org.matsim.api.core.v01.Coord;
//import org.matsim.core.utils.geometry.CoordUtils;
//import org.matsim.core.utils.geometry.geotools.MGC;
//import org.matsim.core.utils.gis.GeoFileReader;
//import org.matsim.core.utils.io.IOUtils;
//import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;
//
//import java.io.FileNotFoundException;
//import java.io.FileReader;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//
//public class ShpHelperClass {
//
//
//    public static void main(String[] args) {
//
////        List<PreparedGeometry> gruenflachen = ShpGeometryUtils.loadPreparedGeometries(IOUtils.resolveFileOrResource("pathTo/myShapeFile.shp"));
//
//
//        Collection<SimpleFeature> allFeatures = GeoFileReader.getAllFeatures(IOUtils.resolveFileOrResource("pathTo/myShapeFile.shp"));
//
//
//        try {
//            CSVReader reader = new CSVReader(new FileReader("persons.csv"));
//
//            String[] personRecord = reader.readNext();
//
//            String id = personRecord[0];
//            String homeX = personRecord[15];
//            String homeY = personRecord[16];
//
//            Coord homeCoord = new Coord(Double.valueOf(homeX),Double.valueOf(homeY));
//
//            for (SimpleFeature simpleFeature : allFeatures) {
//
//                Geometry defaultGeometry = (Geometry) simpleFeature.getDefaultGeometry();
//
//                double shortestDistance = Double.MAX_VALUE;
//                Geometry closestGeometry = null;
//
//                double distanceToCentroid = CoordUtils.calcEuclideanDistance(homeCoord, MGC.point2Coord(defaultGeometry.getCentroid()));
//                if (distanceToCentroid < shortestDistance){
//                    shortestDistance = distanceToCentroid;
//                    closestGeometry = defaultGeometry;
//                }
//
//
//                String name = (String) simpleFeature.getAttribute("name");
//
//
//            }
//
//
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        } catch (CsvValidationException e) {
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        List<Coord> activityCoords = new ArrayList<>();
//
//
//     //   PreparedGeometry flaeche = gruenflachen.get(0);
//
//     //   flaeche.contains(flaeche.getGeometry());
//
//
//    }
//}
