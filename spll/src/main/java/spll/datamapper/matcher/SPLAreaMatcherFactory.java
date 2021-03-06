package spll.datamapper.matcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.opengis.referencing.operation.TransformException;

import core.metamodel.geo.AGeoEntity;
import core.metamodel.geo.AGeoValue;
import core.metamodel.geo.io.IGSGeofile;
import core.util.GSPerformanceUtil;
import spll.datamapper.variable.SPLVariable;

public class SPLAreaMatcherFactory implements ISPLMatcherFactory<SPLVariable, Double> {

	private int matcherCount = 0;

	private Collection<? extends AGeoValue> variables;

	public SPLAreaMatcherFactory(Collection<? extends AGeoValue> variables) {
		this.variables = variables;
	}

	@Override
	public List<ISPLMatcher<SPLVariable, Double>> getMatchers(AGeoEntity entity, 
			IGSGeofile<? extends AGeoEntity> regressorsFile) throws IOException, TransformException, InterruptedException, ExecutionException { 
		return getMatchers(Arrays.asList(entity), regressorsFile);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * </p>
	 * WARNING: make use of parallelism
	 * 
	 */
	@Override
	public List<ISPLMatcher<SPLVariable, Double>> getMatchers(Collection<? extends AGeoEntity> entities,
			IGSGeofile<? extends AGeoEntity> regressorsFile) 
					throws IOException, TransformException, InterruptedException, ExecutionException {
		GSPerformanceUtil gspu = new GSPerformanceUtil("Start processing regressors' data");
		gspu.setObjectif(entities.size());
		List<ISPLMatcher<SPLVariable, Double>> varList = entities
				.parallelStream().map(entity -> getMatchers(entity, 
						regressorsFile.getGeoEntityIteratorWithin(entity.getGeometry()), 
						this.variables, gspu))
				.flatMap(list -> list.stream()).collect(Collectors.toList());
		gspu.sysoStempMessage("-------------------------\n"
				+ "process ends up with "+varList.size()+" collected matches");
		return varList;
	}

	// ----------------------------------------------------------- //

	/*
	 * TODO: could be optimise
	 */
	private List<ISPLMatcher<SPLVariable, Double>> getMatchers(AGeoEntity entity,
			Iterator<? extends AGeoEntity> geoData, Collection<? extends AGeoValue> variables, 
			GSPerformanceUtil gspu) {
		List<ISPLMatcher<SPLVariable, Double>> areaMatcherList = new ArrayList<>();
		while(geoData.hasNext()){
			AGeoEntity geoEntity = geoData.next();  
			for(String prop : geoEntity.getPropertiesAttribute()){
				AGeoValue value = geoEntity.getValueForAttribute(prop);
				if(!variables.isEmpty() && !variables.contains(value))
					continue;
				Optional<ISPLMatcher<SPLVariable, Double>> potentialMatch = areaMatcherList
						.stream().filter(varMatcher -> varMatcher.getVariable().getName().equals(prop.toString()) &&
								varMatcher.getVariable().getValue().equals(value)).findFirst();
				if(potentialMatch.isPresent()){
					// IF Variable is already matched, update area
					potentialMatch.get().expandValue(geoEntity.getArea());
				} else {
					// ELSE create Variable based on the feature and create SPLAreaMatcher with basic area
					//if(!geoEntity.getPropertyAttribute(prop).equals(value))
					areaMatcherList.add(new SPLAreaMatcher(entity, 
							new SPLVariable(value, prop.toString()), geoEntity.getArea()));
				}
			}
		}
		if(gspu != null && ((++matcherCount+1)/gspu.getObjectif() * 100) % 10 == 0d)
			gspu.sysoStempPerformance((matcherCount+1)/gspu.getObjectif(), this);
		return areaMatcherList;
	}

}
