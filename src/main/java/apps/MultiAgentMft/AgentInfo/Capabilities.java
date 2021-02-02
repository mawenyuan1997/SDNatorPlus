package apps.MultiAgentMft.AgentInfo;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

import java.io.Serializable;

public class Capabilities extends DirectedSparseGraph<ProductState,ResourceEvent> implements Serializable {
	
	private static final long serialVersionUID = -2137412673419126879L;

	public void addEdge(ResourceEvent... edges) {
		for(ResourceEvent edge: edges) {
			this.addEdge(edge, edge.getParent(), edge.getChild());
		}
	}
	
}
