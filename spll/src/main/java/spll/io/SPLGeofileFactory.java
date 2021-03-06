package spll.io;

import java.awt.Color;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.media.jai.RasterFactory;

import org.apache.commons.io.FilenameUtils;
import org.geotools.coverage.Category;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.io.AbstractGridCoverageWriter;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.Hints;
import org.geotools.feature.SchemaException;
import org.geotools.gce.arcgrid.ArcGridWriter;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.resources.i18n.Vocabulary;
import org.geotools.resources.i18n.VocabularyKeys;
import org.geotools.util.NumberRange;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import core.metamodel.geo.AGeoEntity;
import core.metamodel.geo.io.IGSGeofile;
import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationEntity;
import core.util.stats.GSBasicStats;
import core.util.stats.GSEnumStats;
import spll.SpllPopulation;
import spll.entity.GSFeature;
import spll.io.exception.InvalidGeoFormatException;

public class SPLGeofileFactory {

	public static String SHAPEFILE_EXT = "shp";
	public static String ARC_EXT = "asc";
	public static String GEOTIFF_EXT = "tif";
	
	public static Color[] bestRedPalette = new Color[] { new Color(254,240,217), 
			new Color(253,204,138), new Color(252,141,89), new Color(227,74,51), 
			new Color(179, 0, 0)};
	public static Color noDataColor = new Color(0, 0, 0, 0);
	
	public static List<String> getSupportedFileFormat(){
		return Arrays.asList(SHAPEFILE_EXT, GEOTIFF_EXT, ARC_EXT);
	}

	/**
	 * Create a geo referenced file 
	 * 
	 * @param geofile
	 * @return
	 * @throws IllegalArgumentException
	 * @throws TransformException
	 * @throws IOException
	 * @throws InvalidGeoFormatException
	 */
	public IGSGeofile<? extends AGeoEntity> getGeofile(File geofile) 
			throws IllegalArgumentException, TransformException, IOException, InvalidGeoFormatException{
		if(FilenameUtils.getExtension(geofile.getName()).equals(SHAPEFILE_EXT))
			return new SPLVectorFile(geofile);
		if(FilenameUtils.getExtension(geofile.getName()).equals(GEOTIFF_EXT) || 
				FilenameUtils.getExtension(geofile.getName()).equals(ARC_EXT))
			return new SPLRasterFile(geofile);
		String[] pathArray = geofile.getPath().split(File.separator);
		throw new InvalidGeoFormatException(pathArray[pathArray.length-1], Arrays.asList(SHAPEFILE_EXT, GEOTIFF_EXT, ARC_EXT));
	}
	
	/**
	 * Create a shapefile from a file
	 * 
	 * @return
	 * @throws IOException, InvalidFileTypeException 
	 */
	public SPLVectorFile getShapeFile(File shapefile) throws IOException, InvalidGeoFormatException {
		if(FilenameUtils.getExtension(shapefile.getName()).equals(SHAPEFILE_EXT))
			return new SPLVectorFile(shapefile);
		String[] pathArray = shapefile.getPath().split(File.separator);
		throw new InvalidGeoFormatException(pathArray[pathArray.length-1], Arrays.asList(SHAPEFILE_EXT));
	}
	
	/**
	 * TODO: javadoc
	 * 
	 * @param shapefile
	 * @param attributes
	 * @return
	 * @throws IOException
	 * @throws InvalidGeoFormatException
	 */
	public SPLVectorFile getShapeFile(File shapefile, List<String> attributes) throws IOException, InvalidGeoFormatException {
		if(FilenameUtils.getExtension(shapefile.getName()).equals(SHAPEFILE_EXT))
			return new SPLVectorFile(shapefile, attributes);
		String[] pathArray = shapefile.getPath().split(File.separator);
		throw new InvalidGeoFormatException(pathArray[pathArray.length-1], Arrays.asList(SHAPEFILE_EXT));
	}
	
	
	// ------------------------------------------------------------ //
	//						CREATE GEOFILE							//
	// ------------------------------------------------------------ //

