package gospl.algos;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import gospl.algos.exception.GosplSamplerException;
import gospl.algos.sampler.GosplAliasSampler;
import gospl.algos.sampler.GosplBasicSampler;
import gospl.algos.sampler.GosplBinarySampler;
import gospl.algos.sampler.ISampler;
import gospl.distribution.exception.IllegalDistributionCreation;
import gospl.distribution.exception.MatrixCoordinateException;
import gospl.distribution.matrix.AFullNDimensionalMatrix;
import gospl.distribution.matrix.ASegmentedNDimensionalMatrix;
import gospl.distribution.matrix.INDimensionalMatrix;
import gospl.distribution.matrix.control.AControl;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.distribution.matrix.coordinate.GosplCoordinate;
import gospl.metamodel.attribut.IAttribute;
import gospl.metamodel.attribut.value.IValue;
import io.util.GSPerformanceUtil;

/**
 * TODO: explain
 * 
 * TODO: find a way to choose the sampling algorithm, e.g. {@link GosplAliasSampler}, {@link GosplBasicSampler} or {@link GosplBinarySampler}
 * HINT: choose a builder that take a {@link IDistributionInferenceAlgo} and an empty {@link ISampler} to fill with
 * 
 * @author kevinchapuis
 *
 */
public class IndependantHypothesisAlgo implements IDistributionInferenceAlgo<IAttribute, IValue> {

	private boolean DEBUG_SYSO;

	public IndependantHypothesisAlgo(boolean DEBUG_SYSO) {
		this.DEBUG_SYSO = DEBUG_SYSO;
	}

