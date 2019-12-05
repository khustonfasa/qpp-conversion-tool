package gov.cms.qpp.conversion.validate.pii;

import gov.cms.qpp.conversion.model.Node;
import gov.cms.qpp.conversion.validate.NodeValidator;

public interface PiiValidator {

	void validateApmTinNpiCombination(Node node, NodeValidator validator);

}