	/**
	 * TODO: test
	 * TODO: change float pixel value type to double (possible through JAI)
	 * 
	 * @param rasterfile
	 * @param pixels
	 * @param crs
	 * @return
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws TransformException
	 */
	public SPLRasterFile createRasterfile(File rasterfile, float[][] pixels, float noData, 
			ReferencedEnvelope envelope) 
			throws IOException, IllegalArgumentException, TransformException {
		// Create image options based on pixels' characteristics
		
		GSBasicStats<Double> gsbs = new GSBasicStats<Double>(GSBasicStats.transpose(pixels), Arrays.asList(new Double(noData)));

		Category nan = new Category(Vocabulary.formatInternational(VocabularyKeys.NODATA), 
				new Color[] { noDataColor },
				NumberRange.create(noData, noData));
		Category values = new Category("values", bestRedPalette, 
				NumberRange.create(gsbs.getStat(GSEnumStats.min)[0], gsbs.getStat(GSEnumStats.max)[0]));

		GridSampleDimension[] bands = new GridSampleDimension[] { 
				new GridSampleDimension("Dimension", new Category[] { nan, values }, null)}; 

		WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT,
				pixels.length, pixels[0].length, 1, null);
		for (int y=0; y<pixels[0].length; y++) {
			for (int x=0; x<pixels.length; x++) {
				raster.setSample(x, y, 0, pixels[x][y]);
			}
		}
		