	@Override
	public ISampler<ACoordinate<IAttribute, IValue>> inferDistributionSampler(
			INDimensionalMatrix<IAttribute, IValue, Double> matrix) throws IllegalDistributionCreation, GosplSamplerException {
		if(matrix == null || matrix.getMatrix().isEmpty())
			throw new IllegalArgumentException("matrix passed in parameter cannot be null or empty");

		// Begin the algorithm (and performance utility)
		GSPerformanceUtil gspu = new GSPerformanceUtil("Compute independant-hypothesis-joint-distribution from conditional distribution\nTheoretical size = "+
				matrix.getDimensions().stream().mapToInt(d -> d.getValues().size()).reduce(1, (i1, i2) -> i1 * i2), DEBUG_SYSO);
		gspu.getStempPerformance(0);
		
		/////////////////////////////////////
		// 1st STEP: identify and scale up GosplMetaDataType#LocalFrequencyTable
		/////////////////////////////////////

		// TODO
				
		/////////////////////////////////////
		// 2st STEP: identify the various inner matrices
		/////////////////////////////////////
		
		// Stop the algorithm and exit the unique matrix if there is only one
		if(!matrix.isSegmented())
			return new GosplBasicSampler(matrix);
		
		// Cast matrix to access inner full matrices
		ASegmentedNDimensionalMatrix<Double> segmentedMatrix = (ASegmentedNDimensionalMatrix<Double>) matrix;

		// Init sample distribution simple expression
		Map<Set<IValue>, Double> sampleDistribution = new HashMap<>();

		// Init collection to store processed attributes & matrix
		Set<IAttribute> allocatedAttributes = new HashSet<>();
		Set<AFullNDimensionalMatrix<Double>> unallocatedMatrices = new HashSet<>(segmentedMatrix.getMatrices());

		/////////////////////////////////////
		// 3rd STEP: disaggregate attributes
		/////////////////////////////////////
		
		// First identify referent & aggregated attributes:
		Set<IAttribute> refAtts = segmentedMatrix.getDimensions()
				.stream().filter(d -> !d.isRecordAttribute() && !d.getReferentAttribute().equals(d))
				.map(d -> d.getReferentAttribute()).collect(Collectors.toSet());
		Map<IAttribute, Set<IAttribute>> aggAttributeMap = refAtts
				.stream().collect(Collectors.toMap(refDim -> refDim, refDim -> segmentedMatrix.getDimensions()
						.stream().filter(aggDim -> aggDim.getReferentAttribute().equals(refDim)).collect(Collectors.toSet())));

		for(IAttribute referentAtt : aggAttributeMap.keySet()){
			// TODO: the job
		}

		////////////////////////////////////
		// 4th STEP: proceed the remaining matrices
		////////////////////////////////////
		
		for(AFullNDimensionalMatrix<Double> jd : unallocatedMatrices.stream()
				.sorted((jd1, jd2) -> jd2.size() - jd1.size()).collect(Collectors.toList())){
			// Collect attribute in the schema for which a probability have already been calculated
			Set<IAttribute> hookAtt = jd.getDimensions()
					.stream().filter(att -> allocatedAttributes.contains(att)).collect(Collectors.toSet());

			gspu.sysoStempPerformance(0d, this);
			// If "hookAtt" is empty fill the proxy distribution with conditional probability of this joint distribution
			if(sampleDistribution.isEmpty()){
				sampleDistribution.putAll(jd.getMatrix().entrySet()
						.parallelStream().collect(Collectors.toMap(e -> new HashSet<>(e.getKey().values()), e -> e.getValue().getValue())));

				gspu.sysoStempPerformance(1d, this);
				System.out.println(sampleDistribution.keySet().parallelStream().mapToInt(c -> c.size()).average().getAsDouble()+" attributs ("+
						Arrays.toString(jd.getDimensions().stream().map(d -> d.getName()).toArray())+")");

				// Else, take into account known conditional probabilities in order to add new attributes
			} else {
				int j = 1;
				Map<Set<IValue>, Double> newSampleDistribution = new HashMap<>();
				for(Set<IValue> indiv : sampleDistribution.keySet()){
					Set<IValue> hookVal = indiv.stream().filter(val -> hookAtt.contains(val.getAttribute())).collect(Collectors.toSet());
					Map<ACoordinate<IAttribute, IValue>, AControl<Double>> coordsHooked = jd.getMatrix().entrySet()
							.parallelStream().filter(e -> e.getKey().containsAll(hookVal))
							.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
					double summedProba = coordsHooked.values().stream().reduce(segmentedMatrix.getNulVal(), (v1, v2) -> v1.add(v2)).getValue();
					for(ACoordinate<IAttribute, IValue> newIndivVal : coordsHooked.keySet()){
						Set<IValue> newIndiv = new HashSet<>(indiv);
						newIndiv.addAll(newIndivVal.values());
						double newProba = sampleDistribution.get(indiv) * coordsHooked.get(newIndivVal).getValue() / summedProba;
						if(newProba > 0d)
							newSampleDistribution.put(newIndiv, newProba);
					}
					if(j++ % (sampleDistribution.size() / 10) == 0)
						gspu.sysoStempPerformance(j * 1d / sampleDistribution.size(), this);
				}
				System.out.println("-------------------------\nFrom "+sampleDistribution.keySet().parallelStream().mapToInt(c -> c.size()).average().getAsDouble()+
						"To "+newSampleDistribution.keySet().parallelStream().mapToInt(c -> c.size()).average().getAsDouble()+" attributs ("+
						Arrays.toString(jd.getDimensions().stream().map(d -> d.getName()).toArray())+")"
						+ "\n-------------------------");
				sampleDistribution = newSampleDistribution;
			}
			allocatedAttributes.addAll(jd.getDimensions());
			gspu.resetStempProp();
			gspu.sysoStempPerformance(1, this);
		}
		long wrongPr = sampleDistribution.keySet().parallelStream().filter(c -> c.size() != segmentedMatrix.getDimensions().size()).count();
		double avrSize =  sampleDistribution.keySet().parallelStream().mapToInt(c -> c.size()).average().getAsDouble();
		gspu.resetStempProp();
		if(wrongPr != 0)
			throw new IllegalDistributionCreation("Some sample indiv ("+( Math.round(Math.round(wrongPr * 1d / sampleDistribution.size() * 100)))+"%) have not all attributs (average attributs nb = "+avrSize+")");
		LinkedHashMap<ACoordinate<IAttribute, IValue>, Double> sdMap = sampleDistribution.entrySet()
				.parallelStream().sorted(Map.Entry.comparingByValue())
				.collect(Collectors.toMap(e -> safeCoordinateCreation(e.getKey(), gspu), e -> e.getValue(), (e1, e2) -> e1, LinkedHashMap::new));
		return new GosplBasicSampler(sdMap);
	}

	// Fake methode in order to create coordinate in lambda java 8 stream operation
	private ACoordinate<IAttribute, IValue> safeCoordinateCreation(Set<IValue> coordinate, GSPerformanceUtil gspu){
		ACoordinate<IAttribute, IValue> coord = null;
		try {
			coord = new GosplCoordinate(coordinate);
		} catch (MatrixCoordinateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return coord;
	}

}
