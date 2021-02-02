package apps.MultiAgentMft.AgentInfo;

import edu.uci.ics.jung.graph.DirectedSparseGraph;

public class Bid extends DirectedSparseGraph<ProductState,ResourceEvent>{

	public Bid copyBid() {
		Bid bid = new Bid();
		
		for (ResourceEvent e : this.getEdges()){
			ResourceEvent newEdge = e.copy();
			bid.addEdge(newEdge,newEdge.getParent(),newEdge.getChild());
		}
		
		return bid;
	}
	
}
