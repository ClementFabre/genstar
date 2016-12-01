package gospl.algo;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import core.io.survey.GSSurveyType;
import core.io.survey.entity.attribut.AGenstarAttribute;
import core.io.survey.entity.attribut.value.AGenstarValue;
import core.util.GSPerformanceUtil;
import gospl.algo.sampler.IHierarchicalSampler;
import gospl.algo.sampler.ISampler;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.ASegmentedNDimensionalMatrix;
import gospl.distribution.matrix.INDimensionalMatrix;
import gospl.distribution.matrix.control.AControl;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.distribution.util.GosplBasicDistribution;
import jj2000.j2k.NotImplementedError;


/**
 * an inference algorithm that assesses the relationships between the attributes passed 
 * as inputs and defines an order for their usage. 
 * 
 * We assume we can always 
 * 
 * @author Kevin Chapuis
 * @author Samuel Thiriot
 *
 */
public class HierarchicalHypothesisAlgo implements IDistributionInferenceAlgo<IHierarchicalSampler> {

	private Logger logger = LogManager.getLogger();
	

	public HierarchicalHypothesisAlgo() {
	}
	


	@Override
	public ISampler<ACoordinate<AGenstarAttribute, AGenstarValue>> inferDistributionSampler(
			INDimensionalMatrix<AGenstarAttribute, AGenstarValue, Double> matrix, 
			IHierarchicalSampler sampler)
					throws IllegalDistributionCreation {
		
		// Begin the algorithm (and performance utility)
		GSPerformanceUtil gspu = new GSPerformanceUtil("Compute hierachical sampler from conditional distribution", logger);
		gspu.getStempPerformance(0);
		
		if (matrix == null)
			throw new IllegalArgumentException("matrix passed in parameter cannot be null or empty");
		
		if (!matrix.isSegmented() && matrix.getMetaDataType().equals(GSSurveyType.LocalFrequencyTable))
		 	throw new IllegalDistributionCreation("can't create a sampler using only one matrix of GosplMetaDataType#LocalFrequencyTable");
		
		// Stop the algorithm and exit the unique matrix if there is only one
		if(!matrix.isSegmented()){
			// TODO is that relevant ? 
			// TODO what to do ? sampler.setDistribution((AFullNDimensionalMatrix<Double>) matrix);
			throw new NotImplementedError();
			//return sampler;
		}

		/////////////////////////////////////
		// 1st STEP: identify the various inner matrices
		/////////////////////////////////////

		// Cast matrix to access inner full matrices
		ASegmentedNDimensionalMatrix<Double> segmentedMatrix = (ASegmentedNDimensionalMatrix<Double>) matrix;

		// Init sample distribution simple expression
		Map<Set<AGenstarValue>, Double> sampleDistribution = new HashMap<>();

		// Init collection to store processed attributes & matrix
		Set<AGenstarAttribute> allocatedAttributes = new HashSet<>();
		Set<AFullNDimensionalMatrix<Double>> unallocatedMatrices = new HashSet<>(segmentedMatrix.getMatrices());

		/////////////////////////////////////
		// 2nd STEP: disaggregate attributes & start processing associated matrices
		/////////////////////////////////////

		// First identify aggregated & referent attributes
		Set<AGenstarAttribute> aggAtts = segmentedMatrix.getDimensions()
				.stream().filter(d -> !d.isRecordAttribute() && !d.getReferentAttribute().equals(d))
				.collect(Collectors.toSet());
		Set<AGenstarAttribute> refAtts = aggAtts.stream().map(att -> att.getReferentAttribute())
				.collect(Collectors.toSet());
		// Then disintegrated attribute -> to aggregated attributes relationships
		Map<AGenstarAttribute, Set<AGenstarAttribute>> aggAttributeMap = refAtts
				.stream().collect(Collectors.toMap(refDim -> refDim, refDim -> segmentedMatrix.getDimensions()
						.stream().filter(aggDim -> aggDim.getReferentAttribute().equals(refDim) 
								&& !aggDim.equals(refDim)).collect(Collectors.toSet())));
		
		// And disintegrated value -> to aggregated values relationships:
		// TODO: move to a less verbose algorithme
		Map<AGenstarValue, Set<AGenstarValue>> aggToRefValues = new HashMap<>();
		for(AGenstarAttribute asa : aggAtts){
			Map<AGenstarValue, Set<AGenstarValue>> tmpMap = new HashMap<>();
			Set<AGenstarValue> values = asa.getValues();
			for(AGenstarValue val : values)
				tmpMap.put(val, val.getAttribute().findMappedAttributeValues(val));
			Set<AGenstarValue> matchedValue = tmpMap.values().stream().flatMap(set -> set.stream()).collect(Collectors.toSet());
			Set<AGenstarValue> refValue = asa.getReferentAttribute().getValues();
			Set<AGenstarValue> unmatchedValue = refValue.stream().filter(val -> !matchedValue.contains(val)).collect(Collectors.toSet());
			tmpMap.put(asa.getEmptyValue(), unmatchedValue);
			aggToRefValues.putAll(tmpMap);
		}

		// Iterate over matrices that have referent attributes and no aggregated attributes
		List<AFullNDimensionalMatrix<Double>> refMatrices = segmentedMatrix.getMatrices().stream()
				.filter(mat -> mat.getDimensions().stream().anyMatch(dim -> aggAttributeMap.keySet().contains(dim) 
						&& !aggAttributeMap.values().stream().flatMap(set -> set.stream()).anyMatch(aggDim -> dim.equals(aggDim))))
				.sorted((mat1, mat2) -> (int) mat2.getDimensions().stream().filter(dim2 -> aggAttributeMap.keySet().contains(dim2)).count()
						- (int) mat1.getDimensions().stream().filter(dim1 -> aggAttributeMap.keySet().contains(dim1)).count())
				.collect(Collectors.toList());
		for(AFullNDimensionalMatrix<Double> mat : refMatrices){
			sampleDistribution = updateGosplProbaMap(sampleDistribution, mat, gspu);
			unallocatedMatrices.remove(mat);
			allocatedAttributes.addAll(mat.getDimensions());
		}
		
		// Iterate over matrices that have aggregated attributes
		List<AFullNDimensionalMatrix<Double>> aggMatrices = unallocatedMatrices.stream()
				.filter(mat -> mat.getDimensions().stream().anyMatch(dim -> aggAttributeMap.values()
						.stream().flatMap(set -> set.stream()).anyMatch(aggDim -> dim.equals(aggDim))))
				.sorted((mat1, mat2) -> (int) mat2.getDimensions().stream().filter(dim2 -> aggAttributeMap.keySet().contains(dim2)).count()
						- (int) mat1.getDimensions().stream().filter(dim1 -> aggAttributeMap.keySet().contains(dim1)).count())
				.collect(Collectors.toList());
		
		for(AFullNDimensionalMatrix<Double> mat : aggMatrices){
			Map<Set<AGenstarValue>, Double> updatedSampleDistribution = new HashMap<>();
			Map<Set<AGenstarValue>, Double> untargetedIndiv = new HashMap<>(sampleDistribution);
			
			// Corresponding disintegrated control total of aggregated values
			double oControl = sampleDistribution.entrySet()
					.parallelStream().filter(indiv -> mat.getDimensions()
							.stream().filter(ad -> aggAtts.contains(ad)).map(ad -> ad.getValues()).allMatch(aVals -> aVals
									.stream().anyMatch(av -> aggToRefValues.get(av)
											.stream().anyMatch(dv -> indiv.getKey().contains(dv)))))
					.mapToDouble(indiv -> indiv.getValue()).sum();
			
			// Iterate over the old sampleDistribution to had new disaggregate values
			for(ACoordinate<AGenstarAttribute, AGenstarValue> aggCoord : mat.getMatrix().keySet()){
				
				// Identify all individual in the distribution that have disintegrated information about aggregated data
				Map<Set<AGenstarValue>, Double> targetedIndiv = sampleDistribution.entrySet()
						.stream().filter(indiv -> aggCoord.getDimensions()
								.stream().filter(d -> allocatedAttributes.contains(d)).map(d -> aggCoord.getMap().get(d))
								.allMatch(v -> indiv.getKey().contains(v))
								&& aggToRefValues.entrySet()
								.stream().filter(e -> aggCoord.contains(e.getKey())).map(e -> e.getValue())
								.allMatch(vals -> vals.stream().anyMatch(v -> indiv.getKey().contains(v))))
						.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
								
				// Retain new values from aggregated coordinate
				Set<AGenstarValue> newVals = aggCoord.getMap().entrySet()
						.stream().filter(e -> !aggAtts.contains(e.getKey()) 
								&& !allocatedAttributes.contains(e.getKey()))
						.map(e -> e.getValue()).collect(Collectors.toSet());
				
				// Identify targeted probability: sum of proba for tageted disintegrated values and proba for aggregated values
				double mControl = targetedIndiv.values().stream().reduce(0d, (d1, d2) -> d1 + d2);
				double aControl = mat.getMatrix().get(aggCoord).getValue();
				for(Set<AGenstarValue> indiv : targetedIndiv.keySet()){
					updatedSampleDistribution.put(Stream.concat(indiv.stream(), newVals.stream()).collect(Collectors.toSet()), 
							targetedIndiv.get(indiv) / mControl * aControl * oControl);

					untargetedIndiv.remove(indiv);
				}
			}
			
			// Iterate over non updated individual to add new attribute empty value (no info in aggregated data)
			Set<AGenstarValue> newVals = mat.getDimensions()
					.stream().filter(d -> !allocatedAttributes.contains(d) && !aggAtts.contains(d))
					.map(d -> d.getEmptyValue()).collect(Collectors.toSet());
			for(Set<AGenstarValue> indiv : untargetedIndiv.keySet())
				updatedSampleDistribution.put(Stream.concat(indiv.stream(), newVals.stream()).collect(Collectors.toSet()), 
						sampleDistribution.get(indiv));
			
			// Update allocated attributes and sampleDistribution
			allocatedAttributes.addAll(mat.getDimensions()
					.stream().filter(a -> !aggAtts.contains(a)).collect(Collectors.toSet()));
			sampleDistribution = updatedSampleDistribution;
			unallocatedMatrices.remove(mat);
		}

		////////////////////////////////////
		// 3rd STEP: proceed the remaining matrices
		////////////////////////////////////

		for(AFullNDimensionalMatrix<Double> mat : unallocatedMatrices){
			gspu.sysoStempPerformance(0d, this);

			// If "hookAtt" is empty fill the proxy distribution with conditional probability of this joint distribution
			sampleDistribution = updateGosplProbaMap(sampleDistribution, mat, gspu);
			allocatedAttributes.addAll(matrix.getDimensions());

			gspu.sysoStempPerformance(1, this);
		}
	
		// TODO to be defined
		
		// at this stage:
		// - allocatedAttributes contains all the attributes of interest here. 
		// - sampleDistribution contains the normalized frequencies (not conditional but local)
		// - segmentedMatrix corresponds to the data received as input segmentedMatrix.getMatrices() gives all the matrices
		
		logger.debug("end of process");
		logger.debug("allocatedAttributes: {}", allocatedAttributes);
		logger.debug("sampleDistribution: {}", sampleDistribution);
		
		for (AFullNDimensionalMatrix<Double> currentMatrix: segmentedMatrix.getMatrices()) {
			for (AGenstarAttribute att: currentMatrix.getDimensions()) {
				logger.debug("	att: {}", att);
			}
		}

		AttributesDependanciesGraph dependancyGraph = AttributesDependanciesGraph.constructDependancies(segmentedMatrix);
		//File dotFile = graph.printDotRepresentationToFile();
		//logger.info("a dot file was stored into "+dotFile.getAbsolutePath());
		
		// Use this to visualize the dependancy graph in png 
		// dependancyGraph.generateDotRepresentationInPNG(true);
		
		// TODO sampler.setDistribution(new GosplBasicDistribution(sampleDistribution));
		Collection<List<AGenstarAttribute>> explorationOrder = proposeExplorationOrder(dependancyGraph);
		
		logger.debug("sample distribution {}", sampleDistribution);
		sampler.setDistribution(new GosplBasicDistribution(sampleDistribution), explorationOrder, segmentedMatrix);
		
		return sampler;
	}

