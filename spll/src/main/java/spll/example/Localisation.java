package spll.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.referencing.operation.TransformException;

import core.metamodel.geo.AGeoEntity;
import core.metamodel.geo.AGeoValue;
import core.metamodel.geo.io.GeoGSFileType;
import core.metamodel.geo.io.IGSGeofile;
import core.util.GSBasicStats;
import core.util.GSPerformanceUtil;
import spll.algo.ISPLRegressionAlgo;
import spll.algo.LMRegressionOLS;
import spll.algo.exception.IllegalRegressionException;
import spll.datamapper.ASPLMapperBuilder;
import spll.datamapper.SPLAreaMapperBuilder;
import spll.datamapper.SPLMapper;
import spll.datamapper.exception.GSMapperException;
import spll.datamapper.variable.SPLVariable;
import spll.io.GeofileFactory;
import spll.io.RasterFile;
import spll.io.ShapeFile;
import spll.io.exception.InvalidGeoFormatException;
import spll.popmapper.normalizer.SPLUniformNormalizer;
import spll.util.SpllUtil;

public class Localisation {

	/**
	 * args[0] = The path to available dir to put created files in
	 * args[1] = Main shape file that contains geometry for the dependent variable
	 * args[2] = The name (String) of the targeted dependent variable
	 * args[3] = String of variables to exclude from regression, using ';' to separate from one another
	 * args[4...] = Shape or raster files that contain explanatory variables (e.g. 30 x 30m raster image of land use or cover)
	 * First ancillary file define output format
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		// TODO: to args
		String outputFileName = "spll_output.tif";
		
		///////////////////////
		// INIT VARS FROM ARGS
		///////////////////////
		
		String stringPath = args[0];
		String stringPathToMainShapefile = args[1];
		String stringOfMainProperty = args[2];
		Collection<String> regVarName = Arrays.asList(args[3].split(";"));
		if(regVarName.size() == 1 && regVarName.iterator().next().isEmpty())
			regVarName = Collections.emptyList();
		Collection<String> stringPathToAncilaryGeofiles = new ArrayList<>();
		for(int i = 4; i < args.length; i++)
			stringPathToAncilaryGeofiles.add(args[i]);
		
		/////////////////////
		// IMPORT DATA FILES
		/////////////////////
		
		GeofileFactory gf = new GeofileFactory();
		
		core.util.GSPerformanceUtil gspu = new GSPerformanceUtil("Localisation of people in Bangkok based on Kwaeng (district) population");
		
		ShapeFile sfAdmin = null;
		try {
			sfAdmin = gf.getShapeFile(new File(stringPathToMainShapefile));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidGeoFormatException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		// WARNING: list of regressor file should be transpose to the main CRS projection !!!
		// Geo data could have divergent referent projection => transposed should be made with care
		// 
		// HINT: See what is made in GAMA
		// get the right projection for data given long / lat
		// 
		// int idx = (int) (0.5 + (longitude + 186) / 6d);
		// boolean north = latitude > 0;
		// String newCode = "EPSG:"+(32600 + idx + (north ? 0 : 100));
		// CoordinateReferentSystem crs = CRS.decode(newCode, true);
		List<IGSGeofile<? extends AGeoEntity>> endogeneousVarFile = new ArrayList<>();
		for(String path : stringPathToAncilaryGeofiles){
			try {
				endogeneousVarFile.add(gf.getGeofile(new File(path)));
			} catch (IllegalArgumentException | TransformException | IOException | InvalidGeoFormatException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		}
		
		String propertyName = sfAdmin.getGeoData().iterator().next()
				.getPropertyAttribute(stringOfMainProperty).getAttributeName();

		Collection<? extends AGeoValue> regVariables = SpllUtil.getValuesFor(regVarName, endogeneousVarFile);
		
		gspu.sysoStempPerformance("Input files data import: done\n", "Main");

		//////////////////////////////////
		// SETUP MAIN CLASS FOR REGRESSION
		//////////////////////////////////
		
		// Choice have been made to regress from areal data count
		ISPLRegressionAlgo<SPLVariable, Double> regressionAlgo = new LMRegressionOLS();
		
		ASPLMapperBuilder<SPLVariable, Double> spllBuilder = new SPLAreaMapperBuilder(
				sfAdmin, propertyName.toString(), endogeneousVarFile, regVariables,
				regressionAlgo);
		gspu.sysoStempPerformance("Setup MapperBuilder to proceed regression: done\n", "Main");

		// Setup main regressor class: SPLMapper
		SPLMapper<SPLVariable,Double> spl = null;
		boolean syso = false;
		try {
			spl = spllBuilder.buildMapper();
			if(syso){
				Map<SPLVariable, Double> regMap = spl.getRegression();
				gspu.sysoStempMessage("Regression parameter: \n"+Arrays.toString(regMap.entrySet().stream().map(e -> e.getKey()+" = "+e.getValue()+"\n").toArray()));
				gspu.sysoStempMessage("Intersect = "+spl.getIntercept());
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (TransformException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (ExecutionException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalRegressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// ---------------------------------
		// Apply regression function to output
		// ---------------------------------
		
		// WARNING: not generic at all - or define 1st ancillary data file to be the one for output format
		RasterFile outputFormat = (RasterFile) endogeneousVarFile
				.stream().filter(file -> file.getGeoGSFileType().equals(GeoGSFileType.RASTER))
				.findFirst().get();
		spllBuilder.setNormalizer(new SPLUniformNormalizer(0, RasterFile.DEF_NODATA));
		float[][] pixelOutput = null;
		try { 
			pixelOutput = spllBuilder.buildOutput(outputFormat, false, true, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalRegressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (TransformException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (GSMapperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		List<Double> outList = GSBasicStats.transpose(pixelOutput);
		GSBasicStats<Double> bs = new GSBasicStats<>(outList, Arrays.asList(RasterFile.DEF_NODATA.doubleValue()));
		gspu.sysoStempMessage("\nStatistics on output:\n"+bs.getStatReport());
		
		/////////////////////////
		// EXPORT OUTPUT
		////////////////////////
		
		try {
			ReferencedEnvelope env = new ReferencedEnvelope(endogeneousVarFile.get(0).getEnvelope(), 
					SpllUtil.getCRSfromWKT(outputFormat.getWKTCoordinateReferentSystem()));
			gf.createRasterfile(new File(stringPath+File.separator+outputFileName), pixelOutput, 
					RasterFile.DEF_NODATA.floatValue(), env);
		} catch (IllegalArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (TransformException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		///////////////////////
		// MATCH TO POPULATION
		///////////////////////
		
		// TODO
		
	}

}