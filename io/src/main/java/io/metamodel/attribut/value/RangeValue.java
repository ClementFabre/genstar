package io.metamodel.attribut.value;

import io.metamodel.attribut.GSEnumAttributeType;
import io.metamodel.attribut.IAttribute;
import io.util.data.GSEnumDataType;

public class RangeValue extends AValue {
	
	private String inputStringLowerBound;
	private String inputStringUpperBound;

	public RangeValue(String inputStringLowerBound, String inputStringUpperBound, String inputStringValue, GSEnumDataType dataType, IAttribute attribute) {
		super(inputStringValue, dataType, attribute);
		this.inputStringLowerBound = inputStringLowerBound;
		this.inputStringUpperBound = inputStringUpperBound;
	}
	
	public RangeValue(GSEnumDataType dataType, IAttribute attribute) {
		this(GSEnumAttributeType.unique.getDefaultStringValue(dataType), GSEnumAttributeType.unique.getDefaultStringValue(dataType), 
				GSEnumAttributeType.range.getDefaultStringValue(dataType), dataType, attribute);
	}
	
	public String getInputStringLowerBound(){
		return inputStringLowerBound;
	}
	
	public String getInputStringUpperBound(){
		return inputStringUpperBound;
	}

}