	/**
	 * Based on the graph of dependancies, defines for each subgraph a valid order of usage of the attributes
	 * for the hierarchical sampling.
	 * @return
	 */
	public Collection<List<AGenstarAttribute>> proposeExplorationOrder(AttributesDependanciesGraph dependancyGraph) {
		
		// first detect the subgraphs
		Collection<Set<AGenstarAttribute>> independantGraphs = dependancyGraph.getConnectedComponents();
		
		Collection<List<AGenstarAttribute>> res = new LinkedList<>();

		
		for (Set<AGenstarAttribute> component: independantGraphs) {
		
			logger.debug("component {} ", component);
			
			// detect the candidate roots here 
			Set<AGenstarAttribute> potentialRoots = dependancyGraph.getRoots(component);
			logger.debug("might start with roots: {}", potentialRoots);
			
			// TODO what is the more relevant ? Start smartly with the less low probabilities to reduce biasing ? 
			// with the highest or lowest cards ? 
			
			// well, right now we just select the first one :-/
			AGenstarAttribute root = potentialRoots.iterator().next();
			
			// add now build the list !
			List<AGenstarAttribute> orderForSubgraph = dependancyGraph.getOrderOfExploration(component, root);
			logger.debug("this component should be explore in this order: {}", orderForSubgraph);
			res.add(orderForSubgraph);
		}
		
		return res;
	}
	
