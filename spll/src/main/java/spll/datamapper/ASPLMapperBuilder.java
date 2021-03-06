package spll.datamapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.geotools.feature.SchemaException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.operation.TransformException;

import core.metamodel.geo.AGeoEntity;
import core.metamodel.geo.io.IGSGeofile;
import spll.algo.ISPLRegressionAlgo;
import spll.algo.exception.IllegalRegressionException;
import spll.datamapper.exception.GSMapperException;
import spll.datamapper.matcher.ISPLMatcherFactory;
import spll.datamapper.variable.ISPLVariable;
import spll.entity.GSFeature;
import spll.io.SPLGeofileFactory;
import spll.io.SPLRasterFile;
import spll.io.SPLVectorFile;
import spll.popmapper.normalizer.ASPLNormalizer;
import spll.util.SpllUtil;

/**
 * The mapper is the main concept of SPLL algorithm. It matches main geographical features
 * contain in a shape file to various geographical variables (e.g. other features, satellite image).
 * It also setup regression algorithm to compute the relationship between an attribute of main features
 * (dependent variable) and ancillary geographical variable (explanatory variables). Last, it encapsulate
 * the method to fit regression output to a proper contract (e.g. integer values, adjusted output sum).
 * 
 * @author kevinchapuis
 *
 * @param <V>
 * @param <T>
 */
public abstract class ASPLMapperBuilder<V extends ISPLVariable, T> {
	
	protected final IGSGeofile<? extends AGeoEntity> mainFile;
	private final String mainAttribute;
	
	protected List<IGSGeofile<? extends AGeoEntity>> ancillaryFiles;
	protected ISPLMatcherFactory<V, T> matcherFactory;
	
	protected ISPLRegressionAlgo<V, T> regressionAlgorithm;
	
	protected ASPLNormalizer normalizer;
	
	public ASPLMapperBuilder(IGSGeofile<? extends AGeoEntity> mainFile, String mainAttribute, 
			List<IGSGeofile<? extends AGeoEntity>> ancillaryFiles) {
		this.mainFile = mainFile;
		this.mainAttribute = mainAttribute;
		this.ancillaryFiles = ancillaryFiles;
	}
	
	////////////////////////////////////////////////////////////////
	// ------------------------- SETTERS ------------------------ //
	////////////////////////////////////////////////////////////////
	
	
	/**
	 * Setup the regression algorithm
	 * 
	 * @param regressionAlgorithm
	 */
	public void setRegressionAlgorithm(ISPLRegressionAlgo<V, T> regressionAlgorithm){
		this.regressionAlgorithm = regressionAlgorithm;
	}
	
	/**
	 * Setup the matcher factory, i.e. the object whose responsible for variable matching
	 * 
	 * @param matcherFactory
	 */
	public void setMatcherFactory(ISPLMatcherFactory<V, T> matcherFactory){
		this.matcherFactory = matcherFactory;
	}
	
	/**
	 * Setup the object that will ensure output value format to fit built-in normalizer requirements
	 * 
	 * @param normalizer
	 */
	public void setNormalizer(ASPLNormalizer normalizer){
		this.normalizer = normalizer;
	}
	
	/**
	 * Setup the output format to fit given geographic file
	 * 
	 * @param outputFormat
	 */
	public void setOutputFormat(IGSGeofile<? extends AGeoEntity> outputFormat){
		if(!ancillaryFiles.contains(outputFormat))
			throw new IllegalArgumentException("output format must be one of ancillary files");
		ancillaryFiles.add(0, ancillaryFiles.remove(ancillaryFiles.indexOf(outputFormat)));
	}
	
	///////////////////////////////////////////////////////////////
	// ------------------------ GETTERS ------------------------ //
	///////////////////////////////////////////////////////////////
	
	public List<IGSGeofile<? extends AGeoEntity>> getAncillaryFiles(){
		return Collections.unmodifiableList(ancillaryFiles);
	}
	
	public IGSGeofile<? extends AGeoEntity> getMainFile(){
		return mainFile;
	}
	
	public String getMainAttribute(){
		return mainAttribute;
	}
	
	/////////////////////////////////////////////////////////////////
	// ---------------------- main contract ---------------------- // 
	/////////////////////////////////////////////////////////////////
	
	/**
	 * This method match all ancillary files with the main shape file. More precisely,
	 * all geographic variables ancillary files contain will be bind to corresponding feature
	 * of the main file. Each {@link ASPLMapperBuilder} has its own definition of how
	 * feature and geographical variable should match to one another (e.g. within, intersect)
	 * 
	 * @return
	 * @throws IOException
	 * @throws TransformException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public abstract SPLMapper<V, T> buildMapper() throws IOException, TransformException, InterruptedException, ExecutionException;
	
	/*
	 * The method to implement to compute regression output with raster output format
	 */
	protected abstract float[][] buildOutput(SPLRasterFile formatFile, boolean intersect, boolean integer, Number targetPopulation) 
			throws IllegalRegressionException, TransformException, IndexOutOfBoundsException, IOException, GSMapperException;
	
	/**
	 * build the output of spll regression based localization as pixel based format output.
	 * Format file argument {@code formatFile} must be an ancillaryFiles, see {@link #getAncillaryFiles()}
	 * 
	 * @param outputFile
	 * @param formatFile
	 * @return
	 * @throws IllegalRegressionException
	 * @throws TransformException
	 * @throws IndexOutOfBoundsException
	 * @throws IOException
	 * @throws GSMapperException 
	 */
	public SPLRasterFile buildOutput(File output, SPLRasterFile formatFile, 
			boolean intersect, boolean integer, Number targetPopulation) 
			throws IllegalRegressionException, TransformException, 
			IndexOutOfBoundsException, IOException, GSMapperException {
		float[][] pixels = this.buildOutput(formatFile, intersect, integer, targetPopulation);
		return new SPLGeofileFactory().createRasterfile(output, pixels, 
				SPLRasterFile.DEF_NODATA.floatValue(), new ReferencedEnvelope(formatFile.getEnvelope(),
						SpllUtil.getCRSfromWKT(formatFile.getWKTCoordinateReferentSystem())));
	}
	
	/*
	 * The method to implement to compute regression output with vector output format
	 */
	protected abstract Map<GSFeature, Number> buildOutput(SPLVectorFile formatFile, boolean intersect, boolean integer, Number tagetPopulation);
	
	/**
	 * build the output of Spll regression based localization as vector based format output.
	 * Format file argument {@code formatFile} must be an ancillaryFiles, see {@link #getAncillaryFiles()}
	 * 
	 * @param outputFile
	 * @param formatFile
	 * @return
	 * @throws SchemaException 
	 * @throws IOException 
	 */
	public SPLVectorFile buildOutput(File output, SPLVectorFile formatFile, 
			boolean intersect, boolean integer, Number tagetPopulation) 
			throws IOException, SchemaException{
		@SuppressWarnings("unused")
		Map<GSFeature, Number> map = this.buildOutput(formatFile, intersect, integer, tagetPopulation);
		Collection<GSFeature> features = new ArrayList<>();
		
		// TODO compute feature from map
		
		return new SPLGeofileFactory().createShapeFile(output, features);
	}
	
}