		return writeRasterFile(rasterfile, 
				new GridCoverageFactory().create(rasterfile.getName(), raster, envelope, bands));
	}
	
	/**
	 * TODO: test
	 * TODO: change float pixel value type to double (possible through JAI)
	 * 
	 * Build a raster file from a list of pixel band.
	 * 
	 * @param rasterfile
	 * @param pixels
	 * @param crs
	 * @return
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws TransformException
	 */
	public SPLRasterFile createRasterfile(File rasterfile, List<float[][]> pixelsBand, float noData, 
			ReferencedEnvelope envelope) 
			throws IOException, IllegalArgumentException, TransformException {
		// Create image options based on pixels' characteristics
		List<GSBasicStats<Double>> stats = pixelsBand.stream()
				.map(pix -> new GSBasicStats<Double>(GSBasicStats.transpose(pix), Arrays.asList(new Double(noData))))
				.collect(Collectors.toList());
		float min = (float) stats.stream().mapToDouble(stat -> stat.getStat(GSEnumStats.min)[0]).min().getAsDouble();
		float max = (float) stats.stream().mapToDouble(stat -> stat.getStat(GSEnumStats.max)[0]).min().getAsDouble();

		Category nan = new Category(Vocabulary.formatInternational(VocabularyKeys.NODATA), 
				new Color[] { noDataColor },
				NumberRange.create(noData, noData));
		Category values = new Category("values", bestRedPalette, 
				NumberRange.create(min, max));

		GridSampleDimension[] bands = new GridSampleDimension[] { 
				new GridSampleDimension("Dimension", new Category[] { nan, values }, null)}; 

		WritableRaster raster = RasterFactory.createBandedRaster(DataBuffer.TYPE_FLOAT,
				pixelsBand.get(0).length, pixelsBand.get(0)[0].length, pixelsBand.size(), null);
		int bandNb = -1;
		for(float[][] pixels : pixelsBand){
			bandNb++;
			for (int y=0; y<pixels[0].length; y++) 
				for (int x=0; x<pixels.length; x++) 
					raster.setSample(x, y, bandNb, pixels[x][y]);
		}
		
		return writeRasterFile(rasterfile, 
				new GridCoverageFactory().create(rasterfile.getName(), raster, envelope, bands));
	}

	/**
	 * Export a population in a shapefile
	 * 
	 * TODO: explain more
	 * 
	 * @param shapefile
	 * @param population
	 * @param crs
	 * @return
	 * @throws IOException
	 * @throws SchemaException
	 */
	public SPLVectorFile createShapeFile(File shapefile, SpllPopulation population) 
			throws IOException, SchemaException {
		if(population.isEmpty()) 
			throw new IllegalStateException("Population ("+Arrays.toString(population.toArray())+") in methode createShapeFile cannot be empty");
		
		final File parent = shapefile.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		ShapefileDataStore newDataStore = new ShapefileDataStore(shapefile.toURI().toURL());

		Map<APopulationEntity, Geometry> geoms = population.stream().filter(e -> e.getLocation() != null)
				.collect(Collectors.toMap(e -> e, e ->  e.getLocation()));
		final StringBuilder specs = new StringBuilder(population.size() * 20);
		String geomType = getGeometryType(geoms.values());
		specs.append("geometry:" + geomType);
		List<String> atts = new ArrayList<>();
			for (final APopulationAttribute at : population.getPopulationAttributes()) {
				atts.add(at.getAttributeName());
				String name = at.getAttributeName().replaceAll("\"", "");
				name = name.replaceAll("'", "");
				final String type = "String";
				specs.append(',').append(name).append(':').append(type);
			}
		final SimpleFeatureType type = DataUtilities.createType(newDataStore.getFeatureSource().getEntry().getTypeName(),
					specs.toString());
		
		newDataStore.createSchema(type);
	

		try (@SuppressWarnings("rawtypes")
		FeatureWriter fw = newDataStore.getFeatureWriter(Transaction.AUTO_COMMIT)) {

			final List<Object> values = new ArrayList<>();

			for (final APopulationEntity entity : population) {
				values.clear();
				final SimpleFeature ff = (SimpleFeature) fw.next();
				values.add(geoms.get(entity));
				for (final String att : atts) {
					values.add(entity.getValueForAttribute(att));
				}
				ff.setAttributes(values);
				fw.write();
			}
			// store.dispose();
			try (FileWriter fwz = new FileWriter(shapefile.getAbsolutePath().replace(".shp", ".prj"))) {
				fwz.write(population.getCrs().toString());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		} 	
		

		return new SPLVectorFile(newDataStore, Collections.emptyList());
	}
	

	/**
	 * Create a shapefile based on a collection of feature 
	 * 
	 * @param shapefile
	 * @param features
	 * @return
	 * @throws IOException
	 * @throws SchemaException
	 */
	public SPLVectorFile createShapeFile(File shapefile, Collection<GSFeature> features) throws IOException, SchemaException {
		if(features.isEmpty())
			throw new IllegalStateException("GSFeature collection ("+Arrays.toString(features.toArray())+") in methode createShapeFile cannot be empty");
		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<>();
		params.put("url", shapefile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

		Set<FeatureType> featTypeSet = features
				.parallelStream().map(feat -> feat.getInnerFeature().getType()).collect(Collectors.toSet());
		if(featTypeSet.size() > 1)
			throw new SchemaException("Multiple feature type to instantiate schema:\n"+Arrays.toString(featTypeSet.toArray()));
		SimpleFeatureType featureType = (SimpleFeatureType) featTypeSet.iterator().next();
		newDataStore.createSchema(featureType);

		Transaction transaction = new DefaultTransaction("create");
		SimpleFeatureStore featureStore = (SimpleFeatureStore) newDataStore.getFeatureSource(newDataStore.getTypeNames()[0]);

		SimpleFeatureCollection collection = new ListFeatureCollection(featureType, 
				features.stream().map(f -> (SimpleFeature) f.getInnerFeature()).collect(Collectors.toList()));
		featureStore.setTransaction(transaction);
		try {
			featureStore.addFeatures(collection);
			transaction.commit();
		} catch (Exception problem) {
			problem.printStackTrace();
			transaction.rollback();
		} finally {
			transaction.close();
		}

		return new SPLVectorFile(newDataStore, new HashSet<>(features));
	}
	
	// ------------------------------------------------------- //
	// ------------------- INNER UTILITIES ------------------- //
	// ------------------------------------------------------- //
	
	private SPLRasterFile writeRasterFile(File rasterfile, GridCoverage2D coverage) 
			throws IllegalArgumentException, TransformException, IOException {
		AbstractGridCoverageWriter writer;
		if(FilenameUtils.getExtension(rasterfile.getName()).contains(ARC_EXT))
			writer = new ArcGridWriter(rasterfile, new Hints(Hints.USE_JAI_IMAGEREAD, true));
		else
			writer = new GeoTiffWriter(rasterfile);
		writer.write(coverage, null);
		return new SPLRasterFile(rasterfile);
	}

	private String getGeometryType(final Collection<Geometry> geoms) {
		String geomType = "";
		for (final Geometry geom : geoms) {
			if (geom != null) {
				geomType = geom.getClass().getSimpleName();
				if (geom.getNumGeometries() > 1) {
					if (geom.getGeometryN(0).getClass() == Point.class) {
						geomType = MultiPoint.class.getSimpleName();
					} else if (geom.getGeometryN(0).getClass() == LineString.class) {
						geomType = MultiLineString.class.getSimpleName();
					} else if (geom.getGeometryN(0).getClass() == Polygon.class) {
						geomType = MultiPolygon.class.getSimpleName();
					}
					break;
				}
			}
		}

		if ("DynamicLineString".equals(geomType))
			geomType = LineString.class.getSimpleName();
		return geomType;
	}
}