	// ------------------------------ inner utility methods ------------------------------ //

	
	private Map<Set<AGenstarValue>, Double> updateGosplProbaMap(Map<Set<AGenstarValue>, Double> sampleDistribution, 
			AFullNDimensionalMatrix<Double> matrix, GSPerformanceUtil gspu){
		Map<Set<AGenstarValue>, Double> updatedSampleDistribution = new HashMap<>();
		if(sampleDistribution.isEmpty()){
			updatedSampleDistribution.putAll(matrix.getMatrix().entrySet()
					.parallelStream().collect(Collectors.toMap(e -> new HashSet<>(e.getKey().values()), e -> e.getValue().getValue())));
		} else {
			int j = 1;
			Set<AGenstarAttribute> allocatedAttributes = sampleDistribution.keySet()
					.parallelStream().flatMap(set -> set.stream()).map(a -> a.getAttribute()).collect(Collectors.toSet());
			Set<AGenstarAttribute> hookAtt = matrix.getDimensions()
					.stream().filter(att -> allocatedAttributes.contains(att)).collect(Collectors.toSet());
			for(Set<AGenstarValue> indiv : sampleDistribution.keySet()){
				Set<AGenstarValue> hookVal = indiv.stream().filter(val -> hookAtt.contains(val.getAttribute()))
						.collect(Collectors.toSet());
				Map<ACoordinate<AGenstarAttribute, AGenstarValue>, AControl<Double>> coordsHooked = matrix.getMatrix().entrySet()
						.parallelStream().filter(e -> e.getKey().containsAll(hookVal))
						.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
				double summedProba = coordsHooked.values()
						.stream().reduce(matrix.getNulVal(), (v1, v2) -> v1.add(v2)).getValue();
				for(ACoordinate<AGenstarAttribute, AGenstarValue> newIndivVal : coordsHooked.keySet()){
					Set<AGenstarValue> newIndiv = Stream.concat(indiv.stream(), newIndivVal.values().stream()).collect(Collectors.toSet());
					double newProba = sampleDistribution.get(indiv) * coordsHooked.get(newIndivVal).getValue() / summedProba;
					if(newProba > 0d)
						updatedSampleDistribution.put(newIndiv, newProba);
				}
				if(j++ % (sampleDistribution.size() / 10) == 0)
					gspu.sysoStempPerformance(j * 1d / sampleDistribution.size(), this);
			}
		}
		return updatedSampleDistribution;

	}



}
