package apps.MultiAgentMft.AgentMsg;

import apps.MultiAgentMft.AgentInfo.ResourceEvent;
import jade.core.AID;

import java.io.Serializable;

public class RequestAction implements Serializable{

	private static final long serialVersionUID = 2444200146397282610L;
	ResourceEvent queriedEdge;
	AID productAgent;
	
	public RequestAction(ResourceEvent queriedEdge, AID productAgent) {
		this.queriedEdge = queriedEdge;
		this.productAgent = productAgent;
	}

	public ResourceEvent getQueriedEdge() {
		return queriedEdge;
	}

	public AID getProductAgent() {
		return productAgent;
	}
}
