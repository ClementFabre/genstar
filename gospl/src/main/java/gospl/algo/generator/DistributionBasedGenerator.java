package gospl.algo.generator;

import java.util.stream.Collectors;

import core.metamodel.pop.APopulationAttribute;
import core.metamodel.pop.APopulationValue;
import gospl.GosplPopulation;
import gospl.algo.sampler.ISampler;
import gospl.algo.sampler.evaluation.IEvaluableSampler;
import gospl.algo.sampler.evaluation.ISamplingEvaluation;
import gospl.distribution.matrix.coordinate.ACoordinate;
import gospl.entity.GosplEntity;

/**
 * A generator that take a defined distribution and a given sampler
 * 
 * TODO: make a builder > for example, build the sampler based on a distribution
 * 
 * @author kevinchapuis
 *
 */
public class DistributionBasedGenerator implements ISyntheticGosplPopGenerator {
	
	private ISampler< ACoordinate<APopulationAttribute, APopulationValue>> sampler;
	
	public DistributionBasedGenerator(ISampler< ACoordinate<APopulationAttribute, APopulationValue>> sampler) {
		this.sampler = sampler;
	}
	
	@Override
	public GosplPopulation generate(int numberOfIndividual) {
		GosplPopulation pop = new GosplPopulation();
		pop.addAll(sampler.draw(numberOfIndividual).parallelStream().map(coord -> new GosplEntity(coord.getMap())).collect(Collectors.toSet()));
		
		// TODO à faire ou pas ?
		if (sampler instanceof IEvaluableSampler) {
			ISamplingEvaluation eval = ((IEvaluableSampler)sampler).evaluateQuality(pop);
			System.out.println("for this population of "+eval.getGeneratedPopulationSize()+", the mean squarred error is "+ eval.getOverallBias());
		}
		
		return pop;
	}

}
