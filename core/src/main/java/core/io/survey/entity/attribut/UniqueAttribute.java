package core.io.survey.entity.attribut;

import core.util.data.GSEnumDataType;

public class UniqueAttribute extends AGenstarAttribute {

	public UniqueAttribute(String name, GSEnumDataType dataType) {
		super(name, dataType);
	}

	@Override
	public boolean isRecordAttribute() {
		return false;
	}

}