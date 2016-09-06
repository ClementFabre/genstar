package gospl.metamodel.attribut;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.xml.schema.AttributeValue;

import gospl.exception.GSException;
import gospl.metamodel.attribut.value.IValue;
import gospl.metamodel.attribut.value.RangeValue;
import gospl.metamodel.attribut.value.UniqueValue;
import io.data.GSDataParser;
import io.data.GSDataType;
import io.datareaders.surveyreader.exception.GSIllegalRangedData;

public class AttributeFactory {

	private final GSDataParser parser;
	
	/*
	 * TODO: change to parameter 
	 * HINT: specific rule implementation
	 */
	private int minInt = 0;
	private int maxInt = 120;
	private double minDouble = 0d;
	private double maxDouble = 1d;

	public AttributeFactory() {
		this.parser = new GSDataParser();
	}

	/**
	 * Main method to instantiate {@link AbstractAttribute}. Concrete type depend on {@link GSAttDataType} passed in argument.
	 * <p> <ul>
	 * <li>If {@code valueType.equals({@link GosplValueType#range})} then return a {@link RangeAttribute}
	 * <li>If {@code valueType.equals({@link GosplValueType#unique})} then return a {@link UniqueAttribute}
	 * <li>If {@code valueType.equals({@link GosplValueType#record})} then return a {@link RecordAttribute}
	 * <li>FutherMore if {@code !IAttribute#getReferentAttribute().equals(this) && !this.isRecordAttribute()} then return a {@link AggregatedAttribute} 
	 * </ul><p>
	 *   
	 * 
	 * @param nameOnData
	 * @param nameOnEntity
	 * @param dataType
	 * @param values
	 * @param valueType
	 * 
	 * @return an {@link IAttribute}
	 * 
	 * @throws GSIllegalRangedData 
	 * @throws GSException
	 * @throws GenstarIllegalRangedData
	 */
	public IAttribute createAttribute(String name, GSDataType dataType, List<String> values,
			GosplValueType valueType) throws GSException, GSIllegalRangedData {
		return createAttribute(name, dataType, values, valueType, null, Collections.emptyMap());
	}

	/**
	 * Method that permit to instantiate specific case of {@link AbstractAttribute}: {@link RecordAttribute} and {@link AggregatedAttribute}
	 * <p>
	 * TODO: explain how
	 * 
	 * @param nameOnData
	 * @param nameOnEntity
	 * @param dataType
	 * @param values
	 * @param valueType
	 * @param referentAttribute
	 * 
	 * @return an {@link IAttribute}
	 * 
	 * @throws GSException
	 * @throws GSIllegalRangedData 
	 * @throws GenstarIllegalRangedData
	 */
	public IAttribute createAttribute(String name, GSDataType dataType, List<String> values,
			GosplValueType valueType, IAttribute referentAttribute, Map<String, Set<String>> mapper) throws GSException, GSIllegalRangedData {
		IAttribute att = null;
		switch (valueType) {
		case unique:
			if(referentAttribute == null)
				att = new UniqueAttribute(name, dataType);
			else if (!mapper.isEmpty())
				att = new AggregatedAttribute(name, dataType, referentAttribute, mapper);
			else
				throw new GSException("cannot instantiate aggregated value without mapper");
			break;
		case range:
			if(mapper.isEmpty())
				att = new RangeAttribute(name, dataType);
			else if(referentAttribute != null)
				att = new AggregatedAttribute(name, dataType, referentAttribute, mapper);
			else
				throw new GSException("cannot instantiate aggregated value with "+referentAttribute+" referent attribute");
			break;
		case record:
			att = new RecordAttribute(name, dataType, referentAttribute);
			break;
		default:
			throw new GSException("The attribute meta data type "+valueType+" is not applicable !");
		}
		att.setValues(this.getValues(valueType, dataType, values, att));
		att.setEmptyValue(this.getEmptyValue(valueType, dataType, att));
		return att;
	}

	/**
	 * create a value with {@code valueType}, concrete value type {@code dataType} and the given {@code attribute} to refer to.
	 * <p>
	 * {@code values} can represent an unlimited number of string values, only the first one for unique type and the two first
	 * ones for range type will be used for {@link AttributeValue} creation. if {@code values} is empty, then returned {@link AttributeValue}
	 * will be empty
	 * 
	 * @param {@link GSAttDataType} valueType
	 * @param {@link GSDataType} dataType
	 * @param {@code List<String>} values
	 * @param {@link AbstractAttribute} attribute
	 * @return a value with {@link AttributeValue} type
	 * @throws GSException
	 * @throws GenstarIllegalRangedData
	 */
	public IValue createValue(GosplValueType valueType, GSDataType dataType, List<String> values, IAttribute attribute) 
			throws GSException, GSIllegalRangedData{
		if(values.isEmpty())
			return getEmptyValue(valueType, dataType, attribute);
		return getValues(valueType, dataType, values, attribute).iterator().next();
	}

	// ----------------------------- Back office ----------------------------- //

	private Set<IValue> getValues(GosplValueType valueType, GSDataType dataType, List<String> values, IAttribute attribute) 
			throws GSException, GSIllegalRangedData{
		Set<IValue> vals = new HashSet<>();
		switch (valueType) {
		case record:
			if(values.size() > 1)
				values = values.subList(0, 1);
		case unique:
			for(String value : values)
				if(dataType.isNumericValue())
					vals.add(new UniqueValue(parser.getNumber(value.trim()).get(0), dataType, attribute));
				else
					vals.add(new UniqueValue(value.trim(), dataType, attribute));
			return vals;
		case range:
			if(dataType.equals(GSDataType.Integer)){
				List<Integer> valList = new ArrayList<>();
				for(String range : values)
					valList.addAll(parser.getRangedIntegerData(range, false));
				Collections.sort(valList);
				for(String val : values){
					List<Integer> intVal = parser.getRangedIntegerData(val, false);
					if(intVal.size() == 1){
						if(intVal.get(0).equals(valList.get(0)))
							intVal.add(0, minInt);
						else
							intVal.add(maxInt);
					}
					vals.add(new RangeValue(intVal.get(0).toString(), intVal.get(1).toString(), val, dataType, attribute));
				}
			} else if(dataType.equals(GSDataType.Double)){
				List<Double> valList = new ArrayList<>();
				for(String range : values)
					valList.addAll(parser.getRangedDoubleData(range, false));
				Collections.sort(valList);
				for(String val : values){
					List<Double> doublVal = parser.getRangedDoubleData(val, false);
					if(doublVal.size() == 1){
						if(doublVal.get(0).equals(valList.get(0)))
							doublVal.add(0, minDouble);
						else
							doublVal.add(maxDouble);
					}
					vals.add(new RangeValue(doublVal.get(0).toString(), doublVal.get(1).toString(), val, dataType, attribute));
				}
			}
			return vals;
		default:
			throw new GSException(valueType+ " is not a valide type to construct a "+IValue.class.getCanonicalName());
		}
	}

	private IValue getEmptyValue(GosplValueType valueType, GSDataType dataType, IAttribute attribute) 
			throws GSException{
		switch (valueType) {
		case unique:
			return new UniqueValue(dataType, attribute);
		case range:
			return new RangeValue(dataType, attribute);
		default:
			return new UniqueValue(dataType, attribute);
		}
	}

}