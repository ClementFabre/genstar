package spll.popmapper.constraint;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import core.metamodel.geo.AGeoEntity;

public class SpatialConstraintMaxDensity extends SpatialConstraintMaxNumber{

	protected Map<String, Double> nestInitDensity;
	
   //maxVal: global value for the max density of entities per nest
	public SpatialConstraintMaxDensity(Collection<? extends AGeoEntity> nests, Double maxVal) {
		super(nests, maxVal);
		
	} 
		
	 //keyAttMax: name of the attribute that contains the max density of entities in the nest file
	public SpatialConstraintMaxDensity(Collection<? extends AGeoEntity> nests, String keyAttMax) {
		super(nests, keyAttMax);
	}
	
	@Override
	public void relaxConstraintOp(Collection<AGeoEntity> nests) {
		for (AGeoEntity n : nests )
			nestCapacities.put(n.getGenstarName(), (int)Math.round(
					nestCapacities.get(n.getGenstarName()) 
					- (int)(Math.round(nestInitDensity.get(n.getGenstarName()) * ((AGeoEntity) n).getArea())))
					+ (int)(Math.round((nestInitDensity.get(n.getGenstarName()) + increaseStep *(1 + nbIncrements)) * ((AGeoEntity) n).getArea())));
	}
		
	
	
	protected Map<String, Integer> computeMaxPerNest(Collection<? extends AGeoEntity> nests, String keyAttMax){
		nestInitDensity = nests.stream().collect(Collectors.toMap(a -> ((AGeoEntity) a).getGenstarName(), 
			a-> ((AGeoEntity) a).getValueForAttribute(keyAttMax).getNumericalValue().doubleValue()));
		
		return nests.stream().collect(Collectors.toMap(a -> ((AGeoEntity) a).getGenstarName(), 
							a-> (int)(Math.round(((AGeoEntity) a).getValueForAttribute(keyAttMax).getNumericalValue().doubleValue() * ((AGeoEntity) a).getArea()))));
	}
	
	protected Map<String, Integer> computeMaxPerNest(Collection<? extends AGeoEntity> nests, Double maxVal){
		nestInitDensity = nests.stream().collect(Collectors.toMap(a -> ((AGeoEntity) a).getGenstarName(), a-> maxVal));
		return nests.stream().collect(Collectors.toMap(a -> ((AGeoEntity) a).getGenstarName(), 
						a-> (int)(Math.round(maxVal * ((AGeoEntity) a).getArea()))));
	}

}
