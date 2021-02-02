package apps.MultiAgentMft.AgentInfo;

import edu.uci.ics.jung.graph.DirectedSparseGraph;
import jade.core.AID;

import java.awt.*;
import java.util.ArrayList;

public class EnvironmentModel extends DirectedSparseGraph<ProductState, ResourceEvent>{
	private static final long serialVersionUID = 1L; //Eclipse wanted me to add this
	
	private ProductState currentState = null;
	private ProductState dummyEmptyNode; // Empty node needed to represent the "parent" of the initial state.
	private ResourceEvent startingEdge;

	private AID productAgent;

	public EnvironmentModel(AID productAgent, ProductState currentState, AID startingResource) {
		
		this.productAgent = productAgent;
		
		this.dummyEmptyNode = new ProductState(null, null, new PhysicalProperty(new Point(18,60))); //random point
		this.currentState = currentState;
		this.startingEdge = new ResourceEvent(startingResource, this.dummyEmptyNode, currentState, null, 0);
		this.addEdge(startingEdge, dummyEmptyNode, currentState);
	}
	

	public void update(DirectedSparseGraph<ProductState, ResourceEvent> systemOutput,
			ProductState currentState) {
		
		//Update both the graph and the current state
		this.update(currentState);
		this.update(systemOutput);
	}
	
	public void update(DirectedSparseGraph<ProductState, ResourceEvent> systemOutput){
		
		//Update directed graph
		for (ResourceEvent event : systemOutput.getEdges()){
			this.addEdge(event,event.getParent(),event.getChild());
		}
		for (ProductState state : systemOutput.getVertices()){
	        this.addVertex(state);
		}
	}
	
	public void update(ProductState currentState){
		addVertex(currentState);
		this.currentState = currentState;
	}
	
	public ProductState getCurrentState() {
		return this.currentState;
	}

	/**
	 * Clears everything except the current node and the dummy first node (edge that indicates current state)
	 */
	public void clear() {
		
		ArrayList<ProductState> removeVertices = new ArrayList<ProductState>();
		ArrayList<ResourceEvent> removeEdges = new ArrayList<ResourceEvent>();
		
		//Find all the vertices to remove
		for (ProductState node : getVertices()){
			// Keep the current and dummy nodes (starting)
			if (!this.currentState.equals(node) && !this.currentState.equals(dummyEmptyNode)){
				removeVertices.add(node);
			}
		}
		
		//Find all the edges to remove
		for (ResourceEvent edge: getEdges()){
			// Keep the starting edge
			if (!this.startingEdge.equals(edge)){
				removeEdges.add(edge);
			}
		}
		
		for (ProductState node : removeVertices){this.removeVertex(node);}
		for (ResourceEvent edge: removeEdges){this.removeEdge(edge);}
		
		// Clean up
		removeVertices.clear();
		removeEdges.clear();
		removeVertices = null;
		removeEdges = null;
	}
	
	/**
	 * @return if there are any events in the environment model
	 */
	public boolean isEmpty(){
		boolean flag = true;
		
		for (ResourceEvent edge: getEdges()){
			// Keep the starting edge
			if (!this.startingEdge.equals(edge)){
				flag = false;
				break;
			}
		}
		
		return flag;
	}
	
	
	@Override
	public String toString() {
		String printString = "";
		for (ProductState v:this.getVertices()) {
			printString = printString + v + ": ";
			for (ResourceEvent e:this.getOutEdges(v)) {
				printString = printString + "(Edge w/cost " + e.getEventTime() + " from " 
						+ e.getParent().getLocation().x + "," + e.getParent().getLocation().y + " to " 
						+ e.getChild().getLocation().x + "," + e.getChild().getLocation().y + "),";
			}
			printString = printString+"\n";
		}
		
		
		return "EnvironmentModel [ProductAgent=" + productAgent + "]\n" + printString;
	}
	
}
